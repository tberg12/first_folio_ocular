package main;

import image.Visualizer;
import image.ImageUtils.PixelType;
import tberg.murphy.indexer.Indexer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lm.LanguageModel;
import model.BeamingSemiMarkovDP;
import model.DefaultInnerLoop;
import model.CUDAInnerLoop;
import model.CachingEmissionModel;
import model.CachingEmissionModelExplicitOffset;
import model.CharacterNgramTransitionModel;
import model.CharacterNgramTransitionModelMarkovOffset;
import model.CharacterTemplate;
import model.DenseBigramTransitionModel;
import model.EmissionCacheInnerLoop;
import model.EmissionModel;
import model.JOCLInnerLoop;
import model.SparseTransitionModel;
import model.SparseTransitionModel.TransitionState;
import tberg.murphy.fig.Option;
import tberg.murphy.fig.OptionsParser;
import tberg.murphy.threading.BetterThreader;
import tberg.murphy.tuple.Pair;
import data.RawImageLoader;
import data.DatasetLoader;
import data.DatasetLoader.Document;
import eval.Evaluator;
import eval.Evaluator.EvalSuffStats;
import tberg.murphy.fileio.f;

public class Main implements Runnable {
	
	@Option(gloss = "Path of the directory that contains the input document images.")
	public static String inputPath = "/Users/tberg/Desktop/test";
//	public static String inputPath = "/Users/tberg/Dropbox/corpora/ocr_data/auto_crop_dev_single_column/old_bailey_1";

	@Option(gloss = "Path of the directory that will contain output transcriptions and line extractions.")
	public static String outputPath = "/Users/tberg/Desktop/output";

	@Option(gloss = "Path to write the learned font file to. (Only if learnFont is set to true.)")
	public static String outputFontPath = "/Users/tberg/Desktop/output/learned.fontser";
//	public static String outputFontPath = "/Users/tberg/Dropbox/corpora/ocr_data/fonts/learned.fontser";


	@Option(gloss = "Path to the language model file.")
	public static String lmPath = "/Users/tberg/Desktop/folio-longs.lmser";
//	public static String lmPath = "/Users/tberg/Dropbox/corpora/ocr_data/lms/nyt.lmser";

	@Option(gloss = "Path of the font initializer file.")
	public static String initFontPath = "/Users/tberg/Desktop/init.fontser";
//	public static String initFontPath = "/Users/tberg/Dropbox/corpora/ocr_data/fonts/init.fontser";


	@Option(gloss = "Quantile to use for pixel value thresholding. (High values mean more black pixels.)")
	public static double binarizeThreshold = 0.12;


	@Option(gloss = "Min horizontal padding between characters in pixels. (Best left at default value: 1.)")
	public static int paddingMinWidth = 1;

	@Option(gloss = "Max horizontal padding between characters in pixels (Best left at default value: 5.)")
	public static int paddingMaxWidth = 5;


	@Option(gloss = "Use Markov chain to generate vertical offsets. (Slower, but more accurate. Turning on Markov offsets my require larger beam size for good results.)")
	public static boolean markovVerticalOffset = true;

	@Option(gloss = "Size of beam for viterbi inference. (Usually in range 10-50. Increasing beam size can improve accuracy, but will reduce speed.)")
	public static int beamSize = 10;

	@Option(gloss = "Whether to learn the font from the input documents and write the font to a file.")
	public static boolean learnFont = true;

	@Option(gloss = "Number of iterations of EM to use for font learning.")
	public static int numEMIters = 4;


	@Option(gloss = "Engine to use for inner loop of emission cache computation. DEFAULT: Uses Java on CPU, which works on any machine but is the slowest method. OPENCL: Faster engine that uses either the CPU or integrated GPU (depending on processor) and requires OpenCL installation. CUDA: Fastest method, but requires a discrete NVIDIA GPU and CUDA installation.")
	public static EmissionCacheInnerLoopType emissionEngine = EmissionCacheInnerLoopType.OPENCL;

	@Option(gloss = "GPU ID when using CUDA emission engine.")
	public static int cudaDeviceID = 0;

