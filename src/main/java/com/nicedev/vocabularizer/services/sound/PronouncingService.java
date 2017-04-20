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


public class PronouncingService extends Thread {
	final static private String[] accents = {"GB", "US"};
	static volatile private boolean interrupted = false;
	private final TTSRequestProxy requestProxy;
	private final boolean showDebug;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private CachingAgent cAgent;
	private BlockingQueue<PronunciationData> pronouncingQueue;
	private BlockingQueue<PronunciationData> saveQueue;
	private BlockingQueue<PronunciationData> cacheQueue;
	private Map<PronunciationData, byte[]> cache;
	private boolean isLimited = false;
	volatile private int pronunciationLimit = 0;
	volatile private int saveLimit = 0;
	private boolean owerloadAccent = false;
	private int accent = 0;
	private Map<String, PronunciationSaver> savers;
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

	//pronounce .wav file at specified source
	public void pronounceURL(PronunciationData pronunciationData) {
		try (InputStream requestStream = requestProxy.requestPronunciationStream(pronunciationData);
		     AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(requestStream)) {
			Clip clip = AudioSystem.getClip();
			activePlayer = new AudioController(clip);
			clip.open(audioInputStream);
			if(!stoppedPlaying)
				clip.start();
			clip.addLineListener(clipStopHandler(clip));
		} catch (IOException | UnsupportedAudioFileException | LineUnavailableException | NullPointerException e) {
//			System.err.println("URLspeller interrupted");
		}
	}
	
	private LineListener clipStopHandler(Clip clip) {
		return event -> {
			if (event.getType() == LineEvent.Type.STOP)
				clip.close();
			clip.removeLineListener(clipStopHandler(clip));
		};
	}
	
	//pronounce mp3 stream requested from TTS service
	public void pronounceGTTS(PronunciationData pronunciationData) {
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
//			System.err.printf("GTTS interrupted");
			System.err.printf("data recieved %s%n", pronunciationData.pronunciationSource);
		}
	}

	public void pronounce(String wordsToPronounce) {
		pronounce(wordsToPronounce, 0);
	}

	public void pronounce(String wordsToPronounce, int delay) {
		stoppedPlaying = false;
		try {
			String accent = wordsToPronounce.contains("://") ? null : accents[this.accent % accents.length];
			for(String words: split(wordsToPronounce, "\n", 0))
				pronouncingQueue.put(new PronunciationData(words, accent, delay, 98));
		} catch (InterruptedException e) {
			interrupted = true;
			stoppedPlaying = true;
//			System.err.println("pronounce interrupted");
		}
	}

	public void save(String toPronounce, String outFileName) {
		if (!savers.containsKey(outFileName)) {
			PronunciationSaver saver = new PronunciationSaver(outFileName);
			executor.execute(saver);
			savers.putIfAbsent(outFileName, saver);
		} else
			savers.get(outFileName).save(toPronounce);
	}

	public void switchAccent() {
		accent++;
		owerloadAccent = true;
	}

	public void resetAcent() {
		accent = 0;
		owerloadAccent = false;
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
		while (!interrupted && (!cacheQueue.isEmpty() || !pronouncingQueue.isEmpty() || hasInput)) {
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
	
	private class CachingAgent extends Thread {
		
		CachingAgent() {
			setName("Cacheer");
			start();
		}
		
		public void run() {
			PronunciationData pronunciationData = new PronunciationData(null, null, 0, 0);
			while (!interrupted)
				try {
					pronunciationData = pronouncingQueue.take();
//					System.err.println("cacheer took data");
					if (owerloadAccent) pronunciationData =
							                    new PronunciationData(pronunciationData.pronunciationSource,
									                                         accents[accent % accents.length],
									                                         pronunciationData.delayAfter,
									                                         pronunciationData.limitPercent);
					cache.put(pronunciationData, cacheFromInputStream(
							requestProxy.requestPronunciationStream(pronunciationData),
							pronunciationData.limitPercent));
					cacheQueue.put(pronunciationData);
//					System.err.printf("(%d, %d)%n", cacheQueue.size(), cache.size());
				} catch (InterruptedException e) {
//					System.err.println("cacheer interrupted");
					break;
				} catch (IOException e) {
					System.err.println("failed to cache " + pronunciationData.pronunciationSource);
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
	
	class PronunciationSaver implements Runnable {
		private BufferedOutputStream out;
//		private String outFileName;
		
		PronunciationSaver(String outFileName) {
//			this.outFileName = outFileName;
			try {
				out = new BufferedOutputStream(Files.newOutputStream(Paths.get(outFileName),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void save(String wordsToPronounce) {
			try {
				PronunciationData pronunciationData = new PronunciationData(wordsToPronounce, accents[accent % accents.length], 0, 100);
				saveQueue.put(pronunciationData);
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
					e.printStackTrace();
				}
				byte[] cache = new byte[4096];
				try (InputStream in = requestProxy.requestPronunciationStream(pronunciationData);
				     ByteArrayOutputStream baoStream = new ByteArrayOutputStream()) {
					int read;
					while ((read = in.read(cache)) != -1)
						baoStream.write(cache, 0, read);
					cache = baoStream.toByteArray();
				} catch (IOException | NullPointerException eIn) {
					eIn.printStackTrace();
				}
				try {
					out.write(cache, 0, cache.length);
					System.err.println(pronunciationData);
				} catch (IOException eOut) {
					interrupted = true;
				}
			}
			try {
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
