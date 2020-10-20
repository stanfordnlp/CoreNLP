package edu.stanford.nlp.parser.lexparser;

import java.util.Collection;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;

/**
 * An interface for training an UnknownWordModel.  Once initialized,
 * you can feed it trees and then call finishTraining to get the
 * UnknownWordModel.
 *
 * @author John Bauer
 */
public interface UnknownWordModelTrainer {

  /**
   * Initialize the trainer with a few of the data structures it needs
   * to train.  Also, it is necessary to estimate the number of trees
   * that it will be given, as many of the UWMs switch training modes
   * after seeing a fraction of the trees.
   *
   * This is an initialization method and not part of the constructor
   * because these Trainers are generally loaded by reflection, and
   * making this a method instead of a constructor lets the compiler
   * catch silly errors.
   */
  void initializeTraining(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex, double totalTrees);

  /**
   * Tallies statistics for this particular collection of trees.  Can
   * be called multiple times.
   */
  void train(Collection<Tree> trees);

  /**
   * Tallies statistics for a weighted collection of trees.  Can
   * be called multiple times.
   */
  void train(Collection<Tree> trees, double weight);

  /**
   * Tallies statistics for a single tree.
   * Can be called multiple times.
   */
  void train(Tree tree, double weight);

  /**
   * Tallies statistics for a single word.
   * Can be called multiple times.
   */
  void train(TaggedWord tw, int loc, double weight);

  /**
   * Maintains a (real-valued) count of how many (weighted) trees have
   * been read in. Can be called multiple times.
   *
   * @param weight The weight of trees additionally trained on
   */
  void incrementTreesRead(double weight);

  /**
   * Returns the trained UWM.  Many of the subclasses build exactly
   * one model, and some of the finishTraining methods manipulate the
   * data in permanent ways, so this should only be called once
   */
  UnknownWordModel finishTraining();


  String unknown = "UNK";

  int nullWord = -1;
  short nullTag = -1;

  IntTaggedWord NULL_ITW = new IntTaggedWord(nullWord, nullTag);

}
