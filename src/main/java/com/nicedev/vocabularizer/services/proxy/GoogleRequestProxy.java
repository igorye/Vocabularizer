package com.nicedev.vocabularizer.services.proxy;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import static com.nicedev.util.SimpleLog.log;

abstract public class GoogleRequestProxy {
	
	final static private String HOST_NAME_FMT = "translate.google.%s";
	private static ExecutorService executor;
	final GTSRequest TTS_REQUEST_FMT;
	private final BlockingDeque<String> relevantSuffixes;
	private final List<String> possibleSuffixes;
	private int next = 0;
	private boolean interrupted;
	private volatile boolean proceedSuffixes = false;
	
	GoogleRequestProxy(GTSRequest requestFmt) {
		TTS_REQUEST_FMT = requestFmt;
		executor = Executors.newFixedThreadPool(10, r -> {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			return thread;
		});
//        executor = Executors.newCachedThreadPool();
		possibleSuffixes = Collections.synchronizedList(new ArrayList<>());
		executor.execute(new SuffixEnumerator());
		while (!proceedSuffixes) Thread.yield();
		relevantSuffixes = new LinkedBlockingDeque<>();
		enumHosts();
		next = new Random(System.currentTimeMillis()).nextInt(50);
	}
	
	public void release() {
		interrupted = true;
		executor.shutdownNow();
	}
	
	private void enumHosts() {
		executor.execute(new HostEnumerator());
	}
	
	void rejectRecentHost(Exception e) {
		String suffix = "";
		if ((e instanceof SSLHandshakeException) || (e instanceof SSLProtocolException)) {
			try {
				suffix = relevantSuffixes.takeLast();
				possibleSuffixes.remove(suffix);
			} catch (InterruptedException eTake) {
				eTake.printStackTrace();
			}
		} else if (e.getMessage().contains("503")) try {
			relevantSuffixes.takeLast();
		} catch (InterruptedException eTake) {
			eTake.printStackTrace();
		}
		if (relevantSuffixes.size() <= 3)
			enumHosts();
		log("[%d] | %s%n", relevantSuffixes.size(), e.getCause().getMessage());
	}
	
	String nextHost() {
		while (relevantSuffixes.isEmpty()) Thread.yield();
		String suffix = "com";
		try {
			suffix = relevantSuffixes.take();
			relevantSuffixes.put(suffix);
		} catch (InterruptedException e) {
			log("%s %s", e.getMessage(), e.getCause());
		}
		return String.format(HOST_NAME_FMT, suffix);
	}
	
	HttpURLConnection getRequestConnection(String request) throws IOException {
		URL url = new URL(request);
		return request.startsWith("https") ? (HttpsURLConnection) url.openConnection()
				       : (HttpURLConnection) url.openConnection();
	}
	
	void prepareConnProps(HttpURLConnection conn) {
		conn.setRequestProperty("Referer", "https://translate.google.com/");
		try {
			conn.setRequestMethod("GET");
		} catch (ProtocolException e) {
			log("%s %s", e.getMessage(), e.getCause());
		}
		conn.setRequestProperty("User-Agent", /*"Chrome/54.0.2840.59"*/"stagefright/1.2 (Linux;Android 5.0)");
		conn.setRequestProperty("Accept-Charset", "UTF-8");
		conn.setRequestProperty("Cache-Control", "max-age=0");
		conn.setRequestProperty("Host", "translate.google.com");
	}
	
	class AddrTester implements Runnable {
		
		private String domainSuffix;
		
		public AddrTester(String domainSuffix) {
			this.domainSuffix = domainSuffix;
		}
		
		private boolean isValidDomainSuffix() throws UnknownHostException {
			Thread.yield();
			String hostName = String.format(GoogleRequestProxy.HOST_NAME_FMT, domainSuffix);
			InetAddress hostAddress = InetAddress.getByName(hostName);
			return !hostAddress.getHostName().isEmpty();
		}
		
		private boolean connectionAvailable() throws IOException {
			Thread.yield();
			String url = String.format("https://%s", String.format(GoogleRequestProxy.HOST_NAME_FMT, domainSuffix));
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
				if (isValidDomainSuffix() && connectionAvailable()) relevantSuffixes.add(domainSuffix);
			} catch (IOException e) { appendCOM = true; }
			if (appendCOM)
				try {
					domainSuffix = "com." + domainSuffix;
					if (isValidDomainSuffix() && connectionAvailable()) relevantSuffixes.add(domainSuffix);
				} catch (IOException e) { /* NOP */ }
		}
		
	}
	
	class SuffixEnumerator implements Runnable {
		
		public static final String LOWER_CASE_ALPHABET = "abcdefghijklmnopqrstuvwxyz";
		
		public void run() {
			final char[] chars = LOWER_CASE_ALPHABET.toCharArray();
			StringBuilder domainSuffix = new StringBuilder();
			for (char a : chars)
				for (char b : chars) {
					domainSuffix.setLength(0);
					domainSuffix.append(a).append(b);
					possibleSuffixes.add(domainSuffix.toString());
				}
			proceedSuffixes = true;
		}
	}
	
	class HostEnumerator implements Runnable {
		public void run() {
			possibleSuffixes.forEach(suffix -> executor.execute(new AddrTester(suffix)));
		}
	}

}
