package edu.stanford.nlp.parser;

import java.util.*;

import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.trees.Tree;


/** An interface that supports finding k best and/or k good
 *  parses and parse sampling.
 *  These operations are specified by separate methods,
 *  but it is expected that many parsers will return
 *  an UnsupportedOperationException for some of these methods.
 *  This has some other methods that essentially provide a rich
 *  parser interface which is used by certain parsers in lexparser,
 *  including other convenience methods like hasParse() and
 *  getBestScore().
 *
 * @author Christopher Manning
 */
public interface KBestViterbiParser extends ViterbiParser {

  /** Get the exact k best parses for the sentence.
   *
   *  @param k The number of best parses to return
   *  @return The exact k best parses for the sentence, with
   *         each accompanied by its score (typically a
   *         negative log probability).
   */
  public List<ScoredObject<Tree>> getKBestParses(int k);


  /** Get a complete set of the maximally scoring parses for a sentence,
   *  rather than one chosen at random.  This set may be of size 1 or larger.
   *
   *  @return All the equal best parses for a sentence, with each
   *         accompanied by its score
   */
  public List<ScoredObject<Tree>> getBestParses();

  /** Get k good parses for the sentence.  It is expected that the
   *  parses returned approximate the k best parses, but without any
   *  guarantee that the exact list of k best parses has been produced.
   *  If a class really provides k best parses functionality, it is
   *  reasonable to also return this output as the k good parses.
   *
   *  @param k The number of good parses to return
   *  @return A list of k good parses for the sentence, with
   *         each accompanied by its score
   */
  public List<ScoredObject<Tree>> getKGoodParses(int k);

  /** Get k parse samples for the sentence.  It is expected that the
   *  parses are sampled based on their relative probability.
   *
   *  @param k The number of sampled parses to return
   *  @return A list of k parse samples for the sentence, with
   *         each accompanied by its score
   */
  public List<ScoredObject<Tree>> getKSampledParses(int k);

  /** Does the sentence in the last call to parse() have a parse?
   *  In theory this method shouldn't be here, but it seemed a
   *  convenient place to put it for our more general parser interface.
   *
   *  @return Whether the last sentence parsed had a parse
   */
  public boolean hasParse();

  /** Gets the score (typically a log probability) of the best
   *  parse of a sentence.
   *  @return The score for the last sentence parsed.
   */
  public double getBestScore();

}
