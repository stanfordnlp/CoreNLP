package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Interner;

import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.ANY_WORD_INT;
import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.ANY_TAG_INT;
import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.STOP_WORD_INT;
import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.STOP_TAG_INT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An abstract base class for dependency grammars.  The only thing you have
 * to implement in a subclass is scoreTB (score a "tag binned" dependency
 * in the tagProjection space).  A subclass also has to either call
 * super() in its constructor, or otherwise initialize the tagBin array.
 * The call to initTagBins() (in the constructor) must be made after all
 * keys have been entered into tagIndex.
 *
 * @author Galen Andrew
 */
public abstract class AbstractDependencyGrammar implements DependencyGrammar {

  protected TagProjection tagProjection;
  protected final Index<String> tagIndex;
  protected final Index<String> wordIndex;
  protected int numTagBins;
  protected int[] tagBin;
  protected TreebankLanguagePack tlp;
  protected boolean directional;
  protected boolean useDistance;
  protected boolean useCoarseDistance;

  protected Lexicon lex;

  protected final IntTaggedWord stopTW;
  protected final IntTaggedWord wildTW;

  protected transient Map<IntDependency,IntDependency> expandDependencyMap =
      new HashMap<IntDependency,IntDependency>();

  private static final boolean DEBUG = false;

  protected int[] coarseDistanceBins = {0, 2, 5};
  protected int[] regDistanceBins = {0, 1, 5, 10};

  protected final Options op;

  transient protected Interner<IntTaggedWord> itwInterner =
    new Interner<IntTaggedWord>();

  public AbstractDependencyGrammar(TreebankLanguagePack tlp, TagProjection tagProjection, boolean directional, boolean useDistance, boolean useCoarseDistance, Options op, Index<String> wordIndex, Index<String> tagIndex) {
    this.tlp = tlp;
    this.tagProjection = tagProjection;
    this.directional = directional;
    this.useDistance = useDistance;
    this.useCoarseDistance = useCoarseDistance;
    this.op = op;
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    stopTW = new IntTaggedWord(STOP_WORD_INT, STOP_TAG_INT);
    wildTW = new IntTaggedWord(ANY_WORD_INT, ANY_TAG_INT);

    initTagBins();
  }

  public void setLexicon(Lexicon lexicon) {
    lex = lexicon;
  }

  /**
   * Default is no-op.
   */
  public void tune(Collection<Tree> trees) {
  }

  public int numTagBins() {
    return numTagBins;
  }

  public int tagBin(int tag) {
    if (tag < 0) {
      return tag;
    } else {
      return tagBin[tag];
    }
  }

  public boolean rootTW(IntTaggedWord rTW) {
    // System.out.println("rootTW: checking if " + rTW.toString("verbose") +
    // " == " + Lexicon.BOUNDARY_TAG + "[" +
    // tagIndex.indexOf(Lexicon.BOUNDARY_TAG) + "]" + ": " +
    // (rTW.tag == tagIndex.indexOf(Lexicon.BOUNDARY_TAG)));
    return rTW.tag == tagIndex.indexOf(Lexicon.BOUNDARY_TAG);
  }

  protected short valenceBin(int distance) {
    if (!useDistance) {
      return 0;
    }
    if (distance < 0) {
      return -1;
    }
    if (distance == 0) {
      return 0;
    }
    return 1;
  }

  public int numDistBins() {
    return useCoarseDistance ? 4 : 5;
  }

  public short distanceBin(int distance) {
    if (!useDistance) {
      return 0;
    } else if (useCoarseDistance) {
      return coarseDistanceBin(distance);
    } else {
      return regDistanceBin(distance);
    }
  }

  public short regDistanceBin(int distance) {
    for(short i=0; i<regDistanceBins.length; ++i)
      if (distance <= regDistanceBins[i])
        return i;
    return (short) regDistanceBins.length;
  }

  public short coarseDistanceBin(int distance) {
    for(short i=0; i<coarseDistanceBins.length; ++i)
      if (distance <= coarseDistanceBins[i])
        return i;
    return (short) coarseDistanceBins.length;
  }

  void setCoarseDistanceBins(int[] bins) {
    assert(bins.length == 3);
    coarseDistanceBins = bins;
  }

  void setRegDistanceBins(int[] bins) {
    assert(bins.length == 4);
    regDistanceBins = bins;
  }

  protected void initTagBins() {
    Index<String> tagBinIndex = new HashIndex<String>();
    if (DEBUG) {
      System.err.println();
      System.err.println("There are " + tagIndex.size() + " tags.");
    }
    tagBin = new int[tagIndex.size()];
    for (int t = 0; t < tagBin.length; t++) {
      String tagStr = tagIndex.get(t);
      String binStr;
      if (tagProjection == null) {
        binStr = tagStr;
      } else {
        binStr = tagProjection.project(tagStr);
      }
      tagBin[t] = tagBinIndex.indexOf(binStr, true);
      if (DEBUG) {
        System.err.println("initTagBins: Mapped " + tagStr + " (" + t +
                           ") to " + binStr + " (" + tagBin[t] + ")");
      }
    }
    numTagBins = tagBinIndex.size();
    if (DEBUG) {
      System.err.println("initTagBins: tags " + tagBin.length + " bins " +
                         numTagBins);
      System.err.println("tagBins: " + tagBinIndex);
    }
  }

  public double score(IntDependency dependency) {
    return scoreTB(dependency.head.word, tagBin(dependency.head.tag), dependency.arg.word, tagBin(dependency.arg.tag), dependency.leftHeaded, dependency.distance);
  }

  // currently unused
  public double score(int headWord, int headTag, int argWord, int argTag, boolean leftHeaded, int dist) {
    IntDependency tempDependency = new IntDependency(headWord, headTag, argWord, argTag, leftHeaded, dist);
    return score(tempDependency); // this method tag bins
  }

  public double scoreTB(int headWord, int headTag, int argWord, int argTag, boolean leftHeaded, int dist) {
    IntDependency tempDependency = new IntDependency(headWord, headTag, argWord, argTag, leftHeaded, dist);
    return scoreTB(tempDependency);
  }

  private void readObject(ObjectInputStream ois)
    throws IOException, ClassNotFoundException
  {
    ois.defaultReadObject();
    // reinitialize the transient objects
    itwInterner = new Interner<IntTaggedWord>();
  }

  /**
   * Default is to throw exception.
   * @throws IOException
   */
  public void readData(BufferedReader in) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Default is to throw exception.
   * @throws IOException
   */
  public void writeData(PrintWriter out) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * This is a custom interner that simultaneously creates and interns
   * an IntDependency.
   *
   * @return An interned IntDependency
   */
  protected IntDependency intern(IntTaggedWord headTW, IntTaggedWord argTW, boolean leftHeaded, short dist) {
    Map<IntDependency,IntDependency> map = expandDependencyMap;
    IntDependency internTempDependency = new IntDependency(itwInterner.intern(headTW), itwInterner.intern(argTW), leftHeaded, dist);
    IntDependency returnDependency = internTempDependency;
    if (map != null) {
      returnDependency = map.get(internTempDependency);
      if (returnDependency == null) {
        map.put(internTempDependency, internTempDependency);
        returnDependency = internTempDependency;
      }
    }
    return returnDependency;
  }

  private static final long serialVersionUID = 3L;

}
