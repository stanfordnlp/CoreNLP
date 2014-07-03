package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This is the default concrete instantiation of the Lexicon interface. It was
 * originally built for Penn Treebank English.
 *
 * @author Dan Klein
 * @author Galen Andrew
 * @author Christopher Manning
 */
public class BaseLexicon implements Lexicon {

  protected UnknownWordModel uwModel;
  protected final String uwModelTrainerClass;
  protected transient UnknownWordModelTrainer uwModelTrainer;

  protected static final boolean DEBUG_LEXICON = false;
  protected static final boolean DEBUG_LEXICON_SCORE = false;

  protected static final int nullWord = -1;

  protected static final short nullTag = -1;

  protected static final IntTaggedWord NULL_ITW = new IntTaggedWord(nullWord, nullTag);

  protected final TrainOptions trainOptions;
  protected final TestOptions testOptions;

  protected final Options op;

  /**
   * If a word has been seen more than this many times, then relative
   * frequencies of tags are used for POS assignment; if not, they are smoothed
   * with tag priors.
   */
  protected int smoothInUnknownsThreshold;

  /**
   * Have tags changeable based on statistics on word types having various
   * taggings.
   */
  protected boolean smartMutation;

  protected final Index<String> wordIndex;

  protected final Index<String> tagIndex;


  /** An array of Lists of rules (IntTaggedWord), indexed by word. */
  public transient List<IntTaggedWord>[] rulesWithWord;

  // protected transient Set<IntTaggedWord> rules = new
  // HashSet<IntTaggedWord>();
  // When it existed, rules somehow held a few less things than rulesWithWord
  // I never figured out why [cdm, Dec 2004]

  /** Set of all tags as IntTaggedWord. Alive in both train and runtime
   *  phases, but transient.
   */
  protected transient Set<IntTaggedWord> tags = Generics.newHashSet();

  protected transient Set<IntTaggedWord> words = Generics.newHashSet();

  // protected transient Set<IntTaggedWord> sigs=Generics.newHashSet();

  /** Records the number of times word/tag pair was seen in training data.
   *  Includes word/tag pairs where one is a wildcard not a real word/tag.
   */
  public ClassicCounter<IntTaggedWord> seenCounter = new ClassicCounter<IntTaggedWord>();

  double[] smooth = { 1.0, 1.0 };

  // these next two are used for smartMutation calculation
  transient double[][] m_TT; // = null;

  transient double[] m_T; // = null;

  protected boolean flexiTag;

  protected boolean useSignatureForKnownSmoothing;

  /**
   * Only used when training, specifically when training on sentenes
   * that weren't part of annotated (eg markovized, etc) data
   */
  private Map<String, Counter<String>> baseTagCounts = Generics.newHashMap();

  public BaseLexicon(Index<String> wordIndex, Index<String> tagIndex) {
    this(new Options(), wordIndex, tagIndex);
  }

  public BaseLexicon(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;

    flexiTag = op.lexOptions.flexiTag;
    useSignatureForKnownSmoothing = op.lexOptions.useSignatureForKnownSmoothing;
    this.smoothInUnknownsThreshold = op.lexOptions.smoothInUnknownsThreshold;
    this.smartMutation = op.lexOptions.smartMutation;
    this.trainOptions = op.trainOptions;
    this.testOptions = op.testOptions;
    this.op = op;

    // Construct UnknownWordModel by reflection -- a right pain
    // Lexicons and UnknownWordModels aren't very well encapsulated
    // from each other!

    if (op.lexOptions.uwModelTrainer == null) {
      this.uwModelTrainerClass = "edu.stanford.nlp.parser.lexparser.BaseUnknownWordModelTrainer";
    } else {
      this.uwModelTrainerClass = op.lexOptions.uwModelTrainer;
    }
  }

  /**
   * Checks whether a word is in the lexicon. This version will compile the
   * lexicon into the rulesWithWord array, if that hasn't already happened
   *
   * @param word The word as an int index to an Index
   * @return Whether the word is in the lexicon
   */
  @Override
  public boolean isKnown(int word) {
    return (word < rulesWithWord.length && word >= 0 && !rulesWithWord[word].isEmpty());
  }

  /**
   * Checks whether a word is in the lexicon. This version works even while
   * compiling lexicon with current counters (rather than using the compiled
   * rulesWithWord array).
   *
   * TODO: The previous version would insert rules into the
   * wordNumberer.  Is that the desired behavior?  Why not test in
   * some way that doesn't affect the index?  For example, start by
   * testing wordIndex.contains(word).
   *
   * @param word The word as a String
   * @return Whether the word is in the lexicon
   */
  @Override
  public boolean isKnown(String word) {
    if (!wordIndex.contains(word))
      return false;
    IntTaggedWord iW = new IntTaggedWord(wordIndex.indexOf(word), nullTag);
    return seenCounter.getCount(iW) > 0.0;
  }

