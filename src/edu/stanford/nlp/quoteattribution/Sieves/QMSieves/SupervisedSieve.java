package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.quoteattribution.Sieves.training.SupervisedSieveTraining;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

import java.util.*;

/**
 * Created by mjfang on 7/7/16.
 */
public class SupervisedSieve extends QMSieve {

    private ExtractQuotesClassifier quotesClassifier;

    public SupervisedSieve(Annotation doc, Map<String, List<Person>> characterMap, Map<Integer, String> pronounCorefMap, Set<String> animacyList) {
        super(doc, characterMap, pronounCorefMap, animacyList, "supervised");
    }

    public void loadModel(String filename) {
        quotesClassifier = new ExtractQuotesClassifier(filename);
    }

    public void doQuoteToMention(Annotation doc) {
        if(quotesClassifier == null) {
            throw new RuntimeException("need to do training first!");
        }
        SupervisedSieveTraining.FeaturesData fd = SupervisedSieveTraining.featurize(new SupervisedSieveTraining.SieveData(doc, this.characterMap, this.pronounCorefMap, this.animacySet), null, false);
        quotesClassifier.scoreBestMentionNew(fd, doc);
    }
}
