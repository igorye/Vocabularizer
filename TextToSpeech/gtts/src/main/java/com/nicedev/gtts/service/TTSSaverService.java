package com.nicedev.gtts.service;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Service for saving TTSResponse into files
 */
public class TTSSaverService extends TTSService {

	private static final String DEFAULT_OUTPUT_FILENAME = "ttsOut.mp3";
	private BufferedOutputStream out;
	private String defaultOutFileName;
	private String actualOutputFileName;
	private boolean invalidateCache = false;

	public TTSSaverService() {
		this(DEFAULT_OUTPUT_FILENAME, DEFAULT_CACHE_SIZE);
	}

	public TTSSaverService(int cacheSize) {
		this(DEFAULT_OUTPUT_FILENAME, cacheSize);
	}

	public TTSSaverService(String defaultOutFileName, int cacheSize) {
		this(defaultOutFileName, cacheSize, false);
	}

	public TTSSaverService(String defaultOutFileName, int cacheSize, boolean bShowProgress) {
		super(cacheSize, bShowProgress);
		this.defaultOutFileName = defaultOutFileName;
		try {
			out = openBufferedOutputStream(defaultOutFileName);
			actualOutputFileName = defaultOutFileName;
		} catch (IOException e) {
			LOGGER.error("Could not write to {} {} {}", defaultOutFileName, e, e.getMessage());
		}
		inputQueue = new ArrayBlockingQueue<>(100);
		outputQueue = new ArrayBlockingQueue<>(DEFAULT_CACHE_SIZE);
		cache = new HashMap<>(DEFAULT_CACHE_SIZE);
//		executor.execute(savingTask);
		setName("SaverService");
		LOGGER.info("Saver service started");
		start();
	}

	public void run () {
		while (!isStopping && hasPendingData() && !isInterrupted()) {
			TTSData ttsData = null;
			try {
				if (isLimited)
					isStopping = executionLimit-- <= 0;
				ttsData = outputQueue.take();
				ensureOutputTarget(ttsData);
				if (invalidateCache) {
					Thread.yield();
				} else {
					if (showProgress) System.out.println(ttsData);
					byte[] cached = cache.remove(ttsData).get();
					out.write(cached, 0, cached.length);
				}
				LOGGER.info("writing: {}", ttsData);
			} catch (IOException eOut) {
				LOGGER.error("Could not write to {}: {}", actualOutputFileName, eOut);
				break;
			} catch (InterruptedException e) {
				LOGGER.info("Saver is interrupted while reading outputQueue");
			} catch (ExecutionException e) {
				LOGGER.error("Error has occurred while caching response \"{}\"", ttsData);
			}
		}
		try {
			out.close();
			LOGGER.info("{} saved", actualOutputFileName);
		} catch (IOException e) {
			LOGGER.error("{} {}", e.getMessage(), e.getCause());
		} finally {
			shutdown();
		}

	}

	@Override
	protected void invalidateCache() {
		invalidateCache = true;
	}

	@Override
	protected void validateCache() {
		invalidateCache = false;
	}

	private BufferedOutputStream openBufferedOutputStream(String outFileName) throws IOException {
		return new BufferedOutputStream(Files.newOutputStream(Paths.get(outFileName),
		                                                      StandardOpenOption.CREATE,
		                                                      StandardOpenOption.APPEND));
	}

	public void enqueue(String ttdData, String outFileName) {
		if (defaultOutFileName.equals(DEFAULT_OUTPUT_FILENAME) && touch(outFileName))
			defaultOutFileName = outFileName;
		super.enqueue(ttdData);
	}

	public void enqueue(String text, int delay) {
		super.enqueue(text, delay);
	}

	private boolean touch(String outFileName) {
		try (BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(outFileName), CREATE, APPEND)){
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private void ensureOutputTarget(TTSData TTSData) throws IOException {
		if (TTSData.outFileName != null && !TTSData.outFileName.equals(actualOutputFileName)) {
			out = openBufferedOutputStream(TTSData.outFileName);
			out.close();
			actualOutputFileName = TTSData.outFileName;
		}
	}

}
