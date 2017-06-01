package com.nicedev.vocabularizer.services.sound;

import com.nicedev.vocabularizer.services.proxy.TTSRequestProxy;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.nicedev.util.SimpleLog.log;


public class PronouncingService extends Thread {
	final static private String[] accents = {"GB", "US"};
	static volatile private boolean interrupted = false;
	private final TTSRequestProxy requestProxy;
	private final boolean showDebug;
	private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
	private final CachingAgent cAgent;
	private final BlockingQueue<PronunciationData> pronouncingQueue;
	private final BlockingQueue<PronunciationData> saveQueue;
	private final BlockingQueue<PronunciationData> cacheQueue;
	private final Map<PronunciationData, byte[]> cache;
	private boolean isLimited = false;
	volatile private int pronunciationLimit = 0;
	volatile private int saveLimit = 0;
	private boolean overloadAccent = false;
	private int accent = 0;
	private final Map<String, PronunciationSaver> savers;
	private boolean hasInput = true;
	private StopableSoundPlayer activePlayer = new AudioController();
	private volatile boolean stoppedPlaying = false;


	public PronouncingService(boolean showDebug) {
		this(10, showDebug);
	}

	public PronouncingService(int cacheSize, boolean showDebug) {
		savers = new HashMap<>();
		this.showDebug = showDebug;
		requestProxy = TTSRequestProxy.getInstance();
		cache = Collections.synchronizedMap(new LinkedHashMap<>());
		pronouncingQueue = new ArrayBlockingQueue<>(100);
//		pronouncingQueue = new LinkedBlockingDeque<>();
		saveQueue = new ArrayBlockingQueue<>(1);
		cacheQueue = new ArrayBlockingQueue<>(cacheSize);
		cAgent = new CachingAgent();
		setName("PronouncingService");
		start();
	}
	
