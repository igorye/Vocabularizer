package com.nicedev.vocabularizer.dictionary;

import com.nicedev.util.Exceptions;
import com.nicedev.util.Maps;
import com.nicedev.util.Strings;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;

public class IndexingService extends Service<Map<String, Collection<String>>> {
	
	private Dictionary source;
	private Task<Map<String, Collection<String>>> task;
	private final EventHandler<WorkerStateEvent> succeededHandler;
	private final int INDEXING_ALGORITHM;
	private final boolean ALLOW_PARALLEL;
	private long start;
	private Map<String, Collection<String>> index;
	
	public IndexingService(Dictionary dictionaryToIndex, EventHandler<WorkerStateEvent> succeededHandler,
	                       boolean allowParallel, int indexingAlgorithm) {
		super();
		this.source = dictionaryToIndex;
		this.succeededHandler = succeededHandler;
		this.INDEXING_ALGORITHM = indexingAlgorithm;
		this.ALLOW_PARALLEL = allowParallel;
		index = source.filterHeadwords("", "i").stream()
				        .collect(TreeMap::new, (map, s) -> map.put(s, singleton(s)), Map::putAll);
	}
	
	public Map<String, Collection<String>> getIndex() {
		return Collections.unmodifiableMap(index);
	}
	
	@Override
	protected Task<Map<String, Collection<String>>> createTask() {
		return task = new Task<Map<String, Collection<String>>>() {
			@Override
			protected Map<String, Collection<String>> call() throws Exception {
				Map<String, Collection<String>> index = getIndex(INDEXING_ALGORITHM);
				if (isCancelled()) return Collections.emptyMap();
				return index;
			}
			@Override
			protected void failed() {
				super.failed();
				log("Exception has occurred while removing from index: %s",
						Exceptions.getPackageStackTrace((Exception) getException(), "com.nicedev"));
			}
		};
	}
	
	@Override
	public void restart() {
		log("indexingService: restarting");
		start = System.currentTimeMillis();
		super.restart();
	}
	
	@Override
	public void start() {
		log("indexingService: starting");
		start = System.currentTimeMillis();
		super.start();
	}
	
	@Override
	protected void failed() {
		super.failed();
		log("Exception has occurred while removing from index: %s",
				Exceptions.getPackageStackTrace((Exception) getException(), "com.nicedev"));
	}
	
	@Override
	protected void succeeded() {
		super.succeeded();
		index = getValue();
		log("indexingService: succeeded");
		log("built index in %f (allowParallelStream==%s, indexingAlgorithm==%s",
				(System.currentTimeMillis() - start) / 1000f, ALLOW_PARALLEL, INDEXING_ALGORITHM);
		// should invoke succeedHandler by ourselves to guarantee succeeded() would be executed before user's handler
		succeededHandler.handle(new WorkerStateEvent(this, WorkerStateEvent.WORKER_STATE_SUCCEEDED));
	}
	
	private boolean isCancelled() {
		return task.isCancelled();
	}
	
	// useful part of job
	
	private Map<String, Collection<String>> getIndex(int indexingAlgo) {
		return getIndex(source.filterVocabulas(""), indexingAlgo);
	}
	
	public Map<String, Collection<String>> getIndex(Vocabula vocabula) {
		return getIndex(Collections.singleton(vocabula), INDEXING_ALGORITHM);
	}
	
	private Map<String, Collection<String>> getIndex(Collection<Vocabula> vocabulas) {
		return getIndex(vocabulas, INDEXING_ALGORITHM);
	}
	
	private Map<String, Collection<String>> getIndex(Collection<Vocabula> vocabulas, int indexingAlgo) {
		Map<String, Collection<String>> res;
		switch (indexingAlgo) {
			case 2 : res = getIndex2(vocabulas); break;
			case 3 : res = getIndex3(vocabulas); break;
			default: res = getIndex1(vocabulas);
		}
		return  res;
	}
	
	private final Predicate<String> allMatch = s -> true;
	
	private final Predicate<String> bTagWrapped = s -> s.contains("<b>");
	
	private final Function<Definition, Stream<String>> hwsFromDefinition = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(singleton(def.explanation), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private final Function<Definition, Stream<String>> hwsFromKnownForms = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(def.getDefinedVocabula().getKnownForms(), allMatch, Stream::of);
	};
	
