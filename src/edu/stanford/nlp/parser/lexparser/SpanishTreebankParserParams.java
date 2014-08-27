package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.SpanishHeadFinder;
import edu.stanford.nlp.trees.international.spanish.SpanishTreeReaderFactory;
import edu.stanford.nlp.trees.international.spanish.SpanishTreebankLanguagePack;
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
public class SpanishTreebankParserParams extends TregexPoweredTreebankParserParams {

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

    annotations.put("-markPPHeads", new Pair("@sp",
                                             new AnnotateHeadFunction(headFinder)));

    // +.1 F1
    annotations.put("-markComo", new Pair("@cs < /(?i)^como$/",
                                          new SimpleStringFunction("[como]")));
    annotations.put("-markSpecHeads", new Pair("@spec", new AnnotateHeadFunction(headFinder)));

    compileAnnotations(headFinder);
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
      "-markInf", "-markGer",

      // prepositional phrase annotations
      // "-markPPHeads", negative F1!

      // clause annotations
      "-markRelative",

      // lexical annotations
      "-markComo", "-markSpecHeads",
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
  public String[] sisterSplitters() {
    return new String[0];
  }

  @Override
  public TreeTransformer collinizer() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public TreeTransformer collinizerEvalb() {
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
   * <p/>
   * Generic options are processed separately by {@link edu.stanford.nlp.parser.lexparser.Options#setOption(String[], int)}, and implementations of this
   * method do not have to worry about them. The Options class handles routing options. TreebankParserParams that extend
   * this class should call super when overriding this method.
   *
   * @param args
   * @param i
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    if (args[i].equalsIgnoreCase("-headFinder") && (i + 1 < args.length)) {
      try {
        HeadFinder hf = (HeadFinder) Class.forName(args[i + 1]).newInstance();
        setHeadFinder(hf);

        optionsString.append("HeadFinder: " + args[i + 1] + "\n");
      } catch (Exception e) {
        System.err.println(e);
        System.err.println(this.getClass().getName() + ": Could not load head finder " + args[i + 1]);
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
    return Sentence.toWordList(sent);
  }

  @Override
  public void display() {
    System.err.println(optionsString.toString());
  }

  public void setHeadFinder(HeadFinder hf) {
    headFinder = hf;

    // Regenerate annotation patterns
    compileAnnotations(headFinder);
  }

}
