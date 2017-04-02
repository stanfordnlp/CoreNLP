package edu.stanford.nlp.parser.eval;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Prints a frequency distribution of the unknown word signatures realized in a given treebank file.
 * 
 * @author Spence Green
 *
 */
public class UNKPrinter {


  private static final int minArgs = 1;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] tree_file \n\n",UNKPrinter.class.getName()));
    usage.append("Options:\n");
    usage.append("  -l lang    : Select language settings from " + Language.langList + "\n");
    usage.append("  -e enc     : Encoding.\n");
  }

  public static void main(String[] args) {
    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    DiskTreebank tb = null;
    String encoding = "UTF-8";
    Language lang = Language.English;

    for(int i = 0; i < args.length; i++) {
      if(args[i].startsWith("-")) {
        switch (args[i]) {
          case "-l":
            lang = Language.valueOf(args[++i].trim());
            tlpp = lang.params;

            break;
          case "-e":
            encoding = args[++i];

            break;
          default:
            System.out.println(usage.toString());
            System.exit(-1);
        }

      } else {
        if(tb == null) {
          if(tlpp == null) {
            System.out.println(usage.toString());
            System.exit(-1);
          } else {
            tlpp.setInputEncoding(encoding);
            tlpp.setOutputEncoding(encoding);
            tb = tlpp.diskTreebank();
          }
        }
        tb.loadPath(args[i]);
      }
    }

    PrintWriter pw = tlpp.pw();
    Options op = new Options();
    Options.LexOptions lexOptions = op.lexOptions;
    if(lang == Language.French) {
      lexOptions.useUnknownWordSignatures = 1;
      lexOptions.smartMutation = false;
      lexOptions.unknownSuffixSize = 2;
      lexOptions.unknownPrefixSize = 1;
    } else if(lang == Language.Arabic) {
      lexOptions.smartMutation = false;
      lexOptions.useUnknownWordSignatures = 9;
      lexOptions.unknownPrefixSize = 1;
      lexOptions.unknownSuffixSize = 1;
    }
    Index<String> wordIndex = new HashIndex<>();
    Index<String> tagIndex = new HashIndex<>();
    Lexicon lex = tlpp.lex(op, wordIndex, tagIndex);
    
    int computeAfter = (int) (0.50 * tb.size());
    Counter<String> vocab = new ClassicCounter<>();
    Counter<String> unkCounter = new ClassicCounter<>();
    int treeId = 0;
    for(Tree t : tb) {
      List<Label> yield = t.yield();
      int posId = 0;
      for(Label word : yield) {
        vocab.incrementCount(word.value());
        if(treeId > computeAfter && vocab.getCount(word.value()) < 2.0)
//          if(lex.getUnknownWordModel().getSignature(word.value(), posId++).equals("UNK"))
//            pw.println(word.value());
          unkCounter.incrementCount(lex.getUnknownWordModel().getSignature(word.value(), posId++));
      }
      treeId++;
    }
    
    List<String> biggestKeys = new ArrayList<>(unkCounter.keySet());
    Collections.sort(biggestKeys, Counters.toComparatorDescending(unkCounter));

    for(String wordType : biggestKeys)
      pw.printf("%s\t%d%n", wordType,(int) unkCounter.getCount(wordType));
    pw.close();

    
    pw.close();
  }
}
