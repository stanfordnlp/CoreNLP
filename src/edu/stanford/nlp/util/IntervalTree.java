package edu.stanford.nlp.util;

import java.util.*;

/**
 * Interval tree
 * Maintain tree so all intervals to the left starts before current interval
 * and all intervals to the right starts after
 *
 * @author Angel Chang
 */
public class IntervalTree<E extends Comparable<E>, T extends HasInterval<E>> extends AbstractCollection<T>
{
  // Tree node
  T value;
  E maxEnd;    // Maximum end in this subtree
  int size;

  IntervalTree<E,T> left;
  IntervalTree<E,T> right;

  IntervalTree<E,T> parent; // Parent for convenience

  @Override
  public boolean isEmpty() { return value == null; }

  @Override
  public void clear() {
    value = null;
    maxEnd = null;
    size = 0;
    left = null;
    right = null;
//    parent = null;
  }

  public String toString() {
    return "Size: " + this.size;
  }

  @Override
  public boolean add(T target) {
    if (target == null) return false;
    if (value == null) {
      this.value = target;
      this.maxEnd = target.getInterval().getEnd();
      this.size = 1;
      return true;
    } else {
      this.maxEnd = Interval.max(maxEnd, target.getInterval().getEnd());
      this.size++;
      if (target.getInterval().getBegin().compareTo(value.getInterval().getBegin()) <= 0) {
        // Should go on left
        if (left == null) {
          left = new IntervalTree<E,T>();
          left.parent = this;
        }
        return left.add(target);
      } else {
        // Should go on right
        if (right == null) {
          right = new IntervalTree<E,T>();
          right.parent = this;
        }
        return right.add(target);
      }
    }
  }

  @Override
  public int size()
  {
    return size;
  }

  @Override
  public Iterator<T> iterator() {
    throw new UnsupportedOperationException("iterator not supported");
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
    return false;
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

  public boolean remove(T target)
  {
    if (target == null) return false;
    if (value == null) return false;
    if (target.equals(value)) {
      int leftSize = (left != null)? left.size():0;
      int rightSize = (right != null)? right.size():0;
      if (leftSize == 0) {
        if (rightSize == 0) {
          value = null;
          size = 0;
        } else {
          value = right.value;
          size = right.size;
          maxEnd = right.maxEnd;
          left = right.left;
          right = right.right;
          if (left != null) left.parent = this;
          if (right != null) right.parent = this;
        }
      } else if (rightSize == 0) {
        value = left.value;
        size = left.size;
        maxEnd = left.maxEnd;
        left = left.left;
        right = left.right;
        if (left != null) left.parent = this;
        if (right != null) right.parent = this;
      } else {
        // Rotate left up
        value = left.value;
        size--;
        maxEnd = Interval.max(left.maxEnd, right.maxEnd);
        IntervalTree<E,T> origRight = right;
        right = left.right;
        left = left.left;
        if (left != null) left.parent = this;
        if (right != null) right.parent = this;

        // Attach origRight somewhere...
        IntervalTree<E,T> rightmost = getRightmostNode();
        rightmost.right = origRight;
        if (rightmost.right != null) {
          rightmost.right.parent = rightmost;
          // adjust maxEnd and sizes on the right
          adjustUpwards(rightmost.right);
        }
      }
      return true;
    } else {
      if (target.getInterval().getBegin().compareTo(value.getInterval().getBegin()) <= 0) {
        // Should go on left
        if (left == null) {
          return false;
        }
        boolean res = left.remove(target);
        if (res) {
          this.maxEnd = Interval.max(maxEnd, left.maxEnd);
          this.size--;
        }
        return res;
      } else {
        // Should go on right
        if (right == null) {
          return false;
        }
        boolean res = right.remove(target);
        if (res) {
          this.maxEnd = Interval.max(maxEnd, right.maxEnd);
          this.size--;
        }
        return res;
      }
    }
  }

  private void adjustUpwards(IntervalTree<E,T> node) {
    adjustUpwards(node, null);
  }

  private void adjustUpwards(IntervalTree<E,T> node, IntervalTree<E,T> stopAt) {
    IntervalTree<E,T> n = node;
    while (n != null && node != stopAt) {
      int leftSize = (n.left != null)? n.left.size():0;
      int rightSize = (n.right != null)? n.right.size():0;
      n.maxEnd = n.value.getInterval().getEnd();
      if (n.left != null) {
        n.maxEnd = Interval.max(n.maxEnd, n.left.maxEnd);
      }
      if (n.right != null) {
        n.maxEnd = Interval.max(n.maxEnd, n.right.maxEnd);
      }
      n.size = leftSize + 1 + rightSize;
      if (n == n.parent) {
         throw new IllegalArgumentException("node is same as parent!!!");
      }
      n = n.parent;
    }
  }

  public void check() {
    Stack<IntervalTree<E,T>> todo = new Stack<IntervalTree<E, T>>();
    todo.add(this);
    while (!todo.isEmpty()) {
      IntervalTree<E,T> node = todo.pop();
      if (node == node.parent) {
        throw new IllegalArgumentException("node is same as parent!!!");
      }
      if (node.isEmpty()) {
        if (node.left != null) throw new IllegalStateException("Empty node shouldn't have left branch");
        if (node.right != null) throw new IllegalStateException("Empty node shouldn't have right branch");
        continue;
      }
      int leftSize = (node.left != null)? node.left.size():0;
      int rightSize = (node.right != null)? node.right.size():0;
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
        throw new IllegalArgumentException("max end is not as expected!!!");
      }
      if (node.size != leftSize + rightSize + 1) {
        throw new IllegalArgumentException("node size is not one plus the sum of left and right!!!");
      }
      if (node.left != null) {
        if (node.left.parent != node) {
          throw new IllegalArgumentException("node left parent is not same as node!!!");
        }
      }
      if (node.right != null) {
        if (node.right.parent != node) {
          throw new IllegalArgumentException("node right parent is not same as node!!!");
        }
      }
      if (node.left != null) todo.add(node.left);
      if (node.right != null) todo.add(node.right);
    }
  }


