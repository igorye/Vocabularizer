package com.nicedev.vocabularizer.services;

import com.nicedev.vocabularizer.dictionary.*;
import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.sun.org.apache.xml.internal.security.transforms.params.XPathContainer;
import com.sun.org.apache.xpath.internal.NodeSet;
import com.sun.org.apache.xpath.internal.XPathContext;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Expositor {

	public final Language language;
	final private String collegiateRequestFmt = "http://www.dictionaryapi.com/api/v1/references/collegiate/xml/%s?key=%s";
	final private String learnersRequestFmt = "http://www.dictionaryapi.com/api/v1/references/learners/xml/%s?key=%s";
	final private String collegiateKey = "f0a68804-fd23-4eca-ba8b-037f5d156a1f";
	final private String learnersKey = "fe9645ff-73f8-4b3f-b09c-dc96b12f767f";
	private String expositorRequestFmt;
	private String key;

	final private Dictionary dictionary;

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
		try(BufferedReader responseReader = new BufferedReader(new InputStreamReader(uRequest.openStream()))) {
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
			System.err.printf("Error retrieving [%s]. Service is unavailable (%s)%n", entry, e.getMessage());
		}
		return null;
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
						case "it": content.append("<it>").append(extractNodeContents(child)).append("</it>"); break;
						default:   content.append(extractNodeContents(child).replace("*",""));
					}
					break;
				case Node.TEXT_NODE: content.append(child.getTextContent().replace("*",""));
			}
		}
		return content.toString();
	}

	private Set<Definition> extractDefinition(XPath path, Node definitionNode, Vocabula defines, String partOfSpeechName)
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
	}

	private Node getRootNode(Node definitionNode) {
		Node node = definitionNode.getParentNode();
		while (node.getParentNode().getParentNode() != null)
			node = node.getParentNode();
		return node;
	}

	private String getFirstDefinition(Set<String> synonyms, String exactPartOfSpeechName) {
		for (String synonym: synonyms) {
			Set<Definition>
					definitions = getVocabula(synonym, exactPartOfSpeechName).getDefinitions(exactPartOfSpeechName);
			if (definitions.size() != 0)
				return definitions.iterator().next().explanatory;
		}
		return null;
	}

	private Vocabula extractVocabula(String query, String queryPartOfSpeechName, Document xmlDoc) throws XPathExpressionException {
		XPathFactory xpFactory = XPathFactory.newInstance();
		XPath path = xpFactory.newXPath();
		Node root = (Node)path.evaluate("/entry_list", xmlDoc, XPathConstants.NODE);
		Node startNode = root;
		//check for appropriate entries
		int siblingCount = ((Number) path.evaluate("count(entry)", root, XPathConstants.NUMBER)).intValue();
		if (siblingCount == 0) {
			System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, collectSuggestions(path, root));
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
		Set<String> knownForms = extractKnownForms(path, formsNode);
		if (!match) {
			formsNode = (Node) path.evaluate("entry", root, XPathConstants.NODE);
			knownForms = extractKnownForms(path, formsNode);
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
			siblingCount = ((Number) path.evaluate("count(entry/*)", root, XPathConstants.NUMBER)).intValue();
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
			Set<String> forms = new LinkedHashSet<>();
			try {
				formsNode = (Node) path.evaluate(expression, root, XPathConstants.NODE);
				forms = extractKnownForms(path, formsNode);
				if(forms.isEmpty() && !knownForms.isEmpty())
					forms = knownForms;
			} catch (XPathExpressionException e) {/* Just ignore if forms source node is missing */}
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
			System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, collectSuggestions(path, startNode));

		return vocabula;
	}

	private Set<String> extractKnownForms(XPath path, Node node) throws XPathExpressionException {
		NodeList formsList = (NodeList) path.evaluate("in/if", node, XPathConstants.NODESET);
		Set<String> knownForms = new LinkedHashSet<>(formsList.getLength());
		for (int i = 0; i < formsList.getLength(); i++) {
			knownForms.add(extractNodeContents(formsList.item(i)));
		}
		return knownForms;
	}

	//checks ignoring case allowing mismatch at one char which is probably punctuation sign
	private boolean equalIgnoreCaseAndPunctuation(String query, String currEntry) {
		if (query.length() != currEntry.length()) return false;
		if (query.length() == 1) return query.equalsIgnoreCase(currEntry);
		int diffPos = -1;
		//find probably punctuation char that can mismatch depending on locale
		String lQuery = query.toLowerCase();
		String lCurrEntry = currEntry.toLowerCase();
		if (lCurrEntry.length() == lQuery.length())
			for (int i = 0; i < query.length(); i++)
				if(lQuery.charAt(i) != lCurrEntry.charAt(i)) {
					diffPos = i;
					break;
				}
		//check for equality of the rest of strings
		if (diffPos >= 0)
			return lQuery.regionMatches(true, diffPos+1, lCurrEntry, diffPos+1, lQuery.length()-diffPos-1);
		return lCurrEntry.equals(lQuery);
	}

	private boolean isSimilar(String word, String entry) {
		if (word.isEmpty() || entry.isEmpty()) return false;
		String wordLC = word.toLowerCase().replaceFirst("[^\\p{L}]","").replace(" ", "");
		String entryLC = entry.toLowerCase().replaceFirst("[^\\p{L}]","").replace(" ", "");
		return wordLC.contains(entryLC) || entryLC.contains(wordLC);
//				       || wordLC.startsWith(entryLC) || entryLC.startsWith(wordLC);
	}

	private Set<String> collectSuggestions(XPath path, Node sourceNode) throws XPathExpressionException {
		String[] pathsToCollect = {"suggestion", "entry/hw", "entry/ew"};
		Set<String> strSuggestions = new LinkedHashSet<>();
		for (String sourcePath: pathsToCollect) {
			NodeList suggestions = (NodeList) path.evaluate(sourcePath, sourceNode, XPathConstants.NODESET);
			for (int i = 0; i < suggestions.getLength(); i++)
				strSuggestions.add(suggestions.item(i).getTextContent().replace("*", ""));
		}
		return strSuggestions;
	}

	private boolean appropriatePartOfSpeech(String partOfSpeechName, String exactPartOfSpeechName) {
		return !language.getPartOfSpeech(partOfSpeechName).partName.equals(PartOfSpeech.UNDEFINED)
				       && (exactPartOfSpeechName.equals(PartOfSpeech.ANY) || partOfSpeechName.equals(exactPartOfSpeechName));
	}

}
