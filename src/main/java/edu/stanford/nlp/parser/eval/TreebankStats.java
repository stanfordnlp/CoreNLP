package edu.stanford.nlp.parser.eval; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.FileFilter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Utility class for extracting a variety of statistics from multi-lingual treebanks.
 *
 * TODO(spenceg) Add sample standard deviation
 *
 * @author Spence Green
 */
public class TreebankStats  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreebankStats.class);

  private final Language languageName;
  private final TreebankLangParserParams tlpp;
  private final List<String> pathNames;

  private enum Split {Train,Dev,Test}
  private Map<Split,Set<String>> splitFileLists;
  private boolean useSplit = false;
  private boolean makeVocab = false;

  private static Set<String> trainVocab = null;

  public TreebankStats(Language langName, List<String> paths, TreebankLangParserParams tlpp) {
    languageName = langName;
    pathNames = paths;
    this.tlpp = tlpp;
  }

  public boolean useSplit(String prefix) {
    Map<Split,File> splitMap = Generics.newHashMap();
    splitMap.put(Split.Train,new File(prefix + ".train"));
    splitMap.put(Split.Test,new File(prefix + ".test"));
    splitMap.put(Split.Dev,new File(prefix + ".dev"));

    splitFileLists = Generics.newHashMap();
    for(Map.Entry<Split, File> entry : splitMap.entrySet()) {
      File f = entry.getValue();
      if(!f.exists()) return false;
      Set<String> files = Generics.newHashSet();
      for(String fileName : IOUtils.readLines(f))
        files.add(fileName);
      splitFileLists.put(entry.getKey(), files);
    }

    useSplit = true;

    return true;
  }

  private ObservedCorpusStats gatherStats(DiskTreebank tb, String name) {
    ObservedCorpusStats ocs = new ObservedCorpusStats(name);

    if(makeVocab) trainVocab = Generics.newHashSet();

    System.out.println("Reading treebank:");
    for(Tree t : tb) {
      Pair<Integer,Integer> treeFacts = dissectTree(t, ocs, makeVocab);
      ocs.addStatsForTree(t.yield().size(), treeFacts.first(), treeFacts.second());
      if(ocs.numTrees % 100 == 0) System.out.print(".");
      else if(ocs.numTrees % 8001 == 0) System.out.println();
    }

    ocs.computeFinalValues();
    System.out.println("done!");
    return ocs;
  }

  /**
   * Returns pair of (depth,breadth) of tree. Does a breadth-first search.
   * @param t
   * @param ocs
   * @param addToVocab
   */
  private static Pair<Integer,Integer> dissectTree(Tree t, ObservedCorpusStats ocs, boolean addToVocab) {
    final Stack<Pair<Integer,Tree>> stack = new Stack<>();
    stack.push(new Pair<>(0, t));

    int maxBreadth = 0;
    int maxDepth = -1;

    if(t == null) {
      throw new RuntimeException("Null tree passed to dissectTree()");

    } else {
      while(!stack.isEmpty()) {
        Pair<Integer,Tree> depthNode = stack.pop();

        final int nodeDepth = depthNode.first();
        final Tree node = depthNode.second();
        if(nodeDepth != maxDepth) {
          maxDepth = nodeDepth;
          if(node.isPhrasal() && stack.size() + 1 > maxBreadth) maxBreadth = stack.size() + 1;
        }

        if(node.isPhrasal()) {
          ocs.addPhrasalBranch(node.value(), node.children().length);
        } else if(node.isPreTerminal())
          ocs.posTags.incrementCount(node.value());
        else if(node.isLeaf()) {
          ocs.words.incrementCount(node.value());
          if(addToVocab) trainVocab.add(node.value());
        }
        for(Tree kid : node.children())
          stack.push(new Pair<>(nodeDepth + 1, kid));
      }
    }

    return new Pair<>(maxDepth, maxBreadth);
  }

  private static void display(ObservedCorpusStats corpStats, boolean displayWords, boolean displayOOV) {
    System.out.println("####################################################################");
    System.out.println("## " + corpStats.getName());
    System.out.println("####################################################################");
    System.out.println();
    corpStats.display(displayWords, displayOOV);
  }

  private static ObservedCorpusStats aggregateStats(List<ObservedCorpusStats> allStats) {
    if(allStats.size() == 0) return null;
    else if(allStats.size() == 1) return allStats.get(0);

    ObservedCorpusStats agStats = new ObservedCorpusStats("CORPUS");
    for(ObservedCorpusStats ocs : allStats) {
      agStats.numTrees += ocs.numTrees;
      agStats.breadth2 += ocs.breadth2;
      agStats.breadths.addAll(ocs.breadths);
      agStats.depth2 += ocs.depth2;
      agStats.depths.addAll(ocs.depths);
      agStats.length2 += ocs.length2;
      agStats.lengths.addAll(ocs.lengths);
      if(ocs.minLength < agStats.minLength) agStats.minLength = ocs.minLength;
      if(ocs.maxLength > agStats.maxLength) agStats.maxLength = ocs.maxLength;
      if(ocs.minBreadth < agStats.minBreadth) agStats.minBreadth = ocs.minBreadth;
      if(ocs.maxBreadth > agStats.maxBreadth) agStats.maxBreadth = ocs.maxBreadth;
      if(ocs.minDepth < agStats.minDepth) agStats.minDepth = ocs.minDepth;
      if(ocs.maxDepth > agStats.maxDepth) agStats.maxDepth = ocs.maxDepth;
      agStats.words.addAll(ocs.words);
      agStats.posTags.addAll(ocs.posTags);
      agStats.phrasalBranching2.addAll(ocs.phrasalBranching2);
      agStats.phrasalBranchingNum2.addAll(ocs.phrasalBranchingNum2);
    }

    agStats.computeFinalValues();
    return agStats;
  }

  public void run(boolean pathsAreFiles, boolean displayWords, boolean displayOOV) {

    if(useSplit) {
      List<ObservedCorpusStats> allSplitStats = new ArrayList<>();
      makeVocab = true;
      for(Map.Entry<Split, Set<String>> split : splitFileLists.entrySet()) {
        DiskTreebank tb = tlpp.diskTreebank();
        FileFilter splitFilter = new SplitFilter(split.getValue());
        for(String path : pathNames)
          tb.loadPath(path, splitFilter);
        ObservedCorpusStats splitStats = gatherStats(tb,languageName.toString() + "." + split.getKey().toString());
        allSplitStats.add(splitStats);
        makeVocab = false;
      }

      display(aggregateStats(allSplitStats), displayWords, displayOOV);
      for(ObservedCorpusStats ocs : allSplitStats)
        display(ocs, displayWords, displayOOV);

    } else if(pathsAreFiles) {
      makeVocab = true;
      for(String path : pathNames) {
        DiskTreebank tb = tlpp.diskTreebank();
        tb.loadPath(path, pathname -> true);

        ObservedCorpusStats stats = gatherStats(tb, languageName.toString() + "  " + path);
        display(stats, displayWords, displayOOV);
        makeVocab = false;
      }

    } else {
      trainVocab = Generics.newHashSet();
      DiskTreebank tb = tlpp.diskTreebank();
      for(String path : pathNames)
        tb.loadPath(path, pathname -> !pathname.isDirectory());

      ObservedCorpusStats allStats = gatherStats(tb, languageName.toString());
      display(allStats, displayWords, displayOOV);
    }
  }

  protected static class SplitFilter implements FileFilter {

    private final Set<String> filterMap;

    public SplitFilter(Set<String> fileList) {
      filterMap = fileList;
    }

    public boolean accept(File f) {
      return filterMap.contains(f.getName());
    }
  }

  protected static class ObservedCorpusStats {
    private final String corpusName;

    public ObservedCorpusStats(String name) {
      corpusName = name;
      words = new ClassicCounter<>();
      posTags = new ClassicCounter<>();
      phrasalBranching2 = new ClassicCounter<>();
      phrasalBranchingNum2 = new ClassicCounter<>();
      lengths = new ArrayList<>();
      depths = new ArrayList<>();
      breadths = new ArrayList<>();
    }

    public String getName() { return corpusName; }

    public void addStatsForTree(int yieldLength, int depth, int breadth) {
      numTrees++;
      breadths.add(breadth);
      breadth2 += breadth;

      lengths.add(yieldLength);
      length2 += yieldLength;

      depths.add(depth);
      depth2 += depth;

      if(depth < minDepth) minDepth = depth;
      else if(depth > maxDepth) maxDepth = depth;

      if(yieldLength < minLength) minLength = yieldLength;
      else if(yieldLength > maxLength) maxLength = yieldLength;

      if(breadth < minBreadth) minBreadth = breadth;
      else if(breadth > maxBreadth) maxBreadth = breadth;
    }

    public double getPercLensLessThan(int maxLen) {
      int lens = 0;
      for(Integer len : lengths)
        if(len <= maxLen)
          lens++;

      return (double) lens / (double) lengths.size();
    }

    public void addPhrasalBranch(String label, int factor) {
      phrasalBranching2.incrementCount(label, factor);
      phrasalBranchingNum2.incrementCount(label);
    }

    public void display(boolean displayWords, boolean displayOOV) {
      NumberFormat nf = new DecimalFormat("0.00");
      System.out.println("======================================================");
      System.out.println(">>> " + corpusName);
      System.out.println(" trees:\t\t" + numTrees);
      System.out.println(" words:\t\t" + words.keySet().size());
      System.out.println(" tokens:\t" + (int) words.totalCount());
      System.out.println(" tags:\t\t" + posTags.size());
      System.out.println(" phrasal types:\t" + phrasalBranchingNum2.keySet().size());
      System.out.println(" phrasal nodes:\t" + (int) phrasalBranchingNum2.totalCount());
      System.out.println(" OOV rate:\t" + nf.format(OOVRate * 100.0) + "%");
      System.out.println("======================================================");
      System.out.println(">>> Per tree means");
      System.out.printf(" depth:\t\t%s\t{min:%d\tmax:%d}\t\ts: %s\n",nf.format(meanDepth),minDepth,maxDepth,nf.format(stddevDepth));
      System.out.printf(" breadth:\t%s\t{min:%d\tmax:%d}\ts: %s\n",nf.format(meanBreadth),minBreadth,maxBreadth,nf.format(stddevBreadth));
      System.out.printf(" length:\t%s\t{min:%d\tmax:%d}\ts: %s\n",nf.format(meanLength),minLength,maxLength,nf.format(stddevLength));
      System.out.println(" branching:\t" + nf.format(meanBranchingFactor));
      System.out.println(" constituents:\t" + nf.format(meanConstituents));
      System.out.println("======================================================");
      System.out.println(">>> Branching factor means by phrasal tag:");
      List<String> sortedKeys = new ArrayList<>(meanBranchingByLabel.keySet());
      Collections.sort(sortedKeys, Counters.toComparator(phrasalBranchingNum2,false,true));
      for(String label : sortedKeys)
        System.out.printf(" %s:\t\t%s  /  %d instances\n", label,nf.format(meanBranchingByLabel.getCount(label)), (int) phrasalBranchingNum2.getCount(label));
      System.out.println("======================================================");
      System.out.println(">>> Phrasal tag counts");
      sortedKeys = new ArrayList<>(phrasalBranchingNum2.keySet());
      Collections.sort(sortedKeys, Counters.toComparator(phrasalBranchingNum2,false,true));
      for(String label : sortedKeys)
        System.out.println(" " + label + ":\t\t" + (int) phrasalBranchingNum2.getCount(label));
      System.out.println("======================================================");
      System.out.println(">>> POS tag counts");
      sortedKeys = new ArrayList<>(posTags.keySet());
      Collections.sort(sortedKeys, Counters.toComparator(posTags,false,true));
      for(String posTag : sortedKeys)
        System.out.println(" " + posTag + ":\t\t" + (int) posTags.getCount(posTag));
      System.out.println("======================================================");

      if(displayWords) {
        System.out.println(">>> Word counts");
        sortedKeys = new ArrayList<>(words.keySet());
        Collections.sort(sortedKeys, Counters.toComparator(words,false,true));
        for(String word : sortedKeys)
          System.out.println(" " + word + ":\t\t" + (int) words.getCount(word));
        System.out.println("======================================================");
      }
      if(displayOOV) {
        System.out.println(">>> OOV word types");
        for(String word : oovWords)
          System.out.println(" " + word);
        System.out.println("======================================================");
      }
    }

    public void computeFinalValues() {
      final double denom = (double) numTrees;
      meanDepth = depth2 / denom;
      meanLength = length2 / denom;
      meanBreadth = breadth2 / denom;
      meanConstituents = phrasalBranchingNum2.totalCount() / denom;
      meanBranchingFactor = phrasalBranching2.totalCount() / phrasalBranchingNum2.totalCount();

      //Compute *actual* stddev (we iterate over the whole population)
      for(int d : depths)
        stddevDepth += Math.pow(d - meanDepth, 2);
      stddevDepth = Math.sqrt(stddevDepth / denom);

      for(int l : lengths)
        stddevLength += Math.pow(l - meanLength, 2);
      stddevLength = Math.sqrt(stddevLength / denom);

      for(int b : breadths)
        stddevBreadth += Math.pow(b - meanBreadth, 2);
      stddevBreadth = Math.sqrt(stddevBreadth / denom);

      meanBranchingByLabel = new ClassicCounter<>();
      for(String label : phrasalBranching2.keySet()) {
        double mean = phrasalBranching2.getCount(label) / phrasalBranchingNum2.getCount(label);
        meanBranchingByLabel.incrementCount(label, mean);
      }

      oovWords = Generics.newHashSet(words.keySet());
      oovWords.removeAll(trainVocab);
      OOVRate = (double) oovWords.size() / (double) words.keySet().size();
    }

    //Corpus wide
    public final Counter<String> words;
    public final Counter<String> posTags;

    private final Counter<String> phrasalBranching2;
    private final Counter<String> phrasalBranchingNum2;

    public int numTrees = 0;
    private double depth2 = 0.0;
    private double breadth2 = 0.0;
    private double length2 = 0.0;
    private final List<Integer> lengths;
    private final List<Integer> breadths;
    private final List<Integer> depths;

    //Tree-level Averages
    private Counter<String> meanBranchingByLabel;
    private double meanDepth = 0.0;
    private double stddevDepth = 0.0;
    private double meanBranchingFactor = 0.0;
    private double meanConstituents = 0.0;
    private double meanLength = 0.0;
    private double stddevLength = 0.0;
    private double meanBreadth = 0.0;
    private double stddevBreadth = 0.0;

    private double OOVRate = 0.0;
    private Set<String> oovWords;

    //Mins and maxes
    public int minLength = Integer.MAX_VALUE;
    public int maxLength = Integer.MIN_VALUE;
    public int minDepth = Integer.MAX_VALUE;
    public int maxDepth = Integer.MIN_VALUE;
    public int minBreadth = Integer.MAX_VALUE;
    public int maxBreadth = Integer.MIN_VALUE;
  }


  private static final int MIN_ARGS = 2;
  private static String usage() {
    StringBuilder usage = new StringBuilder();
    String nl = System.getProperty("line.separator");
    usage.append(String.format("Usage: java %s [OPTS] LANG paths%n%n",TreebankStats.class.getName()));
    usage.append("Options:").append(nl);
    usage.append(" LANG is one of " + Language.langList).append(nl);
    usage.append("  -s prefix : Use a split (extensions must be dev/test/train)").append(nl);
    usage.append("  -w        : Show word distribution").append(nl);
    usage.append("  -f        : Path list is a set of files, and the first file is the training set").append(nl);
    usage.append("  -o        : Print OOV words.").append(nl);
    return usage.toString();
  }

  private static Map<String,Integer> optArgDefs() {
    Map<String,Integer> optArgDefs = Generics.newHashMap(4);
    optArgDefs.put("s", 1);
    optArgDefs.put("w", 0);
    optArgDefs.put("f", 0);
    optArgDefs.put("o", 0);
    return optArgDefs;
  }

  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length < MIN_ARGS) {
      log.info(usage());
      System.exit(-1);
    }

    Properties options = StringUtils.argsToProperties(args, optArgDefs());
    String splitPrefix = options.getProperty("s", null);
    boolean SHOW_WORDS = PropertiesUtils.getBool(options, "w", false);
    boolean pathsAreFiles = PropertiesUtils.getBool(options, "f", false);
    boolean SHOW_OOV = PropertiesUtils.getBool(options, "o", false);

    String[] parsedArgs = options.getProperty("","").split("\\s+");
    if (parsedArgs.length != MIN_ARGS) {
      log.info(usage());
      System.exit(-1);
    }

    Language language = Language.valueOf(parsedArgs[0]);
    List<String> corpusPaths = new ArrayList<>(parsedArgs.length - 1);
    for (int i = 1; i < parsedArgs.length; ++i) {
      corpusPaths.add(parsedArgs[i]);
    }

    TreebankLangParserParams tlpp = language.params;
    TreebankStats cs = new TreebankStats(language,corpusPaths,tlpp);
    if(splitPrefix != null) {
      if(!cs.useSplit(splitPrefix)) log.info("Could not load split!");
    }
    cs.run(pathsAreFiles, SHOW_WORDS, SHOW_OOV);
  }
}
