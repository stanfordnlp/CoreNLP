package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.parser.metrics.AbstractEval;
import edu.stanford.nlp.parser.tools.PunctEquivalenceClasser;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Index;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


/**
 * An abstract class providing a common method base from which to
 * complete a {@code TreebankLangParserParams} implementing class.
 * <br>
 * With some extending classes you'll want to have access to special
 * attributes of the corresponding TreebankLanguagePack while taking
 * advantage of this class's code for making the TreebankLanguagePack
 * accessible.  A good way to do this is to pass a new instance of the
 * appropriate TreebankLanguagePack into this class's constructor,
 * then get it back later on by casting a call to
 * treebankLanguagePack().  See ChineseTreebankParserParams for an
 * example.
 *
 * @author Roger Levy
 */
public abstract class AbstractTreebankParserParams implements TreebankLangParserParams  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AbstractTreebankParserParams.class);

  /**
   * If true, then evaluation is over grammatical functions as well as the labels
   * If false, then grammatical functions are stripped for evaluation.  This really
   * only makes sense if you've trained with grammatical functions but want to evaluate without them.
   */
  protected boolean evalGF = true;

  /** The job of this class is to remove subcategorizations from
   *  tag and category nodes, so as to put a tree in a suitable
   *  state for evaluation.  Providing the TreebankLanguagePack
   *  is defined correctly, this should work for any language.
   */
  protected class SubcategoryStripper implements TreeTransformer {

    protected TreeFactory tf = new LabeledScoredTreeFactory();

    @Override
    public Tree transformTree(Tree tree) {
      Label lab = tree.label();
      if (tree.isLeaf()) {
        Tree leaf = tf.newLeaf(lab);
        leaf.setScore(tree.score());
        return leaf;
      }
      String s = lab.value();
      s = treebankLanguagePack().basicCategory(s);
      int numKids = tree.numChildren();
      List<Tree> children = new ArrayList<>(numKids);
      for (int cNum = 0; cNum < numKids; cNum++) {
        Tree child = tree.getChild(cNum);
        Tree newChild = transformTree(child);
        // cdm 2007: for just subcategory stripping, null shouldn't happen
        // if (newChild != null) {
        children.add(newChild);
        // }
      }
      // if (children.isEmpty()) {
      //   return null;
      // }
      CategoryWordTag newLabel = new CategoryWordTag(lab);
      newLabel.setCategory(s);
      if (lab instanceof HasTag) {
        String tag = ((HasTag) lab).tag();
        tag = treebankLanguagePack().basicCategory(tag);
        newLabel.setTag(tag);
      }
      Tree node = tf.newTreeNode(newLabel, children);
      node.setScore(tree.score());
      return node;
    }

  } // end class SubcategoryStripper

  /** The job of this class is to remove subcategorizations from
   *  tag and category nodes, so as to put a tree in a suitable
   *  state for evaluation.  Providing the TreebankLanguagePack
   *  is defined correctly, this should work for any language.
   *  Very simililar to subcategory stripper, but strips grammatical
   *  functions as well.
   */
  protected class RemoveGFSubcategoryStripper implements TreeTransformer {

    protected TreeFactory tf = new LabeledScoredTreeFactory();

    @Override
    public Tree transformTree(Tree tree) {
      Label lab = tree.label();
      if (tree.isLeaf()) {
        Tree leaf = tf.newLeaf(lab);
        leaf.setScore(tree.score());
        return leaf;
      }
      String s = lab.value();
      s = treebankLanguagePack().basicCategory(s);
      s = treebankLanguagePack().stripGF(s);
      int numKids = tree.numChildren();
      List<Tree> children = new ArrayList<>(numKids);
      for (int cNum = 0; cNum < numKids; cNum++) {
        Tree child = tree.getChild(cNum);
        Tree newChild = transformTree(child);
        children.add(newChild);
      }
      CategoryWordTag newLabel = new CategoryWordTag(lab);
      newLabel.setCategory(s);
      if (lab instanceof HasTag) {
        String tag = ((HasTag) lab).tag();
        tag = treebankLanguagePack().basicCategory(tag);
        tag = treebankLanguagePack().stripGF(tag);

        newLabel.setTag(tag);
      }
      Tree node = tf.newTreeNode(newLabel, children);
      node.setScore(tree.score());
      return node;
    }
  } // end class RemoveGFSubcategoryStripper

  protected String inputEncoding;
  protected String outputEncoding;
  protected TreebankLanguagePack tlp;
  protected boolean generateOriginalDependencies;


  /**
   * Stores the passed-in TreebankLanguagePack and sets up charset encodings.
   *
   * @param tlp The treebank language pack to use
   */
  protected AbstractTreebankParserParams(TreebankLanguagePack tlp) {
    this.tlp = tlp;
    inputEncoding = tlp.getEncoding();
    outputEncoding = tlp.getEncoding();
    generateOriginalDependencies = false;
  }

  @Override
  public Label processHeadWord(Label headWord) {
    return headWord;
  }

  /**
   * Sets whether to consider grammatical functions in evaluation
   */
  @Override
  public void setEvaluateGrammaticalFunctions(boolean evalGFs) {
    this.evalGF = evalGFs;
  }

  /**
   * Sets the input encoding.
   */
  @Override
  public void setInputEncoding(String encoding) {
    inputEncoding = encoding;
  }

  /**
   * Sets the output encoding.
   */
  @Override
  public void setOutputEncoding(String encoding) {
    outputEncoding = encoding;
  }

  /**
   * Returns the output encoding being used.
   */
  @Override
  public String getOutputEncoding() {
    return outputEncoding;
  }

  /**
   * Returns the input encoding being used.
   */
  @Override
  public String getInputEncoding() {
    return inputEncoding;
  }

  /** {@inheritDoc} */
  @Override
  public abstract TreeReaderFactory treeReaderFactory();

  /**
   * Returns a language specific object for evaluating PP attachment
   *
   * @return An object that implements {@link AbstractEval}
   */
  @Override
  public AbstractEval ppAttachmentEval() {
    return null;
  }

  /**
   * Allows you to read in trees from the source you want.  It's the
   * responsibility of treeReaderFactory() to deal properly with character-set
   * encoding of the input.  It also is the responsibility of tr to properly
   * normalize trees.
   */
  @Override
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory(), getInputEncoding());
  }


  /**
   * Allows you to read in trees from the source you want.  It's the
   * responsibility of treeReaderFactory() to deal properly with character-set
   * encoding of the input.  It also is the responsibility of tr to properly
   * normalize trees.
   */
  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), getInputEncoding());
  }

  /**
   * You can often return the same thing for testMemoryTreebank as
   * for memoryTreebank
   */
  @Override
  public MemoryTreebank testMemoryTreebank() {
    return memoryTreebank();
  }

  /**
   * Implemented as required by TreebankFactory. Use diskTreebank() instead.
   */
  @Override
  public Treebank treebank() {
    return diskTreebank();
  }

  /**
   * The PrintWriter used to print output. It's the responsibility of
   * pw to deal properly with character encodings for the relevant
   * treebank.
   */
  @Override
  public PrintWriter pw() {
    return pw(System.out);
  }

  /**
   * The PrintWriter used to print output. It's the responsibility of
   * pw to deal properly with character encodings for the relevant
   * treebank.
   */
  @Override
  public PrintWriter pw(OutputStream o) {
    String encoding = outputEncoding;
    if (!java.nio.charset.Charset.isSupported(encoding)) {
      log.info("Warning: desired encoding " + encoding + " not accepted. ");
      log.info("Using UTF-8 to construct PrintWriter");
      encoding = "UTF-8";
    }

    //log.info("TreebankParserParams.pw(): encoding is " + encoding);
    try {
      return new PrintWriter(new OutputStreamWriter(o, encoding), true);
    } catch (UnsupportedEncodingException e) {
      log.info("Warning: desired encoding " + outputEncoding + " not accepted. " + e);
      try {
        return new PrintWriter(new OutputStreamWriter(o, "UTF-8"), true);
      } catch (UnsupportedEncodingException e1) {
        log.info("Something is really wrong.  Your system doesn't even support UTF-8!" + e1);
        return new PrintWriter(o, true);
      }
    }
  }


  /**
   * Returns an appropriate treebankLanguagePack
   */
  @Override
  public TreebankLanguagePack treebankLanguagePack() {
    return tlp;
  }

  /**
   * The HeadFinder to use for your treebank.
   */
  @Override
  public abstract HeadFinder headFinder();

  /**
   * The HeadFinder to use when extracting typed dependencies.
   */
  @Override
  public abstract HeadFinder typedDependencyHeadFinder();

  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    return new BaseLexicon(op, wordIndex, tagIndex);
  }

  /**
   * Give the parameters for smoothing in the MLEDependencyGrammar.
   * Defaults are the ones previously hard coded into MLEDependencyGrammar.
   * @return an array of doubles with smooth_aT_hTWd, smooth_aTW_hTWd, smooth_stop, and interp
   */
  @Override
  public double[] MLEDependencyGrammarSmoothingParams() {
    return new double[] { 16.0, 16.0, 4.0, 0.6 };
  }

  /**
   * the tree transformer used to produce trees for evaluation.  Will
   * be applied both to the parse output tree and to the gold
   * tree. Should strip punctuation and maybe do some other things.
   */
  @Override
  public abstract AbstractCollinizer collinizer();

  /**
   * the tree transformer used to produce trees for evaluation.  Will
   * be applied both to the parse output tree and to the gold
   * tree. Should strip punctuation and maybe do some other
   * things. The evalb version should strip some more stuff
   * off. (finish this doc!)
   */
  @Override
  public abstract AbstractCollinizer collinizerEvalb();


  /**
   * Returns the splitting strings used for selective splits.
   *
   * @return An array containing ancestor-annotated Strings: categories
   *         should be split according to these ancestor annotations.
   */
  @Override
  public abstract String[] sisterSplitters();


  /**
   * Returns a TreeTransformer appropriate to the Treebank which
   * can be used to remove functional tags (such as "-TMP") from
   * categories. Removes GFs if evalGF = false; if GFs were not used
   * in training, results are equivalent.
   */
  @Override
  public TreeTransformer subcategoryStripper() {
    if(evalGF)
      return new SubcategoryStripper();
    return new RemoveGFSubcategoryStripper();
  }

  /**
   * This method does language-specific tree transformations such
   * as annotating particular nodes with language-relevant features.
   * Such parameterizations should be inside the specific
   * TreebankLangParserParams class.  This method is recursively
   * applied to each node in the tree (depth first, left-to-right),
   * so you shouldn't write this method to apply recursively to tree
   * members.  This method is allowed to (and in some cases does)
   * destructively change the input tree {@code t}. It changes both
   * labels and the tree shape.
   *
   * @param t The input tree (with non-language specific annotation already
   *           done, so you need to strip back to basic categories)
   * @param root The root of the current tree (can be null for words)
   * @return The fully annotated tree node (with daughters still as you
   *           want them in the final result)
   */
  @Override
  public abstract Tree transformTree(Tree t, Tree root);

  /**
   * Display (write to stderr) language-specific settings.
   */
  @Override
  public abstract void display();

  /**
   * Set language-specific options according to flags.
   * This routine should process the option starting in args[i] (which
   * might potentially be several arguments long if it takes arguments).
   * It should return the index after the last index it consumed in
   * processing.  In particular, if it cannot process the current option,
   * the return value should be i.
   * <p>
   * Generic options are processed separately by
   * {@link Options#setOption(String[],int)},
   * and implementations of this method do not have to worry about them.
   * The Options class handles routing options.
   * TreebankParserParams that extend this class should call super when
   * overriding this method.
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    return i;
  }

  /** {@inheritDoc} */
  @Override
  public abstract List<? extends HasWord> defaultTestSentence();

  private static final long serialVersionUID = 4299501909017975915L;

  @Override
  public TokenizerFactory<Tree> treeTokenizerFactory() {
    return new TreeTokenizerFactory(treeReaderFactory());
  }

  @Override
  public Extractor<DependencyGrammar> dependencyGrammarExtractor(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    return new MLEDependencyGrammarExtractor(op, wordIndex, tagIndex);
  }

  public boolean isEvalGF() {
    return evalGF;
  }

  public void setEvalGF(boolean evalGF) {
    this.evalGF = evalGF;
  }

  /**
   * Annotation function for mapping punctuation to PTB-style equivalence classes.
   *
   * @author Spence Green
   *
   */
  protected static class AnnotatePunctuationFunction implements SerializableFunction<TregexMatcher,String> {
    private final String key;
    private final String annotationMark;

    public AnnotatePunctuationFunction(String annotationMark, String key) {
      this.key = key;
      this.annotationMark = annotationMark;
    }

    @Override
    public String apply(TregexMatcher m) {
      String punc = m.getNode(key).value();
      String punctClass = PunctEquivalenceClasser.getPunctClass(punc);

      return punctClass.equals("") ? "" : annotationMark + punctClass;
    }

    @Override
    public String toString() { return "AnnotatePunctuationFunction"; }

    private static final long serialVersionUID = 1L;
  }

  @Override
  public List<GrammaticalStructure>
    readGrammaticalStructureFromFile(String filename)
  {
    throw new UnsupportedOperationException("This language does not support GrammaticalStructures or dependencies");
  }

  @Override
  public GrammaticalStructure getGrammaticalStructure(Tree t,
                                                      Predicate<String> filter,
                                                      HeadFinder hf) {
    throw new UnsupportedOperationException("This language does not support GrammaticalStructures or dependencies");
  }

  /**
   * By default, parsers are assumed to not support dependencies.
   * Only English and Chinese do at present.
   */
  @Override
  public boolean supportsBasicDependencies() {
    return false;
  }

  /**
   * For languages that have implementations of the
   * original Stanford dependencies and Universal
   * dependencies, this parameter is used to decide which
   * implementation should be used.
   */
  @Override
  public void setGenerateOriginalDependencies(boolean originalDependencies) {
    this.generateOriginalDependencies = originalDependencies;
    if (this.tlp != null) {
      this.tlp.setGenerateOriginalDependencies(originalDependencies);
    }
  }

  @Override
  public boolean generateOriginalDependencies() {
    return this.generateOriginalDependencies;
  }

  private static final String[] EMPTY_ARGS = new String[0];

  @Override
  public String[] defaultCoreNLPFlags() {
    return EMPTY_ARGS;
  }

}
