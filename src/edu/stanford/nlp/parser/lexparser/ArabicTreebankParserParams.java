package edu.stanford.nlp.parser.lexparser;

import java.util.*;
import java.util.function.Function;
import java.util.regex.*;

import edu.stanford.nlp.international.arabic.ArabicMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.arabic.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;



/**
 * A {@link TreebankLangParserParams} implementing class for
 * the Penn Arabic Treebank.  The baseline feature set works with either
 * UTF-8 or Buckwalter input, although the behavior of some unused features depends
 * on the input encoding.
 *
 * @author Roger Levy
 * @author Christopher Manning
 * @author Spence Green
 */
public class ArabicTreebankParserParams extends AbstractTreebankParserParams  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ArabicTreebankParserParams.class);

  private static final long serialVersionUID = 8853426784197984653L;

  private final StringBuilder optionsString;

  private boolean retainNPTmp = false;
  private boolean retainNPSbj = false;
  private boolean retainPRD = false;
  private boolean retainPPClr = false;
  private boolean changeNoLabels = false;
  private boolean collinizerRetainsPunctuation = false;
  private boolean discardX = false;

  private HeadFinder headFinder;
  private final Map<String,Pair<TregexPattern,Function<TregexMatcher,String>>> annotationPatterns;
  private final List<Pair<TregexPattern,Function<TregexMatcher,String>>> activeAnnotations;

  private MorphoFeatureSpecification morphoSpec = null;
  
  public ArabicTreebankParserParams() {
    super(new ArabicTreebankLanguagePack());

    optionsString = new StringBuilder();
    optionsString.append("ArabicTreebankParserParams\n");

    annotationPatterns = Generics.newHashMap();
    activeAnnotations = new ArrayList<>();

    //Initialize the headFinder here
    headFinder = headFinder();

    initializeAnnotationPatterns();
  }

  /**
   * Creates an {@link ArabicTreeReaderFactory} with parameters set
   * via options passed in from the command line.
   *
   * @return An {@link ArabicTreeReaderFactory}
   */
  public TreeReaderFactory treeReaderFactory() {
    return new ArabicTreeReaderFactory(retainNPTmp, retainPRD,
        changeNoLabels, discardX,
        retainNPSbj, false, retainPPClr);
  }

  //NOTE (WSG): This method is called by main() to load the test treebank
  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), inputEncoding);
  }

  //NOTE (WSG): This method is called to load the training treebank
  @Override
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory(), inputEncoding);
  }

  @Override
  public HeadFinder headFinder() {
    if(headFinder == null)
      headFinder = new ArabicHeadFinder(treebankLanguagePack());
    return headFinder;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder();
  }


  /**
   * Returns a lexicon for Arabic.  At the moment this is just a BaseLexicon.
   *
   * @param op Lexicon options
   * @return A Lexicon
   */
  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    if(op.lexOptions.uwModelTrainer == null) {
      op.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.ArabicUnknownWordModelTrainer";
    }
    if(morphoSpec != null) {
      return new FactoredLexicon(op, morphoSpec, wordIndex, tagIndex);
    }
    return new BaseLexicon(op, wordIndex, tagIndex);
  }

  /**
   * Return a default sentence for the language (for testing).
   * The example is in UTF-8.
   */
  public List<? extends HasWord> defaultTestSentence() {
    String[] sent = {"هو","استنكر","الحكومة","يوم","امس","."};
    return SentenceUtils.toWordList(sent);
  }

  protected class ArabicSubcategoryStripper implements TreeTransformer {

    protected final TreeFactory tf = new LabeledScoredTreeFactory();

    @Override
    public Tree transformTree(Tree tree) {
      Label lab = tree.label();
      String s = lab.value();

      if (tree.isLeaf()) {
        Tree leaf = tf.newLeaf(lab);
        leaf.setScore(tree.score());
        return leaf;

      } else if(tree.isPhrasal()) {
        if(retainNPTmp && s.startsWith("NP-TMP")) {
          s = "NP-TMP";
        } else if(retainNPSbj && s.startsWith("NP-SBJ")) {
          s = "NP-SBJ";
        } else if(retainPRD && s.matches("VB[^P].*PRD.*")) {
          s = tlp.basicCategory(s);
          s += "-PRD";
        } else {
          s = tlp.basicCategory(s);
        }

      } else if(tree.isPreTerminal()) {
        s = tlp.basicCategory(s);

      } else {
        System.err.printf("Encountered a non-leaf/phrasal/pre-terminal node %s\n",s);
        //Normalize by default
        s = tlp.basicCategory(s);
      }

      // Recursively process children depth-first
      List<Tree> children = new ArrayList<>(tree.numChildren());
      for (Tree child : tree.getChildrenAsList()) {
        Tree newChild = transformTree(child);
        children.add(newChild);
      }

      // Make the new parent label
      Tree node = tf.newTreeNode(lab, children);
      node.setValue(s);
      node.setScore(tree.score());
      if(node.label() instanceof HasTag)
        ((HasTag) node.label()).setTag(s);

      return node;
    }
  }

  /**
   * Returns a TreeTransformer that retains categories
   * according to the following options supported by setOptionFlag:
   * <p>
   * {@code -retainNPTmp} Retain temporal NP marking on NPs.
   * {@code -retainNPSbj} Retain NP subject function tags
   * {@code -markPRDverbs} Retain PRD verbs.
   * </p>
   */
  //NOTE (WSG): This is applied to both the best parse by getBestParse()
  //and to the gold eval tree by testOnTreebank()
  @Override
  public TreeTransformer subcategoryStripper() {
    return new ArabicSubcategoryStripper();
  }


  /**
   * The collinizer eliminates punctuation
   */
  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(tlp, !collinizerRetainsPunctuation, false);
  }

  /**
   * Stand-in collinizer does nothing to the tree.
   */
  @Override
  public AbstractCollinizer collinizerEvalb() {
    return collinizer();
  }

  @Override
  public String[] sisterSplitters() {
    return StringUtils.EMPTY_STRING_ARRAY;
  }

  // WSGDEBUG -- Annotate POS tags with nominal (grammatical) gender
  private static final MorphoFeatureSpecification tagSpec = new ArabicMorphoFeatureSpecification();
  static {
    tagSpec.activate(MorphoFeatureType.NGEN);
  }
  
  @Override
  public Tree transformTree(Tree t, Tree root) {

    String baseCat = t.value();
    StringBuilder newCategory = new StringBuilder();

    //Add manual state splits
    for (Pair<TregexPattern,Function<TregexMatcher,String>> e : activeAnnotations) {
      TregexMatcher m = e.first().matcher(root);
      if (m.matchesAt(t))
        newCategory.append(e.second().apply(m));
    }

    // WSGDEBUG
    //Add morphosyntactic features if this is a POS tag
    if(t.isPreTerminal() && tagSpec != null) {
      if( !(t.firstChild().label() instanceof CoreLabel) || ((CoreLabel) t.firstChild().label()).originalText() == null )
        throw new RuntimeException(String.format("%s: Term lacks morpho analysis: %s",this.getClass().getName(),t.toString()));

      String morphoStr = ((CoreLabel) t.firstChild().label()).originalText();
      MorphoFeatures feats = tagSpec.strToFeatures(morphoStr);
      baseCat = feats.getTag(baseCat);
    }

    //Update the label(s)
    String newCat = baseCat + newCategory;
    t.setValue(newCat);
    if (t.isPreTerminal() && t.label() instanceof HasTag)
      ((HasTag) t.label()).setTag(newCat);

    return t;
  }

  /**
   * These are the annotations included when the user selects the -arabicFactored option.
   */
  private final List<String> baselineFeatures = new ArrayList<>();
  {
    baselineFeatures.add("-markNounNPargTakers");
    baselineFeatures.add("-genitiveMark");
    baselineFeatures.add("-splitPUNC");
    baselineFeatures.add("-markContainsVerb");
    baselineFeatures.add("-markStrictBaseNP");
    baselineFeatures.add("-markOneLevelIdafa");
    baselineFeatures.add("-splitIN");
    baselineFeatures.add("-markMasdarVP");
    baselineFeatures.add("-containsSVO");
    baselineFeatures.add("-splitCC");
    baselineFeatures.add("-markFem");
    
    // Added for MWE experiments
    baselineFeatures.add("-mwe");
    baselineFeatures.add("-mweContainsVerb");
  }
  private final List<String> additionalFeatures = new ArrayList<>();

  private void initializeAnnotationPatterns() {
    //This doesn't/can't really pick out genitives, but just any NP following an NN head.
    //wsg2011: In particular, it doesn't select NP complements of PPs, which are also genitive.
    final String genitiveNodeTregexString = "@NP > @NP $- /^N/";

    TregexPatternCompiler tregexPatternCompiler =
      new TregexPatternCompiler(headFinder());

    try {
      // ******************
      // Baseline features
      // ******************
      annotationPatterns.put("-genitiveMark", new Pair<>(TregexPattern.compile(genitiveNodeTregexString), new SimpleStringFunction("-genitive")));
      annotationPatterns.put("-markStrictBaseNP", new Pair<>(tregexPatternCompiler.compile("@NP !< (__ < (__ < __))"), new SimpleStringFunction("-base"))); // NP with no phrasal node in it
      annotationPatterns.put("-markOneLevelIdafa", new Pair<>(tregexPatternCompiler.compile("@NP < (@NP < (__ < __)) !< (/^[^N]/ < (__ < __)) !< (__ < (__ < (__ < __)))"), new SimpleStringFunction("-idafa1")));
      annotationPatterns.put("-markNounNPargTakers", new Pair<>(tregexPatternCompiler.compile("@NN|NNS|NNP|NNPS|DTNN|DTNNS|DTNNP|DTNNPS ># (@NP < @NP)"), new SimpleStringFunction("-NounNParg")));
      annotationPatterns.put("-markContainsVerb", new Pair<>(tregexPatternCompiler.compile("__ << (/^[CIP]?V/ < (__ !< __))"), new SimpleStringFunction("-withV")));
      annotationPatterns.put("-splitIN", new Pair<>(tregexPatternCompiler.compile("@IN < __=word"), new AddRelativeNodeFunction("-", "word", false)));
      annotationPatterns.put("-splitPUNC", new Pair<>(tregexPatternCompiler.compile("@PUNC < __=" + AnnotatePunctuationFunction2.key), new AnnotatePunctuationFunction2()));
      annotationPatterns.put("-markMasdarVP", new Pair<>(tregexPatternCompiler.compile("@VP|MWVP < /VBG|VN/"), new SimpleStringFunction("-masdar")));
      annotationPatterns.put("-containsSVO", new Pair<>(tregexPatternCompiler.compile("__ << (@S < (@NP . @VP|MWVP))"), new SimpleStringFunction("-hasSVO")));
      annotationPatterns.put("-splitCC", new Pair<>(tregexPatternCompiler.compile("@CC|CONJ . __=term , __"), new AddEquivalencedConjNode("-", "term")));
      annotationPatterns.put("-markFem", new Pair<>(tregexPatternCompiler.compile("__ < /ة$/"), new SimpleStringFunction("-fem")));
      
      // Added for MWE experiments
      annotationPatterns.put("-mwe", new Pair<>(tregexPatternCompiler.compile("__ > /MW/=tag"), new AddRelativeNodeFunction("-", "tag", true)));
      annotationPatterns.put("-mweContainsVerb", new Pair<>(tregexPatternCompiler.compile("__ << @MWVP"), new SimpleStringFunction("-withV")));

      //This version, which uses the PTB equivalence classing, results in slightly lower labeled F1
      //than the splitPUNC feature above, which was included in the COLING2010 evaluation
      annotationPatterns.put("-splitPUNC2", new Pair<>(tregexPatternCompiler.compile("@PUNC < __=punc"), new AnnotatePunctuationFunction("-", "punc")));

      // Label each POS with its parent
      annotationPatterns.put("-tagPAar", new Pair<>(tregexPatternCompiler.compile("!@PUNC < (__ !< __) > __=parent"), new AddRelativeNodeFunction("-", "parent", true)));

      //Didn't work
      annotationPatterns.put("-splitCC1", new Pair<>(tregexPatternCompiler.compile("@CC|CONJ < __=term"), new AddRelativeNodeRegexFunction("-", "term", "-*([^-].*)")));
      annotationPatterns.put("-splitCC2", new Pair<>(tregexPatternCompiler.compile("@CC . __=term , __"), new AddRelativeNodeFunction("-", "term", true)));
      annotationPatterns.put("-idafaJJ1", new Pair<>(tregexPatternCompiler.compile("@NP <, (@NN $+ @NP) <+(@NP) @ADJP"), new SimpleStringFunction("-idafaJJ")));
      annotationPatterns.put("-idafaJJ2", new Pair<>(tregexPatternCompiler.compile("@NP <, (@NN $+ @NP) <+(@NP) @ADJP !<< @SBAR"), new SimpleStringFunction("-idafaJJ")));

      annotationPatterns.put("-properBaseNP", new Pair<>(tregexPatternCompiler.compile("@NP !<< @NP < /NNP/ !< @PUNC|CD"), new SimpleStringFunction("-prop")));
      annotationPatterns.put("-interrog", new Pair<>(tregexPatternCompiler.compile("__ << هل|ماذا|لماذا|اين|متى"), new SimpleStringFunction("-inter")));
      annotationPatterns.put("-splitPseudo", new Pair<>(tregexPatternCompiler.compile("@NN < مع|بعد|بين"), new SimpleStringFunction("-pseudo")));
      annotationPatterns.put("-nPseudo", new Pair<>(tregexPatternCompiler.compile("@NP < (@NN < مع|بعد|بين)"), new SimpleStringFunction("-npseudo")));
      annotationPatterns.put("-pseudoArg", new Pair<>(tregexPatternCompiler.compile("@NP < @NP $, (@NN < مع|بعد|بين)"), new SimpleStringFunction("-pseudoArg")));
      annotationPatterns.put("-eqL1", new Pair<>(tregexPatternCompiler.compile("__ < (@S !< @VP|S)"), new SimpleStringFunction("-haseq")));
      annotationPatterns.put("-eqL1L2", new Pair<>(tregexPatternCompiler.compile("__ < (__ < (@S !< @VP|S)) | < (@S !< @VP|S)"), new SimpleStringFunction("-haseq")));
      annotationPatterns.put("-fullQuote", new Pair<>(tregexPatternCompiler.compile("__ < ((@PUNC < \") $ (@PUNC < \"))"), new SimpleStringFunction("-fq")));
      annotationPatterns.put("-brokeQuote", new Pair<>(tregexPatternCompiler.compile("__ < ((@PUNC < \") !$ (@PUNC < \"))"), new SimpleStringFunction("-bq")));
      annotationPatterns.put("-splitVP", new Pair<>(tregexPatternCompiler.compile("@VP <# __=term1"), new AddRelativeNodeFunction("-", "term1", true)));
      annotationPatterns.put("-markFemP", new Pair<>(tregexPatternCompiler.compile("@NP|ADJP < (__ < /ة$/)"), new SimpleStringFunction("-femP")));
      annotationPatterns.put("-embedSBAR", new Pair<>(tregexPatternCompiler.compile("@NP|PP <+(@NP|PP) @SBAR"), new SimpleStringFunction("-embedSBAR")));
      annotationPatterns.put("-complexVP", new Pair<>(tregexPatternCompiler.compile("__ << (@VP < (@NP $ @NP)) > __"), new SimpleStringFunction("-complexVP")));
      annotationPatterns.put("-containsJJ", new Pair<>(tregexPatternCompiler.compile("@NP <+(@NP) /JJ/"), new SimpleStringFunction("-hasJJ")));
      annotationPatterns.put("-markMasdarVP2", new Pair<>(tregexPatternCompiler.compile("__ << @VN|VBG"), new SimpleStringFunction("-masdar")));
      annotationPatterns.put("-coordNP", new Pair<>(tregexPatternCompiler.compile("@NP|ADJP <+(@NP|ADJP) (@CC|PUNC $- __ $+ __)"), new SimpleStringFunction("-coordNP")));
      annotationPatterns.put("-coordWa", new Pair<>(tregexPatternCompiler.compile("__ << (@CC , __ < و-)"), new SimpleStringFunction("-coordWA")));
      annotationPatterns.put("-NPhasADJP", new Pair<>(tregexPatternCompiler.compile("@NP <+(@NP) @ADJP"), new SimpleStringFunction("-NPhasADJP")));
      annotationPatterns.put("-NPADJP", new Pair<>(tregexPatternCompiler.compile("@NP < @ADJP"), new SimpleStringFunction("-npadj")));
      annotationPatterns.put("-NPJJ", new Pair<>(tregexPatternCompiler.compile("@NP < /JJ/"), new SimpleStringFunction("-npjj")));
      annotationPatterns.put("-NPCC", new Pair<>(tregexPatternCompiler.compile("@NP <+(@NP) @CC"), new SimpleStringFunction("-npcc")));
      annotationPatterns.put("-NPCD", new Pair<>(tregexPatternCompiler.compile("@NP < @CD"), new SimpleStringFunction("-npcd")));
      annotationPatterns.put("-NPNNP", new Pair<>(tregexPatternCompiler.compile("@NP < /NNP/"), new SimpleStringFunction("-npnnp")));
      annotationPatterns.put("-SVO", new Pair<>(tregexPatternCompiler.compile("@S < (@NP . @VP)"), new SimpleStringFunction("-svo")));
      annotationPatterns.put("-containsSBAR", new Pair<>(tregexPatternCompiler.compile("__ << @SBAR"), new SimpleStringFunction("-hasSBAR")));


      //WSGDEBUG - Template
      //annotationPatterns.put("", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile(""), new SimpleStringFunction("")));


      // ************
      // Old and unused features (in various states of repair)
      // *************
      annotationPatterns.put("-markGappedVP", new Pair<>(TregexPattern.compile("@VP > @VP $- __ $ /^(?:CC|CONJ)/ !< /^V/"), new SimpleStringFunction("-gappedVP")));
      annotationPatterns.put("-markGappedVPConjoiners", new Pair<>(TregexPattern.compile("/^(?:CC|CONJ)/ $ (@VP > @VP $- __ !< /^V/)"), new SimpleStringFunction("-gappedVP")));
      annotationPatterns.put("-markGenitiveParent", new Pair<>(TregexPattern.compile("@NP < (" + genitiveNodeTregexString + ')'), new SimpleStringFunction("-genitiveParent")));
      // maSdr: this pattern is just a heuristic classification, which matches on
      // various common maSdr pattterns, but probably also matches on a lot of other
      // stuff.  It marks NPs with possible maSdr.
      // Roger's old pattern:
      annotationPatterns.put("-maSdrMark", new Pair<>(tregexPatternCompiler.compile("/^N/ <<# (/^[t\\u062a].+[y\\u064a].$/ > @NN|NOUN|DTNN)"), new SimpleStringFunction("-maSdr")));
      // chris' attempt
      annotationPatterns.put("-maSdrMark2", new Pair<>(tregexPatternCompiler.compile("/^N/ <<# (/^(?:[t\\u062a].+[y\\u064a].|<.{3,}|A.{3,})$/ > @NN|NOUN|DTNN)"), new SimpleStringFunction("-maSdr")));
      annotationPatterns.put("-maSdrMark3", new Pair<>(tregexPatternCompiler.compile("/^N/ <<# (/^(?:[t\\u062a<A].{3,})$/ > @NN|NOUN|DTNN)"), new SimpleStringFunction("-maSdr")));
      annotationPatterns.put("-maSdrMark4", new Pair<>(tregexPatternCompiler.compile("/^N/ <<# (/^(?:[t\\u062a<A].{3,})$/ > (@NN|NOUN|DTNN > (@NP < @NP)))"), new SimpleStringFunction("-maSdr")));
      annotationPatterns.put("-maSdrMark5", new Pair<>(tregexPatternCompiler.compile("/^N/ <<# (__ > (@NN|NOUN|DTNN > (@NP < @NP)))"), new SimpleStringFunction("-maSdr")));
      annotationPatterns.put("-mjjMark", new Pair<>(tregexPatternCompiler.compile("@JJ|DTJJ < /^m/ $+ @PP ># @ADJP "), new SimpleStringFunction("-mjj")));
      //annotationPatterns.put(markPRDverbString,new Pair<TregexPattern,Function<TregexMatcher,String>>(TregexPattern.compile("/^V[^P]/ > VP $ /-PRD$/"),new SimpleStringFunction("-PRDverb"))); // don't need this pattern anymore, the functionality has been moved to ArabicTreeNormalizer
      // PUNC is PUNC in either raw or Bies POS encoding
      annotationPatterns.put("-markNPwithSdescendant", new Pair<>(tregexPatternCompiler.compile("__ !< @S << @S [ >> @NP | == @NP ]"), new SimpleStringFunction("-inNPdominatesS")));
      annotationPatterns.put("-markRightRecursiveNP", new Pair<>(tregexPatternCompiler.compile("__ <<- @NP [>>- @NP | == @NP]"), new SimpleStringFunction("-rrNP")));
      annotationPatterns.put("-markBaseNP", new Pair<>(tregexPatternCompiler.compile("@NP !< @NP !< @VP !< @SBAR !< @ADJP !< @ADVP !< @S !< @QP !< @UCP !< @PP"), new SimpleStringFunction("-base")));
      // allow only a single level of idafa as Base NP; this version works!
      annotationPatterns.put("-markBaseNPplusIdafa", new Pair<>(tregexPatternCompiler.compile("@NP !< (/^[^N]/ < (__ < __)) !< (__ < (__ < (__ < __)))"), new SimpleStringFunction("-base")));
      annotationPatterns.put("-markTwoLevelIdafa", new Pair<>(tregexPatternCompiler.compile("@NP < (@NP < (@NP < (__ < __)) !< (/^[^N]/ < (__ < __))) !< (/^[^N]/ < (__ < __)) !< (__ < (__ < (__ < (__ < __))))"), new SimpleStringFunction("-idafa2")));
      annotationPatterns.put("-markDefiniteIdafa", new Pair<>(tregexPatternCompiler.compile("@NP < (/^(?:NN|NOUN)/ !$,, /^[^AP]/) <+(/^NP/) (@NP < /^DT/)"), new SimpleStringFunction("-defIdafa")));
      annotationPatterns.put("-markDefiniteIdafa1", new Pair<>(tregexPatternCompiler.compile("@NP < (/^(?:NN|NOUN)/ !$,, /^[^AP]/) < (@NP < /^DT/) !< (/^[^N]/ < (__ < __)) !< (__ < (__ < (__ < __)))"), new SimpleStringFunction("-defIdafa1")));
      annotationPatterns.put("-markContainsSBAR", new Pair<>(tregexPatternCompiler.compile("__ << @SBAR"), new SimpleStringFunction("-withSBAR")));
      annotationPatterns.put("-markPhrasalNodesDominatedBySBAR", new Pair<>(tregexPatternCompiler.compile("__ < (__ < __) >> @SBAR"), new SimpleStringFunction("-domBySBAR")));
      annotationPatterns.put("-markCoordinateNPs", new Pair<>(tregexPatternCompiler.compile("@NP < @CC|CONJ"), new SimpleStringFunction("-coord")));
      //annotationPatterns.put("-markCopularVerbTags",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("/^V/ < " + copularVerbForms),new SimpleStringFunction("-copular")));
      //annotationPatterns.put("-markSBARVerbTags",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("/^V/ < " + sbarVerbForms),new SimpleStringFunction("-SBARverb")));
      annotationPatterns.put("-markNounAdjVPheads", new Pair<>(tregexPatternCompiler.compile("@NN|NNS|NNP|NNPS|JJ|DTJJ|DTNN|DTNNS|DTNNP|DTNNPS ># @VP"), new SimpleStringFunction("-VHead")));
      // a better version of the below might only mark clitic pronouns, but
      // since most pronouns are clitics, let's try this first....
      annotationPatterns.put("-markPronominalNP", new Pair<>(tregexPatternCompiler.compile("@NP < @PRP"), new SimpleStringFunction("-PRP")));
      // try doing coordination parallelism -- there's a lot of that in Arabic (usually the same, sometimes different CC)
      annotationPatterns.put("-markMultiCC", new Pair<>(tregexPatternCompiler.compile("__ < (@CC $.. @CC)"), new SimpleStringFunction("-multiCC"))); // this unfortunately didn't seem helpful for capturing CC parallelism; should try again
      annotationPatterns.put("-markHasCCdaughter", new Pair<>(tregexPatternCompiler.compile("__ < @CC"), new SimpleStringFunction("-CCdtr")));
      annotationPatterns.put("-markAcronymNP", new Pair<>(tregexPatternCompiler.compile("@NP !<  (__ < (__ < __)) < (/^NN/ < /^.$/ $ (/^NN/ < /^.$/)) !< (__ < /../)"), new SimpleStringFunction("-acro")));
      annotationPatterns.put("-markAcronymNN", new Pair<>(tregexPatternCompiler.compile("/^NN/ < /^.$/ $ (/^NN/ < /^.$/) > (@NP !<  (__ < (__ < __)) !< (__ < /../))"), new SimpleStringFunction("-acro")));
      //PP Specific patterns
      annotationPatterns.put("-markPPwithPPdescendant", new Pair<>(tregexPatternCompiler.compile("__ !< @PP << @PP [ >> @PP | == @PP ]"), new SimpleStringFunction("-inPPdominatesPP")));
      annotationPatterns.put("-gpAnnotatePrepositions", new Pair<>(TregexPattern.compile("/^(?:IN|PREP)$/ > (__ > __=gp)"), new AddRelativeNodeFunction("^^", "gp", false)));
      annotationPatterns.put("-gpEquivalencePrepositions", new Pair<>(TregexPattern.compile("/^(?:IN|PREP)$/ > (@PP >+(/^PP/) __=gp)"), new AddEquivalencedNodeFunction("^^", "gp")));
      annotationPatterns.put("-gpEquivalencePrepositionsVar", new Pair<>(TregexPattern.compile("/^(?:IN|PREP)$/ > (@PP >+(/^PP/) __=gp)"), new AddEquivalencedNodeFunctionVar("^^", "gp")));
      annotationPatterns.put("-markPPParent", new Pair<>(tregexPatternCompiler.compile("@PP=max !< @PP"), new AddRelativeNodeRegexFunction("^^", "max", "^(\\w)")));
      annotationPatterns.put("-whPP", new Pair<>(tregexPatternCompiler.compile("@PP <- (@SBAR <, /^WH/)"), new SimpleStringFunction("-whPP")));
      //    annotationPatterns.put("-markTmpPP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP !<+(__) @PP"),new LexicalCategoryFunction("-TMP",temporalNouns)));
      annotationPatterns.put("-deflateMin", new Pair<>(tregexPatternCompiler.compile("__ < (__ < من)"), new SimpleStringFunction("-min")));
      annotationPatterns.put("-v2MarkovIN", new Pair<>(tregexPatternCompiler.compile("@IN > (@__=p1 > @__=p2)"), new AddRelativeNodeFunction("^", "p1", "p2", false)));
      annotationPatterns.put("-pleonasticMin", new Pair<>(tregexPatternCompiler.compile("@PP <, (IN < من) > @S"), new SimpleStringFunction("-pleo")));
      annotationPatterns.put("-v2MarkovPP", new Pair<>(tregexPatternCompiler.compile("@PP > (@__=p1 > @__=p2)"), new AddRelativeNodeFunction("^", "p1", "p2", false)));

    } catch (TregexParseException e) {
      int nth = annotationPatterns.size() + 1;
      String nthStr = (nth == 1) ? "1st": ((nth == 2) ? "2nd": nth + "th");
      log.info("Parse exception on " + nthStr + " annotation pattern initialization:" + e);
      throw e;
    }
  }

  private static class SimpleStringFunction implements SerializableFunction<TregexMatcher,String> {

    public SimpleStringFunction(String result) {
      this.result = result;
    }

    private String result;

    @Override
    public String apply(TregexMatcher tregexMatcher) {
      return result;
    }

    @Override
    public String toString() { return "SimpleStringFunction[" + result + ']'; }

    private static final long serialVersionUID = 1L;
  }


  private static class AddRelativeNodeFunction implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;
    private String key;
    private String key2;
    private boolean doBasicCat = false;

    private static final TreebankLanguagePack tlp = new ArabicTreebankLanguagePack();

    public AddRelativeNodeFunction(String annotationMark, String key, boolean basicCategory) {
      this.annotationMark = annotationMark;
      this.key = key;
      this.key2 = null;
      doBasicCat = basicCategory;
    }

    public AddRelativeNodeFunction(String annotationMark, String key1, String key2, boolean basicCategory) {
      this(annotationMark,key1,basicCategory);
      this.key2 = key2;
    }

    @Override
    public String apply(TregexMatcher m) {
      if(key2 == null)
        return annotationMark + ((doBasicCat) ? tlp.basicCategory(m.getNode(key).label().value()) : m.getNode(key).label().value());
      else {
        String annot1 = (doBasicCat) ? tlp.basicCategory(m.getNode(key).label().value()) : m.getNode(key).label().value();
        String annot2 = (doBasicCat) ? tlp.basicCategory(m.getNode(key2).label().value()) : m.getNode(key2).label().value();
        return annotationMark + annot1 + annotationMark + annot2;
      }
    }

    @Override
    public String toString() {
      if(key2 == null)
        return "AddRelativeNodeFunction[" + annotationMark + ',' + key + ']';
      else
        return "AddRelativeNodeFunction[" + annotationMark + ',' + key + ',' + key2 + ']';
    }

    private static final long serialVersionUID = 1L;

  }


  private static class AddRelativeNodeRegexFunction implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;
    private String key;
    private Pattern pattern;

    private String key2 = null;
    private Pattern pattern2;

    public AddRelativeNodeRegexFunction(String annotationMark, String key, String regex) {
      this.annotationMark = annotationMark;
      this.key = key;
      try {
        this.pattern = Pattern.compile(regex);
      } catch (PatternSyntaxException pse) {
        log.info("Bad pattern: " + regex);
        pattern = null;
        throw new IllegalArgumentException(pse);
      }
    }

    @Override
    public String apply(TregexMatcher m) {
      String val = m.getNode(key).label().value();
      if (pattern != null) {
        Matcher mat = pattern.matcher(val);
        if (mat.find()) {
          val = mat.group(1);
        }
      }

      if(key2 != null && pattern2 != null) {
        String val2 = m.getNode(key2).label().value();
        Matcher mat2 = pattern2.matcher(val2);
        if(mat2.find()) {
          val = val + annotationMark + mat2.group(1);
        } else {
          val = val + annotationMark + val2;
        }
      }

      return annotationMark + val;
    }

    @Override
    public String toString() { return "AddRelativeNodeRegexFunction[" + annotationMark + ',' + key + ',' + pattern + ']'; }

    private static final long serialVersionUID = 1L;
  }


  /** This one only distinguishes VP, S and Other (mainly nominal) contexts.
   *  These seem the crucial distinctions for Arabic true prepositions,
   *  based on raw counts in data.
   */
  private static class AddEquivalencedNodeFunction implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;
    private String key;

    public AddEquivalencedNodeFunction(String annotationMark, String key) {
      this.annotationMark = annotationMark;
      this.key = key;
    }

    @Override
    public String apply(TregexMatcher m) {
      String node = m.getNode(key).label().value();
      if (node.startsWith("S")) {
        return annotationMark + 'S';
      } else if (node.startsWith("V")) {
        return annotationMark + 'V';
      } else {
        return "";
      }
    }

    @Override
    public String toString() { return "AddEquivalencedNodeFunction[" + annotationMark + ',' + key + ']'; }

    private static final long serialVersionUID = 1L;
  }


  /** This one only distinguishes VP, S*, A* versus other (mainly nominal) contexts. */
  private static class AddEquivalencedNodeFunctionVar implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;
    private String key;

    public AddEquivalencedNodeFunctionVar(String annotationMark, String key) {
      this.annotationMark = annotationMark;
      this.key = key;
    }

    @Override
    public String apply(TregexMatcher m) {
      String node = m.getNode(key).label().value();
      // We also tried if (node.startsWith("V")) [var2] and if (node.startsWith("V") || node.startsWith("S")) [var3]. Both seemed markedly worse than the basic function or this var form (which seems a bit better than the basic equiv option).
      if (node.startsWith("S") || node.startsWith("V") || node.startsWith("A")) {
        return annotationMark + "VSA";
      } else {
        return "";
      }
    }

    @Override
    public String toString() { return "AddEquivalencedNodeFunctionVar[" + annotationMark + ',' + key + ']'; }

    private static final long serialVersionUID = 1L;
  }

  private static class AnnotatePunctuationFunction2 implements SerializableFunction<TregexMatcher,String> {
    static final String key = "term";

    private static final Pattern quote = Pattern.compile("^\"$");

    @Override
    public String apply(TregexMatcher m) {

      final String punc = m.getNode(key).value();

      if (punc.equals("."))
        return "-fs";
      else if (punc.equals("?"))
        return "-quest";
      else if (punc.equals(","))
        return "-comma";
      else if (punc.equals(":") || punc.equals(";"))
        return "-colon";
      // include both ( and LRB in case different tokenization settings are used
      else if (punc.equals("("))
        return "-lrb";
      else if (punc.equals("-LRB-"))
        return "-lrb";
      else if (punc.equals(")"))
        return "-rrb";
      else if (punc.equals("-RRB-"))
        return "-rrb";
      else if (punc.equals("-PLUS-"))
        return "-plus";
      else if (punc.equals("-"))
        return "-dash";
      else if (quote.matcher(punc).matches())
        return "-quote";
      //      else if(punc.equals("/"))
      //        return "-slash";
      //      else if(punc.equals("%"))
      //        return "-perc";
      //      else if(punc.contains(".."))
      //        return "-ellipses";
      return "";
    }

    @Override
    public String toString() { return "AnnotatePunctuationFunction2"; }

    private static final long serialVersionUID = 1L;
  }


  private static class AddEquivalencedConjNode implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;
    private String key;

    private static final String nnTags = "DTNN DTNNP DTNNPS DTNNS NN NNP NNS NNPS";
    private static final Set<String> nnTagClass = Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(nnTags.split("\\s+"))));

    private static final String jjTags = "ADJ_NUM DTJJ DTJJR JJ JJR";
    private static final Set<String> jjTagClass = Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(jjTags.split("\\s+"))));

    private static final String vbTags = "VBD VBP";
    private static final Set<String> vbTagClass = Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(vbTags.split("\\s+"))));

    private static final TreebankLanguagePack tlp = new ArabicTreebankLanguagePack();

    public AddEquivalencedConjNode(String annotationMark, String key) {
      this.annotationMark = annotationMark;
      this.key = key;
    }

    @Override
    public String apply(TregexMatcher m) {
      String node = m.getNode(key).value();
      String eqClass = tlp.basicCategory(node);

      if(nnTagClass.contains(eqClass))
        eqClass = "noun";
      else if(jjTagClass.contains(eqClass))
        eqClass = "adj";
      else if(vbTagClass.contains(eqClass))
        eqClass = "vb";

      return annotationMark + eqClass;
    }

    @Override
    public String toString() { return "AddEquivalencedConjNode[" + annotationMark + ',' + key + ']'; }

    private static final long serialVersionUID = 1L;
  }

  /**
   * Reconfigures active features after a change in the default headfinder.
   *
   * @param hf
   */
  private void setHeadFinder(HeadFinder hf) {
    if(hf == null)
      throw new IllegalArgumentException();

    headFinder = hf;

    // Need to re-initialize all patterns due to the new headFinder
    initializeAnnotationPatterns();

    activeAnnotations.clear();

    for(String key : baselineFeatures) {
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(key);
      activeAnnotations.add(p);
    }
    for(String key : additionalFeatures) {
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(key);
      activeAnnotations.add(p);
    }
  }

  /**
   * Configures morpho-syntactic annotations for POS tags.
   *
   * @param activeFeats A comma-separated list of feature values with names according
   * to MorphoFeatureType.
   *
   */
  private String setupMorphoFeatures(String activeFeats) {
    String[] feats = activeFeats.split(",");
    morphoSpec = tlp.morphFeatureSpec();
    for(String feat : feats) {
      MorphoFeatureType fType = MorphoFeatureType.valueOf(feat.trim());
      morphoSpec.activate(fType);
    }
    return morphoSpec.toString();
  }

  private void removeBaselineFeature(String featName) {
    if(baselineFeatures.contains(featName)) {
      baselineFeatures.remove(featName);
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(featName);
      activeAnnotations.remove(p);
    }
  }

  @Override
  public void display() {
    log.info(optionsString.toString());
  }

  /** Some options for setOptionFlag:
   *
   * <p>
   * {@code -retainNPTmp} Retain temporal NP marking on NPs.
   * {@code -retainNPSbj} Retain NP subject function tags
   * {@code -markGappedVP} marked gapped VPs.
   * {@code -collinizerRetainsPunctuation} does what it says.
   * </p>
   *
   * @param args flag arguments (usually from commmand line
   * @param i index at which to begin argument processing
   * @return Index in args array after the last processed index for option
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    //log.info("Setting option flag: "  + args[i]);

    //lang. specific options
    boolean didSomething = false;
    if (annotationPatterns.keySet().contains(args[i])) {
      if(!baselineFeatures.contains(args[i])) additionalFeatures.add(args[i]);
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(args[i]);
      activeAnnotations.add(p);
      optionsString.append("Option " + args[i] + " added annotation pattern " + p.first() + " with annotation " + p.second() + '\n');
      didSomething = true;

    } else if (args[i].equals("-retainNPTmp")) {
      optionsString.append("Retaining NP-TMP marking.\n");
      retainNPTmp = true;
      didSomething = true;

    } else if (args[i].equals("-retainNPSbj")) {
      optionsString.append("Retaining NP-SBJ dash tag.\n");
      retainNPSbj = true;
      didSomething = true;

    } else if (args[i].equals("-retainPPClr")) {
      optionsString.append("Retaining PP-CLR dash tag.\n");
      retainPPClr = true;
      didSomething = true;

    } else if (args[i].equals("-discardX")) {
      optionsString.append("Discarding X trees.\n");
      discardX = true;
      didSomething = true;

    } else if (args[i].equals("-changeNoLabels")) {
      optionsString.append("Change no labels.\n");
      changeNoLabels = true;
      didSomething = true;

    } else if (args[i].equals("-markPRDverbs")) {
      optionsString.append("Mark PRD.\n");
      retainPRD = true;
      didSomething = true;

    } else if (args[i].equals("-collinizerRetainsPunctuation")) {
      optionsString.append("Collinizer retains punctuation.\n");
      collinizerRetainsPunctuation = true;
      didSomething = true;

    } else if (args[i].equals("-arabicFactored")) {
      for(String annotation : baselineFeatures) {
        String[] a = {annotation};
        setOptionFlag(a,0);
      }
      didSomething = true;

    } else if (args[i].equalsIgnoreCase("-headFinder") && (i + 1 < args.length)) {
      try {
        HeadFinder hf = (HeadFinder) Class.forName(args[i + 1]).getDeclaredConstructor().newInstance();
        setHeadFinder(hf);
        optionsString.append("HeadFinder: " + args[i + 1] + "\n");

      } catch (Exception e) {
        log.info(e);
        log.info(this.getClass().getName() +
                           ": Could not load head finder " + args[i + 1]);
      }
      i++;
      didSomething = true;

    } else if(args[i].equals("-factlex") && (i + 1 < args.length)) {
      String activeFeats = setupMorphoFeatures(args[++i]);
      optionsString.append("Factored Lexicon: active features: ").append(activeFeats);
//
//      removeBaselineFeature("-markFem");
//      optionsString.append(" (removed -markFem)\n");

      didSomething = true;

    } else if(args[i].equals("-noFeatures")) {
      activeAnnotations.clear();
      optionsString.append("Removed all manual features.\n");

      didSomething = true;
    }
    //wsg2010: The segmenter does not work, but keep this to remember how it was instantiated.
    //    else if (args[i].equals("-arabicTokenizerModel")) {
    //      String modelFile = args[i+1];
    //      try {
    //        WordSegmenter aSeg = (WordSegmenter) Class.forName("edu.stanford.nlp.wordseg.ArabicSegmenter").newInstance();
    //        aSeg.loadSegmenter(modelFile);
    //        System.out.println("aSeg=" + aSeg);
    //        TokenizerFactory<Word> aTF = WordSegmentingTokenizer.factory(aSeg);
    //        ((ArabicTreebankLanguagePack) treebankLanguagePack()).setTokenizerFactory(aTF);
    //      } catch (RuntimeIOException ex) {
    //        log.info("Couldn't load ArabicSegmenter " + modelFile);
    //        ex.printStackTrace();
    //      } catch (Exception e) {
    //        log.info("Couldn't instantiate segmenter: edu.stanford.nlp.wordseg.ArabicSegmenter");
    //        e.printStackTrace();
    //      }
    //      i++; // 2 args
    //      didSomething = true;
    //    }

    if (didSomething) i++;

    return i;
  }


  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      System.exit(-1);
    }

    ArabicTreebankParserParams tlpp = new ArabicTreebankParserParams();
    String[] options = {"-arabicFactored"};
    tlpp.setOptionFlag(options, 0);
    DiskTreebank tb = tlpp.diskTreebank();
    tb.loadPath(args[0], "txt", false);

    for(Tree t : tb) {
      for(Tree subtree : t) {
        tlpp.transformTree(subtree, t);
      }
      System.out.println(t);
    }
  }
}
