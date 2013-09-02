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
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.metrics.AbstractEval;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.DocumentPreprocessor.DocType;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.DeltaIndex;
import edu.stanford.nlp.util.Timing;

public class LexicalizedParserQuery implements ParserQuery {

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

  private boolean saidMemMessage = false;

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

  public void restoreOriginalWords(Tree tree) {
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

  public List<ScoredObject<Tree>> getBestPCFGParses() {
    return pparser.getBestParses();
  }

  public boolean hasFactoredParse() {
    if (bparser == null) {
      return false;
    }
    return parseSucceeded && bparser.hasParse();
  }

  public Tree getBestFactoredParse() {
    return bparser.getBestParse();
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


  public Tree getBestPCFGParse() {
    return getBestPCFGParse(true);
  }

  public Tree getBestPCFGParse(boolean stripSubcategories) {
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

  public Tree getBestDependencyParse() {
    return getBestDependencyParse(false);
  }

  public Tree getBestDependencyParse(boolean debinarize) {
    Tree t = dparser != null ? dparser.getBestParse() : null;
    if (debinarize && t != null) {
      t = debinarizer.transformTree(t);
    }
    restoreOriginalWords(t);
    return t;
  }

  /**
   * TODO: separate out the parsing and the output calls?
   * TODO: Return true/false and keep track of the tree separately?
   */
  public Tree parseWithFallback(List<? extends HasWord> sentence, PrintWriter pwErr, PrintWriter pwo) {
    Tree tree = null;
    try {
      if ( ! parse(sentence)) {
        pwErr.print("Sentence couldn't be parsed by grammar.");
        if (pparser != null && pparser.hasParse() && fallbackToPCFG) {
          pwErr.println("... falling back to PCFG parse.");
          tree = getBestPCFGParse();
        } else {
          pwErr.println();
        }
      } else {
        tree = getBestParse();
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
          ParserUtils.printOutOfMemory(pwErr);
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
      return null;
    }
    return tree;
  }


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
              ParserUtils.printOutOfMemory(pwErr);
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
      ParserUtils.printOutOfMemory(pwErr);
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


  /** Return a TreePrint for formatting parsed output trees.
   *  @return A TreePrint for formatting parsed output trees.
   */
  public TreePrint getTreePrint() {
    return op.testOptions.treePrint(op.tlpParams);
  }

  public KBestViterbiParser getPCFGParser() {
    return pparser;
  }

  public KBestViterbiParser getDependencyParser() {
    return dparser;
  }

  public KBestViterbiParser getFactoredParser() {
    return bparser;
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
