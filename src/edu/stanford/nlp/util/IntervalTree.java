package edu.stanford.nlp.util;

import java.util.*;

/**
 * Interval tree
 * Maintain tree so all intervals to the left starts before current interval
 * and all intervals to the right starts after
 *
 * @author Angel Chang
 */
public class IntervalTree<E extends Comparable<E>, T extends HasInterval<E>>
{
  // Tree node
  T value;
  E maxEnd;    // Maximum end in this subtree
  int size;

  IntervalTree<E,T> left;
  IntervalTree<E,T> right;

  public boolean isEmpty() { return value == null; }

  public void addAll(Collection<T> c) {
    for (T t:c) {
      add(t);
    }
  }

  public void add(T target) {
    if (target == null) return;
    if (value == null) {
      this.value = target;
      this.maxEnd = target.getInterval().getEnd();
      this.size = 1;
    } else {
      this.maxEnd = Interval.max(maxEnd, target.getInterval().getEnd());
      this.size++;
      if (target.getInterval().getBegin().compareTo(value.getInterval().getBegin()) <= 0) {
        // Should go on left
        if (left == null) {
          left = new IntervalTree<E,T>();
        }
        left.add(target);
      } else {
        // Should go on right
        if (right == null) {
          right = new IntervalTree<E,T>();
        }
        right.add(target);
      }
    }
  }

  public int size()
  {
    return size;
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
        }
      } else if (rightSize == 0) {
        value = left.value;
        size = left.size;
        maxEnd = left.maxEnd;
        left = left.left;
        right = left.right;
      } else {
        // Rotate left up
        value = left.value;
        size--;
        maxEnd = Interval.max(left.maxEnd, right.maxEnd);
        IntervalTree<E,T> origRight = right;
        right = left.right;
        left = left.left;
        // Attach origRight somewhere...
        IntervalTree<E,T> rightmost = getRightmostNode();
        rightmost.right = origRight;
        //add(right, origRight);
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

  public IntervalTree<E,T> getLeftmostNode()
  {
    if (left == null) return this;
    else { return left.getLeftmostNode(); }
  }

  public IntervalTree<E,T> getRightmostNode()
  {
    if (right == null) return this;
    else { return right.getRightmostNode(); }
  }

//  public Iterator<Interval<E>> iterator()
//  {
//    return null;
//  }

  public boolean addNonOverlapping(T target)
  {
    if (overlaps(target)) return false;
    add(target);
    return true;
  }

  public boolean addNonNested(T target)
  {
    if (contains(target)) return false;
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

  public static <E extends Comparable<E>, T extends HasInterval<E>> void getOverlapping(IntervalTree<E,T> n, Interval<E> target, List<T> result) {
    // Don't search nodes that don't exist
    if (n == null || n.isEmpty())
        return;

    // If target is to the right of the rightmost point of any interval
    // in this node and all children, there won't be any matches.
    if (target.first.compareTo(n.maxEnd) > 0)
        return;

    // Search left children
    if (n.left != null) {
        getOverlapping(n.left, target, result);
    }

    // Check this node
    if (n.value.getInterval().overlaps(target)) {
        result.add(n.value);
    }

    // If target is to the left of the start of this interval,
    // then it can't be in any child to the right.
    if (target.second.compareTo(n.value.getInterval().first()) < 0)  {
        return;
    }

    // Otherwise, search right children
    if (n.right != null)  {
        getOverlapping(n.right, target, result);
    }
  }
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(IntervalTree<E,T> n, E p) {
    return overlaps(n, Interval.toInterval(p,p));
  }
  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean overlaps(IntervalTree<E,T> n, Interval<E> target) {
    // Don't search nodes that don't exist
    if (n == null || n.isEmpty())
        return false;

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
        return overlaps(n.left, target);
      }
    } else {
       if (n.right != null)  {
           return overlaps(n.right, target);
       }
    }

    return false;
  }

  public boolean contains(T target) {
    return contains(this, target.getInterval());
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean contains(IntervalTree<E,T> n, E p) {
    return contains(n, Interval.toInterval(p, p));
  }

  public static <E extends Comparable<E>, T extends HasInterval<E>> boolean contains(IntervalTree<E,T> n, Interval<E> target) {
    // Don't search nodes that don't exist
    if (n == null || n.isEmpty())
      return false;

    // If target is to the right of the rightmost point of any interval
    // in this node and all children, there won't be any matches.
    if (target.first.compareTo(n.maxEnd) > 0)
      return false;

    // Check this node
    if (n.value.getInterval().contains(target)) {
      return true;
    }

    // If target is to the left of the start of this interval, then search left
    if (target.second.compareTo(n.value.getInterval().first()) <= 0)  {
      // Search left children
      if (n.left != null) {
        return contains(n.left, target);
      }
    } else {
      if (n.right != null)  {
        return contains(n.right, target);
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
