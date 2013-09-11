// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002 - 2011 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    parser-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/lex-parser.shtml

package edu.stanford.nlp.parser.lexparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasLemma;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.metrics.AbstractEval;
import edu.stanford.nlp.parser.metrics.BestOfTopKEval;
import edu.stanford.nlp.parser.metrics.UnlabeledAttachmentEval;
import edu.stanford.nlp.parser.metrics.EvalbByCat;
import edu.stanford.nlp.parser.metrics.Evalb;
import edu.stanford.nlp.parser.metrics.LeafAncestorEval;
import edu.stanford.nlp.parser.metrics.TaggingEval;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.DocumentPreprocessor.DocType;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.DeltaIndex;
import edu.stanford.nlp.util.Timing;

public class LexicalizedParserQuery {

  private final Options op;
  private final TreeTransformer debinarizer;

  /** The PCFG parser. */
  private final ExhaustivePCFGParser pparser;
  /** The dependency parser. */
  private final ExhaustiveDependencyParser dparser;
  /** The factored parser that combines the dependency and PCFG parsers. */
  private final KBestViterbiParser bparser;

  private final boolean fallbackToPCFG;

  private final TreeTransformer subcategoryStripper;

  private boolean parseSucceeded = false;

  /** In case the words are transformed in some way, these are the
   * original words */
  private List<String> originalWords = null;
  /** These are the original lemmas */
  private List<String> originalLemmas = null;

  /**
   * The tagger optionally used before parsing.
   * <br>
   * We keep it here as a function rather than a MaxentTagger so that
   * we can distribute a version of the parser that doesn't include
   * the entire tagger.
   * <br>
   * TODO: pass this in rather than create it here if we wind up using
   * this in more place.  Right now it's only used in testOnTreebank.
   */
  protected Function<List<? extends HasWord>, ArrayList<TaggedWord>> tagger;


  LexicalizedParserQuery(LexicalizedParser parser) {
    this.op = parser.getOp();

    BinaryGrammar bg = parser.bg;
    UnaryGrammar ug = parser.ug;
    Lexicon lex = parser.lex;
    DependencyGrammar dg = parser.dg;

    Index<String> stateIndex = parser.stateIndex;
    Index<String> wordIndex = new DeltaIndex<String>(parser.wordIndex);
    Index<String> tagIndex = parser.tagIndex;

    this.debinarizer = new Debinarizer(op.forceCNF);

    if (op.doPCFG) {
      if (op.testOptions.iterativeCKY) {
        pparser = new IterativeCKYPCFGParser(bg, ug, lex, op, stateIndex, wordIndex, tagIndex);
      } else {
        pparser = new ExhaustivePCFGParser(bg, ug, lex, op, stateIndex, wordIndex, tagIndex);
      }
    } else {
      pparser = null;
    }

    if (op.doDep) {
      dg.setLexicon(lex);
      if (!op.testOptions.useFastFactored) {
        dparser = new ExhaustiveDependencyParser(dg, lex, op, wordIndex, tagIndex);
      } else {
        dparser = null;
      }
    } else {
      dparser = null;
    }

    if (op.doDep && op.doPCFG) {
      if (op.testOptions.useFastFactored) {
        MLEDependencyGrammar mledg = (MLEDependencyGrammar) dg;
        int numToFind = 1;
        if (op.testOptions.printFactoredKGood > 0) {
          numToFind = op.testOptions.printFactoredKGood;
        }
        bparser = new FastFactoredParser(pparser, mledg, op, numToFind, wordIndex, tagIndex);
      } else {
        Scorer scorer = new TwinScorer(pparser, dparser);
        //Scorer scorer = parser;
        if (op.testOptions.useN5) {
          bparser = new BiLexPCFGParser.N5BiLexPCFGParser(scorer, pparser, dparser, bg, ug, dg, lex, op, stateIndex, wordIndex, tagIndex);
        } else {
          bparser = new BiLexPCFGParser(scorer, pparser, dparser, bg, ug, dg, lex, op, stateIndex, wordIndex, tagIndex);
        }
      }
    } else {
      bparser = null;
    }
    fallbackToPCFG = true;

    subcategoryStripper = op.tlpParams.subcategoryStripper();
  }

  public void setConstraints(List<ParserConstraint> constraints) {
    if (pparser != null) {
      pparser.setConstraints(constraints);
    }
  }

