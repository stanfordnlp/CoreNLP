package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

import java.util.Properties;

/**
 * Created by sonalg on 7/15/14.
 */
public class SemgrexPatternITest {

  @Test
  public void testNERStanfordDependencies() throws Exception{
    String sentence = "John lives in Washington.";
    Properties props = new Properties();
    props.setProperty("annotators","tokenize, ssplit, pos, lemma, ner, parse");
    props.setProperty("parse.originalDependencies", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation doc = new Annotation(sentence);
    pipeline.annotate(doc);
    CoreMap sent = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    SemanticGraph graph = sent.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    graph.prettyPrint();
    String patStr = "({word:/lives/} >/prep_in/ {word:/\\QCalifornia\\E|\\QWashington\\E/} >nsubj {ner:PERSON})";
    SemgrexPattern pat = SemgrexPattern.compile(patStr);
    SemgrexMatcher mat = pat.matcher(graph, true);
    assertTrue(mat.find());
  }

  @Test
  public void testNERUniversalDependencies() throws Exception{
    String sentence = "John lives in Washington.";
    Properties props = new Properties();
    props.setProperty("annotators","tokenize, ssplit, pos, lemma, ner, parse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    props.setProperty("parse.originalDependencies", "false");
    Annotation doc = new Annotation(sentence);
    pipeline.annotate(doc);
    CoreMap sent = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    SemanticGraph graph = sent.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    graph.prettyPrint();
    String patStr = "({word:/lives/} >/obl:in/ {word:/\\QCalifornia\\E|\\QWashington\\E/} >nsubj {ner:PERSON})";
    SemgrexPattern pat = SemgrexPattern.compile(patStr);
    SemgrexMatcher mat = pat.matcher(graph, true);
    assertTrue(mat.find());
  }
}
