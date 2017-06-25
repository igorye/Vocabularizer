package com.nicedev.gtts.service;

import com.nicedev.gtts.proxy.TTSRequestProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;

public abstract class TTSService extends Thread {

	protected static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

	static final int DEFAULT_CACHE_SIZE = 5;
	final static private String[] accents = { "GB", "US" };
	private static final int LENGTH_THRESHOLD = 167; // below that length of text resulting audio ends with relatively long silence
	final boolean showProgress;
	private final CachingAgent cachingAgent;
	volatile boolean isStopping = false;
	final TTSRequestProxy requestProxy;
	private AsyncAppender appender = new AsyncAppender();
	BlockingQueue<TTSData> inputQueue;
	BlockingQueue<TTSData> outputQueue;
	Map<TTSData, Future<byte[]>> cache;
	volatile boolean isLimited = false;
	volatile int executionLimit = 0;
	private boolean overloadAccent = false;
	private int accent = 0;
	private boolean awaitInput = true;
	private final ExecutorService executor;
	private Map<Character, Integer> delays;


	TTSService(int cacheSize, boolean showProgress) {
		final int CACHE_SIZE = (cacheSize > 1 && cacheSize <= 10) ? cacheSize : DEFAULT_CACHE_SIZE;
		this.showProgress = showProgress;
		requestProxy = TTSRequestProxy.getInstance();
		cache = Collections.synchronizedMap(new LinkedHashMap<>());
		inputQueue = new ArrayBlockingQueue<>(100);
		outputQueue = new ArrayBlockingQueue<>(CACHE_SIZE);
		executor = Executors.newFixedThreadPool(CACHE_SIZE);
		cachingAgent = new CachingAgent();
		delays = new HashMap<>();
	}

	public TTSService(int cacheSize) {
		this(cacheSize, false);
	}

	public void enqueueAsync(String wordsToPronounce) {
		enqueueAsync(wordsToPronounce, 0);
	}

	public void enqueueAsync(String wordsToPronounce, int delay) {
		appender.enqueue(wordsToPronounce, delay);
	}

	public void enqueue(String wordsToPronounce) {
		enqueue(wordsToPronounce, 0);
	}

	public void enqueue(String text, int delay) {
		if (text.trim().isEmpty()) return;
		try {
			validateCache();
			String accent = text.contains("://") ? null : getActualAccent();
			for (String words : split(text, "\n", 0)) {
				if (isInterrupted()) break;
				int limitPercent = words.length() <= LENGTH_THRESHOLD ? 98 : 95;
				LOGGER.debug("Enqueuing: {}", words);
				inputQueue.put(new TTSData(words, accent, getDelay(words, delay), limitPercent));
			}
		} catch (InterruptedException e) {
			LOGGER.debug("Interrupted while adding to the queue. inputQueue.size={}", inputQueue.size());
		}
	}

	private static Collection<String> split(String line, String splitter, int nextRule) {
		String[] splitRules = { "(?<=\\\\.{2,3} )",
		                        "(?=\\()",
		                        "(?<=\\)[,:;]{0,1})",
		                        "(?> \'\\w+\' )",
		                        "(?> \"\\w+\" )",
		                        "(?<=[,:;])",
		                        "(?= - )",
		                        "\\s" };
		List<String> res = new ArrayList<>();
		for (String token : line.split(splitter)) {
			if (token.length() <= 200 || token.contains("://")) {
				if (!token.trim().isEmpty())
					res.add(token.trim());
			} else {
				Collection<String> shortTokens = split(token.replaceAll("\\s{2,}"," "), splitRules[nextRule], ++nextRule);
				nextRule = 0;
				StringBuilder tokenBuilder = new StringBuilder();
				for (String shortToken : shortTokens) {
					if (tokenBuilder.length() + shortToken.length() + 1 <= 200) {
						if (tokenBuilder.length() != 0)
							tokenBuilder.append(" ");
					} else {
						res.add(tokenBuilder.toString());
						tokenBuilder.setLength(0);
					}
					tokenBuilder.append(shortToken.trim());
				}
				res.add(tokenBuilder.toString());
			}
		}
		return res;
	}

	public TTSService setDelays(Map<Character, Integer> delays) {
		this.delays = delays;
		return this;
	}

