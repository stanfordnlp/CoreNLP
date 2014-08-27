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
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Clean up an AnCora treebank which has been processed to expand multi-word
 * tokens into separate leaves. (This prior splitting task is performed by
 * {@link SpanishTreeNormalizer} through the {@link SpanishXMLTreeReader}
 * class).
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
      commonNouns.addAll(Arrays.asList(commonNounStr.split("\\s+")));
      commonPluralNouns.addAll(Arrays.asList(commonPluralNounStr.split("\\s+")));
      adjectives.addAll(Arrays.asList(aStr.split("\\s+")));
      preps.addAll(Arrays.asList(pStr.split("\\s+")));
      nUnknownWordTypes = // nouns.size() +
        adjectives.size() + preps.size();
    }

    private static final Pattern digit = Pattern.compile("\\d+");
    private static final Pattern participle = Pattern.compile("[ai]d[oa]$");

    public static String getTag(String word) {
      if(digit.matcher(word).find())
        return "z0";
      else if(commonNouns.contains(word))
        return "ncms000";
      else if(commonPluralNouns.contains(word))
        return "ncmp000";
      else if(adjectives.contains(word))
        return "aq0000";
      else if(preps.contains(word))
        return "sp000";

      // Fallbacks
      if (participle.matcher(word).find())
        return "aq0000";

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
                                    TwoDimensionalCounter<String, String> unigramTagger,
                                    boolean retainNER) {
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
      traverseAndFix(kid, pretermLabel, unigramTagger, retainNER);

    // Post-order visit
    if(t.value().startsWith(SpanishTreeNormalizer.MW_PHRASE_TAG)) {
      nMissingPhrasal++;

      String phrasalCat = inferPhrasalCategory(t, pretermLabel, retainNER);
      if (phrasalCat != null)
        t.setValue(phrasalCat);
    }
  }

  /**
   * Attempt to infer the phrasal category of the given node, which
   * heads words which were expanded from a multi-word token.
   */
  private static String inferPhrasalCategory(Tree t,
                                             TwoDimensionalCounter<String, String> pretermLabel,
                                             boolean retainNER) {
    String phraseValue = t.value();

    // Retrieve the part-of-speech assigned to the original multi-word
    // token
    String originalPos = phraseValue.substring(phraseValue.lastIndexOf('_') + 1);
    // TODO account for CLI flag
    if (originalPos.length() > 0 && originalPos.charAt(0) == 'n') {
      // TODO may lead to some funky trees if a child somehow gets an
      // incorrect tag -- e.g. we may have a `grup.nom` head a `vmis000`

      if (!retainNER)
        return "grup.nom";

      char nerTag = phraseValue.charAt(phraseValue.length() - 1);
      switch (nerTag) {
      case 'l':
        return "grup.nom.lug";
      case 'o':
        return "grup.nom.org";
      case 'p':
        return "grup.nom.pers";
      case '0':
        return "grup.nom.otros";
      default:
        return "grup.nom";
      }
    }

    // Fallback: try to infer based on part-of-speech sequence formed by
    // constituents
    StringBuilder sb = new StringBuilder();
    for(Tree kid : t.children())
      sb.append(kid.value()).append(" ");

    String posSequence = sb.toString().trim();
    if(pretermLabel.firstKeySet().contains(posSequence))
      return Counters.argmax(pretermLabel.getCounter(posSequence));
    else
      System.err.println("No phrasal cat for: " + posSequence);

    // Give up.
    return null;
  }

  private static void resolveDummyTags(File treeFile,
                                       TwoDimensionalCounter<String, String> pretermLabel,
                                       TwoDimensionalCounter<String, String> unigramTagger,
                                       boolean retainNER) {

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      PrintWriter pw = new PrintWriter(new PrintStream(new FileOutputStream(new File(treeFile + ".fixed")),false,"UTF-8"));

      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        traverseAndFix(t, pretermLabel, unigramTagger, retainNER);
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

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTIONS] treebank-file%n",
                            MultiWordPreprocessor.class.getName()));
    sb.append("Options:").append(nl);
    sb.append("   -help: Print this message").append(nl);
    sb.append("   -ner: Retain NER information in tree constituents").append(nl);
    return sb.toString();
  }

  private static Map<String, Integer> argOptionDefs;
  static {
    argOptionDefs = Generics.newHashMap();
    argOptionDefs.put("help", 0);
    argOptionDefs.put("ner", 0);
  }

  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, argOptionDefs);
    if(!options.containsKey("") || options.containsKey("help")) {
      System.err.println(usage());
      return;
    }

    boolean retainNER = PropertiesUtils.getBool(options, "ner", false);

    final File treeFile = new File(options.getProperty(""));
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
      resolveDummyTags(treeFile, pretermLabel, unigramTagger, retainNER);

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
