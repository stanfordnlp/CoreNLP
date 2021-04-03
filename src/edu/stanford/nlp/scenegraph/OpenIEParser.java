package edu.stanford.nlp.scenegraph;

import java.util.Properties;
import java.util.Scanner;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel.OutputFormat;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.CoreMap;

public class OpenIEParser extends AbstractSceneGraphParser {


  public OpenIEParser() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,parse,depparse,lemma,ner,dcoref,natlog,openie");
    props.setProperty("depparse.model", "/Users/sebschu/Dropbox/Uni/RA/VisualGenome/parser-models/run0.gz");
    props.setProperty("depparse.extradependencies", "MAXIMAL");
    this.pipeline = new StanfordCoreNLP(props);
  }

  @Override
  public SceneGraph parse(SemanticGraph sg) {
   throw new RuntimeException("not implemented!");
  }

  @Override
  public SceneGraph parse(Annotation annotation) {

    SceneGraph sg = new SceneGraph();

    /*for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (RelationTriple extraction : sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class)) {


        String[] subjParts = extraction.subjectGloss().split(" ");
        String[] objParts = extraction.objectGloss().split(" ");
        String subj = extraction.subjectHead().toString(OutputFormat.VALUE_INDEX);
        String obj = extraction.objectHead().toString(OutputFormat.VALUE_INDEX);

        String pred = extraction.relationLemmaGloss();


        SceneGraphNode subjNode = sg.getOrAddNode(subj);
        SceneGraphNode objNode = sg.getOrAddNode(obj);

        for (int i = 0; i < subjParts.length - 1; i++) {
          subjNode.addAttribute(subjParts[i]);
        }

        for (int i = 0; i < objParts.length - 1; i++) {
          objNode.addAttribute(objParts[i]);
        }

        if (pred.equals("be")) {
          sg.getOrAddNode(subj).addAttribute(extraction.objectLemmaGloss());
        } else {

          sg.addEdge(subjNode, objNode, pred);
        }


      }
    }
    */

    return sg;

  }

  public static void main(String[] args) {
    AbstractSceneGraphParser parser = new OpenIEParser();

    System.err.println("Processing from stdin. Enter one sentence per line.");
    Scanner scanner = new Scanner(System.in);
    String line;
    while ( (line = scanner.nextLine()) != null ) {
      SceneGraph scene = parser.parse(line);
      System.out.println(scene.toReadableString());

      System.out.println("------------------------");
      System.out.print("> ");

    }

    scanner.close();

  }

}
