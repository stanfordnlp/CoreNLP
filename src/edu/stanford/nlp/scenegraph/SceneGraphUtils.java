package edu.stanford.nlp.scenegraph;

import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.scenegraph.SceneGraphCoreAnnotations.IndicesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

public class SceneGraphUtils {

  public static IndexedWord getCommonAncestor(SemanticGraph sg, IndexedWord w1, IndexedWord w2) {

    List<SemanticGraphEdge> path = sg.getShortestUndirectedPathEdges(w1, w2);

    if (path == null || path.isEmpty()) return null;

    for (int i = 0, max = path.size() - 1; i < max; i++) {
      if (path.get(i).getGovernor() == path.get(i+1).getGovernor()) {
        return path.get(i).getGovernor();
      }
    }

    if (path.get(0).getGovernor() == w1) {
      return w1;
    }

    if (path.get(path.size() - 1).getGovernor() == w2) {
      return w2;
    }

    return null;
  }

  public static boolean inSameSubTree(SemanticGraph sg, IndexedWord w1, IndexedWord w2) {

    //return true;



    List<IndexedWord> path = sg.getShortestUndirectedPathNodes(w1, w2);

    Pair<Integer, Integer> indices = getClosestIndices(w1, w2);

    int minIdx = indices.first;
    int maxIdx = indices.second;

    for (IndexedWord word : path) {
      if ((word.index() > maxIdx || word.index() < minIdx) && ! word.containsKey(IndicesAnnotation.class)) {
        return false;
      } else if (word.containsKey(IndicesAnnotation.class)) {
        boolean reject = true;
        for (Integer idx : word.get(IndicesAnnotation.class)) {
          if (idx <= maxIdx && word.index() >= minIdx) {
            reject = false;
          }
        }
        if (reject) {
          return false;
        }
      }
    } //*/

    //if (path.get(w1, w2))



    return true;
  }

  public static Pair<Integer, Integer> getClosestIndices(IndexedWord w1, IndexedWord w2) {
    int idx1 = w1.index();
    int idx2 = w2.index();

    Set<Integer> idcs1 = w1.get(SceneGraphCoreAnnotations.IndicesAnnotation.class);
    Set<Integer> idcs2 = w2.get(SceneGraphCoreAnnotations.IndicesAnnotation.class);
    if (idcs1 != null || idcs2 != null) {
      if (idcs1 == null) {
        idcs1 = Generics.newHashSet();
        idcs1.add(idx1);
      }
      if (idcs2 == null) {
        idcs2 = Generics.newHashSet();
        idcs2.add(idx2);
      }

      for (Integer i1 : idcs1) {
        for (Integer i2 : idcs2) {
          if (Math.abs(i1 - i2) < Math.abs(idx1-idx2)) {
            idx1 = i1;
            idx2 = i2;
          }
        }
      }
    }
    return new Pair<Integer, Integer>(idx1, idx2);
  }

}
