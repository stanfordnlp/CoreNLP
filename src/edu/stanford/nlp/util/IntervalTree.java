package edu.stanford.nlp.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;


/**
 * An interval tree maintains a tree so that all intervals to the left start
 * before current interval and all intervals to the right start after.
 *
 * @author Angel Chang
 * @param <E> The type of the interval endpoints
 * @param <T> The type of the values stored, which have intervals
 */
public class IntervalTree<E extends Comparable<E>, T extends HasInterval<E>> extends AbstractCollection<T>
{
  private static final double defaultAlpha = 0.65; // How balanced we want this tree (between 0.5 and 1.0)
  private static final boolean debug = false;

  private TreeNode<E,T> root = new TreeNode<>();

  /** Create an empty tree. */
  public IntervalTree() { }

  TreeNode<E, T> root() { return root; }

  /**
   * A node of the tree. An empty node (with a null value) is used for an empty tree.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored, which have intervals
   */
  public static class TreeNode<E extends Comparable<E>, T extends HasInterval<E>> {
    /** Create an empty node. */
    public TreeNode() { }

    T value;
    E maxEnd;    // Maximum end in this subtree
    int size;

    TreeNode<E,T> left;
    TreeNode<E,T> right;

    TreeNode<E,T> parent; // Parent for convenience

    /**
     * Returns whether this node has no value.
     *
     * @return Whether the value is null
     */
    public boolean isEmpty() { return value == null; }

    /** Removes the value and children of this node, making it empty. The parent link is kept. */
    public void clear() {
      value = null;
      maxEnd = null;
      size = 0;
      left = null;
      right = null;
//      parent = null;
    }

    public String toString() {
      return "TreeNode(value=" + value + ", size=" + size + ", maxEnd=" + maxEnd + ")";
    }    
  }

  @Override
  public boolean isEmpty() { return root.isEmpty(); }

  @Override
  public void clear() {
    root.clear();
  }

  public String toString() {
    return "Size: " + root.size;
  }

  @Override
  public boolean add(T target) {
    return add(root, target, defaultAlpha);
  }

  /**
   * Add a value to the subtree rooted at the given node, rebalancing if needed.
   *
   * @param node The root of the subtree to add to
   * @param target The value to add
   * @return Whether the value was added (false if it is null)
   */
  public boolean add(TreeNode<E,T> node, T target) {
    return add(node, target, defaultAlpha);
  }

  /**
   * Add a value to the subtree rooted at the given node - attempting to maintain alpha balance.
   * If the new node is deep enough, the lowest ancestor which has more than 10 nodes and is not
   * alpha balanced is rebalanced.
   *
   * @param node The root of the subtree to add to
   * @param target The value to add
   * @param alpha How balanced the tree should be (between 0.5 and 1.0)
   * @return Whether the value was added (false if it is null)
   */
  public boolean add(TreeNode<E,T> node, T target, double alpha) {
    if (target == null) return false;
    TreeNode<E,T> n = node;
    int depth = 0;
    int thresholdDepth = (node.size > 10)? ((int) (-Math.log(node.size)/Math.log(alpha)+1)):10;
    while (n != null) {
      if (n.value == null) {
        n.value = target;
        n.maxEnd = target.getInterval().getEnd();
        n.size = 1;
        if (depth > thresholdDepth) {
          // Do rebalancing
          TreeNode<E,T> p = n.parent;
          while (p != null) {
            if (p.size > 10 && !isAlphaBalanced(p,alpha)) {
              TreeNode<E,T> newParent = balance(p);
              if (p == root) root = newParent;
              if (debug) this.check();
              break;
            }
            p = p.parent;
          }
        }
        return true;
      } else {
        depth++;
        n.maxEnd = Interval.max(n.maxEnd, target.getInterval().getEnd());
        n.size++;
        if (target.getInterval().compareTo(n.value.getInterval()) <= 0) {
          // Should go on left
          if (n.left == null) {
            n.left = new TreeNode<>();
            n.left.parent = n;
          }
          n = n.left;
        } else {
          // Should go on right
          if (n.right == null) {
            n.right = new TreeNode<>();
            n.right.parent = n;
          }
          n = n.right;
        }
      }
    }
    return false;
  }

  @Override
  public int size()
  {
    return root.size;
  }

  @Override
  public Iterator<T> iterator() {
    return new TreeNodeIterator<>(root);
  }

  private static class TreeNodeIterator<E extends Comparable<E>, T extends HasInterval<E>> extends AbstractIterator<T> {
    TreeNode<E,T> node;
    Iterator<T> curIter;
    int stage = -1;
    T next;