	public TTSService setDelay(Character ending, Integer delay) {
		delays.put(ending, delay >= 0 ? delay : 200);
		return this;
	}

	private int getDelay(String text, int defaultDelay) {
		return delays.getOrDefault(text.charAt(text.length()-1), defaultDelay);
	}

	private class AsyncAppender {

		private Thread instance;

		AsyncAppender() {
			instance = new Thread();
		}
		void enqueue(String wordsToPronounce, int delay) {
			instance = new Thread(() -> TTSService.this.enqueue(wordsToPronounce, delay));
			instance.setDaemon(true);
			instance.start();
		}

		void clearQueue() {
			instance.interrupt();
			LOGGER.debug("Appender cleared");
		}

	}

	public void switchAccent() {
		accent++;
		overloadAccent = true;
	}

	public void resetAccent() {
		accent = 0;
		overloadAccent = false;
	}

	public void release() {
		release(inputQueue.size() + outputQueue.size());
	}

	public void release(int cyclesBeforeRelease) {
		awaitInput = false;
		executionLimit = Math.min(cyclesBeforeRelease, outputQueue.size() + inputQueue.size());
		isLimited = true;
		isStopping = executionLimit == 0;
		LOGGER.debug("releasing in {} cycles. isStopping = {}, inputQueue[{}], outputQueue[{}]",
		             cyclesBeforeRelease, isStopping, inputQueue.size(), outputQueue.size());
		if (isStopping || outputQueue.isEmpty() && inputQueue.isEmpty()) interrupt();
	}

	public void clearQueue() {
		appender.clearQueue();
		invalidateCache();
		inputQueue.clear();
		outputQueue.clear();
		cache.clear();
	}

	abstract protected void validateCache();

	abstract protected void invalidateCache();

	boolean hasPendingData() {
		return awaitInput || !outputQueue.isEmpty() || !inputQueue.isEmpty();
	}

	private String getActualAccent() {
		return accents[TTSService.this.accent % accents.length];
	}

	private class CachingAgent extends Thread {

		CachingAgent() {
			setName("Cacher");
			setDaemon(true);
			start();
		}

		public void run() {
			LOGGER.debug("Cacher started");
			while (!isStopping) {
				TTSData ttsData = null;
				try {
					if (inputQueue.isEmpty())
						LOGGER.debug("awaiting ttsData");
					ttsData = inputQueue.take();
					String data = ttsData.toString();
					LOGGER.debug("caching: {}[{}]",
					             data.length() > 15 ? data.substring(0,  15) : data, data.length());
					if (overloadAccent) ttsData = new TTSData(ttsData.pronunciationSource,
					                                          getActualAccent(),
					                                          ttsData.delayAfter,
					                                          ttsData.limitPercent);
					TTSData fTTSData = ttsData;
					Future<byte[]> cacheTask = executor.submit(() -> getTTSBytes(fTTSData));
					cache.put(ttsData, cacheTask);
					outputQueue.put(ttsData);
					LOGGER.debug("inputQueue[{}], outputQueue[{}], cache[{}]", inputQueue.size(), outputQueue.size(), cache.size());
				} catch (InterruptedException e) {
					String msg = String.format("Interrupted while %s ttsData {} {}", ttsData == null ? "awaiting" : "caching");
					LOGGER.info(msg, ttsData, e.getMessage());
					break;
				}
			}
			LOGGER.info("Cacher terminated");
		}

		private byte[] getTTSBytes(TTSData ttsData) {
			byte[] cache = new byte[4096];
			try (InputStream in = requestProxy.requestTTSStream(ttsData);
			     ByteArrayOutputStream baoStream = new ByteArrayOutputStream()) {
				int read;
				while ((read = in.read(cache)) != -1)
					baoStream.write(cache, 0, read);
				cache = baoStream.toByteArray();
			} catch (IOException | NullPointerException eIn) {
				LOGGER.error("Could not allocate cache: {}", eIn.getMessage());
			}
			return Arrays.copyOf(cache, (int) Math.floor(cache.length * ttsData.limitPercent / 100.f));
		}
	}

	void shutdown() {
		executor.shutdownNow();
		requestProxy.release();
	}
}