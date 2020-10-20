package edu.stanford.nlp.coref.statistical;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.coref.statistical.MaxMarginMentionRanker.ErrorType;

import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Class for training coreference models
 * @author Kevin Clark
 */
public class PairwiseModelTrainer {
  public static void trainRanking(PairwiseModel model) throws Exception {
    Redwood.log("scoref-train", "Reading compression...");
    Compressor<String> compressor = IOUtils.readObjectFromFile(
        StatisticalCorefTrainer.compressorFile);

    Redwood.log("scoref-train", "Reading train data...");
    List<DocumentExamples> trainDocuments = IOUtils.readObjectFromFile(
        StatisticalCorefTrainer.extractedFeaturesFile);

    Redwood.log("scoref-train", "Training...");
    for (int i = 0; i < model.getNumEpochs(); i++) {
      Collections.shuffle(trainDocuments);
      int j = 0;
      for (DocumentExamples doc : trainDocuments) {
        j++;
        Redwood.log("scoref-train", "On epoch: " + i + " / " + model.getNumEpochs()
            + ", document: " + j + " / " + trainDocuments.size());
        Map<Integer, List<Example>> mentionToPotentialAntecedents = new HashMap<>();
        for (Example e : doc.examples) {
          int mention = e.mentionId2;
          List<Example> potentialAntecedents = mentionToPotentialAntecedents.get(mention);
          if (potentialAntecedents == null) {
            potentialAntecedents = new ArrayList<>();
            mentionToPotentialAntecedents.put(mention, potentialAntecedents);
          }
          potentialAntecedents.add(e);
        }

        List<List<Example>> examples = new ArrayList<>(
            mentionToPotentialAntecedents.values());
        Collections.shuffle(examples);
        for (List<Example> es : examples) {
          if (es.size() == 0) {
            continue;
          }

          if (model instanceof MaxMarginMentionRanker) {
            MaxMarginMentionRanker ranker = (MaxMarginMentionRanker) model;
            boolean noAntecedent = es.stream().allMatch(e -> e.label == 0);
            es.add(new Example(es.get(0), noAntecedent));

            double maxPositiveScore = -Double.MAX_VALUE;
            Example maxScoringPositive = null;
            for (Example e : es) {
              double score = model.predict(e, doc.mentionFeatures, compressor);
              if (e.label == 1) {
                assert(!noAntecedent ^ e.isNewLink());
                if (score > maxPositiveScore) {
                  maxPositiveScore = score;
                  maxScoringPositive = e;
                }
              }
            }
            assert(maxScoringPositive != null);

            double maxNegativeScore = -Double.MAX_VALUE;
            Example maxScoringNegative = null;
            ErrorType maxScoringEt = null;
            for (Example e : es) {
              double score = model.predict(e, doc.mentionFeatures, compressor);
              if (e.label != 1) {
                assert(!(noAntecedent && e.isNewLink()));
                ErrorType et = ErrorType.WL;
                if (noAntecedent && !e.isNewLink()) {
                  et = ErrorType.FL;
                } else if (!noAntecedent && e.isNewLink()) {
                  if (e.mentionType2 == MentionType.PRONOMINAL) {
                    et = ErrorType.FN_PRON;
                  } else {
                    et = ErrorType.FN;
                  }
                }

                if (ranker.multiplicativeCost) {
                  score = ranker.costs[et.id] * (1 - maxPositiveScore + score);
                } else {
                  score += ranker.costs[et.id];
                }
                if (score > maxNegativeScore) {
                  maxNegativeScore = score;
                  maxScoringNegative = e;
                  maxScoringEt = et;
                }
              }
            }
            assert(maxScoringNegative != null);

            ranker.learn(maxScoringPositive, maxScoringNegative,
                doc.mentionFeatures, compressor, maxScoringEt);
          } else {
            double maxPositiveScore = -Double.MAX_VALUE;
            double maxNegativeScore = -Double.MAX_VALUE;
            Example maxScoringPositive = null;
            Example maxScoringNegative = null;
            for (Example e : es) {
              double score = model.predict(e, doc.mentionFeatures, compressor);
              if (e.label == 1) {
                if (score > maxPositiveScore) {
                  maxPositiveScore = score;
                  maxScoringPositive = e;
                }
              } else {
                if (score > maxNegativeScore) {
                  maxNegativeScore = score;
                  maxScoringNegative = e;
                }
              }
            }
            model.learn(maxScoringPositive, maxScoringNegative,
                doc.mentionFeatures, compressor, 1);
          }
        }
      }
    }

    Redwood.log("scoref-train", "Writing models...");
    model.writeModel();
  }

  public static List<Pair<Example, Map<Integer, CompressedFeatureVector>>>
      getAnaphoricityExamples(List<DocumentExamples> documents) {
    int p = 0;
    int t = 0;

    List<Pair<Example, Map<Integer, CompressedFeatureVector>>> examples = new ArrayList<>();
    while (!documents.isEmpty()) {
      DocumentExamples doc = documents.remove(documents.size() - 1);
      Map<Integer, Boolean> areAnaphoric = new HashMap<>();
      for (Example e : doc.examples) {
        Boolean isAnaphoric = areAnaphoric.get(e.mentionId2);
        if (isAnaphoric == null) {
          areAnaphoric.put(e.mentionId2, false);
        }
        if (e.label == 1) {
          areAnaphoric.put(e.mentionId2, true);
        }
      }

      for (Map.Entry<Integer, Boolean> e : areAnaphoric.entrySet()) {
        if (e.getValue()) {
          p++;
        }
        t++;
      }

      for (Example e : doc.examples) {
        Boolean isAnaphoric = areAnaphoric.get(e.mentionId2);
        if (isAnaphoric != null) {
          areAnaphoric.remove(e.mentionId2);
          examples.add(new Pair<>(new Example(e, isAnaphoric), doc.mentionFeatures));
        }
      }
    }

    Redwood.log("scoref-train", "Num anaphoricity examples " + p + " positive, " + t + " total");

    return examples;
  }

