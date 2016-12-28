package com.nicedev.vocabularizer.dictionary;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

public class Definition implements Serializable, Comparable{
	
	private static final long serialVersionUID = 2408653327252231176L;

	public final Language language;
	public final String explanatory;
	protected Map.Entry<Vocabula, PartOfSpeech> defines;
	private String identicalTo;
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
	
	public Definition(Definition definition, Vocabula voc, PartOfSpeech pOS) {
		language = definition.language;
		explanation = definition.explanation;
		defines = new AbstractMap.SimpleEntry<>(voc, pOS);
//		useCases = new LinkedHashSet<>(definition.getUseCases());
//		synonyms = new TreeSet<>(definition.getUseCases());
		useCases = definition.useCases;
		synonyms = definition.synonyms;
	}

	public void addUseCase(String useCase) {
		useCases.add(useCase.trim());
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
//		return Maps.unmodifiableSet(synonyms);
//	}
	public Set<String> getSynonyms() {
		return Collections.unmodifiableSet(synonyms);
	}

	public Set<String> getUseCases() {
		return Collections.unmodifiableSet(useCases);
	}

	public void assignTo(String entry) {
		if( Language.charsMatchLanguage(entry, language)) {
			identicalTo = entry;
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
					                         "<td class='definition'>see:<b>%s</b></td></tr></table>", identicalTo));
		if (!synonyms.isEmpty()){
			res.append(String.format("\n<div class='synonym'>synonym%s: ", synonyms.size() > 1 ? "s" : ""))
					.append(synonyms.stream()
										.map(s -> s.contains("(") ? s : wrapInTag(s, s, "b", ""))
										.collect(joining(", "))).append("</div>\n");
//					.append("<b>").append(synonyms.stream().collect(joining("</b>, <b>"))).append("</b></div>\n");
		}
		if (!useCases.isEmpty()){
			res.append("\n<div><table><tr><td></td><td class='usecase'>")
					.append(useCases.stream().collect(joining("<br>\n"))).append("</td></tr></table></div>\n");
		}
		res.append("</div>");
		return res.toString();
	}
	
	private String wrapInTag(String source, String match, String tag, String className) {
		Matcher matcher = Pattern.compile(match).matcher(source);
		String result = source;
		String wrapFmt = className.isEmpty() ? "<%1$s>%3$s</%1$s>" : "<%1$s class='%2$s'>%3$s</%1$s>";
		while (matcher.find()) {
			String wrapped = matcher.group(0);
			result = source.replace(wrapped, String.format(wrapFmt, tag, className, wrapped));
		}
		return result;
	}
	
	
	public void addSynonyms(Collection<String> synonyms) {
		synonyms.forEach(s -> this.synonyms.add(s.trim()));
	}

	public void addUseCases(Collection<String> useCases) {
		useCases.forEach(uc -> this.useCases.add(uc.trim()));
	}

}
