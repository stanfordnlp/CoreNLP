package edu.stanford.nlp.sentiment;

import edu.stanford.nlp.ling.CoreAnnotation;

import edu.stanford.nlp.trees.Tree;

/**
 * Annotations specific to the Sentiment project.  In case there are
 * other projects that use the same RNN machinery, including the RNN
 * core annotations, this lets a sentence have a tree attached where
 * that tree specifically has the sentiment annotations.
 *
 * @author John Bauer
 */
public class SentimentCoreAnnotations {

  /**
   * A tree which contains the annotations used for the Sentiment
   * task.  After forwardPropagate has been called, the Tree will have
   * prediction, etc. attached to it.
   */
  public static class SentimentAnnotatedTree implements CoreAnnotation<Tree> {
    @Override
    public Class<Tree> getType() {
      return Tree.class;
    }
  }


  /**
   * The final label given for a sentence.  Set by the
   * SentimentAnnotator and used by various forms of text output.
   */
  public static class SentimentClass implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }
}

