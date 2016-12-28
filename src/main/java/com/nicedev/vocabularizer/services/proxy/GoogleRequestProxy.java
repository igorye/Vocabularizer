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

abstract public class GoogleRequestProxy {
	
	final static private String HOST_NAME_FMT = "translate.google.%s";
	static protected ExecutorService executor;
	protected final GTSRequest REQUEST_FMT;
	private BlockingDeque<String> suffixes;
	private List<String> possibleSuffixes;
	private int next = 0;
	private boolean interrupted;

	protected GoogleRequestProxy(GTSRequest requestFmt) {
		REQUEST_FMT = requestFmt;
		executor = Executors.newFixedThreadPool(100);
		possibleSuffixes = Collections.synchronizedList(new ArrayList<>());
		executor.execute(new SuffixEnumerator());
		while (possibleSuffixes.isEmpty()) Thread.yield();
		suffixes = new LinkedBlockingDeque<>();
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
	
	public void rejectRecentHost(Exception e) {
		String suffix = "";
		if ((e instanceof SSLHandshakeException) || (e instanceof SSLProtocolException)) {
			try {
				suffix = suffixes.takeLast();
				possibleSuffixes.remove(suffix);
			} catch (InterruptedException eTake) {
				eTake.printStackTrace();
			}
		} else if (e.getMessage().contains("503")) try {
			suffixes.takeLast();
		} catch (InterruptedException eTake) {
			eTake.printStackTrace();
		}
		if (suffixes.size() <= 3)
			enumHosts();
		System.out.printf("[%d] | %s%n", suffixes.size(), e.getCause().getMessage());
	}
	
	protected String nextHost() {
		while (suffixes.isEmpty()) Thread.yield();
		String suffix = "com";
		try {
			suffix = suffixes.take();
			suffixes.put(suffix);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return String.format(HOST_NAME_FMT, suffix);
	}
	
	protected HttpURLConnection getRequestConnection(String request) throws IOException {
		URL url = new URL(request);
		return request.startsWith("https") ? (HttpsURLConnection) url.openConnection()
				       : (HttpURLConnection) url.openConnection();
	}
	
	protected void prepareConnProps(HttpURLConnection conn) {
		conn.setRequestProperty("Referer", "https://translate.google.com/");
		try {
			conn.setRequestMethod("GET");
		} catch (ProtocolException e) {
			e.printStackTrace();
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
			InetAddress hostAddr = InetAddress.getByName(hostName);
			return !hostAddr.getHostName().isEmpty();
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
	
	class SuffixEnumerator implements Runnable {
		public void run() {
			final char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
			StringBuilder domainSuffix = new StringBuilder();
			for (char a : chars)
				for (char b : chars) {
					domainSuffix.setLength(0);
					domainSuffix.append(a).append(b);
					possibleSuffixes.add(domainSuffix.toString());
				}
		}
	}
	
	class HostEnumerator implements Runnable {
		public void run() {
			possibleSuffixes.forEach(suffix -> executor.execute(new AddrTester(suffix)));
		}
	}

}
