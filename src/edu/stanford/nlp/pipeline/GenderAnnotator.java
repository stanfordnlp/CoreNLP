package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Timing;

/**
 * This class adds gender information (MALE / FEMALE) to tokens as GenderAnnotations. It uses the
 * RegexNERSequenceClassifier and a manual mapping from token text to gender labels. Assumes
 * that the Annotation has already been split into sentences, then tokenized into Lists of CoreLabels.
 *
 * @author jtibs
 */

public class GenderAnnotator implements Annotator {

  private final RegexNERSequenceClassifier classifier;
  private final boolean verbose;

  public GenderAnnotator() {
    this(false, DefaultPaths.DEFAULT_GENDER_FIRST_NAMES);
  }

  public GenderAnnotator(boolean verbose, String mapping) {
    classifier = new RegexNERSequenceClassifier(mapping, true, true);
    this.verbose = verbose;
  }

  public void annotate(Annotation annotation) {
    if (verbose) {
      System.err.print("Adding gender annotation...");
    }

    if (! annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
      throw new RuntimeException("Unable to find sentences in " + annotation);

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      classifier.classify(tokens);

      for (CoreLabel token : tokens) {
        token.set(MachineReadingAnnotations.GenderAnnotation.class, token.get(CoreAnnotations.AnswerAnnotation.class));
      }
    }
  }


  @Override
  public Set<Requirement> requires() {
    return TOKENIZE_SSPLIT_POS;
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(GENDER_REQUIREMENT);
  }

}
