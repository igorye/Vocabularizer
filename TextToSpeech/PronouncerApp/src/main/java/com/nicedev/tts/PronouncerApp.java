package com.nicedev.tts;

import com.nicedev.gtts.service.TTSPlaybackService;
import com.nicedev.gtts.service.TTSSaverService;
import com.nicedev.gtts.service.TTSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;


public class PronouncerApp {

	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

	public static void main(String[] args) throws FileNotFoundException, InterruptedException {
		long start = System.currentTimeMillis();
		Map<String, String> mArgs = parseArgs(args);
		if (mArgs.containsKey("-h")) {
			showUsage();
			return;
		}
		boolean bShowProgress = mArgs.containsKey("-p");
		String inFileName = mArgs.getOrDefault("-i", "");
		boolean inIsFile = !inFileName.isEmpty();
		String outFileName = mArgs.get("-o");
		outFileName = (outFileName == null || outFileName.isEmpty())
				              ? getDefaultOutName(inFileName)
				              : outFileName;
		TTSService ttsService = mArgs.containsKey("-o")
				                        ? new TTSSaverService(outFileName, 10, bShowProgress)
				                        : new TTSPlaybackService(10, bShowProgress);
		JobScheduler scheduler = new JobScheduler(ttsService, outFileName);
		Scanner in = inIsFile ? new Scanner(new FileReader(inFileName)) : new Scanner(System.in);
		StringBuilder spellingBuilder = new StringBuilder();
		String line = "";
		while (in.hasNextLine()) {
			line = "";
			try {
				line = in.nextLine().trim();
				switch (line) {
					case "~"  : ttsService.switchAccent(); continue;
					case "!~" : ttsService.resetAccent(); continue;
					default   : spellingBuilder.append(line).append(" ");
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
		LOGGER.debug("releasing scheduler");
		scheduler.release();
		scheduler.join();
		ttsService.release();
		LOGGER.debug("releasing ttsService");
		ttsService.join();
		System.out.printf("%f%n", (System.currentTimeMillis() - start)/1000f);
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

		private final TTSService ttsService;
		private final String outFileName;
		static final TransferQueue<String> jobQueue = new LinkedTransferQueue<>();
		private volatile boolean inputIsEmpty = false;

		JobScheduler(TTSService worker, String outFileName) {
			this.outFileName = outFileName;
			this.ttsService = worker;
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
							int delay = token.trim().endsWith(".") ? 250 : token.trim().endsWith(",") ? 50 : 0;
							ttsService.enqueue(token, delay);
						}
				} catch (InterruptedException e) {
					LOGGER.debug("Interrupted while taking next job chunk");
					break;
				}
			}
			
		}
		
		void schedule(String work) {
			try {
				jobQueue.put(work);
			} catch (InterruptedException e) {
				LOGGER.warn("Interrupted while scheduling \"{}\"", work);
			}
		}
		
		void release() {
			LOGGER.debug("releasing scheduler: jobQueue[{}]", jobQueue.size());
			inputIsEmpty = true;
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
		String valueForKey = "";
		for(String arg: args) {
			if (arg.startsWith("-")) {
				valueForKey = arg;
				mArgs.put(valueForKey, "");
			} else {
				mArgs.put(valueForKey, arg);
				valueForKey = "";
			}
		}
		return mArgs;
	}
	
	private static Collection<String> split(String line, String splitter, int nextRule) {
		String[] splitRules = { "(?<=\\\\.{2,3} )",
		                        "(?=\\()",
		                        "(?<=\\)[,:;]{0,1})",
		                        "(?> \'\\w+\' )",
		                        "(?> \"\\w+\" )",
		                        "(?<=[,:;])",
		                        "(?= - )",
		                        "\\s"};
		List<String> res = new ArrayList<>();
		for (String token: line.split(splitter)) {
			if (token.length() <= 200) {
				if (!token.trim().isEmpty())
					res.add(token.trim());
			} else {
				Collection<String> shortTokens = split(token.replaceAll("\\s{2,}"," "), splitRules[nextRule], ++nextRule);
				nextRule = 0;
				StringBuilder tokenBuilder = new StringBuilder();
				for(String shortToken: shortTokens) {
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

}