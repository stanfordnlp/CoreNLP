package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * This class trains a Good-Turing model for unknown words from a
 * collection of trees.  It builds up a map of statistics which can be
 * used by any UnknownWordModel which wants to use the GT model.
 *
 * Authors:
 *
 * @author Roger Levy
 * @author Greg Donaker (corrections and modeling improvements)
 * @author Christopher Manning (generalized and improved what Greg did)
 * @author Anna Rafferty
 * @author John Bauer (refactored into a separate training class)
 */
public class UnknownGTTrainer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(UnknownGTTrainer.class);
  ClassicCounter<Pair<String,String>> wtCount = new ClassicCounter<>();
  ClassicCounter<String> tagCount = new ClassicCounter<>();
  ClassicCounter<String> r1 = new ClassicCounter<>(); // for each tag, # of words seen once
  ClassicCounter<String> r0 = new ClassicCounter<>(); // for each tag, # of words not seen
  Set<String> seenWords = Generics.newHashSet();

  double tokens = 0;

  Map<String,Float> unknownGT = Generics.newHashMap();

  public void train(Collection<Tree> trees) {
    train(trees, 1.0);
  }

  public void train(Collection<Tree> trees, double weight) {
    for (Tree t : trees) {
      train(t, weight);
    }
  }


  public void train(Tree tree, double weight) {
    /* get TaggedWord and total tag counts, and get set of all
     * words attested in training
     */
    for (TaggedWord word : tree.taggedYield()) {
      train(word, weight);
    }
  }

  public void train(TaggedWord tw, double weight) {
    tokens = tokens + weight;
    String word = tw.word();
    String tag = tw.tag();

    // TaggedWord has crummy equality conditions
    Pair<String,String> wt = new Pair<>(word, tag);
    wtCount.incrementCount(wt, weight);

    tagCount.incrementCount(tag, weight);
    seenWords.add(word);
  }

  public void finishTraining() {
    // testing: get some stats here
    log.info("Total tokens: " + tokens);
    log.info("Total WordTag types: " + wtCount.keySet().size());
    log.info("Total tag types: " + tagCount.keySet().size());
    log.info("Total word types: " + seenWords.size());

    /* find # of once-seen words for each tag */
    for (Pair<String,String> wt : wtCount.keySet()) {
      if (wtCount.getCount(wt) == 1) {
        r1.incrementCount(wt.second());
      }
    }

    /* find # of unseen words for each tag */
    for (String tag : tagCount.keySet()) {
      for (String word : seenWords) {
        Pair<String,String> wt = new Pair<>(word, tag);
        if (!(wtCount.keySet().contains(wt))) {
          r0.incrementCount(tag);
        }
      }
    }

    /* set unseen word probability for each tag */
    for (String tag : tagCount.keySet()) {
      float logprob = (float) Math.log(r1.getCount(tag) / (tagCount.getCount(tag) * r0.getCount(tag)));
      unknownGT.put(tag, Float.valueOf(logprob));
    }

  }

}

