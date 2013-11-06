package edu.stanford.nlp.parser.lexparser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;


/**
 * An interface for lexicons interfacing to lexparser.  Its primary
 * responsibility is to provide a conditional probability
 * P(word|tag), which is fulfilled by the {#score} method.
 * Inside the lexparser,
 * Strings are interned and tags and words are usually represented as integers.
 *
 * @author Galen Andrew
 */
public interface Lexicon extends Serializable {

  String UNKNOWN_WORD = "UNK";  // if UNK were a word, counts would merge
  String BOUNDARY = ".$.";      // boundary word -- assumed not a real word
  String BOUNDARY_TAG = ".$$."; // boundary tag -- assumed not a real tag


  /**
   * Set the model via which unknown words should be scored by this lexicon
   */
  //void setUnknownWordModel(UnknownWordModel uwModel);

  /**
   * Returns the number of times this word/tag pair has been seen;
   * 0 returned if never previously seen
   */
//  double getCount(IntTaggedWord w);

  /**
   * Checks whether a word is in the lexicon.
   *
   * @param word The word as an int
   * @return Whether the word is in the lexicon
   */
  boolean isKnown(int word);

  /**
   * Checks whether a word is in the lexicon.
   *
   * @param word The word as a String
   * @return Whether the word is in the lexicon
   */
  boolean isKnown(String word);

  /**
   * Get an iterator over all rules (pairs of (word, POS)) for this word.
   *
   * @param word The word, represented as an integer in Index
   * @param loc  The position of the word in the sentence (counting from 0).
   *                <i>Implementation note: The BaseLexicon class doesn't
   *                actually make use of this position information.</i>
   * @param featureSpec Additional word features like morphosyntactic information.
   * @return An Iterator over a List ofIntTaggedWords, which pair the word
   *                with possible taggings as integer pairs.  (Each can be
   *                thought of as a <code>tag -&gt; word<code> rule.)
   */
  Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec);

  /**
   * Same thing, but with a string that needs to be translated by the
   * lexicon's word index
   */
  Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc, String featureSpec);

  /** Returns the number of rules (tag rewrites as word) in the Lexicon.
   *  This method assumes that the lexicon has been initialized.
   *
   * @return The number of rules (tag rewrites as word) in the Lexicon.
   */
  public int numRules();

  /**
   * Start training this lexicon on the expected number of trees.
   * (Some UnknownWordModels use the number of trees to know when to
   * start counting statistics.)
   */
  void initializeTraining(double numTrees);

  /**
   * Trains this lexicon on the Collection of trees.
   * Can be called more than once with different collections of trees.
   *
   * @param trees Trees to train on
   */
  void train(Collection<Tree> trees);

  void train(Collection<Tree> trees, double weight);

  // WSGDEBUG
  // Binarizer converts everything to CategoryWordTag, so we lose additional
  // lexical annotations. RawTrees should be the same size as trees.
  void train(Collection<Tree> trees, Collection<Tree> rawTrees);

  void train(Tree tree, double weight);

  /**
   * Not all subclasses support this particular method.  Those that
   * don't will barf...
   */
  void train(List<TaggedWord> sentence, double weight);

  /**
   * Not all subclasses support this particular method.  Those that
   * don't will barf...
   */
  void train(TaggedWord tw, int loc, double weight);

  /**
   * If training on a per-word basis instead of on a per-tree basis,
   * we will want to increment the tree count as this happens.
   */
  void incrementTreesRead(double weight);

  /**
   * Sometimes we might have a sentence of tagged words which we would
   * like to add to the lexicon, but they weren't part of a binarized,
   * markovized, or otherwise annotated tree.
   */
  void trainUnannotated(List<TaggedWord> sentence, double weight);

  /**
   * Done collecting statistics for the lexicon.
   */
  void finishTraining();

  /**
   * Add additional words with expansion of subcategories.
   */
  // void trainWithExpansion(Collection<TaggedWord> taggedWords);

  /**
   * Get the score of this word with this tag (as an IntTaggedWord) at this
   * loc.
   * (Presumably an estimate of P(word | tag).)
   *
   * @param iTW An IntTaggedWord pairing a word and POS tag
   * @param loc The position in the sentence.  <i>In the default implementation
   *               this is used only for unknown words to change their
   *               probability distribution when sentence initial.</i>
   * @param word The word itself; useful so we don't have to look it
   *               up in an index
   * @param featureSpec TODO
   * @return A score, usually, log P(word|tag)
   */
  float score(IntTaggedWord iTW, int loc, String word, String featureSpec);

  /**
   * Write the lexicon in human-readable format to the Writer.
   * (An optional operation.)
   *
   * @param w The writer to output to
   * @throws IOException If any I/O problem
   */
  public void writeData(Writer w) throws IOException;

  /**
   * Read the lexicon from the BufferedReader in the format written by
   * writeData.
   * (An optional operation.)
   *
   * @param in The BufferedReader to read from
   * @throws IOException If any I/O problem
   */
  public void readData(BufferedReader in) throws IOException;

  public UnknownWordModel getUnknownWordModel();

  // todo [cdm Sep 2013]: It seems like we could easily remove this from the interface
  public void setUnknownWordModel(UnknownWordModel uwm);

}
