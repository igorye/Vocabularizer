package com.nicedev.tts.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

abstract public class GoogleRequestProxy {
	
	static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());
	
	final static private String HOST_NAME_FMT = "translate.google.%s";
	private static ExecutorService executor;
	final GTSRequest TTS_REQUEST_FMT;
	private final BlockingDeque<String> relevantSuffixes;
	private final List<String> possibleSuffixes;
	private int next;
	private boolean interrupted;
	private volatile boolean proceedSuffixes = false;
	private int switches;
	private int rejects;
	private int enums;

	GoogleRequestProxy( GTSRequest requestFmt ) {
		TTS_REQUEST_FMT = requestFmt;
		executor = Executors.newFixedThreadPool(10, r -> {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			return thread;
		});
		possibleSuffixes = Collections.synchronizedList(new ArrayList<>());
		relevantSuffixes = new LinkedBlockingDeque<>();
		executor.execute(new SuffixEnumerator());
		enumHosts();
		next = new Random(System.currentTimeMillis()).nextInt(50);
	}
	
	public void release() {
		executor.shutdownNow();
		LOGGER.info("Terminating proxy. Switches - {}, rejects - {}, enumerations - {}",
		            switches, rejects, enums);
	}
	
	private void enumHosts() {
		executor.execute(new HostEnumerator());
	}
	
	void rejectRecentHost(Exception e) {
		String suffix;
		if ((e instanceof SSLHandshakeException) || (e instanceof SSLProtocolException)) {
			try {
				suffix = relevantSuffixes.takeLast();
				possibleSuffixes.remove(suffix);
				infoReject(e);
				rejects++;
			} catch (InterruptedException eTake) {
				warnReject(eTake);
			}
		} else if (e.getMessage().contains("503")) try {
			relevantSuffixes.takeLast();
			infoReject(e);
		} catch (InterruptedException eTake) {
			warnReject(eTake);
		}
		if (relevantSuffixes.size() <= 10) {
			enumHosts();
		}
	}
	
	private void warnReject(InterruptedException e) {
		LOGGER.warn("Interrupted while rejecting host: {} {}", e, e.getMessage());
	}
	
	private void infoReject(Exception e) {
		LOGGER.info("Rejecting host ({} left): {} {}",
								relevantSuffixes.size(), e, e.getMessage());
	}
	
	String nextHost() {
		while (relevantSuffixes.size() < next) Thread.yield();
		if (next > 0) shiftStart(next);
		String suffix;
		try {
			suffix = relevantSuffixes.take();
			relevantSuffixes.put(suffix);
			switches++;
		} catch (InterruptedException e) {
			LOGGER.info("Interrupted while switching to next host: {}, switches = {}", e, switches);
			return nextHost();
		}
		String nextHost = String.format(HOST_NAME_FMT, suffix);
		LOGGER.info("redirecting to {} (among {})", nextHost, relevantSuffixes.size());
		return nextHost;
	}

	private void shiftStart(int next) {
		for (int i = 0; i < next; i++)
			try {
				relevantSuffixes.put(relevantSuffixes.take());
			} catch (InterruptedException e) {
				LOGGER.debug("interrupted while shifting to starting host");
			}
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
			conn.setConnectTimeout(5000);
		} catch (ProtocolException e) {
			LOGGER.info("Unable to set request method GET {}", e.getMessage());
		}
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36");
		conn.setRequestProperty("Accept-Charset", "UTF-8");
		conn.setRequestProperty("Cache-Control", "max-age=0");
		conn.setRequestProperty("Host", "translate.google.com");
	}
	
	class AddrTester implements Runnable {

		private String domainSuffix;
		private final CountDownLatch cdLatch;

		AddrTester(String domainSuffix, CountDownLatch cdLatch) {
			this.domainSuffix = domainSuffix;
			this.cdLatch = cdLatch;
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
			int size = relevantSuffixes.size();
			try {
				if (isValidDomainSuffix() && connectionAvailable()) relevantSuffixes.add(domainSuffix);
			} catch (IOException e) { appendCOM = true; }
			if (appendCOM)
				try {
					domainSuffix = "com." + domainSuffix;
					if (isValidDomainSuffix() && connectionAvailable()) relevantSuffixes.add(domainSuffix);
				} catch (IOException e) { /* NOP */ }
			cdLatch.countDown();
		}
		
	}
	
	class SuffixEnumerator implements Runnable {
		
		static final String LOWER_CASE_ALPHABET = "abcdefghijklmnopqrstuvwxyz";
		
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
			while (!proceedSuffixes) Thread.yield();
			LOGGER.info("Enumerating hosts...");
			CountDownLatch cdLatch = new CountDownLatch(possibleSuffixes.size());
			possibleSuffixes.forEach(suffix -> executor.execute(new AddrTester(suffix, cdLatch)));
			try {
				cdLatch.await();
				LOGGER.info("{} hosts available", relevantSuffixes.size());
				enums++;
			} catch (InterruptedException e) {
				LOGGER.warn("Interrupted awaiting AddrTester. Found {} host available", relevantSuffixes.size());
			}
		}
	}

}