  /**
   * Parse a sentence represented as a List of tokens.
   * The text must already have been tokenized and
   * normalized into tokens that are appropriate to the treebank
   * which was used to train the parser.  The tokens can be of
   * multiple types, and the list items need not be homogeneous as to type
   * (in particular, only some words might be given tags):
   * <ul>
   * <li>If a token implements HasWord, then the word to be parsed is
   * given by its word() value.</li>
   * <li>If a token implements HasTag and the tag() value is not
   * null or the empty String, then the parser is strongly advised to assign
   * a part of speech tag that <i>begins</i> with this String.</li>
   * <li>Otherwise toString() is called on the token, and the returned
   * value is used as the word to be parsed.  In particular, if the
   * token is already a String, this means that the String is used as
   * the word to be parsed.</li>
   * </ul>
   *
   * @param sentence The sentence to parse
   * @return true Iff the sentence was accepted by the grammar
   * @throws UnsupportedOperationException If the Sentence is too long or
   *                                       of zero length or the parse
   *                                       otherwise fails for resource reasons
   */
  public boolean parse(List<? extends HasWord> sentence) {
    int length = sentence.size();
    if (length == 0) {
      throw new UnsupportedOperationException("Can't parse a zero-length sentence!");
    }

    if (op.wordFunction != null) {
      originalWords = new ArrayList<String>(sentence.size());
      originalLemmas = new ArrayList<String>(sentence.size());
      for (HasWord word : sentence) {
        originalWords.add(word.word());
        if (word instanceof HasLemma) {
          originalLemmas.add(((HasLemma) word).lemma());
        } else {
          originalLemmas.add(null);
        }
        word.setWord(op.wordFunction.apply(word.word()));
      }
    }

    List<HasWord> sentenceB = new ArrayList<HasWord>(sentence);
    if (op.testOptions.addMissingFinalPunctuation) {
      addSentenceFinalPunctIfNeeded(sentenceB, length);
    }
    if (length > op.testOptions.maxLength) {
      throw new UnsupportedOperationException("Sentence too long: length " + length);
    }
    TreePrint treePrint = getTreePrint();
    PrintWriter pwOut = op.tlpParams.pw();
    parseSucceeded = false;

    //Insert the boundary symbol
    if(sentence.get(0) instanceof CoreLabel) {
      CoreLabel boundary = new CoreLabel();
      boundary.setWord(Lexicon.BOUNDARY);
      boundary.setValue(Lexicon.BOUNDARY);
      boundary.setTag(Lexicon.BOUNDARY_TAG);
      boundary.setIndex(sentence.size()+1);//1-based indexing used in the parser
      sentenceB.add(boundary);
    } else {
      sentenceB.add(new TaggedWord(Lexicon.BOUNDARY, Lexicon.BOUNDARY_TAG));
    }

    if (op.doPCFG) {
      if (!pparser.parse(sentenceB)) {
        restoreOriginalWords(sentence);
        return parseSucceeded;
      }
      if (op.testOptions.verbose) {
        pwOut.println("PParser output");
        // getBestPCFGParse(false).pennPrint(pwOut); // with scores on nodes
        treePrint.printTree(getBestPCFGParse(false), pwOut); // without scores on nodes
      }
    }
    if (op.doDep && ! op.testOptions.useFastFactored) {
      if ( ! dparser.parse(sentenceB)) {
        restoreOriginalWords(sentence);
        return parseSucceeded;
      }
      // cdm nov 2006: should move these printing bits to the main printing section,
      // so don't calculate the best parse twice!
      if (op.testOptions.verbose) {
        pwOut.println("DParser output");
        treePrint.printTree(dparser.getBestParse(), pwOut);
      }
    }
    if (op.doPCFG && op.doDep) {
      if ( ! bparser.parse(sentenceB)) {
        restoreOriginalWords(sentence);
        return parseSucceeded;
      } else {
        parseSucceeded = true;
      }
    }
    restoreOriginalWords(sentence);
    return true;
  }

  private void restoreOriginalWords(List<? extends HasWord> sentence) {
    if (originalWords == null) {
      return;
    }
    if (sentence.size() != originalWords.size()) {
      return;
    }
    if (originalWords.size() != originalLemmas.size()) {
      throw new AssertionError("originalWords and originalLemmas of different sizes");
    }
    Iterator<String> wordsIterator = originalWords.iterator();
    Iterator<String> lemmasIterator = originalLemmas.iterator();
    for (HasWord word : sentence) {
      word.setWord(wordsIterator.next());
      String lemma = lemmasIterator.next();
      if ((word instanceof HasLemma) && (lemma != null)) {
        ((HasLemma) word).setLemma(lemma);
      }
    }
  }

  private void restoreOriginalWords(Tree tree) {
    if (originalWords == null || tree == null) {
      return;
    }
    List<Tree> leaves = tree.getLeaves();
    if (leaves.size() != originalWords.size()) {
      return;
    }
    if (originalWords.size() != originalLemmas.size()) {
      throw new AssertionError("originalWords and originalLemmas of different sizes");
    }
    Iterator<String> wordsIterator = originalWords.iterator();
    Iterator<String> lemmasIterator = originalLemmas.iterator();
    for (Tree leaf : leaves) {
      leaf.setValue(wordsIterator.next());
      String lemma = lemmasIterator.next();
      if ((leaf.label() instanceof HasLemma) && (lemma != null)) {
        ((HasLemma) leaf.label()).setLemma(lemma);
      }      
    }
  }

  /**
   * Parse a (speech) lattice with the PCFG parser.
   *
   * @param lr a lattice to parse
   * @return Whether the lattice could be parsed by the grammar
   */
  boolean parse(HTKLatticeReader lr) {
    TreePrint treePrint = getTreePrint();
    PrintWriter pwOut = op.tlpParams.pw();
    parseSucceeded = false;
    if (lr.getNumStates() > op.testOptions.maxLength + 1) {  // + 1 for boundary symbol
      throw new UnsupportedOperationException("Lattice too big: " + lr.getNumStates());
    }
    if (op.doPCFG) {
      if (!pparser.parse(lr)) {
        return parseSucceeded;
      }
      if (op.testOptions.verbose) {
        pwOut.println("PParser output");
        treePrint.printTree(getBestPCFGParse(false), pwOut);
      }
    }
    return true;
  }

  /**
   * Return the best parse of the sentence most recently parsed.
   * This will be from the factored parser, if it was used and it succeeded
   * else from the PCFG if it was used and succeed, else from the dependency
   * parser.
   *
   * @return The best tree
   * @throws NoSuchElementException If no previously successfully parsed
   *                                sentence
   */
  public Tree getBestParse() {
    return getBestParse(true);
  }

