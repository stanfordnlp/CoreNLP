package edu.stanford.nlp.trees.international.tuebadz;

import java.io.Serializable;
import java.io.Reader;
import java.util.*;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.StringLabelFactory;


/** @author Christopher Manning */
public class TueBaDZTreeReaderFactory implements TreeReaderFactory, Serializable {

  private static final long serialVersionUID = 1614799885744961795L;

  private TreebankLanguagePack tlp;
  private int nodeCleanup;

  public TueBaDZTreeReaderFactory(TreebankLanguagePack tlp) {
    this(tlp, 0);
  }

  public TueBaDZTreeReaderFactory(TreebankLanguagePack tlp, int nodeCleanup) {
    this.tlp = tlp;
    this.nodeCleanup = nodeCleanup;
  }

  public TreeReader newTreeReader(Reader in) {
    final TreeNormalizer tn1 = new GrammaticalFunctionTreeNormalizer(tlp, nodeCleanup);
    final TueBaDZPennTreeNormalizer tn2 = new TueBaDZPennTreeNormalizer(tlp, nodeCleanup);
    final TreeNormalizer norm = new OrderedCombinationTreeNormalizer(Arrays.asList(tn1, tn2));

    return new PennTreeReader(in, new LabeledScoredTreeFactory(new StringLabelFactory()), norm);
  }

} // end class TueBaDZTreeReaderFactory
