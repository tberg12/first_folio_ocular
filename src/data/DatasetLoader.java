package data;

import image.ImageUtils.PixelType;

import java.util.List;

public interface DatasetLoader {
	
	public static interface Document {
		public String baseName();
		public PixelType[][][] loadLineImages();
		public String[][] loadLineText();
		public boolean useLongS();
	}

	public List<Document> readDataset();
  
}
