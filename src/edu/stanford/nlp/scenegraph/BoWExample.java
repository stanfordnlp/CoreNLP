package edu.stanford.nlp.scenegraph;

import java.util.List;


import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class BoWExample {

  IndexedWord w1;
  IndexedWord w2;

  List<IndexedWord> words;

  SemanticGraph sg;

  public enum FEATURE_SET {
    LEMMA_BOW,
    WORD_BOW,
    TREE_FEAT
  };


  public BoWExample(IndexedWord w1, IndexedWord w2, SemanticGraph sg) {
    this.w1 = w1;
    this.w2 = w2;
    this.words = findWordsInBetween(w1, w2, sg);
    this.sg = sg;
  }


  /**
   * Returns a list of words which are in between w1 and w2.
   */
  private static List<IndexedWord> findWordsInBetween(IndexedWord w1, IndexedWord w2, SemanticGraph sg) {
    List<IndexedWord> words = Generics.newArrayList();

   Pair<Integer, Integer> indices = SceneGraphUtils.getClosestIndices(w1, w2);

    int idx1 = indices.first;
    int idx2 = indices.second;

    for (IndexedWord w : sg.vertexSet()) {
      if (w.index() > idx1 && w.index() < idx2) {
        words.add(w);
      }
    }
    return words;
  }


  public List<String> extractFeatures(FEATURE_SET...featureSets) {
    List<String> features = Generics.newLinkedList();

    /* Word */
    features.add(String.format("w1:%s", w1.word()));
    features.add(String.format("w2:%s", w2.word()));
    /* Lemma */
    features.add(String.format("l1:%s", w1.lemma()));
    features.add(String.format("l2:%s", w2.lemma()));
    /* Predicted object/attribute */
    features.add(String.format("p1:%s", w1.get(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class)));
    features.add(String.format("p2:%s", w2.get(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class)));

    for (FEATURE_SET fs : featureSets) {
      switch (fs) {
        case WORD_BOW:
          /* Words in between w1 and w2 */
          for (IndexedWord word : words) {
            features.add(String.format("w:%s", word.word()));
          }
          break;
        case LEMMA_BOW:
          /* Lemmata of words in between w1 and w2 */
          for (IndexedWord word : words) {
            features.add(String.format("l:%s", word.lemma()));
          }
          break;
        case TREE_FEAT:
          /* Path in SemanticGraph from w1 to w2 */
          List<SemanticGraphEdge> shortestPath = this.sg.getShortestUndirectedPathEdges(w1, w2);
          if (shortestPath == null) {
            break;
          }
          String path = StringUtils.join(shortestPath.stream().map(x -> x.getRelation().getShortName()), "_");
          features.add(String.format("sPath:%s", path));
          break;
        default:
          break;
        }
    }
    return features;
  }
}