    public TreeNodeIterator(TreeNode<E,T> node) {
      this.node = node;
      if (node.isEmpty()) {
        stage = 3;
      }
    }

    @Override
    public boolean hasNext() {
      if (next == null) {
        next = getNext();
      }
      return next != null;
    }

    @Override
    public T next() {
      if (hasNext()) {
        T x = next;
        next = getNext();
        return x;
      } else throw new NoSuchElementException();
    }

    private T getNext() {
      // TODO: Do more efficient traversal down the tree
      if (stage > 2) return null;
      while (curIter == null || !curIter.hasNext()) {
        stage++;
        switch (stage) {
          case 0:
            curIter = (node.left != null)? new TreeNodeIterator<>(node.left):null;
            break;
          case 1:
            curIter = null;
            return node.value;
          case 2:
            curIter = (node.right != null)? new TreeNodeIterator<>(node.right):null;
            break;
          default:
            return null;
        }
      }
      if (curIter != null && curIter.hasNext()) {
        return curIter.next();
      } else return null;
    }
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    boolean modified = false;
    for (Object t:c) {
      if (remove(t)) { modified = true; }
    }
    return modified;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("retainAll not implemented");
  }

  @Override
  public boolean contains(Object o) {
    try {
      return contains((T) o);
    } catch (ClassCastException ex) {
      return false;
    }
  }

  @Override
  public boolean remove(Object o) {
    try {
      return remove((T) o);
    } catch (ClassCastException ex) {
      return false;
    }
  }

  /**
   * Remove a value (compared with equals) from the tree.
   *
   * @param target The value to remove
   * @return Whether the value was found and removed
   */
  public boolean remove(T target) {
    return remove(root, target);
  }

  /**
   * Remove a value (compared with equals) from the subtree rooted at the given node.
   * The search follows the interval ordering, going left when the target's interval is
   * less than or equal to a node's interval.
   *
   * @param node The root of the subtree to remove from
   * @param target The value to remove
   * @return Whether the value was found and removed
   */
  public boolean remove(TreeNode<E,T> node, T target)
  {
    if (target == null) return false;
    if (node.value == null) return false;
    if (target.equals(node.value)) {
      int leftSize = (node.left != null)? node.left.size:0;
      int rightSize = (node.right != null)? node.right.size:0;
      if (leftSize == 0) {
        if (rightSize == 0) {
          node.clear();
        } else {
          node.value = node.right.value;
          node.size = node.right.size;
          node.maxEnd = node.right.maxEnd;
          node.left = node.right.left;
          node.right = node.right.right;
          if (node.left != null) node.left.parent = node;
          if (node.right != null) node.right.parent = node;
        }
      } else if (rightSize == 0) {
        node.value = node.left.value;
        node.size = node.left.size;
        node.maxEnd = node.left.maxEnd;
        // Don't overwrite the left child before getting all of its values!
        node.right = node.left.right;
        node.left = node.left.left;
        if (node.left != null) node.left.parent = node;
        if (node.right != null) node.right.parent = node;
      } else {
        // Rotate left up
        node.value = node.left.value;
        node.size--;
        node.maxEnd = Interval.max(node.left.maxEnd, node.right.maxEnd);
        TreeNode<E,T> origRight = node.right;
        node.right = node.left.right;
        node.left = node.left.left;
        if (node.left != null) node.left.parent = node;
        if (node.right != null) node.right.parent = node;

        // Attach origRight somewhere...
        TreeNode<E,T> rightmost = getRightmostNode(node);
        rightmost.right = origRight;
        if (rightmost.right != null) {
          rightmost.right.parent = rightmost;
          // adjust maxEnd and sizes on the right
          adjustUpwards(rightmost.right,node);
        }
      }
      return true;
    } else {
      if (target.getInterval().compareTo(node.value.getInterval()) <= 0) {
        // Should go on left
        if (node.left == null) {
          return false;
        }
        boolean res = remove(node.left, target);
        if (res) {
          node.maxEnd = node.value.getInterval().getEnd();
          if (node.right != null) {
            node.maxEnd = Interval.max(node.maxEnd, node.right.maxEnd);
          }
          if (node.left != null) {
            if (node.left.size == 0) {
              // got called clear() in a pathway which could have been left or right
              node.left = null;
            } else {
              node.maxEnd = Interval.max(node.maxEnd, node.left.maxEnd);
            }
          }
          node.size--;
        }
        return res;
      } else {
        // Should go on right
        if (node.right == null) {
          return false;
        }
        boolean res = remove(node.right, target);
        if (res) {
          node.maxEnd = node.value.getInterval().getEnd();
          if (node.left != null) {
            node.maxEnd = Interval.max(node.maxEnd, node.left.maxEnd);
          }
          if (node.right != null) {
            if (node.right.size == 0) {
              // got called clear() in a pathway which could have been left or right
              node.right = null;
            } else {
              node.maxEnd = Interval.max(node.maxEnd, node.right.maxEnd);
            }
          }
          node.size--;
        }
        return res;
      }
    }
  }

