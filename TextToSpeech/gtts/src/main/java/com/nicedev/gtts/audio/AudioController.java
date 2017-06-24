package com.nicedev.gtts.audio;

import javazoom.jl.player.Player;

import javax.sound.sampled.Clip;

public class AudioController implements StoppableAudioPlayer {
	private final Object player;

	public AudioController(Object player) {
		this.player = player;
	}

	public AudioController() {
		this(null);
	}

	@Override
	public void stop() {
		if (player instanceof Clip) {
			Clip clip = (Clip) player;
			if (clip.isActive()) clip.stop();
		}
		if(player instanceof Player) ((Player) player).close();
	}
}