package com.nicedev.gtts.service;

public class TTSData {
	public final String pronunciationSource;
	public final int delayAfter;
	public final String accent;
	public final float limitPercent;
	public final String outFileName;
	
	public TTSData(String pronunciationSource, String accent, String outFileName, int delayAfter, float limitPercent) {
		this.pronunciationSource = pronunciationSource;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = outFileName;
	}
	
	public TTSData(String pronunciationSource, String accent, int delayAfter, float limitPercent) {
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
