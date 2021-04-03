package edu.stanford.nlp.scenegraph;

import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

public abstract class AbstractSceneGraphParser {


  public abstract SceneGraph parse(SemanticGraph sg);

  public SceneGraph parse(Annotation annotation) {
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
      return this.parse(sg);
    }
    return null;
  }


  protected StanfordCoreNLP pipeline;

  private void initPipeline() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,parse,lemma,ner");
    props.setProperty("depparse.model", SceneGraphImagePCFGParser.PCFG_MODEL);
    props.setProperty("depparse.extradependencies", "MAXIMAL");
    this.pipeline = new StanfordCoreNLP(props);
  }

  protected AbstractSceneGraphParser() {

  }

  public SceneGraph parse(String input) {
    if (this.pipeline == null) {
      initPipeline();
    }

    Annotation ann = new Annotation(input);
    pipeline.annotate(ann);

    return this.parse(ann);
  }


}
