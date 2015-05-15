package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unchecked")
@Ignore  // TODO(gabor) ignore until the models get formally added
public class NaturalLogicWeightsITest {

  private static Supplier<NaturalLogicWeights> weights = new Supplier<NaturalLogicWeights>() {
    NaturalLogicWeights weights = null;
    @Override
    public NaturalLogicWeights get() {
      if (weights == null) {
        try {
          weights = new NaturalLogicWeights("edu/stanford/nlp/naturalli/", 1.0 / 3.0);
        } catch (IOException e) {
          return null;
        }
      }
      return weights;
    }
  };

  private CoreLabel mockWord(String text) {
    CoreLabel word = new CoreLabel();
    word.setOriginalText(text);
    word.setWord(text);
    return word;
  }

  private Pair<SemanticGraphEdge, List<SemanticGraphEdge>> mkSegment(
          String root, Pair<String, String>... outEdges) {
    IndexedWord rootVertex = new IndexedWord(mockWord(root));
    List<SemanticGraphEdge> edges = Arrays.asList(outEdges).stream().map(pair -> new SemanticGraphEdge(rootVertex,
        new IndexedWord(mockWord(pair.second)),
        GrammaticalRelation.valueOf(Language.English, pair.first), Double.NEGATIVE_INFINITY, false)).collect(Collectors.toList());
    return Pair.makePair(edges.get(0), edges);
  }

  @Test
  public void testLoadWeightsDoesntCrash() throws IOException {
    assertNotNull(weights.get());
  }

  @Test
  public void testSomeSanityChecks() throws IOException {
    Pair<SemanticGraphEdge, List<SemanticGraphEdge>> spec = mkSegment(
        "signed", Pair.makePair("prep_into", "law"), Pair.makePair("nsubj", "Obama"));
    assertTrue(0.5 > weights.get().deletionProbability(spec.first, spec.second));

    spec = mkSegment(
        "threw", Pair.makePair("prep_at", "her"), Pair.makePair("nsubj", "he"), Pair.makePair("dobj", "ball"));
    assertTrue(0.5 < weights.get().deletionProbability(spec.first, spec.second));
  }
}
