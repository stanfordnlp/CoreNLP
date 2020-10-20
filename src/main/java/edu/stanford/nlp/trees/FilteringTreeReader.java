package edu.stanford.nlp.trees;

import java.io.IOException;

import java.util.function.Predicate;

/**
 * A <code>FilteringTreeReader</code> filters the output of another TreeReader.
 * It applies a Filter&lt;Tree&gt; to each returned tree and only returns trees 
 * that are accepted by the Filter.  The Filter should accept trees that it 
 * wants returned.
 *
 * @author Christopher Manning
 * @version 2006/11
 */
public class FilteringTreeReader implements TreeReader {

  private TreeReader tr;
  private Predicate<Tree> f;

  public FilteringTreeReader(TreeReader tr, Predicate<Tree> f) {
    this.tr = tr;
    this.f = f;
  }

  /**
   * Reads a single tree.
   *
   * @return A single tree, or <code>null</code> at end of file.
   */
  public Tree readTree() throws IOException {
    Tree t;
    do {
      t = tr.readTree();
    } while (t != null && ! f.test(t));
    return t;
  }

  /**
   * Close the Reader behind this <code>TreeReader</code>.
   */
  public void close() throws IOException {
    tr.close();
  }

}
