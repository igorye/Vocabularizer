package com.nicedev.vocabularizer.services;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;


public class SpellerTest {
	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
		SpellingService spellingService = new SpellingService(5, false);
		Scanner in = new Scanner(args.length == 1 ? new FileInputStream(args[0]) : System.in);
		while (in.hasNext()) {
			String line = in.nextLine().trim();
			String[] request = null ;
			if(line.length() < 100) {
				request = new String[1];
				request[0] = line;
			} else request = line.split("[[^\\p{L}]&&[^\\s]]");
			for(String tospell :request)
			if(!tospell.isEmpty())
				spellingService.spell(tospell);
		}
		spellingService.release(5);
		spellingService.join();
	}
	
}