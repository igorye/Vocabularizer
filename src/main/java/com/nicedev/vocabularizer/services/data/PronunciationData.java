package com.nicedev.vocabularizer.services.data;

public class PronunciationData {
	public final String pronunciationSource;
	public final int delayAfter;
	public final String accent;
	public final float limitPercent;

	public PronunciationData(String wordsToPronounce, String accent, int delayAfter, float limitPercent) {
		this.pronunciationSource = wordsToPronounce;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
	}

	public String toString() {
		return String.format("%s", pronunciationSource);
	}

}
