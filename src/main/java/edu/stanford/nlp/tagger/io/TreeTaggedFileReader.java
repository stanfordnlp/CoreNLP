package edu.stanford.nlp.tagger.io;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.LabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;

public class TreeTaggedFileReader implements TaggedFileReader {

  private final Treebank treebank;
  private final String filename;
  private final TreeReaderFactory trf;
  private final TreeTransformer transformer;
  private final TreeNormalizer normalizer;
  private final Predicate<Tree> treeFilter;

  private final Iterator<Tree> treeIterator;

  private Tree next = null;

  // int numSentences = 0;

  public TreeTaggedFileReader(TaggedFileRecord record) {
    filename = record.filename();
    trf = record.trf == null ? new LabeledScoredTreeReaderFactory() : record.trf;
    transformer = record.treeTransformer;
    normalizer = record.treeNormalizer;
    treeFilter = record.treeFilter;

    treebank = new DiskTreebank(trf, record.encoding);
    if (record.treeRange != null) {
      treebank.loadPath(filename, record.treeRange);
    } else {
      treebank.loadPath(filename);
    }

    treeIterator = treebank.iterator();
    findNext();
  }

  @Override
  public Iterator<List<TaggedWord>> iterator() { return this; }

  @Override
  public String filename() { return filename; }

  @Override
  public boolean hasNext() { return next != null; }

  @Override
  public List<TaggedWord> next() {
    if (next == null) {
      throw new NoSuchElementException("Iterator exhausted.");
    }
    Tree t = next;
    if (normalizer != null) {
      t = normalizer.normalizeWholeTree(t, t.treeFactory());
    }
    if (transformer != null) {
      t = t.transform(transformer);
    }
    findNext();
    return t.taggedYield();
  }

  /**
   * Skips ahead in the iterator to the next non-filtered tree.
   */
  private void findNext() {
    while (treeIterator.hasNext()) {
      next = treeIterator.next();
      if (treeFilter == null || treeFilter.test(next)) {
        return;
      }
    }
    next = null;
  }

  @Override
  public void remove() { throw new UnsupportedOperationException(); }

}
