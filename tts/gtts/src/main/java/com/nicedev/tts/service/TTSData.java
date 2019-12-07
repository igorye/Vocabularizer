package com.nicedev.tts.service;

public class TTSData {
	public final String audioSource;
	public final int delayAfter;
	public final String accent;
	public final float limitPercent;
	public final String outFileName;
	
	public TTSData(String audioSource, String accent, String outFileName, int delayAfter, float limitPercent) {
		this.audioSource = audioSource;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = outFileName;
	}
	
	public TTSData(String audioSource, String accent, int delayAfter, float limitPercent) {
		this.audioSource = audioSource;
		this.delayAfter = delayAfter;
		this.accent = accent;
		this.limitPercent = limitPercent;
		this.outFileName = null;
	}
	
	public String toString() {
		return audioSource;
	}

}
