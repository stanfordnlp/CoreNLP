package edu.stanford.nlp.international.spanish.pipeline;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeReaderFactory;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeNormalizer;
import edu.stanford.nlp.trees.tregex.ParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Generics;

/**
 * Infer missing part-of-speech tags for multi-word tokens in a treebank.
 *
 * @author Jon Gauthier
 * @author Spence Green (original French version)
 */
public final class MultiWordPreprocessor {

  private static int nMissingPOS = 0;
  private static int nMissingPhrasal = 0;

  private static class ManualUWModel {

    private static final Set<String> commonNouns = Generics.newHashSet();
    private static final String commonNounStr =
      "acecho";

    private static final Set<String> commonPluralNouns = Generics.newHashSet();
    private static final String commonPluralNounStr =
      "abuelos";



    // "A. Alezais alfa Annick Appliances Ardenne Artois baptiste Bargue Bellanger Bregenz clefs Coeurs ...conomie consumer " +
      // "contrôleur Coopérative Coppée cuisson dédoublement demandeuse défraie Domestic dépistage Elektra Elettrodomestici " +
      // "Essonnes Fair Finparcom Gelisim gorge Happy Indesit Italia jockey Lawrence leone Levi machinisme Mc.Donnel MD Merloni " +
      // "Meydan ménagers Muenchener Parcel Prost R. sam Sara Siège silos SPA Stateman Valley Vanity VF Vidal Vives Yorker Young Zemment";

    private static final Set<String> adjectives = Generics.newHashSet();
    private static final String aStr = ""; // TODO "astral bis bovin gracieux intégrante italiano sanguin sèche";

    private static final Set<String> preps = Generics.newHashSet();
    private static final String pStr = ""; // TODO "c o t";

    private static int nUnknownWordTypes;

    static {
      nouns.addAll(Arrays.asList(nStr.split("\\s+")));
      adjectives.addAll(Arrays.asList(aStr.split("\\s+")));
      preps.addAll(Arrays.asList(pStr.split("\\s+")));
      nUnknownWordTypes = nouns.size() + adjectives.size() + preps.size();
    }

    private static final Pattern digit = Pattern.compile("\\d+");
    private static final Pattern

    public static String getTag(String word) {
      if(digit.matcher(word).find())
        return "z0";
      else if(nouns.contains(word))
        return "ncms000";
      else if(adjectives.contains(word))
        return "aq0000";
      else if(preps.contains(word))
        return "sp000";

      System.err.println("No POS tag for " + word);

      return Character.isUpperCase(word.codePointAt(0))
        ? "np00000" : "ncms000";
    }
  }

  public static void updateTagger(TwoDimensionalCounter<String,String> tagger,
                                  Tree t) {
    List<CoreLabel> yield = t.taggedLabeledYield();
    for (CoreLabel cl : yield) {
      if (cl.tag().equals(SpanishTreeNormalizer.MW_TAG))
        continue;

      tagger.incrementCount(cl.word(), cl.tag());
    }
  }


  public static void traverseAndFix(Tree t,
      TwoDimensionalCounter<String, String> pretermLabel,
      TwoDimensionalCounter<String, String> unigramTagger) {
    if(t.isPreTerminal()) {
      if(t.value().equals(SpanishTreeNormalizer.MW_TAG)) {
        nMissingPOS++;
        String word = t.firstChild().value();
        String tag = (unigramTagger.firstKeySet().contains(word)) ?
          Counters.argmax(unigramTagger.getCounter(word)) : ManualUWModel.getTag(word);
        t.setValue(tag);
      }

      return;
    }

    for(Tree kid : t.children())
      traverseAndFix(kid,pretermLabel,unigramTagger);

    //Post-order visit
    if(t.value().equals(SpanishTreeNormalizer.MW_PHRASE_TAG)) {
      nMissingPhrasal++;
      StringBuilder sb = new StringBuilder();
      for(Tree kid : t.children())
        sb.append(kid.value()).append(" ");

      String posSequence = sb.toString().trim();
      if(pretermLabel.firstKeySet().contains(posSequence)) {
        String phrasalCat = Counters.argmax(pretermLabel.getCounter(posSequence));
        t.setValue(phrasalCat);
      } else {
        System.out.println("No phrasal cat for: " + posSequence);
      }
    }
  }


  private static void resolveDummyTags(File treeFile,
      TwoDimensionalCounter<String, String> pretermLabel,
      TwoDimensionalCounter<String, String> unigramTagger) {

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      PrintWriter pw = new PrintWriter(new PrintStream(new FileOutputStream(new File(treeFile + ".fixed")),false,"UTF-8"));

      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        System.err.println(t);
        traverseAndFix(t, pretermLabel, unigramTagger);
        pw.println(t.toString());
      }
      pw.close();
      tr.close();

      System.out.println("Processed " +nTrees+ " trees");

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static final TregexPattern pMWE = TregexPattern.compile("/^MW/");

  static public void countMWEStatistics(Tree t,
      TwoDimensionalCounter<String, String> unigramTagger,
      TwoDimensionalCounter<String, String> labelPreterm,
      TwoDimensionalCounter<String, String> pretermLabel,
      TwoDimensionalCounter<String, String> labelTerm,
      TwoDimensionalCounter<String, String> termLabel)
  {
    updateTagger(unigramTagger,t);

    //Count MWE statistics
    TregexMatcher m = pMWE.matcher(t);
    while (m.findNextMatchingNode()) {
      Tree match = m.getMatch();
      String label = match.value();
      if(label.equals(SpanishTreeNormalizer.MW_PHRASE_TAG))
        continue;

      String preterm = Sentence.listToString(match.preTerminalYield());
      String term = Sentence.listToString(match.yield());

      labelPreterm.incrementCount(label,preterm);
      pretermLabel.incrementCount(preterm,label);
      labelTerm.incrementCount(label,term);
      termLabel.incrementCount(term, label);
    }
  }


  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      System.err.printf("Usage: java %s file%n", MultiWordPreprocessor.class.getName());
      System.exit(-1);
    }

    final File treeFile = new File(args[0]);
    TwoDimensionalCounter<String,String> labelTerm =
      new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> termLabel =
      new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> labelPreterm =
      new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> pretermLabel =
      new TwoDimensionalCounter<String,String>();

    TwoDimensionalCounter<String,String> unigramTagger =
      new TwoDimensionalCounter<String,String>();

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      for(Tree t; (t = tr.readTree()) != null;) {
        countMWEStatistics(t, unigramTagger,
                           labelPreterm, pretermLabel, labelTerm, termLabel);
      }
      tr.close(); //Closes the underlying reader

      System.out.println("Resolving DUMMY tags");
      resolveDummyTags(treeFile, pretermLabel, unigramTagger);

      System.out.println("#Unknown Word Types: " + ManualUWModel.nUnknownWordTypes);
      System.out.println("#Missing POS: " + nMissingPOS);
      System.out.println("#Missing Phrasal: " + nMissingPhrasal);

      System.out.println("Done!");

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
