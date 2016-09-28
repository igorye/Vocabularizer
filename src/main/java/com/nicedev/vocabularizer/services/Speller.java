package com.nicedev.vocabularizer.services;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import javax.net.ssl.HttpsURLConnection;
import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;


public class Speller extends Thread {

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

	static class HostGenerator {
		private List<String> suffixes;
		private int next = 0;
		private static HostGenerator instance;

		final private String hostName = "translate.google.%s";

		private HostGenerator() {
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

		class AddrTester extends Thread {
			List<String> suffixes;
			String domainSuffix;


			String hostName;

			public AddrTester(List<String> suffixes, String hostName, String domainSuffix) {
				this.domainSuffix = domainSuffix;
				this.suffixes = suffixes;
				this.hostName = hostName;
				start();
			}

			public void run() {
				boolean appendCOM = false;
				try {
					InetAddress hostAddr = InetAddress.getByName(String.format(hostName, domainSuffix));
					String name = hostAddr.getHostName();
					Thread.yield();
					if(!name.isEmpty())
						suffixes.add(domainSuffix);
				} catch (UnknownHostException e) {
					appendCOM = true;
				}
				if (appendCOM)
					try {
						domainSuffix = "com." + domainSuffix;
						InetAddress hostAddr = InetAddress.getByName(String.format(hostName, domainSuffix));
						String name = hostAddr.getHostName();
						Thread.yield();
						if(!name.isEmpty())
							suffixes.add(domainSuffix);
					} catch (UnknownHostException e) {
					}
			}


		}

		public static HostGenerator getInstance() {
			if (instance == null) instance = new HostGenerator();
			return instance;
		}
		public String nextHost() {
			return String.format(hostName, suffixes.get(next++ % suffixes.size()));
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

	public void switchAccent() {
		accent++;
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
				interrupted = true;
			}
		}
	}

}