  /**
   * Returns the possible POS taggings for a word.
   *
   * @param word The word, represented as an integer in wordIndex
   * @param loc  The position of the word in the sentence (counting from 0).
   *          <i>Implementation note: The BaseLexicon class doesn't actually
   *          make use of this position information.</i>
   * @return An Iterator over a List ofIntTaggedWords, which pair the word with
   *         possible taggings as integer pairs. (Each can be thought of as a
   *         <code>tag -&gt; word<code> rule.)
   */
  public Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc) {
    return ruleIteratorByWord(wordIndex.indexOf(word, true), loc, null);
  }

  /** Generate the possible taggings for a word at a sentence position.
   *  This may either be based on a strict lexicon or an expanded generous
   *  set of possible taggings. <p>
   *  <i>Implementation note:</i> Expanded sets of possible taggings are
   *  calculated dynamically at runtime, so as to reduce the memory used by
   *  the lexicon (a space/time tradeoff).
   *
   *  @param word The word (as an int)
   * @param loc  Its index in the sentence (usually only relevant for unknown words)
   *  @return A list of possible taggings
   */
  @Override
  public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {
    // if (rulesWithWord == null) { // tested in isKnown already
    // initRulesWithWord();
    // }
    List<IntTaggedWord> wordTaggings;
    if (isKnown(word)) {
      if ( ! flexiTag) {
        // Strict lexical tagging for seen items
        wordTaggings = rulesWithWord[word];
      } else {
        /* Allow all tags with same basicCategory */
        /* Allow all scored taggings, unless very common */
        IntTaggedWord iW = new IntTaggedWord(word, nullTag);
        if (seenCounter.getCount(iW) > smoothInUnknownsThreshold) {
          return rulesWithWord[word].iterator();
        } else {
          // give it flexible tagging not just lexicon
          wordTaggings = new ArrayList<IntTaggedWord>(40);
          for (IntTaggedWord iTW2 : tags) {
            IntTaggedWord iTW = new IntTaggedWord(word, iTW2.tag);
            if (score(iTW, loc, wordIndex.get(word), null) > Float.NEGATIVE_INFINITY) {
              wordTaggings.add(iTW);
            }
          }
        }
      }
    } else {
      // we copy list so we can insert correct word in each item
      wordTaggings = new ArrayList<IntTaggedWord>(40);
      for (IntTaggedWord iTW : rulesWithWord[wordIndex.indexOf(UNKNOWN_WORD)]) {
        wordTaggings.add(new IntTaggedWord(word, iTW.tag));
      }
    }
    if (DEBUG_LEXICON) {
      EncodingPrintWriter.err.println("Lexicon: " + wordIndex.get(word) + " (" +
              (isKnown(word) ? "known": "unknown") + ", loc=" + loc + ", n=" +
              (isKnown(word) ? word: wordIndex.indexOf(UNKNOWN_WORD)) + ") " +
              (flexiTag ? "flexi": "lexicon") + " taggings: " + wordTaggings, "UTF-8");
    }
    return wordTaggings.iterator();
 }

  @Override
  public Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc, String featureSpec) {
    return ruleIteratorByWord(wordIndex.indexOf(word, true), loc, featureSpec);
  }

  protected void initRulesWithWord() {
    if (testOptions.verbose || DEBUG_LEXICON) {
      System.err.print("\nInitializing lexicon scores ... ");
    }
    // int numWords = words.size()+sigs.size()+1;
    int unkWord = wordIndex.indexOf(UNKNOWN_WORD, true);
    int numWords = wordIndex.size();
    rulesWithWord = new List[numWords];
    for (int w = 0; w < numWords; w++) {
      rulesWithWord[w] = new ArrayList<IntTaggedWord>(1); // most have 1 or 2
                                                          // items in them
    }
    // for (Iterator ruleI = rules.iterator(); ruleI.hasNext();) {
    tags = Generics.newHashSet();
    for (IntTaggedWord iTW : seenCounter.keySet()) {
      if (iTW.word() == nullWord && iTW.tag() != nullTag) {
        tags.add(iTW);
      }
    }

    // tags for unknown words
    if (DEBUG_LEXICON) {
      System.err.println("Lexicon initializing tags for UNKNOWN WORD (" +
                         Lexicon.UNKNOWN_WORD + ", " + unkWord + ')');
    }
    if (DEBUG_LEXICON) System.err.println("unSeenCounter is: " + uwModel.unSeenCounter());
    if (DEBUG_LEXICON) System.err.println("Train.openClassTypesThreshold is " + trainOptions.openClassTypesThreshold);
    for (IntTaggedWord iT : tags) {
      if (DEBUG_LEXICON) System.err.println("Entry for " + iT + " is " + uwModel.unSeenCounter().getCount(iT));
      double types = uwModel.unSeenCounter().getCount(iT);
      if (types > trainOptions.openClassTypesThreshold) {
        // Number of types before it's treated as open class
        IntTaggedWord iTW = new IntTaggedWord(unkWord, iT.tag);
        rulesWithWord[iTW.word].add(iTW);
      }
    }
    if (testOptions.verbose || DEBUG_LEXICON) {
      System.err.print("The " + rulesWithWord[unkWord].size() + " open class tags are: [");
      for (IntTaggedWord item : rulesWithWord[unkWord]) {
        System.err.print(" " + tagIndex.get(item.tag()));
        if (DEBUG_LEXICON) {
          IntTaggedWord iTprint = new IntTaggedWord(nullWord, item.tag);
          System.err.print(" (tag " + item.tag() + ", type count is " +
                           uwModel.unSeenCounter().getCount(iTprint) + ')');
        }
      }
      System.err.println(" ] ");
    }

    for (IntTaggedWord iTW : seenCounter.keySet()) {
      if (iTW.tag() != nullTag && iTW.word() != nullWord) {
        rulesWithWord[iTW.word].add(iTW);
      }
    }
  }


  protected List<IntTaggedWord> treeToEvents(Tree tree) {
    List<TaggedWord> taggedWords = tree.taggedYield();
    return listToEvents(taggedWords);
  }

  protected List<IntTaggedWord> listToEvents(List<TaggedWord> taggedWords) {
    List<IntTaggedWord> itwList = new ArrayList<IntTaggedWord>();
    for (TaggedWord tw : taggedWords) {
      IntTaggedWord iTW = new IntTaggedWord(tw.word(), tw.tag(), wordIndex, tagIndex);
      itwList.add(iTW);
    }
    return itwList;
  }

  /** Not yet implemented. */
  public void addAll(List<TaggedWord> tagWords) {
    addAll(tagWords, 1.0);
  }

  /** Not yet implemented. */
  public void addAll(List<TaggedWord> taggedWords, double weight) {
    List<IntTaggedWord> tagWords = listToEvents(taggedWords);
  }

  /** Not yet implemented. */
  public void trainWithExpansion(Collection<TaggedWord> taggedWords) {
  }

  @Override
  public void initializeTraining(double numTrees) {
    this.uwModelTrainer =
      ReflectionLoading.loadByReflection(uwModelTrainerClass);
    uwModelTrainer.initializeTraining(op, this, wordIndex, tagIndex,
                                      numTrees);
  }

  /**
   * Trains this lexicon on the Collection of trees.
   */
  @Override
  public void train(Collection<Tree> trees) {
    train(trees, 1.0);
  }

  /**
   * Trains this lexicon on the Collection of trees.
   * Also trains the unknown word model pointed to by this lexicon.
   */
  @Override
  public void train(Collection<Tree> trees, double weight) {
    // scan data
    for (Tree tree : trees) {
      train(tree, weight);
    }
  }

  @Override
  public void train(Tree tree, double weight) {
    train(tree.taggedYield(), weight);
  }

  @Override
  public final void train(List<TaggedWord> sentence, double weight) {
    uwModelTrainer.incrementTreesRead(weight);
    int loc = 0;
    for (TaggedWord tw : sentence) {
      train(tw, loc, weight);
      ++loc;
    }
  }

  @Override
  public final void incrementTreesRead(double weight) {
    uwModelTrainer.incrementTreesRead(weight);
  }

  @Override
  public final void trainUnannotated(List<TaggedWord> sentence,
                                     double weight) {
    uwModelTrainer.incrementTreesRead(weight);
    int loc = 0;
    for (TaggedWord tw : sentence) {
      String baseTag = op.langpack().basicCategory(tw.tag());
      Counter<String> counts = baseTagCounts.get(baseTag);
      if (counts == null) {
        ++loc;
        continue;
      }
      double totalCount = counts.totalCount();
      if (totalCount == 0) {
        ++loc;
        continue;
      }
      for (String tag : counts.keySet()) {
        TaggedWord newTW = new TaggedWord(tw.word(), tag);
        train(newTW, loc, weight * counts.getCount(tag) / totalCount);
      }
      ++loc;
    }
  }

  @Override
  public void train(TaggedWord tw, int loc, double weight) {
    uwModelTrainer.train(tw, loc, weight);

    IntTaggedWord iTW =
      new IntTaggedWord(tw.word(), tw.tag(), wordIndex, tagIndex);
    seenCounter.incrementCount(iTW, weight);
    IntTaggedWord iT = new IntTaggedWord(nullWord, iTW.tag);
    seenCounter.incrementCount(iT, weight);
    IntTaggedWord iW = new IntTaggedWord(iTW.word, nullTag);
    seenCounter.incrementCount(iW, weight);
    IntTaggedWord i = new IntTaggedWord(nullWord, nullTag);
    seenCounter.incrementCount(i, weight);
    // rules.add(iTW);
    tags.add(iT);
    words.add(iW);

    String tag = tw.tag();
    String baseTag = op.langpack().basicCategory(tag);

    Counter<String> counts = baseTagCounts.get(baseTag);
    if (counts == null) {
      counts = new ClassicCounter<String>();
      baseTagCounts.put(baseTag, counts);
    }
    counts.incrementCount(tag, weight);
  }

  @Override
  public void finishTraining() {
    uwModel = uwModelTrainer.finishTraining();

    tune();

    // index the possible tags for each word
    initRulesWithWord();

    if (DEBUG_LEXICON) {
      printLexStats();
    }
  }

  /**
   * Adds the tagging with count to the data structures in this Lexicon.
   */
  protected void addTagging(boolean seen, IntTaggedWord itw, double count) {
    if (seen) {
      seenCounter.incrementCount(itw, count);
      if (itw.tag() == nullTag) {
        words.add(itw);
      } else if (itw.word() == nullWord) {
        tags.add(itw);
      } else {
        // rules.add(itw);
      }
    } else {
      uwModel.addTagging(seen, itw, count);
      // if (itw.tag() == nullTag) {
      // sigs.add(itw);
      // }
    }
  }



  /**
   * This records how likely it is for a word with one tag to also have another
   * tag. This won't work after serialization/deserialization, but that is how
   * it is currently called....
   */
  void buildPT_T() {
    int numTags = tagIndex.size();
    m_TT = new double[numTags][numTags];
    m_T = new double[numTags];
    double[] tmp = new double[numTags];
    for (IntTaggedWord word : words) {
      double tot = 0.0;
      for (int t = 0; t < numTags; t++) {
        IntTaggedWord iTW = new IntTaggedWord(word.word, t);
        tmp[t] = seenCounter.getCount(iTW);
        tot += tmp[t];
      }
      if (tot < 10) {
        continue;
      }
      for (int t = 0; t < numTags; t++) {
        for (int t2 = 0; t2 < numTags; t2++) {
          if (tmp[t2] > 0.0) {
            double c = tmp[t] / tot;
            m_T[t] += c;
            m_TT[t2][t] += c;
          }
        }
      }
    }
  }


  /**
   * Get the score of this word with this tag (as an IntTaggedWord) at this
   * location. (Presumably an estimate of P(word | tag).)
   * <p>
   * <i>Implementation documentation:</i>
   * Seen:
   * c_W = count(W)      c_TW = count(T,W)
   * c_T = count(T)      c_Tunseen = count(T) among new words in 2nd half
   * total = count(seen words)   totalUnseen = count("unseen" words)
   * p_T_U = Pmle(T|"unseen")
   * pb_T_W = P(T|W). If (c_W &gt; smoothInUnknownsThreshold) = c_TW/c_W
   * Else (if not smart mutation) pb_T_W = bayes prior smooth[1] with p_T_U
   * p_T= Pmle(T)          p_W = Pmle(W)
   * pb_W_T = log(pb_T_W * p_W / p_T) [Bayes rule]
   * Note that this doesn't really properly reserve mass to unknowns.
   *
   * Unseen:
   * c_TS = count(T,Sig|Unseen)      c_S = count(Sig)   c_T = count(T|Unseen)
   * c_U = totalUnseen above
   * p_T_U = Pmle(T|Unseen)
   * pb_T_S = Bayes smooth of Pmle(T|S) with P(T|Unseen) [smooth[0]]
   * pb_W_T = log(P(W|T)) inverted
   *
   * @param iTW An IntTaggedWord pairing a word and POS tag
   * @param loc The position in the sentence. <i>In the default implementation
   *          this is used only for unknown words to change their probability
   *          distribution when sentence initial</i>
   * @return A float score, usually, log P(word|tag)
   */
  @Override
  public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
    // both actual
    double c_TW = seenCounter.getCount(iTW);
    // double x_TW = xferCounter.getCount(iTW);

    IntTaggedWord temp = new IntTaggedWord(iTW.word, nullTag);
    // word counts
    double c_W = seenCounter.getCount(temp);
    // double x_W = xferCounter.getCount(temp);

    // totals
    double total = seenCounter.getCount(NULL_ITW);
    double totalUnseen = uwModel.unSeenCounter().getCount(NULL_ITW);

    temp = new IntTaggedWord(nullWord, iTW.tag);
    // tag counts
    double c_T = seenCounter.getCount(temp);
    double c_Tunseen = uwModel.unSeenCounter().getCount(temp);

    double pb_W_T; // always set below

    if (DEBUG_LEXICON) {
      // dump info about last word
      if (iTW.word != debugLastWord) {
        if (debugLastWord >= 0 && debugPrefix != null) {
          // the 2nd conjunct in test above handles older serialized files
          EncodingPrintWriter.err.println(debugPrefix + debugProbs + debugNoProbs, "UTF-8");
        }
      }
    }

    boolean seen = (c_W > 0.0);

    if (seen) {

      // known word model for P(T|W)
      if (DEBUG_LEXICON_SCORE) {
        System.err.println("Lexicon.score " + wordIndex.get(iTW.word) + "/" + tagIndex.get(iTW.tag) + " as known word.");
      }

      // c_TW = Math.sqrt(c_TW); [cdm: funny math scaling? dunno who played with this]
      // c_TW += 0.5;

      double p_T_U;
      if (useSignatureForKnownSmoothing) { // only works for English currently
        p_T_U = getUnknownWordModel().scoreProbTagGivenWordSignature(iTW, loc, smooth[0], word);
        if (DEBUG_LEXICON_SCORE) System.err.println("With useSignatureForKnownSmoothing, P(T|U) is " + p_T_U + " rather than " + (c_Tunseen / totalUnseen));
      } else {
        p_T_U = c_Tunseen / totalUnseen;
      }
      double pb_T_W; // always set below

      if (DEBUG_LEXICON_SCORE) {
        System.err.println("c_W is " + c_W  + " mle = " + (c_TW/c_W)+ " smoothInUnknownsThresh is " +
                smoothInUnknownsThreshold + " base p_T_U is " + c_Tunseen + "/" + totalUnseen + " = " + p_T_U);
      }
      if (c_W > smoothInUnknownsThreshold && c_TW > 0.0 && c_W > 0.0) {
        // we've seen the word enough times to have confidence in its tagging
        pb_T_W = c_TW / c_W;
      } else {
        // we haven't seen the word enough times to have confidence in its
        // tagging
        if (smartMutation) {
          int numTags = tagIndex.size();
          if (m_TT == null || numTags != m_T.length) {
            buildPT_T();
          }
          p_T_U *= 0.1;
          // System.out.println("Checking "+iTW);
          for (int t = 0; t < numTags; t++) {
            IntTaggedWord iTW2 = new IntTaggedWord(iTW.word, t);
            double p_T_W2 = seenCounter.getCount(iTW2) / c_W;
            if (p_T_W2 > 0) {
              // System.out.println(" Observation of "+tagIndex.get(t)+"
              // ("+seenCounter.getCount(iTW2)+") mutated to
              // "+tagIndex.get(iTW.tag)+" at rate
              // "+(m_TT[tag][t]/m_T[t]));
              p_T_U += p_T_W2 * m_TT[iTW.tag][t] / m_T[t] * 0.9;
            }
          }
        }
        if (DEBUG_LEXICON_SCORE) {
          System.err.println("c_TW = " + c_TW + " c_W = " + c_W +
                             " p_T_U = " + p_T_U);
        }
        // double pb_T_W = (c_TW+smooth[1]*x_TW)/(c_W+smooth[1]*x_W);
        pb_T_W = (c_TW + smooth[1] * p_T_U) / (c_W + smooth[1]);
      }
      double p_T = (c_T / total);
      double p_W = (c_W / total);
      pb_W_T = Math.log(pb_T_W * p_W / p_T);

      if (DEBUG_LEXICON) {
        if (iTW.word != debugLastWord) {
          debugLastWord = iTW.word;
          debugLoc = loc;
          debugProbs = new StringBuilder();
          debugNoProbs = new StringBuilder("impossible: ");
          debugPrefix = "Lexicon: " + wordIndex.get(debugLastWord) + " (known): ";
        }
        if (pb_W_T > Double.NEGATIVE_INFINITY) {
          NumberFormat nf = NumberFormat.getNumberInstance();
          nf.setMaximumFractionDigits(3);
          debugProbs.append(tagIndex.get(iTW.tag) + ": cTW=" + c_TW + " c_T=" + c_T
                  + " pb_T_W=" + nf.format(pb_T_W) + " log pb_W_T=" + nf.format(pb_W_T)
                  + ", ");
          // debugProbs.append("\n" + "smartMutation=" + smartMutation + "
          // smoothInUnknownsThreshold=" + smoothInUnknownsThreshold + "
          // smooth0=" + smooth[0] + "smooth1=" + smooth[1] + " p_T_U=" + p_T_U
          // + " c_W=" + c_W);
        } else {
          debugNoProbs.append(tagIndex.get(iTW.tag)).append(' ');
        }
      } // end if (DEBUG_LEXICON)

    } else { // when unseen
      if (loc >= 0) {
        pb_W_T = getUnknownWordModel().score(iTW, loc, c_T, total, smooth[0], word);
      } else {
        // For negative we now do a weighted average for the dependency grammar :-)
        double pb_W0_T = getUnknownWordModel().score(iTW, 0, c_T, total, smooth[0], word);
        double pb_W1_T = getUnknownWordModel().score(iTW, 1, c_T, total, smooth[0], word);
        pb_W_T = Math.log((Math.exp(pb_W0_T) + 2 * Math.exp(pb_W1_T))/3);
      }
    }

    String tag = tagIndex.get(iTW.tag());

    // Categorical cutoff if score is too low
    if (pb_W_T > -100.0) {
      return (float) pb_W_T;
    }
    return Float.NEGATIVE_INFINITY;
  } // end score()


  private transient int debugLastWord = -1;

  private transient int debugLoc = -1;

  private transient StringBuilder debugProbs;

  private transient StringBuilder debugNoProbs;

  private transient String debugPrefix;

  /**
   * TODO: this used to actually score things based on the original trees
   */
  public final void tune() {
    double bestScore = Double.NEGATIVE_INFINITY;
    double[] bestSmooth = { 0.0, 0.0 };
    for (smooth[0] = 1; smooth[0] <= 1; smooth[0] *= 2.0) {// 64
      for (smooth[1] = 0.2; smooth[1] <= 0.2; smooth[1] *= 2.0) {// 3
        // for (smooth[0]=0.5; smooth[0]<=64; smooth[0] *= 2.0) {//64
        // for (smooth[1]=0.1; smooth[1]<=12.8; smooth[1] *= 2.0) {//3
        double score = 0.0;
        // score = scoreAll(trees);
        if (testOptions.verbose) {
          System.err.println("Tuning lexicon: s0 " + smooth[0] +
                             " s1 " + smooth[1] + " is " + score);
        }
        if (score > bestScore) {
          System.arraycopy(smooth, 0, bestSmooth, 0, smooth.length);
          bestScore = score;
        }
      }
    }
    System.arraycopy(bestSmooth, 0, smooth, 0, bestSmooth.length);
    if (smartMutation) {
      smooth[0] = 8.0;
      // smooth[1] = 1.6;
      // smooth[0] = 0.5;
      smooth[1] = 0.1;
    }
    if (testOptions.unseenSmooth > 0.0) {
      smooth[0] = testOptions.unseenSmooth;
    }
    if (testOptions.verbose) {
      System.err.println("Tuning selected smoothUnseen " + smooth[0] + " smoothSeen " + smooth[1]
                         + " at " + bestScore);
    }
  }

  private void readObject(ObjectInputStream ois)
    throws IOException, ClassNotFoundException
  {
    ois.defaultReadObject();
    // Reinitialize the transient objects.  This must be done here
    // rather than lazily so that there is no race condition to
    // reinitialize them later.
    initRulesWithWord();
  }

  /**
   * Populates data in this Lexicon from the character stream given by the
   * Reader r.
   * TODO: this doesn't appear to correctly read in the
   * UnknownWordModel in the case of a model more complicated than the
   * unSeenCounter
   */
  @Override
  public void readData(BufferedReader in) throws IOException {
    final String SEEN = "SEEN";
    String line;
    int lineNum = 1;
    // all lines have one tagging with raw count per line
    line = in.readLine();
    Pattern p = Pattern.compile("^smooth\\[([0-9])\\] = (.*)$");
    while (line != null && line.length() > 0) {
      try {
        Matcher m = p.matcher(line);
        if (m.matches()) {
          int i = Integer.parseInt(m.group(1));
          smooth[i] = Double.parseDouble(m.group(2));
        } else {
          // split on spaces, quote with doublequote, and escape with backslash
          String[] fields = StringUtils.splitOnCharWithQuoting(line, ' ', '\"', '\\');
          // System.out.println("fields:\n" + fields[0] + "\n" + fields[1] +
          // "\n" + fields[2] + "\n" + fields[3] + "\n" + fields[4]);
          boolean seen = fields[3].equals(SEEN);
          addTagging(seen, new IntTaggedWord(fields[2], fields[0], wordIndex, tagIndex), Double.parseDouble(fields[4]));
        }
      } catch (RuntimeException e) {
        throw new IOException("Error on line " + lineNum + ": " + line, e);
      }
      lineNum++;
      line = in.readLine();
    }
    initRulesWithWord();
  }

  /**
   * Writes out data from this Object to the Writer w. Rules are separated by
   * newline, and rule elements are delimited by \t.
   */
  @Override
  public void writeData(Writer w) throws IOException {
    PrintWriter out = new PrintWriter(w);

    for (IntTaggedWord itw : seenCounter.keySet()) {
      out.println(itw.toLexicalEntry(wordIndex, tagIndex) + " SEEN " + seenCounter.getCount(itw));
    }
    for (IntTaggedWord itw : getUnknownWordModel().unSeenCounter().keySet()) {
      out.println(itw.toLexicalEntry(wordIndex, tagIndex) + " UNSEEN " + getUnknownWordModel().unSeenCounter().getCount(itw));
    }
    for (int i = 0; i < smooth.length; i++) {
      out.println("smooth[" + i + "] = " + smooth[i]);
    }
    out.flush();
  }

  /** Returns the number of rules (tag rewrites as word) in the Lexicon.
   *  This method assumes that the lexicon has been initialized.
   */
  @Override
  public int numRules() {
    int accumulated = 0;
    for (List<IntTaggedWord> lis : rulesWithWord) {
      accumulated += lis.size();
    }
    return accumulated;
  }


  private static final int STATS_BINS = 15;


  protected static void examineIntersection(Set<String> s1, Set<String> s2) {
    Set<String> knownTypes = Generics.newHashSet(s1);
    knownTypes.retainAll(s2);
    if (knownTypes.size() != 0) {
      System.err.printf("|intersect|: %d%n", knownTypes.size());
      for (String word : knownTypes) {
        System.err.print(word + " ");
      }
      System.err.println();
    }
  }

  /** Print some statistics about this lexicon. */
  public void printLexStats() {
    System.out.println("BaseLexicon statistics");
    System.out.println("unknownLevel is " + getUnknownWordModel().getUnknownLevel());
    // System.out.println("Rules size: " + rules.size());
    System.out.println("Sum of rulesWithWord: " + numRules());
    System.out.println("Tags size: " + tags.size());
    int wsize = words.size();
    System.out.println("Words size: " + wsize);
    // System.out.println("Unseen Sigs size: " + sigs.size() +
    // " [number of unknown equivalence classes]");
    System.out.println("rulesWithWord length: " + rulesWithWord.length
                       + " [should be sum of words + unknown sigs]");
    int[] lengths = new int[STATS_BINS];
    ArrayList<String>[] wArr = new ArrayList[STATS_BINS];
    for (int j = 0; j < STATS_BINS; j++) {
      wArr[j] = new ArrayList<String>();
    }
    for (int i = 0; i < rulesWithWord.length; i++) {
      int num = rulesWithWord[i].size();
      if (num > STATS_BINS - 1) {
        num = STATS_BINS - 1;
      }
      lengths[num]++;
      if (wsize <= 20 || num >= STATS_BINS / 2) {
        wArr[num].add(wordIndex.get(i));
      }
    }
    System.out.println("Stats on how many taggings for how many words");
    for (int j = 0; j < STATS_BINS; j++) {
      System.out.print(j + " taggings: " + lengths[j] + " words ");
      if (wsize <= 20 || j >= STATS_BINS / 2) {
        System.out.print(wArr[j]);
      }
      System.out.println();
    }
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(0);
    System.out.println("Unseen counter: " + Counters.toString(uwModel.unSeenCounter(), nf));

    if (wsize < 50 && tags.size() < 10) {
      nf.setMaximumFractionDigits(3);
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println("Tagging probabilities log P(word|tag)");
      for (int t = 0; t < tags.size(); t++) {
        pw.print('\t');
        pw.print(tagIndex.get(t));
      }
      pw.println();
      for (int w = 0; w < wsize; w++) {
        pw.print(wordIndex.get(w));
        pw.print('\t');
        for (int t = 0; t < tags.size(); t++) {
          IntTaggedWord iTW = new IntTaggedWord(w, t);
          pw.print(nf.format(score(iTW, 1, wordIndex.get(w), null)));
          if (t == tags.size() -1) {
            pw.println();
          } else
            pw.print('\t');
        }
      }
      pw.close();
      System.out.println(sw.toString());
    }
  }

  /**
   * Evaluates how many words (= terminals) in a collection of trees are
   * covered by the lexicon. First arg is the collection of trees; second
   * through fourth args get the results. Currently unused; this probably
   * only works if train and test at same time so tags and words variables
   * are initialized.
   */
  public double evaluateCoverage(Collection<Tree> trees, Set<String> missingWords,
                                 Set<String> missingTags, Set<IntTaggedWord> missingTW) {

    List<IntTaggedWord> iTW1 = new ArrayList<IntTaggedWord>();
    for (Tree t : trees) {
      iTW1.addAll(treeToEvents(t));
    }

    int total = 0;
    int unseen = 0;

    for (IntTaggedWord itw : iTW1) {
      total++;
      if (!words.contains(new IntTaggedWord(itw.word(), nullTag))) {
        missingWords.add(wordIndex.get(itw.word()));
      }
      if (!tags.contains(new IntTaggedWord(nullWord, itw.tag()))) {
        missingTags.add(tagIndex.get(itw.tag()));
      }
      // if (!rules.contains(itw)) {
      if (seenCounter.getCount(itw) == 0.0) {
        unseen++;
        missingTW.add(itw);
      }
    }
    return (double) unseen / total;
  }

  int[] tagsToBaseTags = null;

  public int getBaseTag(int tag, TreebankLanguagePack tlp) {
    if (tagsToBaseTags == null) {
      populateTagsToBaseTags(tlp);
    }
    return tagsToBaseTags[tag];
  }

  private void populateTagsToBaseTags(TreebankLanguagePack tlp) {
    int total = tagIndex.size();
    tagsToBaseTags = new int[total];
    for (int i = 0; i < total; i++) {
      String tag = tagIndex.get(i);
      String baseTag = tlp.basicCategory(tag);
      int j = tagIndex.indexOf(baseTag, true);
      tagsToBaseTags[i] = j;
    }
  }

  /** Provides some testing and opportunities for exploration of the
   *  probabilities of a BaseLexicon.  What's here currently probably
   *  only works for the English Penn Treeebank, as it uses default
   *  constructors.  Of the words given to test on,
   *  the first is treated as sentence initial, and the rest as not
   *  sentence initial.
   *
   *  @param args The command line arguments:
   *     java BaseLexicon treebankPath fileRange unknownWordModel words*
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("java BaseLexicon treebankPath fileRange unknownWordModel words*");
      return;
    }
    System.out.print("Training BaseLexicon from " + args[0] + ' ' + args[1] + " ... ");
    Treebank tb = new DiskTreebank();
    tb.loadPath(args[0], new NumberRangesFileFilter(args[1], true));
    // TODO: change this interface so the lexicon creates its own indices?
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    Options op = new Options();
    op.lexOptions.useUnknownWordSignatures = Integer.parseInt(args[2]);
    BaseLexicon lex = new BaseLexicon(op, wordIndex, tagIndex);
    lex.initializeTraining(tb.size());
    lex.train(tb);
    lex.finishTraining();
    System.out.println("done.");
    System.out.println();
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(4);
    List<String> impos = new ArrayList<String>();
    for (int i = 3; i < args.length; i++) {
      if (lex.isKnown(args[i])) {
        System.out.println(args[i] + " is a known word.  Log probabilities [log P(w|t)] for its taggings are:");
        for (Iterator<IntTaggedWord> it = lex.ruleIteratorByWord(wordIndex.indexOf(args[i], true), i - 3, null); it.hasNext(); ) {
          IntTaggedWord iTW = it.next();
          System.out.println(StringUtils.pad(iTW, 24) + nf.format(lex.score(iTW, i - 3, wordIndex.get(iTW.word), null)));
        }
      } else {
        String sig = lex.getUnknownWordModel().getSignature(args[i], i-3);
        System.out.println(args[i] + " is an unknown word.  Signature with uwm " + lex.getUnknownWordModel().getUnknownLevel() + ((i == 3) ? " init": "non-init") + " is: " + sig);
        impos.clear();
        List<String> lis = new ArrayList<String>(tagIndex.objectsList());
        Collections.sort(lis);
        for (String tStr : lis) {
          IntTaggedWord iTW = new IntTaggedWord(args[i], tStr, wordIndex, tagIndex);
          double score = lex.score(iTW, 1, args[i], null);
          if (score == Float.NEGATIVE_INFINITY) {
            impos.add(tStr);
          } else {
            System.out.println(StringUtils.pad(iTW, 24) + nf.format(score));
          }
        }
        if (impos.size() > 0) {
          System.out.println(args[i] + " impossible tags: " + impos);
        }
      }
      System.out.println();
    }
  }

  @Override
  public UnknownWordModel getUnknownWordModel() {
    return uwModel;
  }

  @Override
  public final void setUnknownWordModel(UnknownWordModel uwm) {
    this.uwModel = uwm;
  }

  // TODO(spenceg): Debug method for getting a treebank with CoreLabels. This is for training
  // the FactoredLexicon.
  @Override
  public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
    train(trees);
  }

  private static final long serialVersionUID = 40L;
}

