package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.ud.UniversalDependenciesFeatureAnnotator;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 *
 * Extracts universal dependencies features from a tree
 *
 * @author Sebastian Schuster
 */
public class UDFeatureAnnotator extends SentenceAnnotator {

    private UniversalDependenciesFeatureAnnotator featureAnnotator;


    public UDFeatureAnnotator() {
        try {
            this.featureAnnotator = new UniversalDependenciesFeatureAnnotator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    public Set<Requirement> requirementsSatisfied() {
        return Collections.singleton(UD_FEATURES_REQUIREMENT);
    }

    @Override
    public Set<Requirement> requires() {
        return Annotator.REQUIREMENTS.get(STANFORD_UD_FEATURES);
    }
}
