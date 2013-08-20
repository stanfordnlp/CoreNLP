package edu.stanford.nlp.trees;

import java.io.Reader;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.util.*;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.CollectionValuedMap;


/** @author Christopher Manning */
public class FindDifferentlyTagged {


  private static <E> List<List<E>> subLists(List <E> lis) {
    List<List<E>> slists = new ArrayList<List<E>>();
    for (int i = 0; i < lis.size(); i++) {
      for (int j = i + 1; j < lis.size(); j++) {
        slists.add(lis.subList(i,j));
      }
    }
    return slists;
  }


  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("This main method will let you variously manipulate and view a treebank.");
      System.err.println("Usage: java DiskTreebank [-flags]* treebankPath fileRanges");
      System.err.println("Useful flags include:");
      System.err.println("\t-maxLength n\t-suffix ext\t-treeReaderFactory class");
      System.err.println("\t-pennPrint\t-encoding enc\t-tlp class\t-sentenceLengths");
      System.err.println("\t-summary\t-decimate\t-yield\t-correct\t-punct");
      return;
    }
    int i = 0;
    int maxLength = -1;
    boolean normalized = false;
    boolean correct = false;
    boolean summary = false;
    boolean yield = false;
    String encoding = TreebankLanguagePack.DEFAULT_ENCODING;
    String suffix = Treebank.DEFAULT_TREE_FILE_SUFFIX;
    TreeReaderFactory trf = null;
    TreebankLanguagePack tlp = null;

    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equals("-maxLength") && i + 1 < args.length) {
        maxLength = Integer.parseInt(args[i+1]);
        i += 2;
      } else if (args[i].equals("-normalized")) {
        normalized = true;
        i += 1;
      } else if (args[i].equalsIgnoreCase("-tlp")) {
        try {
          final Object o = Class.forName(args[i+1]).newInstance();
          tlp = (TreebankLanguagePack) o;
          trf = tlp.treeReaderFactory();
        } catch (Exception e) {
          System.err.println("Couldn't instantiate as TreebankLangParserParams: " + args[i+1]);
          return;
        }
        i += 2;
      } else if (args[i].equals("-treeReaderFactory") || args[i].equals("-trf")) {
        try {
          final Object o = Class.forName(args[i+1]).newInstance();
          trf = (TreeReaderFactory) o;
        } catch (Exception e) {
          System.err.println("Couldn't instantiate as TreeReaderFactory: " + args[i+1]);
          return;
        }
        i += 2;
      } else if (args[i].equals("-suffix")) {
        suffix = args[i+1];
        i += 2;
      } else if (args[i].equals("-encoding")) {
        encoding = args[i+1];
        i += 2;
      } else if (args[i].equals("-correct")) {
        correct = true;
        i += 1;
      } else if (args[i].equals("-summary")) {
        summary = true;
        i += 1;
      } else if (args[i].equals("-yield")) {
        yield = true;
        i += 1;
      } else {
        System.err.println("Unknown option: " + args[i]);
        i++;
      }
    }
    Treebank treebank;
    if (trf == null) {
      trf = new TreeReaderFactory() {
          public TreeReader newTreeReader(Reader in) {
            return new PennTreeReader(in, new LabeledScoredTreeFactory());
          }
        };
    }
    if (normalized) {
      treebank = new DiskTreebank();
    } else {
      treebank = new DiskTreebank(trf, encoding);
    }

    final PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, encoding), true);

    if (i + 1 < args.length ) {
      treebank.loadPath(args[i], new NumberRangesFileFilter(args[i+1], true));
    } else {
      treebank.loadPath(args[i], suffix, true);
    }
    System.err.println("Loaded " + treebank.size() + " trees from " + args[i]);

    CollectionValuedMap<List<Label>,List<Label>> cvm = new CollectionValuedMap<List<Label>,List<Label>>();

    for (Tree t : treebank) {
      // List<TaggedWord> twList = t.taggedYield();
      // List<List<TaggedWord>> sublists = subLists(twList);
      // System.out.println(sublists);
      List<List<Label>> tagSubLists = subLists(t.preTerminalYield());
      List<List<Label>> wordSubLists = subLists(t.yield());
      assert(tagSubLists.size() == wordSubLists.size());

      Iterator<List<Label>> wordSubListIterator = wordSubLists.iterator();
      for (List<Label> tagSubList : tagSubLists) {
        List<Label> wordSubList = wordSubListIterator.next();
        if (tagSubList.size() < 5) {
          continue;
        }
        cvm.add(wordSubList, tagSubList);

      }
    }

    Set<Map.Entry<List<Label>,Collection<List<Label>>>> entrySet = cvm.entrySet();
    for (Map.Entry<List<Label>,Collection<List<Label>>> entry : entrySet) {
      List<Label> key = entry.getKey();
      Collection<List<Label>> val = entry.getValue();
      if (val.size() > 1) {
        System.err.println(key + " is tagged " + val.size() + " ways:");
        for (List<Label> item : val) {
          System.err.print("  ");
          System.err.println(item);
        }
      }
    }
  }

}
