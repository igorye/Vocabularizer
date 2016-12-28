package com.nicedev.vocabularizer.services.sound;

import com.nicedev.vocabularizer.services.sound.PronouncingService;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;


public class PronouncingApp {

	public static void main(String[] args) throws FileNotFoundException, InterruptedException {
		Map<String, String> mArgs = parseArgs(args);
		boolean bShowProgress = mArgs.containsKey("-p");
		PronouncingService pronouncingService = new PronouncingService(5, bShowProgress);
		String inFileName = mArgs.getOrDefault("-i", "");
		boolean inIsFile = !inFileName.isEmpty();
		String outFileName = mArgs.get("-o");
		outFileName = outFileName == null ? ""
				              : outFileName.isEmpty() ? getDefaultOutName(inFileName)
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
					case "!~"   : pronouncingService.resetAcent(); continue;
					default     : spellingBuilder.append(line).append(" ");
				}
			} catch (NoSuchElementException e) {
				continue;
			}
			if (spellingBuilder.length() > 200 && line.matches(".+[\\.\"\';:]") || line.matches("^\\s*$")) {
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

	static class JobScheduler extends Thread {

		private PronouncingService pronouncingService;
		private String outFileName;
		public static TransferQueue<String> jobQueue = new LinkedTransferQueue<>();
		private boolean inputIsEmpty = false;


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
				                            : inFileName.replaceAll("((?<=\\w+\\.)\\w+$)", "").concat("mp3");
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