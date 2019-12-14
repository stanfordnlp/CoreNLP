package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * A demo illustrating how to call the OpenIE system programmatically.
 * You can call this code with:
 *
 * <pre>
 *   java -mx1g -cp stanford-openie.jar:stanford-openie-models.jar edu.stanford.nlp.naturalli.OpenIEDemo
 * </pre>
 *
 */
public class OpenIEDemo {

  private OpenIEDemo() {} // static main

  public static void main(String[] args) throws Exception {
    // Create the Stanford CoreNLP pipeline
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // Annotate an example document.
    String text;
    if (args.length > 0) {
      text = IOUtils.slurpFile(args[0]);
    } else {
      text = "Obama was born in Hawaii. He is our president.";
    }
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);

    // Loop over sentences in the document
    int sentNo = 0;
    for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println("Sentence #" + ++sentNo + ": " + sentence.get(CoreAnnotations.TextAnnotation.class));

      // Print SemanticGraph
      System.out.println(sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class).toString(SemanticGraph.OutputFormat.LIST));

      // Get the OpenIE triples for the sentence
      Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);

      // Print the triples
      for (RelationTriple triple : triples) {
        System.out.println(triple.confidence + "\t" +
            triple.subjectGloss() + "\t" +
            triple.relationGloss() + "\t" +
            triple.objectGloss());
      }

      // Alternately, to only run e.g., the clause splitter:
      List<SentenceFragment> clauses = new OpenIE(props).clausesInSentence(sentence);
      for (SentenceFragment clause : clauses) {
        System.out.println(clause.parseTree.toString(SemanticGraph.OutputFormat.LIST));
      }
      System.out.println();
    }
  }

}
