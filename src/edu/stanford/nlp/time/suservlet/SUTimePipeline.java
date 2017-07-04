package edu.stanford.nlp.time.suservlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.time.GUTimeAnnotator;
import edu.stanford.nlp.time.HeidelTimeAnnotator;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.util.logging.Redwood;

public class SUTimePipeline  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SUTimePipeline.class);
  final StanfordCoreNLP pipeline;

  public SUTimePipeline() {
    this(new Properties());
  }

  public SUTimePipeline(Properties props) {
    // By default, we want to tokenize the text, split it into
    // sentences, and then put it through the sutime annotator.
    // We also want to pos tag it and put it through the number and
    // qen annotators.

    // Since there will be different options for the sutime annotator,
    // we will actually create a new sutime annotator for each query.
    // This should be inexpensive.

    if (props.getProperty("annotators") == null) {
      props.setProperty("annotators",
                        "tokenize, ssplit, pos");
//      "tokenize, ssplit, pos, number, qen");
    }
/*    if (props.getProperty("customAnnotatorClass.number") == null) {
      props.setProperty("customAnnotatorClass.number",
                        "edu.stanford.nlp.pipeline.NumberAnnotator");
    }
    if (props.getProperty("customAnnotatorClass.qen") == null) {
      props.setProperty("customAnnotatorClass.qen",
        "edu.stanford.nlp.pipeline.QuantifiableEntityNormalizingAnnotator");
    }    */
    // this replicates the tokenizer behavior in StanfordCoreNLP
    props.setProperty("tokenize.options", "invertible,ptb3Escaping=true");
    this.pipeline = new StanfordCoreNLP(props);
  }

  public boolean isDateOkay(String dateString) {
    return true; // TODO: can we predict which ones it won't like?
  }

  public Annotator getTimeAnnotator(String annotatorType,
                                    Properties props)
  {
    switch (annotatorType) {
      case "sutime":
        return new TimeAnnotator("sutime", props);
      case "gutime":
        return new GUTimeAnnotator("gutime", props);
      case "heideltime":
        return new HeidelTimeAnnotator("heidelTime", props);
      default:
        return null;
    }
  }

  public Annotation process(String sentence, String dateString, Annotator timeAnnotator) {
    log.info("Processing text \"" + sentence + "\" with dateString = " + dateString);
    Annotation anno = new Annotation(sentence);
    if (dateString != null && ! dateString.isEmpty()) {
      anno.set(CoreAnnotations.DocDateAnnotation.class, dateString);
    }
    pipeline.annotate(anno);

    timeAnnotator.annotate(anno);
    return anno;
  }

  public static void main(String[] args) throws IOException {
    SUTimePipeline pipeline = new SUTimePipeline();
    Annotator timeAnnotator = pipeline.getTimeAnnotator("sutime", new Properties());
    BufferedReader is = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("> ");
    for(String line; (line = is.readLine()) != null; ){
      Annotation ann = pipeline.process(line, null, timeAnnotator);
      System.out.println(ann.get(TimeAnnotations.TimexAnnotations.class));
      System.out.print("> ");
    }
  }

}
