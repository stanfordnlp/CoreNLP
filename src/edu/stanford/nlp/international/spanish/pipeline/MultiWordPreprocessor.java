package edu.stanford.nlp.international.spanish.pipeline;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.spanish.SpanishVerbStripper;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
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
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
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
public final class MultiWordPreprocessor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MultiWordPreprocessor.class);

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
  private static Map<String, String> phrasalCategoryMap = new HashMap<>();
  static {
    phrasalCategoryMap.put("ao0000", "grup.a");
    phrasalCategoryMap.put("aq0000", "grup.a");
    phrasalCategoryMap.put("aqo000", "grup.a");
    phrasalCategoryMap.put("da0000", "spec");
    phrasalCategoryMap.put("di0000", "sn");
    phrasalCategoryMap.put("dn0000", "spec");
    phrasalCategoryMap.put("dt0000", "spec");
    phrasalCategoryMap.put("i", "interjeccio");
    phrasalCategoryMap.put("i00", "interjeccio");
    phrasalCategoryMap.put("rg", "grup.adv");
    phrasalCategoryMap.put("rn", "grup.adv"); // no sólo
    phrasalCategoryMap.put("vaip000", "grup.verb");
    phrasalCategoryMap.put("vmg0000", "grup.verb");
    phrasalCategoryMap.put("vmic000", "grup.verb");
    phrasalCategoryMap.put("vmii000", "grup.verb");
    phrasalCategoryMap.put("vmif000", "grup.verb");
    phrasalCategoryMap.put("vmip000", "grup.verb");
    phrasalCategoryMap.put("vmis000", "grup.verb");
    phrasalCategoryMap.put("vmm0000", "grup.verb");
    phrasalCategoryMap.put("vmn0000", "grup.verb");
    phrasalCategoryMap.put("vmp0000", "grup.verb");
    phrasalCategoryMap.put("vmsi000", "grup.verb");
    phrasalCategoryMap.put("vmsp000", "grup.verb");
    phrasalCategoryMap.put("zm", "grup.nom");

    // New groups (not from AnCora specification)
    phrasalCategoryMap.put("cc", "grup.cc");
    phrasalCategoryMap.put("cs", "grup.cs");
    phrasalCategoryMap.put("pn000000", "grup.nom");
    phrasalCategoryMap.put("pi000000", "grup.pron");
    phrasalCategoryMap.put("pr000000", "grup.pron");
    phrasalCategoryMap.put("pt000000", "grup.pron");
    phrasalCategoryMap.put("px000000", "grup.pron");
    phrasalCategoryMap.put("sp000", "grup.prep");
    phrasalCategoryMap.put("w", "grup.w");
    phrasalCategoryMap.put("z", "grup.z");
    phrasalCategoryMap.put("z0", "grup.z");
    phrasalCategoryMap.put("zp", "grup.z");
    phrasalCategoryMap.put("zu", "grup.z");
  }

  private static class ManualUWModel {

    private static Map<String, String> posMap = new HashMap<>();
    static {
      // i.e., "metros cúbicos"
      posMap.put("cúbico", "aq0000");
      posMap.put("cúbicos", "aq0000");
      posMap.put("diagonal", "aq0000");
      posMap.put("diestro", "aq0000");
      posMap.put("llevados", "aq0000"); // llevados a cabo
      posMap.put("llevadas", "aq0000"); // llevadas a cabo
      posMap.put("menudo", "aq0000");
      posMap.put("obstante", "aq0000");
      posMap.put("rapadas", "aq0000"); // cabezas rapadas
      posMap.put("rasa", "aq0000");
      posMap.put("súbito", "aq0000");
      posMap.put("temática", "aq0000");

      posMap.put("tuya", "px000000");

      // foreign words
      posMap.put("alter", "nc0s000");
      posMap.put("ego", "nc0s000");
      posMap.put("Jet", "nc0s000");
      posMap.put("lag", "nc0s000");
      posMap.put("line", "nc0s000");
      posMap.put("lord", "nc0s000");
      posMap.put("model", "nc0s000");
      posMap.put("mortem", "nc0s000"); // post-mortem
      posMap.put("pater", "nc0s000"); // pater familias
      posMap.put("pipe", "nc0s000");
      posMap.put("play", "nc0s000");
      posMap.put("pollastre", "nc0s000");
      posMap.put("post", "nc0s000");
      posMap.put("power", "nc0s000");
      posMap.put("priori", "nc0s000");
      posMap.put("rock", "nc0s000");
      posMap.put("roll", "nc0s000");
      posMap.put("salubritatis", "nc0s000");
      posMap.put("savoir", "nc0s000");
      posMap.put("service", "nc0s000");
      posMap.put("status", "nc0s000");
      posMap.put("stem", "nc0s000");
      posMap.put("street", "nc0s000");
      posMap.put("task", "nc0s000");
      posMap.put("trio", "nc0s000");
      posMap.put("zigzag", "nc0s000");

      // foreign words (invariable)
      posMap.put("mass", "nc0n000");
      posMap.put("media", "nc0n000");

      // foreign words (plural)
      posMap.put("options", "nc0p000");

      // compound words, other invariables
      posMap.put("regañadientes", "nc0n000");
      posMap.put("sabiendas", "nc0n000"); // a sabiendas (de)

      // common gender
      posMap.put("virgen", "nc0s000");

      posMap.put("merced", "ncfs000");
      posMap.put("miel", "ncfs000");
      posMap.put("torera", "ncfs000");
      posMap.put("ultranza", "ncfs000");
      posMap.put("vísperas", "ncfs000");

      posMap.put("acecho", "ncms000");
      posMap.put("alzamiento", "ncms000");
      posMap.put("bordo", "ncms000");
      posMap.put("cápita", "ncms000");
      posMap.put("ciento", "ncms000");
      posMap.put("cuño", "ncms000");
      posMap.put("pairo", "ncms000");
      posMap.put("pese", "ncms000"); // pese a
      posMap.put("pique", "ncms000");
      posMap.put("pos", "ncms000");
      posMap.put("postre", "ncms000");
      posMap.put("pro", "ncms000");
      posMap.put("ralentí", "ncms000");
      posMap.put("ras", "ncms000");
      posMap.put("rebato", "ncms000");
      posMap.put("torno", "ncms000");
      posMap.put("través", "ncms000");

      posMap.put("creces", "ncfp000");
      posMap.put("cuestas", "ncfp000");
      posMap.put("oídas", "ncfp000");
      posMap.put("tientas", "ncfp000");
      posMap.put("trizas", "ncfp000");
      posMap.put("veras", "ncfp000");

      posMap.put("abuelos", "ncmp000");
      posMap.put("ambages", "ncmp000");
      posMap.put("modos", "ncmp000");
      posMap.put("pedazos", "ncmp000");

      posMap.put("A", "sps00");

      posMap.put("amén", "rg"); // amén de

      posMap.put("Bailando", "vmg0000");
      posMap.put("Soñando", "vmg0000");
      posMap.put("Teniendo", "vmg0000");
      posMap.put("echaremos", "vmif000");
      posMap.put("formaba", "vmii000");
      posMap.put("Formabas", "vmii000");
      posMap.put("Forman", "vmip000");
      posMap.put("perece", "vmip000");
      posMap.put("PONE", "vmip000");
      posMap.put("suicídate", "vmm0000");
      posMap.put("tardar", "vmn0000");

      posMap.put("seiscientas", "z0");
      posMap.put("trescientas", "z0");

      posMap.put("cc", "zu");
      posMap.put("km", "zu");
      posMap.put("kms", "zu");
    }

    private static int nUnknownWordTypes = posMap.size();

    private static final Pattern digit = Pattern.compile("\\d+");
    private static final Pattern participle = Pattern.compile("[ai]d[oa]$");

    /**
     * Names which would be mistakenly marked as function words by
     * unigram tagger (and which never appear as function words in
     * multi-word tokens)
     */
    private static final Set<String> actuallyNames = new HashSet<>(Arrays.asList(
            "Avenida",
            "Contra",
            "Gracias", // interjection
            "in", // preposition; only appears in corpus as "in extremis" (preposition)
            "Mercado",
            "Jesús", // interjection
            "Salvo",
            "Van" // verb
    ));

    // Name-looking word that isn't "Al"
    private static final Pattern otherNamePattern = Pattern.compile("\\b(Al\\w+|A[^l]\\w*|[B-Z]\\w+)");
    // Name-looking word that isn't "A"
    private static final Pattern otherNamePattern2 = Pattern.compile("\\b(A\\w+|[B-Z]\\w+)");

    // Determiners which may also appear as pronouns
    private static final Pattern pPronounDeterminers = Pattern.compile("(tod|otr|un)[oa]s?");

    public static String getOverrideTag(String word, String containingPhrase) {
      if (containingPhrase == null)
        return null;

      if (word.equalsIgnoreCase("este") && !containingPhrase.startsWith(word))
        return "np00000";
      else if (word.equals("contra")
        && (containingPhrase.startsWith("en contra") || containingPhrase.startsWith("En contra")))
        return "nc0s000";
      else if (word.equals("total") && containingPhrase.startsWith("ese"))
        return "nc0s000";
      else if (word.equals("DEL"))
        // Uses of "Del" in corpus are proper nouns, but uses of "DEL" are
        // prepositions.. convenient for our purposes
        return "sp000";
      else if (word.equals("sí") && containingPhrase.contains("por sí")
        || containingPhrase.contains("fuera de sí"))
        return "pp000000";
      else if (pPronounDeterminers.matcher(word).matches() && containingPhrase.endsWith(word))
        // Determiners tailing a phrase are pronouns: "sobre todo," "al otro", etc.
        return "pi000000";
      else if (word.equals("cuando") && containingPhrase.endsWith(word))
        return "pi000000";
      else if ((word.equalsIgnoreCase("contra") && containingPhrase.endsWith(word)))
        return "nc0s000";
      else if (word.equals("salvo") && containingPhrase.endsWith("salvo"))
        return "aq0000";
      else if (word.equals("mira") && containingPhrase.endsWith(word))
        return "nc0s000";
      else if (word.equals("pro") && containingPhrase.startsWith("en pro"))
        return "nc0s000";
      else if (word.equals("espera") && containingPhrase.endsWith("espera de"))
        return "nc0s000";
      else if (word.equals("Paso") && containingPhrase.equals("El Paso"))
        return "np00000";
      else if (word.equals("medio") && (containingPhrase.endsWith("medio de") || containingPhrase.endsWith("ambiente")
        || containingPhrase.endsWith("por medio") || containingPhrase.contains("por medio")
        || containingPhrase.endsWith("medio")))
        return "nc0s000";
      else if (word.equals("Medio") && containingPhrase.contains("Ambiente"))
        return "nc0s000";
      else if (word.equals("Medio") && containingPhrase.equals("Oriente Medio"))
        return "aq0000";
      else if (word.equals("media") && containingPhrase.equals("mass media"))
        return "nc0n000";
      else if (word.equals("cuenta")) // tomar en cuenta, darse cuenta de, ...
        return "nc0s000";
      else if (word.equals("h") && containingPhrase.startsWith("km"))
        return "zu";
      else if (word.equals("A") && (containingPhrase.contains("-") || containingPhrase.contains(",")
        || otherNamePattern2.matcher(containingPhrase).find() || containingPhrase.equals("terminal A")))
        return "np00000";
      else if (word.equals("forma") && containingPhrase.startsWith("forma parte"))
        return "vmip000";
      else if (word.equals("Sin") && containingPhrase.contains("Jaime"))
        return "np00000";
      else if (word.equals("di") && containingPhrase.contains("di cuenta"))
        return "vmis000";
      else if (word.equals("demos") && containingPhrase.contains("demos cuenta"))
        return "vmsp000";
      else if ((word.equals("van") || word.equals("den")) && containingPhrase.contains("van den"))
        return "np00000";

      if (word.equals("Al")) {
        // "Al" is sometimes a part of name phrases: Arabic names, Al Gore, etc.
        // Mark it a noun if its containing phrase has some other capitalized word
        if (otherNamePattern.matcher(containingPhrase).find())
          return "np00000";
        else
          return "sp000";
      }

      if (actuallyNames.contains(word))
        return "np00000";

      if (word.equals("sino") && containingPhrase.endsWith(word))
        return "nc0s000";
      else if (word.equals("mañana") || word.equals("paso") || word.equals("monta") || word.equals("deriva")
        || word.equals("visto"))
        return "nc0s000";
      else if (word.equals("frente") && containingPhrase.startsWith("al frente"))
        return "nc0s000";

      return null;
    }

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
      //log.info("No POS tag for " + word);
      return "np00000";
    }
  }

  /**
   * Source training data for a unigram tagger from the given tree.
   */
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
      traverseAndFix(kid, t, unigramTagger, retainNER);

    // Post-order visit
    if(t.value().startsWith(SpanishTreeNormalizer.MW_PHRASE_TAG)) {
      nMissingPhrasal++;

      String phrasalCat = inferPhrasalCategory(t, retainNER);
      if (phrasalCat != null) {
        t.setValue(phrasalCat);
        nFixedPhrasal++;
      }
    }
  }

  /**
   * Get a string representation of the immediate phrase which contains the given node.
   */
  private static String getContainingPhrase(Tree t, Tree parent) {
    if (parent == null)
      return null;

    List<Label> phraseYield = parent.yield();
    StringBuilder containingPhrase = new StringBuilder();
    for (Label l : phraseYield)
      containingPhrase.append(l.value()).append(" ");

    return containingPhrase.toString().substring(0, containingPhrase.length() - 1);
  }

  private static final SpanishVerbStripper verbStripper = SpanishVerbStripper.getInstance();

  /**
   * Attempt to infer the part of speech of the given preterminal node, which
   * was created during the expansion of a multi-word token.
   */
  private static String inferPOS(Tree t, Tree parent,
                                 TwoDimensionalCounter<String, String> unigramTagger) {
    String word = t.firstChild().value();
    String containingPhraseStr = getContainingPhrase(t, parent);

    // Overrides: let the manual POS model handle a few special cases first
    String overrideTag = ManualUWModel.getOverrideTag(word, containingPhraseStr);
    if (overrideTag != null)
      return overrideTag;

    Set<String> unigramTaggerKeys = unigramTagger.firstKeySet();

    // Try treating this word as a verb and stripping any clitic
    // pronouns. If the stripped version exists in the unigram
    // tagger, then stick with the verb hypothesis
    SpanishVerbStripper.StrippedVerb strippedVerb = verbStripper.separatePronouns(word);
    if (strippedVerb != null && unigramTaggerKeys.contains(strippedVerb.getStem())) {
      String pos = Counters.argmax(unigramTagger.getCounter(strippedVerb.getStem()));
      if (pos.startsWith("v"))
        return pos;
    }

    if (unigramTagger.firstKeySet().contains(word))
      return Counters.argmax(unigramTagger.getCounter(word), new POSTieBreaker());

    return ManualUWModel.getTag(word, containingPhraseStr);
  }

  /**
   * Resolves "ties" between candidate part-of-speech tags encountered by the unigram tagger.
   */
  private static class POSTieBreaker implements Comparator<String> {
    @Override
    public int compare(String o1, String o2) {
      boolean firstIsNoun = o1.startsWith("n");
      boolean secondIsNoun = o2.startsWith("n");

      // Prefer nouns over everything
      if (firstIsNoun && !secondIsNoun)
        return -1;
      else if (secondIsNoun && !firstIsNoun)
        return 1;

      // No other policies at the moment
      return 0;
    }
  }

  /**
   * Attempt to infer the phrasal category of the given node, which
   * heads words which were expanded from a multi-word token.
   */
  private static String inferPhrasalCategory(Tree t, boolean retainNER) {
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
    log.info("No phrasal cat for: " + posSequence + " (original POS of MWE: " + originalPos + ")");

    // Give up.
    return null;
  }

  private static void resolveDummyTags(File treeFile,
                                       TwoDimensionalCounter<String, String> unigramTagger,
                                       boolean retainNER, TreeNormalizer tn) {
    TreeFactory tf = new LabeledScoredTreeFactory();
    MultiWordTreeExpander expander = new MultiWordTreeExpander();

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      PrintWriter pw = new PrintWriter(new PrintStream(new FileOutputStream(new File(treeFile + ".fixed")),false,"UTF-8"));

      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        traverseAndFix(t, null, unigramTagger, retainNER);

        // Now "decompress" further the expanded trees formed by
        // multiword token splitting
        t = expander.expandPhrases(t, tn, tf);

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
      log.info(usage());
      return;
    }

    boolean retainNER = PropertiesUtils.getBool(options, "ner", false);
    boolean normalize = PropertiesUtils.getBool(options, "normalize", true);

    final File treeFile = new File(options.getProperty(""));
    TwoDimensionalCounter<String,String> labelTerm =
            new TwoDimensionalCounter<>();
    TwoDimensionalCounter<String,String> termLabel =
            new TwoDimensionalCounter<>();
    TwoDimensionalCounter<String,String> labelPreterm =
            new TwoDimensionalCounter<>();
    TwoDimensionalCounter<String,String> pretermLabel =
            new TwoDimensionalCounter<>();

    TwoDimensionalCounter<String,String> unigramTagger =
            new TwoDimensionalCounter<>();

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new SpanishTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      for(Tree t; (t = tr.readTree()) != null;) {
        updateTagger(unigramTagger, t);
      }
      tr.close(); //Closes the underlying reader

      System.out.println("Resolving DUMMY tags");
      resolveDummyTags(treeFile, unigramTagger, retainNER,
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
