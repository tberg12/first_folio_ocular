package preprocessing;

import image.ImageUtils;
import image.ImageUtils.ConnectedComponentProcessor;
import image.Visualizer;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import preprocessing.VerticalProfile.VerticalSegmentation;
import tberg.murphy.tuple.Pair;
import tberg.murphy.fileio.f;

public class LineExtractor {
	
	public static List<double[][]> extractLines(double[][] levels) {
		VerticalProfile verticalProfile = new VerticalProfile(levels);
		VerticalModel trainedModel = verticalProfile.runEM(5, 100);
		trainedModel.freezeSizeParams(1);
		VerticalSegmentation viterbiSegments = verticalProfile.decode(trainedModel);
//		ImageUtils.display(Visualizer.renderLineExtraction(levels, viterbiSegments));
		List<Pair<Integer,Integer>> lineBoundaries = viterbiSegments.retrieveLineBoundaries();
		List<double[][]> result = new ArrayList<double[][]>();
		for (Pair<Integer,Integer> boundary : lineBoundaries) {
			double[][] line = new double[levels.length][boundary.getSecond().intValue() - boundary.getFirst().intValue()];
			for (int y = boundary.getFirst().intValue(); y < boundary.getSecond().intValue(); y++) {
				for (int x = 0; x < levels.length; x++) {
					line[x][y-boundary.getFirst()] = levels[x][y];
				}
			}
			result.add(line);
		}
		System.out.println("Extractor returned " + result.size() + " line images");
		return result;
	}

	public static void main(String[] args) {
		String path = "/Users/tberg/Desktop/F-tem/seg_extraction/";
		File dir = new File(path);
		for (String name : dir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".png") || name.endsWith(".jpg");
			}
		})) {
			double[][] levels = ImageUtils.getLevels(f.readImage(path+"/"+name));
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
			Binarizer.binarizeGlobal(0.13, levels);
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
			List<double[][]> lines = extractLines(levels);
			for (double[][] line : lines) {
				ImageUtils.display(ImageUtils.makeImage(line));
			}
		}
	}
	
}
