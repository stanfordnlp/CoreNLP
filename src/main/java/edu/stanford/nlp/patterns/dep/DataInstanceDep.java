package edu.stanford.nlp.patterns.dep;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.io.Serializable;
import java.util.List;

/**
 * Created by sonalg on 11/1/14.
 */
public class DataInstanceDep extends DataInstance implements Serializable {

  SemanticGraph graph;
  List<CoreLabel> tokens;

  public DataInstanceDep(CoreMap s){
    graph = s.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
//    System.out.println("CollapsedCCProcessedDependenciesAnnotation graph is " + s.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
//    System.out.println("CollapsedDependenciesAnnotation graph is " + s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
//    System.out.println("BasicDependenciesAnnotation graph is " + s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    tokens = s.get(CoreAnnotations.TokensAnnotation.class);
  }

  @Override
  public List<CoreLabel> getTokens() {
    return tokens;
  }

  public SemanticGraph getGraph() {
    return graph;
  }

  public String toString(){
    return StringUtils.join(tokens, " ");
  }
}
