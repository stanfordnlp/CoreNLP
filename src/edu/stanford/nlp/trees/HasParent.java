package edu.stanford.nlp.trees;

/**
 * Only to be implemented by Tree subclasses that actualy keep their
 * parent pointers.  For example, the base Tree class should
 * <b>not</b> implement this, but TreeGraphNode should.
 *
 * @author John Bauer
 */
public interface HasParent {
  Tree parent();
}

