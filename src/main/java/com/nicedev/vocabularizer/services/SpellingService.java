package com.nicedev.vocabularizer.services;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;


public class SpellingService extends Thread {
	final static private String[] accents = {"GB", "US"};
	private RequestProxy requestProxy;

//	https://translate.google.al/translate_tts?ie=UTF-8&q=text&client=tw-ob&tl=en
	//First %s is a service host. Second is a query to TTS. Third is accent
	final private String requestFmt = "https://%s/translate_tts?ie=UTF-8&q=%s&client=tw-ob&tl=en-%s";
	final private String[] accents = {"GB", "US"};
	private int accent = 0;
	private int delay;

	private HostGenerator hGen;
	private TransferQueue<String> spellQueue;
	private boolean interrupted = false;
	private boolean isLimited = false;
	private int limit = 1;


	static class RequestProxy {
		//https://translate.google.al/translate_tts?ie=UTF-8&q=text&client=tw-ob&tl=en
		//First %s is a service host. Second is a query to TTS. Third is accent
		final private static String requestFmt = "https://%s/translate_tts?ie=UTF-8&q=%s&tl=en-%s&total=%d&idx=2&textlen=%d&client=tw-ob"/*&ttsspeed=150*/;
		final static private String hostNameFmt = "translate.google.%s";
		private static RequestProxy instance;
		private BlockingDeque<String> suffixes;
		private List<String> possibleSuffixes;
		private int next = 0;

		private RequestProxy() {
			possibleSuffixes = Collections.synchronizedList(new ArrayList<>());
			executor.execute(new SuffixEnumerator());
			while (possibleSuffixes.isEmpty()) Thread.yield();
			suffixes = new LinkedBlockingDeque<>();
			enumHosts();
			while (suffixes.size()==0)
				Thread.yield();
			next = new Random(System.currentTimeMillis()).nextInt(50);
		}

		private void enumHosts() {
			char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
			suffixes = Collections.synchronizedList(new ArrayList<>());
			StringBuilder domainSuffix = new StringBuilder();
			for(char a: chars)
				for(char b: chars) {
					domainSuffix.setLength(0);
					domainSuffix.append(a).append(b);
					AddrTester tester = new AddrTester(suffixes, hostName, domainSuffix.toString());
						Thread.yield();
				}
		}

		public void removeRecentHost() {
			suffixes.remove(next % suffixes.size());
			if (suffixes.size() <= 5)
				enumHosts();
			//System.out.printf("%d alive hosts%n", suffixes.size());
		}

		public void shutdown() {
			interrupted = true;
			executor.shutdownNow();
		}

		private void enumHosts() {
			executor.execute(new HostEnumerator());
		}

		class AddrTester implements Runnable {
			private String domainSuffix;

			public AddrTester(String domainSuffix) {
				this.domainSuffix = domainSuffix;
			}

			private boolean isValidDomainSuffix() throws UnknownHostException {
				Thread.yield();
				String hostName = String.format(RequestProxy.hostNameFmt, domainSuffix);
				InetAddress hostAddr = InetAddress.getByName(hostName);
				return !hostAddr.getHostName().isEmpty();
			}

			private boolean connectionAvailable() throws IOException {
				Thread.yield();
				String url = String.format("https://%s", String.format(RequestProxy.hostNameFmt, domainSuffix));
				HttpURLConnection conn = getRequestConnection(url);
				prepareConnProps(conn);
				conn.connect();
				int rc = conn.getResponseCode();
				conn.disconnect();
				return rc == 200;
			}

			//append domain suffix
			public void run() {
				boolean appendCOM = false;
				try {
					if (isValidDomainSuffix() && connectionAvailable())
						suffixes.add(domainSuffix);
				} catch (IOException e) {
					appendCOM = true;
				}
				if (appendCOM)
					try {
						domainSuffix = "com." + domainSuffix;
						if (isValidDomainSuffix() && connectionAvailable())
							suffixes.add(domainSuffix);
					} catch (IOException e) {
					}
			}


		}

