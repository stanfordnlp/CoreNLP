package edu.stanford.nlp.trees;

import java.io.*;
import java.util.*;
import java.text.NumberFormat;
import java.text.DecimalFormat;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ReflectionLoading;


/** This is just a main method and other static methods for
 *  command-line manipulation, statistics, and testing of
 *  Treebank objects.  It has been separated out into its
 *  own class so that users of Treebank classes don't have
 *  to inherit all this class' dependencies.
 *
 *  @author Christopher Manning
 */
public class Treebanks {

  private Treebanks() {} // static methods

  private static void printUsage() {
    System.err.println("This main method will let you variously manipulate and view a treebank.");
    System.err.println("Usage: java Treebanks [-flags]* treebankPath [fileRanges]");
    System.err.println("Useful flags include:");
    System.err.println("\t-maxLength n\t-suffix ext\t-treeReaderFactory class");
    System.err.println("\t-pennPrint\t-encoding enc\t-tlp class\t-sentenceLengths");
    System.err.println("\t-summary\t-decimate\t-yield\t-correct\t-punct");
    System.err.println("\t-oneLine\t-words\t-taggedWords\t-annotate options");
  }

  /**
   * Loads treebank and prints it.
   * All files below the designated <code>filePath</code> within the given
   * number range if any are loaded.  You can normalize the trees or not
   * (English-specific) and print trees one per line up to a certain length
   * (for EVALB).
   * <p>
   * Usage: <code>
   * java edu.stanford.nlp.trees.Treebanks [-maxLength n|-normalize|-treeReaderFactory class] filePath [numberRanges]
   * </code>
   *
   * @param args Array of command-line arguments
   * @throws java.io.IOException If there is a treebank file access problem
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      printUsage();
      return;
    }
    int i = 0;
    final int maxLength;
    final int minLength;
    int maxL = Integer.MAX_VALUE;
    int minL = -1;
    boolean normalized = false;
    boolean decimate = false;
    boolean pennPrintTrees = false;
    boolean oneLinePrint = false;
    boolean printTaggedWords = false;
    boolean printWords = false;
    boolean correct = false;
    String annotationOptions = null;
    boolean summary = false;
    boolean timing = false;
    boolean yield = false;
    boolean punct = false;
    boolean sentenceLengths = false;
    boolean countTaggings = false;
    boolean removeCodeTrees = false;
    String decimatePrefix = null;
    String encoding = TreebankLanguagePack.DEFAULT_ENCODING;
    String suffix = Treebank.DEFAULT_TREE_FILE_SUFFIX;
    TreeReaderFactory trf = null;
    TreebankLanguagePack tlp = null;
    List<Filter<Tree>> filters = new ArrayList<Filter<Tree>>();

    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equals("-maxLength") && i + 1 < args.length) {
        maxL = Integer.parseInt(args[i+1]);
        i += 2;
      } else if (args[i].equals("-minLength") && i + 1 < args.length) {
        minL = Integer.parseInt(args[i+1]);
        i += 2;
      } else if (args[i].equals("-h") || args[i].equals("-help")) {
        printUsage();
        i++;
      } else if (args[i].equals("-normalized")) {
        normalized = true;
        i += 1;
      } else if (args[i].equalsIgnoreCase("-tlp")) {
        try {
          final Object o = Class.forName(args[i+1]).newInstance();
          tlp = (TreebankLanguagePack) o;
          trf = tlp.treeReaderFactory();
        } catch (Exception e) {
          System.err.println("Couldn't instantiate as TreebankLanguagePack: " + args[i+1]);
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
      } else if (args[i].equals("-decimate")) {
        decimate = true;
        decimatePrefix = args[i+1];
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
      } else if (args[i].equals("-punct")) {
        punct = true;
        i += 1;
      } else if (args[i].equals("-pennPrint")) {
        pennPrintTrees = true;
        i++;
      } else if (args[i].equals("-oneLine")) {
        oneLinePrint = true;
        i++;
      } else if (args[i].equals("-taggedWords")) {
        printTaggedWords = true;
        i++;
      } else if (args[i].equals("-words")) {
        printWords = true;
        i++;
      } else if (args[i].equals("-annotate")) {
        annotationOptions = args[i+1];
        i += 2;
      } else if (args[i].equals("-timing")) {
        timing = true;
        i++;
      } else if (args[i].equals("-countTaggings")) {
        countTaggings = true;
        i++;
      } else if (args[i].equals("-sentenceLengths")) {
        sentenceLengths = true;
        i++;
      } else if (args[i].equals("-removeCodeTrees")) {
        removeCodeTrees = true;
        i++;
      } else if (args[i].equals("-filter")) {
        Filter<Tree> filter = ReflectionLoading.loadByReflection(args[i+1]);
        filters.add(filter);
        i += 2;
      } else {
        System.err.println("Unknown option: " + args[i]);
        i++;
      }
    }

    maxLength = maxL;
    minLength = minL;
    Treebank treebank;
    if (trf == null) {
      trf = in -> new PennTreeReader(in, new LabeledScoredTreeFactory());
    }
    if (normalized) {
      treebank = new DiskTreebank();
    } else {
      treebank = new DiskTreebank(trf, encoding);
    }

    for (Filter<Tree> filter : filters) {
      treebank = new FilteringTreebank(treebank, filter);
    }

    final PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, encoding), true);

    if (i + 1 < args.length ) {
      treebank.loadPath(args[i], new NumberRangesFileFilter(args[i+1], true));
    } else if (i < args.length) {
      treebank.loadPath(args[i], suffix, true);
    } else {
      printUsage();
      return;
    }
    // System.err.println("Loaded " + treebank.size() + " trees from " + args[i]);

    if (annotationOptions != null) {
      // todo Not yet implemented
      System.err.println("annotationOptions not yet implemented");
    }

    if (summary) {
      System.out.println(treebank.textualSummary());
    }
    if (sentenceLengths) {
      sentenceLengths(treebank, args[i], ((i+1)<args.length ? args[i+1]: null), pw);
    }

    if (punct) {
      printPunct(treebank, tlp, pw);
    }

    if (correct) {
      treebank = new EnglishPTBTreebankCorrector().transformTrees(treebank);
    }

    if (pennPrintTrees) {
      treebank.apply(tree -> {
        int length = tree.yield().size();
        if (length >= minLength && length <= maxLength) {
          tree.pennPrint(pw);
          pw.println();
        }
      });
    }

    if (oneLinePrint) {
      treebank.apply(tree -> {
        int length = tree.yield().size();
        if (length >= minLength && length <= maxLength) {
          pw.println(tree);
        }
      });
    }

    if (printWords) {
      final TreeNormalizer tn = new BobChrisTreeNormalizer();
      treebank.apply(tree -> {
        Tree tPrime = tn.normalizeWholeTree(tree, tree.treeFactory());
        int length = tPrime.yield().size();
        if (length >= minLength && length <= maxLength) {
          pw.println(Sentence.listToString(tPrime.taggedYield()));
        }
      });
    }

    if (printTaggedWords) {
      final TreeNormalizer tn = new BobChrisTreeNormalizer();
      treebank.apply(tree -> {
        Tree tPrime = tn.normalizeWholeTree(tree, tree.treeFactory());
        pw.println(Sentence.listToString(tPrime.taggedYield(), false, "_"));
      });
    }

    if (countTaggings) {
      countTaggings(treebank, pw);
    }

    if (yield) {
      treebank.apply(tree -> {
        int length = tree.yield().size();
        if (length >= minLength && length <= maxLength) {
          pw.println(Sentence.listToString(tree.yield()));
        }
      });
    }

    if (decimate) {
      Writer w1 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(decimatePrefix + "-train.txt"), encoding));
      Writer w2 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(decimatePrefix + "-dev.txt"), encoding));
      Writer w3 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(decimatePrefix + "-test.txt"), encoding));
      treebank.decimate(w1, w2, w3);
    }

    if (timing) {
      runTiming(treebank);
    }

    if (removeCodeTrees) {
      // this is a bit of a hack. It only works on an individual file
      if (new File(args[i]).isDirectory()) {
        throw new RuntimeException("-removeCodeTrees only works on a single file");
      }
      String treebankStr = IOUtils.slurpFile(args[i]);
      treebankStr = treebankStr.replaceAll("\\( \\(CODE <[^>]+>\\)\\)", "");
      Writer w = new OutputStreamWriter(new FileOutputStream(args[i]), encoding);
      w.write(treebankStr);
      w.close();
    }
  } // end main()


  private static void printPunct(Treebank treebank, TreebankLanguagePack tlp, PrintWriter pw) {
    if (tlp == null) {
      System.err.println("The -punct option requires you to specify -tlp");
    } else {
      Filter<String> punctTagFilter = tlp.punctuationTagAcceptFilter();
      for (Tree t : treebank) {
        List<TaggedWord> tws = t.taggedYield();
        for (TaggedWord tw : tws) {
          if (punctTagFilter.accept(tw.tag())) {
            pw.println(tw);
          }
        }
      }
    }
  }


  private static void countTaggings(Treebank tb, final PrintWriter pw) {
    final TwoDimensionalCounter<String,String> wtc = new TwoDimensionalCounter<String,String>();
    tb.apply(tree -> {
      List<TaggedWord> tags = tree.taggedYield();
      for (TaggedWord tag : tags)
        wtc.incrementCount(tag.word(), tag.tag());
    });
    for (String key : wtc.firstKeySet()) {
      pw.print(key);
      pw.print('\t');
      Counter<String> ctr = wtc.getCounter(key);
      for (String k2 : ctr.keySet()) {
        pw.print(k2 + '\t' + ctr.getCount(k2) + '\t');
      }
      pw.println();
    }
  }


  private static void runTiming(Treebank treebank) {
    System.out.println();
    Timing.startTime();
    int num = 0;
    for (Tree t : treebank) {
      num += t.yield().size();
    }
    Timing.endTime("traversing corpus, counting words with iterator");
    System.err.println("There were " + num + " words in the treebank.");

    treebank.apply(new TreeVisitor() {
        int num = 0;
        @Override
        public void visitTree(final Tree t) {
          num += t.yield().size();
        }
      });
    System.err.println();
    Timing.endTime("traversing corpus, counting words with TreeVisitor");
    System.err.println("There were " + num + " words in the treebank.");

    System.err.println();
    Timing.startTime();
    System.err.println("This treebank contains " + treebank.size() + " trees.");
    Timing.endTime("size of corpus");
  }


  private static void sentenceLengths(Treebank treebank, String name, String range,
                                     PrintWriter pw) {
    final int maxleng = 150;
    int[] lengthCounts = new int[maxleng+2];
    int numSents = 0;
    int longestSeen = 0;
    int totalWords = 0;
    String longSent = "";
    double median = 0.0;
    NumberFormat nf = new DecimalFormat("0.0");
    boolean foundMedian = false;

    for (Tree t : treebank) {
      numSents++;
      int len = t.yield().size();
      if (len <= maxleng) {
        lengthCounts[len]++;
      } else {
        lengthCounts[maxleng+1]++;
      }
      totalWords += len;
      if (len > longestSeen) {
        longestSeen = len;
        longSent = t.toString();
      }
    }
    System.out.print("Files " + name + ' ');
    if (range != null) {
      System.out.print(range + ' ');
    }
    System.out.println("consists of " + numSents + " sentences");
    int runningTotal = 0;
    for (int i = 0; i <= maxleng; i++) {
      runningTotal += lengthCounts[i];
      System.out.println("  " + lengthCounts[i] + " of length " + i +
              " (running total: " + runningTotal + ')');
      if ( ! foundMedian && runningTotal > numSents / 2) {
        if (numSents % 2 == 0 && runningTotal == numSents / 2 + 1) {
          // right on the boundary
          int j = i - 1;
          while (j > 0 && lengthCounts[j] == 0) {
            j--;
          }
          median = ((double) i + j) / 2;
        } else {
          median =  i;
        }
        foundMedian = true;
      }
    }
    if (lengthCounts[maxleng+1] > 0) {
      runningTotal += lengthCounts[maxleng+1];
      System.out.println("  " + lengthCounts[maxleng+1] +
              " of length " + (maxleng+1) + " to " + longestSeen +
              " (running total: " + runningTotal + ')');
    }
    System.out.println("Average length: " +
            nf.format(((double) totalWords) / numSents) + "; median length: " +
            nf.format(median));
    System.out.println("Longest sentence is of length: " + longestSeen);
    pw.println(longSent);
  }




}
