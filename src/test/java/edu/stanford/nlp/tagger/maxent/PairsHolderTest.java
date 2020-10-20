package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.ling.WordTag;

import org.junit.Assert;
import org.junit.Test;


/** @author Christopher Manning */
public class PairsHolderTest {

  @Test
  public void testPairsHolder() {
    PairsHolder pairsHolder = new PairsHolder();

    for (int i = 0; i < 10; i++) {
      pairsHolder.add(new WordTag("girl", "NN"));
    }

    MaxentTagger maxentTagger = new MaxentTagger();
    maxentTagger.init(null);

    //maxentTagger.pairs = pairsHolder;
    History h = new History(0, 5, 3, pairsHolder, maxentTagger.extractors);
    TaggerExperiments te = new TaggerExperiments(maxentTagger);
    int x = te.getHistoryTable().add(h);
    //int x = maxentTagger.tHistories.add(h);
    int y = te.getHistoryTable().getIndex(h);
    //int y = maxentTagger.tHistories.getIndex(h);
    Assert.assertEquals("Failing to get same index for history", x, y);
    Extractor e = new Extractor(0, false);
    String k = e.extract(h);
    Assert.assertEquals("Extractor didn't find stored word", k, "girl");
  }

}
