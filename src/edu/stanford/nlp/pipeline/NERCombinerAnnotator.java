package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.types.Tags;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.RuntimeInterruptedException;

import java.io.IOException;
import java.util.*;

/**
 * This class will add NER information to an
 * Annotation using a combination of NER models.
 * It assumes that the Annotation
 * already contains the tokenized words as a
 * List&lt;? extends CoreLabel&gt; or a
 * List&lt;List&lt;? extends CoreLabel&gt;&gt; under Annotation.WORDS_KEY
 * and adds NER information to each CoreLabel,
 * in the CoreLabel.NER_KEY field.  It uses
 * the NERClassifierCombiner class in the ie package.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu (modified it to work with the new NERClassifierCombiner)
 */
public class NERCombinerAnnotator extends SentenceAnnotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(NERCombinerAnnotator.class);

  private final NERClassifierCombiner ner;

  private final boolean VERBOSE;

  private final long maxTime;
  private final int nThreads;
  private final int maxSentenceLength;

  public NERCombinerAnnotator() throws IOException, ClassNotFoundException {
    this(true);
  }

  public NERCombinerAnnotator(boolean verbose)
    throws IOException, ClassNotFoundException
  {
    this(new NERClassifierCombiner(new Properties()), verbose);
  }

  public NERCombinerAnnotator(boolean verbose, String... classifiers)
    throws IOException, ClassNotFoundException
  {
    this(new NERClassifierCombiner(classifiers), verbose);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose) {
    this(ner, verbose, 1, 0, Integer.MAX_VALUE);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose, int nThreads, long maxTime) {
    this(ner, verbose, nThreads, maxTime, Integer.MAX_VALUE);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose, int nThreads, long maxTime, int maxSentenceLength) {
    VERBOSE = verbose;
    this.ner = ner;
    this.maxTime = maxTime;
    this.nThreads = nThreads;
    this.maxSentenceLength = maxSentenceLength;
  }

  public NERCombinerAnnotator(String name, Properties properties) {
    this(NERClassifierCombiner.createNERClassifierCombiner(name, properties), false,
         PropertiesUtils.getInt(properties, name + ".nthreads", PropertiesUtils.getInt(properties, "nthreads", 1)),
         PropertiesUtils.getLong(properties, name + ".maxtime", -1),
            PropertiesUtils.getInt(properties, name + ".maxlength", Integer.MAX_VALUE));
  }

  @Override
  protected int nThreads() {
    return nThreads;
  }

  @Override
  protected long maxTime() {
    return maxTime;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Adding NER Combiner annotation ... ");
    }

    super.annotate(annotation);
    this.ner.finalizeAnnotation(annotation);

    if (VERBOSE) {
      log.info("done.");
    }
  }

  @Override
  public void doOneSentence(Annotation annotation, CoreMap sentence) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> output; // only used if try assignment works.
    try {
      output = this.ner.classifySentenceWithGlobalInformation(tokens, annotation, sentence);
    } catch (RuntimeInterruptedException e) {
      // If we get interrupted, set the NER labels to the background
      // symbol if they are not already set, then exit.
      doOneFailedSentence(annotation, sentence);
      return;
    }
    if (VERBOSE) {
      boolean first = true;
      log.info("NERCombinerAnnotator direct output: [");
      for (CoreLabel w : output) {
        if (first) { first = false; } else { log.info(", "); }
        log.info(w.toString());
      }
    }
    if (output != null) {
      if (VERBOSE) {
        boolean first = true;
        log.info("NERCombinerAnnotator direct output: [");
        for (CoreLabel w : output) {
          if (first) {
            first = false;
          } else {
            log.info(", ");
          }
          log.info(w.toString());
        }
        log.info(']');
      }

      for (int i = 0; i < tokens.size(); ++i) {
        // add the named entity tag to each token
        String neTag = output.get(i).get(CoreAnnotations.NamedEntityTagAnnotation.class);
        String normNeTag = output.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        tokens.get(i).setNER(neTag);
        if (normNeTag != null) tokens.get(i).set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, normNeTag);
        NumberSequenceClassifier.transferAnnotations(output.get(i), tokens.get(i));
      }

      if (VERBOSE) {
        boolean first = true;
        log.info("NERCombinerAnnotator output: [");
        for (CoreLabel w : tokens) {
          if (first) {
            first = false;
          } else {
            log.info(", ");
          }
          log.info(w.toShorterString("Word", "NamedEntityTag", "NormalizedNamedEntityTag"));
        }
        log.info(']');
      }
    } else {
      for (CoreLabel token : tokens) {
        // add the dummy named entity tag to each token
        token.setNER(this.ner.backgroundSymbol());
      }
    }
  }

  @Override
  public void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token : tokens) {
      if (token.ner() == null) {
        token.setNER(this.ner.backgroundSymbol());
      }
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    // TODO: we could check the models to see which ones use lemmas
    // and which ones use pos tags
    if (ner.usesSUTime() || ner.appliesNumericClassifiers()) {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
          CoreAnnotations.TextAnnotation.class,
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          CoreAnnotations.CharacterOffsetEndAnnotation.class,
          CoreAnnotations.PartOfSpeechAnnotation.class,
          CoreAnnotations.LemmaAnnotation.class,
          CoreAnnotations.BeforeAnnotation.class,
          CoreAnnotations.AfterAnnotation.class,
          CoreAnnotations.TokenBeginAnnotation.class,
          CoreAnnotations.TokenEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.OriginalTextAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class
      )));
    } else {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
          CoreAnnotations.TextAnnotation.class,
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          CoreAnnotations.CharacterOffsetEndAnnotation.class,
          CoreAnnotations.BeforeAnnotation.class,
          CoreAnnotations.AfterAnnotation.class,
          CoreAnnotations.TokenBeginAnnotation.class,
          CoreAnnotations.TokenEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.OriginalTextAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class
      )));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return new HashSet<>(Arrays.asList(
        CoreAnnotations.NamedEntityTagAnnotation.class,
        CoreAnnotations.NormalizedNamedEntityTagAnnotation.class,
        CoreAnnotations.ValueAnnotation.class,
        TimeExpression.Annotation.class,
        TimeExpression.TimeIndexAnnotation.class,
        CoreAnnotations.DistSimAnnotation.class,
        CoreAnnotations.NumericCompositeTypeAnnotation.class,
        TimeAnnotations.TimexAnnotation.class,
        CoreAnnotations.NumericValueAnnotation.class,
        TimeExpression.ChildrenAnnotation.class,
        CoreAnnotations.NumericTypeAnnotation.class,
        CoreAnnotations.ShapeAnnotation.class,
        Tags.TagsAnnotation.class,
        CoreAnnotations.NumerizedTokensAnnotation.class,
        CoreAnnotations.AnswerAnnotation.class,
        CoreAnnotations.NumericCompositeValueAnnotation.class
    ));
  }
}
