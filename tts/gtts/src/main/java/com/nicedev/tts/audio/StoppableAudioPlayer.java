package com.nicedev.tts.audio;

public class StoppableAudioPlayer {
	private final Runnable stopAction;

	public StoppableAudioPlayer( Runnable stopAction ) {
		this.stopAction = stopAction;
	}

	public StoppableAudioPlayer() {
		this(null);
	}

	public void stop() {
		if (stopAction != null) stopAction.run();
	}
}