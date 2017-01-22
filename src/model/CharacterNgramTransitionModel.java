package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import tberg.murphy.arrays.a;
import tberg.murphy.tuple.Pair;
import lm.LanguageModel;
import main.Main;

public class CharacterNgramTransitionModel implements SparseTransitionModel {
	
	public class CharacterNgramTransitionState implements SparseTransitionModel.TransitionState {
		private final int[] context;
		private final TransitionStateType type;

		private final int charIndex;
		
		public CharacterNgramTransitionState(int[] context, TransitionStateType type) {
			this.context = context;
			this.type = type;
			
			if (context.length == 0 || type == TransitionStateType.LMRGN || type == TransitionStateType.LMRGN_HPHN || type == TransitionStateType.RMRGN || type == TransitionStateType.RMRGN_HPHN) {
				this.charIndex = spaceCharIndex;
			} else if (type == TransitionStateType.RMRGN_HPHN_INIT) {
				this.charIndex = hyphenCharIndex;
			} else {
				this.charIndex = context[context.length-1];
			}
		}
		
		public boolean equals(Object other) {
		    if (other instanceof CharacterNgramTransitionState) {
		    	CharacterNgramTransitionState that = (CharacterNgramTransitionState) other;
		    	if (this.type != that.type) {
		    		return false;
		    	} else if (!Arrays.equals(this.context, that.context)) {
		    		return false;
		    	} else {
		    		return true;
		    	}
		    } else {
		    	return false;
		    }
		}
		
		public int hashCode() {
			return 1013 * Arrays.hashCode(context) + 1009 * this.type.ordinal();
		}
		
