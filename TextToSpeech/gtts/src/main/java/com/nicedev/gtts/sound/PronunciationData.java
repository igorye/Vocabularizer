package com.nicedev.gtts.sound;

public class PronunciationData {
	public final String pronunciationSource;
	public final int delayAfter;
	public final String accent;
	public final float limitPercent;
	public final String outFileName;
	
	public PronunciationData(String pronunciationSource, String accent, String outFileName, int delayAfter, float limitPercent) {
		this.pronunciationSource = pronunciationSource;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = outFileName;
	}
	
	public PronunciationData(String pronunciationSource, String accent, int delayAfter, float limitPercent) {
		this.pronunciationSource = pronunciationSource;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = null;
	}
	
	public String toString() {
		return String.format("%s", pronunciationSource);
	}

}
