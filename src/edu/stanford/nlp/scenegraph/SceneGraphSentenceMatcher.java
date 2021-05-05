package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageUtils;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;


/**
 * Matches nodes in a scene graph to words in an
 * image description.
 *
 * @author Sebastian Schuster
 *
 */
public class SceneGraphSentenceMatcher {

  private final Embedding embeddings;

  public SceneGraphSentenceMatcher(Embedding embeddings) {
    this.embeddings = embeddings;
  }

  /**
   * Returns true if res contains null.
   */
  private static boolean containsNull(List<IndexedWord> res) {
    for (IndexedWord lbl : res) {
      if (lbl == null) return true;
    }
    return false;
  }


  /**
   * Picks one IndexedWord from subjects and one IndexedWord from objects
   * such that the distance between them is minimal. Considers multiple
   * appearances of the word in a sentence due to resolved pronouns.
   *
   * @param subjects List of subject candidates.
   * @param objects List of object candidates.
   * @return Subject-object pair.
   */
  public static Pair<IndexedWord, IndexedWord> findClosestPair(List<IndexedWord> subjects, List<IndexedWord> objects) {
    Pair<IndexedWord, IndexedWord> pair = new Pair<IndexedWord, IndexedWord>();
    int minDistance = Integer.MAX_VALUE;

    for (IndexedWord subj : subjects) {
      Set<Integer> subjIndices = subj.get(SceneGraphCoreAnnotations.IndicesAnnotation.class);
      if (subjIndices == null) {
        subjIndices = Generics.newHashSet();
        subjIndices.add(subj.index());
      }
      for (Integer subjIndex : subjIndices) {
        for (IndexedWord obj : objects) {
          Set<Integer> objIndices = obj.get(SceneGraphCoreAnnotations.IndicesAnnotation.class);
          if (objIndices == null) {
            objIndices = Generics.newHashSet();
            objIndices.add(obj.index());
          }
          for (Integer objIdx : objIndices) {
            int dist = Math.abs(subjIndex - objIdx);
            if (dist < minDistance) {
              minDistance = dist;
              pair.setFirst(subj);
              pair.setSecond(obj);
            }
          }
        }
      }
    }
    return pair;
  }

  /**
   * Aligns nodes in the scene graph to words in the corresponding sentence.
   * Returns a list of {@link Triple} of the IndexedWord corresponding to the
   * subject, the IndexedWord corresponding to the object or attribute and the
   * relation between them.
   *
   * @param region SceneGraphRegion with sentence and corresponding scene graph.
   * @return List of relation triples.
   */
  public List<Triple<IndexedWord, IndexedWord, String>> getRelationTriples(SceneGraphImageRegion region) {
    List<Triple<IndexedWord, IndexedWord, String>> triples = Generics.newLinkedList();

    /* Perform some of the enhancements.
     * We don't resolve plurals because we wouldn't know
     * to which copy the nodes in the scene graph should be aligned
     * and we don't collapse particles and compounds because we should
     * be able to align them just using the head word. */
    SemanticGraph sg = region.getBasicSemanticGraph();
    SemanticGraphEnhancer.processQuanftificationModifiers(sg);
    SemanticGraphEnhancer.resolvePronouns(sg);

    for (SceneGraphImageAttribute attr : region.attributes) {
      boolean attrContainsNull = false;
      List<IndexedWord> subjResult = getMatch(region, attr.subjectGloss, attr.subjectLemmaGloss(), sg, false);
      attrContainsNull = attrContainsNull || containsNull(subjResult);
      List<IndexedWord> attrResult  = getMatch(region, attr.attributeGloss, attr.attributeLemmaGloss(), sg, true);
      attrContainsNull = attrContainsNull || containsNull(attrResult);
      if (! attrContainsNull) {
        /* Construct attribute triple. */
        Pair<IndexedWord, IndexedWord> res = findClosestPair(subjResult, attrResult);
        res.first.set(SceneGraphCoreAnnotations.SceneGraphEntitiyAnnotation.class, attr.subjectLemmaGloss());
        res.second.set(SceneGraphCoreAnnotations.SceneGraphEntitiyAnnotation.class, attr.attributeLemmaGloss());
        triples.add(new Triple<IndexedWord,IndexedWord,String>(res.first, res.second, "is"));
      }
    }

    for (SceneGraphImageRelationship reln : region.relationships) {
      boolean relnContainsNull = false;
      List<IndexedWord> subjResult = getMatch(region, reln.subjectGloss, reln.subjectLemmaGloss(), sg, false);
      relnContainsNull = relnContainsNull || containsNull(subjResult);
      List<IndexedWord> objResult = getMatch(region, reln.objectGloss, reln.objectLemmaGloss(), sg, false);
      relnContainsNull = relnContainsNull || containsNull(objResult);
      if ( ! relnContainsNull) {
        /* Construct relation triple. */
        Pair<IndexedWord, IndexedWord> res = findClosestPair(subjResult, objResult);
        res.first.set(SceneGraphCoreAnnotations.SceneGraphEntitiyAnnotation.class, reln.subjectLemmaGloss());
        res.second.set(SceneGraphCoreAnnotations.SceneGraphEntitiyAnnotation.class, reln.objectLemmaGloss());
        triples.add(new Triple<IndexedWord,IndexedWord,String>(res.first, res.second, reln.predicateLemmaGloss()));
      }
    }
    return triples;
  }



