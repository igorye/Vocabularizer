package com.nicedev.vocabularizer.services;

import com.nicedev.util.Maps;
import com.nicedev.util.Strings;
import com.nicedev.vocabularizer.dictionary.Definition;
import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Vocabula;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.nicedev.util.SimpleLog.log;
import static com.nicedev.util.Streams.getStream;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

public class IndexingService<M> extends Service<Map<String, Collection<String>>> {
	
	private Dictionary source;
	private Task<Map<String, Collection<String>>> task;
	private final EventHandler<WorkerStateEvent> succeededHandler;
	private final int INDEXING_ALGORITHM;
	private final boolean ALLOW_PARALLEL;
	
	public IndexingService(Dictionary dictionaryToIndex, EventHandler<WorkerStateEvent> succeededHandler,
	                       boolean allowParallel, int indexingAlgorithm) {
		super();
		this.source = dictionaryToIndex;
		this.succeededHandler = succeededHandler;
		this.INDEXING_ALGORITHM = indexingAlgorithm;
		this.ALLOW_PARALLEL = allowParallel;
		setOnSucceeded(this.succeededHandler);
	}
	
	@Override
	protected Task<Map<String, Collection<String>>> createTask() {
		return task = new Task<Map<String, Collection<String>>>() {
			@Override
			protected Map<String, Collection<String>> call() throws Exception {
				long start = System.currentTimeMillis();
				Map<String, Collection<String>> index = getIndex(INDEXING_ALGORITHM);
				if (isCancelled()) return Collections.emptyMap();
				log("built index in %f (allowParallelStream==%s, INDEXING_ALGORITHM==%s",
						(System.currentTimeMillis() - start) / 1000f, ALLOW_PARALLEL, INDEXING_ALGORITHM);
				return index;
			}
		};
	}
	
	@Override
	public boolean cancel() {
		if (isRunning()) log("indexingService: cancelling");
		return super.cancel();
	}
	
	@Override
	public void restart() {
		log("indexingService: restarting");
		super.restart();
	}
	
	@Override
	public void start() {
		log("indexingService: starting");
		super.start();
	}
	
	@Override
	protected void running() {
		super.running();
		log("indexingService: running");
	}
	
	@Override
	protected void succeeded() {
		super.succeeded();
		log("indexingService: succeeded");
	}
	
	@Override
	protected void cancelled() {
		super.cancelled();
		log("indexingService: cancelled");
	}
	
	public boolean isCancelled() {
		return task.isCancelled();
	}
	
