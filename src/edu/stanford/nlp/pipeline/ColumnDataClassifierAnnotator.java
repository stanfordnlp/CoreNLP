package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.classify.ColumnDataClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Datum;

import java.util.Collections;
import java.util.Set;

/**
 * Created by joberant on 9/8/14.
 * This annotator takes a properties file that was used to
 * train a ColumnDataClassifier and creates an annotator that
 * classifies the text by loading the classifier and running it
 * So you must have the properties that were used to train the classifier
 */
public class ColumnDataClassifierAnnotator implements Annotator {

  private final ColumnDataClassifier cdcClassifier;
  private boolean verbose = false;
  private final static String DUMMY_LABEL_COLUMN = "DUMMY\t";

  public ColumnDataClassifierAnnotator(String propFile) {
    cdcClassifier = new ColumnDataClassifier(propFile);
  }

  public ColumnDataClassifierAnnotator(String propFile, boolean verbose) {
    cdcClassifier = new ColumnDataClassifier(propFile);
    this.verbose = verbose;
  }


  @Override
  public void annotate(Annotation annotation) {
    if(verbose)
      System.out.println("Adding column data classifier annotation...");
    String text = DUMMY_LABEL_COLUMN + annotation.get(CoreAnnotations.TextAnnotation.class);
    if(verbose)
      System.out.println("Dummy column: " + text);

    Datum<String,String> datum = cdcClassifier.makeDatumFromLine(text);
    if(verbose)
      System.out.println("Datum: " + datum.toString());

    String label = cdcClassifier.classOf(datum);
    annotation.set(CoreAnnotations.ColumnDataClassifierAnnotation.class,label);

    if(verbose)
      System.out.println(
              String.format("annotation=%s",annotation.get(CoreAnnotations.ColumnDataClassifierAnnotation.class)));

    if(verbose)
      System.out.println("Done.");
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(COLUMN_DATA_CLASSIFIER);
  }

  @Override
  public Set<Requirement> requires() {
    return Collections.emptySet();
  }

  public static void main(String[] args) {
    ColumnDataClassifierAnnotator annotator =
            new ColumnDataClassifierAnnotator("projects/core/src/edu/stanford/nlp/classify/mood.prop",true);
    Annotation happyAnnotation = new Annotation("I am so glad this is awesome");
    annotator.annotate(happyAnnotation);
    Annotation sadAnnotation = new Annotation("I am so gloomy and depressed");
    annotator.annotate(sadAnnotation);
    Annotation bothAnnotation = new Annotation("I am so gloomy gloomy gloomy gloomy glad");
    annotator.annotate(bothAnnotation);
  }
}
