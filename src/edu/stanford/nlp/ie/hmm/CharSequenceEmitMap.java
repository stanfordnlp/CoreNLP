package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.pnp.PnpClassifier;
import edu.stanford.nlp.stats.ClassicCounter;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * EmitMap that models emissions based on character sequences inside words
 * rather than whole words. Uses a PnpClassifier as the internal model. This
 * is not a typical EmitMap in that you can't directly set word probabilities
 * and there's no word->prob map. Instead, word probabilities are computed by
 * aggregating the probabilities of their internal character sequences. To use,
 * call {@link #addObservation} to pass in examples of contiguous strings of this
 * stat's type. The character sequence probabilities will be estimated based
 * on the character statistics of those observation strings. When you're done
 * adding observations, call {@link #tuneParameters} to perform a one-time
 * estimation of various model-internal parameters. Then use {@link #get} as
 * you normally would for an emit map and the probabilities will be dynamically
 * calculated. If you want to generate emissions on a per-char rather than per-word
 * basis, use {@link #getCharProb}.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class CharSequenceEmitMap implements EmitMap {
  private PnpClassifier pnpc;
  private Properties props; // pnpc properties (saves maxNGramLength)
  private boolean tuned; // have pnpc's parameters been tuned yet
  private int charPosition; // what char in the sequence this emit map generates
  private boolean ignoreContext; // whether to ignore chars before the start of this pnp
  private boolean wordModel; // whether this is being used as a word model (normal emit map) or char-level model
  private EmitMap originalEmissions; // kept only for printing favorite words

  /**
   * Constructs a new CharSequenceEmitMap using the given PNP classifier as
   * the underlying model and considering the given number of conditioning history chars
   * for n-grams passed into <tt>getCharProb</tt>. If charPosition is
   * less than maxNGramLength, the older history chars will be replaced
   * with start symbols to make it look as though the ngram occurred near
   * the beginning of the sequence. Use this for states that emit characters
   * near the beginning of a target. This assumes the classifier comes
   * "pre-tuned" i.e. don't call tune on this emit map, but do tune the
   * classifier before using the emit map to report probabilities.
   */
  public CharSequenceEmitMap(PnpClassifier pnpc, int charPosition, boolean ignoreContext) {
    this.pnpc = pnpc;
    this.charPosition = charPosition;
    this.ignoreContext = ignoreContext;
    tuned = true;
    wordModel = false;
  }

  /**
   * Constructs a new CharSequenceEmitMap using char n-grams of the given
   * length and considering the given number of conditioning history chars
   * for n-grams passed into <tt>getCharProb</tt>. If charPosition is
   * less than maxNGramLength, the older history chars will be replaced
   * with start symbols to make it look as though the ngram occurred near
   * the beginning of the sequence. Use this for states that emit characters
   * near the beginning of a target. If charPosition is -1, the emit map
   * works in a special "end sequence detection" mode where getCharProb
   * returns 0 unless the sequence ends in a space, in which case it returns
   * the probability of ending the pnp given the history.
   */
  public CharSequenceEmitMap(int maxNGramLength, int charPosition) {
    props = getPnpClassifierProperties(maxNGramLength);
    pnpc = new PnpClassifier(props);
    this.charPosition = charPosition;
    ignoreContext = true;
    tuned = false;
    wordModel = false;
  }

  /**
   * Constructs a new CharSequenceEmitMap using char n-grams of the given
   * length and considering the full history passed into <tt>getCharProb</tt>.
   * This is the constructor you want to use if you're using this EmitMap to
   * generate entire words at a time (i.e. normal EmitMap behavior).
   */
  public CharSequenceEmitMap(int maxNGramLength) {
    this(maxNGramLength, maxNGramLength);
  }

  /**
   * Constructs a new CharSequenceEmitMap using char n-grams of a default
   * length (as specified in {@link PnpClassifier#getDefaultProperties}).
   *
   * @see #CharSequenceEmitMap(int)
   */
  public CharSequenceEmitMap() {
    this(Integer.parseInt(PnpClassifier.getDefaultProperties().getProperty("cn")));
  }

  /**
   * This is the constructor you want for a normal word-level EmitMap.
   * The original emissions are used to train the char model.
   * The model is trained upon construction.
   */
  public CharSequenceEmitMap(EmitMap originalEmissions, int maxNGramLength) {
    this(maxNGramLength);
    num = globalnum++; // JS: TAKE ME OUT
    tuneParameters(originalEmissions.getCounter(), null);
  }

  private int num; // JS: TAKE ME OUT
  private static int globalnum = 0; // JS: TAKE ME OUT

  /**
   * This is the constructor you want for a normal word-level EmitMap.
   * The original emissions are used to train the char model.
   * The model is trained upon construction.
   * Default char n-gram length is used.
   */
  public CharSequenceEmitMap(EmitMap originalEmissions) {
    this(originalEmissions, Integer.parseInt(PnpClassifier.getDefaultProperties().getProperty("cn")));
  }

  /**
   * Counts ngram stats on all words (String keys) in the given Counter weighted
   * by their count.
   */
  private void addAllObservations(ClassicCounter observations) {
    for (Iterator iter = observations.keySet().iterator(); iter.hasNext();) {
      String word = (String) iter.next();
      addObservation(word, observations.getCount(word));
    }
  }

  /**
   * Learns various model-internal parameters using held out data that was
   * set aside from some of the calls to <tt>addObservation</tt>. Then trains
   * on that held-out data. Call this method when you're done adding
   * observations and before you use it to get probabilities.
   */
  public void tuneParameters() {

    pnpc.tuneParameters();
    tuned = true;
  }

  /**
   * Calls <tt>tuneParameters()</tt> and returns 0. TODO: integrate better.
   */
  public double tuneParameters(ClassicCounter expectedEmissions, HMM hmm) {
    //System.err.println("Tuning params:");
    wordModel = true;
    originalEmissions = new PlainEmitMap(expectedEmissions); // for printing
    //originalEmissions.printEmissions(new PrintWriter(System.err,true),false); // JS: TAKE ME OUT
    pnpc = new PnpClassifier(props); // create a fresh one
    addAllObservations(expectedEmissions);
    tuneParameters();
    return (0); // hack since we don't know how much params have changed since last iter
  }

  /**
   * Adds counts for all the char sequences in the given string.
   * Pass all the contiguous strings of this state's type through this
   * function to build the character statistics. Some of these observations
   * will be internally held-out to use for parameter estimation when you
   * call {@link #tuneParameters} (which you should do when you're done
   * adding observations). You shouldn't continue to add observations after
   * calling <tt>tuneParameters</tt> though it won't cause anything to break.
   */
  public void addObservation(String s, double weight) {
    pnpc.addCounts(s, true, weight);
  }

  /**
   * Adds the given observation with the standard weight of 1.0.
   */
  public void addObservation(String s) {
    addObservation(s, 1.0);
  }

  /**
   * Returns a modified version of the default PnpClassifier properties in
   * which only the char n-gram model is used. Uses the given max n-gram
   * length (i.e. the <tt>cn</tt> property), or uses the default if it's -1.
   */
  public static Properties getPnpClassifierProperties(int maxNGramLength) {
    Properties props = new Properties(PnpClassifier.getDefaultProperties());
    if (maxNGramLength != -1) {
      props.setProperty("cn", Integer.toString(maxNGramLength));
    }
    props.setProperty("startSymbol", "\u0003");
    props.setProperty("useLengthModel", "false");
    props.setProperty("useWordModel", "false");
    props.setProperty("useLengthNormalization", "false");
    props.setProperty("usePriorBoost", "false");
    //props.setProperty("DEBUG","true");   // TAKE ME OUT OR HOOK UP TO CALLER
    return (props);
  }

  /**
   * Returns the probability of generating s using the char n-gram.
   * If you call this before calling {@link #tuneParameters} it will throw
   * IllegalStateException.
   */
  public double get(String s) {
    if (!tuned) {
      throw(new IllegalStateException("must call tuneParameters before calling get"));
    }

    if (wordModel) {
      //System.err.println(num+"-get("+s+") = "+Math.exp(pnpc.getLogProb(s))); // JS: TAKE ME OUT
      return (Math.exp(pnpc.getLogProb(s))); // normal word-based use
    } else {
      return (getCharProb(s)); // char-based use
    }
  }

  /**
   * Returns the prob of the last char in the sequence given the rest.
   * If there are more than charPosition+1 chars in the sequence, only
   * considers the right-most charPosition worth of history and replaces
   * the older chars with start symbols. For example, if charPosition is 2,
   * <tt>getCharProb(abcde)</tt> returns P(e|&nsbp;&nbsp;cd). If the sequence
   * is longer than maxNGramLength only the rightmost portion is used.
   */
  public double getCharProb(String charSequence) {
    //System.err.print("getCharProb("+charSequence+") [cp="+charPosition+"] = ");
    // if you pass in too long a sequence, just uses the rightmost part
    if (charSequence.length() > pnpc.cn) {
      charSequence = charSequence.substring(charSequence.length() - pnpc.cn);
    }

    // turns chars older than the effective history into start symbols
    StringBuffer sb = new StringBuffer(charSequence);
    int start = charSequence.length() - charPosition - 1; // first char of this pnp
    if (charPosition == -1) {
      start = 0; // use full history for end detection
    }
    //System.err.println("start="+start);
    for (int i = 0; i < start; i++) {
      //System.err.println("cS["+i+"]="+charSequence.charAt(i));
      if (ignoreContext || i == start - 1) {
        //System.err.println(" - masking");
        sb.setCharAt(i, pnpc.startSymbol); // masks the char
      }
    }
    if (charPosition == -1) {
      // end sequence detection mode -> look for end symbol or give up
      if (charSequence.charAt(charSequence.length() - 1) == ' ') {
        sb.setCharAt(charSequence.length() - 1, pnpc.endSymbol);
      } else {
        //System.err.println("XXX = 0.0");
        return (0.0); // impossible to end on a char
      }
    }

    double prob = pnpc.getInterpolatedCharProb(sb.toString()); // emission prob
    //System.err.println("gIP("+sb.toString()+") = "+prob);
    return (prob);
  }

  /**
   * Returns the map from the original emissions used to train this model.
   */
  public ClassicCounter getCounter() {
    //throw(new UnsupportedOperationException("can't call getMap in CharSequenceEmitMap"));
    return (originalEmissions.getCounter());
  }

  /**
   * Throws UnsupportedOperationException (no internal map).
   */
  public void set(String s, double d) {
    throw(new UnsupportedOperationException("can't call set in CharSequenceEmitMap"));
  }

  /**
   * Prints out some or all of the most probable char sequences. For each
   * char sequence that occurred at least once, prints it along with the
   * empirical prob of the last char given the rest. Sorts with most common
   * sequences first. If <tt>justCommon</tt> is true, just prints top 30.
   */
  public void printEmissions(PrintWriter pw, boolean justCommon) {
    if (wordModel) {
      pw.println("Based on these original emissions:");
      originalEmissions.printEmissions(pw, justCommon);
    } else {
      pw.println("[charPosition=" + charPosition + "]");
    }
    /** // not very informative and quite slow
     Map charSequenceCounts=pnpc.getCharSequenceCounts();
     Map charSequenceProbs=new HashMap(charSequenceCounts.size());
     for(Iterator iter=charSequenceCounts.keySet().iterator();iter.hasNext();)
     {
     String charSequence=(String)iter.next();
     charSequenceProbs.put(charSequence,new Double(pnpc.getEmpiricalProb(charSequence)));
     }

     List sortedEntries=new ArrayList(charSequenceProbs.entrySet());
     Collections.sort(sortedEntries,new DoubleValueComparator());


     int n=sortedEntries.size();
     if(justCommon && n>30)
     {
     n=30;
     pw.println("Favorite char sequences");
     pw.println("-----------------------");
     }
     else
     {
     pw.println("All char sequences");
     pw.println("------------------");
     }
     for(int i=0;i<n;i++)
     {
     Map.Entry entry=(Map.Entry)sortedEntries.get(i);
     pw.println("["+entry.getKey()+"]\t"+entry.getValue());
     }
     */
  }

  /**
   * Compares two Map.Entry objects with Double values, preferring the
   * larger value.
   */
  private static class DoubleValueComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      Map.Entry e1 = (Map.Entry) o1;
      Map.Entry e2 = (Map.Entry) o2;

      Double d1 = (Double) e1.getValue();
      Double d2 = (Double) e2.getValue();
      return (d2.compareTo(d1));
    }
  }

  /**
   * Does nothing.
   */
  public void printUnseenEmissions(PrintWriter pw, NumberFormat nf) {
  }

  /**
   * For internal debugging purposes only.
   */
  /*
  public static void main(String[] args)
  {
      CharSequenceEmitMap csEmitMap=new CharSequenceEmitMap();
      csEmitMap.addObservation("abcd efg hi j");
      csEmitMap.addObservation("abc");
      csEmitMap.addObservation("jkl");
      csEmitMap.addObservation("efgh");
      csEmitMap.addObservation("hxi");
      csEmitMap.printEmissions(new PrintWriter(System.out,true),false);
  }
   */

  public static void main(String[] args) {
    ClassicCounter target = new ClassicCounter();
    for (int i = 0; i < 20; i++) {
      target.incrementCount("France" + ('a' + (char) i));
    }
    //target.incrementCount("Greece");
    CharSequenceEmitMap csem = new CharSequenceEmitMap(new PlainEmitMap(target), 2);
    System.err.println(csem.get("Francea"));
    //System.err.println(csem.get("Greece"));
  }
}