		public Collection<Pair<TransitionState,Double>> nextLineStartStates() {
			List<Pair<TransitionState,Double>> result = new ArrayList<Pair<TransitionState,Double>>();
			TransitionStateType type = getType();
			int[] context = getContext();
			if (type == TransitionStateType.TMPL) {
				double scoreWithSpace =  Math.log(lm.getCharNgramProb(context, spaceCharIndex));
				if (scoreWithSpace != Double.NEGATIVE_INFINITY) {
					int[] contextWithSpace = shrinkContext(a.append(context, spaceCharIndex));
					{
						double score = Math.log(LINE_MRGN_PROB) + scoreWithSpace;
						if (score != Double.NEGATIVE_INFINITY) {
							result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(contextWithSpace, TransitionStateType.LMRGN), score));
						}
					}
					for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
						double score = Math.log((1.0 - LINE_MRGN_PROB)) + scoreWithSpace + Math.log(lm.getCharNgramProb(contextWithSpace, c));
						if (score != Double.NEGATIVE_INFINITY) {
							int[] nextContext = shrinkContext(a.append(contextWithSpace, c));
							result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.TMPL), score));
						}
					}
				}
			} else if (type == TransitionStateType.RMRGN) {
				{
					double score = Math.log(LINE_MRGN_PROB);
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.LMRGN), score));
					}
				}
				for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
					double score = Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(context, c));
					if (score != Double.NEGATIVE_INFINITY) {
						int[] nextContext = shrinkContext(a.append(context, c));
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.TMPL), score));
					}
				}
			} else if (type == TransitionStateType.RMRGN_HPHN || type == TransitionStateType.RMRGN_HPHN_INIT) {
				{
					double score = Math.log(LINE_MRGN_PROB);
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.LMRGN_HPHN), score));
					}
				}
				for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
					double score = Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(context, c));
					if (c != spaceCharIndex && !isPunc[c]) {
						int[] nextContext = shrinkContext(a.append(context, c));
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.TMPL), score));
					}
				}
			} else if (type == TransitionStateType.LMRGN || type == TransitionStateType.LMRGN_HPHN) {
				{
					double score = Math.log(LINE_MRGN_PROB);
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(new int[0], TransitionStateType.LMRGN), score));
					}
				}
				for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
					double score = Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(context, c));
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(new int[] {c}, TransitionStateType.TMPL), score));
					}
				}
			}
			return result;
		}
		
		public double endLogProb() {
			return 0.0;
		}
		
		public Collection<Pair<TransitionState,Double>> forwardTransitions() {
			int[] context = getContext();
			TransitionStateType type = getType();
			List<Pair<TransitionState,Double>> result = new ArrayList<Pair<TransitionState,Double>>();
			if (type == TransitionStateType.LMRGN) {
				{
					double score = Math.log(LINE_MRGN_PROB);
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.LMRGN), score));
					}
				}
				for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
					double score = Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(context, c));
					if (score != Double.NEGATIVE_INFINITY) {
						int[] nextContext = shrinkContext(a.append(context, c));
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.TMPL), score));
					}
				}
			} else if (type == TransitionStateType.LMRGN_HPHN) {
				{
					double score = Math.log(LINE_MRGN_PROB);
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.LMRGN_HPHN), score));
					}
				}
				for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
					double score = Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(context, c));
					if (c != spaceCharIndex && !isPunc[c]) {
						int[] nextContext = shrinkContext(a.append(context, c));
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.TMPL), score));
					}
				}
			} else if (type == TransitionStateType.RMRGN) {
				double score = Math.log(LINE_MRGN_PROB);
				if (score != Double.NEGATIVE_INFINITY) {
					result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.RMRGN), score));
				}
			} else if (type == TransitionStateType.RMRGN_HPHN) {
				double score = Math.log(LINE_MRGN_PROB);
				if (score != Double.NEGATIVE_INFINITY) {
					result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.RMRGN_HPHN), score));
				}
			} else if (type == TransitionStateType.RMRGN_HPHN_INIT) {
				double score = Math.log(LINE_MRGN_PROB);
				if (score != Double.NEGATIVE_INFINITY) {
					result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.RMRGN_HPHN), score));
				}
			} else if (type == TransitionStateType.TMPL) {
				{
					double score = Math.log(LINE_MRGN_PROB) + Math.log(1.0 - LINE_END_HYPHEN_PROB) + Math.log(lm.getCharNgramProb(context, spaceCharIndex));
					if (score != Double.NEGATIVE_INFINITY) {
						int[] nextContext = shrinkContext(a.append(context, spaceCharIndex));
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.RMRGN), score));
					}
				}
				{
					double score = Math.log(LINE_MRGN_PROB) + Math.log(LINE_END_HYPHEN_PROB);
					if (score != Double.NEGATIVE_INFINITY) {
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(context, TransitionStateType.RMRGN_HPHN_INIT), score));
					}
				}
				for (int nextC=0; nextC<lm.getCharacterIndexer().size(); ++nextC) {
					double score = Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(context, nextC));
					if (score != Double.NEGATIVE_INFINITY) {
						int[] nextContext = shrinkContext(a.append(context, nextC));
						result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(nextContext, TransitionStateType.TMPL), score));
					}
				}
			}
			return result;
		}
		
		public int getCharIndex() {
			return charIndex;
		}
		
		public int getOffset() {
			throw new Error("Method not implemented");
		}
		
		public int getExposure() {
			throw new Error("Method not implemented");
		}
		
		public int[] getContext() {
			return context;
		}
		
		public TransitionStateType getType() {
			return type;
		}
		
	}
	
	public static enum TransitionStateType {TMPL, LMRGN, LMRGN_HPHN, RMRGN, RMRGN_HPHN_INIT, RMRGN_HPHN};
	
	public static final double LINE_MRGN_PROB = 0.5;
	public static final double LINE_END_HYPHEN_PROB = 1e-8;
	
	private int n;
	private LanguageModel lm;
	private int spaceCharIndex;
	private int hyphenCharIndex;
	private boolean[] isPunc;

	public CharacterNgramTransitionModel(LanguageModel lm, int n) {
		this.lm = lm;
		this.n = n;
		this.spaceCharIndex = lm.getCharacterIndexer().getIndex(Main.SPACE);
		this.hyphenCharIndex = lm.getCharacterIndexer().getIndex(Main.HYPHEN);
		this.isPunc = new boolean[lm.getCharacterIndexer().size()];
		Arrays.fill(this.isPunc, false);
		for (String c : Main.PUNC) {
			isPunc[lm.getCharacterIndexer().getIndex(c)] = true;
		}
	}

	public Collection<Pair<TransitionState,Double>> startStates(int d) {
		List<Pair<TransitionState,Double>> result = new ArrayList<Pair<TransitionState,Double>>();
		result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(new int[0], TransitionStateType.LMRGN), Math.log(LINE_MRGN_PROB)));
		for (int c=0; c<lm.getCharacterIndexer().size(); ++c) {
			result.add(Pair.makePair((TransitionState) new CharacterNgramTransitionState(new int[] {c}, TransitionStateType.TMPL), Math.log((1.0 - LINE_MRGN_PROB)) + Math.log(lm.getCharNgramProb(new int[0], c))));
		}
		return result;
	}
	
	private int[] shrinkContext(int[] context) {
		if (context.length > n-1) context = shortenContextForward(context);
		while (!lm.containsContext(context) && context.length > 0) {
//			if (context.length == 0) {
//                throw new AssertionError("CharacterNgramTransitionModelMarkovOffset.shrinkContext: context.length == 0;");
//			}
			context = shortenContextForward(context);
		}
		return context;
	}
	
	private static int[] shortenContextForward(int[] context) {
		if (context.length > 0) {
			int[] result = new int[context.length-1];
			System.arraycopy(context, 1, result, 0, result.length);
			return result;
		} else {
			return context;
		}
	}
	
}
