package com.nicedev.gtts.service;

import com.nicedev.gtts.audio.AudioController;
import com.nicedev.gtts.audio.StoppableAudioPlayer;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;

public class TTSPlaybackService extends TTSService {

	private StoppableAudioPlayer activePlayer = new AudioController();
	private volatile boolean invalidateCache = false;
	private boolean showProgress;


	public TTSPlaybackService(int cacheSize, boolean showProgress) {
		super(cacheSize, showProgress);
		this.showProgress = showProgress;
		cache = Collections.synchronizedMap(new LinkedHashMap<>());
		inputQueue = new ArrayBlockingQueue<>(100);
		outputQueue = new ArrayBlockingQueue<>(cacheSize);
		setName("PlaybackService");
		LOGGER.info("pronouncing service started");
		start();
	}

	public void run() {
		while (!isStopping && hasPendingData() && !isInterrupted()) {
			try {
				if (outputQueue.isEmpty())
					LOGGER.info("awaiting ttsData");
				TTSData ttsData = outputQueue.take();
				String data = ttsData.toString();
				LOGGER.info("About to play: {}[{}]",
				            data.length() > 15 ? data.substring(0, 15) : data, data.length());
				if (invalidateCache) {
					Thread.yield();
				} else {
					if (ttsData.pronunciationSource.contains("://"))
						playURL(ttsData);
					else
						playGTTS(ttsData);
				}
			} catch (InterruptedException e) {
				LOGGER.debug("Interrupted while playing. outputQueue[{}], isStopping = {}, hasPendingData = {}",
				             outputQueue.size(), isStopping, hasPendingData());
				break;
			}
		}
		shutdown();
		LOGGER.info("pronouncing service stopped");
		LOGGER.debug("outputQueue[{}], isStopping = {}, hasPendingData = {}", outputQueue.size(), isStopping, hasPendingData());
	}

	public TTSPlaybackService(int cacheSize) {
		this(cacheSize, false);
	}

	// enque wav file at specified source
	private void playURL(TTSData pronunciationData) {
		try (InputStream requestStream = requestProxy.requestTTSStream(pronunciationData);
		     AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(requestStream)) {
			Clip clip = AudioSystem.getClip();
			activePlayer = new AudioController(clip);
			clip.open(audioInputStream);
			if (!invalidateCache)
				clip.start();
			clip.addLineListener(clipStopHandler(clip));
		} catch (IOException | UnsupportedAudioFileException | LineUnavailableException | NullPointerException e) {
			LOGGER.error("URLspeller: {}", e.toString());
		}
	}

	private LineListener clipStopHandler(Clip clip) {
		return event -> {
			if (event.getType() == LineEvent.Type.STOP)
				clip.close();
			clip.removeLineListener(clipStopHandler(clip));
		};
	}

	// enque mp3 stream requested from TTS service
	private void playGTTS(TTSData ttsData) {
		if (isLimited)
			isStopping = executionLimit-- <= 0;
		try (InputStream pronunciationStream = new ByteArrayInputStream(cache.remove(ttsData).get())) {
			Player playerMP3 = new Player(pronunciationStream);
			activePlayer = new AudioController(playerMP3);
			if (!invalidateCache) {
				if (showProgress) System.out.println(ttsData);
				playerMP3.play();
			}
			Thread.sleep(ttsData.delayAfter);
		} catch (JavaLayerException | IOException | NullPointerException e) {
			LOGGER.info("Error has occurred trying play {}%n", ttsData.pronunciationSource);
		} catch (InterruptedException e) {
			LOGGER.info("GTTS isStopping");
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void invalidateCache() {
		invalidateCache = true;
		activePlayer.stop();
		LOGGER.debug("inputQueue.size={}, outputQueue.size={}, cache.size={}",
		             inputQueue.size(), outputQueue.size(), cache.size());
	}

	@Override
	protected void validateCache() {
		invalidateCache = false;
	}
}
