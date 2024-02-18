package edu.stanford.nlp.parser.metrics; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import edu.stanford.nlp.io.NullOutputStream;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.parser.common.NoSuchParseException;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserQueryFactory;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.parser.common.ParsingThreadsafeProcessor;
import edu.stanford.nlp.parser.lexparser.AbstractCollinizer;
import edu.stanford.nlp.parser.lexparser.BoundaryRemover;
import edu.stanford.nlp.parser.lexparser.Debinarizer;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreeAnnotatorAndBinarizer;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.LeftHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;

public class EvaluateTreebank  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(EvaluateTreebank.class);

  private final Options op;
  private final TreeTransformer debinarizer;
  private final TreeTransformer subcategoryStripper;
  private final AbstractCollinizer collinizer;
  private final TreeTransformer boundaryRemover;

  private final ParserQueryFactory pqFactory;

  // private final Lexicon lex;

  final List<Eval> evals;
  final List<ParserQueryEval> parserQueryEvals;

  private final boolean summary;
  private final boolean tsv;

  // no annotation
  private final TreeAnnotatorAndBinarizer binarizerOnly;

  AbstractEval pcfgLB = null;
  AbstractEval pcfgChildSpecific = null;
  LeafAncestorEval pcfgLA = null;
  AbstractEval pcfgCB = null;
  AbstractEval pcfgDA = null;
  AbstractEval pcfgTA = null;
  AbstractEval depDA = null;
  AbstractEval depTA = null;
  AbstractEval factLB = null;
  AbstractEval factChildSpecific = null;
  LeafAncestorEval factLA = null;
  AbstractEval factCB = null;
  AbstractEval factDA = null;
  AbstractEval factTA = null;
  AbstractEval pcfgRUO = null;
  AbstractEval pcfgCUO = null;
  AbstractEval pcfgCatE = null;
  AbstractEval.ScoreEval pcfgLL = null;
  AbstractEval.ScoreEval depLL = null;
  AbstractEval.ScoreEval factLL = null;
  AbstractEval kGoodLB = null;

  List<Double> factLBHistory = null;

  BestOfTopKEval pcfgTopK = null;
  private final List<BestOfTopKEval> topKEvals = new ArrayList<>();

  private int kbestPCFG = 0;

  private int numSkippedEvals = 0;

  private boolean saidMemMessage = false;

  /**
   * The tagger optionally used before parsing.
   * <br>
   * We keep it here as a function rather than a MaxentTagger so that
   * we can distribute a version of the parser that doesn't include
   * the entire tagger.
   */
  protected final Function<List<? extends HasWord>, List<TaggedWord>> tagger;

  /** we will multiply by this constant instead of divide by log(2) */
  private static final double LN_TO_LOG2 = 1. / Math.log(2);

  public EvaluateTreebank(LexicalizedParser parser) {
    this(parser.getOp(), parser.lex, parser);
  }

  public EvaluateTreebank(Options op, Lexicon lex, ParserGrammar pqFactory) {
    this(op, lex, pqFactory, pqFactory.loadTagger(), pqFactory.getExtraEvals(), pqFactory.getParserQueryEvals());
  }

  public EvaluateTreebank(Options op, Lexicon lex, ParserQueryFactory pqFactory, Function<List<? extends HasWord>,List<TaggedWord>> tagger,
                          List<Eval> extraEvals, List<ParserQueryEval> parserQueryEvals) {
    this.op = op;
    this.debinarizer = new Debinarizer(op.forceCNF);
    this.subcategoryStripper = op.tlpParams.subcategoryStripper();

    this.evals = new ArrayList<>();
    if (extraEvals != null) {
      this.evals.addAll(extraEvals);
    }
    this.parserQueryEvals = new ArrayList<>();
    if (parserQueryEvals != null) {
      this.parserQueryEvals.addAll(parserQueryEvals);
    }

    // this.lex = lex;
    this.pqFactory = pqFactory;

    this.tagger = tagger;

    collinizer = op.tlpParams.collinizer();
    boundaryRemover = new BoundaryRemover();

    boolean runningAverages = Boolean.parseBoolean(op.testOptions.evals.getProperty("runningAverages"));
    summary = Boolean.parseBoolean(op.testOptions.evals.getProperty("summary"));
    tsv = Boolean.parseBoolean(op.testOptions.evals.getProperty("tsv"));

    if (!op.trainOptions.leftToRight) {
      binarizerOnly = new TreeAnnotatorAndBinarizer(op.tlpParams, op.forceCNF, false, false, op);
    } else {
      binarizerOnly = new TreeAnnotatorAndBinarizer(op.tlpParams.headFinder(), new LeftHeadFinder(), op.tlpParams, op.forceCNF, false, false, op);
    }


    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLB"))) {
      pcfgLB = new Evalb("pcfg LP/LR", runningAverages);
    }
    // TODO: might be nice to allow more than one child-specific scorer
    if (op.testOptions.evals.getProperty("pcfgChildSpecific") != null) {
      String filter = op.testOptions.evals.getProperty("pcfgChildSpecific");
      pcfgChildSpecific = FilteredEval.childFilteredEval("pcfg children matching " + filter + " LP/LR", runningAverages, op.langpack(), filter);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLA"))) {
      pcfgLA = new LeafAncestorEval("pcfg LeafAncestor");
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgCB"))) {
      pcfgCB = new Evalb.CBEval("pcfg CB", runningAverages);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgDA"))) {
      pcfgDA = new UnlabeledAttachmentEval("pcfg DA", runningAverages, op.langpack().headFinder());
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgTA"))) {
      pcfgTA = new TaggingEval("pcfg Tag", runningAverages, lex);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depDA"))) {
      depDA = new UnlabeledAttachmentEval("dep DA", runningAverages, null, op.langpack().punctuationWordRejectFilter());
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depTA"))) {
      depTA = new TaggingEval("dep Tag", runningAverages, lex);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLB"))) {
      factLB = new Evalb("factor LP/LR", runningAverages);
      factLBHistory = new ArrayList<>();
    }
    if (op.testOptions.evals.getProperty("factChildSpecific") != null) {
      String filter = op.testOptions.evals.getProperty("factChildSpecific");
      factChildSpecific = FilteredEval.childFilteredEval("fact children matching " + filter + " LP/LR", runningAverages, op.langpack(), filter);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLA"))) {
      factLA = new LeafAncestorEval("factor LeafAncestor");
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factCB"))) {
      factCB = new Evalb.CBEval("fact CB", runningAverages);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factDA"))) {
      factDA = new UnlabeledAttachmentEval("factor DA", runningAverages, null);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factTA"))) {
      factTA = new TaggingEval("factor Tag", runningAverages, lex);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgRUO"))) {
      pcfgRUO = new AbstractEval.RuleErrorEval("pcfg Rule under/over");
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgCUO"))) {
      pcfgCUO = new AbstractEval.CatErrorEval("pcfg Category under/over");
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgCatE"))) {
      pcfgCatE = new EvalbByCat("pcfg Category Eval", runningAverages);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLL"))) {
      pcfgLL = new AbstractEval.ScoreEval("pcfgLL", runningAverages);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depLL"))) {
      depLL = new AbstractEval.ScoreEval("depLL", runningAverages);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLL"))) {
      factLL = new AbstractEval.ScoreEval("factLL", runningAverages);
    }
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("topMatch"))) {
      evals.add(new TopMatchEval("topMatch", runningAverages));
    }
    // this one is for the various k Good/Best options.  Just for individual results
    kGoodLB = new Evalb("kGood LP/LR", false);

    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgTopK"))) {
      pcfgTopK = new BestOfTopKEval(new Evalb("pcfg top k comparisons", false), new Evalb("pcfg top k LP/LR", runningAverages));
      topKEvals.add(pcfgTopK);
    }

    if (topKEvals.size() > 0) {
      kbestPCFG = op.testOptions.evalPCFGkBest;
    }
    if (op.testOptions.printPCFGkBest > 0) {
      kbestPCFG = Math.max(kbestPCFG, op.testOptions.printPCFGkBest);
    }

  }

  public double getLBScore() {
    if (factLB != null) {
      return factLB.getEvalbF1Percent();
    }
    if (pcfgLB != null) {
      return pcfgLB.getEvalbF1Percent();
    }
    return 0.0;
  }

  public double getTagScore() {
    if (factTA != null) {
      return factTA.getEvalbF1Percent();
    }
    if (pcfgTA != null) {
      return pcfgTA.getEvalbF1Percent();
    }
    return 0.0;
  }

  public double getPCFGTopKF1() {
    if (pcfgTopK == null) {
      return 0.0;
    }
    return pcfgTopK.getEvalbF1();
  }

  public boolean hasPCFGTopKF1() {
    return pcfgTopK != null;
  }

  public List<Double> getF1History() {
    return Collections.unmodifiableList(factLBHistory);
  }

  /**
   * Remove tree scores, so they don't print.
   * <br>
   * TODO: The printing architecture should be fixed up in the trees package
   * sometime.
   */
  private static void nanScores(Tree tree) {
    tree.setScore(Double.NaN);
    Tree[] kids = tree.children();
    for (Tree kid : kids) {
      nanScores(kid);
    }
  }

  public void processResults(ParserQuery pq, Tree goldTree, PrintWriter pwErr, PrintWriter pwOut, PrintWriter pwFileOut, PrintWriter pwStats, TreePrint treePrint) {
      if (pq.saidMemMessage()) {
        saidMemMessage = true;
      }

      Tree tree;
      List<? extends HasWord> sentence = pq.originalSentence();
      try {
        tree = pq.getBestParse();
      } catch (NoSuchParseException e) {
        tree = null;
      }

      List<ScoredObject<Tree>> kbestPCFGTrees = null;
      if (tree != null && kbestPCFG > 0) {
        kbestPCFGTrees = pq.getKBestPCFGParses(kbestPCFG);
      }

      //combo parse goes to pwOut (System.out)
      if (op.testOptions.verbose) {
        pwOut.println("ComboParser best");
        Tree ot = tree;
        if (ot != null && ! op.tlpParams.treebankLanguagePack().isStartSymbol(ot.value())) {
          ot = ot.treeFactory().newTreeNode(op.tlpParams.treebankLanguagePack().startSymbol(), Collections.singletonList(ot));
        }
        treePrint.printTree(ot, pwOut);
      } else {
        treePrint.printTree(tree, pwOut);
      }

      // **OUTPUT**
      // print various n-best like outputs (including 1-best)
      // print various statistics
      if (tree != null) {
        if(op.testOptions.printAllBestParses) {
          List<ScoredObject<Tree>> parses = pq.getBestPCFGParses();
          int sz = parses.size();
          if (sz > 1) {
            pwOut.println("There were " + sz + " best PCFG parses with score " + parses.get(0).score() + '.');
            Tree transGoldTree = collinizer.transformTree(goldTree, goldTree);
            int iii = 0;
            for (ScoredObject<Tree> sot : parses) {
              iii++;
              Tree tb = sot.object();
              Tree tbd = debinarizer.transformTree(tb);
              tbd = subcategoryStripper.transformTree(tbd);
              pq.restoreOriginalWords(tbd);
              pwOut.println("PCFG Parse #" + iii + " with score " + tbd.score());
              tbd.pennPrint(pwOut);
              Tree tbtr = collinizer.transformTree(tbd, goldTree);
              // pwOut.println("Tree size = " + tbtr.size() + "; depth = " + tbtr.depth());
              kGoodLB.evaluate(tbtr, transGoldTree, pwErr);
            }
          }
        }
        // Huang and Chiang (2006) Algorithm 3 output from the PCFG parser
        else if (op.testOptions.printPCFGkBest > 0 && op.testOptions.outputkBestEquivocation == null) {
          List<ScoredObject<Tree>> trees = kbestPCFGTrees.subList(0, op.testOptions.printPCFGkBest);
          Tree transGoldTree = collinizer.transformTree(goldTree, goldTree);
          int i = 0;
          for (ScoredObject<Tree> tp : trees) {
            i++;
            pwOut.println("PCFG Parse #" + i + " with score " + tp.score());
            Tree tbd = tp.object();
            tbd.pennPrint(pwOut);
            Tree tbtr = collinizer.transformTree(tbd, goldTree);
            kGoodLB.evaluate(tbtr, transGoldTree, pwErr);
          }
        }
        // Chart parser (factored) n-best list
        else if (op.testOptions.printFactoredKGood > 0 && pq.hasFactoredParse()) {
          // DZ: debug n best trees
          List<ScoredObject<Tree>> trees = pq.getKGoodFactoredParses(op.testOptions.printFactoredKGood);
          Tree transGoldTree = collinizer.transformTree(goldTree, goldTree);
          int ii = 0;
          for (ScoredObject<Tree> tp : trees) {
            ii++;
            pwOut.println("Factored Parse #" + ii + " with score " + tp.score());
            Tree tbd = tp.object();
            tbd.pennPrint(pwOut);
            Tree tbtr = collinizer.transformTree(tbd, goldTree);
            kGoodLB.evaluate(tbtr, transGoldTree, pwOut);
          }
        }
        //1-best output
        else if(pwFileOut != null) {
          pwFileOut.println(tree.toString());
        }

        //Print the derivational entropy
        if(op.testOptions.outputkBestEquivocation != null && op.testOptions.printPCFGkBest > 0) {
          List<ScoredObject<Tree>> trees = kbestPCFGTrees.subList(0, op.testOptions.printPCFGkBest);

          double[] logScores = new double[trees.size()];
          int treeId = 0;
          for(ScoredObject<Tree> kBestTree : trees)
            logScores[treeId++] = kBestTree.score();

          //Re-normalize
          double entropy = 0.0;
          double denom = ArrayMath.logSum(logScores);
          for (double logScore : logScores) {
            double logPr = logScore - denom;
            entropy += Math.exp(logPr) * logPr * LN_TO_LOG2;
          }
          entropy *= -1; //Convert to bits
          pwStats.printf("%f\t%d\t%d\n", entropy,trees.size(),sentence.size());
        }
      }


      // **EVALUATION**
      // Perform various evaluations specified by the user
      if (tree != null) {
        //Strip subcategories and remove punctuation for evaluation
        tree = subcategoryStripper.transformTree(tree);
        Tree treeFact = collinizer.transformTree(tree, goldTree);

        //Setup the gold tree
        if (op.testOptions.verbose) {
          pwOut.println("Correct parse");
          treePrint.printTree(goldTree, pwOut);
        }
        Tree transGoldTree = collinizer.transformTree(goldTree, goldTree);
        if(transGoldTree != null)
          transGoldTree = subcategoryStripper.transformTree(transGoldTree);

        //Can't do evaluation in these two cases
        if (transGoldTree == null) {
          pwErr.println("Couldn't transform gold tree for evaluation, skipping eval. Gold tree was:");
          goldTree.pennPrint(pwErr);
          numSkippedEvals++;
          return;

        } else if (treeFact == null) {
          pwErr.println("Couldn't transform hypothesis tree for evaluation, skipping eval. Tree was:");
          tree.pennPrint(pwErr);
          numSkippedEvals++;
          return;

        } else if(treeFact.yield().size() != transGoldTree.yield().size()) {
          List<Label> fYield = treeFact.yield();
          List<Label> gYield = transGoldTree.yield();
          pwErr.println("WARNING: Evaluation could not be performed due to gold/parsed yield mismatch.");
          pwErr.printf("  sizes: gold: %d (transf) %d (orig); parsed: %d (transf) %d (orig).%n", gYield.size(), goldTree.yield().size(),
                       fYield.size(), tree.yield().size());
          pwErr.println("  gold: " + SentenceUtils.listToString(gYield, true));
          pwErr.println("  pars: " + SentenceUtils.listToString(fYield, true));
          numSkippedEvals++;
          return;
        }

        if (topKEvals.size() > 0) {
          List<Tree> transGuesses = new ArrayList<>();
          int kbest = Math.min(op.testOptions.evalPCFGkBest, kbestPCFGTrees.size());
          for (ScoredObject<Tree> guess : kbestPCFGTrees.subList(0, kbest)) {
            transGuesses.add(collinizer.transformTree(guess.object(), goldTree));
          }
          for (BestOfTopKEval eval : topKEvals) {
            eval.evaluate(transGuesses, transGoldTree, pwErr);
          }
        }

        //PCFG eval
        Tree treePCFG = pq.getBestPCFGParse();
        if (treePCFG != null) {
          Tree treePCFGeval = collinizer.transformTree(treePCFG, goldTree);
          if (pcfgLB != null) {
            pcfgLB.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgChildSpecific != null) {
            pcfgChildSpecific.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if(pcfgLA != null) {
            pcfgLA.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgCB != null) {
            pcfgCB.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgDA != null) {
            // Re-index the leaves after Collinization, stripping traces, etc.
            treePCFGeval.indexLeaves(true);
            transGoldTree.indexLeaves(true);
            pcfgDA.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgTA != null) {
            pcfgTA.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgLL != null && pq.getPCFGParser() != null) {
            pcfgLL.recordScore(pq.getPCFGParser(), pwErr);
          }
          if (pcfgRUO != null) {
            pcfgRUO.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgCUO != null) {
            pcfgCUO.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
          if (pcfgCatE != null) {
            pcfgCatE.evaluate(treePCFGeval, transGoldTree, pwErr);
          }
        }

        //Dependency eval
        // todo: is treeDep really useful here, or should we really use depDAEval tree (debinarized) throughout? We use it for parse, and it sure seems like we could use it for tag eval, but maybe not factDA?
        Tree treeDep = pq.getBestDependencyParse(false);
        if (treeDep != null) {
          Tree goldTreeB = binarizerOnly.transformTree(goldTree);

          Tree goldTreeEval = goldTree.deepCopy();
          goldTreeEval.indexLeaves(true);
          goldTreeEval.percolateHeads(op.langpack().headFinder());

          Tree depDAEval = pq.getBestDependencyParse(true);
          depDAEval.indexLeaves(true);
          depDAEval.percolateHeadIndices();
          if (depDA != null) {
            depDA.evaluate(depDAEval, goldTreeEval, pwErr);
          }
          if (depTA != null) {
            Tree undoneTree = debinarizer.transformTree(treeDep);
            undoneTree = subcategoryStripper.transformTree(undoneTree);
            pq.restoreOriginalWords(undoneTree);
            // pwErr.println("subcategoryStripped tree: " + undoneTree.toStructureDebugString());
            depTA.evaluate(undoneTree, goldTree, pwErr);
          }
          if (depLL != null && pq.getDependencyParser() != null) {
            depLL.recordScore(pq.getDependencyParser(), pwErr);
          }
          Tree factTreeB;
          if (pq.hasFactoredParse()) {
            factTreeB = pq.getBestFactoredParse();
          } else {
            factTreeB = treeDep;
          }
          if (factDA != null) {
            factDA.evaluate(factTreeB, goldTreeB, pwErr);
          }
        }

        //Factored parser (1best) eval
        if (factLB != null) {
          factLB.evaluate(treeFact, transGoldTree, pwErr);
          factLBHistory.add(factLB.getLastF1());
        }
        if (factChildSpecific != null) {
          factChildSpecific.evaluate(treeFact, transGoldTree, pwErr);
        }
        if(factLA != null) {
          factLA.evaluate(treeFact, transGoldTree, pwErr);
        }
        if (factTA != null) {
          factTA.evaluate(tree, boundaryRemover.transformTree(goldTree), pwErr);
        }
        if (factLL != null && pq.getFactoredParser() != null) {
          factLL.recordScore(pq.getFactoredParser(), pwErr);
        }
        if (factCB != null) {
          factCB.evaluate(treeFact, transGoldTree, pwErr);
        }
        for (Eval eval : evals) {
          eval.evaluate(treeFact, transGoldTree, pwErr);
        }
        for (ParserQueryEval eval : parserQueryEvals) {
          eval.evaluate(pq, transGoldTree, pwErr);
        }
        if (op.testOptions.evalb) {
          // empty out scores just in case
          nanScores(tree);
          EvalbFormatWriter.writeEVALBline(treeFact, transGoldTree);
        }
      }
      pwErr.println();
  }

  /**
   * Wrapper for a way to pass in a dataset which may need reprocessing to get parse results
   */
  public static interface EvaluationDataset {
    void processDataset(PrintWriter pwErr, PrintWriter pwOut, PrintWriter pwFileOut, PrintWriter pwStats, TreePrint treePrint, BiConsumer<ParserQuery, Tree> processResults);

    void summarize(PrintWriter pwErr, TreebankLanguagePack tlp);
  }

  public static class TreebankEvaluationDataset implements EvaluationDataset {
    final Treebank testTreebank;
    final ParserQueryFactory pqFactory;
    final Options op;
    final Function<List<? extends HasWord>, List<TaggedWord>> tagger;

    public TreebankEvaluationDataset(Treebank testTreebank, ParserQueryFactory pqFactory, Options op, Function<List<? extends HasWord>, List<TaggedWord>> tagger) {
      this.pqFactory = pqFactory;
      this.testTreebank = testTreebank;
      this.op = op;
      this.tagger = tagger;
    }

    /**
     * Returns an input sentence based on this tree for use in the parser.
     */
    private List<CoreLabel> getInputSentence(Tree t) {
      if (op.testOptions.forceTags) {
        if (op.testOptions.preTag) {
          List<TaggedWord> s = tagger.apply(t.yieldWords());
          if(op.testOptions.verbose) {
            log.info("Guess tags: "+Arrays.toString(s.toArray()));
            log.info("Gold tags: "+t.labeledYield().toString());
          }
          return SentenceUtils.toCoreLabelList(s);
        } else if(op.testOptions.noFunctionalForcing) {
          ArrayList<? extends HasWord> s = t.taggedYield();
          for (HasWord word : s) {
            String tag = ((HasTag) word).tag();
            tag = tag.split("-")[0];
            ((HasTag) word).setTag(tag);
          }
          return SentenceUtils.toCoreLabelList(s);
        } else {
          return SentenceUtils.toCoreLabelList(t.taggedYield());
        }
      } else {
        return SentenceUtils.toCoreLabelList(t.yieldWords());
      }
    }

    public void processDataset(PrintWriter pwErr, PrintWriter pwOut, PrintWriter pwFileOut, PrintWriter pwStats, TreePrint treePrint, BiConsumer<ParserQuery, Tree> processResults) {

      if (op.testOptions.testingThreads != 1) {
        MulticoreWrapper<List<? extends HasWord>, ParserQuery> wrapper = new MulticoreWrapper<>(op.testOptions.testingThreads, new ParsingThreadsafeProcessor(pqFactory, pwErr));

        LinkedList<Tree> goldTrees = new LinkedList<>();
        for (Tree goldTree : testTreebank) {
          List<? extends HasWord> sentence = getInputSentence(goldTree);
          goldTrees.add(goldTree);

          pwErr.println("Parsing [len. " + sentence.size() + "]: " + SentenceUtils.listToString(sentence));
          wrapper.put(sentence);
          while (wrapper.peek()) {
            ParserQuery pq = wrapper.poll();
            goldTree = goldTrees.poll();
            processResults.accept(pq, goldTree);
          }
        } // for tree iterator
        wrapper.join();
        while (wrapper.peek()) {
          ParserQuery pq = wrapper.poll();
          Tree goldTree = goldTrees.poll();
          processResults.accept(pq, goldTree);
        }
      } else {
        ParserQuery pq = pqFactory.parserQuery();
        for (Tree goldTree : testTreebank) {
          final List<CoreLabel> sentence = getInputSentence(goldTree);

          pwErr.println("Parsing [len. " + sentence.size() + "]: " + SentenceUtils.listToString(sentence));

          pq.parseAndReport(sentence, pwErr);
          processResults.accept(pq, goldTree);
        } // for tree iterator
      }
    }

    public void summarize(PrintWriter pwErr, TreebankLanguagePack tlp) {
      pwErr.print("Testing ");
      pwErr.println(testTreebank.textualSummary(tlp));
    }
  }

  /**
   * Wrapper for a dataset which was already parsed, such as that passed in to EvaluateExternalParser.
   * <br>
   * Using this and the EvaluationDataset in general allows for scoring already known results and
   * the results of a parser on raw text with the same codepaths
   */
  public static class PreparsedEvaluationDataset implements EvaluationDataset {
    List<Pair<ParserQuery, Tree>> testTreebank;

    public PreparsedEvaluationDataset(List<Pair<ParserQuery, Tree>> testTreebank) {
      this.testTreebank = testTreebank;
    }

    public void processDataset(PrintWriter pwErr, PrintWriter pwOut, PrintWriter pwFileOut, PrintWriter pwStats, TreePrint treePrint, BiConsumer<ParserQuery, Tree> processResults) {
      for (Pair<ParserQuery, Tree> result : testTreebank) {
        processResults.accept(result.first, result.second);
      }
    }

    public void summarize(PrintWriter pwErr, TreebankLanguagePack tlp) {
      // TODO: could pass in a summary, but we haven't done that yet
    }
  }

  /** Test the parser on a treebank. Parses will be written to stdout, and
   *  various other information will be written to stderr and stdout,
   *  particularly if <code>op.testOptions.verbose</code> is true.
   *
   *  @param testTreebank The treebank to parse
   *  @return The labeled precision/recall F<sub>1</sub> (EVALB measure)
   *          of the parser on the treebank.
   */
  public double testOnTreebank(Treebank testTreebank) {
    return testOnTreebank(new TreebankEvaluationDataset(testTreebank, pqFactory, op, tagger));
  }

  public double testOnTreebank(List<Pair<ParserQuery, Tree>> testTreebank) {
    return testOnTreebank(new PreparsedEvaluationDataset(testTreebank));
  }

  public double testOnTreebank(EvaluationDataset testTreebank) {
    if (summary || !op.testOptions.quietEvaluation) {
      log.info("Testing on treebank");
    }
    Timing treebankTotalTimer = (summary || !op.testOptions.quietEvaluation) ? new Timing() : null;
    TreePrint treePrint = op.testOptions.treePrint(op.tlpParams);
    TreebankLangParserParams tlpParams = op.tlpParams;
    TreebankLanguagePack tlp = op.langpack();
    PrintWriter pwOut, pwEvalErr;
    if (op.testOptions.quietEvaluation) {
      NullOutputStream quiet = new NullOutputStream();
      pwOut = tlpParams.pw(quiet);
      pwEvalErr = tlpParams.pw(quiet);
    } else {
      pwOut = tlpParams.pw();
      pwEvalErr = tlpParams.pw(System.err);
    }
    if (op.testOptions.verbose) {
      testTreebank.summarize(pwEvalErr, tlp);
    }
    if (op.testOptions.evalb) {
      EvalbFormatWriter.initEVALBfiles(tlpParams);
    }

    final PrintWriter pwFileOut;
    if (op.testOptions.writeOutputFiles) {
      String fname = op.testOptions.outputFilesPrefix + "." + op.testOptions.outputFilesExtension;
      try {
        pwFileOut = op.tlpParams.pw(new FileOutputStream(fname));
      } catch (IOException ioe) {
        throw new RuntimeIOException(ioe);
      }
    } else {
      pwFileOut = null;
    }

    final PrintWriter pwStats;
    if (op.testOptions.outputkBestEquivocation != null) {
      try {
        pwStats = op.tlpParams.pw(new FileOutputStream(op.testOptions.outputkBestEquivocation));
      } catch(IOException ioe) {
        throw new RuntimeIOException(ioe);
      }
    } else {
      pwStats = null;
    }

    testTreebank.processDataset(pwEvalErr, pwOut, pwFileOut, pwStats, treePrint,
                                (pq, goldTree) -> processResults(pq, goldTree, pwEvalErr, pwOut, pwFileOut, pwStats, treePrint));

    //Done parsing...print the results of the evaluations
    if (treebankTotalTimer != null) {
      treebankTotalTimer.done("Testing on treebank");
    }
    PrintWriter pwErr = tlpParams.pw(System.err);
    if (saidMemMessage) {
      ParserUtils.printOutOfMemory(pwErr);
    }
    if (op.testOptions.evalb) {
      EvalbFormatWriter.closeEVALBfiles();
    }
    if(numSkippedEvals != 0) {
      pwErr.printf("Unable to evaluate %d parser hypotheses due to yield mismatch\n",numSkippedEvals);
    }
    // only created here so we know what parser types are supported...
    // TODO: pass in the various pcfgparser, dependencyparser, etc?
    ParserQuery pq = pqFactory != null ? pqFactory.parserQuery() : null;
    if (summary) {
      if (pcfgLB != null) pcfgLB.display(false, pwErr);
      if (pcfgChildSpecific != null) pcfgChildSpecific.display(false, pwErr);
      if (pcfgLA != null) pcfgLA.display(false, pwErr);
      if (pcfgCB != null) pcfgCB.display(false, pwErr);
      if (pcfgDA != null) pcfgDA.display(false, pwErr);
      if (pcfgTA != null) pcfgTA.display(false, pwErr);
      if (pcfgLL != null && pq != null && pq.getPCFGParser() != null) pcfgLL.display(false, pwErr);
      if (depDA != null) depDA.display(false, pwErr);
      if (depTA != null) depTA.display(false, pwErr);
      if (depLL != null && pq != null && pq.getDependencyParser() != null) depLL.display(false, pwErr);
      if (factLB != null) factLB.display(false, pwErr);
      if (factChildSpecific != null) factChildSpecific.display(false, pwErr);
      if (factLA != null) factLA.display(false, pwErr);
      if (factCB != null) factCB.display(false, pwErr);
      if (factDA != null) factDA.display(false, pwErr);
      if (factTA != null) factTA.display(false, pwErr);
      if (factLL != null && pq != null && pq.getFactoredParser() != null) factLL.display(false, pwErr);
      if (pcfgCatE != null) pcfgCatE.display(false, pwErr);
      for (Eval eval : evals) {
        eval.display(false, pwErr);
      }
      for (BestOfTopKEval eval : topKEvals) {
        eval.display(false, pwErr);
      }
    }
    // these ones only have a display mode, so display if turned on!!
    if (pcfgRUO != null) pcfgRUO.display(true, pwErr);
    if (pcfgCUO != null) pcfgCUO.display(true, pwErr);
    if (tsv) {
      NumberFormat nf = new DecimalFormat("0.00");
      pwErr.println("factF1\tfactDA\tfactEx\tpcfgF1\tdepDA\tfactTA\tnum");
      if (factLB != null) pwErr.print(nf.format(factLB.getEvalbF1Percent()));
      pwErr.print("\t");
      if (pq != null && pq.getDependencyParser() != null && factDA != null) pwErr.print(nf.format(factDA.getEvalbF1Percent()));
      pwErr.print("\t");
      if (factLB != null) pwErr.print(nf.format(factLB.getExactPercent()));
      pwErr.print("\t");
      if (pcfgLB != null) pwErr.print(nf.format(pcfgLB.getEvalbF1Percent()));
      pwErr.print("\t");
      if (pq != null && pq.getDependencyParser() != null && depDA != null) pwErr.print(nf.format(depDA.getEvalbF1Percent()));
      pwErr.print("\t");
      if (pq != null && pq.getPCFGParser() != null && factTA != null) pwErr.print(nf.format(factTA.getEvalbF1Percent()));
      pwErr.print("\t");
      if (factLB != null) pwErr.print(factLB.getNum());
      pwErr.println();
    }

    double f1 = 0.0;
    if (factLB != null) {
      f1 = factLB.getEvalbF1();
    }

    //Close files (if necessary)
    if(pwFileOut != null) pwFileOut.close();
    if(pwStats != null) pwStats.close();

    for (ParserQueryEval parserQueryEval : parserQueryEvals) {
      parserQueryEval.display(false, pwErr);
    }

    return f1;
  } // end testOnTreebank()



}
