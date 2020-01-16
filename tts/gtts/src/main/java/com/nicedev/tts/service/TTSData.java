package com.nicedev.tts.service;

public class TTSData {
	public final String textToSpeak;
	public final int    delayAfter;
	public final String accent;
	public final float  limitPercent;
	public final String outFileName;

	public TTSData( String textToSpeak, String accent, String outFileName, int delayAfter, float limitPercent) {
		this.textToSpeak = textToSpeak;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = outFileName;
	}
	
	public TTSData( String textToSpeak, String accent, int delayAfter, float limitPercent) {
		this.textToSpeak = textToSpeak;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = null;
	}
	
	public String toString() {
		return textToSpeak;
	}

}
