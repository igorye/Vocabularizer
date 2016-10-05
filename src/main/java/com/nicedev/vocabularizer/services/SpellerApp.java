package com.nicedev.vocabularizer.services;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class SpellerApp {
	public static BlockingQueue<String> spellingQueue = new LinkedBlockingQueue<>();
	private static boolean interrupted;

	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
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
		WorkScheduler scheduler = new WorkScheduler(spellingService, outIsFile ? outFileName : "");
		Scanner in = inIsFile ? new Scanner(new FileReader(inFileName)) : new Scanner(System.in);
		StringBuilder spellingBuilder = new StringBuilder();
		String line = "";
		while (in.hasNextLine()) {
			line = "";
			try {
				line = in.nextLine();
				switch (line) {
					case "~"    : spellingService.switchAccent(); continue;
					case "!~"   : spellingService.resetAcent(); continue;
					default     : spellingBuilder.append(line).append(" ");
				}
			} catch (NoSuchElementException e) {
				System.err.printf("Exception!!! Scanner hasNextLine = %s%n", in.hasNextLine());
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

	static class WorkScheduler extends Thread {

		private SpellingService spellingService;
		private String outFileName;

		public WorkScheduler(SpellingService worker, String outFileName) {
			this.outFileName = outFileName;
			this.spellingService = worker;
			setName("Scheduler");
			start();
		}

		public void run() {
			while (!interrupted) {
				Collection<String> request;
				String arg;
				try {
					arg = spellingQueue.take();
					request = split(arg.replaceAll("`", "'").replaceAll("\\*", ""),"(?<=\\p{L}{2,}[\\\\.] )", 0);
					for (String tospell : request)
						if (!tospell.isEmpty()) {
							int delay = tospell.trim().endsWith(".") ? 300 : tospell.trim().endsWith(",") ? 100 : 0;
							if (outFileName.isEmpty()) spellingService.spell(tospell, delay);
							else spellingService.save(tospell, outFileName);
						}
				} catch (InterruptedException e) {
					break;
				}
			}
//			System.err.printf("Scheduler: interrupted = %s%n", interrupted);
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