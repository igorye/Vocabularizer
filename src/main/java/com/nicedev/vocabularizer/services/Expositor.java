package com.nicedev.vocabularizer.services;

import com.nicedev.vocabularizer.dictionary.*;
import com.nicedev.vocabularizer.dictionary.Dictionary;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.partitioningBy;

public class Expositor {

	public final Language language;
	final private String COLLEGIATE_REQUEST_FMT = "http://www.dictionaryapi.com/api/v1/references/collegiate/xml/%s?key=%s";
	final private String LEARNERS_REQUEST_FMT = "http://www.dictionaryapi.com/api/v1/references/learners/xml/%s?key=%s";
	final private String COLLEGIATE_KEY = "f0a68804-fd23-4eca-ba8b-037f5d156a1f";
	final private String LEARNERS_KEY = "fe9645ff-73f8-4b3f-b09c-dc96b12f767f";
	private String expositorRequestFmt;
	private String key;
	private Collection<String> recentQuerySuggestions = new ArrayList<>();

	final private Dictionary dictionary;
	private XPath xPath;

	public Expositor(String langName, boolean switchedSource) {
		language = new Language(langName);
		this. dictionary = null;
		initKeys(switchedSource);
	}

	public Expositor(Dictionary dictionary, boolean switchedSource) {
		this.dictionary = dictionary;
		language = this.dictionary.language;
//		fixPartsOfSpeech();
		initKeys(switchedSource);
	}

