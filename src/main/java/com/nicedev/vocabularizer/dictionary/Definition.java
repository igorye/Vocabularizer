package com.nicedev.vocabularizer.dictionary;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.*;

@XmlRootElement
public class Definition implements Serializable, Comparable{
	public final Language language;
	public final String explanatory;
	protected Map.Entry<Vocabula, PartOfSpeech> defines;
	private String indenticalTo;
	private Set<String> useCases;
	private Set<String> synonyms;

	private boolean hasAccordance;

	public Definition(Language lang, Vocabula vocabula, PartOfSpeech partOfSpeech, String explanatory) {
		this.explanatory = explanatory;
		this.language = lang;
		synonyms = new TreeSet<>();
		useCases = new LinkedHashSet<>();
		this.defines = new AbstractMap.SimpleEntry<>(vocabula, partOfSpeech);
	}

	public Definition(Language lang, Vocabula vocabula, PartOfSpeech partOfSpeech) {
		this(lang, vocabula, partOfSpeech, vocabula.headWord);
	}

	public void addUseCase(String useCase) {
		useCases.add(useCase);
	}

	public void removeUseCase(String useCase) {
		useCases.remove(useCase);
	}

	public void addSynonym(String headWord) {
		synonyms.add(headWord);
	}

	public void removeSynonym(String headWord) {
//		synonyms.add(definition);
	}

//	public Set<Definition> getSynonyms() {
//		return Collections.unmodifiableSet(synonyms);
//	}
	@XmlElement(name = "synonym")
	public Set<String> getSynonyms() {
		return Collections.unmodifiableSet(synonyms);
	}

	@XmlElement(name = "useCase")
	public Set<String> getUseCases() {
		return Collections.unmodifiableSet(useCases);
	}

	public void assignTo(String entry) {
		if( Language.charsMatchLanguage(entry, language)) {
			indenticalTo = entry;
			hasAccordance = true;
		}
	}

	@Override
	public int compareTo(Object o) {
		return explanatory.compareTo(((Definition) o).explanatory);
	}

	public boolean hasAccordance() {
		return hasAccordance;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Definition that = (Definition) o;

		return explanatory.equalsIgnoreCase(that.explanatory);
	}

	@Override
	public int hashCode() {
		return explanatory.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		if (explanatory.length() != 0)
			res.append(String.format("    - %s",explanatory.replaceAll("<b>", "<").replaceAll("</b>", ">")));
		else if (indenticalTo != null)
			res.append(String.format("    - see <%s>", indenticalTo.replaceAll("<b>", "<").replaceAll("</b>", ">")));
		if (!synonyms.isEmpty()){
			res.append(String.format("\n      synonym%s: ", synonyms.size() > 1 ? "s" : ""));
			synonyms.forEach(uc -> res.append(uc).append(", "));
			if (res.lastIndexOf(", ") == res.length()-2)
				res.replace(res.lastIndexOf(", "), res.length(), "");
		}
		if (!useCases.isEmpty()){
			res.append("\n      : ");
			useCases.forEach(uc -> res.append(String.format("%s", uc.replaceAll("<b>", "<").replaceAll("</b>", ">")))
					                       .append("\n        "));
		}
		res.append("\n");
		return res.toString();
	}

	public String toHTML() {
		StringBuilder res = new StringBuilder();
		if (explanatory.length() != 0)
			res.append(String.format("<div class='definition'><table><tr><td>-</td>" +
					                         "<td class='definition'>%s</td></tr></table>",explanatory));
		else if (indenticalTo != null)
			res.append(String.format("<div class='definition'><table><tr><td>-</td>" +
					                         "<td class='definition'>see:<b>%s</b></td></tr></table>", indenticalTo));
		if (!synonyms.isEmpty()){
			res.append(String.format("\n<div class='synonym'>synonym%s: ", synonyms.size() > 1 ? "s" : ""));
			synonyms.forEach(uc -> res.append(uc).append(", "));
			if (res.lastIndexOf(", ") == res.length()-2)
				res.replace(res.lastIndexOf(", "), res.length(), "</div>\n");
		}
		if (!useCases.isEmpty()){
			res.append("\n<div><table><tr><td></td><td class='usecase'>");
			useCases.forEach(uc -> res.append(uc).append("<br>\n"));
			if (res.lastIndexOf("<br>") == res.length()-5)
				res.replace(res.lastIndexOf("<br>"), res.length(), "</td></tr></table></div>\n");
		}
		res.append("</div>");
		return res.toString();
	}

	public void addSynonyms(Collection<String> synonyms) {
		this.synonyms.addAll(synonyms);
	}

	public void addUseCases(Collection<String> useCases) {
		this.useCases.addAll(useCases);
	}

}
