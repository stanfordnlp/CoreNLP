package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.Sieves.Sieve;
import edu.stanford.nlp.quoteattribution.Sieves.training.SupervisedSieveTraining;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Created by michaelf on 3/31/16.
 *
 */
public class ExtractQuotesClassifier {

  boolean verbose = true;
  private Classifier<String, String> quoteToMentionClassifier;
  public ExtractQuotesClassifier(GeneralDataset<String, String> trainingSet)
  {
    LinearClassifierFactory<String, String> lcf = new LinearClassifierFactory<>();
    quoteToMentionClassifier = lcf.trainClassifier(trainingSet);
  }

  public ExtractQuotesClassifier(String modelPath) {
    try {
      ObjectInputStream si = IOUtils.readStreamFromString(modelPath);
      quoteToMentionClassifier = (Classifier<String, String>) si.readObject();
      si.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      throw new RuntimeException();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new RuntimeException();
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException();
    }
  }
  public Classifier<String, String> getClassifier() {
    return quoteToMentionClassifier;
  }

  public void scoreBestMentionNew(SupervisedSieveTraining.FeaturesData fd, Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for(int i = 0; i < quotes.size(); i++) {
      CoreMap quote = quotes.get(i);
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        continue;
      }
      double maxConfidence = 0;
      int maxDataIdx = -1;
      int goldDataIdx = -1;
      Pair<Integer, Integer> dataRange = fd.mapQuoteToDataRange.get(i);
      if(dataRange == null) {
        continue;
      }
      else {
        for(int dataIdx = dataRange.first; dataIdx <= dataRange.second; dataIdx++) {
          RVFDatum<String, String> datum = fd.dataset.getRVFDatum(dataIdx);
          double isMentionConfidence = quoteToMentionClassifier.scoresOf(datum).getCount("isMention");
          if(isMentionConfidence > maxConfidence) {
            maxConfidence = isMentionConfidence;
            maxDataIdx = dataIdx;
          }
        }
        if(maxDataIdx != -1) {

          Sieve.MentionData mentionData = fd.mapDatumToMention.get(maxDataIdx);
          if(mentionData.type.equals("animate noun"))
            continue;
          quote.set(QuoteAttributionAnnotator.MentionAnnotation.class, mentionData.text);
          quote.set(QuoteAttributionAnnotator.MentionBeginAnnotation.class, mentionData.begin);
          quote.set(QuoteAttributionAnnotator.MentionEndAnnotation.class, mentionData.end);
          quote.set(QuoteAttributionAnnotator.MentionTypeAnnotation.class, mentionData.type);
          quote.set(QuoteAttributionAnnotator.MentionSieveAnnotation.class, "supervised");
        }
      }
    }
  }
}
