package com.nicedev.vocabularizer.dictionary.parser;

import com.nicedev.util.Exceptions;
import com.nicedev.util.Html;
import com.nicedev.util.Strings;
import com.nicedev.vocabularizer.dictionary.model.Definition;
import com.nicedev.vocabularizer.dictionary.model.Language;
import com.nicedev.vocabularizer.dictionary.model.PartOfSpeech;
import com.nicedev.vocabularizer.dictionary.model.Vocabula;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.xml.xpath.XPathConstants.*;
import static org.w3c.dom.Node.ELEMENT_NODE;
import static org.w3c.dom.Node.TEXT_NODE;


public class MerriamWebsterParser {

	static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final String PART_OF_SPEECH_CONTRACTION = "contraction";
	private static final String PART_OF_SPEECH_VERB = "verb";
	private static final String DASH = String.valueOf((char) 8211);
	private static final String HYPHEN = "-";
	private static final String ASTERISK = "*";
	private final Language language;
	public final int priority;
	private static int instantiated = 0;
	final private static String COLLEGIATE_REQUEST_FMT = "http://www.dictionaryapi.com/api/v1/references/collegiate/xml/%s?key=%s";
	final private static String LEARNERS_REQUEST_FMT = "http://www.dictionaryapi.com/api/v1/references/learners/xml/%s?key=%s";
	final private static String COLLEGIATE_KEY = "f0a68804-fd23-4eca-ba8b-037f5d156a1f";
	final private static String LEARNERS_KEY = "fe9645ff-73f8-4b3f-b09c-dc96b12f767f";
	private String expositorRequestFmt;
	private String key;
	private Set<String> recentQuerySuggestions = new LinkedHashSet<>();

	private XPath xPath;

	public MerriamWebsterParser(String langName, boolean switchedSource) {
		language = new Language(langName);
		initKeys(switchedSource);
		priority = instantiated++;
	}

	public MerriamWebsterParser(com.nicedev.vocabularizer.dictionary.model.Dictionary dictionary,
	                            boolean switchedSource) {
//		language = new Language("english", "en");
		language = dictionary.language;
		initKeys(switchedSource);
		priority = instantiated++;
	}

	private void initKeys(boolean switchedSource) {
		if (switchedSource) {
			expositorRequestFmt = COLLEGIATE_REQUEST_FMT;
			key = COLLEGIATE_KEY;
		} else {
			expositorRequestFmt = LEARNERS_REQUEST_FMT;
			key = LEARNERS_KEY;
		}
	}

