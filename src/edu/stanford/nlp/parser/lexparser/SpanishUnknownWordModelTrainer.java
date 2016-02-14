package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;

@SuppressWarnings("unused")
public class SpanishUnknownWordModelTrainer
  extends AbstractUnknownWordModelTrainer {

  private ClassicCounter<IntTaggedWord> seenCounter;

  private ClassicCounter<IntTaggedWord> unSeenCounter;

  private double indexToStartUnkCounting;

  // boundary tag -- assumed not a real tag
  private static final String BOUNDARY_TAG = ".$$.";

  private UnknownWordModel model;


  @Override
  public void initializeTraining(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex, double totalTrees) {
    super.initializeTraining(op, lex, wordIndex, tagIndex, totalTrees);
    indexToStartUnkCounting = (totalTrees * op.trainOptions.fractionBeforeUnseenCounting);

    seenCounter = new ClassicCounter<>();
    unSeenCounter = new ClassicCounter<>();

    model = new SpanishUnknownWordModel(op, lex, wordIndex, tagIndex,
                                        unSeenCounter);
  }

  /**
   * Trains this lexicon on the Collection of trees.
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
      if (seenCounter.getCount(iW) < 2) {
        // it's an entirely unknown word
        int s = model.getSignatureIndex(iTW.word, loc,
                                        wordIndex.get(iTW.word));
        IntTaggedWord iTS = new IntTaggedWord(s, iTW.tag);
        IntTaggedWord iS = new IntTaggedWord(s, nullTag);
        unSeenCounter.incrementCount(iTS, weight);
        unSeenCounter.incrementCount(iT, weight);
        unSeenCounter.incrementCount(iS, weight);
        unSeenCounter.incrementCount(i, weight);
      }
    }
  }

  public UnknownWordModel finishTraining() {
    // make sure the unseen counter isn't empty!  If it is, put in
    // a uniform unseen over tags
    if (unSeenCounter.isEmpty()) {
      System.err.printf("%s: WARNING: Unseen word counter is empty!",
                        this.getClass().getName());
      int numTags = tagIndex.size();
      for (int tt = 0; tt < numTags; tt++) {
        if ( ! BOUNDARY_TAG.equals(tagIndex.get(tt))) {
          IntTaggedWord iT = new IntTaggedWord(nullWord, tt);
          IntTaggedWord i = NULL_ITW;
          unSeenCounter.incrementCount(iT);
          unSeenCounter.incrementCount(i);
        }
      }
    }

    return model;
  }


}

