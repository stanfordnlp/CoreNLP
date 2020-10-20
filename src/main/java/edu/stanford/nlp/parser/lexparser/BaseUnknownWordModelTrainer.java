package edu.stanford.nlp.parser.lexparser; 
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.logging.Redwood;

public class BaseUnknownWordModelTrainer
  extends AbstractUnknownWordModelTrainer
{

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(BaseUnknownWordModelTrainer.class);

  // Records the number of times word/tag pair was seen in training data.
  ClassicCounter<IntTaggedWord> seenCounter;
  // Counts of each tag (stored as a Label) on unknown words.
  ClassicCounter<Label> tc;

  // tag (Label) --> signature --> count
  Map<Label,ClassicCounter<String>> c;

  ClassicCounter<IntTaggedWord> unSeenCounter;

  Map<Label,ClassicCounter<String>> tagHash;

  Set<String> seenEnd;

  double indexToStartUnkCounting = 0;

  UnknownGTTrainer unknownGTTrainer;

  boolean useEnd, useFirst, useFirstCap, useGT;

  UnknownWordModel model;

  @Override
  public void initializeTraining(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex, double totalTrees) {
    super.initializeTraining(op, lex, wordIndex, tagIndex, totalTrees);

    seenCounter = new ClassicCounter<>();
    unSeenCounter = new ClassicCounter<>();
    tagHash = Generics.newHashMap();
    tc = new ClassicCounter<>();
    c = Generics.newHashMap();
    seenEnd = Generics.newHashSet();

    useEnd = (op.lexOptions.unknownSuffixSize > 0 &&
              op.lexOptions.useUnknownWordSignatures > 0);
    useFirstCap = op.lexOptions.useUnknownWordSignatures > 0;
    useGT = (op.lexOptions.useUnknownWordSignatures == 0);
    useFirst = false;

    if (useFirst) {
      log.info("Including first letter for unknown words.");
    }
    if (useFirstCap) {
      log.info("Including whether first letter is capitalized for unknown words");
    }
    if (useEnd) {
      log.info("Classing unknown word as the average of their equivalents by identity of last " + op.lexOptions.unknownSuffixSize + " letters.");
    }
    if (useGT) {
      log.info("Using Good-Turing smoothing for unknown words.");
    }

    this.indexToStartUnkCounting = (totalTrees * op.trainOptions.fractionBeforeUnseenCounting);

    this.unknownGTTrainer = (useGT) ? new UnknownGTTrainer() : null;

    this.model = buildUWM();
  }

  @Override
  public void train(TaggedWord tw, int loc, double weight) {
    if (useGT) {
      unknownGTTrainer.train(tw, weight);
    }

    // scan data
    String word = tw.word();
    String subString = model.getSignature(word, loc);

    Label tag = new Tag(tw.tag());
    if ( ! c.containsKey(tag)) {
      c.put(tag, new ClassicCounter<>());
    }
    c.get(tag).incrementCount(subString, weight);

    tc.incrementCount(tag, weight);

    seenEnd.add(subString);

    String tagStr = tw.tag();
    IntTaggedWord iW = new IntTaggedWord(word, IntTaggedWord.ANY, wordIndex, tagIndex);
    seenCounter.incrementCount(iW, weight);
    if (treesRead > indexToStartUnkCounting) {
      // start doing this once some way through trees;
      // treesRead is 1 based counting
      if (seenCounter.getCount(iW) < 2) {
        IntTaggedWord iT = new IntTaggedWord(IntTaggedWord.ANY, tagStr, wordIndex, tagIndex);
        unSeenCounter.incrementCount(iT, weight);
        unSeenCounter.incrementCount(NULL_ITW, weight);
      }
    }
  }

  @Override
  public UnknownWordModel finishTraining() {
    if (useGT) {
      unknownGTTrainer.finishTraining();
    }

    for (Map.Entry<Label, ClassicCounter<String>> entry : c.entrySet()) {
      /* outer iteration is over tags */
      Label key = entry.getKey();
      ClassicCounter<String> wc = entry.getValue(); // counts for words given a tag

      if (!tagHash.containsKey(key)) {
        tagHash.put(key, new ClassicCounter<>());
      }

      /* the UNKNOWN sequence is assumed to be seen once in each tag */
      // This is sort of broken, but you can regard it as a Dirichlet prior.
      tc.incrementCount(key);
      wc.setCount(unknown, 1.0);

      /* inner iteration is over words */
      for (Map.Entry<String, Double> wEntry : wc.entrySet()) {
        String end = wEntry.getKey();
        double prob = Math.log(wEntry.getValue() / tc.getCount(key));  // p(sig|tag)
        tagHash.get(key).setCount(end, prob);
        //if (Test.verbose)
        //EncodingPrintWriter.out.println(tag + " rewrites as " + end + " endchar with probability " + prob,encoding);
      }
    }

    return model;
  }

  protected UnknownWordModel buildUWM() {
    Map<String,Float> unknownGT = null;
    if (useGT) {
      unknownGT = unknownGTTrainer.unknownGT;
    }
    return new BaseUnknownWordModel(op, lex, wordIndex, tagIndex,
                                    unSeenCounter, tagHash,
                                    unknownGT, seenEnd);
  }

}

