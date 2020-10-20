package edu.stanford.nlp.parser.nndep.demo;

import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Demonstrates how to use the NN dependency
 * parser via a CoreNLP pipeline.
 *
 * @author Christopher Manning
 */
public class DependencyParserCoreNLPDemo {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(DependencyParserCoreNLPDemo.class);

  private DependencyParserCoreNLPDemo() {} // static main method only

  public static void main(String[] args) {
    String text;
    if (args.length > 0) {
      text = IOUtils.slurpFileNoExceptions(args[0], "utf-8");
    } else {
      text = "I can almost always tell when movies use fake dinosaurs.";
    }
    Annotation ann = new Annotation(text);

    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize,ssplit,pos,depparse",
            "depparse.model", DependencyParser.DEFAULT_MODEL
    );

    AnnotationPipeline pipeline = new StanfordCoreNLP(props);

    pipeline.annotate(ann);

    for (CoreMap sent : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      SemanticGraph sg = sent.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      log.info(IOUtils.eolChar + sg.toString(SemanticGraph.OutputFormat.LIST));
    }
  }

}
