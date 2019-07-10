package edu.stanford.nlp.trees;

import java.io.File;
import java.io.FileFilter;
import java.util.Iterator;
import java.util.function.Predicate;

/** 
 * This class wraps another Treebank, and will vend trees that passed
 * a Filter&lt;Tree&gt;.
 *
 *  @author John Bauer
 */
public class FilteringTreebank extends Treebank {
  private Predicate<Tree> filter;
  private Treebank treebank;

  private static final boolean VERBOSE = false;

  public FilteringTreebank(Treebank treebank, Predicate<Tree> filter) {
    this.filter = filter;
    this.treebank = treebank;
  }

  /**
   * Empty a <code>Treebank</code>.
   */
  @Override
  public void clear() {
    treebank.clear();
    filter = null;
  }

  /**
   * Load trees from given path specification.  Passes the path and
   * filter to the underlying treebank.
   *
   * @param path file or directory to load from
   * @param filt a FilenameFilter of files to load
   */
  @Override
  public void loadPath(File path, FileFilter filt) {
    treebank.loadPath(path, filt);
  }

  /**
   * Applies the TreeVisitor, but only to the trees that pass the
   * filter.  Applies the visitor to a copy of the tree.
   *
   * @param tv A class that can process trees.
   */
  @Override
  public void apply(TreeVisitor tv) {
    if (VERBOSE) {
      System.out.println("Applying " + tv + " to treebank");
    }
    for (Tree t : treebank) {
      if (!filter.test(t)) {
        if (VERBOSE) System.out.println("  Skipping " + t);
        continue;
      }
      Tree tmpT = t.deepCopy();
      if (VERBOSE) System.out.println("  Applying to " + tmpT);
      tv.visitTree(tmpT);
    }
  }

  /**
   */
  @Override
  public Iterator<Tree> iterator() {
    return new FilteringTreebankIterator(treebank.iterator(), filter);
  }


  private static class FilteringTreebankIterator implements Iterator<Tree> {
    private Iterator<Tree> iter;
    private Predicate<Tree> filter;

    Tree next;

    FilteringTreebankIterator (Iterator<Tree> iter, Predicate<Tree> filter) {
      this.iter = iter;
      this.filter = filter;
      primeNext();
    }

    public boolean hasNext() {
      return (next != null);
    }

    public Tree next() {
      Tree answer = next;
      primeNext();
      return answer;
    }

    public void primeNext() {
      while (iter.hasNext()) {
        next = iter.next();
        if (filter.test(next)) {
          return;
        }
      }
      next = null;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}