  private void adjustUpwards(TreeNode<E,T> node) {
    adjustUpwards(node, null);
  }

  // Adjust upwards starting at this node until stopAt
  private void adjustUpwards(TreeNode<E,T> node, TreeNode<E,T> stopAt) {
    TreeNode<E,T> n = node;
    while (n != null && n != stopAt) {
      int leftSize = (n.left != null)? n.left.size:0;
      int rightSize = (n.right != null)? n.right.size:0;
      n.maxEnd = n.value.getInterval().getEnd();
      if (n.left != null) {
        n.maxEnd = Interval.max(n.maxEnd, n.left.maxEnd);
      }
      if (n.right != null) {
        n.maxEnd = Interval.max(n.maxEnd, n.right.maxEnd);
      }
      n.size = leftSize + 1 + rightSize;
      if (n == n.parent) {
         throw new IllegalStateException("node is same as parent!!!");
      }
      n = n.parent;
    }
  }

  private void adjust(TreeNode<E,T> node) {
    adjustUpwards(node, node.parent);
  }

  /**
   * Checks the structure of the tree.
   *
   * @throws IllegalStateException If an invariant of the tree (sizes, maxEnd, parent links, ordering) is violated
   */
  public void check() {
    check(root);
  }

  /**
   * Checks the structure of the subtree rooted at the given node.
   *
   * @param treeNode The root of the subtree to check
   * @throws IllegalStateException If an invariant of the tree (sizes, maxEnd, parent links, ordering) is violated
   */
  public void check(TreeNode<E,T> treeNode) {
    Stack<TreeNode<E,T>> todo = new Stack<>();
    todo.add(treeNode);
    while (!todo.isEmpty()) {
      TreeNode<E,T> node = todo.pop();
      if (node == node.parent) {
        throw new IllegalStateException("node is same as parent!!!");
      }
      if (node.isEmpty()) {
        if (node.left != null) throw new IllegalStateException("Empty node shouldn't have left branch");
        if (node.right != null) throw new IllegalStateException("Empty node shouldn't have right branch");
        continue;
      }
      int leftSize = (node.left != null)? node.left.size:0;
      int rightSize = (node.right != null)? node.right.size:0;
      E leftMax = (node.left != null)? node.left.maxEnd:null;
      E rightMax = (node.right != null)? node.right.maxEnd:null;
      E maxEnd = node.value.getInterval().getEnd();
      if (leftMax != null && leftMax.compareTo(maxEnd) > 0) {
        maxEnd = leftMax;
      }
      if (rightMax != null && rightMax.compareTo(maxEnd) > 0) {
        maxEnd = rightMax;
      }
      if (!maxEnd.equals(node.maxEnd)) {
        throw new IllegalStateException("max end is not as expected!!!");
      }
      if (node.size != leftSize + rightSize + 1) {
        throw new IllegalStateException("node size is not one plus the sum of left and right!!!");
      }
      if (node.left != null) {
        if (node.left.parent != node) {
          throw new IllegalStateException("node left parent is not same as node!!!");
        }
      }
      if (node.right != null) {
        if (node.right.parent != node) {
          throw new IllegalStateException("node right parent is not same as node!!!");
        }
      }
      if (node.parent != null) {
        // Go up parent and make sure we are on correct side
        TreeNode<E,T> n = node;
        while (n != null && n.parent != null) {
          // Check we are either right or left
          if (n == n.parent.left) {
            // Check that node is less than the parent
            if (node.value != null) {
              if (node.value.getInterval().compareTo(n.parent.value.getInterval()) > 0) {
                throw new IllegalStateException("node is not on the correct side!!!");
              }
            }
          } else if (n == n.parent.right) {
            // Check that node is greater than the parent
            if (node.value.getInterval().compareTo(n.parent.value.getInterval()) <= 0) {
              throw new IllegalStateException("node is not on the correct side!!!");
            }
          } else {
            throw new IllegalStateException("node is not parent's left or right child!!!");
          }
          n = n.parent;
        }
      }
      if (node.left != null) todo.add(node.left);
      if (node.right != null) todo.add(node.right);
    }
  }


