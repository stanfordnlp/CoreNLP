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
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/lex-parser.html

package edu.stanford.nlp.parser.lexparser;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.common.NoSuchParseException;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.DeltaIndex;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.logging.Redwood;


public class LexicalizedParserQuery implements ParserQuery  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(LexicalizedParserQuery.class);

  private final Options op;
  private final TreeTransformer debinarizer;
  private final TreeTransformer boundaryRemover;

  /** The PCFG parser. */
  private final ExhaustivePCFGParser pparser;
  /** The dependency parser. */
  private final ExhaustiveDependencyParser dparser;
  /** The factored parser that combines the dependency and PCFG parsers. */
  private final KBestViterbiParser bparser;

  private final boolean fallbackToPCFG = true;

  private final TreeTransformer subcategoryStripper;

  // Whether or not the most complicated model available successfully
  // parsed the input sentence.
  private boolean parseSucceeded = false;
  // parseSkipped means that not only did we not succeed at parsing,
  // but for some reason we didn't even try.  Most likely this happens
  // when the sentence is too long or is of length 0.
  private boolean parseSkipped = false;
  // In some sense we succeeded, but only because we used a fallback grammar
  private boolean parseFallback = false;
  // Not enough memory to parse
  private boolean parseNoMemory = false;
  // Horrible error
  private boolean parseUnparsable = false;
  // If something ran out of memory, where the error occurred
  private String whatFailed = null;

  public boolean parseSucceeded() { return parseSucceeded; }
  public boolean parseSkipped() { return parseSkipped; }
  public boolean parseFallback() { return parseFallback; }
  public boolean parseNoMemory() { return parseNoMemory; }
  public boolean parseUnparsable() { return parseUnparsable; }

  private List<? extends HasWord> originalSentence;

  @Override
  public List<? extends HasWord> originalSentence() { return originalSentence; }

  /** Keeps track of whether the sentence had punctuation added, which affects the expected length of the sentence */
  private boolean addedPunct = false;

  private boolean saidMemMessage = false;

  public boolean saidMemMessage() {
    return saidMemMessage;
  }


  LexicalizedParserQuery(LexicalizedParser parser) {
    this.op = parser.getOp();

    BinaryGrammar bg = parser.bg;
    UnaryGrammar ug = parser.ug;
    Lexicon lex = parser.lex;
    DependencyGrammar dg = parser.dg;

    Index<String> stateIndex = parser.stateIndex;
    Index<String> wordIndex = new DeltaIndex<>(parser.wordIndex);
    Index<String> tagIndex = parser.tagIndex;

    this.debinarizer = new Debinarizer(op.forceCNF);
    this.boundaryRemover = new BoundaryRemover();

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

    subcategoryStripper = op.tlpParams.subcategoryStripper();
  }

  @Override
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
   * </ul>
   *
   * @param sentence The sentence to parse
   * @return true Iff the sentence was accepted by the grammar
   * @throws UnsupportedOperationException If the Sentence is too long or
   *                                       of zero length or the parse
   *                                       otherwise fails for resource reasons
   */
  private boolean parseInternal(List<? extends HasWord> sentence) {
    parseSucceeded = false;
    parseNoMemory = false;
    parseUnparsable = false;
    parseSkipped = false;
    parseFallback = false;
    whatFailed = null;
    addedPunct = false;
    originalSentence = sentence;
    int length = sentence.size();
    if (length == 0) {
      parseSkipped = true;
      throw new UnsupportedOperationException("Can't parse a zero-length sentence!");
    }

    List<HasWord> sentenceB;
    if (op.wordFunction != null) {
      sentenceB = Generics.newArrayList();
      for (HasWord word : originalSentence) {
        if (word instanceof Label) {
          Label label = (Label) word;
          Label newLabel = label.labelFactory().newLabel(label);
          if (newLabel instanceof HasWord) {
            sentenceB.add((HasWord) newLabel);
          } else {
            throw new AssertionError("This should have been a HasWord");
          }
        } else if (word instanceof HasTag) {
          TaggedWord tw = new TaggedWord(word.word(), ((HasTag) word).tag());
          sentenceB.add(tw);
        } else {
          sentenceB.add(new Word(word.word()));
        }
      }
      for (HasWord word : sentenceB) {
        word.setWord(op.wordFunction.apply(word.word()));
      }
    } else {
      sentenceB = new ArrayList<>(sentence);
    }

    if (op.testOptions.addMissingFinalPunctuation) {
      addedPunct = addSentenceFinalPunctIfNeeded(sentenceB, length);
    }
    if (length > op.testOptions.maxLength) {
      parseSkipped = true;
      throw new UnsupportedOperationException("Sentence too long: length " + length);
    }
    TreePrint treePrint = getTreePrint();
    PrintWriter pwOut = op.tlpParams.pw();

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

    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }

    if (op.doPCFG) {
      if (!pparser.parse(sentenceB)) {
        return parseSucceeded;
      }
      if (op.testOptions.verbose) {
        pwOut.println("PParser output");
        // getBestPCFGParse(false).pennPrint(pwOut); // with scores on nodes
        treePrint.printTree(getBestPCFGParse(false), pwOut); // without scores on nodes
      }
    }
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    if (op.doDep && ! op.testOptions.useFastFactored) {
      if ( ! dparser.parse(sentenceB)) {
        return parseSucceeded;
      }
      // cdm nov 2006: should move these printing bits to the main printing section,
      // so don't calculate the best parse twice!
      if (op.testOptions.verbose) {
        pwOut.println("DParser output");
        treePrint.printTree(dparser.getBestParse(), pwOut);
      }
    }
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    if (op.doPCFG && op.doDep) {
      if ( ! bparser.parse(sentenceB)) {
        return parseSucceeded;
      } else {
        parseSucceeded = true;
      }
    }
    return true;
  }


  @Override
  public void restoreOriginalWords(Tree tree) {
    if (originalSentence == null || tree == null) {
      return;
    }
    List<Tree> leaves = tree.getLeaves();
    int expectedSize = addedPunct ? originalSentence.size() + 1 : originalSentence.size();
    if (leaves.size() != expectedSize) {
      throw new IllegalStateException("originalWords and sentence of different sizes: " + expectedSize + " vs. " + leaves.size() +
                                      "\n Orig: " + SentenceUtils.listToString(originalSentence) +
                                      "\n Pars: " + SentenceUtils.listToString(leaves));
    }
    Iterator<Tree> leafIterator = leaves.iterator();
    for (HasWord word : originalSentence) {
      Tree leaf = leafIterator.next();
      if (!(word instanceof Label)) {
        continue;
      }
      leaf.setLabel((Label) word);
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
    parseNoMemory = false;
    parseUnparsable = false;
    parseSkipped = false;
    parseFallback = false;
    whatFailed = null;
    originalSentence = null;
    if (lr.getNumStates() > op.testOptions.maxLength + 1) {  // + 1 for boundary symbol
      parseSkipped = true;
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
    parseSucceeded = true;
    return true;
  }

  /**
   * Return the best parse of the sentence most recently parsed.
   * This will be from the factored parser, if it was used and it succeeded
   * else from the PCFG if it was used and succeed, else from the dependency
   * parser.
   *
   * @return The best tree
   * @throws NoSuchParseException If no previously successfully parsed
   *                                sentence
   */
  @Override
  public Tree getBestParse() {
    return getBestParse(true);
  }

  Tree getBestParse(boolean stripSubcat) {
    if (parseSkipped) {
      return null;
    }
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
      // Should we strip subcategories like this?  Traditionally haven't...
      // return subcategoryStripper.transformTree(getBestDependencyParse(true));
      return getBestDependencyParse(true);
    } else {
      throw new NoSuchParseException();
    }
  }


  /**
   * Return the k best parses of the sentence most recently parsed.
   *
   * NB: The dependency parser does not implement a k-best method
   * and the factored parser's method seems to be broken and therefore
   * this method always returns a list of size 1 if either of these
   * two parsers was used.
   *
   * @return A list of scored trees
   * @throws NoSuchParseException If no previously successfully parsed
   *                                sentence   */
  @Override
  public List<ScoredObject<Tree>> getKBestParses(int k) {
    if (parseSkipped) {
      return null;
    }
    if (bparser != null && parseSucceeded) {
      //The getKGoodParses seems to be broken, so just return the best parse
      Tree binaryTree = bparser.getBestParse();
      Tree tree = debinarizer.transformTree(binaryTree);

      if (op.nodePrune) {
        NodePruner np = new NodePruner(pparser, debinarizer);
        tree = np.prune(tree);
      }
      tree = subcategoryStripper.transformTree(tree);
      restoreOriginalWords(tree);

      double score = dparser.getBestScore();
      ScoredObject<Tree> so = new ScoredObject<>(tree, score);
      List<ScoredObject<Tree>> trees = new ArrayList<>(1);
      trees.add(so);
      return trees;
    } else if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
      return this.getKBestPCFGParses(k);
    } else if (dparser != null && dparser.hasParse()) { // && fallbackToDG
      // The dependency parser doesn't support k-best parse extraction, so just
      // return the best parse
      Tree tree = this.getBestDependencyParse(true);
      double score = dparser.getBestScore();
      ScoredObject<Tree> so = new ScoredObject<>(tree, score);
      List<ScoredObject<Tree>> trees = new ArrayList<>(1);
      trees.add(so);
      return trees;
    } else {
      throw new NoSuchParseException();
    }
  }

  /**
   *
   * Checks which parser (factored, PCFG, or dependency) was used and
   * returns the score of the best parse from this parser.
   *
   * If no parse could be obtained, it returns Double.NEGATIVE_INFINITY.
   *
   * @return the score of the best parse, or Double.NEGATIVE_INFINITY
   */
  @Override
  public double getBestScore() {
    if (parseSkipped) {
      return Double.NEGATIVE_INFINITY;
    }
    if (bparser != null && parseSucceeded) {
      return bparser.getBestScore();
    } else if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
      return pparser.getBestScore();
    } else if (dparser != null && dparser.hasParse()) {
      return dparser.getBestScore();
    } else {
      return Double.NEGATIVE_INFINITY;
    }
  }


  public List<ScoredObject<Tree>> getBestPCFGParses() {
    return pparser.getBestParses();
  }

  public boolean hasFactoredParse() {
    if (bparser == null) {
      return false;
    }
    return !parseSkipped && parseSucceeded && bparser.hasParse();
  }

  public Tree getBestFactoredParse() {
    return bparser.getBestParse();
  }

  public List<ScoredObject<Tree>> getKGoodFactoredParses(int k) {
    if (bparser == null || parseSkipped) {
      return null;
    }

    List<ScoredObject<Tree>> binaryTrees = bparser.getKGoodParses(k);
    if (binaryTrees == null) {
      return null;
    }

    List<ScoredObject<Tree>> trees = new ArrayList<>(k);
    for (ScoredObject<Tree> tp : binaryTrees) {
      Tree t = debinarizer.transformTree(tp.object());
      if (op.nodePrune) {
        NodePruner np = new NodePruner(pparser, debinarizer);
        t = np.prune(t);
      }
      t = subcategoryStripper.transformTree(t);
      restoreOriginalWords(t);
      trees.add(new ScoredObject<>(t, tp.score()));
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
    List<ScoredObject<Tree>> trees = new ArrayList<>(k);
    for (ScoredObject<Tree> p : binaryTrees) {
      Tree t = debinarizer.transformTree(p.object());
      t = subcategoryStripper.transformTree(t);
      restoreOriginalWords(t);
      trees.add(new ScoredObject<>(t, p.score()));
    }
    return trees;
  }


  public Tree getBestPCFGParse() {
    return getBestPCFGParse(true);
  }

  public Tree getBestPCFGParse(boolean stripSubcategories) {
    if (pparser == null || parseSkipped || parseUnparsable) {
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

  @Override
  public double getPCFGScore() {
    return pparser.getBestScore();
  }

  double getPCFGScore(String goalStr) {
    return pparser.getBestScore(goalStr);
  }

  void parsePCFG(List<? extends HasWord> sentence) {
    parseSucceeded = false;
    parseNoMemory = false;
    parseUnparsable = false;
    parseSkipped = false;
    parseFallback = false;
    whatFailed = null;
    originalSentence = sentence;
    pparser.parse(sentence);
  }

  public Tree getBestDependencyParse() {
    return getBestDependencyParse(false);
  }

  @Override
  public Tree getBestDependencyParse(boolean debinarize) {
    if (dparser == null || parseSkipped || parseUnparsable) {
      return null;
    }
    Tree t = dparser.getBestParse();
    if (t != null) {
      if (debinarize) {
        t = debinarizer.transformTree(t);
      }
      t = boundaryRemover.transformTree(t); // remove boundary .$$. which is otherwise still there from dparser.
      restoreOriginalWords(t);
    }
    return t;
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
   * </ul>
   *
   * @param sentence The sentence to parse
   * @return true Iff the sentence was accepted by the grammar.  If
   *              the main grammar fails, but the PCFG succeeds, then
   *              this still returns true, but parseFallback() will
   *              also return true.  getBestParse() will have a valid
   *              result iff this returns true.
   */
  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    try {
      if (!parseInternal(sentence)) {
        if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
          parseFallback = true;
          return true;
        } else {
          parseUnparsable = true;
          return false;
        }
      } else {
        return true;
      }
    } catch (OutOfMemoryError e) {
      if (op.testOptions.maxLength != -0xDEADBEEF) {
        // this means they explicitly asked for a length they cannot handle.
        // Throw exception.  Avoid string concatenation before throw it.
        log.info("NOT ENOUGH MEMORY TO PARSE SENTENCES OF LENGTH ");
        log.info(op.testOptions.maxLength);
        throw e;
      }
      if (pparser.hasParse() && fallbackToPCFG) {
        try {
          whatFailed = "dependency";
          if (dparser.hasParse()) {
            whatFailed = "factored";
          }
          parseFallback = true;
          return true;
        } catch (OutOfMemoryError oome) {
          oome.printStackTrace();
          parseNoMemory = true;
          pparser.nudgeDownArraySize();
          return false;
        }
      } else {
        parseNoMemory = true;
        return false;
      }
    } catch (UnsupportedOperationException uoe) {
      parseSkipped = true;
      return false;
    }
  }

  /**
   * Implements the same parsing with fallback that parse() does, but
   * also outputs status messages for failed parses to pwErr.
   */
  @Override
  public boolean parseAndReport(List<? extends HasWord> sentence, PrintWriter pwErr) {
    boolean result = parse(sentence);
    if (result) {
      if (whatFailed != null) {
        // Something failed, probably because of memory problems.
        // However, we still got a PCFG parse, at least.
        if ( ! saidMemMessage) {
          ParserUtils.printOutOfMemory(pwErr);
          saidMemMessage = true;
        }
        pwErr.println("Sentence too long for " + whatFailed + " parser.  Falling back to PCFG parse...");
      } else if (parseFallback) {
        // We had to fall back for some other reason.
        pwErr.println("Sentence couldn't be parsed by grammar.... falling back to PCFG parse.");
      }
    } else if (parseUnparsable) {
      // No parse at all, completely failed.
      pwErr.println("Sentence couldn't be parsed by grammar.");
    } else if (parseNoMemory) {
      // Ran out of memory, either with or without a possible PCFG parse.
      if (!saidMemMessage) {
        ParserUtils.printOutOfMemory(pwErr);
        saidMemMessage = true;
      }
      if (pparser.hasParse() && fallbackToPCFG) {
        pwErr.println("No memory to gather PCFG parse. Skipping...");
      } else {
        pwErr.println("Sentence has no parse using PCFG grammar (or no PCFG fallback).  Skipping...");
      }
    } else if (parseSkipped) {
      pwErr.println("Sentence too long (or zero words).");
    }
    return result;
  }


  /** Return a TreePrint for formatting parsed output trees.
   *  @return A TreePrint for formatting parsed output trees.
   */
  public TreePrint getTreePrint() {
    return op.testOptions.treePrint(op.tlpParams);
  }

  @Override
  public KBestViterbiParser getPCFGParser() {
    return pparser;
  }

  @Override
  public KBestViterbiParser getDependencyParser() {
    return dparser;
  }

  @Override
  public KBestViterbiParser getFactoredParser() {
    return bparser;
  }

  /** Adds a sentence final punctuation mark to sentences that lack one.
   *  This method adds a period (the first sentence final punctuation word
   *  in a parser language pack) to sentences that don't have one within
   *  the last 3 words (to allow for close parentheses, etc.).  It checks
   *  tags for punctuation, if available, otherwise words.
   *
   *  @param sentence The sentence to check
   *  @param length The length of the sentence (just to avoid recomputation)
   */
  private boolean addSentenceFinalPunctIfNeeded(List<HasWord> sentence, int length) {
    int start = length - 3;
    if (start < 0) start = 0;
    TreebankLanguagePack tlp = op.tlpParams.treebankLanguagePack();
    for (int i = length - 1; i >= start; i--) {
      HasWord item = sentence.get(i);
      // An object (e.g., CoreLabel) can implement HasTag but not actually store
      // a tag so we need to check that there is something there for this case.
      // If there is, use only it, since word tokens can be ambiguous.
      String tag = null;
      if (item instanceof HasTag) {
        tag = ((HasTag) item).tag();
      }
      if (tag != null && ! tag.isEmpty()) {
        if (tlp.isSentenceFinalPunctuationTag(tag)) {
          return false;
        }
      } else {
        String str = item.word();
        if (tlp.isPunctuationWord(str)) {
          return false;
        }
      }
    }
    // none found so add one.
    if (op.testOptions.verbose) {
      log.info("Adding missing final punctuation to sentence.");
    }
    String[] sfpWords = tlp.sentenceFinalPunctuationWords();
    if (sfpWords.length > 0) {
      sentence.add(new Word(sfpWords[0]));
    }
    return true;
  }

}