  // Balances this tree
  public void balance() {
    Stack<IntervalTree<E,T>> todo = new Stack<IntervalTree<E, T>>();
    todo.add(this);
    while (!todo.isEmpty()) {
      IntervalTree<E,T> node = todo.pop();
      // Balance tree between this node
      // Select median nodes and try to balance the tree
      int medianAt = node.size/2;
      IntervalTree<E,T> median = node.getNode(medianAt);
      // Okay, this is going to be our root
      if (median != null && median != node) {
        // Yes, there is indeed something to be done
        int leftSize = (node.left != null)? node.left.size():0;
        int rightSize = (node.right != null)? node.right.size():0;

        // Copy this somewhere
        IntervalTree<E,T> copy = new IntervalTree<E,T>();
        copy.left = node.left;
        copy.right = node.right;
        copy.value = node.value;
        if (copy.left != null) copy.left.parent = copy;
        if (copy.right != null) copy.right.parent = copy;

        // Let's take values in the median node and make it this
        node.value = median.value;
        node.left = median.left;
        node.right = median.right;
        // Node should have the same maxEnd and size as before
        if (node.left != null) node.left.parent = node;
        if (node.right != null) node.right.parent = node;

        // Cut median off from the parent
        IntervalTree<E,T> mparent = median.parent;
        median.parent = null;
        if (mparent != null) {
          if (mparent.left == median) {
            mparent.left = null;
          } else if (mparent.right == median) {
            mparent.right = null;
          }
          adjustUpwards(mparent,node);
        }

        if (medianAt < leftSize) {
          // median was from the left branch
          // take old node with left branch down to median's parent, old node, and right branch
          // and have that be our rightmost branch
          IntervalTree<E,T> rightmost = node.getRightmostNode();
          rightmost.right = copy;
          copy.parent = rightmost;
          // adjust maxEnd and sizes on the right
          adjustUpwards(rightmost.right,node);
        } else if (medianAt > leftSize) {
          // median was from the right branch
          // take old node with right branch down to median's parent, old node, and left branch
          // and have that be our new left branch
          IntervalTree<E,T> leftmost = node.getLeftmostNode();
          leftmost.left = copy;
          copy.parent = leftmost;
          // adjust maxEnd and sizes on the right
          adjustUpwards(leftmost.left,node);
        } else if (medianAt == leftSize) {
          throw new RuntimeException("Shouldn't be here...");
        }
      }
      if (node.left != null) todo.push(node.left);
      if (node.right != null) todo.push(node.right);
    }
  }

