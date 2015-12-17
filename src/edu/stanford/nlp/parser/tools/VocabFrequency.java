package edu.stanford.nlp.parser.tools;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;

/**
 * Prints a frequency distribution from a collection of trees.
 * 
 * @author Spence Green
 *
 */
public class VocabFrequency {

  private static final int minArgs = 1;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] tree_file \n\n",VocabFrequency.class.getName()));
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

    for(int i = 0; i < args.length; i++) {
      if(args[i].startsWith("-")) {
        switch (args[i]) {
          case "-l":
            Language lang = Language.valueOf(args[++i].trim());
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

    Counter<String> vocab = new ClassicCounter<>();
    for(Tree t : tb) {
      List<Label> yield = t.yield();
      for(Label word : yield)
        vocab.incrementCount(word.value());
    }

    List<String> biggestKeys = new ArrayList<>(vocab.keySet());
    Collections.sort(biggestKeys, Counters.toComparatorDescending(vocab));

    PrintWriter pw = tlpp.pw();
    for(String wordType : biggestKeys)
      pw.printf("%s\t%d%n", wordType,(int) vocab.getCount(wordType));
    pw.close();
  }
}
