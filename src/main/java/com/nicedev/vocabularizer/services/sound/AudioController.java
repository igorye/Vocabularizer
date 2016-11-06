package com.nicedev.vocabularizer.services.sound;

import javazoom.jl.player.Player;

import javax.sound.sampled.Clip;

public class AudioController implements StopableSoundPlayer {
	private Object player;

	public AudioController(Object player) {
		this.player = player;
	}

	public AudioController() {
		this(null);
	}

	@Override
	public void stop() {
		if(player instanceof Clip) ((Clip) player).stop();
		if(player instanceof Player) ((Player) player).close();
	}
}