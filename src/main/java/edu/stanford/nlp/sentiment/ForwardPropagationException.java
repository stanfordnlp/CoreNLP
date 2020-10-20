package edu.stanford.nlp.sentiment;

/**
 * Runtime exception for when a tree is wrong in the forward
 * propagation.  This lets us print out the whole tree - at the time
 * the error happens, it's too late to report the tree unless you pass
 * it around everywhere
 */
public class ForwardPropagationException extends RuntimeException {

  private static final long serialVersionUID = 564523452761325243L;

  public ForwardPropagationException(String s) {
    super(s);
  }
}

