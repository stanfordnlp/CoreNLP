package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Beam;
import edu.stanford.nlp.util.Scored;
import edu.stanford.nlp.util.ScoredComparator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A class capable of computing the best sequence given a SequenceModel.
 * Uses beam search.
 *
 * @author Dan Klein
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class BeamBestSequenceFinder implements BestSequenceFinder {

  // todo [CDM 2013]: AFAICS, this class doesn't actually work correctly AND gives nondeterministic answers. See the commented out test in BestSequenceFinderTest

  private static int[] tmp = null;

  private static class TagSeq implements Scored {

    private static class TagList {
      int tag = -1;
      TagList last = null;
    }

    private double score = 0.0;

    public double score() {
      return score;
    }

    private int size = 0;

    public int size() {
      return size;
    }

    private TagList info = null;

    public int[] tmpTags(int count, int s) {
      if (tmp == null || tmp.length < s) {
        //tmp = new int[1024*128];
        tmp = new int[s];
      }
      TagList tl = info;
      int i = size() - 1;
      while (tl != null && count >= 0) {
        tmp[i] = tl.tag;
        i--;
        count--;
        tl = tl.last;
      }
      return tmp;
    }

    public int[] tags() {
      int[] t = new int[size()];
      int i = size() - 1;
      for (TagList tl = info; tl != null; tl = tl.last) {
        t[i] = tl.tag;
        i--;
      }
      return t;
    }

    public void extendWith(int tag) {
      TagList last = info;
      info = new TagList();
      info.tag = tag;
      info.last = last;
      size++;
    }

    public void extendWith(int tag, SequenceModel ts, int s) {
      extendWith(tag);
      int[] tags = tmpTags(ts.leftWindow() + 1 + ts.rightWindow(), s);
      score += ts.scoreOf(tags, size() - ts.rightWindow() - 1);

      //for (int i=0; i<tags.length; i++)
      //System.out.print(tags[i]+" ");
      //System.out.println("\nWith "+tag+" score was "+score);
    }

    public TagSeq tclone() {
      TagSeq o = new TagSeq();
      o.info = info;
      o.size = size;
      o.score = score;
      return o;
    }

  } // end class TagSeq


  private int beamSize;
  private boolean exhaustiveStart;
  private boolean recenter = true;

  public int[] bestSequence(SequenceModel ts) {
    return bestSequence(ts, (1024 * 128));
  }

  public int[] bestSequence(SequenceModel ts, int size) {

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();
    int padLength = length + leftWindow + rightWindow;
    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];
    for (int pos = 0; pos < padLength; pos++) {
      tags[pos] = ts.getPossibleValues(pos);
      tagNum[pos] = tags[pos].length;
    }

    Beam newBeam = new Beam(beamSize, ScoredComparator.ASCENDING_COMPARATOR);
    TagSeq initSeq = new TagSeq();
    newBeam.add(initSeq);
    for (int pos = 0; pos < padLength; pos++) {
      //System.out.println("scoring word " + pos + " / " + (leftWindow + length) + ", tagNum = " + tagNum[pos] + "...");
      //System.out.flush();

      Beam oldBeam = newBeam;
      if (pos < leftWindow + rightWindow && exhaustiveStart) {
        newBeam = new Beam(100000, ScoredComparator.ASCENDING_COMPARATOR);
      } else {
        newBeam = new Beam(beamSize, ScoredComparator.ASCENDING_COMPARATOR);
      }
      // each hypothesis gets extended and beamed
      for (Iterator beamI = oldBeam.iterator(); beamI.hasNext();) {
        // System.out.print("#"); System.out.flush();
        TagSeq tagSeq = (TagSeq) beamI.next();
        for (int nextTagNum = 0; nextTagNum < tagNum[pos]; nextTagNum++) {
          TagSeq nextSeq = tagSeq.tclone();

          if (pos >= leftWindow + rightWindow) {
            nextSeq.extendWith(tags[pos][nextTagNum], ts, size);
          } else {
            nextSeq.extendWith(tags[pos][nextTagNum]);
          }

          //System.out.println("Created: "+nextSeq.score()+" %% "+arrayToString(nextSeq.tags(), nextSeq.size()));
          newBeam.add(nextSeq);
          //		System.out.println("Beam size: "+newBeam.size()+" of "+beamSize);
          //System.out.println("Best is: "+((Scored)newBeam.iterator().next()).score());
        }
      }
      // System.out.println(" done");
      if (recenter) {
        double max = Double.NEGATIVE_INFINITY;
        for (Iterator beamI = newBeam.iterator(); beamI.hasNext();) {
          TagSeq tagSeq = (TagSeq) beamI.next();
          if (tagSeq.score > max) {
            max = tagSeq.score;
          }
        }
        for (Iterator beamI = newBeam.iterator(); beamI.hasNext();) {
          TagSeq tagSeq = (TagSeq) beamI.next();
          tagSeq.score -= max;
        }
      }
    }
    try {
      TagSeq bestSeq = (TagSeq) newBeam.iterator().next();
      int[] seq = bestSeq.tags();
      return seq;
    } catch (NoSuchElementException e) {
      System.err.println("Beam empty -- no best sequence.");
      return null;
    }

    /*
    int[] tempTags = new int[padLength];

    // Set up product space sizes
    int[] productSizes = new int[padLength];

    int curProduct = 1;
    for (int i=0; i<leftWindow+rightWindow; i++)
      curProduct *= tagNum[i];
    for (int pos = leftWindow+rightWindow; pos < padLength; pos++) {
      if (pos > leftWindow+rightWindow)
	curProduct /= tagNum[pos-leftWindow-rightWindow-1]; // shift off
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos-rightWindow] = curProduct;
    }

    // Score all of each window's options
    double[][] windowScore = new double[padLength][];
    for (int pos=leftWindow; pos<leftWindow+length; pos++) {
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags,tags[0][0]);
      for (int product=0; product<productSizes[pos]; product++) {
	int p = product;
	int shift = 1;
	for (int curPos = pos+rightWindow; curPos >= pos-leftWindow; curPos--) {
	  tempTags[curPos] = tags[curPos][p % tagNum[curPos]];
	  p /= tagNum[curPos];
	  if (curPos > pos)
	    shift *= tagNum[curPos];
	}
	if (tempTags[pos] == tags[pos][0]) {
	  // get all tags at once
	  double[] scores = ts.scoresOf(tempTags, pos);
	  // fill in the relevant windowScores
	  for (int t = 0; t < tagNum[pos]; t++) {
	    windowScore[pos][product+t*shift] = scores[t];
	  }
	}
      }
    }


    // Set up score and backtrace arrays
    double[][] score = new double[padLength][];
    int[][] trace = new int[padLength][];
    for (int pos=0; pos<padLength; pos++) {
      score[pos] = new double[productSizes[pos]];
      trace[pos] = new int[productSizes[pos]];
    }

    // Do forward Viterbi algorithm

    // loop over the classification spot
    //System.err.println();
    for (int pos=leftWindow; pos<length+leftWindow; pos++) {
      //System.err.print(".");
      // loop over window product types
      for (int product=0; product<productSizes[pos]; product++) {
	// check for initial spot
	if (pos==leftWindow) {
	  // no predecessor type
	  score[pos][product] = windowScore[pos][product];
	  trace[pos][product] = -1;
	} else {
	  // loop over possible predecessor types
	  score[pos][product] = Double.NEGATIVE_INFINITY;
	  trace[pos][product] = -1;
	  int sharedProduct = product / tagNum[pos+rightWindow];
	  int factor = productSizes[pos] / tagNum[pos+rightWindow];
	  for (int newTagNum=0; newTagNum<tagNum[pos-leftWindow-1]; newTagNum++) {
	    int predProduct = newTagNum*factor+sharedProduct;
	    double predScore = score[pos-1][predProduct]+windowScore[pos][product];
	    if (predScore > score[pos][product]) {
	      score[pos][product] = predScore;
	      trace[pos][product] = predProduct;
	    }
	  }
	}
      }
    }

    // Project the actual tag sequence
    double bestFinalScore = Double.NEGATIVE_INFINITY;
    int bestCurrentProduct = -1;
    for (int product=0; product<productSizes[leftWindow+length-1]; product++) {
      if (score[leftWindow+length-1][product] > bestFinalScore) {
	bestCurrentProduct = product;
	bestFinalScore = score[leftWindow+length-1][product];
      }
    }
    int lastProduct = bestCurrentProduct;
    for (int last=padLength-1; last>=length-1; last--) {
      tempTags[last] = tags[last][lastProduct % tagNum[last]];
      lastProduct /= tagNum[last];
    }
    for (int pos=leftWindow+length-2; pos>=leftWindow; pos--) {
      int bestNextProduct = bestCurrentProduct;
      bestCurrentProduct = trace[pos+1][bestNextProduct];
      tempTags[pos-leftWindow] = tags[pos-leftWindow][bestCurrentProduct / (productSizes[pos]/tagNum[pos-leftWindow])];
    }
    return tempTags;
    */
  }

  /*
  public int[] bestSequenceOld(TagScorer ts) {

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();
    int padLength = length+leftWindow+rightWindow;
    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];
    for (int pos = 0; pos < padLength; pos++) {
      tags[pos] = ts.tagsAt(pos);
      tagNum[pos] = tags[pos].length;
    }

    int[] tempTags = new int[padLength];

    // Set up product space sizes
    int[] productSizes = new int[padLength];

    int curProduct = 1;
    for (int i=0; i<leftWindow+rightWindow; i++)
      curProduct *= tagNum[i];
    for (int pos = leftWindow+rightWindow; pos < padLength; pos++) {
      if (pos > leftWindow+rightWindow)
	curProduct /= tagNum[pos-leftWindow-rightWindow-1]; // shift off
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos-rightWindow] = curProduct;
    }

    // Score all of each window's options
    double[][] windowScore = new double[padLength][];
    for (int pos=leftWindow; pos<leftWindow+length; pos++) {
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags,tags[0][0]);
      for (int product=0; product<productSizes[pos]; product++) {
	int p = product;
	for (int curPos = pos+rightWindow; curPos >= pos-leftWindow; curPos--) {
	  tempTags[curPos] = tags[curPos][p % tagNum[curPos]];
	  p /= tagNum[curPos];
	}
	windowScore[pos][product] = ts.scoreOf(tempTags, pos);
      }
    }


    // Set up score and backtrace arrays
    double[][] score = new double[padLength][];
    int[][] trace = new int[padLength][];
    for (int pos=0; pos<padLength; pos++) {
      score[pos] = new double[productSizes[pos]];
      trace[pos] = new int[productSizes[pos]];
    }

    // Do forward Viterbi algorithm

    // loop over the classification spot
    //System.err.println();
    for (int pos=leftWindow; pos<length+leftWindow; pos++) {
      //System.err.print(".");
      // loop over window product types
      for (int product=0; product<productSizes[pos]; product++) {
	// check for initial spot
	if (pos==leftWindow) {
	  // no predecessor type
	  score[pos][product] = windowScore[pos][product];
	  trace[pos][product] = -1;
	} else {
	  // loop over possible predecessor types
	  score[pos][product] = Double.NEGATIVE_INFINITY;
	  trace[pos][product] = -1;
	  int sharedProduct = product / tagNum[pos+rightWindow];
	  int factor = productSizes[pos] / tagNum[pos+rightWindow];
	  for (int newTagNum=0; newTagNum<tagNum[pos-leftWindow-1]; newTagNum++) {
	    int predProduct = newTagNum*factor+sharedProduct;
	    double predScore = score[pos-1][predProduct]+windowScore[pos][product];
	    if (predScore > score[pos][product]) {
	      score[pos][product] = predScore;
	      trace[pos][product] = predProduct;
	    }
	  }
	}
      }
    }

    // Project the actual tag sequence
    double bestFinalScore = Double.NEGATIVE_INFINITY;
    int bestCurrentProduct = -1;
    for (int product=0; product<productSizes[leftWindow+length-1]; product++) {
      if (score[leftWindow+length-1][product] > bestFinalScore) {
	bestCurrentProduct = product;
	bestFinalScore = score[leftWindow+length-1][product];
      }
    }
    int lastProduct = bestCurrentProduct;
    for (int last=padLength-1; last>=length-1; last--) {
      tempTags[last] = tags[last][lastProduct % tagNum[last]];
      lastProduct /= tagNum[last];
    }
    for (int pos=leftWindow+length-2; pos>=leftWindow; pos--) {
      int bestNextProduct = bestCurrentProduct;
      bestCurrentProduct = trace[pos+1][bestNextProduct];
      tempTags[pos-leftWindow] = tags[pos-leftWindow][bestCurrentProduct / (productSizes[pos]/tagNum[pos-leftWindow])];
    }
    return tempTags;
  }
  */


  public BeamBestSequenceFinder(int beamSize) {
    this(beamSize, false, false);
  }

  public BeamBestSequenceFinder(int beamSize, boolean exhaustiveStart) {
    this(beamSize, exhaustiveStart, false);
  }

  public BeamBestSequenceFinder(int beamSize, boolean exhaustiveStart, boolean recenter) {
    this.exhaustiveStart = exhaustiveStart;
    this.beamSize = beamSize;
    this.recenter = recenter;
  }

}
