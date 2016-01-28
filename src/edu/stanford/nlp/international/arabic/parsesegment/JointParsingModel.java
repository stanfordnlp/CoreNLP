package edu.stanford.nlp.international.arabic.parsesegment;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.CategoryWordTagFactory;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

public class JointParsingModel {

  private boolean VERBOSE = false;

  //Factored parsing models (Klein and Manning, 2002)
  private static ExhaustivePCFGParser pparser;
  private static ExhaustiveDependencyParser dparser;
  private BiLexPCFGParser bparser;

  //Parser objects
  private Options op;
  private LexicalizedParser lp;
  private TreeTransformer debinarizer;
  private TreeTransformer subcategoryStripper;
  private TreePrint treePrint;
  private static List<CoreLabel> bestSegmentationB;

  private boolean serInput = false;
  private int maxSentLen = 5000;

  private static final int trainLengthLimit = 100000;

  public void setVerbose(boolean b) {
    VERBOSE = b;
    op.testOptions.verbose = b;
    op.trainOptions.printAnnotatedStateCounts = b;
    op.trainOptions.printAnnotatedRuleCounts = b;
  }

  public void setSerInput(boolean ser_input) { serInput = ser_input; }

  public void setMaxEvalSentLen(int maxSentLen) { this.maxSentLen = maxSentLen; }


  private void removeDeleteSplittersFromSplitters(TreebankLanguagePack tlp) {
    if (op.trainOptions.deleteSplitters != null) {
      List<String> deleted = new ArrayList<String>();
      for (String del : op.trainOptions.deleteSplitters) {
        String baseDel = tlp.basicCategory(del);
        boolean checkBasic = del.equals(baseDel);
        for (Iterator<String> it = op.trainOptions.splitters.iterator(); it.hasNext(); ) {
          String elem = it.next();
          String baseElem = tlp.basicCategory(elem);
          boolean delStr = checkBasic && baseElem.equals(baseDel) || elem.equals(del);
          if (delStr) {
            it.remove();
            deleted.add(elem);
          }
        }
      }
      if (op.testOptions.verbose) {
        System.err.println("Removed from vertical splitters: " + deleted);
      }
    }
  }

  public List<Tree> getAnnotatedBinaryTreebankFromTreebank(Treebank trainTreebank) {
    TreebankLangParserParams tlpParams = op.tlpParams;
    TreebankLanguagePack tlp = tlpParams.treebankLanguagePack();

    if (VERBOSE) System.err.println("\n\n" + trainTreebank.textualSummary(tlp));

    System.err.print("Binarizing trees...");
    TreeAnnotatorAndBinarizer binarizer = new TreeAnnotatorAndBinarizer(tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), true, op);
    Timing.tick("done.");

    if (op.trainOptions.selectiveSplit) {
      op.trainOptions.splitters = ParentAnnotationStats.getSplitCategories(trainTreebank, op.trainOptions.tagSelectiveSplit, 0, op.trainOptions.selectiveSplitCutOff, op.trainOptions.tagSelectiveSplitCutOff, tlp);
      removeDeleteSplittersFromSplitters(tlp);
      if (op.testOptions.verbose) {
        List<String> list = new ArrayList<String>(op.trainOptions.splitters);
        Collections.sort(list);
        System.err.println("Parent split categories: " + list);
      }
    }
    //		if (op.trainOptions.selectivePostSplit) {
    //			// Do all the transformations once just to learn selective splits on annotated categories
    //			TreeTransformer myTransformer = new TreeAnnotator(tlpParams.headFinder(), tlpParams);
    //			Treebank annotatedTB = trainTreebank.transform(myTransformer);
    //			op.trainOptions.postSplitters = ParentAnnotationStats.getSplitCategories(annotatedTB, true, 0, op.trainOptions.selectivePostSplitCutOff, op.trainOptions.tagSelectivePostSplitCutOff, tlp);
    //			if (op.testOptions.verbose) {
    //				System.err.println("Parent post annotation split categories: " + op.trainOptions.postSplitters);
    //			}
    //		}
    if (op.trainOptions.hSelSplit) {
      // We run through all the trees once just to gather counts for hSelSplit!
      int ptt = op.trainOptions.printTreeTransformations;
      op.trainOptions.printTreeTransformations = 0;
      binarizer.setDoSelectiveSplit(false);
      for (Tree tree : trainTreebank) {
        binarizer.transformTree(tree);
      }
      binarizer.setDoSelectiveSplit(true);
      op.trainOptions.printTreeTransformations = ptt;
    }

    //Tree transformation
    //
    List<Tree> binaryTrainTrees = new ArrayList<Tree>();
    for (Tree tree : trainTreebank) {
      tree = binarizer.transformTree(tree);
      if (tree.yield().size() - 1 <= trainLengthLimit) {
        binaryTrainTrees.add(tree);
      }
    }

