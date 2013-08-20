package edu.stanford.nlp.parser.lexparser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;


public class SubcatUnknownWordModelTrainer 
  extends AbstractUnknownWordModelTrainer
{
  // Records the number of times word/tag pair was seen in training data.
  ClassicCounter<IntTaggedWord> seenCounter;

  ClassicCounter<IntTaggedWord> unSeenCounter;

  Set<Integer> targetWords;
  // TODO: separate this class
  SubcatParserTrainer.SubcatMarkedLocalTreeExtractor treeExtractor;

  UnknownWordModel model;

  public void initializeTraining(Options op, Lexicon lex, 
                                 Index<String> wordIndex, 
                                 Index<String> tagIndex, double totalTrees,
                                 Set<Integer> targetWords, 
                                 SubcatParserTrainer.SubcatMarkedLocalTreeExtractor treeExtractor) {
    super.initializeTraining(op, lex, wordIndex, tagIndex, totalTrees);
    this.targetWords = targetWords;
    this.treeExtractor = treeExtractor;

    seenCounter = new ClassicCounter<IntTaggedWord>();
    unSeenCounter = new ClassicCounter<IntTaggedWord>();

    model = new EnglishUnknownWordModel(op, lex, wordIndex, tagIndex, 
                                        unSeenCounter);
  }

  public UnknownWordModel finishTraining() {
    return model;
  }

  public void train(TaggedWord tw, int loc, double weight) {
    IntTaggedWord iTW = 
      new IntTaggedWord(tw.word(), tw.tag(), wordIndex, tagIndex);
    
    String tagString = tagIndex.get(iTW.tag);
    boolean isMarked = tagString.indexOf('_') >= 0;
    
    // check to see whether it this word is one of our target words
    if (targetWords.contains(Integer.valueOf(iTW.word)) && isMarked) {
      // if so, we want to put in some marked rules in with the target word marked
      String wordString = wordIndex.get(iTW.word);
      IntTaggedWord miTW = new IntTaggedWord(wordIndex.indexOf(wordString + "^", true), iTW.tag);
      seenCounter.incrementCount(miTW, weight);
      IntTaggedWord miT = new IntTaggedWord(nullWord, miTW.tag);
      seenCounter.incrementCount(miT, weight);
      IntTaggedWord miW = new IntTaggedWord(miTW.word, nullTag);
      seenCounter.incrementCount(miW, weight);
      IntTaggedWord mi = new IntTaggedWord(nullWord, nullTag);
      seenCounter.incrementCount(mi, weight);
      // rules.add(miTW);
    }
    // if it is the target word, but it is not marked, then we treat it as normal
    
    // everything else should proceed with the unmarked tag
    if (isMarked) {
      tagString = treeExtractor.removeSubcatMarkersFromString(tagString, new ArrayList<Integer>());
      iTW = new IntTaggedWord(iTW.word, tagIndex.indexOf(tagString, true));
    }
    seenCounter.incrementCount(iTW, weight);
    IntTaggedWord iT = new IntTaggedWord(nullWord, iTW.tag);
    seenCounter.incrementCount(iT, weight);
    IntTaggedWord iW = new IntTaggedWord(iTW.word, nullTag);
    seenCounter.incrementCount(iW, weight);
    IntTaggedWord i = new IntTaggedWord(nullWord, nullTag);
    seenCounter.incrementCount(i, weight);
    
    if (treesRead > totalTrees / 2) {
      // start doing this once we're halfway through the trees
      if (seenCounter.getCount(iW) < 2) {
        // it's an entirely unknown word
        wordIndex.indexOf(Lexicon.UNKNOWN_WORD, true); //cdm: outside loop?
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
}
