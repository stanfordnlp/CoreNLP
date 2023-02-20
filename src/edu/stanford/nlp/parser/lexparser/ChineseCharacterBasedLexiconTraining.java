package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.WordSegmenter;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeToBracketProcessor;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.WordCatConstituent;
import edu.stanford.nlp.trees.WordCatEqualityChecker;
import edu.stanford.nlp.trees.WordCatEquivalenceClasser;
import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

import edu.stanford.nlp.parser.lexparser.ChineseCharacterBasedLexicon.Symbol;

/**
 * Includes a main file which trains a ChineseCharacterBasedLexicon.
 * Separated from ChineseCharacterBasedLexicon so that packages which
 * use ChineseCharacterBasedLexicon don't also need all of
 * LexicalizedParser.
 *
 * @author Galen Andrew
 */
public class ChineseCharacterBasedLexiconTraining  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseCharacterBasedLexiconTraining.class);
  protected static final NumberFormat formatter = new DecimalFormat("0.000");

  public static void printStats(Collection<Tree> trees, PrintWriter pw) {
    ClassicCounter<Integer> wordLengthCounter = new ClassicCounter<>();
    ClassicCounter<TaggedWord> wordCounter = new ClassicCounter<>();
    ClassicCounter<Symbol> charCounter = new ClassicCounter<>();
    int counter = 0;
    for (Tree tree : trees) {
      counter++;
      List<TaggedWord> taggedWords = tree.taggedYield();
      for (TaggedWord taggedWord : taggedWords) {
        String word = taggedWord.word();
        if (word.equals(Lexicon.BOUNDARY)) {
          continue;
        }
        wordCounter.incrementCount(taggedWord);
        wordLengthCounter.incrementCount(Integer.valueOf(word.length()));
        for (int j = 0, length = word.length(); j < length; j++) {
          Symbol sym = Symbol.cannonicalSymbol(word.charAt(j));
          charCounter.incrementCount(sym);
        }
        charCounter.incrementCount(Symbol.END_WORD);
      }
    }

    Set<Symbol> singletonChars = Counters.keysBelow(charCounter, 1.5);
    Set<TaggedWord> singletonWords = Counters.keysBelow(wordCounter, 1.5);

    ClassicCounter<String> singletonWordPOSes = new ClassicCounter<>();
    for (TaggedWord taggedWord : singletonWords) {
      singletonWordPOSes.incrementCount(taggedWord.tag());
    }
    Distribution<String> singletonWordPOSDist = Distribution.getDistribution(singletonWordPOSes);

    ClassicCounter<Character> singletonCharRads = new ClassicCounter<>();
    for (Symbol s : singletonChars) {
      singletonCharRads.incrementCount(Character.valueOf(RadicalMap.getRadical(s.getCh())));
    }
    Distribution<Character> singletonCharRadDist = Distribution.getDistribution(singletonCharRads);

    Distribution<Integer> wordLengthDist = Distribution.getDistribution(wordLengthCounter);

    NumberFormat percent = new DecimalFormat("##.##%");

    pw.println("There are " + singletonChars.size() + " singleton chars out of " + (int) charCounter.totalCount() + " tokens and " + charCounter.size() + " types found in " + counter + " trees.");
    pw.println("Thus singletonChars comprise " + percent.format(singletonChars.size() / charCounter.totalCount()) + " of tokens and " + percent.format((double) singletonChars.size() / charCounter.size()) + " of types.");
    pw.println();
    pw.println("There are " + singletonWords.size() + " singleton words out of " + (int) wordCounter.totalCount() + " tokens and " + wordCounter.size() + " types.");
    pw.println("Thus singletonWords comprise " + percent.format(singletonWords.size() / wordCounter.totalCount()) + " of tokens and " + percent.format((double) singletonWords.size() / wordCounter.size()) + " of types.");
    pw.println();
    pw.println("Distribution over singleton word POS:");
    pw.println(singletonWordPOSDist.toString());
    pw.println();
    pw.println("Distribution over singleton char radicals:");
    pw.println(singletonCharRadDist.toString());
    pw.println();
    pw.println("Distribution over word length:");
    pw.println(wordLengthDist);
  }

  public static void main(String[] args) throws IOException {
    Map<String,Integer> flagsToNumArgs = Generics.newHashMap();
    flagsToNumArgs.put("-parser", Integer.valueOf(3));
    flagsToNumArgs.put("-lex", Integer.valueOf(3));
    flagsToNumArgs.put("-test", Integer.valueOf(2));
    flagsToNumArgs.put("-out", Integer.valueOf(1));
    flagsToNumArgs.put("-lengthPenalty", Integer.valueOf(1));
    flagsToNumArgs.put("-penaltyType", Integer.valueOf(1));
    flagsToNumArgs.put("-maxLength", Integer.valueOf(1));
    flagsToNumArgs.put("-stats", Integer.valueOf(2));

    Map<String,String[]> argMap = StringUtils.argsToMap(args, flagsToNumArgs);

    boolean eval = argMap.containsKey("-eval");

    PrintWriter pw = null;
    if (argMap.containsKey("-out")) {
      pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream((argMap.get("-out"))[0]), "GB18030"), true);
    }

    log.info("ChineseCharacterBasedLexicon called with args:");

    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    for (int i = 0; i < args.length; i++) {
      ctpp.setOptionFlag(args, i);
      log.info(" " + args[i]);
    }
    log.info();
    Options op = new Options(ctpp);

    if (argMap.containsKey("-stats")) {
      String[] statArgs = (argMap.get("-stats"));
      MemoryTreebank rawTrainTreebank = op.tlpParams.memoryTreebank();
      FileFilter trainFilt = new NumberRangesFileFilter(statArgs[1], false);
      rawTrainTreebank.loadPath(new File(statArgs[0]), trainFilt);
      log.info("Done reading trees.");
      MemoryTreebank trainTreebank;
      if (argMap.containsKey("-annotate")) {
        trainTreebank = new MemoryTreebank();
        TreeAnnotator annotator = new TreeAnnotator(ctpp.headFinder(), ctpp, op);
        for (Tree tree : rawTrainTreebank) {
          trainTreebank.add(annotator.transformTree(tree));
        }
        log.info("Done annotating trees.");
      } else {
        trainTreebank = rawTrainTreebank;
      }
      printStats(trainTreebank, pw);
      System.exit(0);
    }

    int maxLength = 1000000;
    //    Test.verbose = true;
    if (argMap.containsKey("-norm")) {
      op.testOptions.lengthNormalization = true;
    }
    if (argMap.containsKey("-maxLength")) {
      maxLength = Integer.parseInt((argMap.get("-maxLength"))[0]);
    }
    op.testOptions.maxLength = 120;
    boolean combo = argMap.containsKey("-combo");
    if (combo) {
      ctpp.useCharacterBasedLexicon = true;
      op.testOptions.maxSpanForTags = 10;
      op.doDep = false;
      op.dcTags = false;
    }

    LexicalizedParser lp = null;
    Lexicon lex = null;
    if (argMap.containsKey("-parser")) {
      String[] parserArgs = (argMap.get("-parser"));
      if (parserArgs.length > 1) {
        FileFilter trainFilt = new NumberRangesFileFilter(parserArgs[1], false);
        lp = LexicalizedParser.trainFromTreebank(parserArgs[0], trainFilt, op);
        if (parserArgs.length == 3) {
          String filename = parserArgs[2];
          log.info("Writing parser in serialized format to file " + filename + " ");
          System.err.flush();
          ObjectOutputStream out = IOUtils.writeStreamFromString(filename);
          out.writeObject(lp);
          out.close();
          log.info("done.");
        }
      } else {
        String parserFile = parserArgs[0];
        lp = LexicalizedParser.loadModel(parserFile, op);
      }
      lex = lp.getLexicon();
      op = lp.getOp();
      ctpp = (ChineseTreebankParserParams) op.tlpParams;
    }

    if (argMap.containsKey("-rad")) {
      ctpp.useUnknownCharacterModel = true;
    }

    if (argMap.containsKey("-lengthPenalty")) {
      ctpp.lengthPenalty = Double.parseDouble((argMap.get("-lengthPenalty"))[0]);
    }

    if (argMap.containsKey("-penaltyType")) {
      ctpp.penaltyType = Integer.parseInt((argMap.get("-penaltyType"))[0]);
    }

    if (argMap.containsKey("-lex")) {
      String[] lexArgs = (argMap.get("-lex"));
      if (lexArgs.length > 1) {
        Index<String> wordIndex = new HashIndex<>();
        Index<String> tagIndex = new HashIndex<>();
        lex = ctpp.lex(op, wordIndex, tagIndex);
        MemoryTreebank rawTrainTreebank = op.tlpParams.memoryTreebank();
        FileFilter trainFilt = new NumberRangesFileFilter(lexArgs[1], false);
        rawTrainTreebank.loadPath(new File(lexArgs[0]), trainFilt);
        log.info("Done reading trees.");
        MemoryTreebank trainTreebank;
        if (argMap.containsKey("-annotate")) {
          trainTreebank = new MemoryTreebank();
          TreeAnnotator annotator = new TreeAnnotator(ctpp.headFinder(), ctpp, op);
          for (Tree tree : rawTrainTreebank) {
            tree = annotator.transformTree(tree);
            trainTreebank.add(tree);
          }
          log.info("Done annotating trees.");
        } else {
          trainTreebank = rawTrainTreebank;
        }
        lex.initializeTraining(trainTreebank.size());
        lex.train(trainTreebank);
        lex.finishTraining();
        log.info("Done training lexicon.");
        if (lexArgs.length == 3) {
          String filename = lexArgs.length == 3 ? lexArgs[2] : "parsers/chineseCharLex.ser.gz";
          log.info("Writing lexicon in serialized format to file " + filename + " ");
          System.err.flush();
          ObjectOutputStream out = IOUtils.writeStreamFromString(filename);
          out.writeObject(lex);
          out.close();
          log.info("done.");
        }
      } else {
        String lexFile = lexArgs.length == 1 ? lexArgs[0] : "parsers/chineseCharLex.ser.gz";
        log.info("Reading Lexicon from file " + lexFile);
        ObjectInputStream in = IOUtils.readStreamFromString(lexFile);
        try {
          lex = (Lexicon) in.readObject();
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Bad serialized file: " + lexFile);
        }
        in.close();
      }
    }

    if (argMap.containsKey("-test")) {
      boolean segmentWords = ctpp.segment;
      boolean parse = lp != null;
      assert (parse || segmentWords);
      //      WordCatConstituent.collinizeWords = argMap.containsKey("-collinizeWords");
      //      WordCatConstituent.collinizeTags = argMap.containsKey("-collinizeTags");
      WordSegmenter seg = null;
      if (segmentWords) {
        seg = (WordSegmenter) lex;
      }
      String[] testArgs = (argMap.get("-test"));
      MemoryTreebank testTreebank = op.tlpParams.memoryTreebank();
      FileFilter testFilt = new NumberRangesFileFilter(testArgs[1], false);
      testTreebank.loadPath(new File(testArgs[0]), testFilt);
      TreeTransformer subcategoryStripper = op.tlpParams.subcategoryStripper();
      AbstractCollinizer collinizer = ctpp.collinizer();

      WordCatEquivalenceClasser eqclass = new WordCatEquivalenceClasser();
      WordCatEqualityChecker eqcheck = new WordCatEqualityChecker();
      EquivalenceClassEval basicEval = new EquivalenceClassEval(eqclass, eqcheck, "basic");
      EquivalenceClassEval collinsEval = new EquivalenceClassEval(eqclass, eqcheck, "collinized");
      List<String> evalTypes = new ArrayList<>(3);
      boolean goodPOS = false;
      if (segmentWords) {
        evalTypes.add(WordCatConstituent.wordType);
        if (ctpp.segmentMarkov && !parse) {
          evalTypes.add(WordCatConstituent.tagType);
          goodPOS = true;
        }
      }
      if (parse) {
        evalTypes.add(WordCatConstituent.tagType);
        evalTypes.add(WordCatConstituent.catType);
        if (combo) {
          evalTypes.add(WordCatConstituent.wordType);
          goodPOS = true;
        }
      }
      TreeToBracketProcessor proc = new TreeToBracketProcessor(evalTypes);

      log.info("Testing...");
      for (Tree goldTop : testTreebank) {
        Tree gold = goldTop.firstChild();
        List<HasWord> goldSentence = gold.yieldHasWord();
        if (goldSentence.size() > maxLength) {
          log.info("Skipping sentence; too long: " + goldSentence.size());
          continue;
        } else {
          log.info("Processing sentence; length: " + goldSentence.size());
        }
        List<HasWord> s;
        if (segmentWords) {
          StringBuilder goldCharBuf = new StringBuilder();
          for (HasWord aGoldSentence : goldSentence) {
            StringLabel word = (StringLabel) aGoldSentence;
            goldCharBuf.append(word.value());
          }
          String goldChars = goldCharBuf.toString();
          s = seg.segment(goldChars);
        } else {
          s = goldSentence;
        }
        Tree tree;
        if (parse) {
          tree = lp.parseTree(s);
          if (tree == null) {
            throw new RuntimeException("PARSER RETURNED NULL!!!");
          }
        } else {
          tree = Trees.toFlatTree(s);
          tree = subcategoryStripper.transformTree(tree);
        }

        if (pw != null) {
          if (parse) {
            tree.pennPrint(pw);
          } else {
            Iterator sentIter = s.iterator();
            for (; ;) {
              Word word = (Word) sentIter.next();
              pw.print(word.word());
              if (sentIter.hasNext()) {
                pw.print(" ");
              } else {
                break;
              }
            }
          }
          pw.println();
        }

        if (eval) {
          Collection ourBrackets, goldBrackets;
          ourBrackets = proc.allBrackets(tree);
          goldBrackets = proc.allBrackets(gold);
          if (goodPOS) {
            ourBrackets.addAll(proc.commonWordTagTypeBrackets(tree, gold));
            goldBrackets.addAll(proc.commonWordTagTypeBrackets(gold, tree));
          }

          basicEval.eval(ourBrackets, goldBrackets);
          System.out.println("\nScores:");
          basicEval.displayLast();

          Tree collinsTree = collinizer.transformTree(tree, gold);
          Tree collinsGold = collinizer.transformTree(gold, gold);
          ourBrackets = proc.allBrackets(collinsTree);
          goldBrackets = proc.allBrackets(collinsGold);
          if (goodPOS) {
            ourBrackets.addAll(proc.commonWordTagTypeBrackets(collinsTree, collinsGold));
            goldBrackets.addAll(proc.commonWordTagTypeBrackets(collinsGold, collinsTree));
          }

          collinsEval.eval(ourBrackets, goldBrackets);
          System.out.println("\nCollinized scores:");
          collinsEval.displayLast();

          System.out.println();
        }
      }
      if (eval) {
        basicEval.display();
        System.out.println();
        collinsEval.display();
      }
    }
  }

}
