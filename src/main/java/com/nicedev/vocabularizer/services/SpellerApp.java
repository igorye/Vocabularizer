package com.nicedev.vocabularizer.services;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class SpellerApp {

	public static void main(String[] args) throws FileNotFoundException, InterruptedException {
		Map<String, String> mArgs = parseArgs(args);
		boolean bShowProgress = mArgs.containsKey("-p");
		SpellingService spellingService = new SpellingService(5, bShowProgress);
		String inFileName = mArgs.getOrDefault("-i", "");
		boolean inIsFile = !inFileName.isEmpty();
		String outFileName = mArgs.get("-o");
		outFileName = outFileName == null ? ""
				              : outFileName.isEmpty() ? getDefaultOutName(inFileName)
						                : outFileName;
		boolean outIsFile = !outFileName.isEmpty();
		JobScheduler scheduler = new JobScheduler(spellingService, outIsFile ? outFileName : "");
		Scanner in = inIsFile ? new Scanner(new FileReader(inFileName)) : new Scanner(System.in);
		StringBuilder spellingBuilder = new StringBuilder();
		String line = "";
		while (in.hasNextLine()) {
			line = "";
			try {
				line = in.nextLine().trim();
				switch (line) {
					case "~"    : spellingService.switchAccent(); continue;
					case "!~"   : spellingService.resetAcent(); continue;
					default     : spellingBuilder.append(line).append(" ");
				}
			} catch (NoSuchElementException e) {
				continue;
			}
			Collection<String> request = new ArrayList<>();
			if(line.length() < 100)
				request.add(line);
			else 
				request = split(line, "[\",:-][\\s]");
			for(String tospell :request)
			if(!tospell.isEmpty()) {
				int delay = tospell.trim().contains(".") ? 200 : tospell.trim().contains("\"") ? 100 : 10;
				speller.spell(tospell, delay);
			}
		}
		speller.release(0);
		speller.join();
	}

	static class JobScheduler extends Thread {

		private SpellingService spellingService;
		private String outFileName;
		public static TransferQueue<String> jobQueue = new LinkedTransferQueue<>();
		private boolean inputIsEmpty = false;


		public JobScheduler(SpellingService worker, String outFileName) {
			this.outFileName = outFileName;
			this.spellingService = worker;
			setName("Scheduler");
			start();
		}

		public void run() {
			while (!inputIsEmpty || !jobQueue.isEmpty()) {
				Collection<String> request;
				String toSpell;
				try {
					toSpell = jobQueue.take();
					request = split(toSpell.replaceAll("`", "'").replaceAll("\\*", ""),"(?<=\\p{L}{2,}[\\\\.] )", 0);
					for (String token : request)
						if (!token.isEmpty()) {
							int delay = token.trim().endsWith(".") ? 300 : token.trim().endsWith(",") ? 50 : 0;
							if (outFileName.isEmpty()) spellingService.spell(token, delay);
							else spellingService.save(token, outFileName);
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
		String defaultOutFileName = inFileName.isEmpty() ? "spelling.mp3"
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
		String[] splitRules = {"(?<=\\\\.{2,3} )", "(?<=[\\(\\)])", "(?> \'\\w+\' )", "(?> \"\\w+\" )", "(?<=[,:;])", "(?= - )", "\\s"};
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