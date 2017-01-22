package model;

public interface EmissionCacheInnerLoop {
	public void startup(float[][] whiteTemplates, float[][] blackTemplates, int[] templateNumIndices, int[] templateIndicesOffsets, int minTemplateWidth, int maxTemplateWidth, int maxSequenceLength, int totalTemplateNumIndices);
	public void shutdown();
	public void compute(final float[] scores, final float[] whiteObservations, final float[] blackObservations, final int sequenceLength);
	public int numOuterThreads();
	public int numPopulateThreads();
}