  public static List<Pair<Example, Map<Integer, CompressedFeatureVector>>> getExamples(
      List<DocumentExamples> documents) {
    List<Pair<Example, Map<Integer, CompressedFeatureVector>>> examples = new ArrayList<>();
    while (!documents.isEmpty()) {
      DocumentExamples doc = documents.remove(documents.size() - 1);
      Map<Integer, CompressedFeatureVector> mentionFeatures = doc.mentionFeatures;
      for (Example e : doc.examples) {
        examples.add(new Pair<>(e, mentionFeatures));
      }
    }
    return examples;
  }

  public static void trainClassification(PairwiseModel model, boolean anaphoricityModel)
      throws Exception {
    int numTrainingExamples = model.getNumTrainingExamples();

    Redwood.log("scoref-train", "Reading compression...");
    Compressor<String> compressor = IOUtils.readObjectFromFile(
        StatisticalCorefTrainer.compressorFile);

    Redwood.log("scoref-train", "Reading train data...");
    List<DocumentExamples> trainDocuments = IOUtils.readObjectFromFile(
        StatisticalCorefTrainer.extractedFeaturesFile);

    Redwood.log("scoref-train", "Building train set...");
    List<Pair<Example, Map<Integer, CompressedFeatureVector>>> allExamples = anaphoricityModel
        ? getAnaphoricityExamples(trainDocuments) : getExamples(trainDocuments);

    Redwood.log("scoref-train", "Training...");
    Random random = new Random(0);
    int i = 0;
    boolean stopTraining = false;
    while (!stopTraining) {
      Collections.shuffle(allExamples, random);
      for (Pair<Example, Map<Integer, CompressedFeatureVector>> pair : allExamples) {
        if (i++ > numTrainingExamples) {
          stopTraining = true;
          break;
        }
        if (i % 10000 == 0) {
          Redwood.log("scoref-train", String.format("On train example %d/%d = %.2f%%",
              i, numTrainingExamples, 100.0 * i / numTrainingExamples));
        }
        model.learn(pair.first, pair.second, compressor);
      }
    }

    Redwood.log("scoref-train", "Writing models...");
    model.writeModel();
  }

  public static void test(PairwiseModel model, String predictionsName,
      boolean anaphoricityModel) throws Exception {
    Redwood.log("scoref-train", "Reading compression...");
    Compressor<String> compressor = IOUtils.readObjectFromFile(
        StatisticalCorefTrainer.compressorFile);

    Redwood.log("scoref-train", "Reading test data...");
    List<DocumentExamples> testDocuments = IOUtils.readObjectFromFile(
        StatisticalCorefTrainer.extractedFeaturesFile);

    Redwood.log("scoref-train", "Building test set...");
    List<Pair<Example, Map<Integer, CompressedFeatureVector>>> allExamples = anaphoricityModel
        ? getAnaphoricityExamples(testDocuments) : getExamples(testDocuments);

    Redwood.log("scoref-train", "Testing...");
    PrintWriter writer = new PrintWriter(model.getDefaultOutputPath() + predictionsName);
    Map<Integer, Counter<Pair<Integer, Integer>>> scores = new HashMap<>();
    writeScores(allExamples, compressor, model, writer, scores);
    if (model instanceof MaxMarginMentionRanker) {
      writer.close();
      writer = new PrintWriter(model.getDefaultOutputPath() + predictionsName + "_anaphoricity");
      testDocuments = IOUtils.readObjectFromFile(
          StatisticalCorefTrainer.extractedFeaturesFile);
      allExamples = getAnaphoricityExamples(testDocuments);
      writeScores(allExamples, compressor, model, writer, scores);
    }
    IOUtils.writeObjectToFile(scores, model.getDefaultOutputPath() + predictionsName + ".ser");
    writer.close();
  }

  public static void writeScores(List<Pair<Example,
      Map<Integer, CompressedFeatureVector>>> examples,
      Compressor<String> compressor, PairwiseModel model, PrintWriter writer,
      Map<Integer, Counter<Pair<Integer, Integer>>> scores) {
    int i  = 0;
    for (Pair<Example, Map<Integer, CompressedFeatureVector>> pair : examples) {
      if (i++ % 10000 == 0) {
        Redwood.log("scoref-train", String.format("On test example %d/%d = %.2f%%",
            i, examples.size(), 100.0 * i / examples.size()));
      }
      Example example = pair.first;
      Map<Integer, CompressedFeatureVector> mentionFeatures = pair.second;
      double p = model.predict(example, mentionFeatures, compressor);
      writer.println(example.docId + " " + example.mentionId1 + ","
          + example.mentionId2 + " "  + p + " " + example.label);
      Counter<Pair<Integer, Integer>> docScores = scores.get(example.docId);
      if (docScores == null) {
        docScores = new ClassicCounter<>();
        scores.put(example.docId, docScores);
      }
      docScores.incrementCount(new Pair<>(example.mentionId1, example.mentionId2), p);
    }
  }
}
