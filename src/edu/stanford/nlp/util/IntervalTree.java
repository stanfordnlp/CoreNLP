package edu.stanford.nlp.util;

import java.util.*;

/**
 * An interval tree maintains a tree so that all intervals to the left start
 * before current interval and all intervals to the right start after.
 *
 * @author Angel Chang
 */
public class IntervalTree<E extends Comparable<E>, T extends HasInterval<E>> extends AbstractCollection<T>
{
  private static final double defaultAlpha = 0.65; // How balanced we want this tree (between 0.5 and 1.0)
  private static final boolean debug = false;

  private TreeNode<E,T> root = new TreeNode<E,T>();

  // Tree node
  public static class TreeNode<E extends Comparable<E>, T extends HasInterval<E>> {
    T value;
    E maxEnd;    // Maximum end in this subtree
    int size;

    TreeNode<E,T> left;
    TreeNode<E,T> right;

    TreeNode<E,T> parent; // Parent for convenience

    public boolean isEmpty() { return value == null; }

    public void clear() {
      value = null;
      maxEnd = null;
      size = 0;
      left = null;
      right = null;
//      parent = null;
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

  public boolean add(TreeNode<E,T> node, T target) {
    return add(node, target, defaultAlpha);
  }

  // Add node to tree - attempting to maintain alpha balance
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
            n.left = new TreeNode<E,T>();
            n.left.parent = n;
          }
          n = n.left;
        } else {
          // Should go on right
          if (n.right == null) {
            n.right = new TreeNode<E,T>();
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
    return new TreeNodeIterator<E,T>(root);
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
            curIter = (node.left != null)? new TreeNodeIterator<E,T>(node.left):null;
            break;
          case 1:
            curIter = null;
            return node.value;
          case 2:
            curIter = (node.right != null)? new TreeNodeIterator<E,T>(node.right):null;
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

  public boolean remove(T target) {
    return remove(root, target);
  }

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
        node.left = node.left.left;
        node.right = node.left.right;
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
          node.maxEnd = Interval.max(node.maxEnd, node.left.maxEnd);
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
          node.maxEnd = Interval.max(node.maxEnd, node.right.maxEnd);
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

  public void check() {
    check(root);
  }

  public void check(TreeNode<E,T> treeNode) {
    Stack<TreeNode<E,T>> todo = new Stack<TreeNode<E, T>>();
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


  public boolean isAlphaBalanced(TreeNode<E,T> node, double alpha) {
    int leftSize = (node.left != null)? node.left.size:0;
    int rightSize = (node.right != null)? node.right.size:0;
    int threshold = (int) (alpha*node.size) + 1;
    return (leftSize <= threshold) && (rightSize <= threshold);
  }

  public void balance() {
    root = balance(root);
  }

  // Balances this tree
  public TreeNode<E,T> balance(TreeNode<E,T> node) {
    if (debug) check(node);
    Stack<TreeNode<E,T>> todo = new Stack<TreeNode<E, T>>();
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

  // Moves this node up the tree until it replaces the target node
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

  // Moves this node to the right and the left child up and returns the new root
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

  // Moves this node to the left and the right child up and returns the new root
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

  public int height() { return height(root); }

  public int height(TreeNode<E,T> node) {
    if (node.value == null) return 0;
    int lh = (node.left != null)? height(node.left):0;
    int rh = (node.right != null)? height(node.right):0;
    return Math.max(lh,rh) + 1;
  }

  public TreeNode<E,T> getLeftmostNode(TreeNode<E,T> node)
  {
    TreeNode<E,T> n = node;
    while (n.left != null) {
      n = n.left;
    }
    return n;
  }

  public TreeNode<E,T> getRightmostNode(TreeNode<E,T> node)
  {
    TreeNode<E,T> n = node;
    while (n.right != null) {
      n = n.right;
    }
    return n;
  }

  // Returns ith node
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

  public boolean addNonOverlapping(T target)
  {
    if (overlaps(target)) return false;
    add(target);
    return true;
  }

  public boolean addNonNested(T target)
  {
    if (containsInterval(target, false)) return false;
    add(target);
    return true;
  }

  public boolean overlaps(T target) {
    return overlaps(root, target.getInterval());
  }

  public List<T> getOverlapping(T target) {
    return getOverlapping(root, target.getInterval());
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> List<T> getOverlapping(TreeNode<E,T> n, E p)
  {
    List<T> overlapping = new ArrayList<T>();
    getOverlapping(n, p, overlapping);
    return overlapping;
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> List<T> getOverlapping(TreeNode<E,T> n, Interval<E> target)
  {
    List<T> overlapping = new ArrayList<T>();
    getOverlapping(n, target, overlapping);
    return overlapping;
  }

  // Search for all intervals which contain p, starting with the
  // node "n" and adding matching intervals to the list "result"
  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(TreeNode<E,T> n, E p, List<T> result) {
    getOverlapping(n, Interval.toInterval(p,p), result);
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(TreeNode<E,T> node, Interval<E> target, List<T> result) {
    Queue<TreeNode<E,T>> todo = new LinkedList<TreeNode<E, T>>();
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

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(TreeNode<E,T> n, E p) {
    return overlaps(n, Interval.toInterval(p,p));
  }
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(TreeNode<E,T> node, Interval<E> target) {
    Stack<TreeNode<E,T>> todo = new Stack<TreeNode<E, T>>();
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

  public boolean contains(T target) {
    return containsValue(this, target);
  }

  public boolean containsInterval(T target, boolean exact) {
    return containsInterval(this, target.getInterval(), exact);
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean containsInterval(IntervalTree<E,T> n, E p, boolean exact) {
    return containsInterval(n, Interval.toInterval(p, p), exact);
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean containsInterval(IntervalTree<E,T> node, Interval<E> target, boolean exact) {
    Function<T,Boolean> containsTargetFunction = new ContainsIntervalFunction(target, exact);
    return contains(node, target.getInterval(), containsTargetFunction);
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean containsValue(IntervalTree<E,T> node, T target) {
    Function<T,Boolean> containsTargetFunction = new ContainsValueFunction(target);
    return contains(node, target.getInterval(), containsTargetFunction);
  }

  private static class ContainsValueFunction<E extends Comparable<E>, T extends HasInterval<E>>
      implements Function<T,Boolean> {
    private T target;

    public ContainsValueFunction(T target) {
      this.target = target;
    }

    @Override
    public Boolean apply(T in) {
      return in.equals(target);
    }
  }

  private static class ContainsIntervalFunction<E extends Comparable<E>, T extends HasInterval<E>>
      implements Function<T,Boolean> {
    private Interval<E> target;
    private boolean exact;

    public ContainsIntervalFunction(Interval<E> target, boolean exact) {
      this.target = target;
      this.exact = exact;
    }

    @Override
    public Boolean apply(T in) {
      if (exact) {
        return in.getInterval().equals(target);
      } else {
        return in.getInterval().contains(target);
      }
    }
  }

  private static <E extends Comparable<E>, T extends HasInterval<E>>
    boolean contains(IntervalTree<E,T> tree, Interval<E> target, Function<T,Boolean> containsTargetFunction) {
    return contains(tree.root, target, containsTargetFunction);
  }

  private static <E extends Comparable<E>, T extends HasInterval<E>>
    boolean contains(TreeNode<E,T> node, Interval<E> target, Function<T,Boolean> containsTargetFunction) {
    Stack<TreeNode<E,T>> todo = new Stack<TreeNode<E,T>>();
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
      if (containsTargetFunction.apply(n.value))
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

  public static <T, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc)
  {
    List<T> nonOverlapping = new ArrayList<T>();
    IntervalTree<E,Interval<E>> intervals = new IntervalTree<E, Interval<E>>();
    for (T item:items) {
      Interval<E> i = toIntervalFunc.apply(item);
      boolean addOk = intervals.addNonOverlapping(i);
      if (addOk) {
        nonOverlapping.add(item);
      }
    }
    return nonOverlapping;
  }

  public static <T, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc, Comparator<? super T> compareFunc)
  {
    List<T> sorted = new ArrayList<T>(items);
    Collections.sort(sorted, compareFunc);
    return getNonOverlapping(sorted, toIntervalFunc);
  }

  public static <T extends HasInterval<E>, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items, Comparator<? super T> compareFunc)
  {
    Function<T,Interval<E>> toIntervalFunc = new Function<T, Interval<E>>() {
      @Override
      public Interval<E> apply(T in) {
        return in.getInterval();
      }
    };
    return getNonOverlapping(items, toIntervalFunc, compareFunc);
  }

  public static <T extends HasInterval<E>, E extends Comparable<E>> List<T> getNonOverlapping(
          List<? extends T> items)
  {
    Function<T,Interval<E>> toIntervalFunc = new Function<T, Interval<E>>() {
      @Override
      public Interval<E> apply(T in) {
        return in.getInterval();
      }
    };
    return getNonOverlapping(items, toIntervalFunc);
  }

  private static class PartialScoredList<T,E> {
    T object;
    E lastMatchKey;
    int size;
    double score;
  }
  public static <T, E extends Comparable<E>> List<T> getNonOverlappingMaxScore(
      List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc, Function<? super T, Double> scoreFunc)
  {
    if (items.size() > 1) {
      Map<E,PartialScoredList<T,E>> bestNonOverlapping = new TreeMap<E,PartialScoredList<T,E>>();
      for (T item:items) {
        Interval<E> itemInterval = toIntervalFunc.apply(item);
        E mBegin = itemInterval.getBegin();
        E mEnd = itemInterval.getEnd();
        PartialScoredList<T,E> bestk = bestNonOverlapping.get(mEnd);
        double itemScore = scoreFunc.apply(item);
        if (bestk == null) {
          bestk = new PartialScoredList<T,E>();
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
      List<T> nonOverlapping = new ArrayList<T>(best.size);
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
      List<T> nonOverlapping = new ArrayList<T>(items);
      return nonOverlapping;
    }
  }
  public static <T extends HasInterval<E>, E extends Comparable<E>> List<T> getNonOverlappingMaxScore(
      List<? extends T> items, Function<? super T, Double> scoreFunc)
  {
    Function<T,Interval<E>> toIntervalFunc = new Function<T, Interval<E>>() {
      @Override
      public Interval<E> apply(T in) {
        return in.getInterval();
      }
    };
    return getNonOverlappingMaxScore(items, toIntervalFunc, scoreFunc);
  }

  public static <T, E extends Comparable<E>> List<T> getNonNested(
          List<? extends T> items, Function<? super T,Interval<E>> toIntervalFunc, Comparator<? super T> compareFunc)
  {
    List<T> sorted = new ArrayList<T>(items);
    Collections.sort(sorted, compareFunc);
    List<T> res = new ArrayList<T>();
    IntervalTree<E,Interval<E>> intervals = new IntervalTree<E, Interval<E>>();
    for (T item:sorted) {
      Interval<E> i = toIntervalFunc.apply(item);
      boolean addOk = intervals.addNonNested(i);
      if (addOk) {
        res.add(item);
      } else {
        //        System.err.println("Discarding " + item);
      }
    }
    return res;
  }

}