  /**
   * Returns whether neither child of the node has more than {@code alpha * size + 1} nodes.
   *
   * @param node The node to check
   * @param alpha How balanced the tree should be (between 0.5 and 1.0)
   * @return Whether the node is alpha balanced
   */
  public boolean isAlphaBalanced(TreeNode<E,T> node, double alpha) {
    int leftSize = (node.left != null)? node.left.size:0;
    int rightSize = (node.right != null)? node.right.size:0;
    int threshold = (int) (alpha*node.size) + 1;
    return (leftSize <= threshold) && (rightSize <= threshold);
  }

  /** Rebalances the whole tree. */
  public void balance() {
    root = balance(root);
  }

  /**
   * Balances the subtree rooted at the given node, by rotating the median node of each subtree up to its root.
   * The tree's root is not updated by this method.
   *
   * @param node The root of the subtree to balance
   * @return The new root of the subtree
   */
  public TreeNode<E,T> balance(TreeNode<E,T> node) {
    if (debug) check(node);
    Stack<TreeNode<E,T>> todo = new Stack<>();
    todo.add(node);
    TreeNode<E,T> newRoot = null;
    while (!todo.isEmpty()) {
      TreeNode<E,T> n = todo.pop();
      // Balance tree between this node
      // Select median nodes and try to balance the tree
      int medianAt = n.size/2;
      TreeNode<E,T> median = getNode(n, medianAt);
      // Okay, this is going to be our root
      if (median != null && median != n) {
        // Yes, there is indeed something to be done
        rotateUp(median, n);
      }
      if (newRoot == null) {
        newRoot = median;
      }
      if (median.left != null) todo.push(median.left);
      if (median.right != null) todo.push(median.right);
    }
    if (newRoot == null) return node;
    else return newRoot;
  }

  /**
   * Moves this node up the tree, by rotations, until it replaces the target node.
   *
   * @param node The node to move up
   * @param target The ancestor of {@code node} whose place it should take
   */
  public void rotateUp(TreeNode<E,T> node, TreeNode<E,T> target) {
    TreeNode<E,T> n = node;
    boolean done = false;
    while (n != null && n.parent != null && !done) {
      // Check if we are the left or right child
      done = (n.parent == target);
      if (n == n.parent.left) {
        n = rightRotate(n.parent);
      } else if (n == n.parent.right) {
        n = leftRotate(n.parent);
      } else {
        throw new IllegalStateException("Not on parent's left or right branches.");
      }
      if (debug) check(n);
    }
  }

  /**
   * Moves this node to the right and the left child up and returns the new root.
   * The tree's root is not updated by this method.
   *
   * @param oldRoot The node to rotate
   * @return The new root of the subtree, or {@code oldRoot} itself if it is null, empty or has no left child
   */
  public TreeNode<E,T> rightRotate(TreeNode<E,T> oldRoot) {
    if (oldRoot == null || oldRoot.isEmpty() || oldRoot.left == null) return oldRoot;

    TreeNode<E,T> oldLeftRight = oldRoot.left.right;

    TreeNode<E,T> newRoot = oldRoot.left;
    newRoot.right = oldRoot;
    oldRoot.left = oldLeftRight;

    // Adjust parents and such
    newRoot.parent = oldRoot.parent;
    newRoot.maxEnd = oldRoot.maxEnd;
    newRoot.size = oldRoot.size;
    if (newRoot.parent != null) {
      if (newRoot.parent.left == oldRoot) {
        newRoot.parent.left = newRoot;
      } else if (newRoot.parent.right == oldRoot) {
        newRoot.parent.right = newRoot;
      } else {
        throw new IllegalStateException("Old root not a child of it's parent");
      }
    }

    oldRoot.parent = newRoot;
    if (oldLeftRight != null) oldLeftRight.parent = oldRoot;
    adjust(oldRoot);
    return newRoot;
  }

  /**
   * Moves this node to the left and the right child up and returns the new root.
   * The tree's root is not updated by this method.
   *
   * @param oldRoot The node to rotate
   * @return The new root of the subtree, or {@code oldRoot} itself if it is null, empty or has no right child
   */
  public TreeNode<E,T> leftRotate(TreeNode<E,T> oldRoot) {
    if (oldRoot == null || oldRoot.isEmpty() || oldRoot.right == null) return oldRoot;

    TreeNode<E,T> oldRightLeft = oldRoot.right.left;

    TreeNode<E,T> newRoot = oldRoot.right;
    newRoot.left = oldRoot;
    oldRoot.right = oldRightLeft;

    // Adjust parents and such
    newRoot.parent = oldRoot.parent;
    newRoot.maxEnd = oldRoot.maxEnd;
    newRoot.size = oldRoot.size;
    if (newRoot.parent != null) {
      if (newRoot.parent.left == oldRoot) {
        newRoot.parent.left = newRoot;
      } else if (newRoot.parent.right == oldRoot) {
        newRoot.parent.right = newRoot;
      } else {
        throw new IllegalStateException("Old root not a child of it's parent");
      }
    }

    oldRoot.parent = newRoot;
    if (oldRightLeft != null) oldRightLeft.parent = oldRoot;
    adjust(oldRoot);
    return newRoot;
  }

