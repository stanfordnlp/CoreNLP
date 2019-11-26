package edu.stanford.nlp.coref.neural;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Extracts word-embedding features from mentions.
 * @author Kevin Clark
 */
public class EmbeddingExtractor {
  private final boolean conll;
  private final Embedding staticWordEmbeddings;
  private final Embedding tunedWordEmbeddings;

  public EmbeddingExtractor(boolean conll, Embedding staticWordEmbeddings,
      Embedding tunedWordEmbeddings) {
    this.conll = conll;
    this.staticWordEmbeddings = staticWordEmbeddings;
    this.tunedWordEmbeddings = tunedWordEmbeddings;
  }

  public SimpleMatrix getDocumentEmbedding(Document document) {
    if (!conll) {
      return new SimpleMatrix(staticWordEmbeddings.getEmbeddingSize(), 1);
    }
    List<CoreLabel> words = new ArrayList<>();
    Set<Integer> seenSentences = new HashSet<>();
    for (Mention m : document.predictedMentionsByID.values()) {
      if (!seenSentences.contains(m.sentNum)) {
        seenSentences.add(m.sentNum);
        words.addAll(m.sentenceWords);
      }
    }
    return getAverageEmbedding(words);
  }

  public SimpleMatrix getMentionEmbeddings(Mention m, SimpleMatrix docEmbedding) {
    Iterator<SemanticGraphEdge> depIterator =
        m.enhancedDependency.incomingEdgeIterator(m.headIndexedWord);
    SemanticGraphEdge depRelation = depIterator.hasNext() ? depIterator.next() : null;

    return NeuralUtils.concatenate(
        getAverageEmbedding(m.sentenceWords, m.startIndex, m.endIndex),
        getAverageEmbedding(m.sentenceWords, m.startIndex - 5, m.startIndex),
        getAverageEmbedding(m.sentenceWords, m.endIndex, m.endIndex + 5),
        getAverageEmbedding(m.sentenceWords.subList(0, m.sentenceWords.size() - 1)),
        docEmbedding,
        getWordEmbedding(m.sentenceWords, m.headIndex),
        getWordEmbedding(m.sentenceWords, m.startIndex),
        getWordEmbedding(m.sentenceWords, m.endIndex - 1),
        getWordEmbedding(m.sentenceWords, m.startIndex - 1),
        getWordEmbedding(m.sentenceWords, m.endIndex),
        getWordEmbedding(m.sentenceWords, m.startIndex - 2),
        getWordEmbedding(m.sentenceWords, m.endIndex + 1),
        getWordEmbedding(depRelation == null ? null : depRelation.getSource().word())
    );
  }

  private SimpleMatrix getAverageEmbedding(List<CoreLabel> words) {
    SimpleMatrix emb = new SimpleMatrix(staticWordEmbeddings.getEmbeddingSize(), 1);
    for (CoreLabel word : words) {
      emb = emb.plus(getStaticWordEmbedding(word.word()));
    }
    return emb.divide(Math.max(1, words.size()));
  }

  private SimpleMatrix getAverageEmbedding(List<CoreLabel> sentence, int start, int end) {
    return getAverageEmbedding(sentence.subList(Math.max(Math.min(start, sentence.size() - 1), 0),
        Math.max(Math.min(end, sentence.size() - 1), 0)));
  }

  private SimpleMatrix getWordEmbedding(List<CoreLabel> sentence, int i) {
    return getWordEmbedding(i < 0 || i >= sentence.size() ? null : sentence.get(i).word());
  }

  public SimpleMatrix getWordEmbedding(String word) {
    word = normalizeWord(word);
    return tunedWordEmbeddings.containsWord(word) ? tunedWordEmbeddings.get(word) :
      staticWordEmbeddings.get(word);
  }

  public SimpleMatrix getStaticWordEmbedding(String word) {
    return staticWordEmbeddings.get(normalizeWord(word));
  }

  private static String normalizeWord(String w) {
    if (w == null) {
      return "<missing>";
    } else if (w.equals("/.")) {
      return ".";
    } else if (w.equals("/?")) {
      return "?";
    } else if (w.equals("-LRB-")) {
      return "(";
    } else if (w.equals("-RRB-")) {
      return ")";
    } else if (w.equals("-LCB-")) {
      return "{";
    } else if (w.equals("-RCB-")) {
      return "}";
    } else if (w.equals("-LSB-")) {
      return "[";
    } else if (w.equals("-RSB-")) {
      return "]";
    }
    return w.replaceAll("\\d", "0").toLowerCase();
  }
}
