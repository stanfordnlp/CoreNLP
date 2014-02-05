package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sentiment.CollapseUnaryTransformer;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCostAndGradient;
import edu.stanford.nlp.sentiment.SentimentModel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * This annotator attaches a binarized tree with sentiment annotations
 * to each sentence.  It requires there to already be binarized trees
 * attached to the sentence, which is best done in the
 * ParserAnnotator.
 * <br>
 * The tree will be attached to each sentence in the
 * SentencesAnnotation via the SentimentCoreAnnotations.AnnotatedTree
 * annotation.
 * <br>
 * The reason the decision was made to do the binarization in the
 * ParserAnnotator is because it may require specific options set in
 * the parser.  An alternative would be to do the binarization here,
 * which would require at a minimum the HeadFinder used in the parser.
 *
 * @author John Bauer 
 */
public class SentimentAnnotator implements Annotator {
  static final String DEFAULT_MODEL = "edu/stanford/nlp/models/sentiment/sentiment.ser.gz";
  String modelPath;
  SentimentModel model;
  CollapseUnaryTransformer transformer = new CollapseUnaryTransformer();

  public SentimentAnnotator(String name, Properties props) {
    this.modelPath = props.getProperty(name + ".model", DEFAULT_MODEL);
    if (modelPath == null) {
      throw new IllegalArgumentException("No model specified for Sentiment annotator");
    }
    this.model = SentimentModel.loadSerialized(modelPath);
  }

  public Set<Requirement> requirementsSatisfied() {
    return Collections.emptySet();
  }

  public Set<Requirement> requires() {
    return PARSE_TAG_BINARIZED_TREES;
  }

  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // TODO: parallelize
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      for (CoreMap sentence : sentences) {
        Tree binarized = sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class);
        Tree collapsedUnary = transformer.transformTree(binarized);
        SentimentCostAndGradient scorer = new SentimentCostAndGradient(model, null);
        scorer.forwardPropagateTree(collapsedUnary);
        sentence.set(SentimentCoreAnnotations.AnnotatedTree.class, collapsedUnary);
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

}