	private void fixPartsOfSpeech() {
		PartOfSpeech pos = language.partsOfSpeech.values().stream().filter(p -> (p.partName.isEmpty())).findFirst().get();
		if (pos != null) {
			System.out.printf("%s%n", pos.partName);
			language.partsOfSpeech.remove(pos.partName);
			language.partsOfSpeech.remove("noun");
			System.out.printf("%s%n", language);
			language.getPartOfSpeech("noun");
			System.out.printf("%s%n", language);
		}
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

	public Collection<Vocabula> getVocabula(String entry, boolean lookupInSuggestions) {
		return getVocabula(entry, PartOfSpeech.ANY, lookupInSuggestions);
	}

	public Collection<Vocabula> getVocabula(String entry, String exactPartOfSpeechName, boolean lookupInSuggestions) {
		String encodedEntry = null;
		try {
			encodedEntry = URLEncoder.encode(entry, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String request = String.format(expositorRequestFmt, encodedEntry != null ? encodedEntry : entry, key);
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document xmlDoc = builder.parse(request);
			return extractVocabula(entry, exactPartOfSpeechName, xmlDoc, lookupInSuggestions);

		} catch (SAXException | ParserConfigurationException | XPathExpressionException | NullPointerException e) {
			System.err.printf("Error retrieving [%s]. Unable to parse  document (%s).%n", entry, e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.printf("Error retrieving [%s]. No response to request (%s)%n", entry, e.getMessage());
		} catch (InterruptedException e) {
			//ignore PronouncingService's existence
		}
		return Collections.emptyList();
	}

	private Collection<Vocabula> extractVocabula(String query, String exactPartOfSpeechName, Document xmlDoc, boolean lookupInSuggestions)
			throws XPathExpressionException, InterruptedException {
		XPathFactory xpFactory = XPathFactory.newInstance();
		xPath = xpFactory.newXPath();
		Node rootNode = (Node) xPath.evaluate("/entry_list", xmlDoc, XPathConstants.NODE);
		Collection<Vocabula> extracted = new HashSet<>();
		recentQuerySuggestions = new ArrayList<>();
		recentQuerySuggestions.clear();
		Vocabula newVoc = null;
		PartOfSpeech partOfSpeech;
		Definition definition;
		//find headwords
		Collection<SearchResult> headWords = findEntries(query, false, rootNode);
		String lastHW = "";
		//retrieve available information for each headword
		Set<SearchResult> partsOfSpeechSR;
		if (!headWords.isEmpty()) {
			for (SearchResult headWordSR : headWords) {
				Collection<String> pronunciationURLs = findPronunciation(headWordSR);
				SearchResult transcription = new SearchResult("", null);
				if (!headWordSR.entry.matches("\\w+(\\s\\w+)+") && headWordSR.perfectMatch)
					transcription = findTranscription(headWordSR.foundAt);
				if (newVoc == null || newVoc != null && !newVoc.headWord.equals(headWordSR.entry)) {
					if(newVoc != null) extracted.add(newVoc);
					newVoc = new Vocabula(headWordSR.entry, language, transcription.entry);
				}
				newVoc.addPronunciations(pronunciationURLs);
				partsOfSpeechSR = new LinkedHashSet<>();
				SearchResult partOfSpeechSR = findPartOfSpeech(headWordSR.foundAt);
				if (!partsOfSpeechSR.contains(partOfSpeechSR)) {
					//part of speech
					partsOfSpeechSR.add(partOfSpeechSR);
					partOfSpeech = new PartOfSpeech(language, partOfSpeechSR.entry);
					//forms
					Collection<String> forms;
					if (headWordSR.perfectMatch && !(forms = findForms(headWordSR.foundAt)).isEmpty() && newVoc != null)
						newVoc.addKnownForms(partOfSpeech, forms);
					//definitions
					Collection<SearchResult> definitions = findDefinitions(headWordSR, partOfSpeechSR);
					if (!definitions.isEmpty())
						for (SearchResult definitionSR : definitions) {
						definition = new Definition(language, newVoc, partOfSpeech, definitionSR.entry);
						//synonyms
						Collection<String> synonyms;
						synonyms = findSynonyms(definitionSR.foundAt);
						definition.addSynonyms(synonyms);
						//usage cases
						Collection<String> useCases = findUseCases(definitionSR.foundAt);
						definition.addUseCases(useCases);
						newVoc.addDefinition(partOfSpeech, definition);
					}
				}
			}
			if(newVoc != null) extracted.add(newVoc);
			return extracted;
		} else {
			collectSuggestions(rootNode);
			if(lookupInSuggestions)
				for(String suggestion: recentQuerySuggestions)
					if(equalIgnoreCaseAndPunctuation(query, suggestion)) return getVocabula(suggestion, false);
			return Collections.emptySet();
		}
	}

	private void printVocabula(String query, String exactPartOfSpeechName, Document xmlDoc)
			throws XPathExpressionException, InterruptedException {
		XPathFactory xpFactory = XPathFactory.newInstance();
		xPath = xpFactory.newXPath();
		Node rootNode = (Node) xPath.evaluate("/entry_list", xmlDoc, XPathConstants.NODE);
		//find headwords
		Collection<SearchResult> headWords = findEntries(query, false, rootNode);
		String lastHW = "";
		//retrieve available information for each headword
		Set<SearchResult> partsOfSpeechSR = null;
		if (!headWords.isEmpty()) {
			for (SearchResult headWordSR : headWords) {
				if (!equalIgnoreCaseAndPunctuation(lastHW, headWordSR.entry)) {
					lastHW = headWordSR.entry;
					Collection<String> pronunciationURLs = findPronunciation(headWordSR);
					Iterator<String> sIt = pronunciationURLs.iterator();
					SearchResult transcription = new SearchResult("", null);
					if (!headWordSR.entry.matches("\\w+(\\s\\w+)+") && headWordSR.perfectMatch) {
						transcription = findTranscription(headWordSR.foundAt);
					}
					System.out.printf("%s [%s]%n", headWordSR.entry, transcription.entry);
					partsOfSpeechSR = new LinkedHashSet<>();
				}
				SearchResult partOfSpeechSR = findPartOfSpeech(headWordSR.foundAt);
				if (!partsOfSpeechSR.contains(partOfSpeechSR)) {
					//part of speech
					partsOfSpeechSR.add(partOfSpeechSR);
					System.out.printf("  :%s%n", partOfSpeechSR.entry);
					//forms
					Collection<String> forms = null;
					if (headWordSR.perfectMatch && !(forms = findForms(headWordSR.foundAt)).isEmpty())
						System.out.printf("    forms: %s%n", forms);
					//definitions
					Collection<SearchResult> definitions = findDefinitions(headWordSR, partOfSpeechSR);
					if (!definitions.isEmpty()) {
						StringBuilder defContent = new StringBuilder();
						for (SearchResult definitionSR : definitions) {
							if (forms != null)
							defContent.append("    - ").append(definitionSR.entry).append("\n");
							//synonyms
							Collection<String> synonyms;
							synonyms = findSynonyms(definitionSR.foundAt);
							int defLen;
							if (!synonyms.isEmpty()) {
								defContent.append("    synonyms: ");
								synonyms.forEach(syn -> defContent.append(syn).append(", "));
								defLen = defContent.length();
								defContent.replace(defLen - 2, defLen, "").append("\n");
							}
							//usage cases
							Collection<String> useCases = findUseCases(definitionSR.foundAt);
							if (!useCases.isEmpty()) {
								defContent.append("      :");
								useCases.forEach(uc -> defContent.append(String.format("\"%s\", ", uc)));
								defLen = defContent.length();
								defContent.replace(defLen - 2, defLen, "\n");
							}
							System.out.print(defContent.toString());
							defContent.delete(0, defContent.length());
						}
					}
				}
			}
		} else {
			collectSuggestions(rootNode);
			System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, recentQuerySuggestions);
		}
	}

	public Collection<String> getRecentSuggestions() {
		return Collections.unmodifiableCollection(recentQuerySuggestions);
	}

	class SearchResult implements Comparable {

		final public String entry;
		final public Node foundAt;
		final public boolean perfectMatch;

		public SearchResult() {
			entry = null;
			foundAt = null;
			perfectMatch = false;
		}

		public SearchResult(String entry, Node foundAt) {
			this(entry, foundAt, true);
		}

		public SearchResult(String entry, Node foundAt, boolean perfectMatch) {
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

			if (!equalIgnoreCaseAndPunctuation(entry, searchResult.entry)) return false;
			return foundAt.equals(searchResult.foundAt);
		}

		@Override
		public int hashCode() {
			int result = entry.hashCode();
			result = 31 * result + foundAt.hashCode();
			return result;
		}

		@Override
		public int compareTo(Object o) {
			SearchResult searchResult = (SearchResult) o;
			return equalIgnoreCaseAndPunctuation(entry, searchResult.entry) ? 0 : entry.compareTo(searchResult.entry);
		}
	}

	public Collection<SearchResult> findEntries(String query, boolean acceptFormRootsEntry, Node rootNode)
			throws XPathExpressionException {
		String[] entryNodeTags = {"ew", "hw", "/in/if", "dro/dre", "uro/ure"};
		String queryLC = query.toLowerCase();
		Set<SearchResult> entriesSR = new LinkedHashSet<>();
		Set<SearchResult> similarsSR = new LinkedHashSet<>();
		for (String entryNode:  entryNodeTags) {
			String expression = String.format("entry/%s", entryNode);
			NodeList nodes = (NodeList) xPath.evaluate(expression, rootNode, XPathConstants.NODESET);
			int nodeCount = nodes.getLength();
			for (int i = 0; i < nodeCount; i++) {
				String currEntry = nodes.item(i).getTextContent().replace("*", "").replace(String.valueOf((char)8211), "-");
				if (equalIgnoreCaseAndPunctuation(currEntry, queryLC) && hasDefinition(nodes.item(i).getParentNode())
						    || acceptFormRootsEntry) {
					entriesSR.add(new SearchResult(currEntry, nodes.item(i).getParentNode()));
				} else if (isSimilar(currEntry, queryLC))
					similarsSR.add(new SearchResult(currEntry, nodes.item(i).getParentNode()));
				if (findForms(nodes.item(i).getParentNode()).contains(queryLC)) {
					entriesSR.add(new SearchResult(queryLC, nodes.item(i).getParentNode(), false));
				}
//				if (!acceptFormRootsEntry && !entriesSR.isEmpty()) continue;
				Collection<String> forms = findForms(nodes.item(i).getParentNode());
				String formEntry = forms.contains(queryLC) ? queryLC : forms.contains(query) ? query : "";
				if(!formEntry.isEmpty()) {
					entriesSR.add(new SearchResult(formEntry, nodes.item(i).getParentNode()));
					break;
				}
			}
		}
		if (entriesSR.isEmpty()) {
			SearchResult entrySR = similarsSR.stream()
					                       .filter(similar -> isSimilar(similar.entry, queryLC))
					                       .findFirst().orElseGet(SearchResult::new);
			if(entrySR.entry != null)
				entriesSR.add(entrySR);
		}
		recentQuerySuggestions.addAll(similarsSR.stream().map(sr -> sr.entry).collect(Collectors.toList()));
		recentQuerySuggestions.addAll(entriesSR.stream().map(sr -> sr.entry).collect(Collectors.toList()));
//		entriesSR.addAll(similarsSR);
		return entriesSR/*.stream().limit(10).collect(Collectors.toCollection(LinkedHashSet::new))*/;
	}

	public SearchResult findTranscription(Node rootNode) throws XPathExpressionException {
		String[] transcriptionTags = {"pr", "vr/pr", "altpr"};
		String transcription = "";
		Node node = rootNode;
		for (String tag: transcriptionTags) {
			node = rootNode;
			switch (((Element)node).getTagName()) {
				case "dre":
				case "ure": node = node.getParentNode();
			}
			//
//			node = (Node) xPath.evaluate(tag, node, XPathConstants.NODE);
//			while (node != null && (transcription = extractNodeContents(node)).isEmpty())
			while (node != null && (transcription = xPath.evaluate(tag, node)).isEmpty())
				node = node.getParentNode();
			if (!transcription.isEmpty()) break;
		}
		return new SearchResult(transcription, node);
	}

	private Collection<String> findPronunciation(SearchResult headWord) throws XPathExpressionException {
		if (!headWord.perfectMatch) return Collections.<String>emptySet();
		NodeList nodes = (NodeList) xPath.evaluate("sound/wav", headWord.foundAt, XPathConstants.NODESET);
		int nNodes = nodes.getLength();
		if (nNodes == 0) return Collections.<String>emptySet();
		Collection<String> sounds = new ArrayList<>();
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

	public SearchResult findPartOfSpeech(Node rootNode) throws XPathExpressionException {
		String partOfSpeechName;
		Node node = rootNode;
		while ((partOfSpeechName = xPath.evaluate("fl", node)).isEmpty() && node.getParentNode() != null)
			node = node.getParentNode();
		if (partOfSpeechName.isEmpty()) {
			partOfSpeechName = xPath.evaluate("//fl", node.getFirstChild());
			node = node.getFirstChild();
		}
		if (partOfSpeechName.isEmpty()) {
			partOfSpeechName = PartOfSpeech.UNDEFINED;
			if (node == null) node = rootNode;
		}
		return new SearchResult(partOfSpeechName, node);
	}

	private Collection<String> findForms(Node node) throws XPathExpressionException {
		String[] entryNodeTags = {"in/if", "gram"};
		Collection<String> knownForms = new ArrayList<>();
		for (String entryNode:  entryNodeTags) {
			String expression = String.format("%s", entryNode);
			NodeList formsList = (NodeList) xPath.evaluate(expression, node, XPathConstants.NODESET);
			for (int i = 0; i < formsList.getLength(); i++)
				Collections.addAll(knownForms, extractNodeContents(formsList.item(i)).split(";") );
		}
		return knownForms;
	}

	private boolean isAFormOf(SearchResult headWord, SearchResult parentNodeEntry, SearchResult partOfSpeech) throws XPathExpressionException {
		return findForms(parentNodeEntry.foundAt).contains(headWord.entry)
				       && findPartOfSpeech(parentNodeEntry.foundAt).entry.equals(partOfSpeech.entry);
	}
	
	public Collection<SearchResult> findDefinitions(SearchResult headWord, SearchResult partOfSpeech) throws XPathExpressionException {
		String[] definitionTags = {"def/dt"};
		Collection<SearchResult> definitionsSR = new LinkedHashSet<>();
		String definition;
		StringBuilder defBuilder = new StringBuilder();
		for(String tag: definitionTags) {
			NodeList defNodes = (NodeList) xPath.evaluate(tag, headWord.foundAt, XPathConstants.NODESET);
			int defCount = defNodes.getLength();
			if(headWord.perfectMatch)
				for (int i = 0; i < defCount; i++) {
					defBuilder.setLength(0);
					Node currNode = defNodes.item(i).getFirstChild();
					if (currNode.getNodeType() != Node.ELEMENT_NODE) {
						String definitionContents = extractNodeContents(defNodes.item(i));
						String[] definitions = definitionContents.split(":");
						boolean closeParenthesis = false;
						for (int j = 0, first = 0; j < definitions.length; j++) {
							boolean emptyDefinition = definitions[j].trim().isEmpty();
							if (emptyDefinition){
								first++;
								continue;
							}
							if(j > first) {
								defBuilder.append(" (=");
								closeParenthesis = true;
							}
							defBuilder.append(definitions[j].trim());
							if(closeParenthesis) {
								defBuilder.append(")");
								closeParenthesis = false;
							}
						}
					}
					SearchResult usageNote = findUsageNote(currNode.getParentNode());
					Node useCasesRoot = currNode.getParentNode();
					if(!usageNote.entry.isEmpty()) {
						if(defBuilder.length() != 0) defBuilder.append(" - ");
						defBuilder.append(usageNote.entry);
						useCasesRoot = usageNote.foundAt;
					}
					definition = defBuilder.toString();
					if (!definition.isEmpty())
						definitionsSR.add(new SearchResult(definition.trim(), useCasesRoot));
					else {
						Collection<String> synonyms = findSynonyms(defNodes.item(i));
						if (!synonyms.isEmpty()) {
							defBuilder.append("see: <b>");
							synonyms.forEach(syn -> defBuilder.append(syn).append(", "));
							int defLen = defBuilder.length();
							definition = defBuilder.replace(defLen - 2, defLen, "</b>").toString();
						}
						if (!definition.isEmpty())
							definitionsSR.add(new SearchResult(definition, headWord.foundAt));
					}
				}
		}
		if (definitionsSR.isEmpty()) {
			Node referenceNode = (Node) xPath.evaluate("cx", headWord.foundAt, XPathConstants.NODE);
			if (referenceNode != null) {
				definition = extractNodeContents(referenceNode);
				definitionsSR.add(new SearchResult(definition, referenceNode));
			}
		}
		if (definitionsSR.isEmpty()) {
			SearchResult parentNodeEntry = getParentNodeEntry(headWord.foundAt);
			if (parentNodeEntry.entry != null) {
				String definitionPrefix = isAFormOf(headWord, parentNodeEntry, partOfSpeech) ? "form of": "see";
				definition = defBuilder.append(String.format("%s <b>", definitionPrefix))
						             .append(parentNodeEntry.entry).append("</b>").toString();
				definitionsSR.add(new SearchResult(definition, headWord.foundAt));
			}
		}
		if (definitionsSR.isEmpty()) {
			Node referenceNode = (Node) xPath.evaluate("dx", headWord.foundAt, XPathConstants.NODE);
			if (referenceNode != null) {
				definition = extractNodeContents(referenceNode);
				definitionsSR.add(new SearchResult(definition, referenceNode));
			}
		}
		return definitionsSR;
	}

	public Collection<String> findSynonyms(Node rootNode) throws XPathExpressionException {
		Collection<String> synonyms = new ArrayList<>();
		NodeList synNodes = (NodeList) xPath.evaluate("sx", rootNode, XPathConstants.NODESET);
		int nNodes = synNodes.getLength();
		for (int i = 0; i < nNodes; i++)
			synonyms.add(synNodes.item(i).getFirstChild().getTextContent());
		return synonyms;
	}

	public Collection<String> findUseCases(Node rootNode) throws XPathExpressionException {
		String[] useCasesTags = {"vi", "snote/vi", "utxt/vi"};
		Collection<String> useCases = new ArrayList<>();
		for(String useCasesTag: useCasesTags) {
			NodeList nodes = (NodeList) xPath.evaluate(useCasesTag, rootNode, XPathConstants.NODESET);
			int nNodes = nodes.getLength();
			for (int i = 0; i < nNodes; i++) {
				String useCase = extractNodeContents(nodes.item(i));
				if (!useCase.isEmpty())
					useCases.add(useCase);
			}
		}
		return useCases;
	}

	private SearchResult findUsageNote(Node rootNode) throws XPathExpressionException {
		Node node = (Node) xPath.evaluate("un", rootNode, XPathConstants.NODE);
		String usageNote = "";
		if (node != null) usageNote = extractNodeContents(node);
		return new SearchResult(usageNote, node);
	}

	private String extractNodeContents(Node node) {
		StringBuilder content = new StringBuilder();
		NodeList children = node.getChildNodes();
		int limit = children.getLength();
		for (int i = 0; i < limit; i++) {
			Node child = children.item(i);
			switch (child.getNodeType()){
				case Node.ELEMENT_NODE:
					switch (((Element)child).getTagName()) {
						case "it":
						case "fw":
						case "sx":
						case "ct":
						case "dxt":
						case "phrase": content.append("<b>").append(extractNodeContents(child)).append("</b>"); break;
						case "wsgram": content.append("(").append(extractNodeContents(child)).append(")"); break;
						case "dx":
						case "un":
						case "vi": i = limit; break;
						case "snote":
							int length = content.toString().length();
							if (length > 2) content.replace(length-1, length, ". ");
							content.append(extractNodeContents(child));
							break;
						default: content.append(extractNodeContents(child).replace("*",""));
					}
					break;
				case Node.TEXT_NODE: content.append(child.getTextContent().replace("*",""));
			}
		}
		return content.toString();
	}

	private SearchResult getParentNodeEntry(Node rootNode) throws XPathExpressionException {
		String[] entryNodeTags = {"ew", "hw", "dro/dre", "uro/ure"};
		while(rootNode.getParentNode() != null) {
			for (String entryNode : entryNodeTags) {
				String expression = String.format("%s", entryNode);
				NodeList nodes = (NodeList) xPath.evaluate(expression, rootNode, XPathConstants.NODESET);
				int nodeCount = nodes.getLength();
				for (int i = 0; i < nodeCount; i++) {
					String currEntry = nodes.item(i).getTextContent().replace("*", "");
					if (!currEntry.isEmpty())
						return new SearchResult(currEntry, rootNode);
				}
			}
			rootNode = rootNode.getParentNode();
		}
		return new SearchResult();
	}

	private boolean hasDefinition(Node rootNode) throws XPathExpressionException {
		int nDefinitions = ((Number) xPath.evaluate("count(def/dt)", rootNode, XPathConstants.NUMBER)).intValue();
		nDefinitions += ((Number) xPath.evaluate("count(def/dt/un)", rootNode, XPathConstants.NUMBER)).intValue();
		//<utxt> means that there are some use cases thus definition will be composed as reference at parent node's entry
		nDefinitions += ((Number) xPath.evaluate("count(utxt)", rootNode, XPathConstants.NUMBER)).intValue();
		nDefinitions += ((Number) xPath.evaluate("count(snote)", rootNode, XPathConstants.NUMBER)).intValue();
		nDefinitions += ((Number) xPath.evaluate("count(cx)", rootNode, XPathConstants.NUMBER)).intValue();
		return nDefinitions != 0;
	}

	public void collectSuggestions(Node sourceNode) throws XPathExpressionException {
		String[] pathsToCollect = {"suggestion", "entry/hw", "entry/ew", "entry/dro/dre", "entry/uro/ure", "entry/cx/ct"};
		Collection<String> strSuggestions = new ArrayList<>();
		for (String sourcePath: pathsToCollect) {
			NodeList suggestions = (NodeList) xPath.evaluate(sourcePath, sourceNode, XPathConstants.NODESET);
			for (int i = 0; i < suggestions.getLength(); i++)
				strSuggestions.add(suggestions.item(i).getFirstChild().getTextContent().replace("*", ""));
		}
		recentQuerySuggestions = strSuggestions;
	}

	//checks ignoring case allowing mismatch at punctuation chars
	private boolean equalIgnoreCaseAndPunctuation(String query, String currEntry) {
		if (query.length() == 1) return query.equalsIgnoreCase(currEntry);
		query = query.replaceAll("[^\\p{L}]","").toLowerCase().replace(" ", "");
		currEntry = currEntry.replaceAll("[^\\p{L}]","").toLowerCase().replace(" ", "");
		return query.equals(currEntry);
	}

	//checks ignoring case allowing mismatch at punctuation chars and partial equivalence
	private boolean isSimilar(String compared, String match) {
		if (compared.isEmpty() || match.isEmpty()) return false;
		String comparedLC = compared.toLowerCase().replaceFirst("[^\\p{L}]","").replace(" ", "");
		String matchLC = match.toLowerCase().replaceFirst("[^\\p{L}]","").replace(" ", "");
		return comparedLC.contains(matchLC);
	}

	private boolean appropriatePartOfSpeech(String partOfSpeechName, String exactPartOfSpeechName) {
		return !language.getPartOfSpeech(partOfSpeechName).partName.equals(PartOfSpeech.UNDEFINED)
				       && (exactPartOfSpeechName.equals(PartOfSpeech.ANY) || partOfSpeechName.equals(exactPartOfSpeechName));
	}

	private Node getRootNode(Node definitionNode) {
		Node node = definitionNode.getParentNode();
		while (node.getParentNode().getParentNode() != null)
			node = node.getParentNode();
		return node;
	}

	public String getRawContents(URL uRequest){
		StringBuilder res = new StringBuilder();
		String line;
		try(BufferedReader responseReader = new BufferedReader(new InputStreamReader(uRequest.openStream()), 5128)) {
			while ((line = responseReader.readLine()) != null){
				res.append(line).append("\n");
			}
			return res.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
