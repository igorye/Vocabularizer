package com.nicedev.vocabularizer.services.sound;

import javazoom.jl.player.Player;

import javax.sound.sampled.Clip;

public class AudioController implements StopableSoundPlayer {
	private final Object player;

	AudioController(Object player) {
		this.player = player;
	}

	AudioController() {
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