  Tree getBestParse(boolean stripSubcat) {
    if (bparser != null && parseSucceeded) {
      Tree binaryTree = bparser.getBestParse();

      Tree tree = debinarizer.transformTree(binaryTree);
      if (op.nodePrune) {
        NodePruner np = new NodePruner(pparser, debinarizer);
        tree = np.prune(tree);
      }
      if (stripSubcat) {
        tree = subcategoryStripper.transformTree(tree);
      }
      restoreOriginalWords(tree);
      return tree;

    } else if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
      return getBestPCFGParse();
    } else if (dparser != null && dparser.hasParse()) { // && fallbackToDG
      // Should we strip subcategorize like this?  Traditionally haven't...
      // return subcategoryStripper.transformTree(getBestDependencyParse(true));
      return getBestDependencyParse(true);
    } else {
      throw new NoSuchElementException();
    }
  }




  public List<ScoredObject<Tree>> getKGoodFactoredParses(int k) {
    if (bparser == null) {
      return null;
    }
    List<ScoredObject<Tree>> binaryTrees = bparser.getKGoodParses(k);
    if (binaryTrees == null) {
      return null;
    }
    List<ScoredObject<Tree>> trees = new ArrayList<ScoredObject<Tree>>(k);
    for (ScoredObject<Tree> tp : binaryTrees) {
      Tree t = debinarizer.transformTree(tp.object());
      t = subcategoryStripper.transformTree(t);
      restoreOriginalWords(t);
      trees.add(new ScoredObject<Tree>(t, tp.score()));
    }
    return trees;
  }

  /**
   * Returns the trees (and scores) corresponding to the
   * k-best derivations of the sentence.  This cannot be
   * a Counter because frequently there will be multiple
   * derivations which lead to the same parse tree.
   *
   * @param k The number of best parses to return
   * @return The list of trees with their scores (log prob).
   */
  public List<ScoredObject<Tree>> getKBestPCFGParses(int k) {
    if (pparser == null) {
      return null;
    }
    List<ScoredObject<Tree>> binaryTrees = pparser.getKBestParses(k);
    if (binaryTrees == null) {
      return null;
    }
    List<ScoredObject<Tree>> trees = new ArrayList<ScoredObject<Tree>>(k);
    for (ScoredObject<Tree> p : binaryTrees) {
      Tree t = debinarizer.transformTree(p.object());
      t = subcategoryStripper.transformTree(t);
      restoreOriginalWords(t);
      trees.add(new ScoredObject<Tree>(t, p.score()));
    }
    return trees;
  }


  Tree getBestPCFGParse() {
    return getBestPCFGParse(true);
  }

  Tree getBestPCFGParse(boolean stripSubcategories) {
    if (pparser == null) {
      return null;
    }
    Tree binaryTree = pparser.getBestParse();

    if (binaryTree == null) {
      return null;
    }
    Tree t = debinarizer.transformTree(binaryTree);
    if (stripSubcategories) {
      t = subcategoryStripper.transformTree(t);
    }
    restoreOriginalWords(t);
    return t;
  }

  public double getPCFGScore() {
    return pparser.getBestScore();
  }

  double getPCFGScore(String goalStr) {
    return pparser.getBestScore(goalStr);
  }

  void parsePCFG(List<? extends HasWord> sentence) {
    pparser.parse(sentence);
  }

  Tree getBestDependencyParse() {
    return getBestDependencyParse(false);
  }

  Tree getBestDependencyParse(boolean debinarize) {
    Tree t = dparser != null ? dparser.getBestParse() : null;
    if (debinarize && t != null) {
      t = debinarizer.transformTree(t);
    }
    restoreOriginalWords(t);
    return t;
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
    System.err.println("Testing on treebank");
    Timing treebankTotalTtimer = new Timing();
    TreePrint treePrint = getTreePrint();
    TreebankLangParserParams tlpParams = op.tlpParams;
    TreebankLanguagePack tlp = op.langpack();
    PrintWriter pwOut = tlpParams.pw();
    PrintWriter pwErr = tlpParams.pw(System.err);
    if (op.testOptions.verbose) {
      pwErr.print("Testing ");
      pwErr.println(testTreebank.textualSummary(tlp));
    }
    if (op.testOptions.evalb) {
      EvalbFormatWriter.initEVALBfiles(tlpParams);
    }

    PrintWriter pwo = null;
    if (op.testOptions.writeOutputFiles) {
      String fname = op.testOptions.outputFilesPrefix + "." + op.testOptions.outputFilesExtension;
      try {
        pwo = op.tlpParams.pw(new FileOutputStream(fname));
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }

    PrintWriter statsPwo = null;
    if(op.testOptions.outputkBestEquivocation != null) {
      try {
        statsPwo = op.tlpParams.pw(new FileOutputStream(op.testOptions.outputkBestEquivocation));
      } catch(IOException ioe) {
        ioe.printStackTrace();
      }
    }

    TreeTransformer tc = tlpParams.collinizer();
    TreeTransformer br = new BoundaryRemover();

    // evaluation setup
    boolean runningAverages = Boolean.parseBoolean(op.testOptions.evals.getProperty("runningAverages"));
    boolean summary = Boolean.parseBoolean(op.testOptions.evals.getProperty("summary"));
    boolean tsv = Boolean.parseBoolean(op.testOptions.evals.getProperty("tsv"));
    tlpParams.setupForEval();
    // subcategoryStripper = tlpParams.subcategoryStripper(); // NOT NEEDED. THIS WAS DONE ON CLASS INITIALIZATION

    List<BestOfTopKEval> topKEvals = new ArrayList<BestOfTopKEval>();
    
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgTopK"))) {
      topKEvals.add(new BestOfTopKEval(new Evalb("pcfg top k comparisons", false), new Evalb("pcfg top k LP/LR", runningAverages)));
    }

    AbstractEval pcfgLB = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLB"))) {
      pcfgLB = new Evalb("pcfg LP/LR", runningAverages);
    }
    LeafAncestorEval pcfgLA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLA"))) {
      pcfgLA = new LeafAncestorEval("pcfg LeafAncestor");
    }
    AbstractEval pcfgCB = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgCB"))) {
      pcfgCB = new Evalb.CBEval("pcfg CB", runningAverages);
    }
    AbstractEval pcfgDA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgDA"))) {
      pcfgDA = new UnlabeledAttachmentEval("pcfg DA", runningAverages, tlp.headFinder());
    }
    AbstractEval pcfgTA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgTA"))) {
      pcfgTA = new TaggingEval("pcfg Tag", runningAverages, pparser.lex);
    }
    AbstractEval depDA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depDA"))) {
      depDA = new UnlabeledAttachmentEval("dep DA", runningAverages, null, tlp.punctuationWordRejectFilter());
    }
    AbstractEval depTA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depTA"))) {
      depTA = new TaggingEval("dep Tag", runningAverages, pparser.lex);
    }
    AbstractEval factLB = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLB"))) {
      factLB = new Evalb("factor LP/LR", runningAverages);
    }
    LeafAncestorEval factLA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLA"))) {
      factLA = new LeafAncestorEval("factor LeafAncestor");
    }
    AbstractEval factCB = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factCB"))) {
      factCB = new Evalb.CBEval("fact CB", runningAverages);
    }
    AbstractEval factDA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factDA"))) {
      factDA = new UnlabeledAttachmentEval("factor DA", runningAverages, null);
    }
    AbstractEval factTA = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factTA"))) {
      if (op.doPCFG) {
        factTA = new TaggingEval("factor Tag", runningAverages, pparser.lex);
      } else {
        // only doing dep parser, and need to get tags out in special way....
        factTA = new TaggingEval("factor Tag", runningAverages, pparser.lex);
      }
    }
    AbstractEval pcfgRUO = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgRUO"))) {
      pcfgRUO = new AbstractEval.RuleErrorEval("pcfg Rule under/over");
    }
    AbstractEval pcfgCUO = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgCUO"))) {
      pcfgCUO = new AbstractEval.CatErrorEval("pcfg Category under/over");
    }
    AbstractEval pcfgCatE = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgCatE"))) {
      pcfgCatE = new EvalbByCat("pcfg Category Eval", runningAverages);
    }
    AbstractEval.ScoreEval pcfgLL = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLL"))) {
      pcfgLL = new AbstractEval.ScoreEval("pcfgLL", runningAverages);
    }
    AbstractEval.ScoreEval depLL = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depLL"))) {
      depLL = new AbstractEval.ScoreEval("depLL", runningAverages);
    }
    AbstractEval.ScoreEval factLL = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLL"))) {
      factLL = new AbstractEval.ScoreEval("factLL", runningAverages);
    }
    // this one is for the various k Good/Best options.  Just for individual results
    AbstractEval kGoodLB = new Evalb("kGood LP/LR", false);

    // no annotation
    TreeAnnotatorAndBinarizer binarizerOnly;
    if (!op.trainOptions.leftToRight) {
      binarizerOnly = new TreeAnnotatorAndBinarizer(tlpParams, op.forceCNF, false, false, op);
    } else {
      binarizerOnly = new TreeAnnotatorAndBinarizer(tlpParams.headFinder(), new LeftHeadFinder(), tlpParams, op.forceCNF, false, false, op);
    }

    if(op.testOptions.preTag) {
      try {
        Class[] argsClass = { String.class };
        Object[] arguments = { op.testOptions.taggerSerializedFile };
        System.err.printf("Loading tagger from serialized file %s ...\n",op.testOptions.taggerSerializedFile);
        tagger = (Function<List<? extends HasWord>,ArrayList<TaggedWord>>) Class.forName("edu.stanford.nlp.tagger.maxent.MaxentTagger").getConstructor(argsClass).newInstance(arguments);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    int numSkippedEvals = 0;
    boolean saidMemMessage = false;
    Timing timer = new Timing();
    for (Tree goldTree : testTreebank) {
      final ArrayList<? extends HasWord> sentence = getInputSentence(goldTree);

      timer.start();

      pwErr.println("Parsing [len. " + sentence.size() + "]: " + Sentence.listToString(sentence));
      Tree tree = null;
      List<ScoredObject<Tree>> kbestPCFGTrees = null;
      int kbestPCFG = 0;
      if (topKEvals.size() > 0) {
        kbestPCFG = op.testOptions.evalPCFGkBest;
      }
      if (op.testOptions.printPCFGkBest > 0) {
        kbestPCFG = Math.max(kbestPCFG, op.testOptions.printPCFGkBest);
      }
      try {
        if ( ! parse(sentence)) {
          pwErr.print("Sentence couldn't be parsed by grammar.");
          if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
            pwErr.println("... falling back to PCFG parse.");
            tree = getBestPCFGParse();
            if (kbestPCFG > 0) {
              kbestPCFGTrees = getKBestPCFGParses(kbestPCFG);
            }
          } else {
            pwErr.println();
          }
        } else {
          tree = getBestParse();
          if (kbestPCFG > 0) {
            kbestPCFGTrees = getKBestPCFGParses(kbestPCFG);
          }
          if (bparser != null) pwErr.println("FactoredParser parse score is " + bparser.getBestScore());
        }

      } catch (OutOfMemoryError e) {
        if (op.testOptions.maxLength != -0xDEADBEEF) {
          // this means they explicitly asked for a length they cannot handle.
          // Throw exception.  Avoid string concatenation before throw it.
          pwErr.print("NOT ENOUGH MEMORY TO PARSE SENTENCES OF LENGTH ");
          pwErr.println(op.testOptions.maxLength);
          throw e;

        } else {
          if ( ! saidMemMessage) {
            printOutOfMemory(pwErr);
            saidMemMessage = true;
          }
          if (pparser.hasParse() && fallbackToPCFG) {
            try {
              String what = "dependency";
              if (dparser.hasParse()) {
                what = "factored";
              }
              pwErr.println("Sentence too long for " + what + " parser.  Falling back to PCFG parse...");
              tree = getBestPCFGParse();
              if (kbestPCFG > 0) {
                kbestPCFGTrees = getKBestPCFGParses(kbestPCFG);
              }
            } catch (OutOfMemoryError oome) {
              oome.printStackTrace();
              pwErr.println("No memory to gather PCFG parse. Skipping...");
              pparser.nudgeDownArraySize();
            }
          } else {
            pwErr.println("Sentence has no parse using PCFG grammar (or no PCFG fallback).  Skipping...");
          }
          pwErr.println();
        }
      } catch (UnsupportedOperationException uoe) {
        pwErr.println("Sentence too long (or zero words).");
        if(pwo != null) {
          pwo.println("(())");
        }
        continue;
      }

      //combo parse goes to pwOut (System.out)
      if (op.testOptions.verbose) {
        pwOut.println("ComboParser best");
        Tree ot = tree;
        if (ot != null && ! tlpParams.treebankLanguagePack().isStartSymbol(ot.value())) {
          ot = ot.treeFactory().newTreeNode(tlpParams.treebankLanguagePack().startSymbol(), Collections.singletonList(ot));
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
          List<ScoredObject<Tree>> parses = pparser.getBestParses();
          int sz = parses.size();
          if (sz > 1) {
            pwOut.println("There were " + sz + " best PCFG parses with score " + parses.get(0).score() + '.');
            Tree transGoldTree = tc.transformTree(goldTree);
            int iii = 0;
            for (ScoredObject<Tree> sot : parses) {
              iii++;
              Tree tb = sot.object();
              Tree tbd = debinarizer.transformTree(tb);
              tbd = subcategoryStripper.transformTree(tbd);
              restoreOriginalWords(tbd);
              pwOut.println("PCFG Parse #" + iii + " with score " + tbd.score());
              tbd.pennPrint(pwOut);
              Tree tbtr = tc.transformTree(tbd);
              // pwOut.println("Tree size = " + tbtr.size() + "; depth = " + tbtr.depth());
              kGoodLB.evaluate(tbtr, transGoldTree, pwErr);
            }
          }
        }
        // Huang and Chiang (2006) Algorithm 3 output from the PCFG parser
        else if (op.testOptions.printPCFGkBest > 0 && op.testOptions.outputkBestEquivocation == null) {
          List<ScoredObject<Tree>> trees = kbestPCFGTrees.subList(0, op.testOptions.printPCFGkBest);
          Tree transGoldTree = tc.transformTree(goldTree);
          int i = 0;
          for (ScoredObject<Tree> tp : trees) {
            i++;
            pwOut.println("PCFG Parse #" + i + " with score " + tp.score());
            Tree tbd = tp.object();
            tbd.pennPrint(pwOut);
            Tree tbtr = tc.transformTree(tbd);
            kGoodLB.evaluate(tbtr, transGoldTree, pwErr);
          }
        }
        // Chart parser (factored) n-best list
        else if (op.testOptions.printFactoredKGood > 0 && bparser.hasParse()) {
          // DZ: debug n best trees
          List<ScoredObject<Tree>> trees = getKGoodFactoredParses(op.testOptions.printFactoredKGood);
          Tree transGoldTree = tc.transformTree(goldTree);
          int ii = 0;
          for (ScoredObject<Tree> tp : trees) {
            ii++;
            pwOut.println("Factored Parse #" + ii + " with score " + tp.score());
            Tree tbd = tp.object();
            tbd.pennPrint(pwOut);
            Tree tbtr = tc.transformTree(tbd);
            kGoodLB.evaluate(tbtr, transGoldTree, pwOut);
          }
        }
        //1-best output
        else if(pwo != null) {
          pwo.println(tree.toString());
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
            entropy += Math.exp(logPr) * (logPr / Math.log(2));
          }
          entropy *= -1; //Convert to bits
          statsPwo.printf("%f\t%d\t%d\n", entropy,trees.size(),sentence.size());
        }
      }


      // **EVALUATION**
      // Perform various evaluations specified by the user
      if (tree != null) {
        //Strip subcategories and remove punctuation for evaluation
        tree = subcategoryStripper.transformTree(tree);
        Tree treeFact = tc.transformTree(tree);

        //Setup the gold tree
        if (op.testOptions.verbose) {
          pwOut.println("Correct parse");
          treePrint.printTree(goldTree, pwOut);
        }
        Tree transGoldTree = tc.transformTree(goldTree);
        if(transGoldTree != null)
          transGoldTree = subcategoryStripper.transformTree(transGoldTree);

        //Can't do evaluation in these two cases
        if (transGoldTree == null) {
          pwErr.println("Couldn't transform gold tree for evaluation, skipping eval. Gold tree was:");
          goldTree.pennPrint(pwErr);
          numSkippedEvals++;
          continue;

        } else if (treeFact == null) {
          pwErr.println("Couldn't transform hypothesis tree for evaluation, skipping eval. Tree was:");
          tree.pennPrint(pwErr);
          numSkippedEvals++;
          continue;
        
        } else if(treeFact.yield().size() != transGoldTree.yield().size()) {
          List<Label> fYield = treeFact.yield();
          List<Label> gYield = transGoldTree.yield();
          pwErr.println("WARNING: Evaluation could not be performed due to guess/gold yield mismatch.");
          pwErr.println("  sizes: g: " + gYield.size() + " p: " + fYield.size());
          pwErr.println("  g: " + Sentence.listToString(gYield, true));
          pwErr.println("  p: " + Sentence.listToString(gYield, true));
          numSkippedEvals++;
          continue;
        }

        if (topKEvals.size() > 0) {
          List<Tree> transGuesses = new ArrayList<Tree>();
          int kbest = Math.min(op.testOptions.evalPCFGkBest, kbestPCFGTrees.size());
          for (ScoredObject<Tree> guess : kbestPCFGTrees.subList(0, kbest)) {
            transGuesses.add(tc.transformTree(guess.object()));
          }
          for (BestOfTopKEval eval : topKEvals) {
            eval.evaluate(transGuesses, transGoldTree, pwErr);
          }
        }

        //PCFG eval
        Tree treePCFG = getBestPCFGParse();
        if (treePCFG != null) {
          Tree treePCFGeval = tc.transformTree(treePCFG);
          if (pcfgLB != null) {
            pcfgLB.evaluate(treePCFGeval, transGoldTree, pwErr);
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
          if (pcfgLL != null && pparser != null) {
            pcfgLL.recordScore(pparser, pwErr);
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
        Tree treeDep = getBestDependencyParse();
        if (treeDep != null) {
          Tree goldTreeB = binarizerOnly.transformTree(goldTree);

          Tree goldTreeEval = goldTree.deepCopy();
          goldTreeEval.indexLeaves(true);
          goldTreeEval.percolateHeads(tlp.headFinder());

          Tree depDAEval = getBestDependencyParse(true);
          depDAEval.indexLeaves(true);
          depDAEval.percolateHeadIndices();
          if (depDA != null) {
            depDA.evaluate(depDAEval, goldTreeEval, pwErr);
          }
          if (depTA != null) {
            Tree undoneTree = debinarizer.transformTree(treeDep);
            undoneTree = subcategoryStripper.transformTree(undoneTree);
            restoreOriginalWords(undoneTree);
            // pwErr.println("subcategoryStripped tree: " + undoneTree.toStructureDebugString());
            depTA.evaluate(undoneTree, goldTree, pwErr);
          }
          if (depLL != null && dparser != null) {
            depLL.recordScore(dparser, pwErr);
          }
          Tree factTreeB;
          if (bparser != null && parseSucceeded) {
            factTreeB = bparser.getBestParse();
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
        }
        if(factLA != null) {
          factLA.evaluate(treeFact, transGoldTree, pwErr);
        }
        if (factTA != null) {
          factTA.evaluate(tree, br.transformTree(goldTree), pwErr);
        }
        if (factLL != null && bparser != null) {
          factLL.recordScore(bparser, pwErr);
        }
        if (factCB != null) {
          factCB.evaluate(treeFact, transGoldTree, pwErr);
        }
        if (op.testOptions.evalb) {
          // empty out scores just in case
          nanScores(tree);
          EvalbFormatWriter.writeEVALBline(treeFact, transGoldTree);
        }
      }
      pwErr.println();
    } // for tree iterator

    //Done parsing...print the results of the evaluations
    treebankTotalTtimer.done("Testing on treebank");
    if (saidMemMessage) {
      printOutOfMemory(pwErr);
    }
    if (op.testOptions.evalb) {
      EvalbFormatWriter.closeEVALBfiles();
    }
    if(numSkippedEvals != 0) {
      pwOut.printf("Unable to evaluate %d parser hypotheses due to yield mismatch\n",numSkippedEvals);
    }
    if (summary) {
      if (pcfgLB != null) pcfgLB.display(false, pwErr);
      if (pcfgLA != null) pcfgLA.display(false, pwErr);
      if (pcfgCB != null) pcfgCB.display(false, pwErr);
      if (pcfgDA != null) pcfgDA.display(false, pwErr);
      if (pcfgTA != null) pcfgTA.display(false, pwErr);
      if (pcfgLL != null && pparser != null) pcfgLL.display(false, pwErr);
      if (depDA != null) depDA.display(false, pwErr);
      if (depTA != null) depTA.display(false, pwErr);
      if (depLL != null && dparser != null) depLL.display(false, pwErr);
      if (factLB != null) factLB.display(false, pwErr);
      if (factLA != null) factLA.display(false, pwErr);
      if (factCB != null) factCB.display(false, pwErr);
      if (factDA != null) factDA.display(false, pwErr);
      if (factTA != null) factTA.display(false, pwErr);
      if (factLL != null && bparser != null) factLL.display(false, pwErr);
      if (pcfgCatE != null) pcfgCatE.display(false, pwErr);
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
      if (dparser != null && factDA != null) pwErr.print(nf.format(factDA.getEvalbF1Percent()));
      pwErr.print("\t");
      if (factLB != null) pwErr.print(nf.format(factLB.getExactPercent()));
      pwErr.print("\t");
      if (pcfgLB != null) pwErr.print(nf.format(pcfgLB.getEvalbF1Percent()));
      pwErr.print("\t");
      if (dparser != null && depDA != null) pwErr.print(nf.format(depDA.getEvalbF1Percent()));
      pwErr.print("\t");
      if (pparser != null && factTA != null) pwErr.print(nf.format(factTA.getEvalbF1Percent()));
      pwErr.print("\t");
      if (factLB != null) pwErr.print(factLB.getNum());
      pwErr.println();
    }

    double f1 = 0.0;
    if (factLB != null) {
      f1 = factLB.getEvalbF1();
    }

    //Close files (if necessary)
    if(pwo != null) pwo.close();
    if(statsPwo != null) statsPwo.close();

    return f1;
  } // end testOnTreebank()



  /** Parse the files with names given in the String array args elements from
   *  index argIndex on.
   */
  void parseFiles(String[] args, int argIndex, boolean tokenized, TokenizerFactory<? extends HasWord> tokenizerFactory, String elementDelimiter, String sentenceDelimiter, Function<List<HasWord>, List<HasWord>> escaper, String tagDelimiter) {
    final TreebankLanguagePack tlp = op.tlpParams.treebankLanguagePack();
    final PrintWriter pwOut = op.tlpParams.pw();
    final PrintWriter pwErr = op.tlpParams.pw(System.err);
    final TreePrint treePrint = getTreePrint();
    final Timing timer = new Timing();

    int numWords = 0;
    int numSents = 0;
    int numUnparsable = 0;
    int numNoMemory = 0;
    int numFallback = 0;
    int numSkipped = 0;
    boolean saidMemMessage = false;

    if (op.testOptions.verbose) {
      if(tokenizerFactory != null)
        pwErr.println("parseFiles: Tokenizer factory is: " + tokenizerFactory);
      pwErr.println("Sentence final words are: " + Arrays.asList(tlp.sentenceFinalPunctuationWords()));
      pwErr.println("File encoding is: " + op.tlpParams.getInputEncoding());
    }

    // evaluation setup
    boolean runningAverages = Boolean.parseBoolean(op.testOptions.evals.getProperty("runningAverages"));
    boolean summary = Boolean.parseBoolean(op.testOptions.evals.getProperty("summary"));
    AbstractEval.ScoreEval pcfgLL = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("pcfgLL"))) {
      pcfgLL = new AbstractEval.ScoreEval("pcfgLL", runningAverages);
    }
    AbstractEval.ScoreEval depLL = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("depLL"))) {
      depLL = new AbstractEval.ScoreEval("depLL", runningAverages);
    }
    AbstractEval.ScoreEval factLL = null;
    if (Boolean.parseBoolean(op.testOptions.evals.getProperty("factLL"))) {
      factLL = new AbstractEval.ScoreEval("factLL", runningAverages);
    }

    timer.start();

    //Loop over the files
    final DocType docType = (elementDelimiter == null) ? DocType.Plain : DocType.XML;
    for (int i = argIndex; i < args.length; i++) {
      final String filename = args[i];

      final DocumentPreprocessor documentPreprocessor;
      if (filename.equals("-")) {
        try {
          documentPreprocessor = new DocumentPreprocessor(new BufferedReader(new InputStreamReader(System.in, op.tlpParams.getInputEncoding())),docType);
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      } else {
        documentPreprocessor = new DocumentPreprocessor(filename,docType,op.tlpParams.getInputEncoding());
      }

      //Unused values are null per the main() method invocation below
      //null is the default for these properties
      documentPreprocessor.setSentenceFinalPuncWords(tlp.sentenceFinalPunctuationWords());
      documentPreprocessor.setEscaper(escaper);
      documentPreprocessor.setSentenceDelimiter(sentenceDelimiter);
      documentPreprocessor.setTagDelimiter(tagDelimiter);
      documentPreprocessor.setElementDelimiter(elementDelimiter);
      if(tokenizerFactory == null)
        documentPreprocessor.setTokenizerFactory((tokenized) ? null : tlp.getTokenizerFactory());
      else
        documentPreprocessor.setTokenizerFactory(tokenizerFactory);

      //Setup the output
      PrintWriter pwo = pwOut;
      if (op.testOptions.writeOutputFiles) {
        String normalizedName = filename;
        try {
          URL url = new URL(normalizedName); // this will exception if not a URL
          normalizedName = normalizedName.replaceAll("/","_");
        } catch (MalformedURLException e) {
          //It isn't a URL, so silently ignore
        }

        String ext = (op.testOptions.outputFilesExtension == null) ? "stp" : op.testOptions.outputFilesExtension;
        String fname = normalizedName + '.' + ext;
        if (op.testOptions.outputFilesDirectory != null && !op.testOptions.outputFilesDirectory.equals("")) {
          String fseparator = System.getProperty("file.separator");
          if (fseparator == null || "".equals(fseparator)) {
            fseparator = "/";
          }
          File fnameFile = new File(fname);
          fname = op.testOptions.outputFilesDirectory + fseparator + fnameFile.getName();
        }

        try {
          pwo = op.tlpParams.pw(new FileOutputStream(fname));
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
      }
      treePrint.printHeader(pwo, op.tlpParams.getOutputEncoding());


      pwErr.println("Parsing file: " + filename);
      int num = 0;
      for (List<HasWord> sentence : documentPreprocessor) {
        num++;
        numSents++;
        int len = sentence.size();
        numWords += len;
        pwErr.println("Parsing [sent. " + num + " len. " + len + "]: " + Sentence.listToString(sentence, true));

        Tree ansTree = null;
        try {
          // TODO: combine with the similar fallback pattern in
          // testOnTreebank
          if ( ! parse(sentence)) {
            pwErr.print("Sentence couldn't be parsed by grammar.");
            if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
              pwErr.println("... falling back to PCFG parse.");
              ansTree = getBestPCFGParse();
              numFallback++;
            } else {
              pwErr.println();
              numUnparsable++;
            }
          } else {
            // pwOut.println("Score: " + lp.pparser.bestScore);
            ansTree = getBestParse();
          }
          if (pcfgLL != null && pparser != null) {
            pcfgLL.recordScore(pparser, pwErr);
          }
          if (depLL != null && dparser != null) {
            depLL.recordScore(dparser, pwErr);
          }
          if (factLL != null && bparser != null) {
            factLL.recordScore(bparser, pwErr);
          }
        } catch (OutOfMemoryError e) {
          if (op.testOptions.maxLength != -0xDEADBEEF) {
            // this means they explicitly asked for a length they cannot handle. Throw exception.
            pwErr.println("NOT ENOUGH MEMORY TO PARSE SENTENCES OF LENGTH " + op.testOptions.maxLength);
            pwo.println("(())");
            throw e;
          } else {
            if ( ! saidMemMessage) {
              printOutOfMemory(pwErr);
              saidMemMessage = true;
            }
            if (pparser.hasParse() && fallbackToPCFG) {
              try {
                String what = "dependency";
                if (dparser.hasParse()) {
                  what = "factored";
                }
                pwErr.println("Sentence too long for " + what + " parser.  Falling back to PCFG parse...");
                ansTree = getBestPCFGParse();
                numFallback++;
              } catch (OutOfMemoryError oome) {
                oome.printStackTrace();
                numNoMemory++;
                pwErr.println("No memory to gather PCFG parse. Skipping...");
                pwo.println("(())");
                pparser.nudgeDownArraySize();
              }
            } else {
              pwErr.println("Sentence has no parse using PCFG grammar (or no PCFG fallback).  Skipping...");
              pwo.println("(())");
              numSkipped++;
            }
          }
        } catch (UnsupportedOperationException uoe) {
          pwErr.println("Sentence too long (or zero words).");
          pwo.println("(())");
          numWords -= len;
          numSkipped++;
        }
        try {
          treePrint.printTree(ansTree, Integer.toString(num), pwo);
        } catch (RuntimeException re) {
          pwErr.println("TreePrint.printTree skipped: out of memory (or other error)");
          re.printStackTrace();
          numNoMemory++;
          try {
            treePrint.printTree(null, Integer.toString(num), pwo);
          } catch (Exception e) {
            pwErr.println("Sentence skipped: out of memory and error calling TreePrint.");
            pwo.println("(())");
            e.printStackTrace();
          }
        }
        // crude addition of k-best tree printing
        if (op.testOptions.printPCFGkBest > 0 && pparser.hasParse()) {
          List<ScoredObject<Tree>> trees = getKBestPCFGParses(op.testOptions.printPCFGkBest);
          treePrint.printTrees(trees, Integer.toString(num), pwo);
        } else if (op.testOptions.printFactoredKGood > 0 && bparser.hasParse()) {
          // DZ: debug n best trees
          List<ScoredObject<Tree>> trees = getKGoodFactoredParses(op.testOptions.printFactoredKGood);
          treePrint.printTrees(trees, Integer.toString(num), pwo);
        }
      }

      treePrint.printFooter(pwo);
      if (op.testOptions.writeOutputFiles) pwo.close();

      pwErr.println("Parsed file: " + filename + " [" + num + " sentences].");
    }

    long millis = timer.stop();

    if (summary) {
      if (pcfgLL != null) pcfgLL.display(false, pwErr);
      if (depLL != null) depLL.display(false, pwErr);
      if (factLL != null) factLL.display(false, pwErr);
    }

    if (saidMemMessage) {
      printOutOfMemory(pwErr);
    }
    double wordspersec = numWords / (((double) millis) / 1000);
    double sentspersec = numSents / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    pwErr.println("Parsed " + numWords + " words in " + numSents +
        " sentences (" + nf.format(wordspersec) + " wds/sec; " +
        nf.format(sentspersec) + " sents/sec).");
    if (numFallback > 0) {
      pwErr.println("  " + numFallback + " sentences were parsed by fallback to PCFG.");
    }
    if (numUnparsable > 0 || numNoMemory > 0 || numSkipped > 0) {
      pwErr.println("  " + (numUnparsable + numNoMemory + numSkipped) + " sentences were not parsed:");
      if (numUnparsable > 0) {
        pwErr.println("    " + numUnparsable + " were not parsable with non-zero probability.");
      }
      if (numNoMemory > 0) {
        pwErr.println("    " + numNoMemory + " were skipped because of insufficient memory.");
      }
      if (numSkipped > 0) {
        pwErr.println("    " + numSkipped + " were skipped as length 0 or greater than " + op.testOptions.maxLength);
      }
    }
  } // end parseFiles


  private static void printOutOfMemory(PrintWriter pw) {
    pw.println();
    pw.println("*******************************************************");
    pw.println("***  WARNING!! OUT OF MEMORY! THERE WAS NOT ENOUGH  ***");
    pw.println("***  MEMORY TO RUN ALL PARSERS.  EITHER GIVE THE    ***");
    pw.println("***  JVM MORE MEMORY, SET THE MAXIMUM SENTENCE      ***");
    pw.println("***  LENGTH WITH -maxLength, OR PERHAPS YOU ARE     ***");
    pw.println("***  HAPPY TO HAVE THE PARSER FALL BACK TO USING    ***");
    pw.println("***  A SIMPLER PARSER FOR VERY LONG SENTENCES.      ***");
    pw.println("*******************************************************");
    pw.println();
  }


  // Remove tree scores, so they don't print.
  // TODO: The printing architecture should be fixed up in the trees package
  // sometime.
  private static void nanScores(Tree tree) {
    tree.setScore(Double.NaN);
    Tree[] kids = tree.children();
    for (int i = 0; i < kids.length; i++) {
      nanScores(kids[i]);
    }
  }


  /**
   * Returns the input sentence for the parser.
   */
  private ArrayList<? extends HasWord> getInputSentence(Tree t) {
    if (op.testOptions.forceTags) {
      if (op.testOptions.preTag) {
        ArrayList<TaggedWord> s = tagger.apply(t.yieldWords());
        if(op.testOptions.verbose) {
          System.err.println("Guess tags: "+Arrays.toString(s.toArray()));
          System.err.println("Gold tags: "+t.labeledYield().toString());
        }
        return s;
      } else if(op.testOptions.noFunctionalForcing) {
        ArrayList<? extends HasWord> s = t.taggedYield();
        for(HasWord word : s) {
          String tag = ((HasTag) word).tag();
          tag = tag.split("-")[0];
          ((HasTag) word).setTag(tag);
        }
        return s;
      } else {
        return t.taggedYield();
      }
    } else {
      return t.yieldWords();
    }
  }

  /** Return a TreePrint for formatting parsed output trees.
   *  @return A TreePrint for formatting parsed output trees.
   */
  public TreePrint getTreePrint() {
    return op.testOptions.treePrint(op.tlpParams);
  }

  /** Adds a sentence final punctuation mark to sentences that lack one.
   *  This method adds a period (the first sentence final punctuation word
   *  in a parser language pack) to sentences that don't have one within
   *  the last 3 words (to allow for close parentheses, etc.).  It checks
   *  tags for punctuation, if available, otherwise words.
   *  @param sentence The sentence to check
   *  @param length The length of the sentence (just to avoid recomputation)
   */
  void addSentenceFinalPunctIfNeeded(List<HasWord> sentence, int length) {
    int start = length - 3;
    if (start < 0) start = 0;
    TreebankLanguagePack tlp = op.tlpParams.treebankLanguagePack();
    for (int i = length - 1; i >= start; i--) {
      Object item = sentence.get(i);
      // An object (e.g., MapLabel) can implement HasTag but not actually store
      // a tag so we need to check that there is something there for this case.
      // If there is, use only it, since word tokens can be ambiguous.
      String tag = null;
      if (item instanceof HasTag) {
        tag = ((HasTag) item).tag();
      }
      if (tag != null && ! "".equals(tag)) {
        if (tlp.isSentenceFinalPunctuationTag(tag)) {
          return;
        }
      } else if (item instanceof HasWord) {
        String str = ((HasWord) item).word();
        if (tlp.isPunctuationWord(str)) {
          return;
        }
      } else {
        String str = item.toString();
        if (tlp.isPunctuationWord(str)) {
          return;
        }
      }
    }
    // none found so add one.
    if (op.testOptions.verbose) {
      System.err.println("Adding missing final punctuation to sentence.");
    }
    String[] sfpWords = tlp.sentenceFinalPunctuationWords();
    if (sfpWords.length > 0) {
      sentence.add(new Word(sfpWords[0]));
    }
  }

}