    // WSGDEBUG: Lot's of stuff on the grammar
    //    if(VERBOSE) {
    //      binarizer.printStateCounts();
    //      binarizer.printRuleCounts();
    //    binarizer.dumpStats();
    //    }

    return binaryTrainTrees;
  }


  public LexicalizedParser getParserDataFromTreebank(Treebank trainTreebank) {

    System.err.print("Binarizing training trees...");
    List<Tree> binaryTrainTrees = getAnnotatedBinaryTreebankFromTreebank(trainTreebank);
    Timing.tick("done.");

    Index<String> stateIndex = new HashIndex<String>();

    System.err.print("Extracting PCFG...");
    Extractor<Pair<UnaryGrammar,BinaryGrammar>> bgExtractor = new BinaryGrammarExtractor(op, stateIndex);
    Pair<UnaryGrammar,BinaryGrammar> bgug = bgExtractor.extract(binaryTrainTrees);

    BinaryGrammar bg = bgug.second;
    bg.splitRules();

    UnaryGrammar ug = bgug.first;
    ug.purgeRules();
    Timing.tick("done.");

    System.err.print("Extracting Lexicon...");
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    Lexicon lex = op.tlpParams.lex(op, wordIndex, tagIndex);
    lex.initializeTraining(binaryTrainTrees.size());
    lex.train(binaryTrainTrees);
    lex.finishTraining();
    Timing.tick("done.");

    Extractor<DependencyGrammar> dgExtractor = op.tlpParams.dependencyGrammarExtractor(op, wordIndex, tagIndex);
    DependencyGrammar dg = null;
    if (op.doDep) {
      System.err.print("Extracting Dependencies...");
      dg = dgExtractor.extract(binaryTrainTrees);
      dg.setLexicon(lex);
      Timing.tick("done.");
    }

    System.err.println("Done extracting grammars and lexicon.");

    return new LexicalizedParser(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
  }

  private void makeParsers() {
    if (lp == null)
      throw new RuntimeException(this.getClass().getName() + ": Parser grammar does not exist");

    //a la (Klein and Manning, 2002)
    pparser = new ExhaustivePCFGParser(lp.bg, lp.ug, lp.lex, op, lp.stateIndex, lp.wordIndex, lp.tagIndex);
    dparser = new ExhaustiveDependencyParser(lp.dg, lp.lex, op, lp.wordIndex, lp.tagIndex);
    bparser = new BiLexPCFGParser(new GenericLatticeScorer(), pparser, dparser, lp.bg, lp.ug, lp.dg, lp.lex, op, lp.stateIndex, lp.wordIndex, lp.tagIndex);
  }

  private boolean parse(InputStream inputStream) {
    final LatticeXMLReader reader = new LatticeXMLReader();

    if(!reader.load(inputStream,serInput)) {
      System.err.printf("%s: Error loading input lattice xml from stdin\n", this.getClass().getName());
      return false;
    }

    System.err.printf("%s: Entering main parsing loop...\n", this.getClass().getName());

    int latticeNum = 0;
    int parseable = 0;
    int successes = 0;
    int fParseSucceeded = 0;
    for(final Lattice lattice : reader) {

      if (lattice.getNumNodes() > op.testOptions.maxLength + 1) {  // + 1 for boundary symbol
        System.err.printf("%s: Lattice %d too big! (%d nodes)\n",this.getClass().getName(),latticeNum,lattice.getNumNodes());
        latticeNum++;
        continue;
      }

      parseable++;

      //TODO This doesn't work for what we want. Check the implementation in ExhaustivePCFG parser
      //op.testOptions.constraints = lattice.getConstraints();

      try {
        Tree rawTree = null;
        if(op.doPCFG && pparser.parse(lattice)) {
          rawTree = pparser.getBestParse(); //1best segmentation
          bestSegmentationB = rawTree.yield(new ArrayList<CoreLabel>()); //has boundary symbol

          if(op.doDep && dparser.parse(bestSegmentationB)) {
            System.err.printf("%s: Dependency parse succeeded!\n", this.getClass().getName());
            if(bparser.parse(bestSegmentationB)) {
              System.err.printf("%s: Factored parse succeeded!\n", this.getClass().getName());
              rawTree = bparser.getBestParse();
              fParseSucceeded++;
            }

          } else {
            System.out.printf("%s: Dependency parse failed. Backing off to PCFG...\n", this.getClass().getName());
          }

        } else {
          System.out.printf("%s: WARNING: parsing failed for lattice %d\n", this.getClass().getName(), latticeNum);
        }

        //Post-process the tree
        if (rawTree == null) {
          System.out.printf("%s: WARNING: Could not extract best parse for lattice %d\n", this.getClass().getName(), latticeNum);

        } else {
          Tree t = debinarizer.transformTree(rawTree);
          t = subcategoryStripper.transformTree(t);
          treePrint.printTree(t);

          successes++;
        }

        //When a best parse can't be extracted
      } catch (Exception e) {
        System.out.printf("%s: WARNING: Could not extract best parse for lattice %d\n", this.getClass().getName(), latticeNum);
        e.printStackTrace();
      }

      latticeNum++;
    }

    System.err.println("===================================================================");
    System.err.println("===================================================================");
    System.err.println("Post mortem:");
    System.err.println("  Input:     " + latticeNum);
    System.err.println("  Parseable: " + parseable);
    System.err.println("  Parsed:    " + successes);
    System.err.println("  f_Parsed:  " + fParseSucceeded);
    System.err.println("  String %:  " + (int)((double) successes * 10000.0 / (double) parseable) / 100.0);

    return true;
  }


  public boolean run(File trainTreebankFile, File testTreebankFile, InputStream inputStream) {
    op = new Options();
    op.tlpParams = new ArabicTreebankParserParams();
    op.setOptions("-arabicFactored");
    op.testOptions.maxLength = maxSentLen;
    op.testOptions.MAX_ITEMS = 5000000; //500000 is the default for Arabic, but we have substantially more edges now
    op.testOptions.outputFormatOptions = "removeTopBracket,includePunctuationDependencies";

    // WSG: Just set this to some high value so that extractBestParse()
    // actually calls the lattice reader (e.g., this says that we can't have a word longer than
    // 80 characters...seems sensible for Arabic
    op.testOptions.maxSpanForTags = 80;

    treePrint = op.testOptions.treePrint(op.tlpParams);
    debinarizer = new Debinarizer(op.forceCNF, new CategoryWordTagFactory());
    subcategoryStripper = op.tlpParams.subcategoryStripper();

    Timing.startTime();

    final Treebank trainTreebank = op.tlpParams.diskTreebank();
    trainTreebank.loadPath(trainTreebankFile);

    lp = getParserDataFromTreebank(trainTreebank);

    makeParsers();

    if (VERBOSE) {
      op.display();
      String lexNumRules = (pparser != null) ? Integer.toString(lp.lex.numRules()): "";
      System.err.println("Grammar\tStates\tTags\tWords\tUnaryR\tBinaryR\tTaggings");
      System.err.println("Grammar\t" +
                         lp.stateIndex.size() + '\t' +
                         lp.tagIndex.size() + '\t' +
                         lp.wordIndex.size() + '\t' +
                         (pparser != null ? lp.ug.numRules(): "") + '\t' +
                         (pparser != null ? lp.bg.numRules(): "") + '\t' +
                         lexNumRules);
      System.err.println("ParserPack is " + op.tlpParams.getClass().getName());
      System.err.println("Lexicon is " + lp.lex.getClass().getName());
    }

    return parse(inputStream);
  }


  /*
   * pparser chart uses segmentation interstices; dparser uses 1best word
   * interstices. Convert between the two here for bparser.
   */
  private static class GenericLatticeScorer implements LatticeScorer {

    public Item convertItemSpan(Item item) {
      if(bestSegmentationB == null || bestSegmentationB.size() == 0)
        throw new RuntimeException(this.getClass().getName() + ": No 1best segmentation available");

      item.start = bestSegmentationB.get(item.start).beginPosition();
      item.end = bestSegmentationB.get(item.end - 1).endPosition();
      return item;
    }

    public double oScore(Edge edge) {
      final Edge latticeEdge = (Edge) convertItemSpan(new Edge(edge));

      double pOscore = pparser.oScore(latticeEdge);
      double dOscore = dparser.oScore(edge);

      return pOscore + dOscore;
    }

    public double iScore(Edge edge) {
      final Edge latticeEdge = (Edge) convertItemSpan(new Edge(edge));

      double pIscore = pparser.iScore(latticeEdge);
      double dIscore = dparser.iScore(edge);

      return pIscore + dIscore;
    }

    public boolean oPossible(Hook hook) {
      final Hook latticeHook = (Hook) convertItemSpan(new Hook(hook));

      return pparser.oPossible(latticeHook) && dparser.oPossible(hook);
    }

    public boolean iPossible(Hook hook) {
      final Hook latticeHook = (Hook) convertItemSpan(new Hook(hook));

      return pparser.iPossible(latticeHook) && dparser.iPossible(hook);
    }

    public boolean parse(List<? extends HasWord> words) {
      throw new UnsupportedOperationException(this.getClass().getName() + ": Does not support parse operation.");
    }
  }

}
