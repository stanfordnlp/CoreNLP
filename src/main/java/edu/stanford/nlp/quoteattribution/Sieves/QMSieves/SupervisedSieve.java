package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.quoteattribution.Sieves.training.SupervisedSieveTraining;



/**
 * Created by mjfang on 7/7/16.
 */
public class SupervisedSieve extends QMSieve {

  private ExtractQuotesClassifier quotesClassifier;

  public SupervisedSieve(Annotation doc, Map<String, List<Person>> characterMap,
                         Map<Integer,String> pronounCorefMap, Set<String> animacyList) {
    super(doc, characterMap, pronounCorefMap, animacyList, "supervised");
  }

  public void loadModel(String filename) {
    quotesClassifier = new ExtractQuotesClassifier(filename);
  }

  public void doQuoteToMention(Annotation doc) {
    if (quotesClassifier == null) {
      throw new RuntimeException("need to do training first!");
    }
    SupervisedSieveTraining.FeaturesData fd = SupervisedSieveTraining.featurize(new SupervisedSieveTraining.SieveData(doc, this.characterMap, this.pronounCorefMap, this.animacySet), null, false);
    quotesClassifier.scoreBestMentionNew(fd, doc);
  }

}