	private static Collection<String> split(String line, String splitter, int nextRule) {
		String[] splitRules = {"(?<=\\\\.{2,3} )", "(?=\\()", "(?<=\\)[,:;]{0,1})", "(?<=[,:;])", "(?= - )", "\\s"};
		List<String> res = new ArrayList<>();
		for (String token : line.split(splitter)) {
			if (token.length() <= 200 || token.contains("://")) {
				if (!token.trim().isEmpty())
					res.add(token.trim());
			} else {
				Collection<String> shortTokens = split(token, splitRules[nextRule], ++nextRule);
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

	// pronounce wav file at specified source
	private void pronounceURL(PronunciationData pronunciationData) {
		try (InputStream requestStream = requestProxy.requestPronunciationStream(pronunciationData);
		     AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(requestStream)) {
			Clip clip = AudioSystem.getClip();
			activePlayer = new AudioController(clip);
			clip.open(audioInputStream);
			if(!stoppedPlaying)
				clip.start();
			clip.addLineListener(clipStopHandler(clip));
		} catch (IOException | UnsupportedAudioFileException | LineUnavailableException | NullPointerException e) {
			log("URLspeller interrupted");
		}
	}
	
	private LineListener clipStopHandler(Clip clip) {
		return event -> {
			if (event.getType() == LineEvent.Type.STOP)
				clip.close();
			clip.removeLineListener(clipStopHandler(clip));
		};
	}
	
	// pronounce mp3 stream requested from TTS service
	private void pronounceGTTS(PronunciationData pronunciationData) {
		if(isLimited)
			interrupted = pronunciationLimit-- <= 0;
		try (InputStream pronunciationStream = new ByteArrayInputStream(cache.get(pronunciationData))){
			Player playerMP3 = new Player(pronunciationStream);
			if (showDebug) System.err.println(pronunciationData);
			activePlayer = new AudioController(playerMP3);
			if(!stoppedPlaying)
				playerMP3.play();
			Thread.sleep(pronunciationData.delayAfter);
			cache.remove(pronunciationData);
		} catch (JavaLayerException | IOException | InterruptedException | NullPointerException e) {
			log("GTTS interrupted");
			log("data recieved on exception == %s%n", pronunciationData.pronunciationSource);
		}
	}

	public void pronounce(String wordsToPronounce) {
		pronounce(wordsToPronounce, 0);
	}

	public void pronounce(String wordsToPronounce, int delay) {
		if (wordsToPronounce.trim().isEmpty()) return;
		stoppedPlaying = false;
		try {
			String accent = wordsToPronounce.contains("://") ? null : getActualAccent();
			for(String words: split(wordsToPronounce, "\n", 0))
				pronouncingQueue.put(new PronunciationData(words, accent, delay, 98));
		} catch (InterruptedException e) {
			interrupted = true;
			stoppedPlaying = true;
			log("pronounce interrupted");
		}
	}

	public void save(String toPronounce, String outFileName) {
		if (!savers.containsKey(outFileName)) {
			PronunciationSaver saver = new PronunciationSaver(outFileName);
			saveExecutor.execute(saver);
			savers.putIfAbsent(outFileName, saver);
		} else
			savers.get(outFileName).save(toPronounce);
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
		release(Math.max(pronouncingQueue.size()+cacheQueue.size(), saveQueue.size()));
	}
	
	public void release(int stepsBeforeRelease) {
		hasInput = false;
		pronunciationLimit = Math.min(stepsBeforeRelease, pronouncingQueue.size());
		saveLimit = Math.min(stepsBeforeRelease, saveQueue.size());
		isLimited = true;
		interrupted = (pronunciationLimit == 0 && saveLimit == 0);
		if (interrupted || cacheQueue.isEmpty() && pronouncingQueue.isEmpty()) interrupt();
	}

	public void clear() {
		stoppedPlaying = true;
		pronouncingQueue.clear();
		cacheQueue.clear();
		activePlayer.stop();
		cache.clear();
	}

	public void run() {
		while (!interrupted && hasSomeData()) {
			try {
				PronunciationData toPronounce = cacheQueue.take();
				if (!stoppedPlaying)
					if (toPronounce.pronunciationSource.contains("://"))
						pronounceURL(toPronounce);
					else
						pronounceGTTS(toPronounce);
			} catch (InterruptedException e) {
				if(cAgent.isAlive()) cAgent.interrupt();
				requestProxy.release();
//				System.err.println("speller interrupted");
				break;
			}
//			System.err.printf("(queue=%d, cache=%d)%n", cacheQueue.size(), cache.size());
		}
		if(cAgent.isAlive()) cAgent.interrupt();
		requestProxy.release();
	}
	
	private boolean hasSomeData() {
		return hasInput || !cacheQueue.isEmpty() || !pronouncingQueue.isEmpty();
	}
	
	private class CachingAgent extends Thread {
		
		CachingAgent() {
			setName("Cacheer");
			start();
		}
		
		public void run() {
			PronunciationData pData = null;
			while (!interrupted)
				try {
					pData = pronouncingQueue.take();
					if (overloadAccent) pData = new PronunciationData(pData.pronunciationSource,
							                                                 getActualAccent(),
							                                                 pData.delayAfter,
							                                                 pData.limitPercent);
					cache.put(pData,
										cacheFromInputStream(requestProxy.requestPronunciationStream(pData), pData.limitPercent));
					cacheQueue.put(pData);
				} catch (InterruptedException e) {
					log("Caching agent interrupted");
					break;
				} catch (IOException e) {
					log("failed to cache %s", pData.pronunciationSource);
				}
		}
		
		private byte[] cacheFromInputStream(InputStream inputStream, float percents) throws IOException {
			byte[] cache = new byte[4096];
			if (inputStream == null) return cache;
			ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
			int read;
			while ((read = inputStream.read(cache)) != -1) {
				baoStream.write(cache, 0, read);
			}
			cache = baoStream.toByteArray();
			return Arrays.copyOf(cache, (int) Math.floor(cache.length * percents / 100.f));
		}
	}
	
	private String getActualAccent() {
		return accents[PronouncingService.this.accent % accents.length];
	}
	
	class PronunciationSaver implements Runnable {
		private BufferedOutputStream out;
		private final String defaultOutFileName;
		private String actualOutputFileName;
		
		PronunciationSaver(String outFileName) {
			this.defaultOutFileName = outFileName;
			try {
				out = openBufferedOutputStream(outFileName);
			} catch (IOException e) {
				log("%s %s", e.getMessage(), e.getCause());
			}
		}
		
		private BufferedOutputStream openBufferedOutputStream(String outFileName) throws IOException {
			return new BufferedOutputStream(Files.newOutputStream(Paths.get(outFileName),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND));
		}
		
		public void save(String wordsToPronounce) {
			try {
				saveQueue.put(new PronunciationData(wordsToPronounce, getActualAccent(), defaultOutFileName, 0, 100));
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		
		public void save(String wordsToPronounce, String outFileName) {
			try {
				saveQueue.put(new PronunciationData(wordsToPronounce, getActualAccent(), outFileName, 0, 100));
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		
		public void run() {
			while (!interrupted) {
				if (isLimited)
					interrupted = saveLimit-- <= 0;
				PronunciationData pronunciationData = null;
				try {
					pronunciationData = saveQueue.take();
				} catch (InterruptedException e) {
					log("%s %s", e.getMessage(), e.getCause());
				}
				byte[] cache = new byte[4096];
				try (InputStream in = requestProxy.requestPronunciationStream(pronunciationData);
				     ByteArrayOutputStream baoStream = new ByteArrayOutputStream()) {
					int read;
					while ((read = in.read(cache)) != -1)
						baoStream.write(cache, 0, read);
					cache = baoStream.toByteArray();
				} catch (IOException | NullPointerException eIn) {
					log(eIn.getMessage() + eIn.getCause());
				}
				try {
					ensureOutputTarget(pronunciationData);
					out.write(cache, 0, cache.length);
					log("%s", pronunciationData);
				} catch (IOException eOut) {
					log(eOut.getMessage() + eOut.getCause());
					interrupted = true;
				}
			}
			try {
				out.close();
			} catch (IOException e) {
				log("%s %s", e.getMessage(), e.getCause());
			}
		}
		
		private void ensureOutputTarget(PronunciationData pronunciationData) throws IOException {
			if (!pronunciationData.outFileName.equals(actualOutputFileName)) {
				out.close();
				out = openBufferedOutputStream(pronunciationData.outFileName);
				actualOutputFileName = pronunciationData.outFileName;
			}
		}
		
	}
	
}