	@Option(gloss = "Number of threads to use for LFBGS during m-step.")
	public static int numMstepThreads = 8;

	@Option(gloss = "Number of threads to use during emission cache compuation. (Only has affect when emissionEngine is set to DEFAULT.)")
	public static int numEmissionCacheThreads = 8;

	@Option(gloss = "Number of threads to use for decoding. (Should be no smaller than decodeBatchSize.)")
	public static int numDecodeThreads = 8;

	@Option(gloss = "Number of lines that compose a single decode batch. (Smaller batch size can reduce memory consumption.)")
	public static int decodeBatchSize = 32;


	public static enum EmissionCacheInnerLoopType {DEFAULT, OPENCL, CUDA};

	public static final String SPACE = " ";
	public static final String HYPHEN = "-";
	public static final String LONGS = "|";
	public static final String[] PUNC = new String[] {"&", ".", ",", ";", ":", "\"", "'", "!", "?", "(", ")", HYPHEN}; 
	public static final String[] ALPHABET = new String[] {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",  "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"}; 

	public static void main(String[] args) {
		Main main = new Main();
		OptionsParser parser = new OptionsParser();
		parser.doRegisterAll(new Object[] {main});
		if (!parser.doParse(args)) System.exit(1);
		main.run();
	}

	public void run() {
		long overallNanoTime = System.nanoTime();
		long overallEmissionCacheNanoTime = 0;

		File outputDir = new File(outputPath);
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}
		
		List<Pair<String,Map<String,EvalSuffStats>>> allEvals = new ArrayList<Pair<String,Map<String,EvalSuffStats>>>();

		DatasetLoader loader =  new RawImageLoader(inputPath, CharacterTemplate.LINE_HEIGHT, numMstepThreads);
		List<Document> documents = loader.readDataset();
		for (Document doc : documents) {
			final PixelType[][][] pixels = doc.loadLineImages();
			System.out.println("Printing line extraction for document: "+doc.baseName());
			f.writeImage(outputPath+"/line_extract_"+doc.baseName(), Visualizer.renderLineExtraction(pixels));
		}

		System.out.println("Loading LM..");
		final LanguageModel lm = LMTrainMain.readLM(lmPath);
		DenseBigramTransitionModel backwardTransitionModel = new DenseBigramTransitionModel(lm);
		SparseTransitionModel forwardTransitionModel = null;
		if (markovVerticalOffset) {
			forwardTransitionModel = new CharacterNgramTransitionModelMarkovOffset(lm, lm.getMaxOrder());
		} else {
			forwardTransitionModel = new CharacterNgramTransitionModel(lm, lm.getMaxOrder());
		}
		final Indexer<String> charIndexer = lm.getCharacterIndexer();
		System.out.println("Characters: " + charIndexer.getObjects());
		System.out.println("Num characters: " + charIndexer.size());

		System.out.println("Loading font initializer..");
		Map<String,CharacterTemplate> font = FontInitMain.readFont(initFontPath);
		final CharacterTemplate[] templates = new CharacterTemplate[charIndexer.size()];
		for (int c=0; c<templates.length; ++c) {
			templates[c] = font.get(charIndexer.getObject(c));
		}

		EmissionCacheInnerLoop emissionInnerLoop = null;
		if (emissionEngine == EmissionCacheInnerLoopType.DEFAULT) {
			emissionInnerLoop = new DefaultInnerLoop(numEmissionCacheThreads);
		} else if (emissionEngine == EmissionCacheInnerLoopType.OPENCL) {
			emissionInnerLoop = new JOCLInnerLoop(numEmissionCacheThreads);
		} else if (emissionEngine == EmissionCacheInnerLoopType.CUDA) {
			emissionInnerLoop = new CUDAInnerLoop(numEmissionCacheThreads, cudaDeviceID);
		}
		
		if (!learnFont) numEMIters = 1;
		
		for (int iter=0; iter<numEMIters; ++iter) {
			System.out.println("Iteration: "+iter+"");

			for (int c=0; c<templates.length; ++c) if (templates[c] != null) templates[c].clearCounts();

			for (Document doc : documents) {
				System.out.println("Document: "+doc.baseName());

				final PixelType[][][] pixels = doc.loadLineImages();
				final String[][] text = doc.loadLineText();

				// e-step

				TransitionState[][] decodeStates = new TransitionState[pixels.length][0];
				int[][] decodeWidths = new int[pixels.length][0];
				int numBatches = (int) Math.ceil(pixels.length / (double) decodeBatchSize);

				for (int b=0; b<numBatches; ++b) {
					System.gc();
					System.gc();
					System.gc();

					System.out.println("Batch: "+b);

					int startLine = b*decodeBatchSize;
					int endLine = Math.min((b+1)*decodeBatchSize, pixels.length);
					PixelType[][][] batchPixels = new PixelType[endLine-startLine][][];
					for (int line=startLine; line<endLine; ++line) {
						batchPixels[line-startLine] = pixels[line];
					}

					final EmissionModel emissionModel = (markovVerticalOffset ? new CachingEmissionModelExplicitOffset(templates, charIndexer, batchPixels, paddingMinWidth, paddingMaxWidth, emissionInnerLoop) : new CachingEmissionModel(templates, charIndexer, batchPixels, paddingMinWidth, paddingMaxWidth, emissionInnerLoop));
					long emissionCacheNanoTime = System.nanoTime();
					emissionModel.rebuildCache();
					overallEmissionCacheNanoTime += (System.nanoTime() - emissionCacheNanoTime);

					long nanoTime = System.nanoTime();
					BeamingSemiMarkovDP dp = new BeamingSemiMarkovDP(emissionModel, forwardTransitionModel, backwardTransitionModel);
					Pair<Pair<TransitionState[][],int[][]>,Double> decodeStatesAndWidthsAndJointLogProb = dp.decode(beamSize, numDecodeThreads);
					final TransitionState[][] batchDecodeStates = decodeStatesAndWidthsAndJointLogProb.getFirst().getFirst();
					final int[][] batchDecodeWidths = decodeStatesAndWidthsAndJointLogProb.getFirst().getSecond();
					System.out.println("Decode: " + (System.nanoTime() - nanoTime)/1000000 + "ms");

					if (iter < numEMIters-1) {
						nanoTime = System.nanoTime();
						BetterThreader.Function<Integer,Object> func = new BetterThreader.Function<Integer,Object>(){public void call(Integer line, Object ignore){
							emissionModel.incrementCounts(line, batchDecodeStates[line], batchDecodeWidths[line]);
						}};
						BetterThreader<Integer,Object> threader = new BetterThreader<Integer,Object>(func, numMstepThreads);
						for (int line=0; line<emissionModel.numSequences(); ++line) threader.addFunctionArgument(line);
						threader.run();
						System.out.println("Increment counts: " + (System.nanoTime() - nanoTime)/1000000 + "ms");
					}

					for (int line=0; line<emissionModel.numSequences(); ++line) {
						decodeStates[startLine+line] = batchDecodeStates[line];
						decodeWidths[startLine+line] = batchDecodeWidths[line];
					}
				}

				// evaluate

				printTranscription(iter, numEMIters, doc, allEvals, text, decodeStates, charIndexer);

			}

			// m-step

			if (iter < numEMIters-1) {
				long nanoTime = System.nanoTime();
				{
					final int iterFinal = iter;
					BetterThreader.Function<Integer,Object> func = new BetterThreader.Function<Integer,Object>(){public void call(Integer c, Object ignore){
						if (templates[c] != null) templates[c].updateParameters(iterFinal, numEMIters);
					}};
					BetterThreader<Integer,Object> threader = new BetterThreader<Integer,Object>(func, numMstepThreads);
					for (int c=0; c<templates.length; ++c) threader.addFunctionArgument(c);
					threader.run();
				}
				System.out.println("Update parameters: " + (System.nanoTime() - nanoTime)/1000000 + "ms");
			}

		}
		
		if (learnFont) {
			FontInitMain.writeFont(font, outputFontPath);
		}

		if (!allEvals.isEmpty()) {
			printEvaluation(allEvals);
		}

		System.out.println("Emission cache time: " + overallEmissionCacheNanoTime/1e9 + "s");
		System.out.println("Overall time: " + (System.nanoTime() - overallNanoTime)/1e9 + "s");
	}

