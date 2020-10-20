package edu.stanford.nlp.coref.neural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefRules;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.util.Pair;

/**
 * Extracts string matching, speaker, distance, and document genre features from mentions.
 * @author Kevin Clark
 */
public class CategoricalFeatureExtractor {
  private final Dictionaries dictionaries;
  private final Map<String, Integer> genres;
  private final boolean conll;

  public CategoricalFeatureExtractor(Properties props, Dictionaries dictionaries) {
    this.dictionaries = dictionaries;
    conll = CorefProperties.conll(props);

    if (conll) {
      genres = new HashMap<>();
      genres.put("bc", 0);
      genres.put("bn", 1);
      genres.put("mz", 2);
      genres.put("nw", 3);
      boolean english = CorefProperties.getLanguage(props) == Locale.ENGLISH;
      if (english) {
        genres.put("pt", 4);
      }
      genres.put("tc", english ? 5 : 4);
      genres.put("wb", english ? 6 : 5);
    } else {
      genres = null;
    }
  }

  public SimpleMatrix getPairFeatures(Pair<Integer, Integer> pair, Document document,
      Map<Integer, List<Mention>> mentionsByHeadIndex) {
    Mention m1 = document.predictedMentionsByID.get(pair.first);
    Mention m2 = document.predictedMentionsByID.get(pair.second);
    List<Integer> featureVals = pairwiseFeatures(document, m1, m2, dictionaries);
    SimpleMatrix features = new SimpleMatrix(featureVals.size(), 1);
    for (int i = 0; i < featureVals.size(); i++) {
      features.set(i, featureVals.get(i));
    }
    features = NeuralUtils.concatenate(features,
        encodeDistance(m2.sentNum - m1.sentNum),
        encodeDistance(m2.mentionNum - m1.mentionNum - 1),
        new SimpleMatrix(new double[][] {{
          m1.sentNum == m2.sentNum && m1.endIndex > m2.startIndex ? 1 : 0}}),
        getMentionFeatures(m1, document, mentionsByHeadIndex),
        getMentionFeatures(m2, document, mentionsByHeadIndex),
        encodeGenre(document));

    return features;
  }

  public static List<Integer> pairwiseFeatures(Document document, Mention m1, Mention m2,
      Dictionaries dictionaries) {
    String speaker1 = m1.headWord.get(CoreAnnotations.SpeakerAnnotation.class);
    String speaker2 = m2.headWord.get(CoreAnnotations.SpeakerAnnotation.class);
    boolean hasSpeakers = speaker1 != null && speaker2 != null;
    List<Integer> features = new ArrayList<>();
    features.add(hasSpeakers ? (speaker1.equals(speaker2) ? 1 : 0) : 0);
    features.add(hasSpeakers ?
        (CorefRules.antecedentIsMentionSpeaker(document, m2, m1, dictionaries) ? 1 : 0) : 0);
    features.add(hasSpeakers ?
        (CorefRules.antecedentIsMentionSpeaker(document, m1, m2, dictionaries) ? 1 : 0) : 0);
    features.add(m1.headsAgree(m2) ? 1 : 0);
    features.add(
        m1.toString().trim().toLowerCase().equals(m2.toString().trim().toLowerCase()) ? 1 : 0);
    features.add(edu.stanford.nlp.coref.statistical.FeatureExtractor.relaxedStringMatch(m1, m2)
        ? 1 : 0);
    return features;
  }

  public SimpleMatrix getAnaphoricityFeatures(Mention m, Document document,
      Map<Integer, List<Mention>> mentionsByHeadIndex) {
    return NeuralUtils.concatenate(
        getMentionFeatures(m, document, mentionsByHeadIndex),
        encodeGenre(document)
    );
  }

  private SimpleMatrix getMentionFeatures(Mention m, Document document,
      Map<Integer, List<Mention>> mentionsByHeadIndex) {
    return NeuralUtils.concatenate(
        NeuralUtils.oneHot(m.mentionType.ordinal(), 4),
        encodeDistance(m.endIndex - m.startIndex - 1),
        new SimpleMatrix(new double[][] {
          {m.mentionNum / (double) document.predictedMentionsByID.size()},
          {mentionsByHeadIndex.get(m.headIndex).stream()
            .anyMatch(m2 -> m != m2 && m.insideIn(m2)) ? 1 : 0}})
    );
  }

  public static SimpleMatrix encodeDistance(int d) {
    SimpleMatrix m = new SimpleMatrix(11, 1);
    if (d < 5) {
      m.set(d, 1);
    } else if (d < 8) {
      m.set(5, 1);
    } else if (d < 16) {
      m.set(6, 1);
    } else if (d < 32) {
      m.set(7, 1);
    } else if (d < 64) {
      m.set(8, 1);
    } else {
      m.set(9, 1);
    }
    m.set(10, Math.min(d, 64) / 64.0);
    return m;
  }

  private SimpleMatrix encodeGenre(Document document) {
    return conll ? NeuralUtils.oneHot(
        genres.get(document.docInfo.get("DOC_ID").split("/")[0]), genres.size()) :
          new SimpleMatrix(1, 1);
  }
}
