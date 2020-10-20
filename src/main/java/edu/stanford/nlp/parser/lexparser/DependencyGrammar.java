package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.Tree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collection;

/**
 * An interface for DependencyGrammars.
 *
 * @author Galen Andrew
 * @author Christopher Manning
 */
public interface DependencyGrammar extends Serializable {

  /** @return The number of tags recognized in the reduced (projected) tag
   *    space used in the DependencyGrammar. 
   */
  int numTagBins();

  /** Converts a tag (coded as an integer via a Numberer) from its
   *  representation in the full tag space to the reduced (projected) tag
   *  space used in the DependencyGrammar.
   *  
   *  @param tag An int encoding a tag (in the "tags" Numberer)
   *  @return An int representing the tag in the reduced binTag space
   */
  int tagBin(int tag);

  /** @return The number of distance buckets (measured between the head and
   *    the nearest corner of the argument) used in calculating attachment
   *    probabilities by the DependencyGrammar
   */
  int numDistBins();

  /** @param distance A distance in intervening words between head and arg 
   *  @return The distance bucket corresponding the the original distance
   *    (measured between the head and
   *    the nearest corner of the argument) used in calculating attachment
   *    probabilities by the DependencyGrammar.  Bucket numbers are small
   *    integers [0, ..., numDistBins - 1].
   */
  short distanceBin(int distance);

  /**
   * Tune free parameters on these trees.
   * A substantive implementation is optional. 
   * @param trees A Collection of Trees for use as a tuning data set
   */
  void tune(Collection<Tree> trees);

  /** Score a IntDependency according to the grammar.
   *  
   *  @param dependency The dependency object to be scored, in normal form.
   *  @return The negative log probability given to the dependency by the
   *         grammar.  This may be Double.NEGATIVE_INFINITY for "impossible".
   */
  double score(IntDependency dependency);

  /** Score an IntDependency in the reduced tagBin space according to the
   *  grammar.
   *  
   *  @param dependency The dependency object to be scored, where the tags in
   *         the dependency have already been mapped to a reduced space by a
   *         tagProjection function.
   *  @return The negative log probability given to the dependency by the
   *         grammar.  This may be Double.NEGATIVE_INFINITY for "impossible".
   */
  double scoreTB(IntDependency dependency);

  /** Score a dependency according to the grammar, where the elements of the
   *  dependency are represented in separate paramters.
   *  
   *  @return The negative log probability given to the dependency by the
   *         grammar.  This may be Double.NEGATIVE_INFINITY for "impossible".
   */
  double score(int headWord, int headTag, int argWord, int argTag, boolean leftHeaded, int dist);

  /** Score a dependency according to the grammar, where the elements of the
   *  dependency are represented in separate paramters.  The tags in
   *  the dependency have already been mapped to a reduced space by a
   *  tagProjection function.
   *
   *  @return The negative log probability given to the dependency by the
   *         grammar.  This may be Double.NEGATIVE_INFINITY for "impossible".
   */
  double scoreTB(int headWord, int headTag, int argWord, int argTag, boolean leftHeaded, int dist);

  /**
   * Read from text grammar.  Optional.
   * @throws IOException
   */
  void readData(BufferedReader in) throws IOException;

  /**
   * Write to text grammar.  Optional.
   * @throws IOException
   */
  void writeData(PrintWriter w) throws IOException;

  /** Set the Lexicon, which the DependencyGrammar may use in scoring P(w|t). */
  public void setLexicon(Lexicon lexicon);

}
