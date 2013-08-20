package edu.stanford.nlp.wordseg;

import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.Sets;
import edu.stanford.nlp.util.StringUtils;


/** A data analysis class for comparing dictionary coverage and overlap.
 *
 *  @author Christopher Manning
 */
public class DictionaryComparer {

  private DictionaryComparer() {}

  public static void main(String[] args) throws IOException {
    Random rnd = new Random();
    List<Set<String>> dicts = new ArrayList<Set<String>>();
    List<List<Integer>> overlaps = new ArrayList<List<Integer>>();
    Counter<String> lexiconCount = new ClassicCounter<String>();

    for (String arg: args) {
      System.err.print("Loading " + arg + " ... ");
      Set<String> words = new CorpusDictionary(arg, true).getTable();
      for (String word : words) {
        String wordNorm = ChineseUtils.normalize(word, 1, 1, 1);
        if ( ! wordNorm.equals(word)) {
          EncodingPrintWriter.err.println("Unnormalized dictionary entry: " +
          word + " should be " + wordNorm, "UTF-8");
        }
        lexiconCount.incrementCount(word);
      }
      dicts.add(words);
      System.err.println(words.size() + " words.");
    }

    List<Integer> dictSizes = new ArrayList<Integer>();
    for (Set<String> dict : dicts) {
      dictSizes.add(dict.size());
    }

    List<Set<String>> uniques = new ArrayList<Set<String>>();
    List<Set<String>> badGaps = new ArrayList<Set<String>>();
    int numGood = (args.length - 1) / 2;
    List<String> uniqExx = new ArrayList<String>();
    List<String> gapExx = new ArrayList<String>();
    for (Set<String> dict : dicts) {
      Set<String> uniq = new HashSet<String>(dict);
      List<Integer> olaps = new ArrayList<Integer>();
      for (Set<String> dict2 : dicts) {
        Set<String> intersect = Sets.intersection(dict, dict2);
        olaps.add(intersect.size());
        if (dict != dict2) {
          uniq.removeAll(dict2);
        }
      }
      overlaps.add(olaps);
      uniques.add(uniq);
      if (uniq.size() <= 6) {
        uniqExx.add(uniq.toString());
      } else {
        List<String> uniqList = new ArrayList<String>(uniq);
        Set<String> sample = new HashSet<String>();
        while (sample.size() < 6) {
          int rand = rnd.nextInt(uniq.size());
          String rstr = uniqList.get(rand);
          if ( ! sample.contains(rstr)) {
            sample.add(rstr);
          }
        }
        uniqExx.add(sample.toString());
      }

      Set<String> gaps = new HashSet<String>();
      for (String item : Counters.keysAbove(lexiconCount, numGood)) {
        if ( ! dict.contains(item)) {
          gaps.add(item);
        }
      }
      badGaps.add(gaps);
      if (gaps.size() <= 6) {
        gapExx.add(gaps.toString());
      } else {
        List<String> gapList = new ArrayList<String>(gaps);
        Set<String> sample = new HashSet<String>();
        while (sample.size() < 6) {
          int rand = rnd.nextInt(gaps.size());
          String rstr = gapList.get(rand);
          if ( ! sample.contains(rstr)) {
            sample.add(rstr);
          }
        }
        gapExx.add(sample.toString());
      }
    }

    String[][] table = new String[args.length][args.length + 5];
    String[] cols = new String[args.length + 5];
    cols[0] = "Size";
    for (int i = 0; i < args.length; i++ ) {
      cols[i+1] = args[i].substring(0, 5);
    }
    cols[args.length+1] = "Unique";
    cols[args.length+2] = "Uniq exx";
    cols[args.length+3] = "Gaps";
    cols[args.length+4] = "Gap exx";

    for (int i = 0; i < args.length; i++) {
      table[i][0] = Integer.toString(dictSizes.get(i));
      for (int j = 0; j < args.length; j++) {
        table[i][j+1] = Integer.toString(overlaps.get(i).get(j));
      }
      table[i][args.length+1] = Integer.toString(uniques.get(i).size());
      table[i][args.length+2] = uniqExx.get(i);
      table[i][args.length+3] = Integer.toString(badGaps.get(i).size());
      table[i][args.length+4] = gapExx.get(i);
    }
    // String data = StringUtils.makeAsciiTable(table, args, cols, 7, 0, true);
    String data = StringUtils.makeHTMLTable(table, args, cols);
    EncodingPrintWriter.out.println(data, "UTF-8");
  }

}