  /**
   * Returns the height of the tree.
   *
   * @return The number of nodes on the longest path from the root to a leaf (0 for an empty tree)
   */
  public int height() { return height(root); }

  /**
   * Returns the height of the subtree rooted at the given node.
   *
   * @param node The root of the subtree
   * @return The number of nodes on the longest path from {@code node} to a leaf (0 for an empty node)
   */
  public int height(TreeNode<E,T> node) {
    if (node.value == null) return 0;
    int lh = (node.left != null)? height(node.left):0;
    int rh = (node.right != null)? height(node.right):0;
    return Math.max(lh,rh) + 1;
  }

  /**
   * Returns the leftmost node of the subtree rooted at the given node.
   *
   * @param node The root of the subtree
   * @return The leftmost node, which is {@code node} if it has no left child
   */
  public TreeNode<E,T> getLeftmostNode(TreeNode<E,T> node)
  {
    TreeNode<E,T> n = node;
    while (n.left != null) {
      n = n.left;
    }
    return n;
  }

  /**
   * Returns the rightmost node of the subtree rooted at the given node.
   *
   * @param node The root of the subtree
   * @return The rightmost node, which is {@code node} if it has no right child
   */
  public TreeNode<E,T> getRightmostNode(TreeNode<E,T> node)
  {
    TreeNode<E,T> n = node;
    while (n.right != null) {
      n = n.right;
    }
    return n;
  }

  /**
   * Returns the ith node (counting from 0, in order) of the subtree rooted at the given node.
   *
   * @param node The root of the subtree
   * @param nodeIndex The position of the node to return
   * @return The node, or null if {@code nodeIndex} is out of range
   */
  public TreeNode<E,T> getNode(TreeNode<E,T> node, int nodeIndex) {
    int i = nodeIndex;
    TreeNode<E,T> n = node;
    while (n != null) {
      if (i < 0 || i >= n.size) return null;
      int leftSize = (n.left != null)? n.left.size:0;
      if (i == leftSize) {
        return n;
      } else if (i > leftSize) {
        // Look for in right side of tree
        n = n.right;
        i = i - leftSize - 1;
      } else {
        n = n.left;
      }
    }
    return null;
  }

  /**
   * Adds the value if its interval does not overlap the interval of any value in the tree.
   *
   * @param target The value to add
   * @return Whether the value was added
   */
  public boolean addNonOverlapping(T target)
  {
    if (overlaps(target)) return false;
    add(target);
    return true;
  }

  /**
   * Adds the value unless its interval is contained in the interval of a value in the tree.
   *
   * @param target The value to add
   * @return Whether the value was added
   */
  public boolean addNonNested(T target)
  {
    if (containsInterval(target, false)) return false;
    add(target);
    return true;
  }

  /**
   * Returns whether the interval of any value in the tree overlaps the interval of the target.
   *
   * @param target The value whose interval to check
   * @return Whether there is an overlapping value
   */
  public boolean overlaps(T target) {
    return overlaps(root, target.getInterval());
  }

  /**
   * Returns the values in the tree whose intervals overlap the interval of the target.
   *
   * @param target The value whose interval to check
   * @return A new list of the overlapping values
   */
  public List<T> getOverlapping(T target) {
    return getOverlapping(root, target.getInterval());
  }