	public Set<Vocabula> getVocabula(String entry, boolean acceptSimilar) {
		Optional<String> encodedEntry;
		recentQuerySuggestions.clear();
		String filteredEntry = String.join(" ", entry.split("[\\\\/?&]")).replaceAll("\\s{2,}", " ").trim();
		encodedEntry = Optional.of(URLEncoder.encode(filteredEntry, StandardCharsets.UTF_8));
		String requestUrl = String.format(expositorRequestFmt, encodedEntry.orElse(entry), key);
		requestUrl = checkURL(requestUrl);
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document xmlDoc = builder.parse(requestUrl);
			return extractVocabula(entry, xmlDoc, acceptSimilar);

		} catch (SAXException | ParserConfigurationException | XPathExpressionException | NullPointerException e) {
			LOGGER.error("Error occured while retrieving [{}]. Unable to parse  document. {}", entry,
			             Exceptions.getPackageStackTrace(e, "com.nicedev"));
		} catch (IOException e) {
			LOGGER.error("Error occured while retrieving [{}]. No response to request ({} {})",
						 entry, e.getMessage(), e.getCause());
		}
		return Collections.emptySet();
	}

	private String checkURL( String requestUrl ) {
		HttpURLConnection conn = null;
		try {
			URL validated = new URL(requestUrl);
			conn = (HttpURLConnection) validated.openConnection();
			conn.connect();
			if (conn.getResponseCode() != 200) {
				return Optional.ofNullable(conn.getHeaderField("Location")).orElse(requestUrl);
			}
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		} finally {
			if (nonNull(conn)) conn.disconnect();
		}
		return requestUrl;
	}

	private static class SearchResult implements Comparable<SearchResult> {
		final String entry;
		final Node foundAt;
		final boolean perfectMatch;

		SearchResult(String entry, Node foundAt) {
			this(entry, foundAt, true);
		}

		SearchResult(String entry, Node foundAt, boolean perfectMatch) {
			this.entry = entry;
			this.foundAt = foundAt;
			this.perfectMatch = perfectMatch;
		}

		public String toString() {
			return String.format("%s at %s", entry, foundAt.getAttributes().item(0));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			SearchResult searchResult = (SearchResult) o;

			return entry.equals(searchResult.entry) && foundAt.equals(searchResult.foundAt);
		}

		@Override
		public int hashCode() {
			int result = entry.hashCode();
			result = 31 * result + foundAt.hashCode();
			return result;
		}

		public int compareTo(SearchResult other) {
			return entry.compareTo(other.entry);
		}

		boolean sameOrigin(SearchResult other) {
			return foundAt == other.foundAt;
		}

	}

	private Set<Vocabula> extractVocabula(String query, Document xmlDoc, boolean acceptSimilar)
			throws XPathExpressionException {
		XPathFactory xPathFactory = XPathFactory.newInstance();
		xPath = xPathFactory.newXPath();
		Node rootNode = (Node) xPath.evaluate("/entry_list", xmlDoc, NODE);
		Set<Vocabula> extracted = new HashSet<>();
		Optional<Vocabula> optNewVocabula = Optional.empty();
		Set<SearchResult> headWordsSR = findHeadwordsSR(query, rootNode, acceptSimilar);
		if (!headWordsSR.isEmpty()) {
			// retrieve available information for each headword
			for (SearchResult headWordSR : headWordsSR) {
				Set<String> partsOfSpeech = new HashSet<>();
				List<String> pronunciationURLs = findPronunciation(headWordSR);
				SearchResult transcription = findTranscriptionSR(headWordSR.foundAt);
				if (isNewVocabulaProceeding(optNewVocabula, headWordSR)) {
					//optNewVocabula.ifPresent(vocabula -> { if (!vocabula.getDefinitions().isEmpty()) extracted.add(vocabula); });
					optNewVocabula.ifPresent(extracted::add);
					optNewVocabula = Optional.of(new Vocabula(headWordSR.entry, language, transcription.entry));
				}
				if (!transcription.entry.isEmpty())
					extracted.stream()
							.filter(v -> v.getTranscription().isEmpty() && v.headWord.equalsIgnoreCase(headWordSR.entry))
							.forEach(v -> v.setTranscription(transcription.entry));
				optNewVocabula.ifPresent(vocabula -> {
					if (!pronunciationURLs.isEmpty()) vocabula.addPronunciations(pronunciationURLs);
					if (vocabula.getTranscription().isEmpty() && !transcription.entry.isEmpty())
						vocabula.setTranscription(transcription.entry);
				});
				SearchResult partOfSpeechSR = findPartOfSpeechSR(headWordSR);
				if (!partsOfSpeech.contains(partOfSpeechSR.entry)) {
					Collection<SearchResult> definitions = findDefinitionsSR(headWordSR, partOfSpeechSR);
					if (isContraction(partOfSpeechSR, definitions))
						partOfSpeechSR = new SearchResult(PART_OF_SPEECH_CONTRACTION, partOfSpeechSR.foundAt);
					boolean headwordIsAVerbForm = definitions.stream().map(sr -> sr.entry).allMatch(s -> s.contains("tense"));
					PartOfSpeech partOfSpeech = headwordIsAVerbForm
							                            ? language.getPartOfSpeech(PART_OF_SPEECH_VERB)
							                            : language.getPartOfSpeech(partOfSpeechSR.entry);
					//find known forms
					if (headWordSR.perfectMatch) {
						Collection<String> forms = findForms(headWordSR.foundAt);
						// due to invalid <gram>'s relative position in document check for correctnes of form's parsing
						if (!forms.isEmpty() && !forms.contains(partOfSpeech.partName))
							optNewVocabula.ifPresent(vocabula -> vocabula.addKnownForms(partOfSpeech, forms));
					}
					//process definitions
					if (!definitions.isEmpty() && optNewVocabula.isPresent()) {
						Vocabula vocabula = optNewVocabula.get();
						definitions.stream()
								.map(defSR -> new Object[] { defSR.foundAt,
								                             new Definition(language, vocabula, partOfSpeech, defSR.entry) })
								.forEach(tuple -> {
									Node searchNode = (Node) tuple[0];
									Definition definition = (Definition) tuple[1];
									definition.addSynonyms(findSynonyms(searchNode));
									definition.addUseCases(findUseCases(searchNode));
									vocabula.addDefinition(partOfSpeech, definition);
								});

					}
					//update list of accepted parts of speech
					partsOfSpeech.add(partOfSpeech.partName);
				}
			}
			optNewVocabula.ifPresent(extracted::add);
			if (!extracted.isEmpty()) return extracted;
		}
		collectSuggestions(rootNode);
		if (acceptSimilar) {
			for (String suggestion : recentQuerySuggestions)
				if (Strings.equalIgnoreCaseAndPunct(query, suggestion))
					return extractVocabula(suggestion, xmlDoc, false);
		}
		return Collections.emptySet();
	}

	private boolean isContraction(SearchResult partOfSpeechSR, Collection<SearchResult> definitions) {
		return partOfSpeechSR.entry.equals(PartOfSpeech.UNDEFINED)
				       && definitions.size() == 1
				       && definitions.stream().anyMatch(def -> def.entry.contains(PART_OF_SPEECH_CONTRACTION));
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private boolean isNewVocabulaProceeding(Optional<Vocabula> optNewVocabula, SearchResult headWordSR) {
		return optNewVocabula.isEmpty() || optNewVocabula.filter(
				nV -> !nV.headWord.equals(headWordSR.entry)).isPresent();
	}

	public Set<String> getRecentSuggestions() {
		return Collections.unmodifiableSet(recentQuerySuggestions);
	}

	private Set<SearchResult> findHeadwordsSR(String query, Node rootNode, boolean acceptSimilar)
			throws XPathExpressionException {
		String[] entryNodeTags = { "ew", "hw", "in/if", "dro/dre", "dro/vr/va", "dro/drp", "uro/ure", "uro/vr/va" };
		String queryLC = query.toLowerCase();
		Set<SearchResult> entriesSR = new LinkedHashSet<>();
		Set<SearchResult> similarsSR = new LinkedHashSet<>();
		String entryNodeTag;
		for (int j = 0; j < entryNodeTags.length; j++) {
			entryNodeTag = entryNodeTags[j];
			String expression = String.format("entry/%s", entryNodeTag);
			NodeList nodes = (NodeList) xPath.evaluate(expression, rootNode, NODESET);
			int nodeCount = nodes.getLength();
			for (int i = 0; i < nodeCount; i++) {
				boolean entryAccepted = false;
				String currEntry = extractNodeContents(nodes.item(i));
				Node sourceNode = getSourceNode(nodes.item(i), entryNodeTag);
				if (currEntry.equals(query) || isEntryAcceptable(acceptSimilar, query, currEntry, sourceNode)) {
					entriesSR.add(new SearchResult(currEntry, sourceNode));
					entryAccepted = true;
				} else if (Strings.isSimilar(currEntry, query)) {
					similarsSR.add(new SearchResult(currEntry, sourceNode));
				}
				if (!entryAccepted && findForms(nodes.item(i).getParentNode()).contains(query))
					findFormsSR(nodes.item(i).getParentNode())
							.stream()
							.filter(sr -> sr.entry.equals(queryLC))
							.findFirst()
							.ifPresent(entriesSR::add);
			}
			if (j == 0 && !entriesSR.isEmpty()) j++;
		}
		if (entriesSR.isEmpty() && acceptSimilar) {
			Comparator<SearchResult> hasDefinitionFirst = (sr1, sr2) -> {
				if (hasDefinition(sr1.foundAt) && !hasDefinition(sr2.foundAt)) return -1;
				if (!hasDefinition(sr1.foundAt) && hasDefinition(sr2.foundAt)) return 1;
				return sr1.compareTo(sr2);
			};
			Optional<SearchResult> entrySR = similarsSR.stream().min(hasDefinitionFirst);
			entrySR.ifPresent(entriesSR::add);
		}
		return entriesSR;
	}

	private boolean isEntryAcceptable(boolean acceptSimilar, String query, String currEntry, Node sourceNode) {
		return acceptSimilar
				       && (Strings.equalIgnoreCaseAndPunct(currEntry, query)
						           || Strings.partEquals(currEntry, query))
				       && hasDefinition(sourceNode);
	}

	private Node getSourceNode(Node parentNode, String entryNodeTag) {
		Node node = parentNode;
		int limit = entryNodeTag.endsWith("/va") ? 2 : 1;
		for (int i = 0; i < limit; i++)
			node = node.getParentNode();
		return node;
	}

	private SearchResult findTranscriptionSR(Node rootNode) throws XPathExpressionException {
		String[] transcriptionTags = { "pr", "vr/pr", "altpr" };
		String transcription = "";
		Node node = rootNode;
		for (String tag : transcriptionTags) {
			node = rootNode;
			switch (((Element) node).getTagName()) {
				case "dre":
				case "ure":
					node = node.getParentNode();
			}
			transcription = xPath.evaluate(tag, node);
			if (!transcription.isEmpty()) break;
		}
		return new SearchResult(transcription, node);
	}

	private List<String> findPronunciation(SearchResult headWord) throws XPathExpressionException {
		if (!headWord.perfectMatch) return Collections.emptyList();
		NodeList nodes = (NodeList) xPath.evaluate("sound/wav", headWord.foundAt, NODESET);
		int nNodes = nodes.getLength();
		if (nNodes == 0) return Collections.emptyList();
		List<String> sounds = new ArrayList<>();
		String urlFmt = "http://media.merriam-webster.com/soundc11/%s/%s";
		for (int i = 0; i < nNodes; i++) {
			String fileName = nodes.item(i).getTextContent();
			String fileNamePrefix = fileName.startsWith("bix")
					                        ? "bix"
					                        : fileName.startsWith("gg") ? "gg" : fileName.substring(0, 1);
			String dir = fileNamePrefix.matches("[\\p{Digit}]") ? "number" : fileNamePrefix;
			sounds.add(String.format(urlFmt, dir, fileName));
		}
		return sounds;
	}

	private SearchResult findPartOfSpeechSR(SearchResult headwordSR) throws XPathExpressionException {
		//String partOfSpeechName = PartOfSpeech.UNDEFINED;
		String partOfSpeechName = "";
		Node node = headwordSR.foundAt;
		String[] pOSTags = { "fl", "gram", "def/gram" };
		for (String pOSTag : pOSTags) {
			partOfSpeechName = xPath.evaluate(String.format("%s", pOSTag), node);
			if (!partOfSpeechName.isEmpty()) return new SearchResult(partOfSpeechName, node);
		}
		// accept parent's node part of speech if headword is not a collocation
		if (!headwordSR.entry.contains(" "))
			while ((partOfSpeechName = xPath.evaluate("fl", node)).isEmpty()
					       && !node.getParentNode().getNodeName().equals("entry_list"))
				node = node.getParentNode();
		if (partOfSpeechName.isEmpty()) {
			if (node == null) node = headwordSR.foundAt;
			partOfSpeechName = PartOfSpeech.UNDEFINED;
		}
		return new SearchResult(partOfSpeechName, node);
	}

	private List<String> findForms(Node node) throws XPathExpressionException {
		String[] entryNodeTags = { "in/if", "def/gram", "vr/va", "def/svr/va" };
		List<String> knownForms = new ArrayList<>();
		for (String entryNode : entryNodeTags) {
			String expression = String.format("%s", entryNode);
			NodeList formsList = (NodeList) xPath.evaluate(expression, node, NODESET);
			for (int i = 0; i < formsList.getLength(); i++)
				knownForms.addAll(Stream.of(extractSeparateForms(formsList.item(i)))
						                  .map(String::trim).collect(toList()));
			if (!knownForms.isEmpty()) return knownForms;
		}
		return Collections.emptyList();
	}

	private String[] extractSeparateForms(Node node) {
		return extractNodeContents(node).replace("(also )|(or )", "").split(";");
	}

	private List<SearchResult> findFormsSR(Node node) throws XPathExpressionException {
		String[] formNodeTags = { "in/if", "def/gram", "vr/va", "def/svr/va" };
		List<SearchResult> knownFormsSR = new ArrayList<>();
		for (String entryNode : formNodeTags) {
			String expression = String.format("%s", entryNode);
			NodeList formsList = (NodeList) xPath.evaluate(expression, node, NODESET);
			for (int i = 0; i < formsList.getLength(); i++) {
				int finalI = i;
				Stream.of(extractSeparateForms(formsList.item(i)))
						.forEach(s -> knownFormsSR.add(new SearchResult(s, formsList.item(finalI).getParentNode())));
			}
			if (!knownFormsSR.isEmpty()) return knownFormsSR;
		}
		return knownFormsSR;
	}

	private boolean isAFormOf(SearchResult headWord, SearchResult parentNodeEntry, SearchResult partOfSpeech)
			throws XPathExpressionException {
		return findForms(parentNodeEntry.foundAt).contains(headWord.entry)
				       && findPartOfSpeechSR(parentNodeEntry).entry.equals(partOfSpeech.entry);
	}

	private String getKindOfForm(SearchResult parentNodeEntry) throws XPathExpressionException {
		String[] formKindNodeTags = { "cx/cl"/*, "in/il"*/ };
		String kindOfForm = "";
		for (String formKindTag : formKindNodeTags) {
			kindOfForm = xPath.evaluate(formKindTag, parentNodeEntry.foundAt);
			if (!kindOfForm.isEmpty()) break;
		}
		return kindOfForm.isEmpty() ? Definition.EXPLANATION_PREFIX_FORM : kindOfForm;
	}

	private Set<SearchResult> findDefinitionsSR(SearchResult headWordSR, SearchResult partOfSpeech)
			throws XPathExpressionException {
		String[] definitionTags = { "cx", "def/dt", "def/dt/snote" };
		Set<SearchResult> definitionsSR = new LinkedHashSet<>();
		String definition;
		StringBuilder defBuilder = new StringBuilder();
		for (String tag : definitionTags) {
			NodeList defNodes = (NodeList) xPath.evaluate(tag, headWordSR.foundAt, NODESET);
			int defCount = defNodes.getLength();
			if (headWordSR.perfectMatch)
				for (int i = 0; i < defCount; i++) {
					defBuilder.setLength(0);
					Node currNode = defNodes.item(i).getFirstChild();
					String definitionContents = extractNodeContents(defNodes.item(i)).replaceAll("</b> <b>", "</b>, <b>");
					String[] definitions = definitionContents.split(":");
					for (int j = 0, first = 0; j < definitions.length; j++) {
						if (definitions[j].trim().isEmpty()) {
							first++;
						} else {
							if (j > first) defBuilder.append("; ");
							defBuilder.append(definitions[j].trim());
						}
					}
					Node useCasesRoot = currNode.getParentNode();
					SearchResult usageNote = findUsageNote(currNode.getParentNode());
					if (!usageNote.entry.isEmpty()) {
						if (defBuilder.length() != 0) defBuilder.append(" - ");
						defBuilder.append(usageNote.entry);
					}
					definition = toSplitBTag(defBuilder.toString());
					if (!definition.isEmpty()) {
						definitionsSR.add(new SearchResult(definition.trim(), useCasesRoot));
					} else {
						Collection<String> synonyms = findSynonyms(defNodes.item(i));
						if (!synonyms.isEmpty()) {
							defBuilder.append(synonyms.stream().map(s -> Html.wrapInTag(s, s, "b", "")).collect(joining(", ")));
							definitionsSR.add(new SearchResult(defBuilder.toString(), defNodes.item(i)));
						}
					}
					// look for a reference at definition section
					if (definitionsSR.isEmpty()) definitionsSR.addAll(evaluateDxNode(defNodes.item(i)));
				}
		}
		// look for a reference at entry section
		if (definitionsSR.isEmpty()) definitionsSR.addAll(evaluateDxNode(headWordSR.foundAt));
		// compose definition as a reference to parent node's headword
		if (definitionsSR.isEmpty()) {
			Optional<SearchResult> parentNodeEntryOSR = getParentNodeEntry(headWordSR.foundAt);
			if (parentNodeEntryOSR.isPresent()) {
				SearchResult parentNodeEntrySR = parentNodeEntryOSR.get();
				String definitionPrefix = isAFormOf(headWordSR, parentNodeEntrySR, partOfSpeech)
						                          ? getKindOfForm(parentNodeEntrySR)
						                          : "";
				Node foundAt = headWordSR.sameOrigin(partOfSpeech)
						               ? headWordSR.foundAt
						               : parentNodeEntrySR.foundAt;
				definition = defBuilder.append(String.format("%s <b>", definitionPrefix))
						             .append(parentNodeEntrySR.entry).append("</b>").toString();
				if (foundAt == headWordSR.foundAt) {
					definition = definition.concat(String.format("[: %s]", findPartOfSpeechSR(parentNodeEntrySR).entry));
				}
				definitionsSR.add(new SearchResult(definition, foundAt));
			}
		}
		return definitionsSR;
	}

	// evaluate particular sub-node of the given node
	private List<SearchResult> evaluateDxNode(Node root)
			throws XPathExpressionException {
		List<SearchResult> result = new ArrayList<>();
		Optional<Node> evalNode = Optional.ofNullable((Node) xPath.evaluate("dx", root, NODE));
		evalNode.ifPresent(node -> result.add(new SearchResult(extractNodeContents(node), node)));
		return result;
	}

	private Set<String> findSynonyms(Node rootNode) {
		Set<String> synonyms = new HashSet<>();
		try {
			NodeList synNodes = (NodeList) xPath.evaluate("sx", rootNode, NODESET);
			int nNodes = synNodes.getLength();
			for (int i = 0; i < nNodes; i++)
				synonyms.add(extractNodeContents(synNodes.item(i)));
			if (synonyms.isEmpty() && !rootNode.getNodeName().equals("dt") && rootNode.getParentNode() != null)
				return findSynonyms(rootNode.getParentNode());
		} catch (XPathExpressionException e) {
			LOGGER.error("Failed to find synonyms {}", Exceptions.getPackageStackTrace(e, "com.nicedev"));
		}
		return synonyms;
	}

	private Set<String> findUseCases(Node rootNode) {
		String[] useCasesTags = { "vi", "un/vi", "snote/vi", "utxt/vi" };
		Set<String> useCases = new LinkedHashSet<>();
		for (String useCasesTag : useCasesTags)
			try {
				NodeList ucNodes = (NodeList) xPath.evaluate(useCasesTag, rootNode, NODESET);
				IntStream.range(0, ucNodes.getLength())
						.mapToObj(i -> extractNodeContents(ucNodes.item(i)))
						.filter(Strings::notBlank)
						.map(this::toSplitBTag)
						.forEach(useCases::add);
			} catch (XPathExpressionException e) {
				LOGGER.error("Failed to find usecases. {}", Exceptions.getPackageStackTrace(e, "com.nicedev"));
			}
		return useCases;
	}

	// transform collocation embraced in <b>...</b> into separate "<b>"-embraced tokens,
	// e.g. "<b>tok1, tok2, tok3</b>" -> "<b>tok1</b>, <b>tok2</b>, <b>tok3</b>"
	private String toSplitBTag(String s) {
		return Html.splitTagContents(s, "b", "[,;] ");
	}

	private SearchResult findUsageNote(Node rootNode) throws XPathExpressionException {
		Optional<Node> node = Optional.ofNullable((Node) xPath.evaluate("un", rootNode, NODE));
		String usageNote = node.map(this::extractNodeContents).orElse("");
		return new SearchResult(usageNote, node.orElse(rootNode));
	}

	private String extractNodeContents(Node node) {
		StringBuilder content = new StringBuilder();
		NodeList children = node.getChildNodes();
		int limit = children.getLength();
		for (int i = 0; i < limit; i++) {
			Node child = children.item(i);
			switch (child.getNodeType()) {
				case ELEMENT_NODE:
					switch (((Element) child).getTagName()) {
						case "sx":
						case "ctn":
							break;
						case "it":
						case "fw":
						case "ct":
						case "dxt":
						case "phrase":
							content.append("<b>").append(extractNodeContents(child)).append("</b>");
							break;
						case "sxn":
							int index = content.lastIndexOf(" ", 2);
							content.insert(index != -1 ? index : content.length() - 1, "");
							content.append("(definition ").append(extractNodeContents(child)).append(")");
							break;
						case "wsgram":
							content.append("(").append(extractNodeContents(child)).append(")");
							break;
						case "dx":
						case "un":
						case "vi":
						case "snote":
							i = limit;
							break;
						/*case "snote":
							int length = content.toString().length();
							if (length > 2) content.replace(length - 1, length, ". ");
							content.append(extractNodeContents(child));
							break;*/
						default:
							content.append(extractNodeContents(child));
					}
					break;
				case TEXT_NODE:
					content.append(child.getTextContent());
			}
		}
		return content.toString().replace(DASH, HYPHEN).replace(ASTERISK, "").replaceAll("[\n\r]", "");
	}

	private Optional<SearchResult> getParentNodeEntry(Node rootNode) throws XPathExpressionException {
		String[] entryTags = { "ew", "hw", "dro/dre", "uro/ure" };
		Node parentNode = rootNode;
		String parentEntry = "";
		while (parentNode.getParentNode() != null) {
			for (String entryNode : entryTags) {
				String expression = String.format("%s", entryNode);
				NodeList nodes = (NodeList) xPath.evaluate(expression, parentNode, NODESET);
				int nodeCount = nodes.getLength();
				for (int i = 0; i < nodeCount; i++) {
					String currEntry = extractNodeContents(nodes.item(i));
					if (parentEntry.isEmpty()) parentEntry = currEntry;
					if (!currEntry.isEmpty() && nodes.item(i).getParentNode() == rootNode.getParentNode())
						return Optional.of(new SearchResult(currEntry, parentNode));
				}
			}
			parentNode = parentNode.getParentNode();
		}
		return Optional.empty();
	}

	private boolean hasDefinition(Node rootNode) {
		String[] nodeTags = { "def/dt", "def/dt/un", "utxt", "snote", "cx", "dx" };
		try {
			for (String nodeTag : nodeTags) {
				int nDefinitions = ((Number) xPath.evaluate(String.format("count(%s)", nodeTag), rootNode, NUMBER)).intValue();
				if (nDefinitions > 0)
					return true;
			}
		} catch (XPathExpressionException e) {
			LOGGER.error("Failed to find definitions {} {}", e, Exceptions.getPackageStackTrace(e, "com.nicedev"));
		}
		return false;
	}

	// collect available headwords of the document in case we couldn't find any match
	private void collectSuggestions(Node sourceNode) throws XPathExpressionException {
		String[] pathsToCollect = { "suggestion", "entry/hw", "entry/ew", "entry/dro/dre", "entry/uro/ure", "entry/cx/ct",
		                            "entry/in/if" };
		Set<String> strSuggestions = new LinkedHashSet<>();
		for (String sourcePath : pathsToCollect) {
			NodeList suggestions = (NodeList) xPath.evaluate(sourcePath, sourceNode, NODESET);
			for (int i = 0; i < suggestions.getLength(); i++)
				if (sourcePath.equals(pathsToCollect[0]) || hasDefinition(suggestions.item(i).getParentNode()))
					strSuggestions.add(extractNodeContents(suggestions.item(i)));
		}
		recentQuerySuggestions = strSuggestions;
	}

	@SuppressWarnings("unused")
	private boolean appropriatePartOfSpeech(String partOfSpeechName, String exactPartOfSpeechName) {
		return !language.getPartOfSpeech(partOfSpeechName).partName.equals(PartOfSpeech.UNDEFINED)
				       && (exactPartOfSpeechName.equals(PartOfSpeech.ANY) || partOfSpeechName.equals(exactPartOfSpeechName));
	}

	@SuppressWarnings("unused")
	private Node getRootNode(Node definitionNode) {
		Node node = definitionNode.getParentNode();
		while (node.getParentNode().getParentNode() != null)
			node = node.getParentNode();
		return node;
	}

	@SuppressWarnings("unused")
	public String getRawContents(URL uRequest) {
		StringBuilder res = new StringBuilder();
		String line;
		try (BufferedReader responseReader = new BufferedReader(new InputStreamReader(uRequest.openStream()), 5128)) {
			while ((line = responseReader.readLine()) != null) {
				res.append(line).append("\n");
			}
			return res.toString();
		} catch (IOException e) {
			LOGGER.error("Error has occurred while retrieving contents at {}.\n{}. {}",
			             uRequest, e, Exceptions.getPackageStackTrace(e, "com.nicedev"));
		}
		return null;
	}

}
