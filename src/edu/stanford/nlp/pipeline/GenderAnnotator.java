package edu.stanford.nlp.pipeline;

import java.util.List;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.GenderAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Timing;

/**
 * This class adds gender information (MALE / FEMALE) to tokens as GenderAnnotations. It uses the 
 * RegexNERSequenceClassifier and a manual mapping from token text to gender labels. Assumes 
 * that the Annotation has already been split into sentences, then tokenized into Lists of CoreLabels. 
 * 
 * @author jtibs
 * 
 */

public class GenderAnnotator implements Annotator {
  private RegexNERSequenceClassifier classifier;
  private Timing timer;
  private boolean verbose;
  
  public GenderAnnotator() {
    this(false, DefaultPaths.DEFAULT_GENDER_FIRST_NAMES);
  }
  
  public GenderAnnotator(boolean verbose, String mapping) {
    classifier = new RegexNERSequenceClassifier(mapping, true, true);
    timer = new Timing();
    this.verbose = verbose;
  }

  public void annotate(Annotation annotation) {
    if (verbose) {    
      timer.start();
      System.err.print("Adding gender annotation...");
    }
    
    if (! annotation.containsKey(SentencesAnnotation.class))
      throw new RuntimeException("Unable to find sentences in " + annotation);
  
    List<CoreMap> sentences = annotation.get(SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
      classifier.classify(tokens);
  
      for (CoreLabel token : tokens) 
        token.set(GenderAnnotation.class, token.get(AnswerAnnotation.class));
    }
    
    if (verbose)
      timer.stop("done.");
  }
}
