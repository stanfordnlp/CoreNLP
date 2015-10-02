package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.TSVSentenceProcessor;
import edu.stanford.nlp.util.Execution;

import java.io.InputStream;

/**
 * A script to convert a TSV dump from our KBP sentences table into a Turk-task ready clause splitting dataset.
 *
 * @author Gabor Angeli
 */
public class CreateClauseDataset implements TSVSentenceProcessor {

  @Execution.Option(name="input", gloss="The input to read from")
  private static InputStream in = System.in;


  @Override
  public void process(long id, Annotation doc) {
    System.err.println(doc.get(CoreAnnotations.DocIDAnnotation.class));
  }


  public static void main(String[] args) {
    Execution.fillOptions(CreateClauseDataset.class, args);

    new CreateClauseDataset().runAndExit(in, System.err, code -> code);
  }
}
