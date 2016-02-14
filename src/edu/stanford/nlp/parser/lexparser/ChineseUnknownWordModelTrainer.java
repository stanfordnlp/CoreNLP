package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

public class ChineseUnknownWordModelTrainer
  extends AbstractUnknownWordModelTrainer
{

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseUnknownWordModelTrainer.class);

  // Records the number of times word/tag pair was seen in training data.
  private ClassicCounter<IntTaggedWord> seenCounter;
  private ClassicCounter<IntTaggedWord> unSeenCounter;

  // c has a map from tags as Label to a Counter from word
  // signatures to Strings; it is used to collect counts that will
  // initialize the probabilities in tagHash
  private Map<Label,ClassicCounter<String>> c;

  // tc record the marginal counts for each tag as an unknown.  It
  // should be the same as c's totalCount ??
  private ClassicCounter<Label> tc;

  private boolean useFirst, useGT, useUnicodeType;

  private Map<Label, ClassicCounter<String>> tagHash;

  private Set<String> seenFirst;

  private double indexToStartUnkCounting;

  private UnknownGTTrainer unknownGTTrainer;

  private IntTaggedWord iTotal = new IntTaggedWord(nullWord, nullTag);

  private UnknownWordModel model;


  @Override
  public void initializeTraining(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex, double totalTrees) {
    super.initializeTraining(op, lex, wordIndex, tagIndex, totalTrees);

    boolean useGoodTuringUnknownWordModel = ChineseTreebankParserParams.DEFAULT_USE_GOOD_TURNING_UNKNOWN_WORD_MODEL;
    useFirst = true;
    useGT = (op.lexOptions.useUnknownWordSignatures == 0);
    if (lex instanceof ChineseLexicon) {
      useGoodTuringUnknownWordModel = ((ChineseLexicon) lex).useGoodTuringUnknownWordModel;
    } else if (op.tlpParams instanceof ChineseTreebankParserParams) {
      useGoodTuringUnknownWordModel = ((ChineseTreebankParserParams) op.tlpParams).useGoodTuringUnknownWordModel;
    }
    if (useGoodTuringUnknownWordModel) {
      this.useGT = true;
      this.useFirst = false;
    }

    this.useUnicodeType = op.lexOptions.useUnicodeType;

    if (useFirst) {
      log.info("ChineseUWM: treating unknown word as the average of their equivalents by first-character identity. useUnicodeType: " + useUnicodeType);
    }
    if (useGT) {
      log.info("ChineseUWM: using Good-Turing smoothing for unknown words.");
    }

    this.c = Generics.newHashMap();
    this.tc = new ClassicCounter<>();
    this.unSeenCounter = new ClassicCounter<>();
    this.seenCounter = new ClassicCounter<>();
    this.seenFirst = Generics.newHashSet();
    this.tagHash = Generics.newHashMap();

    this.indexToStartUnkCounting = (totalTrees * op.trainOptions.fractionBeforeUnseenCounting);

    this.unknownGTTrainer = (useGT) ? new UnknownGTTrainer() : null;

    Map<String,Float> unknownGT = null;
    if (useGT) {
      unknownGT = unknownGTTrainer.unknownGT;
    }
    this.model = new ChineseUnknownWordModel(op, lex, wordIndex, tagIndex,
                                             unSeenCounter, tagHash,
                                             unknownGT, useGT, seenFirst);
  }

  /**
   * Trains the first-character based unknown word model.
   *
   * @param tw The word we are currently training on
   * @param loc The position of that word
   * @param weight The weight to give this word in terms of training
   */
  @Override
  public void train(TaggedWord tw, int loc, double weight) {
    if (useGT) {
      unknownGTTrainer.train(tw, weight);
    }

    String word = tw.word();
    Label tagL = new Tag(tw.tag());
    String first = word.substring(0, 1);
    if (useUnicodeType) {
      char ch = word.charAt(0);
      int type = Character.getType(ch);
      if (type != Character.OTHER_LETTER) {
        // standard Chinese characters are of type "OTHER_LETTER"!!
        first = Integer.toString(type);
      }
    }
    String tag = tw.tag();

    if ( ! c.containsKey(tagL)) {
      c.put(tagL, new ClassicCounter<>());
    }
    c.get(tagL).incrementCount(first, weight);

    tc.incrementCount(tagL, weight);

    seenFirst.add(first);

    IntTaggedWord iW = new IntTaggedWord(word, IntTaggedWord.ANY, wordIndex, tagIndex);
    seenCounter.incrementCount(iW, weight);
    if (treesRead > indexToStartUnkCounting) {
      // start doing this once some way through trees;
      // treesRead is 1 based counting
      if (seenCounter.getCount(iW) < 2) {
        IntTaggedWord iT = new IntTaggedWord(IntTaggedWord.ANY, tag, wordIndex, tagIndex);
        unSeenCounter.incrementCount(iT, weight);
        unSeenCounter.incrementCount(iTotal, weight);
      }
    }
  }

  @Override
  public UnknownWordModel finishTraining() {
    // Map<String,Float> unknownGT = null;
    if (useGT) {
      unknownGTTrainer.finishTraining();
      // unknownGT = unknownGTTrainer.unknownGT;
    }

    for (Label tagLab : c.keySet()) {
      // outer iteration is over tags as Labels
      ClassicCounter<String> wc = c.get(tagLab); // counts for words given a tag

      if ( ! tagHash.containsKey(tagLab)) {
        tagHash.put(tagLab, new ClassicCounter<>());
      }

      // the UNKNOWN first character is assumed to be seen once in
      // each tag
      // this is really sort of broken!  (why??)
      tc.incrementCount(tagLab);
      wc.setCount(unknown, 1.0);

      // inner iteration is over words  as strings
      for (String first : wc.keySet()) {
        double prob = Math.log(((wc.getCount(first))) / tc.getCount(tagLab));
        tagHash.get(tagLab).setCount(first, prob);
        //if (Test.verbose)
        //EncodingPrintWriter.out.println(tag + " rewrites as " + first + " first char with probability " + prob,encoding);
      }
    }

    return model;
  }

}