	private final Function<Definition, Stream<String>> hwsFromSynonyms = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(def.getSynonyms(), allMatch, this::extractSynonym);
	};
	
	private Stream<String> extractSynonym(String s) {
		return s.contains("(") ? extract_bTag_Wrapped(s) : Stream.of(s);
	}
	
	private final Function<Definition, Stream<String>> hwsFromUseCases = def -> {
		if (isCancelled()) return Stream.empty();
		return hwCollector(def.getUseCases(), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Stream<String> hwCollector(Collection<String> source,
	                                   Predicate<String> hwMatcher,
	                                   Function<String, Stream<String>> hwExtractor) {
		return source.stream().filter(hwMatcher).flatMap(hwExtractor).filter(Strings::notBlank);
	}
	
	private Stream<String> extract_bTag_Wrapped(String source) {
		Set<String> HWs = new HashSet<>();
		Matcher matcher = Pattern.compile("(?<=^|[\\s-=()])<b>([^<>]+)</b>(?=\\p{Punct}|\\s|$)").matcher(source);
		while (matcher.find()) HWs.add(matcher.group(1).trim());
		return HWs.stream();
	}
	
	private Map<String, Collection<String>> indexFor(Vocabula vocabula) {
		if (isCancelled()) return emptyMap();
		return indexFor(vocabula.getDefinitions(),
				asList(hwsFromKnownForms, hwsFromDefinition, hwsFromSynonyms, hwsFromUseCases));
	}
	
	private Map<String, Collection<String>> indexFor(Collection<Definition> definitions,
	                                                 Collection<Function<Definition, Stream<String>>> hwSuppliers) {
		if (definitions.isEmpty() || isCancelled()) return emptyMap();
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> combiner = (m1, m2) -> {
			Maps.mergeLeft(m1, m2, ALLOW_PARALLEL);
		};
		BiConsumer<Map<String, Collection<String>>, Definition> accumulator = (resultMap, definition) -> {
			String definedAt = definition.getDefinedVocabula().headWord;
			Function<String, Collection<String>> keyMapper = key -> new HashSet<>();
			Collection<String> references = resultMap.computeIfAbsent(definedAt, keyMapper);
			references.add(definedAt);
			getStream(hwSuppliers, ALLOW_PARALLEL)
					.flatMap(supplier -> supplier.apply(definition))
					.forEach(hw -> {
						String fHW = hw.matches("\\p{Lu}+|\\p{Lu}\\p{L}+(\\W\\p{Lu}\\p{L}+)+" +
								                        "|\\p{L}+(\\W\\p{L}*\\W?\\p{Lu}(\\p{L}|\\p{Lu})*)+")
								             ? hw
								             : hw.toLowerCase();
						Collection<String> refs = resultMap.computeIfAbsent(fHW, keyMapper);
						refs.add(definedAt);
						refs.add(fHW);
						references.add(fHW);
					});
		};
		return getStream(definitions, ALLOW_PARALLEL).collect(ConcurrentHashMap::new, accumulator, combiner);
	}
	
	private Map<String, Collection<String>> getIndex1(Collection<Vocabula> vocabulas) {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isCancelled())  Maps.mergeLeft(m1, m2, ALLOW_PARALLEL);
		};
		if (isCancelled()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL)
				       .map(this::indexFor)
				       .collect(ConcurrentHashMap::new, accumulator, accumulator);
	}
	
	private int getCollectionSize(Map<String, Collection<String>> m, String k) {
		return ofNullable(m.get(k)).map(Collection::size).orElse(0);
	}
	
	private Map<String, Collection<String>> getIndex2(Collection<Vocabula> vocabulas) {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isCancelled()) Maps.merge(m1, m2, k -> {
				int size = max(getCollectionSize(m1, k), getCollectionSize(m2, k));
				return new HashSet<>(size);
			});
		};
		if (isCancelled()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL)
				       .map(this::indexFor)
				       .collect(ConcurrentHashMap::new, accumulator, accumulator);
	}
	
	private Map<String, Collection<String>> getIndex3(Collection<Vocabula> vocabulas) {
		if (isCancelled()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL)
				       .map(this::indexFor)
				       .reduce(this::accumulate)
				       .orElse(emptyMap());
	}
	
	private <K, T> Map<K, Collection<T>> accumulate(Map<K, Collection<T>> m1, Map<K, Collection<T>> m2) {
		Map<K, Collection<T>> result = Maps.clone(m1, ALLOW_PARALLEL);
		if (isCancelled()) return emptyMap();
		Maps.mergeLeft(result, m2, ALLOW_PARALLEL);
		return result;
	}
	
	public void alter(Collection<Vocabula> vocabulas) {
		Task<Void> alteringTask = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				Map<String, Collection<String>> addToIndex = getIndex(vocabulas);
				Maps.mergeLeft(index, addToIndex, ALLOW_PARALLEL);
				return null;
			}
		};
		alteringTask.setOnSucceeded(succeededHandler);
		alteringTask.run();
	}
	
	
}
