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
	private Speller speller;

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
		speller.release(0);
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
		speller = new Speller();
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
//			URL uRequest = new URL(request);
//			InputStream is = new ByteArrayInputStream(getRawContents(uRequest).getBytes());
			Document xmlDoc = builder.parse(request);
//			Document xmlDoc = builder.parse(is);
//
//			return extractVocabula(entry, exactPartOfSpeechName, xmlDoc);

			try {
				return extractVocabula2(entry, exactPartOfSpeechName, xmlDoc);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		} catch (SAXException | ParserConfigurationException | XPathExpressionException | NullPointerException e) {
			System.err.printf("Error retrieving [%s]. Unable to parse  document (%s).%n", entry, e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.printf("Error retrieving [%s]. No response to request (%s)%n", entry, e.getMessage());
		}
		return null;
	}

	private Vocabula extractVocabula2(String query, String exactPartOfSpeechName, Document xmlDoc) throws XPathExpressionException, InterruptedException {
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
					if (sIt.hasNext()) speller.spell(sIt.next(), 500);
					else speller.spell(headWord.entry, 500);
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
					Collection<String> forms = findKnownForms(headWord.foundAt);
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
		Collection<String> useCases = new ArrayList<>();
		NodeList nodes = (NodeList) xPath.evaluate("vi", rootNode, XPathConstants.NODESET);
		int nNodes = nodes.getLength();
		for (int i = 0; i < nNodes; i++) {
			String useCase = extractNodeContents(nodes.item(i));
			if(!useCase.isEmpty())
				useCases.add(useCase);
		}
		return useCases;
	}

	public Collection<SearchResult> findDefinitions(Node rootNode) throws XPathExpressionException {
		String[] definitionTags = {"def/dt", "def/dt/un"};
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
					if (definitions[j].trim().isEmpty()){
						first++;
						continue;
					}
					if(j > first && !definitions[j].trim().isEmpty()) {
						defBuilder.append(" (=");
						closeParenthesis = true;
					}
					defBuilder.append(definitions[j].trim());
					if(closeParenthesis) {
						defBuilder.append(")");
						closeParenthesis = false;
					}
				}
				definition = defBuilder.toString();
				if (!definition.isEmpty())
					definitionsSR.add(new SearchResult(definition.trim(), currNode.getParentNode()));
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
		String partOfSpeechName = xPath.evaluate("fl", rootNode);
		Node node = rootNode;
		while ((partOfSpeechName = xPath.evaluate("fl", node)).isEmpty() && node.getParentNode() != null)
			node = node.getParentNode();
		if (partOfSpeechName.isEmpty()) {
			partOfSpeechName = PartOfSpeech.UNDEFINED;
			if (node == null) node = rootNode;
		}
		return new SearchResult(partOfSpeechName, node);
	}

	private Collection<String> findKnownForms(Node node) throws XPathExpressionException {
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
		int longestSimilarLength = 0;
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
				} else if (isSimilar(currEntry, queryLC)) {
					similarsSR.add(new SearchResult(currEntry, nodes.item(i).getParentNode()));
					if (currEntry.length() > longestSimilarLength)
						longestSimilarLength = currEntry.length();
				}
				if (!acceptFormRootsEntry && !entriesSR.isEmpty()) continue;
				Collection<String> forms = findKnownForms(nodes.item(i).getParentNode());
				for (String form: forms)
					if(form.toLowerCase().equals(queryLC)) {
						entriesSR.addAll(findEntries(form, true, nodes.item(i).getParentNode().getParentNode()));
						break;
					} else
						similarsSR.add(new SearchResult(form, nodes.item(i).getParentNode()));
			}
		}
		if (entriesSR.isEmpty()) {
			final int finLongSimLen = longestSimilarLength;
			similarsSR.stream()
					.filter(similar -> isSimilar(similar.entry, queryLC) && similar.entry.length() == finLongSimLen)
					.forEach(entriesSR::add);
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
						case "it": content.append("<it>").append(extractNodeContents(child)).append("</it>"); break;
						case "fw": content.append("<it>").append(extractNodeContents(child)).append("</it>"); break;
						case "sx":
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
	private boolean isSimilar(String word, String entry) {
		if (word.isEmpty() || entry.isEmpty()) return false;
		String wordLC = word.toLowerCase().replaceFirst("[^\\p{L}]","").replace(" ", "");
		String entryLC = entry.toLowerCase().replaceFirst("[^\\p{L}]","").replace(" ", "");
		return wordLC.contains(entryLC) || entryLC.contains(wordLC);
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


/*	private Vocabula extractVocabula(String query, String queryPartOfSpeechName, Document xmlDoc) throws XPathExpressionException {
		XPathFactory xpFactory = XPathFactory.newInstance();
		XPath path = xpFactory.newXPath();
		Node root = (Node)path.evaluate("/entry_list", xmlDoc, XPathConstants.NODE);
		Node startNode = root;
		//check for appropriate entries
		int siblingCount = ((Number) path.evaluate("count(entry)", root, XPathConstants.NUMBER)).intValue();
		if (siblingCount == 0) {
			System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, collectSuggestions(root));
			return null;
		}
		String partOfSpeechName = path.evaluate("entry[1]/fl", root);
		//find requested entry
		String entryPath = "ew",
				formPath = "fl",
				defPath = "def/dt",
				sibling = "entry";
		String transcription = path.evaluate("entry[1]//pr", root);
		if (transcription.isEmpty())
			transcription = path.evaluate("entry[1]//altpr", root);
		String entry = path.evaluate("entry[1]/ew", root).trim();
		if (entry.isEmpty()) {
			entry = path.evaluate("entry[1]/hw", root).trim();
			entryPath = "hw";
		}
		entry = entry.replace("*", "");
		boolean match = equalIgnoreCaseAndPunctuation(entry, query);
		Node formsNode = (Node) path.evaluate("entry", root, XPathConstants.NODE);
		Collection<String> knownForms = findKnownForms(formsNode);
		if (!match) {
			formsNode = (Node) path.evaluate("entry", root, XPathConstants.NODE);
			knownForms = findKnownForms(formsNode);
			if (knownForms.contains(query))
				return getVocabula(entry);
		}
		//check if we heed to change path to lookup definition
		int nodeCount = ((Number) path.evaluate("count(entry[1]/def)", root, XPathConstants.NUMBER)).intValue();
		if (nodeCount == 0 || !match) {
			match = false;
			sibling = "dro";
			entryPath = "dre";
//			formPath = "def/gram";
			siblingCount = ((Number) path.evaluate("count(entry*//*)", root, XPathConstants.NUMBER)).intValue();
			if (siblingCount != 0) {
				root = (Node) path.evaluate("entry", root, XPathConstants.NODE);
				siblingCount = ((Number) path.evaluate("count(*)", root, XPathConstants.NUMBER)).intValue();
			}

		}
		String equalsTo = null;
		//first round looking up vocabula's content
		Vocabula vocabula = null;
		for(int i = 1; i <= siblingCount; i++) {
			String currEntry = path.evaluate(String.format("%s[%d]/%s", sibling, i, entryPath), root).replace("*","");
			if(currEntry.isEmpty())
				currEntry = entry;
			//check if current sibling matches query
			boolean acceptCurrEntry = !match && isSimilar(query, currEntry);
//			boolean acceptCurrEntry = !match && isSimilar(query, currEntry);
			if(siblingCount > 1 && !equalIgnoreCaseAndPunctuation(query, currEntry) && !acceptCurrEntry)
				continue;
			String currPartOfSpeechName = path.evaluate(String.format("%s[%d]/%s", sibling, i, formPath), root);
			if (currPartOfSpeechName.isEmpty())
				currPartOfSpeechName = partOfSpeechName;
			//check if current entry's form matches query
			if (!appropriatePartOfSpeech(currPartOfSpeechName, queryPartOfSpeechName))
				continue;
			String expression = String.format("%s[%d]", sibling, i);
			Collection<String> forms = new LinkedHashSet<>();
			try {
				formsNode = (Node) path.evaluate(expression, root, XPathConstants.NODE);
				forms = findKnownForms(formsNode);
				if(forms.isEmpty() && !knownForms.isEmpty())
					forms = knownForms;
			} catch (XPathExpressionException e) {*//* Just ignore if forms source node is missing *//*}
			expression = String.format("count(%s[%d]/%s)", sibling, i, defPath);
			int definitionCount = ((Number) path.evaluate(expression, root, XPathConstants.NUMBER)).intValue();
			if (definitionCount == 0) continue;
			if (vocabula == null) {
				vocabula = new Vocabula((!query.equals(entry) && acceptCurrEntry) ? currEntry : entry,
						                       language, transcription);
			}
//			expression = String.format("count(%s[%d]/%s)", sibling, i, defPath);
			//process available definitions
			for(int k = 1; k <= definitionCount; k++) {
				expression = String.format("%s[%d]/%s[%d]", sibling, i, defPath, k);
				Node definitionNode = (Node) path.evaluate(expression, root, XPathConstants.NODE);
				Set<Definition> definitions = extractDefinition(path, definitionNode, vocabula, currPartOfSpeechName);
				vocabula.addKnownForms(currPartOfSpeechName, forms);
				vocabula.addDefinitions(currPartOfSpeechName, definitions);
				if (vocabula.getDefinitions(PartOfSpeech.ANY).isEmpty())
					vocabula = null;
			}
		}
		//second round looking up vocabula's content
		if (vocabula == null){
			root = (Node)path.evaluate("entry[1]/uro", startNode, XPathConstants.NODE);
			if (root != null) {
				entry = path.evaluate("ure", root).replace("*","").trim();
				if (transcription == null)
					transcription = path.evaluate("pr", root);
			} else {
				root = (Node) path.evaluate("entry[1]", startNode, XPathConstants.NODE);
				if (root == null)
					root = startNode;
			}
			partOfSpeechName = path.evaluate("fl", root);
			Set<Definition> definitions = null;
			PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
			vocabula = new Vocabula(entry, language, transcription);
			Node definitionNode = (Node) path.evaluate("utxt", root, XPathConstants.NODE);
			if(definitionNode == null)
				definitionNode = (Node) path.evaluate("cx", root, XPathConstants.NODE);
			if (definitionNode == null) {
				if (knownForms.contains(query)) {
					knownForms.remove(query);
					knownForms.add(entry);
					String explanatory = String.format("see: <it>%s</it>", entry);
					entry = query;
					vocabula = new Vocabula(entry, language, "");
					Definition definition = new Definition(language, new AbstractMap.SimpleEntry<>(vocabula, partOfSpeech),
							                                      explanatory);
					vocabula.addKnownForms(partOfSpeech, knownForms);
					definitions = new TreeSet<>();
					definitions.add(definition);
					vocabula.addDefinitions(partOfSpeech, definitions);
				}
			} else {
				vocabula = new Vocabula(entry, language, transcription);
				definitions = extractDefinition(path, definitionNode, vocabula, partOfSpeechName);
				vocabula.addDefinitions(partOfSpeechName, definitions);
			}
			if (vocabula.getDefinitions(PartOfSpeech.ANY).isEmpty())
				vocabula = null;
		}
		//if nothing found
		if (vocabula == null)
			System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, collectSuggestions(startNode));

		return vocabula;
	}*/

	/*private Set<Definition> extractDefinition(XPath path, Node definitionNode, Vocabula defines, String partOfSpeechName)
			throws XPathExpressionException {
		NodeList nodes = definitionNode.getChildNodes();
		Set<Definition> usageNotes = new LinkedHashSet<>(nodes.getLength());
		StringBuilder defBuilder = new StringBuilder();
		Set<String> synonyms = new LinkedHashSet<>(),
				useCases = new LinkedHashSet<>(),
				knownForms = new LinkedHashSet<>();

		for (int i = 0; i < nodes.getLength(); i++) {
			Node child = nodes.item(i);
			switch (child.getNodeType()) {
				case Node.TEXT_NODE:
					String content = child.getTextContent().replaceFirst(":", "");
					if (!content.trim().isEmpty())
						defBuilder.append(content);
					break;
				case Node.ELEMENT_NODE:
					switch (((Element)child).getTagName()) {
						case "it": defBuilder.append("<it>").append(extractNodeContents(child)).append("</it>");
							break;
						case "cl": defBuilder.append(extractNodeContents(child));
							break;
						case "ct": knownForms.add(child.getFirstChild().getTextContent().trim());
							break;
						case "sx": synonyms.add(child.getFirstChild().getTextContent().trim());
							break;
						case "fw": defBuilder.append("<it>").append(extractNodeContents(child)).append("</it>");
							break;
						case "vi": useCases.add(extractNodeContents(child));
							break;
						case "un": usageNotes.addAll(extractDefinition(path,
								(Node) path.evaluate(".",child, XPathConstants.NODE), defines, partOfSpeechName));
							break;
						case "dro": nodes =  (NodeSet) path.evaluate(".", child, XPathConstants.NODESET);
							i = 0;
							break;
						case "dre": useCases.add(child.getTextContent()); break;
					}
					break;
			}
		}
		int replacementPos;
		if ((replacementPos = defBuilder.lastIndexOf(":")) > 0)
			defBuilder.replace(replacementPos, defBuilder.length(), "");
		String definitionEntry = defBuilder.toString().trim();
		//do we have definition explanatory
		boolean incompleteDefinition = definitionEntry.isEmpty();

		boolean hasUseCases = useCases.size() != 0;
		boolean hasSynonyms = synonyms.size() != 0;
		boolean hasUsageNotes = usageNotes.size() != 0;
		boolean hasForms = knownForms.size() != 0;

		//if we don't - compose definition
		Node rootNode = getRootNode(definitionNode);
		String nodeEntry;
		if (incompleteDefinition) {
			if (hasSynonyms) {
				defBuilder.append("<it>see</it>: ");
				synonyms.forEach(syn -> defBuilder.append(syn).append(", "));
				if ((replacementPos = defBuilder.lastIndexOf(", ")) == defBuilder.length() - 2)
					defBuilder.replace(replacementPos, defBuilder.length(), "");
			} else if (!hasUsageNotes) {
				defBuilder.append("<it>see</it>: ");
				nodeEntry = path.evaluate("//entry[1]/ew", rootNode);
				if (nodeEntry.isEmpty())
					nodeEntry = path.evaluate("//entry[1]/hw", rootNode);
				defBuilder.append(nodeEntry.replace("*",""));
			}
			definitionEntry = defBuilder.toString();
		}
		if (hasForms) {
			defBuilder.append(" <it>");
			knownForms.forEach(kf -> defBuilder.append(kf).append(", "));
			if ((replacementPos = defBuilder.lastIndexOf(", ")) == defBuilder.length() - 2)
				defBuilder.replace(replacementPos, defBuilder.length(), "</it>");
			defBuilder.insert(0, definitionEntry);
			definitionEntry = defBuilder.toString().trim();
		}
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		Definition definition = new Definition(language, new AbstractMap.SimpleEntry<>(defines, partOfSpeech),
				                                      definitionEntry);
		if (hasSynonyms) definition.addSynonyms(synonyms);
		if (hasUseCases) definition.addUseCases(useCases);
		Set<Definition> definitions = new LinkedHashSet<>(nodes.getLength());
		if (!definitionEntry.isEmpty()) definitions.add(definition);
		// usage notes contain use cases thus they can represent separate definitions
		if (hasUsageNotes) definitions.addAll(usageNotes);
		return definitions;
	}*/
}