  /**
   * Returns the values in the subtree whose intervals contain the point p.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param n The root of the subtree to search
   * @param p The point
   * @return A new list of the matching values
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> List<T> getOverlapping(TreeNode<E,T> n, E p)
  {
    List<T> overlapping = new ArrayList<>();
    getOverlapping(n, p, overlapping);
    return overlapping;
  }

  /**
   * Returns the values in the subtree whose intervals overlap the target interval.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param n The root of the subtree to search
   * @param target The interval
   * @return A new list of the overlapping values
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> List<T> getOverlapping(TreeNode<E,T> n, Interval<E> target)
  {
    List<T> overlapping = new ArrayList<>();
    getOverlapping(n, target, overlapping);
    return overlapping;
  }

  /**
   * Search for all intervals which contain p, starting with the
   * node "n" and adding matching intervals to the list "result".
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param n The root of the subtree to search
   * @param p The point
   * @param result The list to add the matching values to
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(TreeNode<E,T> n, E p, List<T> result) {
    getOverlapping(n, Interval.toInterval(p,p), result);
  }

  /**
   * Search for all values whose intervals overlap the target interval, starting with the
   * given node and adding matching values to the list "result".
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param node The root of the subtree to search
   * @param target The interval
   * @param result The list to add the overlapping values to
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(TreeNode<E,T> node, Interval<E> target, List<T> result) {
    Queue<TreeNode<E,T>> todo = new LinkedList<>();
    todo.add(node);
    while (!todo.isEmpty()) {
      TreeNode<E,T> n = todo.poll();
      // Don't search nodes that don't exist
      if (n == null || n.isEmpty())
        continue;

      // If target is to the right of the rightmost point of any interval
      // in this node and all children, there won't be any matches.
      if (target.first.compareTo(n.maxEnd) > 0)
        continue;

      // Search left children
      if (n.left != null) {
          todo.add(n.left);
      }

      // Check this node
      if (n.value.getInterval().overlaps(target)) {
          result.add(n.value);
      }

      // If target is to the left of the start of this interval,
      // then it can't be in any child to the right.
      if (target.second.compareTo(n.value.getInterval().first()) < 0)  {
        continue;
      }

      // Otherwise, search right children
      if (n.right != null)  {
        todo.add(n.right);
      }
    }
  }

  /**
   * Returns whether the interval of any value in the subtree contains the point p.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param n The root of the subtree to search
   * @param p The point
   * @return Whether there is a matching value
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(TreeNode<E,T> n, E p) {
    return overlaps(n, Interval.toInterval(p,p));
  }
  /**
   * Returns whether the interval of any value in the subtree overlaps the target interval.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param node The root of the subtree to search
   * @param target The interval
   * @return Whether there is an overlapping value
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(TreeNode<E,T> node, Interval<E> target) {
    Stack<TreeNode<E,T>> todo = new Stack<>();
    todo.push(node);

    while (!todo.isEmpty()) {
      TreeNode<E,T> n = todo.pop();
      // Don't search nodes that don't exist
      if (n == null || n.isEmpty()) continue;

      // If target is to the right of the rightmost point of any interval
      // in this node and all children, there won't be any matches.
      if (target.first.compareTo(n.maxEnd) > 0)
          continue;

      // Check this node
      if (n.value.getInterval().overlaps(target)) {
          return true;
      }

      // Search left children
      if (n.left != null) {
        todo.add(n.left);
      }

      // If target is to the left of the start of this interval,
      // then it can't be in any child to the right.
      if (target.second.compareTo(n.value.getInterval().first()) < 0)  {
        continue;
      }

      if (n.right != null)  {
        todo.add(n.right);
      }
    }
    return false;
  }

  /**
   * Returns whether the tree contains a value equal to the target.
   *
   * @param target The value to look for
   * @return Whether the value is in the tree
   */
  public boolean contains(T target) {
    return containsValue(this, target);
  }

  /**
   * Returns whether the tree has a value whose interval equals (if {@code exact})
   * or contains (otherwise) the interval of the target.
   *
   * @param target The value whose interval to look for
   * @param exact Whether the interval must be equal rather than just containing
   * @return Whether there is such a value
   */
  public boolean containsInterval(T target, boolean exact) {
    return containsInterval(this, target.getInterval(), exact);
  }

  /**
   * Returns whether the tree has a value whose interval equals (if {@code exact})
   * or contains (otherwise) the interval consisting of the point p.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param n The tree to search
   * @param p The point
   * @param exact Whether the interval must be equal rather than just containing
   * @return Whether there is such a value
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean containsInterval(IntervalTree<E,T> n, E p, boolean exact) {
    return containsInterval(n, Interval.toInterval(p, p), exact);
  }

  /**
   * Returns whether the tree has a value whose interval equals (if {@code exact})
   * or contains (otherwise) the target interval.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param node The tree to search
   * @param target The interval
   * @param exact Whether the interval must be equal rather than just containing
   * @return Whether there is such a value
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean containsInterval(IntervalTree<E,T> node, Interval<E> target, boolean exact) {
    Predicate<T> containsTargetFunction = new ContainsIntervalFunction(target, exact);
    return contains(node, target.getInterval(), containsTargetFunction);
  }

  /**
   * Returns whether the tree contains a value equal to the target.
   *
   * @param <E> The type of the interval endpoints
   * @param <T> The type of the values stored
   * @param node The tree to search
   * @param target The value to look for
   * @return Whether the value is in the tree
   */
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean containsValue(IntervalTree<E,T> node, T target) {
    Predicate<T> containsTargetFunction = new ContainsValueFunction(target);
    return contains(node, target.getInterval(), containsTargetFunction);
  }

