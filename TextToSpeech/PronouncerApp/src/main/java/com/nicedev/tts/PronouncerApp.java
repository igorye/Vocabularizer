package com.nicedev.tts;

import com.nicedev.gtts.service.TTSPlaybackService;
import com.nicedev.gtts.service.TTSSaverService;
import com.nicedev.gtts.service.TTSService;
import com.nicedev.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


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
		boolean fileOutput = mArgs.containsKey("-o");
		TTSService ttsService = fileOutput
				                        ? new TTSSaverService(outFileName, 10, bShowProgress)
				                        : new TTSPlaybackService(10, bShowProgress);
		Scanner in = inIsFile ? new Scanner(new FileReader(inFileName)) : new Scanner(System.in);
		StringBuilder spellingBuilder = new StringBuilder();
		String line = "", lastLine = "";
		ttsService.setDelay('.', 200)
				.setDelay(':', 150)
				.setDelay(';', 100)
				.setDelay(',', 0)
				.setDelay('-', 100);
		while (in.hasNextLine()) {
			line = "";
			try {
				line = in.nextLine().trim();
				switch (line) {
					case "~"  : ttsService.switchAccent(); continue;
					case "!~" : ttsService.resetAccent(); continue;
					default   :
						if (!lastLine.isEmpty()){
						spellingBuilder.append(lastLine);
						spellingBuilder.append(" ");
					}
				}
			} catch (NoSuchElementException e) {
				continue;
			}
			if (line.matches("^\\s*$") || !fileOutput) {
				spellingBuilder.append(line);
				lastLine = "";
				String text = prepareChunk(spellingBuilder);
				ttsService.enqueue(text, fileOutput ? 600 : 200);
				spellingBuilder.setLength(0);
			} else
				lastLine = line;
		}
		spellingBuilder.append(line);
		if (spellingBuilder.length() > 0 || !Strings.isBlank(line)) {
			ttsService.enqueue(prepareChunk(spellingBuilder), 0);
		}
		ttsService.release();
		LOGGER.debug("releasing ttsService");
		ttsService.join();
		if (fileOutput) {
			System.out.printf("Processed in %fs%n", (System.currentTimeMillis() - start)/1000f);
		}
	}

	private static String prepareChunk(StringBuilder spellingBuilder) {
		return spellingBuilder.toString().replaceAll("`", "'").replaceAll("\\*", "");
	}

	private static void showUsage() {
		System.out.println("PronouncerApp [key] [value] ...");
		System.out.println("-h - this help");
		System.out.println("-i - input file");
		System.out.println("-o - output file");
		System.out.println("-p - show progress");
		System.out.println("During reading \"~\" - switch accent (applied after a few phrases)");
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
}