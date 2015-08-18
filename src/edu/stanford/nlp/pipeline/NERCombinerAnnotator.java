package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
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
public class NERCombinerAnnotator extends SentenceAnnotator {

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
      System.err.print("Adding NER Combiner annotation ... ");
    }

    super.annotate(annotation);
    this.ner.finalizeAnnotation(annotation);

    if (VERBOSE) {
      System.err.println("done.");
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
      System.err.print("NERCombinerAnnotator direct output: [");
      for (CoreLabel w : output) {
        if (first) { first = false; } else { System.err.print(", "); }
        System.err.print(w.toString());
      }
    }
    if (output != null) {
      if (VERBOSE) {
        boolean first = true;
        System.err.print("NERCombinerAnnotator direct output: [");
        for (CoreLabel w : output) {
          if (first) {
            first = false;
          } else {
            System.err.print(", ");
          }
          System.err.print(w.toString());
        }
        System.err.println(']');
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
        System.err.print("NERCombinerAnnotator output: [");
        for (CoreLabel w : tokens) {
          if (first) {
            first = false;
          } else {
            System.err.print(", ");
          }
          System.err.print(w.toShorterString("Word", "NamedEntityTag", "NormalizedNamedEntityTag"));
        }
        System.err.println(']');
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
  public Set<Requirement> requires() {
    // TODO: we could check the models to see which ones use lemmas
    // and which ones use pos tags
    if (ner.usesSUTime() || ner.appliesNumericClassifiers()) {
      return TOKENIZE_SSPLIT_POS_LEMMA;
    } else {
      return TOKENIZE_AND_SSPLIT;
    }
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(NER_REQUIREMENT);
  }
}
