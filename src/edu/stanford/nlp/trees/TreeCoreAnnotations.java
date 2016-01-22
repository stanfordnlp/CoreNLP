package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CoreAnnotation;


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
   * The standard key for storing a head word in the map as a pointer to
   * another node.
   */
  public static class HeadWordAnnotation implements CoreAnnotation<Tree> {
    public Class<Tree> getType() {  return Tree.class; } }

  /**
   * The standard key for storing a head tag in the map as a pointer to
   * another node.
   */
  public static class HeadTagAnnotation implements CoreAnnotation<Tree> {
    public Class<Tree> getType() {  return Tree.class; } }

}