  /**
   *
   * Returns a list of IndexedWord from the sentence which are the closest match to
   * the scene graph node passed as gloss.
   *
   * In case no match can be found, it returns a list with a null element.
   *
   * @param region A {@link SceneGraphImageRegion}.
   * @param gloss A list of CoreLabels with the words in the scene graph node.
   * @param lemmaGloss The lemma gloss of the scene graph node.
   * @param sg A SemanticGraph which is used to extract the candidate objects and attributes.
   * @param isAttr Whether the scene graph node is an object or an attribute.
   * @return A list of IndexedWord which match the scene graph node.
   */
  private List<IndexedWord> getMatch(SceneGraphImageRegion region, List<CoreLabel> gloss, String lemmaGloss, SemanticGraph sg, boolean isAttr) {

    List<IndexedWord> match = Generics.newLinkedList();

    /* Get candidates. */
    List<IndexedWord> tokens;
    if (isAttr) {
      tokens = EntityExtractor.extractAttributes(sg);
    } else {
      tokens = EntityExtractor.extractEntities(sg);
    }

    int len = gloss.size();
    if (len == 0) {
      match.add(null);
      return match;
    }


    int i;
    int max;
    if (isAttr) {
      /* First word is the head token. */
      i = 0;
      max = 1;
    } else {
      /* Last word is the head token. */
      i = len - 1;
      max = len;
    }

    for (; i < max; i++) {

      /* Test 1: Perfect match. */
      List<IndexedWord> result = SceneGraphImageUtils.findWord(tokens, gloss.get(i).word());
      if ( ! result.isEmpty()) {
        match.addAll(result);
        continue;
      }

      /* Test 2: Lemma match. */
      result = SceneGraphImageUtils.findLemma(tokens, gloss.get(i).lemma());
      if ( ! result.isEmpty()) {
        match.addAll(result);
        continue;
      }


      if (gloss.get(i).word().length() > 3) {
      /* Test 3: Edit distance. */
        int eMinIdx = -1;
        int eMinScore = 3;
        for (int j = 0; j < tokens.size(); j++) {
          String word1 = tokens.get(j).word();
          String word2 = gloss.get(i).word();
          int eDist = StringUtils.levenshteinDistance(word1, word2);
          if (eDist < eMinScore) {
            eMinIdx = j;
            eMinScore = eDist;
          }
        }


        if (eMinIdx > -1) {
          match.add(tokens.get(eMinIdx));
          continue;
        }
      }


      /* Test 4: Vector space distance. */
      SimpleMatrix vec1 = embeddings.get(gloss.get(i).lemma());
      if (vec1 == null) {
        match.add(null);
        continue;
      }
      int minIdx = -1;
      double minDist = Double.MAX_VALUE;
      List<IndexedWord> allTokens = sg.vertexListSorted();
      for (int j = 0; j < allTokens.size(); j++) {
        SimpleMatrix vec2 = embeddings.get(allTokens.get(j).lemma());
        if (vec2 == null) {
          continue;
        }
        double dist = vec1.minus(vec2).normF();
        if (dist < minDist) {
          minIdx = j;
          minDist = dist;
        }
      }

      if (minIdx > -1 && tagsCompatible(allTokens.get(minIdx), gloss.get(i))) {
        match.add(allTokens.get(minIdx));
      } else {
        match.add(null);
      }
    }

    for (IndexedWord word : match) {
      if (word != null) {
        word.set(SceneGraphCoreAnnotations.GoldEntityAnnotation.class, lemmaGloss);
      }
    }

    return match;
  }

  /**
   * Checks if the tag of a candidate and a scene graph node are
   * compatible. We consider nouns to compatible with other nouns
   * and adjectives and verbs compatible with other adjectives or verbs.
   *
   */
  private static boolean tagsCompatible(HasTag lbl1, HasTag lbl2) {
    if (lbl1.tag().startsWith("N") && lbl2.tag().startsWith("N")) {
      return true;
    }

    if (lbl1.tag().startsWith("V") && (lbl2.tag().startsWith("J") || lbl2.tag().startsWith("V"))) {
      return true;
    }

    if (lbl1.tag().startsWith("J") && (lbl2.tag().startsWith("J") || lbl2.tag().startsWith("V"))) {
      return true;
    }

    return false;
  }



  public static void main(String[] args) throws IOException {

    String filename = args[0];

    BufferedReader reader = IOUtils.readerFromString(filename);


    List<SceneGraphImage> images = Generics.newLinkedList();

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      images.add(img);

    }
  }
}
