package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;

public class CoNLLUTagUpdater {

  public static MaxentTagger maxentTagger;

  public static void main(String[] args) throws ClassNotFoundException, IOException {
    // load properties
    Properties props = StringUtils.argsToProperties(args);
    String taggerPath = props.getProperty("tagger");
    String inputPath = props.getProperty("inputFile");
    String outputPath = props.getProperty("outputFile");
    // set up tagger
    maxentTagger = new MaxentTagger(taggerPath);
    // make CoNLLUReader and read in file
    CoNLLUReader reader = new CoNLLUReader();
    List<Annotation> annotations = reader.readCoNLLUFile(inputPath);
    // update tags
    for (Annotation ann : annotations) {
      for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
        maxentTagger.tagCoreLabels(sentence.get(CoreAnnotations.TokensAnnotation.class));
      }
    }
    // output updated file
    Properties outputProps = new Properties();
    outputProps.setProperty("output.dependenciesType", "ENHANCEDPLUSPLUS");
    CoNLLUOutputter conLLUOutputter = new CoNLLUOutputter(outputProps);
    for (Annotation ann : annotations) {
      conLLUOutputter.print(ann, new BufferedOutputStream(new FileOutputStream(outputPath)));
    }
  }
}
