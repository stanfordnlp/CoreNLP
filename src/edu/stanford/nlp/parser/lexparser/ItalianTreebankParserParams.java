package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LeftHeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.PennTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.italian.ItalianTreebankLanguagePack;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Bare-bones implementation of a ParserParams for the Italian Turin treebank.
 * <br>
 * Suitable for use in the SR Parser.  Will need additional work to function in the PCFG.
 * Also, would likely function better with a new headfinder.
 */
public class ItalianTreebankParserParams extends AbstractTreebankParserParams  {
  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ItalianTreebankParserParams.class);

  public ItalianTreebankParserParams() {
    super(new ItalianTreebankLanguagePack());
    // TODO: make a Italian specific HeadFinder or build one that can be learned
    headFinder = new LeftHeadFinder();
  }

  private HeadFinder headFinder;

  private TreeNormalizer normalizer = null;

  static final String[] EMPTY_SISTERS = new String[0];

  @Override
  public HeadFinder headFinder() {
    return headFinder;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder;
  }

  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(tlp, true, false, 0);
  }

  @Override
  public AbstractCollinizer collinizerEvalb() {
    return collinizer();
  }

  @Override
  public String[] sisterSplitters() {
    // TODO: the SR Parser does not use this code path, so it is not implemented
    return EMPTY_SISTERS;
  }

  @Override
  public Tree transformTree(Tree t, Tree root) {
    // TODO: the SR Parser does not use this code path, so it is not implemented
    return t;
  }

  public static class ItalianSubcategoryStripper extends TreeNormalizer {
    @Override
    public String normalizeNonterminal(String category) {
      // The stanza script leaves the fancy endings on the tags
      // but simplifies the constiituency tags
      List<String> pieces = StringUtils.split(category, "~");

      return pieces.get(0);
    }
  }

  TreeNormalizer buildNormalizer() {
    return new ItalianSubcategoryStripper();
  }

  /** {@inheritDoc} */
  @Override
  public TreeReaderFactory treeReaderFactory() {
    if (normalizer == null) {
      normalizer = buildNormalizer();
    }
    return new PennTreeReaderFactory(normalizer);
  }


  @Override
  public void display() {
    String params = "Using ItalianTreebankParserParams";
    log.info(params);
  }

  /** {@inheritDoc} */
  @Override
  public List<? extends HasWord> defaultTestSentence() {
    List<Word> ret = new ArrayList<>();
    String[] sent = {"Questo", "è", "un", "test", "."};
    for (String str : sent) {
      ret.add(new Word(str));
    }
    return ret;
  }

  private static final long serialVersionUID = 9824524678L;
}
