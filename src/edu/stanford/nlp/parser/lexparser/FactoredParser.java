// StanfordLexicalizedParser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002, 2003, 2004, 2005 The Board of Trustees of
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.metrics.AbstractEval;
import edu.stanford.nlp.parser.metrics.UnlabeledAttachmentEval;
import edu.stanford.nlp.parser.metrics.Evalb;
import edu.stanford.nlp.parser.metrics.EvalbFormatWriter;
import edu.stanford.nlp.parser.metrics.TaggingEval;
import edu.stanford.nlp.trees.LeftHeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeLengthComparator;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * @author Dan Klein (original version)
 * @author Christopher Manning (better features, ParserParams, serialization)
 * @author Roger Levy (internationalization)
 * @author Teg Grenager (grammar compaction, etc., tokenization, etc.)
 * @author Galen Andrew (lattice parsing)
 * @author Philip Resnik and Dan Zeman (n good parses)
 */
public class FactoredParser  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(FactoredParser.class);

/* some documentation for Roger's convenience
 * {pcfg,dep,combo}{PE,DE,TE} are precision/dep/tagging evals for the models

 * parser is the PCFG parser
 * dparser is the dependency parser
 * bparser is the combining parser

 * during testing:
 * tree is the test tree (gold tree)
 * binaryTree is the gold tree binarized
 * tree2b is the best PCFG paser, binarized
 * tree2 is the best PCFG parse (debinarized)
 * tree3 is the dependency parse, binarized
 * tree3db is the dependency parser, debinarized
 * tree4 is the best combo parse, binarized and then debinarized
 * tree4b is the best combo parse, binarized
 */

  public static void main(String[] args) {
    Options op = new Options(new EnglishTreebankParserParams());
    // op.tlpParams may be changed to something else later, so don't use it till
    // after options are parsed.

    StringUtils.logInvocationString(log, args);

    String path = "/u/nlp/stuff/corpora/Treebank3/parsed/mrg/wsj";
    int trainLow = 200, trainHigh = 2199, testLow = 2200, testHigh = 2219;
    String serializeFile = null;

    int i = 0;
    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equalsIgnoreCase("-path") && (i + 1 < args.length)) {
        path = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-train") && (i + 2 < args.length)) {
        trainLow = Integer.parseInt(args[i + 1]);
        trainHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-test") && (i + 2 < args.length)) {
        testLow = Integer.parseInt(args[i + 1]);
        testHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-serialize") && (i + 1 < args.length)) {
        serializeFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-tLPP") && (i + 1 < args.length)) {
        try {
          op.tlpParams = (TreebankLangParserParams) Class.forName(args[i + 1]).newInstance();
        } catch (ClassNotFoundException e) {
          log.info("Class not found: " + args[i + 1]);
          throw new RuntimeException(e);
        } catch (InstantiationException e) {
          log.info("Couldn't instantiate: " + args[i + 1] + ": " + e.toString());
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          log.info("illegal access" + e);
          throw new RuntimeException(e);
        }
        i += 2;
      } else if (args[i].equals("-encoding")) {
        // sets encoding for TreebankLangParserParams
        op.tlpParams.setInputEncoding(args[i + 1]);
        op.tlpParams.setOutputEncoding(args[i + 1]);
        i += 2;
      } else {
        i = op.setOptionOrWarn(args, i);
      }
    }
    // System.out.println(tlpParams.getClass());
    TreebankLanguagePack tlp = op.tlpParams.treebankLanguagePack();

    op.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(op.tlpParams.sisterSplitters()));
    //    BinarizerFactory.TreeAnnotator.setTreebankLang(tlpParams);
    PrintWriter pw = op.tlpParams.pw();

    op.testOptions.display();
    op.trainOptions.display();
    op.display();
    op.tlpParams.display();

    // setup tree transforms
    Treebank trainTreebank = op.tlpParams.memoryTreebank();
    MemoryTreebank testTreebank = op.tlpParams.testMemoryTreebank();
    // Treebank blippTreebank = ((EnglishTreebankParserParams) tlpParams).diskTreebank();
    // String blippPath = "/afs/ir.stanford.edu/data/linguistic-data/BLLIP-WSJ/";
    // blippTreebank.loadPath(blippPath, "", true);

    Timing.startTime();
    log.info("Reading trees...");
    testTreebank.loadPath(path, new NumberRangeFileFilter(testLow, testHigh, true));
    if (op.testOptions.increasingLength) {
      Collections.sort(testTreebank, new TreeLengthComparator());
    }

    trainTreebank.loadPath(path, new NumberRangeFileFilter(trainLow, trainHigh, true));
    Timing.tick("done.");

    log.info("Binarizing trees...");
    TreeAnnotatorAndBinarizer binarizer;
    if (!op.trainOptions.leftToRight) {
      binarizer = new TreeAnnotatorAndBinarizer(op.tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), true, op);
    } else {
      binarizer = new TreeAnnotatorAndBinarizer(op.tlpParams.headFinder(), new LeftHeadFinder(), op.tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), true, op);
    }

    CollinsPuncTransformer collinsPuncTransformer = null;
    if (op.trainOptions.collinsPunc) {
      collinsPuncTransformer = new CollinsPuncTransformer(tlp);
    }
    TreeTransformer debinarizer = new Debinarizer(op.forceCNF);
    List<Tree> binaryTrainTrees = new ArrayList<>();

    if (op.trainOptions.selectiveSplit) {
      op.trainOptions.splitters = ParentAnnotationStats.getSplitCategories(trainTreebank, op.trainOptions.tagSelectiveSplit, 0, op.trainOptions.selectiveSplitCutOff, op.trainOptions.tagSelectiveSplitCutOff, op.tlpParams.treebankLanguagePack());
      if (op.trainOptions.deleteSplitters != null) {
        List<String> deleted = new ArrayList<>();
        for (String del : op.trainOptions.deleteSplitters) {
          String baseDel = tlp.basicCategory(del);
          boolean checkBasic = del.equals(baseDel);
          for (Iterator<String> it = op.trainOptions.splitters.iterator(); it.hasNext(); ) {
            String elem = it.next();
            String baseElem = tlp.basicCategory(elem);
            boolean delStr = checkBasic && baseElem.equals(baseDel) ||
              elem.equals(del);
            if (delStr) {
              it.remove();
              deleted.add(elem);
            }
          }
        }
        log.info("Removed from vertical splitters: " + deleted);
      }
    }
    if (op.trainOptions.selectivePostSplit) {
      TreeTransformer myTransformer = new TreeAnnotator(op.tlpParams.headFinder(), op.tlpParams, op);
      Treebank annotatedTB = trainTreebank.transform(myTransformer);
      op.trainOptions.postSplitters = ParentAnnotationStats.getSplitCategories(annotatedTB, true, 0, op.trainOptions.selectivePostSplitCutOff, op.trainOptions.tagSelectivePostSplitCutOff, op.tlpParams.treebankLanguagePack());
    }

    if (op.trainOptions.hSelSplit) {
      binarizer.setDoSelectiveSplit(false);
      for (Tree tree : trainTreebank) {
        if (op.trainOptions.collinsPunc) {
          tree = collinsPuncTransformer.transformTree(tree);
        }
        //tree.pennPrint(tlpParams.pw());
        tree = binarizer.transformTree(tree);
        //binaryTrainTrees.add(tree);
      }
      binarizer.setDoSelectiveSplit(true);
    }
    for (Tree tree : trainTreebank) {
      if (op.trainOptions.collinsPunc) {
        tree = collinsPuncTransformer.transformTree(tree);
      }
      tree = binarizer.transformTree(tree);
      binaryTrainTrees.add(tree);
    }
    if (op.testOptions.verbose) {
      binarizer.dumpStats();
    }

    List<Tree> binaryTestTrees = new ArrayList<>();
    for (Tree tree : testTreebank) {
      if (op.trainOptions.collinsPunc) {
        tree = collinsPuncTransformer.transformTree(tree);
      }
      tree = binarizer.transformTree(tree);
      binaryTestTrees.add(tree);
    }
    Timing.tick("done.");  // binarization
    BinaryGrammar bg = null;
    UnaryGrammar ug = null;
    DependencyGrammar dg = null;
    // DependencyGrammar dgBLIPP = null;
    Lexicon lex = null;
    Index<String> stateIndex = new HashIndex<>();

    // extract grammars
    Extractor<Pair<UnaryGrammar,BinaryGrammar>> bgExtractor = new BinaryGrammarExtractor(op, stateIndex);
    //Extractor bgExtractor = new SmoothedBinaryGrammarExtractor();//new BinaryGrammarExtractor();
    // Extractor lexExtractor = new LexiconExtractor();

    //Extractor dgExtractor = new DependencyMemGrammarExtractor();

    if (op.doPCFG) {
      log.info("Extracting PCFG...");
      Pair<UnaryGrammar, BinaryGrammar> bgug = null;
      if (op.trainOptions.cheatPCFG) {
        List<Tree> allTrees = new ArrayList<>(binaryTrainTrees);
        allTrees.addAll(binaryTestTrees);
        bgug = bgExtractor.extract(allTrees);
      } else {
        bgug = bgExtractor.extract(binaryTrainTrees);
      }
      bg = bgug.second;
      bg.splitRules();
      ug = bgug.first;
      ug.purgeRules();
      Timing.tick("done.");
    }
    log.info("Extracting Lexicon...");
    Index<String> wordIndex = new HashIndex<>();
    Index<String> tagIndex = new HashIndex<>();
    lex = op.tlpParams.lex(op, wordIndex, tagIndex);
    lex.initializeTraining(binaryTrainTrees.size());
    lex.train(binaryTrainTrees);
    lex.finishTraining();
    Timing.tick("done.");

    if (op.doDep) {
      log.info("Extracting Dependencies...");
      binaryTrainTrees.clear();
      Extractor<DependencyGrammar> dgExtractor = new MLEDependencyGrammarExtractor(op, wordIndex, tagIndex);
      // dgBLIPP = (DependencyGrammar) dgExtractor.extract(new ConcatenationIterator(trainTreebank.iterator(),blippTreebank.iterator()),new TransformTreeDependency(tlpParams,true));

      // DependencyGrammar dg1 = dgExtractor.extract(trainTreebank.iterator(), new TransformTreeDependency(op.tlpParams, true));
      //dgBLIPP=(DependencyGrammar)dgExtractor.extract(blippTreebank.iterator(),new TransformTreeDependency(tlpParams));

      //dg = (DependencyGrammar) dgExtractor.extract(new ConcatenationIterator(trainTreebank.iterator(),blippTreebank.iterator()),new TransformTreeDependency(tlpParams));
      // dg=new DependencyGrammarCombination(dg1,dgBLIPP,2);
      dg = dgExtractor.extract(binaryTrainTrees); //uses information whether the words are known or not, discards unknown words
      Timing.tick("done.");
      //System.out.print("Extracting Unknown Word Model...");
      //UnknownWordModel uwm = (UnknownWordModel)uwmExtractor.extract(binaryTrainTrees);
      //Timing.tick("done.");
      System.out.print("Tuning Dependency Model...");
      dg.tune(binaryTestTrees);
      //System.out.println("TUNE DEPS: "+tuneDeps);
      Timing.tick("done.");
    }

    BinaryGrammar boundBG = bg;
    UnaryGrammar boundUG = ug;

    GrammarProjection gp = new NullGrammarProjection(bg, ug);

    // serialization
    if (serializeFile != null) {
      log.info("Serializing parser...");
      LexicalizedParser parser = new LexicalizedParser(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
      parser.saveParserToSerialized(serializeFile);
      Timing.tick("done.");
    }

    // test: pcfg-parse and output

    ExhaustivePCFGParser parser = null;
    if (op.doPCFG) {
      parser = new ExhaustivePCFGParser(boundBG, boundUG, lex, op, stateIndex, wordIndex, tagIndex);
    }


    ExhaustiveDependencyParser dparser = ((op.doDep && ! op.testOptions.useFastFactored) ? new ExhaustiveDependencyParser(dg, lex, op, wordIndex, tagIndex) : null);

    Scorer scorer = (op.doPCFG ? new TwinScorer(new ProjectionScorer(parser, gp, op), dparser) : null);
    //Scorer scorer = parser;
    BiLexPCFGParser bparser = null;
    if (op.doPCFG && op.doDep) {
      bparser = (op.testOptions.useN5) ? new BiLexPCFGParser.N5BiLexPCFGParser(scorer, parser, dparser, bg, ug, dg, lex, op, gp, stateIndex, wordIndex, tagIndex) : new BiLexPCFGParser(scorer, parser, dparser, bg, ug, dg, lex, op, gp, stateIndex, wordIndex, tagIndex);
    }

    Evalb pcfgPE = new Evalb("pcfg  PE", true);
    Evalb comboPE = new Evalb("combo PE", true);
    AbstractEval pcfgCB = new Evalb.CBEval("pcfg  CB", true);

    AbstractEval pcfgTE = new TaggingEval("pcfg  TE");
    AbstractEval comboTE = new TaggingEval("combo TE");
    AbstractEval pcfgTEnoPunct = new TaggingEval("pcfg nopunct TE");
    AbstractEval comboTEnoPunct = new TaggingEval("combo nopunct TE");
    AbstractEval depTE = new TaggingEval("depnd TE");

    AbstractEval depDE = new UnlabeledAttachmentEval("depnd DE", true, null, tlp.punctuationWordRejectFilter());
    AbstractEval comboDE = new UnlabeledAttachmentEval("combo DE", true, null, tlp.punctuationWordRejectFilter());

    if (op.testOptions.evalb) {
      EvalbFormatWriter.initEVALBfiles(op.tlpParams);
    }

    // int[] countByLength = new int[op.testOptions.maxLength+1];

    // Use a reflection ruse, so one can run this without needing the
    // tagger.  Using a function rather than a MaxentTagger means we
    // can distribute a version of the parser that doesn't include the
    // entire tagger.
    Function<List<? extends HasWord>,ArrayList<TaggedWord>> tagger = null;
    if (op.testOptions.preTag) {
      try {
        Class[] argsClass = { String.class };
        Object[] arguments = new Object[]{op.testOptions.taggerSerializedFile};
        tagger = (Function<List<? extends HasWord>,ArrayList<TaggedWord>>) Class.forName("edu.stanford.nlp.tagger.maxent.MaxentTagger").getConstructor(argsClass).newInstance(arguments);
      } catch (Exception e) {
        log.info(e);
        log.info("Warning: No pretagging of sentences will be done.");
      }
    }

    for (int tNum = 0, ttSize = testTreebank.size(); tNum < ttSize; tNum++) {
      Tree tree = testTreebank.get(tNum);
      int testTreeLen = tree.yield().size();
      if (testTreeLen > op.testOptions.maxLength) {
        continue;
      }
      Tree binaryTree = binaryTestTrees.get(tNum);
      // countByLength[testTreeLen]++;
      System.out.println("-------------------------------------");
      System.out.println("Number: " + (tNum + 1));
      System.out.println("Length: " + testTreeLen);

      //tree.pennPrint(pw);
      // System.out.println("XXXX The binary tree is");
      // binaryTree.pennPrint(pw);
      //System.out.println("Here are the tags in the lexicon:");
      //System.out.println(lex.showTags());
      //System.out.println("Here's the tagnumberer:");
      //System.out.println(Numberer.getGlobalNumberer("tags").toString());

      long timeMil1 = System.currentTimeMillis();
      Timing.tick("Starting parse.");
      if (op.doPCFG) {
        //log.info(op.testOptions.forceTags);
        if (op.testOptions.forceTags) {
          if (tagger != null) {
            //System.out.println("Using a tagger to set tags");
            //System.out.println("Tagged sentence as: " + tagger.processSentence(cutLast(wordify(binaryTree.yield()))).toString(false));
            parser.parse(addLast(tagger.apply(cutLast(wordify(binaryTree.yield())))));
          } else {
            //System.out.println("Forcing tags to match input.");
            parser.parse(cleanTags(binaryTree.taggedYield(), tlp));
          }
        } else {
          // System.out.println("XXXX Parsing " + binaryTree.yield());
          parser.parse(binaryTree.yieldHasWord());
        }
        //Timing.tick("Done with pcfg phase.");
      }
      if (op.doDep) {
        dparser.parse(binaryTree.yieldHasWord());
        //Timing.tick("Done with dependency phase.");
      }
      boolean bothPassed = false;
      if (op.doPCFG && op.doDep) {
        bothPassed = bparser.parse(binaryTree.yieldHasWord());
        //Timing.tick("Done with combination phase.");
      }
      long timeMil2 = System.currentTimeMillis();
      long elapsed = timeMil2 - timeMil1;
      log.info("Time: " + ((int) (elapsed / 100)) / 10.00 + " sec.");
      //System.out.println("PCFG Best Parse:");
      Tree tree2b = null;
      Tree tree2 = null;
      //System.out.println("Got full best parse...");
      if (op.doPCFG) {
        tree2b = parser.getBestParse();
        tree2 = debinarizer.transformTree(tree2b);
      }
      //System.out.println("Debinarized parse...");
      //tree2.pennPrint();
      //System.out.println("DepG Best Parse:");
      Tree tree3 = null;
      Tree tree3db = null;
      if (op.doDep) {
        tree3 = dparser.getBestParse();
        // was: but wrong Tree tree3db = debinarizer.transformTree(tree2);
        tree3db = debinarizer.transformTree(tree3);
        tree3.pennPrint(pw);
      }
      //tree.pennPrint();
      //((Tree)binaryTrainTrees.get(tNum)).pennPrint();
      //System.out.println("Combo Best Parse:");
      Tree tree4 = null;
      if (op.doPCFG && op.doDep) {
        try {
          tree4 = bparser.getBestParse();
          if (tree4 == null) {
            tree4 = tree2b;
          }
        } catch (NullPointerException e) {
          log.info("Blocked, using PCFG parse!");
          tree4 = tree2b;
        }
      }
      if (op.doPCFG && !bothPassed) {
        tree4 = tree2b;
      }
      //tree4.pennPrint();
      if (op.doDep) {
        depDE.evaluate(tree3, binaryTree, pw);
        depTE.evaluate(tree3db, tree, pw);
      }
      AbstractCollinizer tc = op.tlpParams.collinizer();
      AbstractCollinizer tcEvalb = op.tlpParams.collinizerEvalb();
      if (op.doPCFG) {
        // System.out.println("XXXX Best PCFG was: ");
        // tree2.pennPrint();
        // System.out.println("XXXX Transformed best PCFG is: ");
        // tc.transformTree(tree2).pennPrint();
        //System.out.println("True Best Parse:");
        //tree.pennPrint();
        //tc.transformTree(tree).pennPrint();
        pcfgPE.evaluate(tc.transformTree(tree2, tree2), tc.transformTree(tree, tree), pw);
        pcfgCB.evaluate(tc.transformTree(tree2, tree2), tc.transformTree(tree, tree), pw);
        Tree tree4b = null;
        if (op.doDep) {
          comboDE.evaluate((bothPassed ? tree4 : tree3), binaryTree, pw);
          tree4b = tree4;
          tree4 = debinarizer.transformTree(tree4);
          if (op.nodePrune) {
            NodePruner np = new NodePruner(parser, debinarizer);
            tree4 = np.prune(tree4);
          }
          //tree4.pennPrint();
          comboPE.evaluate(tc.transformTree(tree4, tree4), tc.transformTree(tree, tree), pw);
        }
        //pcfgTE.evaluate(tree2, tree);
        pcfgTE.evaluate(tcEvalb.transformTree(tree2, tree2), tcEvalb.transformTree(tree, tree), pw);
        pcfgTEnoPunct.evaluate(tc.transformTree(tree2, tree2), tc.transformTree(tree, tree), pw);

        if (op.doDep) {
          comboTE.evaluate(tcEvalb.transformTree(tree4, tree4), tcEvalb.transformTree(tree, tree), pw);
          comboTEnoPunct.evaluate(tc.transformTree(tree4, tree4), tc.transformTree(tree, tree), pw);
        }
        System.out.println("PCFG only: " + parser.scoreBinarizedTree(tree2b, 0));

        //tc.transformTree(tree2).pennPrint();
        tree2.pennPrint(pw);

        if (op.doDep) {
          System.out.println("Combo: " + parser.scoreBinarizedTree(tree4b, 0));
          // tc.transformTree(tree4).pennPrint(pw);
          tree4.pennPrint(pw);
        }
        System.out.println("Correct:" + parser.scoreBinarizedTree(binaryTree, 0));
        /*
        if (parser.scoreBinarizedTree(tree2b,true) < parser.scoreBinarizedTree(binaryTree,true)) {
          System.out.println("SCORE INVERSION");
          parser.validateBinarizedTree(binaryTree,0);
        }
        */
        tree.pennPrint(pw);
      } // end if doPCFG

      if (op.testOptions.evalb) {
        if (op.doPCFG && op.doDep) {
          EvalbFormatWriter.writeEVALBline(tcEvalb.transformTree(tree, tree), tcEvalb.transformTree(tree4, tree4));
        } else if (op.doPCFG) {
          EvalbFormatWriter.writeEVALBline(tcEvalb.transformTree(tree, tree), tcEvalb.transformTree(tree2, tree2));
        } else if (op.doDep) {
          EvalbFormatWriter.writeEVALBline(tcEvalb.transformTree(tree, tree), tcEvalb.transformTree(tree3db, tree3db));
        }
      }
    } // end for each tree in test treebank

    if (op.testOptions.evalb) {
      EvalbFormatWriter.closeEVALBfiles();
    }

    // op.testOptions.display();
    if (op.doPCFG) {
      pcfgPE.display(false, pw);
      System.out.println("Grammar size: " + stateIndex.size());
      pcfgCB.display(false, pw);
      if (op.doDep) {
        comboPE.display(false, pw);
      }
      pcfgTE.display(false, pw);
      pcfgTEnoPunct.display(false, pw);
      if (op.doDep) {
        comboTE.display(false, pw);
        comboTEnoPunct.display(false, pw);
      }
    }
    if (op.doDep) {
      depTE.display(false, pw);
      depDE.display(false, pw);
    }
    if (op.doPCFG && op.doDep) {
      comboDE.display(false, pw);
    }
    // pcfgPE.printGoodBad();
  }


  private static List<TaggedWord> cleanTags(List<TaggedWord> twList, TreebankLanguagePack tlp) {
    int sz = twList.size();
    List<TaggedWord> l = new ArrayList<>(sz);
    for (TaggedWord tw : twList) {
      TaggedWord tw2 = new TaggedWord(tw.word(), tlp.basicCategory(tw.tag()));
      l.add(tw2);
    }
    return l;
  }

  private static ArrayList<Word> wordify(List wList) {
    ArrayList<Word> s = new ArrayList<>();
    for (Object obj : wList) {
      s.add(new Word(obj.toString()));
    }
    return s;
  }

  private static ArrayList<Word> cutLast(ArrayList<Word> s) {
    return new ArrayList<>(s.subList(0, s.size() - 1));
  }

  private static ArrayList<Word> addLast(ArrayList<? extends Word> s) {
    ArrayList<Word> s2 = new ArrayList<>(s);
    //s2.add(new StringLabel(Lexicon.BOUNDARY));
    s2.add(new Word(Lexicon.BOUNDARY));
    return s2;
  }

  /**
   * Not an instantiable class
   */
  private FactoredParser() {
  }

}
