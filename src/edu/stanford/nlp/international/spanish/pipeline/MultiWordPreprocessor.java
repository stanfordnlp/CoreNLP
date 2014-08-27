package edu.stanford.nlp.international.spanish.pipeline;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
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

  private static int nMissingPOS;
  private static int nMissingPhrasal;

  private static int nFixedPOS;
  private static int nFixedPhrasal;

  /**
   * If a multiword token has a part-of-speech tag matching a key of
   * this map, the constituent heading the split expression should
   * have a label with the value corresponding to said key.
   *
   * e.g., since `(rg, grup.adv)` is in this map, we will eventually
   * convert
   *
   *     (rg cerca_de)
   *
   * to
   *
   *     (grup.adv (rg cerca) (sp000 de))
   */
  private static Map<String, String> phrasalCategoryMap = new HashMap<String, String>() {{
      put("ao0000", "grup.a");
      put("aq0000", "grup.a");
      put("dn0000", "spec");
      put("dt0000", "spec");
      put("rg", "grup.adv");
      put("rn", "grup.adv"); // no sólo
      put("vmg0000", "grup.verb");
      put("vmic000", "grup.verb");
      put("vmii000", "grup.verb");
      put("vmif000", "grup.verb");
      put("vmip000", "grup.verb");
      put("vmis000", "grup.verb");
      put("vmn0000", "grup.verb");
      put("vmp0000", "grup.verb");
      put("vmsi000", "grup.verb");
      put("vmsp000", "grup.verb");
      put("zm", "grup.nom");

      // New groups (not from AnCora specification)
      put("cc", "grup.cc");
      put("cs", "grup.cs");
      put("i", "grup.i");
      put("pr000000", "grup.pron");
      put("pt000000", "grup.pron");
      put("px000000", "grup.pron");
      put("sp000", "grup.prep");
      put("w", "grup.w");
      put("z", "grup.z");
      put("z0", "grup.z");
      put("zp", "grup.z");
    }};

  private static class ManualUWModel {

    private static Map<String, String> posMap = new HashMap<String, String>() {{
        // i.e., "metros cúbicos"
        put("cúbico", "aq0000");
        put("cúbicos", "aq0000");
        put("diagonal", "aq0000");
        put("diestro", "aq0000");
        put("llevados", "aq0000"); // llevados a cabo
        put("llevadas", "aq0000"); // llevadas a cabo
        put("menudo", "aq0000");
        put("obstante", "aq0000");
        put("rapadas", "aq0000"); // cabezas rapadas
        put("rasa", "aq0000");
        put("súbito", "aq0000");

        put("tuya", "px000000");

        // foreign words
        put("alter", "nc0s000");
        put("ego", "nc0s000");
        put("Jet", "nc0s000");
        put("lag", "nc0s000");
        put("line", "nc0s000");
        put("lord", "nc0s000");
        put("model", "nc0s000");
        put("mortem", "nc0s000"); // post-mortem
        put("pater", "nc0s000"); // pater familias
        put("pipe", "nc0s000");
        put("play", "nc0s000");
        put("pollastre", "nc0s000");
        put("post", "nc0s000");
        put("power", "nc0s000");
        put("priori", "nc0s000");
        put("rock", "nc0s000");
        put("roll", "nc0s000");
        put("salubritatis", "nc0s000");
        put("savoir", "nc0s000");
        put("service", "nc0s000");
        put("status", "nc0s000");
        put("stem", "nc0s000");
        put("street", "nc0s000");
        put("task", "nc0s000");
        put("trio", "nc0s000");
        put("zigzag", "nc0s000");

        // foreign words (invariable)
        put("mass", "nc0n000");
        put("media", "nc0n000");

        // foreign words (plural)
        put("options", "nc0p000");

        // compound words, other invariables
        put("regañadientes", "nc0n000");
        put("sabiendas", "nc0n000"); // a sabiendas (de)

        // common gender
        put("virgen", "nc0s000");

        put("merced", "ncfs000");
        put("miel", "ncfs000");
        put("torera", "ncfs000");
        put("ultranza", "ncfs000");
        put("vísperas", "ncfs000");

        put("acecho", "ncms000");
        put("alzamiento", "ncms000");
        put("bordo", "ncms000");
        put("cápita", "ncms000");
        put("ciento", "ncms000");
        put("cuño", "ncms000");
        put("pairo", "ncms000");
        put("pese", "ncms000"); // pese a
        put("pique", "ncms000");
        put("pos", "ncms000");
        put("postre", "ncms000");
        put("ralentí", "ncms000");
        put("ras", "ncms000");
        put("rebato", "ncms000");
        put("torno", "ncms000");
        put("través", "ncms000");

        put("creces", "ncfp000");
        put("cuestas", "ncfp000");
        put("oídas", "ncfp000");
        put("tientas", "ncfp000");
        put("trizas", "ncfp000");
        put("veras", "ncfp000");

        put("abuelos", "ncmp000");
        put("ambages", "ncmp000");
        put("modos", "ncmp000");
        put("pedazos", "ncmp000");

        put("amén", "rg"); // amén de

        put("formaba", "vmii000");
        put("perece", "vmip000");
        put("tardar", "vmn0000");

        put("seiscientas", "z0");
        put("trescientas", "z0");
      }};

    private static int nUnknownWordTypes = posMap.size();

    private static final Pattern digit = Pattern.compile("\\d+");
    private static final Pattern participle = Pattern.compile("[ai]d[oa]$");

    /**
     * Match phrases for which unknown words should be assumed to be
     * common nouns
     *
     * - a trancas y barrancas
     * - en vez de, en pos de
     * - sin embargo
     * - merced a
     * - pese a que
     */
    private static final Pattern commonPattern =
      Pattern.compile("^al? |^en .+ de$|sin | al?$| que$",
                      Pattern.CASE_INSENSITIVE);

    public static String getTag(String word, String containingPhrase) {
      // Exact matches
      if (word.equals("%"))
        return "ft";
      else if (word.equals("+"))
        return "fz";
      else if (word.equals("&") || word.equals("@"))
        return "f0";

      if(digit.matcher(word).find())
        return "z0";
      else if (posMap.containsKey(word))
        return posMap.get(word);

      // Fallbacks
      if (participle.matcher(word).find())
        return "aq0000";

      // One last hint: is the phrase one which we have designated to
      // contain mostly common nouns?
      if (commonPattern.matcher(word).matches())
        return "ncms000";

      // Now make an educated guess.
      System.err.println("No POS tag for " + word);
      return "np00000";
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
                                    Tree parent,
                                    TwoDimensionalCounter<String, String> pretermLabel,
                                    TwoDimensionalCounter<String, String> unigramTagger,
                                    boolean retainNER) {
    if(t.isPreTerminal()) {
      if(t.value().equals(SpanishTreeNormalizer.MW_TAG)) {
        nMissingPOS++;

        String pos = inferPOS(t, parent, unigramTagger);
        if (pos != null) {
          t.setValue(pos);
          nFixedPOS++;
        }
      }

      return;
    }

    for(Tree kid : t.children())
      traverseAndFix(kid, t, pretermLabel, unigramTagger, retainNER);

    // Post-order visit
    //
    // TODO merge unnecessarily deep trees (maybe the job for a separate
    // tree normalizer?)
    if(t.value().startsWith(SpanishTreeNormalizer.MW_PHRASE_TAG)) {
      nMissingPhrasal++;

      String phrasalCat = inferPhrasalCategory(t, pretermLabel, retainNER);
      if (phrasalCat != null) {
        t.setValue(phrasalCat);
        nFixedPhrasal++;
      }
    }
  }

  /**
   * Attempt to infer the part of speech of the given preterminal node, which
   * was created during the expansion of a multi-word token.
   */
  private static String inferPOS(Tree t, Tree parent,
                                 TwoDimensionalCounter<String, String> unigramTagger) {
    String word = t.firstChild().value();
    if (unigramTagger.firstKeySet().contains(word))
      return Counters.argmax(unigramTagger.getCounter(word));

    if (parent == null)
      return null;

    List<Label> phraseYield = parent.yield();
    StringBuilder containingPhrase = new StringBuilder();
    for (Label l : phraseYield)
      containingPhrase.append(l).append(" ");

    String containingPhraseStr =
      containingPhrase.toString().substring(0, containingPhrase.length() - 1);
    return ManualUWModel.getTag(word, containingPhraseStr);
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

    if (phrasalCategoryMap.containsKey(originalPos)) {
      return phrasalCategoryMap.get(originalPos);
    } else if (originalPos.length() > 0 && originalPos.charAt(0) == 'n') {
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
                                       boolean retainNER, TreeNormalizer tn) {
    TreeFactory tf = new LabeledScoredTreeFactory();

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      PrintWriter pw = new PrintWriter(new PrintStream(new FileOutputStream(new File(treeFile + ".fixed")),false,"UTF-8"));

      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        traverseAndFix(t, null, pretermLabel, unigramTagger, retainNER);

        if (tn != null)
          t = tn.normalizeWholeTree(t, tf);

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
    sb.append("   -ner: Retain NER information in tree constituents (pre-pre-terminal nodes)").append(nl);
    sb.append("   -normalize {true, false}: Run the Spanish tree normalizer (non-aggressive) on the output of the main routine (true by default)").append(nl);
    return sb.toString();
  }

  private static Map<String, Integer> argOptionDefs;
  static {
    argOptionDefs = Generics.newHashMap();
    argOptionDefs.put("help", 0);
    argOptionDefs.put("ner", 0);
    argOptionDefs.put("normalize", 1);
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
    boolean normalize = PropertiesUtils.getBool(options, "normalize", true);

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
      resolveDummyTags(treeFile, pretermLabel, unigramTagger, retainNER,
                       normalize ? new SpanishTreeNormalizer(true, false, false) : null);

      System.out.println("#Unknown Word Types: " + ManualUWModel.nUnknownWordTypes);
      System.out.println(String.format("#Missing POS: %d (fixed: %d, %.2f%%)",
                                       nMissingPOS, nFixedPOS,
                                       (double) nFixedPOS / nMissingPOS * 100));
      System.out.println(String.format("#Missing Phrasal: %d (fixed: %d, %.2f%%)",
                                       nMissingPhrasal, nFixedPhrasal,
                                       (double) nFixedPhrasal / nMissingPhrasal * 100));

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
