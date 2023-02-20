package edu.stanford.nlp.parser.lexparser;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.spanish.SpanishHeadFinder;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeReaderFactory;
import edu.stanford.nlp.trees.international.spanish.SpanishTreebankLanguagePack;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.util.List;

/**
 * TreebankLangParserParams for the AnCora corpus. This package assumes
 * that the provided trees are in PTB format, read from the initial
 * AnCora XML with
 * {@link edu.stanford.nlp.trees.international.spanish.SpanishXMLTreeReader}
 * and preprocessed with
 * {@link edu.stanford.nlp.international.spanish.pipeline.MultiWordPreprocessor}.
 *
 * @author Jon Gauthier
 *
 */
public class SpanishTreebankParserParams extends TregexPoweredTreebankParserParams  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SpanishTreebankParserParams.class);

  private static final long serialVersionUID = -8734165273482119424L;

  private final StringBuilder optionsString;

  private HeadFinder headFinder;

  public SpanishTreebankParserParams() {
    super(new SpanishTreebankLanguagePack());

    setInputEncoding(treebankLanguagePack().getEncoding());
    setHeadFinder(new SpanishHeadFinder());

    optionsString = new StringBuilder();
    optionsString.append(getClass().getSimpleName() + "\n");

    buildAnnotations();
  }

  private static final String PODER_FORM =
    "(?iu)^(?:pued(?:o|[ea][sn]?)|" +
      "pod(?:e[dr]|ido|[ea]mos|[éá]is|r(?:é(?:is)?|á[sn]?|emos)|r?ía(?:s|mos|is|n)?)|" +
      "pud(?:[eo]|i(?:ste(?:is)?|mos|eron|er[ea](?:[sn]|is)?|ér[ea]mos|endo)))$";

  /**
   * Forms of hacer which may lead time expressions
   */
  private static final String HACER_TIME_FORM = "(?iu)^(?:hac(?:er|ía))$";

  @SuppressWarnings("unchecked")
  private void buildAnnotations() {
    // +.25 F1
    annotations.put("-markInf", new Pair("/^(S|grup\\.verb|infinitiu|gerundi)/ < @infinitiu",
                                         new SimpleStringFunction("-infinitive")));
    annotations.put("-markGer", new Pair("/^(S|grup\\.verb|infinitiu|gerundi)/ < @gerundi",
                                         new SimpleStringFunction("-gerund")));

    // +.04 F1
    annotations.put("-markRelative", new Pair("@S <, @relatiu",
                                              new SimpleStringFunction("-relative")));

    // Negative F1; unused in default config
    annotations.put("-markPPHeads", new Pair("@sp",
                                             new AnnotateHeadFunction(headFinder)));

    // +.1 F1
    annotations.put("-markComo", new Pair("@cs < /(?iu)^como$/",
                                          new SimpleStringFunction("[como]")));
    annotations.put("-markSpecHeads", new Pair("@spec", new AnnotateHeadFunction(headFinder)));

    // +.32 F1
    annotations.put("-markSingleChildNPs", new Pair("/^(sn|grup\\.nom)/ <: __",
                                                    new SimpleStringFunction("-singleChild")));

    // +.05 F1
    annotations.put("-markPPFriendlyVerbs", new Pair("/^v/ > /^grup\\.prep/",
                                                     new SimpleStringFunction("-PPFriendly")));

    // +.46 F1
    annotations.put("-markConjTypes", new Pair("@conj <: /^c[cs]/=c", new MarkConjTypeFunction()));

    // +.09 F1
    annotations.put("-markPronounNPs", new Pair("/^(sn|grup\\.nom)/ <<: /^p[0p]/",
                                                new SimpleStringFunction("-pronoun")));

    // +1.39 F1
    annotations.put("-markParticipleAdjs", new Pair(
      "@aq0000 < /(?iu)([aeií]d|puest|biert|vist|(ben|mal)dit|[fh]ech|scrit|muert|[sv]uelt|[rl]ect|"
        + "frit|^(rot|dich|impres|desnud|sujet|exent))[oa]s?$/",
      new SimpleStringFunction("-part")));

    // Negative F1; unused in default config
    annotations.put("-markSentenceInitialClauses", new Pair("@S !, __",
                                                            new SimpleStringFunction("-init")));

    // Insignificant F1; unused in default config
    annotations.put("-markPoder", new Pair(
      String.format("/^(infinitiu|gerundi|grup\\.verb)/ <<: /%s/", PODER_FORM),
      new SimpleStringFunction("-poder")));

    // +.29 F1
    annotations.put("-markBaseNPs", new Pair("/^grup\\.nom/ !< (__ < (__ < __))",
                                             new SimpleStringFunction("-base")));

    // +.17 F1
    annotations.put("-markVerbless", new Pair("@S|sentence !<< /^(v|participi$)/",
                                              new SimpleStringFunction("-verbless")));

    // +.23 F1
    annotations.put("-markDominatesVerb", new Pair("__ << (/^(v|participi$)/ < __)",
                                                   new SimpleStringFunction("-dominatesV")));

    // Negative F1 -- not used by default
    annotations.put("-markNonRecSPs", new Pair("@sp !<< @sp", new SimpleStringFunction("-nonRec")));

    // In right-recursive verb phrases, mark the prefix of the first verb on its tag.
    // This annotation tries to capture the fact that only a few roots are ever really part of
    // these constructions: poder, deber, ir, etc.
    annotations.put("-markRightRecVPPrefixes",
                    new Pair("/^v/ $+ @infinitiu|gerundi >, /^(grup.verb|infinitiu|gerundi)/",
                             new MarkPrefixFunction(3)));


    // Negative F1 -- not used by default
    annotations.put("-markParentheticalNPs", new Pair("@sn <<, fpa <<` fpt",
                                                      new SimpleStringFunction("-paren")));
    annotations.put("-markNumericNPs", new Pair("@sn << (/^z/ < __) !<< @sn",
                                                new SimpleStringFunction("-num")));

    // Negative F1 -- not used by default
    annotations.put("-markCoordinatedNPs", new Pair(
      "@sn <, (/^(sn|grup\\.nom)/ $+ (@conj < /^(cc|grup\\.cc)/ $+ /^(sn|grup\\.nom)/=last))" +
        "<` =last",
      new SimpleStringFunction("-coord")));

    annotations.put("-markHacerTime", new Pair(
      String.format("/^vm/ < /%s/ $+ /^d/", HACER_TIME_FORM),
      new SimpleStringFunction("-hacerTime")));

    compileAnnotations(headFinder);
  }

  /**
   * Mark `conj` constituents with their `cc` / `cs` child.
   */
  private static class MarkConjTypeFunction implements SerializableFunction<TregexMatcher, String> {

    private static final long serialVersionUID = 403406212736445856L;

    public String apply(TregexMatcher m) {
      String type = m.getNode("c").value().toUpperCase();
      return "-conj" + type;
    }

  }

  /**
   * Mark a tag with a prefix of its constituent word.
   */
  private static class MarkPrefixFunction implements SerializableFunction<TregexMatcher, String> {

    private static final long serialVersionUID = -3275700521562916350L;

    private static final int DEFAULT_PREFIX_LENGTH = 3;
    private final int prefixLength;

    public MarkPrefixFunction() {
      this(DEFAULT_PREFIX_LENGTH);
    }

    public MarkPrefixFunction(int prefixLength) {
      this.prefixLength = prefixLength;
    }

    public String apply(TregexMatcher m) {
      Tree tagNode = m.getMatch();

      String yield = tagNode.firstChild().value();
      String prefix = yield.substring(0, Math.min(yield.length(), prefixLength));
      return "[p," + prefix + ']';
    }

  }

  /**
   * Features which should be enabled by default.
   *
   * @see #buildAnnotations()
   */
  @Override
  protected String[] baselineAnnotationFeatures() {
    return new String[] {
      // verb phrase annotations
      "-markInf", "-markGer", "-markRightRecVPPrefixes",

      // noun phrase annotations
      "-markSingleChildNPs", "-markBaseNPs", "-markPronounNPs",
      // "-markCoordinatedNPs",
      // "-markParentheticalNPs",
      // "-markNumericNPs",

      // prepositional phrase annotations
      // "-markNonRecSPs", negative F1!
      // "-markPPHeads", negative F1!

      // clause annotations
      "-markRelative", /* "-markSentenceInitialClauses", */

      // lexical / word- or tag-level annotations
      "-markComo", "-markSpecHeads", "-markPPFriendlyVerbs", "-markParticipleAdjs",
      "-markHacerTime",
      /* "-markPoder", */

      // conjunction annotations
      "-markConjTypes",

      // sentence annotations
      "-markVerbless", "-markDominatesVerb",
    };
  }

  @Override
  public HeadFinder headFinder() {
    return headFinder;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    // Not supported
    return null;
  }

  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    // Override unknown word model
    if (op.lexOptions.uwModelTrainer == null)
      op.lexOptions.uwModelTrainer =
        "edu.stanford.nlp.parser.lexparser.SpanishUnknownWordModelTrainer";

    return new BaseLexicon(op, wordIndex, tagIndex);
  }

  @Override
  public String[] sisterSplitters() {
    return new String[0];
  }

  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public AbstractCollinizer collinizerEvalb() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public DiskTreebank diskTreebank() {
   return new DiskTreebank(treeReaderFactory(), inputEncoding);
  }

  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), inputEncoding);
  }

  /**
   * Set language-specific options according to flags. This routine should process the option starting in args[i] (which
   * might potentially be several arguments long if it takes arguments). It should return the index after the last index
   * it consumed in processing.  In particular, if it cannot process the current option, the return value should be i.
   * <br>
   * Generic options are processed separately by {@link edu.stanford.nlp.parser.lexparser.Options#setOption(String[], int)}, and implementations of this
   * method do not have to worry about them. The Options class handles routing options. TreebankParserParams that extend
   * this class should call super when overriding this method.
   *
   * @param args Command-line arguments, still including leading dashes
   * @param i index in array to start processing from
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    if (args[i].equalsIgnoreCase("-headFinder") && (i + 1 < args.length)) {
      try {
        HeadFinder hf = (HeadFinder) Class.forName(args[i + 1]).newInstance();
        setHeadFinder(hf);

        optionsString.append("HeadFinder: " + args[i + 1] + "\n");
      } catch (ReflectiveOperationException|SecurityException e) {
        log.info(e);
        log.info(this.getClass().getName() + ": Could not load head finder " + args[i + 1]);
      }
      i += 2;
    }

    return i;
  }

  public TreeReaderFactory treeReaderFactory() {
    return new SpanishTreeReaderFactory();
  }

  public List<HasWord> defaultTestSentence() {
    String[] sent = {"Ésto", "es", "sólo", "una", "prueba", "."};
    return SentenceUtils.toWordList(sent);
  }

  @Override
  public void display() {
    log.info(optionsString.toString());
    super.display();
  }

  public void setHeadFinder(HeadFinder hf) {
    headFinder = hf;

    // Regenerate annotation patterns
    compileAnnotations(headFinder);
  }

}