  private static class ContainsValueFunction<E extends Comparable<E>, T extends HasInterval<E>>
      implements Predicate<T> {
    private T target;

    public ContainsValueFunction(T target) {
      this.target = target;
    }

    @Override
    public boolean test(T in) {
      return in.equals(target);
    }
  }

  private static class ContainsIntervalFunction<E extends Comparable<E>, T extends HasInterval<E>>
      implements Predicate<T> {
    private Interval<E> target;
    private boolean exact;

    public ContainsIntervalFunction(Interval<E> target, boolean exact) {
      this.target = target;
      this.exact = exact;
    }

    @Override
    public boolean test(T in) {
      if (exact) {
        return in.getInterval().equals(target);
      } else {
        return in.getInterval().contains(target);
      }
    }
  }

  private static <E extends Comparable<E>, T extends HasInterval<E>>
    boolean contains(IntervalTree<E,T> tree, Interval<E> target, Predicate<T> containsTargetFunction) {
    return contains(tree.root, target, containsTargetFunction);
  }

  private static <E extends Comparable<E>, T extends HasInterval<E>>
    boolean contains(TreeNode<E,T> node, Interval<E> target, Predicate<T> containsTargetFunction) {
    Stack<TreeNode<E,T>> todo = new Stack<>();
    todo.push(node);

    // Don't search nodes that don't exist
    while (!todo.isEmpty()) {
      TreeNode<E,T> n = todo.pop();
      // Don't search nodes that don't exist
      if (n == null || n.isEmpty()) continue;

      // If target is to the right of the rightmost point of any interval
      // in this node and all children, there won't be any matches.
      if (target.first.compareTo(n.maxEnd) > 0) {
        continue;
      }

      // Check this node
      if (containsTargetFunction.test(n.value))
        return true;

      if (n.left != null) {
        todo.push(n.left);
      }
      // If target is to the left of the start of this interval, then no need to search right
      if (target.second.compareTo(n.value.getInterval().first()) <= 0)  {
        continue;
      }

      // Need to check right children
      if (n.right != null)  {
        todo.push(n.right);
      }
    }
    return false;
  }

