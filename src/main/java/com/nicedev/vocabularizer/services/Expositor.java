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

import static java.util.stream.Collectors.partitioningBy;

public class Expositor {

	public final Language language;
	final private String collegiateRequestFmt = "http://www.dictionaryapi.com/api/v1/references/collegiate/xml/%s?key=%s";
	final private String learnersRequestFmt = "http://www.dictionaryapi.com/api/v1/references/learners/xml/%s?key=%s";
	final private String collegiateKey = "f0a68804-fd23-4eca-ba8b-037f5d156a1f";
	final private String learnersKey = "fe9645ff-73f8-4b3f-b09c-dc96b12f767f";
	private String expositorRequestFmt;
	private String key;
	private SpellingService spellingService;

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

	public void finalize() {
		try {
			super.finalize();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
		spellingService.release(0);
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
		spellingService = new SpellingService(5, false);
		if (switchedSource) {
			expositorRequestFmt = collegiateRequestFmt;
			key = collegiateKey;
		} else {
			expositorRequestFmt = learnersRequestFmt;
			key = learnersKey;
		}
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

	public Vocabula getVocabula(String entry) {
		return getVocabula(entry, PartOfSpeech.ANY);
	}

	public Vocabula getVocabula(String entry, String exactPartOfSpeechName) {
		String encodedEntry = null;
		try {
			encodedEntry = URLEncoder.encode(entry, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String request = String.format(expositorRequestFmt, encodedEntry != null ? encodedEntry : entry, key).toString();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document xmlDoc = builder.parse(request);
			return extractVocabula(entry, exactPartOfSpeechName, xmlDoc);

		} catch (SAXException | ParserConfigurationException | XPathExpressionException | NullPointerException e) {
			System.err.printf("Error retrieving [%s]. Unable to parse  document (%s).%n", entry, e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.printf("Error retrieving [%s]. No response to request (%s)%n", entry, e.getMessage());
		} catch (InterruptedException e) {
			//ignore SpellingService's existence
		}
		return null;
	}

	private Vocabula extractVocabula(String query, String exactPartOfSpeechName, Document xmlDoc) throws XPathExpressionException, InterruptedException {
		XPathFactory xpFactory = XPathFactory.newInstance();
		xPath = xpFactory.newXPath();
		Node rootNode = (Node) xPath.evaluate("/entry_list", xmlDoc, XPathConstants.NODE);
		//find headwords
		Collection<SearchResult> headWords = findEntries(query, false, rootNode);
		String lastHW = "";
		String lastPoS = "";
		//retrieve available information for each headword
		Set<SearchResult> partsOfSpeech = null;
		if (!headWords.isEmpty())
			for(SearchResult headWord: headWords) {
				if (!equalIgnoreCaseAndPunctuation(lastHW, headWord.entry)) {
					lastHW = headWord.entry;
					Collection<String> spellURL = findSpelling(headWord.foundAt);
					Iterator<String> sIt = spellURL.iterator();
					if (sIt.hasNext()) spellingService.spell(sIt.next(), 500);
					else spellingService.spell(headWord.entry, 500);
					SearchResult transcription =  findTranscription(headWord.foundAt);
					System.out.printf("%s [%s]%n", headWord.entry, transcription.entry);
					partsOfSpeech = new LinkedHashSet<>();
				}
				SearchResult partOfSpeech = findPartOfSpeech(headWord.foundAt);
				if (!partsOfSpeech.contains(partOfSpeech)) {
					//part of speech
					partsOfSpeech.add(partOfSpeech);
					System.out.printf("  :%s%n", partOfSpeech.entry);
					//forms
					Collection<String> forms = findForms(headWord.foundAt);
					if (!forms.isEmpty())
						System.out.printf("    forms: %s%n", forms);
					//definitions
					Collection<SearchResult> definitions = findDefinitions(headWord.foundAt);
					if (!definitions.isEmpty()) {
						StringBuilder defContent = new StringBuilder();
						for (SearchResult definition : definitions) {
							defContent.append("    - ").append(definition.entry).append("\n");
							//synonyms
							Collection<String> synonyms;
							synonyms = findSynonyms(definition.foundAt);
							int defLen;
							if (!synonyms.isEmpty()) {
								defContent.append("    synonyms: ");
								synonyms.forEach(syn -> defContent.append(syn).append(", "));
								defLen = defContent.length();
								defContent.replace(defLen - 2, defLen, "").append("\n");
							}
							//usage cases
							Collection<String> useCases = findUseCases(definition.foundAt);
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
		else {
			System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, collectSuggestions(rootNode));
			return null;
		}
		return null;
	}

	private Collection<String> findSpelling(Node rootNode) throws XPathExpressionException {
		NodeList nodes = (NodeList) xPath.evaluate("sound/wav", rootNode, XPathConstants.NODESET);
		int nNodes = nodes.getLength();
		if (nNodes == 0) return Collections.<String>emptySet();
		Collection<String> sounds = new ArrayList<>();
		String urlFmt = "http://media.merriam-webster.com/soundc11/%s/%s";
		for (int i = 0; i < nNodes; i++) {
			String fileName = nodes.item(i).getTextContent();
			String fName1stCh = fileName.startsWith("bix")
					                    ? "bix"
					                    : fileName.startsWith("gg") ? "gg" : fileName.substring(0, 1);
			String dir = fName1stCh.matches("[\\p{L}]")
					             ? fName1stCh
					             : fileName.substring(0, fileName.indexOf(fileName.replaceFirst("[^\\p{L}]", "")));
			sounds.add(String.format(urlFmt, dir, fileName));
		}
		return sounds;
	}

	class SearchResult {
		final public String entry;
		final public Node foundAt;

		public SearchResult() {
			entry = null;
			foundAt = null;
		}

		public SearchResult(String entry, Node foundAt) {
			this.entry = entry;
			this.foundAt = foundAt;
		}

		public String toString() {
			return String.format("%s at %s", entry, foundAt.getLocalName());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			SearchResult that = (SearchResult) o;

			if (!entry.equals(that.entry)) return false;
			return foundAt.equals(that.foundAt);
		}

		@Override
		public int hashCode() {
			int result = entry.hashCode();
			result = 31 * result + foundAt.hashCode();
			return result;
		}
	}

	public Collection<String> findUseCases(Node rootNode) throws XPathExpressionException {
		String[] useCasesTags = {"vi", "snote"};
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

	public Collection<SearchResult> findDefinitions(Node rootNode) throws XPathExpressionException {
		String[] definitionTags = {"def/dt"/*, "def/dt/un"*/};
		Collection<SearchResult> definitionsSR = new LinkedHashSet<>();
		String definition;
		StringBuilder defBuilder = new StringBuilder();
		for(String tag: definitionTags) {
			NodeList defNodes = (NodeList) xPath.evaluate(tag, rootNode, XPathConstants.NODESET);
			int defCount = defNodes.getLength();
			for (int i = 0; i < defCount; i++) {
				defBuilder.setLength(0);
				Node currNode = defNodes.item(i).getFirstChild();
				if (currNode.getNodeType() == Node.ELEMENT_NODE)
					continue;
				String definitionContents = extractNodeContents(defNodes.item(i));
				String[] definitions = definitionContents.split(":");
				boolean closeParenthesis = false;
				for (int j = 0, first = 0; j < definitions.length; j++) {
					boolean emptyDefinition = definitions[j].trim().isEmpty();
					if (emptyDefinition){
						first++;
						continue;
					}
					if(j > first && !emptyDefinition) {
						defBuilder.append(" (=");
						closeParenthesis = true;
					}
					defBuilder.append(definitions[j].trim());
					if(closeParenthesis) {
						defBuilder.append(")");
						closeParenthesis = false;
					}
				}
				SearchResult usageNote = findUsageNote(currNode.getParentNode());
				Node useCasesRoot = currNode.getParentNode();
				if(!usageNote.entry.isEmpty()) {
					defBuilder.append(" - ").append(usageNote.entry);
					useCasesRoot = usageNote.foundAt;
				}
				definition = defBuilder.toString();
				if (!definition.isEmpty())
					definitionsSR.add(new SearchResult(definition.trim(), useCasesRoot));
				else {
					Collection<String> synonyms = findSynonyms(defNodes.item(i));
					if (!synonyms.isEmpty()) {
						defBuilder.append("see: <it>");
						synonyms.forEach(syn -> defBuilder.append(syn).append(", "));
						int defLen = defBuilder.length();
						definition = defBuilder.replace(defLen - 2, defLen, "</it>").toString();
					}
					if (!definition.isEmpty())
						definitionsSR.add(new SearchResult(definition, rootNode));
				}
			}
		}
		if (definitionsSR.isEmpty()) {
			definition = getParentNodeEntry(rootNode);
			if (!definition.isEmpty()) {
				definition = defBuilder.append("see: <it>").append(definition).append("</it>").toString();
				Node useCaseSourceNode = (Node) xPath.evaluate("utxt", rootNode, XPathConstants.NODE);
				if (useCaseSourceNode != null)
					definitionsSR.add(new SearchResult(definition, useCaseSourceNode));
			}
		}
		if (definitionsSR.isEmpty()) {
			Node referenceNode = (Node) xPath.evaluate("dx", rootNode, XPathConstants.NODE);
			if (referenceNode != null) {
				definition = extractNodeContents(referenceNode);
				definitionsSR.add(new SearchResult(definition, referenceNode));
			}
		}
		return definitionsSR;
	}

	private SearchResult findUsageNote(Node rootNode) throws XPathExpressionException {
		Node node = (Node) xPath.evaluate("un", rootNode, XPathConstants.NODE);
		String usageNote = "";
		if (node != null) usageNote = extractNodeContents(node);
		return new SearchResult(usageNote, node);
	}

	private String getParentNodeEntry(Node rootNode) throws XPathExpressionException {
		String[] entryNodeTags = {"ew", "hw", "dro/dre", "uro/ure"};
		for (String entryNode : entryNodeTags) {
			String expression = String.format("//%s", entryNode);
			NodeList nodes = (NodeList) xPath.evaluate(expression, rootNode, XPathConstants.NODESET);
			int nodeCount = nodes.getLength();
			for (int i = 0; i < nodeCount; i++) {
				String currEntry = nodes.item(i).getTextContent().replace("*", "");
				if (!currEntry.isEmpty())
					return currEntry;
			}
		}
		return "";
	}

	public Collection<String> findSynonyms(Node rootNode) throws XPathExpressionException {
		Collection<String> synonyms = new ArrayList<>();
		NodeList synNodes = (NodeList) xPath.evaluate("sx", rootNode, XPathConstants.NODESET);
		int nNodes = synNodes.getLength();
		for (int i = 0; i < nNodes; i++)
			synonyms.add(synNodes.item(i).getFirstChild().getTextContent());
		return synonyms;
	}

	public SearchResult findTranscription(Node rootNode) throws XPathExpressionException {
		String[] transcriptionTags = {"pr", "vr/pr", "altpr"};
		String transcription = "";
		Node node = rootNode;
		for (String tag: transcriptionTags) {
			node = rootNode;
			String expression = String.format("%s", tag);
			switch (((Element)node).getTagName()) {
				case "dre":
				case "ure": node = node.getParentNode();
			}
			while (node != null && (transcription = xPath.evaluate(expression, node)).isEmpty())
				node = node.getParentNode();
			if (!transcription.isEmpty()) break;
		}
		return new SearchResult(transcription, node);
	}

	public SearchResult findPartOfSpeech(Node rootNode) throws XPathExpressionException {
		String partOfSpeechName/* = xPath.evaluate("fl", rootNode)*/;
		Node node = rootNode;
		while ((partOfSpeechName = xPath.evaluate("fl", node)).isEmpty() && node.getParentNode() != null)
			node = node.getParentNode();
		if (partOfSpeechName.isEmpty()) {
			partOfSpeechName = PartOfSpeech.UNDEFINED;
			if (node == null) node = rootNode;
		}
		return new SearchResult(partOfSpeechName, node);
	}

	private Collection<String> findForms(Node node) throws XPathExpressionException {
		NodeList formsList = (NodeList) xPath.evaluate("in/if", node, XPathConstants.NODESET);
		Collection<String> knownForms = new ArrayList<>(formsList.getLength());
		for (int i = 0; i < formsList.getLength(); i++)
			knownForms.add(extractNodeContents(formsList.item(i)));
		return knownForms;
	}

	public Collection<SearchResult> findEntries(String query, boolean acceptFormRootsEntry, Node rootNode)
			throws XPathExpressionException {
		String[] entryNodeTags = {"ew", "hw", "dro/dre", "uro/ure"};
		String queryLC = query.toLowerCase();
		Set<SearchResult> entriesSR = new LinkedHashSet<>();
		Set<SearchResult> similarsSR = new LinkedHashSet<>();
		for (String entryNode:  entryNodeTags) {
			String expression = String.format("entry/%s", entryNode);
			NodeList nodes = (NodeList) xPath.evaluate(expression, rootNode, XPathConstants.NODESET);
			int nodeCount = nodes.getLength();
			for (int i = 0; i < nodeCount; i++) {
				String currEntry = nodes.item(i).getTextContent().replace("*", "");
				if (equalIgnoreCaseAndPunctuation(currEntry, queryLC)
						    && hasDefinition(nodes.item(i).getParentNode()) || acceptFormRootsEntry) {
					entriesSR.add(new SearchResult(currEntry, nodes.item(i).getParentNode()));
					if (acceptFormRootsEntry)
						return entriesSR;
				} else if (isSimilar(currEntry, queryLC))
					similarsSR.add(new SearchResult(currEntry, nodes.item(i).getParentNode()));
				if (!acceptFormRootsEntry && !entriesSR.isEmpty()) continue;
				Collection<String> forms = findForms(nodes.item(i).getParentNode());
				for (String form: forms)
					if(form.toLowerCase().equals(queryLC)) {
						entriesSR.addAll(findEntries(form, true, nodes.item(i).getParentNode().getParentNode()));
						break;
					} else
						similarsSR.add(new SearchResult(form, nodes.item(i).getParentNode()));
			}
		}
		if (entriesSR.isEmpty()) {
			SearchResult entrySR = similarsSR.stream()
					.filter(similar -> isSimilar(similar.entry, queryLC))
					.findFirst().orElseGet(SearchResult::new);
			if(entrySR.entry != null) entriesSR.add(entrySR);
		}
		return entriesSR;
	}

	private boolean hasDefinition(Node rootNode) throws XPathExpressionException {
		int nDefinitions = ((Number) xPath.evaluate("count(def/dt)", rootNode, XPathConstants.NUMBER)).intValue();
		nDefinitions += ((Number) xPath.evaluate("count(def/dt/un)", rootNode, XPathConstants.NUMBER)).intValue();
		//<utxt> means that there are some use cases thus definition will be composed as reference at parent node's entry
		nDefinitions += ((Number) xPath.evaluate("count(utxt)", rootNode, XPathConstants.NUMBER)).intValue();
		nDefinitions += ((Number) xPath.evaluate("count(dx)", rootNode, XPathConstants.NUMBER)).intValue();
		return nDefinitions != 0;
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
						case "dxt":
						case "it":
						case "fw": content.append("<it>").append(extractNodeContents(child)).append("</it>"); break;
						case "sx":
						case "dx":
						case "un":
						case "vi": i = limit; break;
						default:   content.append(extractNodeContents(child).replace("*",""));
					}
					break;
				case Node.TEXT_NODE: content.append(child.getTextContent().replace("*",""));
			}
		}
		return content.toString();
	}

	public Collection<String> collectSuggestions(Node sourceNode) throws XPathExpressionException {
		String[] pathsToCollect = {"suggestion", "entry/hw", "entry/ew", "entry/dro/dre", "entry/uro/ure"};
		Collection<String> strSuggestions = new ArrayList<>();
		for (String sourcePath: pathsToCollect) {
			NodeList suggestions = (NodeList) xPath.evaluate(sourcePath, sourceNode, XPathConstants.NODESET);
			for (int i = 0; i < suggestions.getLength(); i++)
				strSuggestions.add(suggestions.item(i).getFirstChild().getTextContent().replace("*", ""));
		}
		return strSuggestions;
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

}
