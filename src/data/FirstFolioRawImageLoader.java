package data;

import tberg.murphy.fileio.f;
import image.ImageUtils;
import image.ImageUtils.ConnectedComponentProcessor;
import image.ImageUtils.PixelType;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;

import main.Main;
import preprocessing.Binarizer;
import preprocessing.LineExtractor;
import tberg.murphy.threading.BetterThreader;

public class FirstFolioRawImageLoader implements DatasetLoader {

	public static class RawImageDocument implements DatasetLoader.Document {
		private final String baseName;
		final PixelType[][][] observations;
		
		public RawImageDocument(String inputPath, String baseName, int lineHeight) {
			this.baseName = baseName;
			System.out.println("Preparing "+baseName+"..");
			double[][] levels = ImageUtils.getLevels(f.readImage(inputPath+"/"+baseName));
			ConnectedComponentProcessor ccprocBig = new ConnectedComponentProcessor() {
				public void process(double[][] levels, List<int[]> connectedComponent) {
					if (connectedComponent.size() > 1000) {
						for (int[] pixel : connectedComponent) {
							levels[pixel[0]][pixel[1]] = 255.0;
						}
					}
				}
			};
			ImageUtils.processConnectedComponents(levels, 50.0, ccprocBig);
			Binarizer.binarizeGlobal(Main.binarizeThreshold, levels);
			ConnectedComponentProcessor ccprocSmall = new ConnectedComponentProcessor() {
				public void process(double[][] levels, List<int[]> connectedComponent) {
					if (connectedComponent.size() < 20 || connectedComponent.size() > 1000) {
						for (int[] pixel : connectedComponent) {
							levels[pixel[0]][pixel[1]] = 255.0;
						}
					}
				}
			};
			ImageUtils.processConnectedComponents(levels, 127.0, ccprocSmall);
			
			List<double[][]> lines = LineExtractor.extractLines(levels);
			observations = new PixelType[lines.size()][][];
			for (int i=0; i<lines.size(); ++i) {
				if (lineHeight >= 0) {
					observations[i] = ImageUtils.getPixelTypes(ImageUtils.resampleImage(ImageUtils.makeImage(lines.get(i)), lineHeight));
				} else {
					observations[i] = ImageUtils.getPixelTypes(ImageUtils.makeImage(lines.get(i)));
				}
			}
		}

		public PixelType[][][] loadLineImages() {
			return observations;
		}

		public String[][] loadLineText() {
			return null;
		}
		
		public String baseName() {
			return baseName;
		}

		public boolean useLongS() {
			return false;
		}

	}
	
	private final String inputPath;
	private final int lineHeight;
	private final int numThreads;

	public FirstFolioRawImageLoader(String inputPath, int lineHeight, int numThreads) {
		this.inputPath = inputPath;
		this.lineHeight = lineHeight;
		this.numThreads = numThreads;
	}
	
	public List<Document> readDataset() {
		System.out.println("Extracting text line images from dataset..");
		File dir = new File(inputPath);
		final String[] dirList = dir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".png") || name.endsWith(".jpg");
			}
		});
		final Document[] docs = new Document[dirList.length]; 
		BetterThreader.Function<Integer,Object> func = new BetterThreader.Function<Integer,Object>(){public void call(Integer i, Object ignore){
			String baseName = dirList[i];
			docs[i] = new RawImageDocument(inputPath, baseName, lineHeight);
		}};
		BetterThreader<Integer,Object> threader = new BetterThreader<Integer,Object>(func, numThreads);
		for (int i=0; i<dirList.length; ++i) threader.addFunctionArgument(i);
		threader.run();
		return Arrays.asList(docs);
	}

}
