package preprocessing;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

import image.ImageUtils;
import image.ImageUtils.ConnectedComponentProcessor;
import tberg.murphy.fileio.f;

public class ManualStackCropperPrep {
	
	public static void main(String[] args) {
		String path = args[0];
		File dir = new File(path);
		String[] names = dir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".png") || name.endsWith(".jpg");
			}
		});
		File oddDirCol1 = new File(path + "/odd_col1");
		File oddDirCol2 = new File(path + "/odd_col2");
		oddDirCol1.mkdirs();
		oddDirCol2.mkdirs();
		File evenDirCol1 = new File(path + "/even_col1");
		File evenDirCol2 = new File(path + "/even_col2");
		evenDirCol1.mkdirs();
		evenDirCol2.mkdirs();
		File dirExtr = new File(path + "/col_extraction");
		dirExtr.mkdirs();
		boolean odd = false;
		for (String name : names) {
			double[][] levels = ImageUtils.getLevels(f.readImage(path+"/"+name));
			double[][] rotLevels = Straightener.straighten(levels);
			ConnectedComponentProcessor ccprocBig = new ConnectedComponentProcessor() {
				public void process(double[][] levels, List<int[]> connectedComponent) {
					if (connectedComponent.size() > 1000) {
						for (int[] pixel : connectedComponent) {
							levels[pixel[0]][pixel[1]] = 255.0;
						}
					}
				}
			};
			ImageUtils.processConnectedComponents(rotLevels, 50.0, ccprocBig);
			Binarizer.binarizeGlobal(0.13, rotLevels);
			ConnectedComponentProcessor ccprocSmall = new ConnectedComponentProcessor() {
				public void process(double[][] levels, List<int[]> connectedComponent) {
					if (connectedComponent.size() < 20 || connectedComponent.size() > 1000) {
						for (int[] pixel : connectedComponent) {
							levels[pixel[0]][pixel[1]] = 255.0;
						}
					}
				}
			};
			ImageUtils.processConnectedComponents(rotLevels, 127.0, ccprocSmall);
			String baseName = (name.lastIndexOf('.') == -1) ? name : name.substring(0, name.lastIndexOf('.'));
			f.writeImage((odd ? oddDirCol1.getAbsolutePath() : evenDirCol1.getAbsolutePath()) +"/"+ baseName + "_col1.png", ImageUtils.makeImage(rotLevels));
			f.writeImage((odd ? oddDirCol2.getAbsolutePath() : evenDirCol2.getAbsolutePath()) +"/"+ baseName + "_col2.png", ImageUtils.makeImage(rotLevels));
			odd = !odd;
		}
	}

}