  /**
   * Returns the items whose intervals do not overlap the intervals of earlier items kept, going through
   * the items in order and keeping each one which does not overlap any item already kept.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @param toIntervalFunc The function giving the interval of each item
   * @return A new list of the items kept, in their original order
   */
  public static <T, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc)
  {
    List<T> nonOverlapping = new ArrayList<>();
    IntervalTree<E,Interval<E>> intervals = new IntervalTree<>();
    for (T item:items) {
      Interval<E> i = toIntervalFunc.apply(item);
      boolean addOk = intervals.addNonOverlapping(i);
      if (addOk) {
        nonOverlapping.add(item);
      }
    }
    return nonOverlapping;
  }

  /**
   * Returns the non-overlapping items chosen as in {@link #getNonOverlapping(List, Function)},
   * after sorting (a copy of) the items with the given comparator, so earlier items in that order are preferred.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @param toIntervalFunc The function giving the interval of each item
   * @param compareFunc The order in which to consider the items
   * @return A new list of the items kept, in the comparator's order
   */
  public static <T, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc, Comparator<? super T> compareFunc)
  {
    List<T> sorted = new ArrayList<>(items);
    Collections.sort(sorted, compareFunc);
    return getNonOverlapping(sorted, toIntervalFunc);
  }

  /**
   * Returns the non-overlapping items chosen as in {@link #getNonOverlapping(List, Function)},
   * after sorting (a copy of) the items with the given comparator, so earlier items in that order are preferred.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @param compareFunc The order in which to consider the items
   * @return A new list of the items kept, in the comparator's order
   */
  public static <T extends HasInterval<E>, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items, Comparator<? super T> compareFunc)
  {
    Function<T,Interval<E>> toIntervalFunc = in -> in.getInterval();
    return getNonOverlapping(items, toIntervalFunc, compareFunc);
  }

  /**
   * Returns the items whose intervals do not overlap the intervals of earlier items kept, going through
   * the items in order and keeping each one which does not overlap any item already kept.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @return A new list of the items kept, in their original order
   */
  public static <T extends HasInterval<E>, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items)
  {
    Function<T,Interval<E>> toIntervalFunc = in -> in.getInterval();
    return getNonOverlapping(items, toIntervalFunc);
  }

  private static class PartialScoredList<T,E> {
    T object;
    E lastMatchKey;
    int size;
    double score;
  }
  /**
   * Returns a set of items with non-overlapping intervals chosen to maximize the total score (preferring fewer
   * items when scores are tied), using dynamic programming over the interval ends. An item is only combined
   * with items ending at or before its beginning which come earlier in {@code items}.
   * If there are fewer than two items, a copy of the list is returned.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @param toIntervalFunc The function giving the interval of each item
   * @param scoreFunc The function giving the score of each item
   * @return A new list of the items chosen, in order of their intervals
   */
  public static <T, E extends Comparable<E>> List<T> getNonOverlappingMaxScore(
      List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc, ToDoubleFunction<? super T> scoreFunc)
  {
    if (items.size() > 1) {
      Map<E,PartialScoredList<T,E>> bestNonOverlapping = new TreeMap<>();
      for (T item:items) {
        Interval<E> itemInterval = toIntervalFunc.apply(item);
        E mBegin = itemInterval.getBegin();
        E mEnd = itemInterval.getEnd();
        PartialScoredList<T,E> bestk = bestNonOverlapping.get(mEnd);
        double itemScore = scoreFunc.applyAsDouble(item);
        if (bestk == null) {
          bestk = new PartialScoredList<>();
          bestk.size = 1;
          bestk.score = itemScore;
          bestk.object = item;
          bestNonOverlapping.put(mEnd, bestk);
        }
        // Assumes map is ordered
        for (E j:bestNonOverlapping.keySet()) {
          if (j.compareTo(mBegin) > 0) break;
          // Consider adding this match into the bestNonOverlapping strand at j
          PartialScoredList<T,E> bestj = bestNonOverlapping.get(j);
          double withMatchScore = bestj.score + itemScore;
          boolean better = false;
          if (withMatchScore > bestk.score) {
            better = true;
          } else if (withMatchScore == bestk.score) {
            if (bestj.size + 1 < bestk.size) {
              better = true;
            }
          }
          if (better) {
            bestk.size = bestj.size + 1;
            bestk.score = withMatchScore;
            bestk.object = item;
            bestk.lastMatchKey = j;
          }
        }
      }

      PartialScoredList<T,E> best = null;
      for (PartialScoredList<T,E> v: bestNonOverlapping.values()) {
        if (best == null || v.score > best.score) {
          best = v;
        }
      }
      List<T> nonOverlapping = new ArrayList<>(best.size);
      PartialScoredList<T,E> prev = best;
      while (prev != null) {
        if (prev.object != null) {
          nonOverlapping.add(prev.object);
        }
        if (prev.lastMatchKey != null) {
          prev = bestNonOverlapping.get(prev.lastMatchKey);
        } else {
          prev = null;
        }
      }
      Collections.reverse(nonOverlapping);
      return nonOverlapping;
    } else {
      List<T> nonOverlapping = new ArrayList<>(items);
      return nonOverlapping;
    }
  }
  /**
   * Returns a set of items with non-overlapping intervals chosen to maximize the total score, as in
   * {@link #getNonOverlappingMaxScore(List, Function, ToDoubleFunction)}.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @param scoreFunc The function giving the score of each item
   * @return A new list of the items chosen, in order of their intervals
   */
  public static <T extends HasInterval<E>, E extends Comparable<E>> List<T> getNonOverlappingMaxScore(
      List<? extends T> items, ToDoubleFunction<? super T> scoreFunc)
  {
    Function<T,Interval<E>> toIntervalFunc = in -> in.getInterval();
    return getNonOverlappingMaxScore(items, toIntervalFunc, scoreFunc);
  }

  /**
   * Returns the items whose intervals are not contained in the interval of an earlier item kept, going
   * through (a copy of) the items sorted with the given comparator and keeping each one whose interval
   * is not contained in the interval of an item already kept.
   *
   * @param <T> The type of the items
   * @param <E> The type of the interval endpoints
   * @param items The items
   * @param toIntervalFunc The function giving the interval of each item
   * @param compareFunc The order in which to consider the items
   * @return A new list of the items kept, in the comparator's order
   */
  public static <T, E extends Comparable<E>> List<T> getNonNested(
          List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc, Comparator<? super T> compareFunc)
  {
    List<T> sorted = new ArrayList<>(items);
    Collections.sort(sorted, compareFunc);
    List<T> res = new ArrayList<>();
    IntervalTree<E,Interval<E>> intervals = new IntervalTree<>();
    for (T item:sorted) {
      Interval<E> i = toIntervalFunc.apply(item);
      boolean addOk = intervals.addNonNested(i);
      if (addOk) {
        res.add(item);
      } else {
        //        log.info("Discarding " + item);
      }
    }
    return res;
  }

}
