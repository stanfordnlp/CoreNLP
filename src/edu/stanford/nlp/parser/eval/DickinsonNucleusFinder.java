package edu.stanford.nlp.parser.eval;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.io.PrintFile;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.ConstituentFactory;
import edu.stanford.nlp.trees.LabeledConstituent;
import edu.stanford.nlp.trees.LabeledScoredConstituentFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Triple;

/**
 * Loosely-based on the variation nuclei method of Dickinson (2005).
 * 
 * @author Spence Green
 *
 */
public class DickinsonNucleusFinder {

  private static final ConstituentFactory cf = new LabeledScoredConstituentFactory();

  protected static Set<Constituent> makeConstituents(Tree t) {
    Set<Constituent> set = new HashSet<Constituent>();
    set.addAll(t.constituents(cf));
    return set;
  }

  private static class ConcreteNgram {
    public String str;
    public int start;
    public int order;

    @Override
    public String toString() { return String.format("%s [%d len:%d]",str,start,order); }

    @Override
    public int hashCode() { return (str.hashCode() << order) ^ start; }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof ConcreteNgram))
        return false;

      ConcreteNgram otherNgram = (ConcreteNgram) o;
      return str.equals(otherNgram.str) &&
      start == otherNgram.start &&
      order == otherNgram.order;
    }
  }


  /**
   * The data structure borrows from suffix arrays of the sort described by e.g. Gusfield (1997).
   * <p>
   * Implementation uses StringBuffer for efficiency since initial runs were very slow using the "+"
   * operator.
   * 
   * @param sentence
   */
  protected static Set<ConcreteNgram> extractNgrams(final ArrayList<Label> sentence) {
    final Set<ConcreteNgram> ngrams = new HashSet<ConcreteNgram>();
    final int len = sentence.size();
    for(int i = 0; i < len; i++) {
      for(int j = i; j < len; j++) {
        ConcreteNgram ngram = new ConcreteNgram();
        ngram.str = Sentence.extractNgram(sentence, i, j + 1);
        ngram.start = i;
        ngram.order = j - i + 1;
        ngrams.add(ngram);
      }
    }
    return ngrams;
  }

  /**
   * Collapse unaries per the recommendation of Dickinson (2005).
   * 
   * @param t
   */
  protected static Tree collapseUnaries(Tree t) {
    if(t == null || t.isPreTerminal()) 
      return t;

    if(t.numChildren() == 1) {
      final Tree fc = t.firstChild();
      if(fc.isPhrasal()) {
        t.setValue(t.value() + "/" + fc.value());
        t.setChildren(fc.children());
        collapseUnaries(t);
      }
    }

    for(Tree kid : t.getChildrenAsList())
      collapseUnaries(kid);

    return t;
  }

  protected static int minArgs = 1;
  protected static StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] tree_file\n\n",DickinsonNucleusFinder.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from " + Languages.listOfLanguages() + ".\n");
    usage.append("  -u         : Collapse unaries.\n");
    usage.append("  -s NUM     : Randomly sample and output num nuclei.\n");
    usage.append("  -f         : Output frequencies instead of entropies.\n");
  }

  /**
   * Evaluate entropy of similar ngrams with multiple constituent labels.
   * 
   * @param args
   */
  public static void main(String[] args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    Language lang = Language.English;
    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    boolean VERBOSE = false;
    boolean collapseUnaries = false;
    boolean outputFreq = false;
    int sampleSize = -1;

    File treeFile = null;

    for(int i = 0; i < args.length; i++) {

      if(args[i].startsWith("-")) {

        if(args[i].equals("-l")) {
          lang = Language.valueOf(args[++i].trim());
          tlpp = Languages.getLanguageParams(lang);

        } else if(args[i].equals("-v")) {
          VERBOSE = true;

        } else if(args[i].equals("-u")) {
          collapseUnaries = true;

        } else if(args[i].equals("-s")) {
          sampleSize = Integer.parseInt(args[++i].trim());

        } else if(args[i].equals("-f")) {
          outputFreq = true;

        } else {
          System.out.println(usage.toString());
          System.exit(-1);
        }

      } else {
        //Required parameters
        treeFile = new File(args[i]);
        break;
      }
    }

    final PrintWriter pwOut = tlpp.pw();

    final Treebank tb = tlpp.diskTreebank();
    tb.loadPath(treeFile);
    if(VERBOSE) pwOut.println(tb.textualSummary());

    pwOut.println("Reading trees:");

    final Map<String,Counter<String>> ngramLabelMap = new HashMap<String,Counter<String>>(20000000);
    final Map<String,Set<Integer>> ngramIndex = new HashMap<String,Set<Integer>>();

    final TreebankLanguagePack tlp = tlpp.treebankLanguagePack();

    int numTrees = 0;
    for(Tree t : tb) {
      if(tlp.isStartSymbol(t.value())) t = t.firstChild();

      if(collapseUnaries) collapseUnaries(t);

      final Set<Constituent> constituents = makeConstituents(t);
      final ArrayList<Label> sentence = t.yield();
      final Set<ConcreteNgram> sentNgrams = extractNgrams(sentence);

      //Add all constituents
      for(final Constituent c : constituents) {
        LabeledConstituent lc = (LabeledConstituent) c;

        ConcreteNgram constituentNgram = new ConcreteNgram();
        constituentNgram.str = Sentence.extractNgram(sentence, lc.start(), lc.end() + 1);
        constituentNgram.start = lc.start();
        constituentNgram.order = lc.end() - lc.start() + 1;

        if(!ngramIndex.containsKey(constituentNgram.str))
          ngramIndex.put(constituentNgram.str, new HashSet<Integer>());
        ngramIndex.get(constituentNgram.str).add(numTrees + 1);

        sentNgrams.remove(constituentNgram);

        if(!ngramLabelMap.containsKey(constituentNgram.str))
          ngramLabelMap.put(constituentNgram.str, new ClassicCounter<String>());
        ngramLabelMap.get(constituentNgram.str).incrementCount(lc.label().value());
      }

      //Add all NIL ngrams
      for(ConcreteNgram ngram : sentNgrams) {
        if(ngram.order == 1) continue; //Ignore unigrams since constituents only span unigrams in the case of unary rules
        if(!ngramLabelMap.containsKey(ngram.str))
          ngramLabelMap.put(ngram.str, new ClassicCounter<String>());
        ngramLabelMap.get(ngram.str).incrementCount("NIL");

        if(!ngramIndex.containsKey(ngram.str))
          ngramIndex.put(ngram.str, new HashSet<Integer>());
        ngramIndex.get(ngram.str).add(numTrees + 1);
      }

      numTrees++;
      if((numTrees % 200) == 0) {
        System.out.print(".");
      } if((numTrees % 4000) == 0) {
        System.out.println();
        System.out.println("Map: " + ngramLabelMap.keySet().size());
      }
    }

    //Compute the "variation nuclei" with multiple labels
    final List<Triple<Double, String, Set<String>>> nucleiSet = new ArrayList<Triple<Double,String,Set<String>>>(40000);
    double entropies = 0.0;
    for(String ngram : ngramLabelMap.keySet()) {
      Counter<String> cnt = ngramLabelMap.get(ngram);

      if(cnt.keySet().size() > 1) { //Definition of a nucleus from Dickinson (2005)
        double freq = cnt.totalCount();
        double entropy = Counters.entropy(cnt);
        entropies += entropy;
        nucleiSet.add(new Triple<Double,String,Set<String>>((outputFreq) ? freq : entropy,ngram,cnt.keySet()));
      }
    }

    //Display final statistics
    pwOut.println("\n\n=======================================================================");
    pwOut.println("Language:             " + lang.toString());
    pwOut.println("Input file:           " + treeFile.getPath());
    pwOut.println("Num. trees:           " + numTrees);
    pwOut.println("Variation nuclei:     " + nucleiSet.size());
    pwOut.println("Macro Avg. entropy:   " + entropies / (double) nucleiSet.size());
    pwOut.println("=======================================================================");

    try {
      PrintStream ps = new PrintFile(treeFile.getName() + ".ngram.index");

      //Randomly sample a subset of the variation nuclei
      if(sampleSize > 0) {
        final Random rand = new Random();
        Set<Integer> sampledIndices = new HashSet<Integer>();
        int numSamples = 0;
        while(numSamples < sampleSize) {
          int sampleIdx = rand.nextInt(nucleiSet.size());
          if( ! sampledIndices.contains(sampleIdx)) {
            Triple<Double,String,Set<String>> nucleus = nucleiSet.get(sampleIdx);
            pwOut.printf("%f\t%s\t%s\n", nucleus.first(), nucleus.third().toString(), nucleus.second());
            ps.printf("%s %s\n", nucleus.second(), nucleus.third().toString());
            Set<Integer> docIds = ngramIndex.get(nucleus.second());
            for(int idx : docIds)
              ps.printf(" %d\n", idx);
            ps.println();
            sampledIndices.add(sampleIdx);
            numSamples++;
          }
        }
        pwOut.printf("\n\nGenerated %d samples and wrote index\n", numSamples);

      } else {
        //Output everything
        for(Triple<Double,String,Set<String>> nucleus : nucleiSet) {
          pwOut.printf("%f\t%s\t%s%n", nucleus.first(), nucleus.third().toString(), nucleus.second());

          //Output the index
          StringBuilder sb = new StringBuilder();
          Set<Integer> treeIds = ngramIndex.get(nucleus.second());
          for(int idx : treeIds) sb.append(idx).append(",");
          ps.printf("%s\t%d\t%s\t%s%n", nucleus.second(), (int) ((double)nucleus.first()), nucleus.third().toString(),sb.toString());
        }
        pwOut.println();
      }

      ps.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}