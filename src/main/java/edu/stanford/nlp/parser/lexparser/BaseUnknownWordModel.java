package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;


/**
 *  An unknown word model for a generic language.  This was originally designed for
 *  German, changing only to remove German-specific numeric features.  Models unknown
 *  words based on their prefix and suffixes, as well as capital letters.
 *
 * @author Roger Levy
 * @author Greg Donaker (corrections and modeling improvements)
 * @author Christopher Manning (generalized and improved what Greg did)
 * @author Anna Rafferty
 *
 */
public class BaseUnknownWordModel implements UnknownWordModel  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(BaseUnknownWordModel.class);

  private static final long serialVersionUID = 6355171148751673822L;

  protected static final boolean VERBOSE = false;

  protected boolean useFirst; //= true;
  private final boolean useEnd;
  protected boolean useGT;
  private final boolean useFirstCap; // Only care if first is capitalized

  private int endLength = 2; // only used if useEnd==true

  /** What type of equivalence classing is done in getSignature */
  protected final int unknownLevel;

  protected static final String unknown = "UNK";

  protected static final int nullWord = -1;
  protected static final short nullTag = -1;

  protected static final IntTaggedWord NULL_ITW = new IntTaggedWord(nullWord, nullTag);

  protected final TrainOptions trainOptions;

  protected final Index<String> wordIndex;
  protected final Index<String> tagIndex;

  /**
   * Has counts for taggings in terms of unseen signatures. The IntTagWords are
   * for (tag,sig), (tag,null), (null,sig), (null,null). (None for basic UNK if
   * there are signatures.)
   */
  protected final ClassicCounter<IntTaggedWord> unSeenCounter;

  /** This maps from a tag (as a label) to a Counter from word signatures to
   *  their P(sig|tag), as estimated in the model. For Chinese, the word
   *  signature is just the first character or its unicode type for things
   *  that aren't Chinese characters.
   */
  protected final Map<Label,ClassicCounter<String>> tagHash;

  /** This is the set of all signatures that we have seen. */
  private final Set<String> seenEnd;

  final Map<String,Float> unknownGT;

  /** All classes that implement UnknownWordModel must call the constructor that initializes this variable. */
  private final Lexicon lex;


  public BaseUnknownWordModel(Options op, Lexicon lex,
                              Index<String> wordIndex,
                              Index<String> tagIndex,
                              ClassicCounter<IntTaggedWord> unSeenCounter,
                              Map<Label,ClassicCounter<String>> tagHash,
                              Map<String,Float> unknownGT,
                              Set<String> seenEnd) {
    endLength = op.lexOptions.unknownSuffixSize;
    // TODO: refactor these terms into BaseUnknownWordModelTrainer
    useEnd = (op.lexOptions.unknownSuffixSize > 0 &&
              op.lexOptions.useUnknownWordSignatures > 0);
    useFirstCap = op.lexOptions.useUnknownWordSignatures > 0;
    useGT = (op.lexOptions.useUnknownWordSignatures == 0);
    useFirst = false;
    this.lex = lex;
    this.trainOptions = op.trainOptions;
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    this.unSeenCounter = unSeenCounter;
    this.tagHash = tagHash;
    this.seenEnd = seenEnd;
    this.unknownGT = unknownGT;
    unknownLevel = op.lexOptions.useUnknownWordSignatures;
  }


  /**
   * This constructor creates an UWM with empty data structures.  Only
   * use if loading in the data separately, such as by reading in text
   * lines containing the data.
   */
  public BaseUnknownWordModel(Options op, Lexicon lex,
                              Index<String> wordIndex,
                              Index<String> tagIndex) {
    this(op, lex, wordIndex, tagIndex,
            new ClassicCounter<>(),
         Generics.<Label,ClassicCounter<String>>newHashMap(),
         Generics.<String,Float>newHashMap(),
         Generics.<String>newHashSet());
  }


  /**
   * Currently we don't consider loc or the other parameters in determining
   * score in the default implementation; only English uses them.
   */
  @Override
  public float score(IntTaggedWord itw, int loc, double c_Tseen, double total, double smooth, String word) {
    return score(itw, word);
  }


  // todo [cdm 2010]: Recheck that this method really does the right thing in making a P(W|T) estimate....
  public float score(IntTaggedWord itw, String word) {
    float logProb;

    // Label tag = itw.tagLabel();
    String tagStr = itw.tagString(tagIndex);
    Label tag = new Tag(tagStr);

    // testing
    //EncodingPrintWriter.out.println("Scoring unknown word " + word + " with tag " + tag,encoding);
    // end testing

    if (useEnd || useFirst || useFirstCap) {
      String end = getSignature(word, -1); // The getSignature here doesn't use sentence position
      if (useGT && ! seenEnd.contains(end)) {
        logProb = scoreGT(tagStr);
      } else {
        if ( ! seenEnd.contains(end)) {
          end = unknown;
        }
        //System.out.println("using end-character model for for unknown word "+  word + " for tag " + tag);

        /* get the Counter of terminal rewrites for the relevant tag */
        ClassicCounter<String> wordProbs = tagHash.get(tag);
        /* if the proposed tag has never been seen before, issue a
         * warning and return probability 0
         */
        if (wordProbs == null) {
          log.info("Warning: proposed tag is unseen in training data:\t"+tagStr);
          logProb = Float.NEGATIVE_INFINITY;
        } else if (wordProbs.keySet().contains(end)) {
          logProb = (float) wordProbs.getCount(end);
        } else {
          logProb = (float) wordProbs.getCount(unknown);
        }
      }
    } else if (useGT) {
      logProb = scoreGT(tagStr);
    } else {
      log.info("Warning: no unknown word model in place!\nGiving the combination " + word + ' ' + tagStr + " zero probability.");
      logProb = Float.NEGATIVE_INFINITY; // should never get this!
    }

    //EncodingPrintWriter.out.println("Unknown word estimate for " + word + " as " + tag + ": " + logProb,encoding); //debugging
    return logProb;
  }


  /** Calculate P(Tag|Signature) with Bayesian smoothing via just P(Tag|Unknown) */
  @Override
  public double scoreProbTagGivenWordSignature(IntTaggedWord iTW, int loc, double smooth, String word) {
    throw new UnsupportedOperationException();
  }


  // todo [cdm 2012, based on error report from Thang]: this is broken because the Label passed in is a Tag, which will never match on the CoreLabel's now in unknownGT.keySet()
  // todo [cdm 2012]: But see if this bug is only if you use Lexicon's main method, or also when training a parser in the usual way.
  protected float scoreGT(String tag) {
    if (VERBOSE) log.info("using GT for unknown word and tag " + tag);
    if (unknownGT.containsKey(tag)) {
      return unknownGT.get(tag).floatValue();
    } else {
      return Float.NEGATIVE_INFINITY;
    }
  }

  /**
   * Signature for a specific word; loc parameter is ignored.
   * @param word The word
   * @param loc Its sentence position
   * @return A "signature" (which represents an equivalence class of Strings), e.g., a suffix of the string
   */
  @Override
  public String getSignature(String word, int loc) {
    StringBuilder subStr = new StringBuilder("UNK-");
    int n = word.length() - 1;
    char first = word.charAt(0);
    if (useFirstCap) {
      if (Character.isUpperCase(first) || Character.isTitleCase(first)) {
        subStr.append('C');
      } else {
        subStr.append('c');
      }
    }
    if (useFirst) {
      subStr.append(first);
    }
    if (useEnd) {
      subStr.append(word.substring(n - endLength > 0 ? n - endLength : 0, n));
    }
    return subStr.toString();
  }

  @Override
  public int getSignatureIndex(int wordIndex, int sentencePosition, String word) {
    return 0;
  }

  /**
   * Get the lexicon associated with this unknown word model; usually not used, but
   * might be useful to tell you if a related word is known or unknown, for example.
   */
  @Override
  public Lexicon getLexicon() {
    return lex;
  }


  @Override
  public int getUnknownLevel() {
    return unknownLevel;
  }

  /**
   * Adds the tagging with count to the data structures in this Lexicon.
   */
  @Override
  public void addTagging(boolean seen, IntTaggedWord itw, double count) {
    if (seen) {
      log.info("UWM.addTagging: Shouldn't call with seen word!");
   } else {
      unSeenCounter.incrementCount(itw, count);
      // if (itw.tag() == nullTag) {
      // sigs.add(itw);
      // }
    }
  }

  @Override
  public Counter<IntTaggedWord> unSeenCounter() {
    return unSeenCounter;
  }

}
