package edu.stanford.nlp.parser.tools;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;

/**
 * Prints out a frequency distribution of all punctuation tags in a treebank.
 * 
 * @author Spence Green
 *
 */
public final class PunctFrequencyDist {

  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] punct_tag tree_file \n\n",PunctFrequencyDist.class.getName()));
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
    String puncTag = null;

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
        puncTag = args[i++];
        
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

    Counter<String> puncTypes = new ClassicCounter<>();
    for(Tree t : tb) {
      List<CoreLabel> yield = t.taggedLabeledYield();
      for(CoreLabel word : yield)
        if(word.tag().equals(puncTag))
          puncTypes.incrementCount(word.word());
    }

    List<String> biggestKeys = new ArrayList<>(puncTypes.keySet());
    Collections.sort(biggestKeys, Counters.toComparatorDescending(puncTypes));

    PrintWriter pw = tlpp.pw();
    for(String wordType : biggestKeys)
      pw.printf("%s\t%d%n", wordType,(int) puncTypes.getCount(wordType));
    pw.close();
  }

}
