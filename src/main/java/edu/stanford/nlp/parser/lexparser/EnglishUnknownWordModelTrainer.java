package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;


public class EnglishUnknownWordModelTrainer
  extends AbstractUnknownWordModelTrainer
{

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(EnglishUnknownWordModelTrainer.class);

  private static final boolean DOCUMENT_UNKNOWNS = false;

  // Records the number of times word/tag pair was seen in training data.
  ClassicCounter<IntTaggedWord> seenCounter;

  ClassicCounter<IntTaggedWord> unSeenCounter;

  double indexToStartUnkCounting;

  UnknownWordModel model;

  @Override
  public void initializeTraining(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex, double totalTrees) {
    super.initializeTraining(op, lex, wordIndex, tagIndex, totalTrees);

    this.indexToStartUnkCounting = (totalTrees * op.trainOptions.fractionBeforeUnseenCounting);

    seenCounter = new ClassicCounter<>();
    unSeenCounter = new ClassicCounter<>();

    model = new EnglishUnknownWordModel(op, lex, wordIndex, tagIndex,
                                        unSeenCounter);

    // scan data
    if (DOCUMENT_UNKNOWNS) {
      log.info("Collecting " + Lexicon.UNKNOWN_WORD +
                         " from trees " + (indexToStartUnkCounting + 1) +
                         " to " + totalTrees);
    }
  }

  /**
   * Trains this UWM on the tagged word tw at position loc with weighting weight.
   */
  public void train(TaggedWord tw, int loc, double weight) {
    IntTaggedWord iTW =
      new IntTaggedWord(tw.word(), tw.tag(), wordIndex, tagIndex);
    IntTaggedWord iT = new IntTaggedWord(nullWord, iTW.tag);
    IntTaggedWord iW = new IntTaggedWord(iTW.word, nullTag);
    seenCounter.incrementCount(iW, weight);
    IntTaggedWord i = NULL_ITW;

    if (treesRead > indexToStartUnkCounting) {
      // start doing this once some way through trees;
      // treesRead is 1 based counting
      if (seenCounter.getCount(iW) < 1.5) {
        // it's an entirely unknown word
        int s = model.getSignatureIndex(iTW.word, loc,
                                        wordIndex.get(iTW.word));
        if (DOCUMENT_UNKNOWNS) {
          String wStr = wordIndex.get(iTW.word);
          String tStr = tagIndex.get(iTW.tag);
          String sStr = wordIndex.get(s);
          EncodingPrintWriter.err.println("Unknown word/tag/sig:\t" +
                                          wStr + '\t' + tStr + '\t' +
                                          sStr, "UTF-8");
        }
        IntTaggedWord iTS = new IntTaggedWord(s, iTW.tag);
        IntTaggedWord iS = new IntTaggedWord(s, nullTag);
        unSeenCounter.incrementCount(iTS, weight);
        unSeenCounter.incrementCount(iT, weight);
        unSeenCounter.incrementCount(iS, weight);
        unSeenCounter.incrementCount(i, weight);
        // rules.add(iTS);
        // sigs.add(iS);
      } // else {
        // if (seenCounter.getCount(iTW) < 2) {
        // it's a new tag for a known word
        // do nothing for now
        // }
        // }
    }
  }


  public UnknownWordModel finishTraining() {
    // make sure the unseen counter isn't empty!  If it is, put in
    // a uniform unseen over tags
    if (unSeenCounter.isEmpty()) {
      int numTags = tagIndex.size();
      for (int tt = 0; tt < numTags; tt++) {
        if ( ! Lexicon.BOUNDARY_TAG.equals(tagIndex.get(tt))) {
          IntTaggedWord iT = new IntTaggedWord(nullWord, tt);
          IntTaggedWord i = NULL_ITW;
          unSeenCounter.incrementCount(iT);
          unSeenCounter.incrementCount(i);
        }
      }
    }

    // index the possible tags for each word
    // numWords = wordIndex.size();
    // unknownWordIndex = wordIndex.indexOf(Lexicon.UNKNOWN_WORD, true);
    // initRulesWithWord();

    return model;
  }



}

