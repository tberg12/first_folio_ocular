package main;

import tberg.murphy.indexer.HashMapIndexer;
import tberg.murphy.indexer.Indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lm.LanguageModel;
import lm.LanguageModel.LMType;
import tberg.murphy.fig.Option;
import tberg.murphy.fig.OptionsParser;

public class LMTrainMain implements Runnable {
	
	@Option(gloss = "Output LM file path.")
	public static String lmPath = "/Users/tberg/Desktop/ob-longs-uv-4gm-4pow-old.lmser";
//	public static String lmPath = "/Users/tberg/Dropbox/corpora/ocr_data/lms/nyt.lmser";
	
	@Option(gloss = "Input corpus path.")
	public static String textPath = "/Users/tberg/Desktop/big-lm.txt";
//	public static String textPath = "/Users/tberg/Desktop/test/page_text.txt";
//	public static String textPath = "/Users/tberg/Dropbox/corpora/EnglishGigaword/nyt_all";
	
	@Option(gloss = "Use separate character type for long s.")
	public static boolean useLongS = true;
	
	@Option(gloss = "Allow 'u' and 'v' to interchange.")
	public static boolean useUV = true;
	
	@Option(gloss = "Maximum number of lines to use from corpus.")
	public static int maxLines = 1000000;
	
	@Option(gloss = "LM character n-gram length.")
	public static int charN = 4;
	
	@Option(gloss = "Exponent on LM scores.")
	public static double power = 4.0;
	
	public static void main(String[] args) {
		LMTrainMain main = new LMTrainMain();
		OptionsParser parser = new OptionsParser();
		parser.doRegisterAll(new Object[] {main});
		if (!parser.doParse(args)) System.exit(1);
		main.run();
	}

	public void run() {
		Indexer<String>charIndexer = new HashMapIndexer<String>();
		List<String> vocab = new ArrayList<String>();
		for (String c : Main.ALPHABET) vocab.add(c);
		if (useLongS) vocab.add(Main.LONGS);
		for (String c : Main.PUNC) vocab.add(c);
		vocab.add(Main.SPACE);
		for (String c : vocab) {
			charIndexer.getIndex(c);
		}
		charIndexer.lock();
		LanguageModel lm = LanguageModel.buildFromText(textPath, maxLines, charIndexer, charN, LMType.KNESER_NEY, power, useLongS, useUV);
		writeLM(lm, lmPath);
	}
	
	public static LanguageModel readLM(String lmPath) {
		LanguageModel lm = null;
		try {
			File file = new File(lmPath);
			if (!file.exists()) {
				System.out.println("Serialized lm file " + lmPath + " not found");
				return null;
			}
			FileInputStream fileIn = new FileInputStream(file);
			ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(fileIn));
			lm = (LanguageModel) in.readObject();
			in.close();
			fileIn.close();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		return lm;
	}

	public static void writeLM(LanguageModel lm, String lmPath) {
		try {
			FileOutputStream fileOut = new FileOutputStream(lmPath);
			ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(fileOut));
			out.writeObject(lm);
			out.close();
			fileOut.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