		private String nextHost() {
			String suffix = "com";
			try {
				suffix = suffixes.take();
				suffixes.put(suffix);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return String.format(hostNameFmt, suffix);
		}

		private HttpURLConnection getRequestConnection(String request) throws IOException {
			URL url = new URL(request);
			return request.startsWith("https") ? (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
		}

		private void prepareConnProps(HttpURLConnection conn) {
			conn.setRequestProperty("Referer", "http://translate.google.com/");
			conn.setRequestProperty("User-Agent", "stagefright/1.2 (Linux;Android 5.0)");
			conn.setRequestProperty("Host", "translate.google.com");
		}

		//request mp3 stream
		private InputStream requestSpellingStream(SpellData spellData) {
			String request = spellData.spellingSource;
			boolean ttsRequest = false;
			try {
				if(!request.contains("://"))
					request = String.format(requestFmt, nextHost(), URLEncoder.encode(request, "UTF-8"), spellData.accent, request.length(), request.length());
			} catch (UnsupportedEncodingException e) {

				return  requestSpellingStream(spellData);
			}
			try {
				HttpURLConnection conn = getRequestConnection(request);
				ttsRequest = conn instanceof HttpsURLConnection;
				if(ttsRequest)
					prepareConnProps(conn);

				int streamSize = conn.getContentLength();
//				spellData.touch();
				return new BufferedInputStream(conn.getInputStream(), streamSize);
			} catch (IOException e) {
				if(ttsRequest) rejectRecentHost(e);
				return requestSpellingStream(spellData);
			}
		}

	}

	public Speller() {
		this(100);
	}

	public Speller(int delay) {
		hGen = HostGenerator.getInstance();
		spellQueue = new LinkedTransferQueue<>();
		this.delay = delay;
		start();
	}

	//spell .wav file at specified source
	public void spellURL(String url) {
		try (InputStream requestStream = requestSpellingStream(url)) {
			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(requestStream);
			Clip clip = AudioSystem.getClip();
			clip.open(audioInputStream);
			clip.start();
		} catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
		}
	}

	//spell mp3 stream requested from TTS service
	public void spellGTTS(String wordToSpell) {
		String request = null;
		try {
			request = String.format(requestFmt, hGen.nextHost(), URLEncoder.encode(wordToSpell, "UTF-8"), accents[accent % accents.length]);
		} catch (UnsupportedEncodingException e) {
			spellGTTS("Can't encode request");
		}
		try (InputStream spellStream = requestSpellingStream(request)){
			if (spellStream == null) {
				hGen.removeRecentHost();
				spellGTTS(wordToSpell);
				return;
			}
			Player playMP3 = new Player(spellStream);
			playMP3.play();
		} catch (JavaLayerException | IOException e) {
		}
		if(isLimited) limit--;
	}

	//request mp3 stream
	private InputStream requestSpellingStream(String request) {
		try {
			URL url = new URL(request);
			HttpURLConnection conn = request.startsWith("https") ? (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Referer", "http://translate.google.com/");
			conn.setRequestProperty("User-Agent", "stagefright/1.2 (Linux;Android 5.0)");
			int streamSize = conn.getContentLength();
			return new BufferedInputStream(conn.getInputStream(), streamSize);
		} catch (IOException e) {
//			spellQueue.addFirst("Ouch");
			//System.out.println("Host removed");
		}
		return null;
	}

	public void spell(String wordsToSpell) {
		spellQueue.add(wordsToSpell);
	}

	public void spell(String wordsToSpell, int newDelay) {
		delay = newDelay;
		spell(wordsToSpell);
	}

	class SpellSaver implements Runnable {
		private BufferedOutputStream out;
//		private String outFileName;

		SpellSaver(String outFileName) {
//			this.outFileName = outFileName;
			try {
				out = new BufferedOutputStream(Files.newOutputStream(Paths.get(outFileName),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void save(String wordsToSpell) {
			try {
				SpellData spellData = new SpellData(wordsToSpell, accents[accent % accents.length], 0, 100);
				saveQueue.put(spellData);
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}

		public void run() {
			while (!interrupted) {
				if(isLimited)
					interrupted = saveLimit-- == 0;
				SpellData spellData = null;
				try {
					spellData = saveQueue.take();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				byte[] cache = new byte[4096];
				try (InputStream in = new BufferedInputStream(requestProxy.requestSpellingStream(spellData));
				     ByteArrayOutputStream baoStream = new ByteArrayOutputStream()) {
					int read;
					while ((read = in.read(cache)) != -1)
						baoStream.write(cache, 0, read);
					cache = baoStream.toByteArray();
				} catch (IOException eIn) {
					eIn.printStackTrace();
				}
				try{
					out.write(cache, 0, cache.length);
					System.err.println(spellData);

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

	public void save(String toSpell, String outFileName) {
		if (!savers.containsKey(outFileName)) {
			SpellSaver saver = new SpellSaver(outFileName);
			executor.execute(saver);
			savers.putIfAbsent(outFileName, saver);
		} else
			savers.get(outFileName).save(toSpell);
	}

	public void switchAccent() {
		accent++;
		owerloadAccent = true;
	}

	public void resetAcent() {
		accent = 0;
		owerloadAccent = false;
	}
	
	public void release(int releaseIn) {
		limit = releaseIn;
		isLimited = true;
	}

	public void run() {
		while ((spellQueue.size() != 0 || !interrupted) && limit > 0) {
			try {
				if (spellQueue.size() == 0) Thread.sleep(delay);
				else {
					String toSpell = spellQueue.remove();
					if(toSpell.startsWith("http"))
						spellURL(toSpell);
					else
						spellGTTS(toSpell);
//					System.out.printf("%d words to spell%n", spellQueue.size());
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

}
