package edu.stanford.nlp.parser.lexparser;

import java.util.Collection;
import java.util.Iterator;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.trees.Tree;

/**
 * @author grenager
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */

public interface Extractor<T> {
  public T extract(Collection<Tree> trees);

  public T extract(Iterator<Tree> iterator, Function<Tree, Tree> f);
}

