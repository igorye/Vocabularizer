package com.nicedev.tts;

import com.nicedev.gtts.sound.PronouncingService;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;


public class PronouncerApp {
	
	public static void main(String[] args) throws FileNotFoundException, InterruptedException {
		Map<String, String> mArgs = parseArgs(args);
		if (mArgs.containsKey("-h")) {
			showUsage();
			return;
		}
		boolean bShowProgress = mArgs.containsKey("-p");
		PronouncingService pronouncingService = new PronouncingService(5, bShowProgress);
		String inFileName = mArgs.getOrDefault("-i", "");
		boolean inIsFile = !inFileName.isEmpty();
		String outFileName = mArgs.get("-o");
		outFileName = outFileName == null ? "" : outFileName.isEmpty()
				                                         ? getDefaultOutName(inFileName)
				                                         : outFileName;
		boolean outIsFile = !outFileName.isEmpty();
		JobScheduler scheduler = new JobScheduler(pronouncingService, outIsFile ? outFileName : "");
		Scanner in = inIsFile ? new Scanner(new FileReader(inFileName)) : new Scanner(System.in);
		StringBuilder spellingBuilder = new StringBuilder();
		String line = "";
		while (in.hasNextLine()) {
			line = "";
			try {
				line = in.nextLine().trim();
				switch (line) {
					case "~"    : pronouncingService.switchAccent(); continue;
					case "!~"   : pronouncingService.resetAccent(); continue;
					default     : spellingBuilder.append(line).append(" ");
				}
			} catch (NoSuchElementException e) {
				continue;
			}
			if (spellingBuilder.length() > 200 && line.matches(".+[.\"\';:]") || line.matches("^\\s*$")) {
				scheduler.schedule(spellingBuilder.toString());
				spellingBuilder.setLength(0);
			}
		}
		if (spellingBuilder.length() > 0 || !line.isEmpty()) {
			scheduler.schedule(spellingBuilder.toString());
		}
		scheduler.release();
		scheduler.join();
		pronouncingService.release();
		pronouncingService.join();
	}
	
	private static void showUsage() {
		System.out.println("PronouncerApp [key] [value] ...");
		System.out.println("-h - this help");
		System.out.println("-i - input file");
		System.out.println("-o - output file");
		System.out.println("-p - show progress");
		System.out.println("During reading \"~\" - switch accent (applied after a few phrases)");
	}
	
	static class JobScheduler extends Thread {
		
		private final PronouncingService pronouncingService;
		private final String outFileName;
		public static final TransferQueue<String> jobQueue = new LinkedTransferQueue<>();
		private volatile boolean inputIsEmpty = false;
		
		
		public JobScheduler(PronouncingService worker, String outFileName) {
			this.outFileName = outFileName;
			this.pronouncingService = worker;
			setName("Scheduler");
			start();
		}
		
		public void run() {
			while (!inputIsEmpty || !jobQueue.isEmpty()) {
				Collection<String> request;
				String toPronounce;
				try {
					toPronounce = jobQueue.take();
					request = split(toPronounce.replaceAll("`", "'").replaceAll("\\*", ""),"(?<=\\p{L}{2,}[\\\\.] )", 0);
					for (String token : request)
						if (!token.isEmpty()) {
							int delay = token.trim().endsWith(".") ? 300 : token.trim().endsWith(",") ? 50 : 0;
							if (outFileName.isEmpty()) pronouncingService.pronounce(token, delay);
							else pronouncingService.save(token, outFileName);
						}
				} catch (InterruptedException e) {
					break;
				}
			}
			
		}
		
		public void schedule(String work) {
			try {
				jobQueue.put(work);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		public void release() {
			inputIsEmpty = true;
			Thread.yield();
			if (jobQueue.isEmpty()) interrupt();
		}
	}
	
	private static String getDefaultOutName(String inFileName) {
		String defaultOutFileName = inFileName.isEmpty() ? "pronunciation.mp3"
				                            : inFileName.replaceAll("((?<=\\w{1,255}\\.)\\w+$)", "").concat("mp3");
		Path path = Paths.get(defaultOutFileName);
		defaultOutFileName = path.getName(path.getNameCount() - 1).toString();
		return defaultOutFileName;
	}
	
	private static Map<String, String> parseArgs(String[] args) {
		Map<String, String> mArgs = new LinkedHashMap<>();
		boolean lookForValue = false;
		String param = "";
		for(String arg: args) {
			if (arg.startsWith("-") && !lookForValue) {
				param = arg;
				if(!param.isEmpty()) mArgs.put(param, "");
				lookForValue = true;
				continue;
			}
			if (!param.isEmpty() && lookForValue && !arg.isEmpty() && !arg.startsWith("-")) {
				mArgs.put(param, arg);
				param = "";
				lookForValue = false;
			}
		}
		return mArgs;
	}
	
	private static Collection<String> split(String line, String splitter, int nextRule) {
		String[] splitRules = {"(?<=\\\\.{2,3} )", "(?=\\()", "(?<=\\)[,:;]{0,1})", "(?> \'\\w+\' )",
				"(?> \"\\w+\" )", "(?<=[,:;])", "(?= - )", "\\s"};
		List<String> res = new ArrayList<>();
		String[] tokens = line.split(splitter);
		for (String token: tokens) {
			if (token.length() <= 200)
				res.add(token.trim());
			else {
				Collection<String> shortTokens = split(token.replaceAll("\\s{2,}"," "), splitRules[nextRule], ++nextRule);
				nextRule = 0;
				StringBuilder tokenBuilder = new StringBuilder();
				for(String shortToken: shortTokens) {
					if(tokenBuilder.length() + shortToken.length() + 1 <= 200) {
						if(tokenBuilder.length() != 0)
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
	
}