	private Map<String, Collection<String>> getMockIndex() {
		try {
			log("getMockIndex: sleeping on %s thread", Platform.isFxApplicationThread() ? "FXApp" : "background");
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return Collections.emptyMap();
	}
	
	// useful part of job
	
	private Map<String, Collection<String>> getIndex(int indexingAlgo) {
		//log("chosen algo %d", indexingAlgo);
		//long start = System.currentTimeMillis();
		Map<String, Collection<String>> res;
		switch (indexingAlgo) {
			case 1 : res = getIndex1(); break;
			case 2 : res = getIndex2(); break;
			default: res = getIndex();
		}
		//log("built index in %f (allowParallelStream==%s, indexingAlgo==%d",
		//	(System.currentTimeMillis() - start) / 1000f, ALLOW_PARALLEL_STREAM, indexingAlgo);
		return  res;
	}
	
	private Predicate<String> allMatch = s -> true;
	
	private Predicate<String> bTagWrapped = s -> s.contains("<b>");
	
	private Function<Definition, Stream<String>> hwFromDefinition = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(singleton(def.explanation), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Function<Definition, Stream<String>> hwFromKnownForms = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(def.getDefinedVocabula().getKnownForms(), allMatch, Stream::of);
	};
	
	private Function<Definition, Stream<String>> hwFromSynonyms = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(def.getSynonyms(), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Function<Definition, Stream<String>> hwFromUseCases = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(def.getUseCases(), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Stream<String> hwCollector(Set<String> source,
	                                   Predicate<String> hwMatcher,
	                                   Function<String, Stream<String>> hwExtractor) {
		return source.stream().filter(hwMatcher).flatMap(hwExtractor).filter(Strings::notBlank);
	}
	
	private Stream<String> extract_bTag_Wrapped(String source) {
		Set<String> HWs = new HashSet<>();
		Matcher matcher = Pattern.compile("<b>((?<=<b>)[^<>]+(?=</b>))</b>").matcher(source);
		while (matcher.find()) HWs.add(matcher.group(1).trim());
		Set<String> separateHWs = HWs.stream()
				                          .flatMap(s -> stream(s.split("[,.:;/()\\s\\\\]"))
						                                        .filter(Strings.notBlank)
						                                        .distinct())
				                          .collect(toSet());
		HWs.addAll(separateHWs);
		return HWs.stream();
	}
	
	private Map<String, Collection<String>> indexFor(Collection<Definition> definitions,
	                                                 Collection<Function<Definition, Stream<String>>> hwSuppliers) {
		if (definitions.isEmpty() || isCancelled()) return emptyMap();
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> combiner = (m1, m2) -> {
			Maps.mergeLeft(m1, m2, ALLOW_PARALLEL);
		};
		return getStream(definitions, ALLOW_PARALLEL)
				       .collect(HashMap::new,
						       (resultMap, definition) -> {
							       String definedAt = definition.getDefinedVocabula().headWord;
							       Function<String, Collection<String>> keyMapper = key -> new HashSet<>();
							       Collection<String> references = resultMap.computeIfAbsent(definedAt, keyMapper);
							       references.add(definedAt);
							       getStream(hwSuppliers, ALLOW_PARALLEL)
									       .flatMap(supplier -> supplier.apply(definition))
									       .forEach(hw -> {
										       String hwLC = hw.toLowerCase();
										       Collection<String> refs = resultMap.computeIfAbsent(hwLC, keyMapper);
										       refs.add(definedAt);
										       refs.add(hwLC);
										       references.add(hwLC);
									       });
						       },
						       combiner);
	}
	
	private Map<String, Collection<String>> getIndex() {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isCancelled())  Maps.mergeLeft(m1, m2, ALLOW_PARALLEL);
		};
		if (isCancelled()) return emptyMap();
		return getStream(source.filterVocabulas(""), ALLOW_PARALLEL)
				       .map(this::indexFor)
				       .collect(HashMap::new, accumulator, accumulator);
	}
	
	private int getValueSize(Map<String, Collection<String>> m, String k) {
		return Optional.ofNullable(m.get(k)).map(Collection::size).orElse(0);
	}
	
	protected Map<String, Collection<String>> getIndex1() {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isCancelled()) Maps.merge(m1, m2, k -> {
				int size = max(getValueSize(m1, k), getValueSize(m2, k));
				return new HashSet<>(size);
			});
		};
		if (isCancelled()) return emptyMap();
		return getStream(source.filterVocabulas(""), ALLOW_PARALLEL)
				       .map(this::indexFor)
				       .collect(HashMap::new, accumulator, accumulator);
	}
	
	private Map<String, Collection<String>> getIndex2() {
		if (isCancelled()) return emptyMap();
		return getStream(source.filterVocabulas(""), ALLOW_PARALLEL)
				       .map(this::indexFor)
				       .reduce(this::combine)
				       .orElse(emptyMap());
	}
	
	private <K, T> Map<K, Collection<T>> combine(Map<K, Collection<T>> m1, Map<K, Collection<T>> m2) {
		Map<K, Collection<T>> result = Maps.clone(m1, ALLOW_PARALLEL);
		if (isCancelled()) return emptyMap();
		Maps.mergeLeft(result, m2, ALLOW_PARALLEL);
		return result;
	}
	
	private Map<String, Collection<String>> indexFor(Vocabula vocabula) {
		if (isCancelled()) return emptyMap();
		return indexFor(vocabula.getDefinitions(),
				asList(hwFromKnownForms, hwFromDefinition, hwFromSynonyms, hwFromUseCases));
	}
	
}
