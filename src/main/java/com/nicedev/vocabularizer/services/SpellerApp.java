package com.nicedev.vocabularizer.services;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.*;


public class SpellerApp {
	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
		Speller speller = new Speller();
		Scanner in = new Scanner(args.length == 1 ? new FileInputStream(args[0]) : System.in);
		while (in.hasNext()) {
			String line = in.nextLine().trim();
			if (line.startsWith("~")) {
				speller.switchAccent();
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
	
	private static Collection<String> split(String line, String splitter) {
		List<String> res = new ArrayList<>();
		String[] tokens = line.split(splitter);
		for (String token: tokens) {
			if (token.length() <= 100)
				res.add(token.trim());
			else {
				Collection<String> shortTokens = split(token, "\\s");
				StringBuilder tokenBuilder = new StringBuilder();
				for(String shortToken: shortTokens) {
					if(tokenBuilder.length() + shortToken.length() + 1 < 100) {
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