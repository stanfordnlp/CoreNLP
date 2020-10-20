package edu.stanford.nlp.trees;

import java.io.File;
import java.io.FileFilter;
import java.util.Iterator;
import java.util.Collection;

/**
 * @author Galen Andrew
 */
public class CompositeTreebank extends Treebank {
  private Treebank t1;
  private Treebank t2;

  public CompositeTreebank(Treebank t1, Treebank t2) {
    this.t1 = t1;
    this.t2 = t2;
  }

  @Override
  public void clear() {
    t1.clear();
    t2.clear();
  }

  @Override
  public void loadPath(File path, FileFilter filt) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void apply(TreeVisitor tp) {
    for (Tree tree : this) {
      tp.visitTree(tree);
    }
  }


  @Override
  public Iterator<Tree> iterator() {
    return new CompositeTreebankIterator(t1, t2);
  }


  private static class CompositeTreebankIterator implements Iterator<Tree> {

    private final Iterator<Tree> it1;
    private final Iterator<Tree> it2;

    public CompositeTreebankIterator(Collection<Tree> c1, Collection<Tree> c2) {
      it1 = c1.iterator();
      it2 = c2.iterator();
    }

    public boolean hasNext() {
      return (it1.hasNext() || it2.hasNext());
    }

    public Tree next() {
      Tree tree = it1.hasNext() ? it1.next() : it2.next();
      return tree;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

  } // end static class CompositeTreebankIterator

}
