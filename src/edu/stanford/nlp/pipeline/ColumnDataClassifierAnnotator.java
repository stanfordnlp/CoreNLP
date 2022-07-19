package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.classify.ColumnDataClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

/**
 * This annotator takes a properties file that was used to
 * train a ColumnDataClassifier and creates an annotator that
 * classifies the text by loading the classifier and running it.
 * So you must have the properties that were used to train the classifier.
 *
 * @author joberant
 * @version 9/8/14
 */
public class ColumnDataClassifierAnnotator implements Annotator {

  private final ColumnDataClassifier cdcClassifier;
  private final boolean verbose;
  private static final String DUMMY_LABEL_COLUMN = "DUMMY\t";

  public ColumnDataClassifierAnnotator(String propFile) {
    cdcClassifier = new ColumnDataClassifier(propFile);
    verbose = false;   // todo [cdm 2016]: Should really set from properties in propFile
  }

  public ColumnDataClassifierAnnotator(Properties props) {
    cdcClassifier = new ColumnDataClassifier(props);
    verbose = PropertiesUtils.getBool(props, "classify.verbose", false);
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

    // todo [cdm 2016]: At the moment this is hardwired to only work with answer = col 0, datum = col 1 classifier
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
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  //test - run from your top javanlp directory to get the files etc.
  public static void main(String[] args) {

    Properties props = StringUtils.propFileToProperties("src/edu/stanford/nlp/classify/mood.prop");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation happyAnnotation = new Annotation("I am so glad this is awesome");
    pipeline.annotate(happyAnnotation);
    Annotation sadAnnotation = new Annotation("I am so gloomy and depressed");
    pipeline.annotate(sadAnnotation);
    Annotation bothAnnotation = new Annotation("I am so gloomy gloomy gloomy gloomy glad");
    pipeline.annotate(bothAnnotation);
  }
}