  public int height() {
    if (value == null) return 0;
    int lh = (left != null)? left.height():0;
    int rh = (right != null)? right.height():0;
    return Math.max(lh,rh) + 1;
  }

  public IntervalTree<E,T> getLeftmostNode()
  {
    IntervalTree<E,T> n = this;
    while (n.left != null) {
      n = n.left;
    }
    return n;
  }

  public IntervalTree<E,T> getRightmostNode()
  {
    IntervalTree<E,T> n = this;
    while (n.right != null) {
      n = n.right;
    }
    return n;
  }

  // Returns ith node
  public IntervalTree<E,T> getNode(int nodeIndex) {
    int i = nodeIndex;
    IntervalTree<E,T> n = this;
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
    return overlaps(this, target.getInterval());
  }

  public List<T> getOverlapping(T target) {
    return getOverlapping(this, target.getInterval());
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> List<T> getOverlapping(IntervalTree<E,T> n, E p)
  {
    List<T> overlapping = new ArrayList<T>();
    getOverlapping(n, p, overlapping);
    return overlapping;
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> List<T> getOverlapping(IntervalTree<E,T> n, Interval<E> target)
  {
    List<T> overlapping = new ArrayList<T>();
    getOverlapping(n, target, overlapping);
    return overlapping;
  }

  // Search for all intervals which contain p, starting with the
  // node "n" and adding matching intervals to the list "result"
  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(IntervalTree<E,T> n, E p, List<T> result) {
    getOverlapping(n, Interval.toInterval(p,p), result);
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(IntervalTree<E,T> node, Interval<E> target, List<T> result) {
    Queue<IntervalTree<E,T>> todo = new LinkedList<IntervalTree<E, T>>();
    todo.add(node);
    while (!todo.isEmpty()) {
      IntervalTree<E,T> n = todo.poll();
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

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(IntervalTree<E,T> n, E p) {
    return overlaps(n, Interval.toInterval(p,p));
  }
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(IntervalTree<E,T> node, Interval<E> target) {
    IntervalTree<E,T> n = node;

    // Don't search nodes that don't exist
    while (n != null && !n.isEmpty()) {
      IntervalTree<E,T> next = null;
      // If target is to the right of the rightmost point of any interval
      // in this node and all children, there won't be any matches.
      if (target.first.compareTo(n.maxEnd) > 0)
          return false;

      // Check this node
      if (n.value.getInterval().overlaps(target)) {
          return true;
      }

      // If target is to the left of the start of this interval, then search left
      if (target.second.compareTo(n.value.getInterval().first()) <= 0)  {
         // Search left children
        if (n.left != null) {
          next = n.left;
        }
      } else {
         if (n.right != null)  {
             next = n.right;
         }
      }
      n = next;
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
    boolean contains(IntervalTree<E,T> node, Interval<E> target, Function<T,Boolean> containsTargetFunction) {
    IntervalTree<E,T> n = node;

    // Don't search nodes that don't exist
    while (n != null && !n.isEmpty()) {
      IntervalTree<E,T> next = null;

      // If target is to the right of the rightmost point of any interval
      // in this node and all children, there won't be any matches.
      if (target.first.compareTo(n.maxEnd) > 0)
        return false;

      // Check this node
      if (containsTargetFunction.apply(n.value))
        return true;

      // If target is to the left of the start of this interval, then search left
      if (target.second.compareTo(n.value.getInterval().first()) <= 0)  {
        // Search left children
        if (n.left != null) {
          next = n.left;
        }
      } else {
        if (n.right != null)  {
          next = n.right;
        }
      }
      n = next;
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
      public Interval<E> apply(T in) {
        return in.getInterval();
      }
    };
    return getNonOverlapping(items, toIntervalFunc);
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
