package main;

import image.FontRenderer;
import image.ImageUtils.PixelType;
import tberg.murphy.indexer.HashMapIndexer;
import tberg.murphy.indexer.Indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import tberg.murphy.threading.BetterThreader;
import model.CharacterTemplate;
import tberg.murphy.fig.Option;
import tberg.murphy.fig.OptionsParser;

public class FontInitMain implements Runnable {

	@Option(gloss = "Output font file path.")
	public static String fontPath = "/Users/tberg/Desktop/init.fontser";
//	public static String fontPath = "/Users/tberg/Dropbox/corpora/ocr_data/templates/init.fontser";

	@Option(gloss = "Number of threads to use.")
	public static int numFontInitThreads = 8;
	
	@Option(gloss = "Max template width as fraction of text line height.")
	public static double templateMaxWidthFraction = 1.0;

	@Option(gloss = "Min template width as fraction of text line height.")
	public static double templateMinWidthFraction = 0.0;

	@Option(gloss = "Max space template width as fraction of text line height.")
	public static double spaceMaxWidthFraction = 1.0;

	@Option(gloss = "Min space template width as fraction of text line height.")
	public static double spaceMinWidthFraction = 0.0;
	
	
	public static void main(String[] args) {
		FontInitMain main = new FontInitMain();
		OptionsParser parser = new OptionsParser();
		parser.doRegisterAll(new Object[] {main});
		if (!parser.doParse(args)) System.exit(1);
		main.run();
	}

	public void run() {
		final Indexer<String> charIndexer = new HashMapIndexer<String>();
		List<String> vocab = new ArrayList<String>();
		for (String c : Main.ALPHABET) vocab.add(c);
		vocab.add(Main.LONGS);
		for (String c : Main.PUNC) vocab.add(c);
		vocab.add(Main.SPACE);
		for (String c : vocab) {
			charIndexer.getIndex(c);
		}
		charIndexer.lock();
		final CharacterTemplate[] templates = new CharacterTemplate[charIndexer.size()];
		final PixelType[][][][] fontPixelData = FontRenderer.getRenderedFont(charIndexer, CharacterTemplate.LINE_HEIGHT);
		final PixelType[][][] fAndBarFontPixelData = buildFAndBarFontPixelData(charIndexer, fontPixelData);
		BetterThreader.Function<Integer,Object> func = new BetterThreader.Function<Integer,Object>(){public void call(Integer c, Object ignore){
			String currChar = charIndexer.getObject(c);
			if (!currChar.equals(Main.SPACE)) {
				templates[c] = new CharacterTemplate(currChar, (float) templateMaxWidthFraction, (float) templateMinWidthFraction);
				if (currChar.equals(Main.LONGS)) {
					templates[c].initializeAndSetPriorFromFontData(fAndBarFontPixelData);
				} else {
					templates[c].initializeAndSetPriorFromFontData(fontPixelData[c]);
				}
			} else {
				templates[c] = new CharacterTemplate(Main.SPACE, (float) spaceMaxWidthFraction, (float) spaceMinWidthFraction);
			}
		}};
		BetterThreader<Integer,Object> threader = new BetterThreader<Integer,Object>(func, numFontInitThreads);
		for (int c=0; c<templates.length; ++c) threader.addFunctionArgument(c);
		threader.run();
		Map<String,CharacterTemplate> font = new HashMap<String, CharacterTemplate>();
		for (CharacterTemplate template : templates) {
			font.put(template.getCharacter(), template);
		}
		FontInitMain.writeFont(font, fontPath);
	}
	
	private static PixelType[][][] buildFAndBarFontPixelData(Indexer<String> charIndexer, PixelType[][][][] fontPixelData) {
		List<PixelType[][]> fAndBarFontPixelData = new ArrayList<PixelType[][]>();
		if (charIndexer.contains("f")) {
			int c = charIndexer.getIndex("f");
			for (PixelType[][] datum : fontPixelData[c]) {
				fAndBarFontPixelData.add(datum);
			}
		}
		if (charIndexer.contains("|")) {
			int c = charIndexer.getIndex("|");
			for (PixelType[][] datum : fontPixelData[c]) {
				fAndBarFontPixelData.add(datum);
			}
		}
		return fAndBarFontPixelData.toArray(new PixelType[0][][]);
	}
	
	public static Map<String,CharacterTemplate> readFont(String fontPath) {
		Map<String,CharacterTemplate> font = null;
		try {
			File file = new File(fontPath);
			if (!file.exists()) {
				System.out.println("Serialized font file " + fontPath + " not found");
				return null;
			}
			FileInputStream fileIn = new FileInputStream(file);
			ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(fileIn));
			font = (Map<String,CharacterTemplate>) in.readObject();
			in.close();
			fileIn.close();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		return font;
	}

	public static void writeFont(Map<String,CharacterTemplate> font, String fontPath) {
		try {
			FileOutputStream fileOut = new FileOutputStream(fontPath);
			ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(fileOut));
			out.writeObject(font);
			out.close();
			fileOut.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

}
