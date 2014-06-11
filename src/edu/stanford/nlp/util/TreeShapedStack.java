package edu.stanford.nlp.util;

import java.util.EmptyStackException;

/**
 * Represents a stack where one prefix of the stack can branch in
 * several directions.  Calling "push" on this object returns a new
 * object which points to the previous state.  Calling "pop" returns a
 * pointer to the previous state.  The only way to access the current
 * node's information is with "peek".
 * <br>
 * Note that if you have an earlier node in the tree, you have no way
 * of recovering later nodes.  It is essential to keep the ends of the
 * stack you are interested in.
 */
public class TreeShapedStack<T> {
  /**
   * Creates an empty stack.
   */
  public TreeShapedStack() { 
    this(null, null, 0);
  }

  private TreeShapedStack(TreeShapedStack previous, T data, int size) {
    this.previous = previous;
    this.data = data;
    this.size = size;
  }

  /**
   * Returns the previous state.  If the size of the stack is 0, an
   * exception is thrown.  If the size is 1, an empty node is
   * returned.
   */
  public TreeShapedStack pop() {
    if (size == 0) {
      throw new EmptyStackException();
    }
    return previous;
  }

  /**
   * Returns a new node with the new data attached.
   */
  public TreeShapedStack push(T data) {
    return new TreeShapedStack(this, data, size + 1);
  }

  /**
   * Returns the data in the top node of the stack.  If there is no
   * data, eg the stack size is 0, an exception is thrown.
   */
  public T peek() {
    if (size == 0) {
      throw new EmptyStackException();
    }
    return data;
  }

  final T data;
  final int size;
  final TreeShapedStack previous;
}
