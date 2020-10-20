package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.ScoredObject;

import java.util.List;

/**
 * Set of common annotations for {@link edu.stanford.nlp.util.CoreMap}s 
 * that require classes from the trees package.  See 
 * {@link edu.stanford.nlp.ling.CoreAnnotations} for more information.
 * This class exists so that
 * {@link edu.stanford.nlp.ling.CoreAnnotations} need not depend on
 * trees classes, making distributions easier.
 * @author Anna Rafferty
 *
 */

public class TreeCoreAnnotations {
  
  private TreeCoreAnnotations() {} // only static members

  /**
   * The CoreMap key for getting the syntactic parse tree of a sentence.
   *
   * This key is typically set on sentence annotations.
   */
  public static class TreeAnnotation implements CoreAnnotation<Tree> {
    public Class<Tree> getType() {
      return Tree.class;
    }
  }

  /**
   * The CoreMap key for getting the binarized version of the
   * syntactic parse tree of a sentence.
   *
   * This key is typically set on sentence annotations.  It is only
   * set if the parser annotator was specifically set to parse with
   * this (parse.saveBinarized).  The sentiment annotator requires
   * this kind of tree, but otherwise it is not typically used.
   */
  public static class BinarizedTreeAnnotation implements CoreAnnotation<Tree> {
    public Class<Tree> getType() {
      return Tree.class;
    }
  }

  /**
   * The standard key for storing a head word in the map as a pointer to
   * the head label.
   */
  public static class HeadWordLabelAnnotation implements CoreAnnotation<CoreLabel> {
    public Class<CoreLabel> getType() {  return CoreLabel.class; } }

  /**
   * The standard key for storing a head tag in the map as a pointer to
   * the head label.
   */
  public static class HeadTagLabelAnnotation implements CoreAnnotation<CoreLabel> {
    public Class<CoreLabel> getType() {  return CoreLabel.class; } }

  /**
   * The standard key for storing a list of k-best parses.
   */
  public static class KBestTreesAnnotation implements CoreAnnotation<List<Tree>> {
    public Class<List<Tree>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }
}
