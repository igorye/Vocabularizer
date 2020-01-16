package com.nicedev.tts.service;

import com.nicedev.tts.audio.StoppableAudioPlayer;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class TTSPlaybackService extends TTSService {

	private          StoppableAudioPlayer activePlayer    = new StoppableAudioPlayer();
	private volatile boolean              invalidateCache = false;


	public TTSPlaybackService(int cacheSize, boolean showProgress) {
		super(cacheSize, showProgress);
		cache = new ConcurrentHashMap<>();
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
				LOGGER.info("About to play: {}", ttsData);
				if (invalidateCache) {
					yield();
				} else {
					if (ttsData.textToSpeak.contains("://"))
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

	// enqueue wav file at specified source
	private void playURL(TTSData pronunciationData) {
		try (InputStream requestStream = requestProxy.requestTTSStream(pronunciationData);
		     AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(requestStream)) {
			Clip clip = AudioSystem.getClip();
			clip.open(audioInputStream);
			clip.addLineListener(clipStopHandler(clip));
			activePlayer = new StoppableAudioPlayer(clip::stop);
			if (!invalidateCache) {
				clip.start();
			}
		} catch (IOException | UnsupportedAudioFileException | LineUnavailableException | NullPointerException e) {
			LOGGER.error("URLplayer: ", e);
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
			isStopping = executionLimit.getAndDecrement() <= 0;
		try (InputStream pronunciationStream = new ByteArrayInputStream(cache.remove(ttsData).get())) {
//			logStreamBytes(ttsData, pronunciationStream);
			Player playerMP3 = new Player(pronunciationStream);
			activePlayer = new StoppableAudioPlayer(playerMP3::close);
			if (!invalidateCache) {
				if (showProgress) System.out.println(ttsData);
				playerMP3.play();
			}
			sleep(ttsData.delayAfter);
		} catch (JavaLayerException | IOException | NullPointerException e) {
			LOGGER.info("Error has occurred trying play {}%n", ttsData.textToSpeak);
		} catch (InterruptedException e) {
			LOGGER.info("GTTS isStopping");
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	private void logStreamBytes( TTSData ttsData, InputStream stream ) {
		if(stream.markSupported()) {
			stream.mark(Integer.MAX_VALUE);
			try {
				LOGGER.debug("Stream bytes for tts of {}: {}",
							 ttsData,
							 toHexString(stream.readAllBytes()));
				stream.reset();
			} catch (IOException e) {/*NOP*/}
		}
	}

	private String toHexString( byte[] bytes ) {
		StringBuilder hex = new StringBuilder(bytes.length);
		for(byte bt: bytes) {
			hex.append(String.format(" %H", Byte.toUnsignedInt(bt)));
		}
		return hex.toString().replaceFirst(" ", "");
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