	public static void printEvaluation(List<Pair<String,Map<String,EvalSuffStats>>> allEvals) {
		Map<String,EvalSuffStats> totalSuffStats = new HashMap<String,EvalSuffStats>();
		StringBuffer buf = new StringBuffer();
		buf.append("All evals:\n");
		for (Pair<String,Map<String,EvalSuffStats>> docNameAndEvals : allEvals) {
			String docName = docNameAndEvals.getFirst();
			Map<String,EvalSuffStats> evals = docNameAndEvals.getSecond();
			buf.append("Document: "+docName+"\n");
			buf.append(Evaluator.renderEval(evals)+"\n");
			for (String evalType : evals.keySet()) {
				EvalSuffStats eval = evals.get(evalType);
				EvalSuffStats totalEval = totalSuffStats.get(evalType);
				if (totalEval == null) {
					totalEval = new EvalSuffStats();
					totalSuffStats.put(evalType, totalEval);
				}
				totalEval.increment(eval);
			}
		}

		buf.append("\nMarco-avg total eval:\n");
		buf.append(Evaluator.renderEval(totalSuffStats)+"\n");

		f.writeString(outputPath+"/eval.txt", buf.toString());
		System.out.println();
		System.out.println(buf.toString());
	}

	private static void printTranscription(int iter, int numIters, Document doc, List<Pair<String,Map<String,EvalSuffStats>>> allEvals, String[][] text, TransitionState[][] decodeStates, Indexer<String> charIndexer) {
		List<String>[] viterbiChars = new List[decodeStates.length];
		for (int line=0; line<decodeStates.length; ++line) {
			viterbiChars[line] = new ArrayList<String>();

			for (int i=0; i<decodeStates[line].length; ++i) {
				int c = decodeStates[line][i].getCharIndex();
				if (viterbiChars[line].isEmpty() || !(HYPHEN.equals(viterbiChars[line].get(viterbiChars[line].size()-1)) && HYPHEN.equals(charIndexer.getObject(c)))) {
					viterbiChars[line].add(charIndexer.getObject(c));
				}
			}
		}
		if (text != null) {
			List<String>[] goldCharSequences = new List[text.length];
			for (int line=0; line<decodeStates.length; ++line) {
				goldCharSequences[line] = new ArrayList<String>();
				for (int i=0; i<text[line].length; ++i) {
					goldCharSequences[line].add(text[line][i]);
				}
			}

			StringBuffer guessAndGoldOut = new StringBuffer();
			for (int line=0; line<decodeStates.length; ++line) {
				for (String c : viterbiChars[line]) {
					guessAndGoldOut.append(c);
				}
				guessAndGoldOut.append("\n");
				for (String c : goldCharSequences[line]) {
					guessAndGoldOut.append(c);
				}
				guessAndGoldOut.append("\n");
				guessAndGoldOut.append("\n");
			}

			Map<String,EvalSuffStats> evals = Evaluator.getUnsegmentedEval(viterbiChars, goldCharSequences);
			if (iter == Main.numEMIters-1) {
				allEvals.add(Pair.makePair(doc.baseName(), evals));
			}
			System.out.println(guessAndGoldOut.toString()+Evaluator.renderEval(evals));

			f.writeString(outputPath+"/"+doc.baseName()+".iter-"+iter+".txt", guessAndGoldOut.toString()+Evaluator.renderEval(evals));
		} else {
			StringBuffer guessOut = new StringBuffer();
			for (int line=0; line<decodeStates.length; ++line) {
				for (String c : viterbiChars[line]) {
					guessOut.append(c);
				}
				guessOut.append("\n");
			}
			System.out.println(guessOut.toString());

			f.writeString(outputPath+"/"+doc.baseName()+".iter-"+iter+".txt", guessOut.toString());
		}
	}

}
