package edu.stanford.nlp.util;

import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;

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
 *
 * @param <T> The type of the data stored in the stack
 */
public class TreeShapedStack<T> {
  /**
   * Creates an empty stack.
   */
  public TreeShapedStack() { 
    this(null, null, 0);
  }

  private TreeShapedStack(TreeShapedStack<T> previous, T data, int size) {
    this.previous = previous;
    this.data = data;
    this.size = size;
  }

  /**
   * Returns the previous state.  If the size of the stack is 0, an
   * exception is thrown.  If the size is 1, an empty node is
   * returned.
   *
   * @return The stack before the top element was pushed
   * @throws EmptyStackException If the stack is empty
   */
  public TreeShapedStack<T> pop() {
    if (size == 0) {
      throw new EmptyStackException();
    }
    return previous;
  }

  /**
   * Returns a new node with the new data attached.  This node is not changed.
   *
   * @param data The data to put on top of the stack
   * @return A new stack with {@code data} on top of this one
   */
  public TreeShapedStack<T> push(T data) {
    return new TreeShapedStack<>(this, data, size + 1);
  }

  /**
   * Returns the data in the top node of the stack.  If there is no
   * data, eg the stack size is 0, an exception is thrown.
   *
   * @return The data on top of the stack
   * @throws EmptyStackException If the stack is empty
   */
  public T peek() {
    if (size == 0) {
      throw new EmptyStackException();
    }
    return data;
  }

  /**
   * How many nodes in this branch of the stack
   *
   * @return The size of the stack
   */
  public int size() { 
    return size; 
  }

  /**
   * Returns the current stack as a list
   *
   * @return A new list of the stack's data, from bottom to top
   */
  public List<T> asList() {
    List<T> result = Generics.newArrayList(size);
    TreeShapedStack<T> current = this;
    for (int index = 0; index < size; ++index) {
      result.add(current.data);
      current = current.pop();
    }
    Collections.reverse(result);
    return result;
  }

  @Override
  public String toString() {
    return "[" + internalToString(" ") + "]";
  }

  /**
   * Returns the stack's data from bottom to top, in brackets and separated by a comma
   * followed by {@code delimiter}.
   *
   * @param delimiter Text added after each comma
   * @return A string representation of the stack
   */
  public String toString(String delimiter) {
    return "[" + internalToString(delimiter) + "]";
  }

  private String internalToString(String delimiter) {
    if (size() == 0) {
      return " ";
    } else if (size() == 1) {
      return data.toString();
    } else {
      return previous.internalToString(delimiter) + "," + delimiter + data.toString();
    }
  }

  @Override
  public int hashCode() {
    int hash = size();
    if (size() > 0 && peek() != null) {
      hash ^= peek().hashCode();
    }
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TreeShapedStack)) {
      return false;
    }
    TreeShapedStack<?> other = (TreeShapedStack<?>) o;
    TreeShapedStack<T> current = this;
    if (other.size() != this.size()) {
      return false;
    }
    for (int i = 0; i < size(); ++i) {
      T currentObject = current.peek();
      Object otherObject = other.peek();
      if (!(currentObject == otherObject || (currentObject != null && currentObject.equals(otherObject)))) {
        return false;
      }
      other = other.pop();
      current = current.pop();
    }
    return true;
  }

  final T data;
  final int size;
  final TreeShapedStack<T> previous;
}
