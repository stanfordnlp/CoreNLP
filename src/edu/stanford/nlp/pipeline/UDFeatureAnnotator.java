package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.ud.UniversalDependenciesFeatureAnnotator;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collections;
import java.util.Set;

/**
 * Extracts universal dependencies features from a tree
 *
 * @author Sebastian Schuster
 */
public class UDFeatureAnnotator extends SentenceAnnotator {

  private UniversalDependenciesFeatureAnnotator featureAnnotator;


  public UDFeatureAnnotator() {
    this.featureAnnotator = new UniversalDependenciesFeatureAnnotator();
  }

  @Override
  protected int nThreads() {
    return 1;
  }

  @Override
  protected long maxTime() {
    return 0;
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    Tree t = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    featureAnnotator.addFeatures(sg, t, false, true);
  }

  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    //do nothing
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.CoNLLUFeats.class);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(Collections.singleton(
            SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class
    ));
  }

}
