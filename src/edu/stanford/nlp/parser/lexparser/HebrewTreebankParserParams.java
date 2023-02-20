package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LeftHeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.international.hebrew.HebrewTreeReaderFactory;
import edu.stanford.nlp.trees.international.hebrew.HebrewTreebankLanguagePack;

/**
 * Initial version of a parser pack for the HTB. Not yet integrated
 * into the Stanford parser.
 * <p>
 * This package assumes the romanized orthographic form of Hebrew as
 * used in the treebank. 
 * 
 * @author Spence Green
 *
 */
public class HebrewTreebankParserParams extends AbstractTreebankParserParams  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(HebrewTreebankParserParams.class);

  private static final long serialVersionUID = -3466519995341208619L;

  private final StringBuilder optionsString;
  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  public HebrewTreebankParserParams() {
    this(new HebrewTreebankLanguagePack());
  }

  protected HebrewTreebankParserParams(TreebankLanguagePack tlp) {
    super(tlp);
    optionsString = new StringBuilder();
    optionsString.append("HebrewTreebankParserParams\n");
  }

  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(tlp, true, false);
  }

  /**
   * Stand-in collinizer does nothing to the tree.
   */
  @Override
  public AbstractCollinizer collinizerEvalb() {
    return collinizer();
  }

  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), inputEncoding);
  }

  @Override
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory(), inputEncoding);
  }

  @Override
  public void display() {
    log.info(optionsString.toString());
  }

  //TODO Add Reut's rules (from her thesis).
  @Override
  public HeadFinder headFinder() {
    return new LeftHeadFinder();
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder();
  }

  @Override
  public String[] sisterSplitters() {
    return EMPTY_STRING_ARRAY;
  }

  @Override
  public Tree transformTree(Tree t, Tree root) {
    return t;
  }


  public List<? extends HasWord> defaultTestSentence() {
    String[] sent = {"H", "MWX", "MTPLC", "LA", "RQ", "M", "H", "TWPEH", "H", "MBIFH", "ALA", "GM", "M", "DRKI", "H", "HERMH", "yyDOT"};
    return SentenceUtils.toWordList(sent);
  }

  public TreeReaderFactory treeReaderFactory() {
    return new HebrewTreeReaderFactory();
  }

}
