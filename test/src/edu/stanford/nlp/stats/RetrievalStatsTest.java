package edu.stanford.nlp.stats;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.util.DefaultValuedMap;

import junit.framework.Assert;
import junit.framework.TestCase;

public class RetrievalStatsTest extends TestCase {
  
  public void testAveragePrecision() {
    Set<String> relevant;
    List<String> results;
    double expected, actual;
    
    relevant = new HashSet<String>(Arrays.asList("H D".split(" ")));
    results = Arrays.asList("A B C D E F G H".split(" "));
    expected = (0.25 + 0.25) / 2;
    actual = RetrievalStats.averagePrecision(relevant, results);
    Assert.assertEquals(expected, actual, 1e-10);

    relevant = new HashSet<String>(Arrays.asList("A B C D".split(" ")));
    results = Arrays.asList("A B C D".split(" "));
    expected = (1.0 + 1.0 + 1.0 + 1.0) / 4;
    actual = RetrievalStats.averagePrecision(relevant, results);
    Assert.assertEquals(expected, actual, 1e-10);

    relevant = new HashSet<String>(Arrays.asList("B H".split(" ")));
    results = Arrays.asList("A B C D E F G H".split(" "));
    expected = (0.5 + 0.25) / 2;
    actual = RetrievalStats.averagePrecision(relevant, results);
    Assert.assertEquals(expected, actual, 1e-10);

    relevant = new HashSet<String>(Arrays.asList("H D".split(" ")));
    results = Arrays.asList("A B C E F G".split(" "));
    expected = 0.0;
    actual = RetrievalStats.averagePrecision(relevant, results);
    Assert.assertEquals(expected, actual, 1e-10);
    
    relevant = new HashSet<String>(Arrays.asList("D E".split(" ")));
    results = Arrays.asList("A B C D".split(" "));
    expected = 0.25 / 2;
    actual = RetrievalStats.averagePrecision(relevant, results);
    Assert.assertEquals(expected, actual, 1e-10);
    
    try {
      RetrievalStats.averagePrecision(new HashSet<String>(), results);
      Assert.fail("expected exception for empty relevant set");
    } catch (IllegalArgumentException e) {}
  }
  
  public void testDatumAveragePrecision() {
    double expected, actual;

    RVFDataset<Boolean, Integer> data = new RVFDataset<Boolean, Integer>();
    this.addDatum(data, true, 1, 2);
    this.addDatum(data, false, 3, 4);
    this.addDatum(data, false, 2, 4);
    this.addDatum(data, true, 4, 1);
    expected = (1.0 + 0.5) / 2;
    actual = RetrievalStats.averagePrecision(data, null);
    Assert.assertEquals(expected, actual, 1e-10);
    
    expected = (1.0 + 0.5) / 4;
    actual = RetrievalStats.averagePrecision(data, 4);
    Assert.assertEquals(expected, actual, 1e-10);
  }
  
  private <L, F> void addDatum(RVFDataset<L, F> data, L label, F ... features) {
    data.add(new RVFDatum<L, F>(Counters.asCounter(Arrays.asList(features)), label));
  }

  public void testPageRank() {
    // A
    //   \
    //     C - D
    //   /
    // B
    // 
    // E - F - H
    //   \
    //     G
    Map<String, List<String>> links = DefaultValuedMap.arrayListValuedMap();
    links.get("A").add("C");
    links.get("B").add("C");
    links.get("C").add("D");
    links.get("D");
    links.get("E").add("F");
    links.get("E").add("G");
    links.get("F").add("H");
    links.get("G");
    links.get("H");
    Counter<String> pageRanks;
    
    // make sure a single iteration just gives all pages equal values
    pageRanks = RetrievalStats.pageRanks(links, 0, 0.75);
    Assert.assertEquals(8, pageRanks.size());
    double a0 = 0.125;
    double b0 = 0.125;
    double c0 = 0.125;
    double d0 = 0.125;
    double e0 = 0.125;
    double f0 = 0.125;
    double g0 = 0.125;
    double h0 = 0.125;
    Assert.assertEquals(a0, pageRanks.getCount("A"), 0.0);
    Assert.assertEquals(b0, pageRanks.getCount("B"), 0.0);
    Assert.assertEquals(c0, pageRanks.getCount("C"), 0.0);
    Assert.assertEquals(d0, pageRanks.getCount("D"), 0.0);
    Assert.assertEquals(e0, pageRanks.getCount("E"), 0.0);
    Assert.assertEquals(f0, pageRanks.getCount("F"), 0.0);
    Assert.assertEquals(g0, pageRanks.getCount("G"), 0.0);
    Assert.assertEquals(h0, pageRanks.getCount("H"), 0.0);

    // run one iteration
    pageRanks = RetrievalStats.pageRanks(links, 1, 0.75);
    Assert.assertEquals(8, pageRanks.size());
    double sink1 = (d0 + g0 + h0) / 8;
    double a1 = 0.25 / 8 + 0.75 * (sink1);
    double b1 = 0.25 / 8 + 0.75 * (sink1);
    double c1 = 0.25 / 8 + 0.75 * (sink1 + a0 / 1 + b0 / 1);
    double d1 = 0.25 / 8 + 0.75 * (sink1 + c0 / 1);
    double e1 = 0.25 / 8 + 0.75 * (sink1);
    double f1 = 0.25 / 8 + 0.75 * (sink1 + e0 / 2);
    double g1 = 0.25 / 8 + 0.75 * (sink1 + e0 / 2);
    double h1 = 0.25 / 8 + 0.75 * (sink1 + f0 / 1);
    Assert.assertEquals(a1, pageRanks.getCount("A"), 0.0);
    Assert.assertEquals(b1, pageRanks.getCount("B"), 0.0);
    Assert.assertEquals(c1, pageRanks.getCount("C"), 0.0);
    Assert.assertEquals(d1, pageRanks.getCount("D"), 0.0);
    Assert.assertEquals(e1, pageRanks.getCount("E"), 0.0);
    Assert.assertEquals(f1, pageRanks.getCount("F"), 0.0);
    Assert.assertEquals(g1, pageRanks.getCount("G"), 0.0);
    Assert.assertEquals(h1, pageRanks.getCount("H"), 0.0);

    // run two iterations
    pageRanks = RetrievalStats.pageRanks(links, 2, 0.75);
    Assert.assertEquals(8, pageRanks.size());
    double sink2 = (d1 + g1 + h1) / 8;
    double a2 = 0.25 / 8 + 0.75 * (sink2);
    double b2 = 0.25 / 8 + 0.75 * (sink2);
    double c2 = 0.25 / 8 + 0.75 * (sink2 + a1 / 1 + b1 / 1);
    double d2 = 0.25 / 8 + 0.75 * (sink2 + c1 / 1);
    double e2 = 0.25 / 8 + 0.75 * (sink2);
    double f2 = 0.25 / 8 + 0.75 * (sink2 + e1 / 2);
    double g2 = 0.25 / 8 + 0.75 * (sink2 + e1 / 2);
    double h2 = 0.25 / 8 + 0.75 * (sink2 + f1 / 1);
    Assert.assertEquals(a2, pageRanks.getCount("A"), 0.0);
    Assert.assertEquals(b2, pageRanks.getCount("B"), 0.0);
    Assert.assertEquals(c2, pageRanks.getCount("C"), 0.0);
    Assert.assertEquals(d2, pageRanks.getCount("D"), 0.0);
    Assert.assertEquals(e2, pageRanks.getCount("E"), 0.0);
    Assert.assertEquals(f2, pageRanks.getCount("F"), 0.0);
    Assert.assertEquals(g2, pageRanks.getCount("G"), 0.0);
    Assert.assertEquals(h2, pageRanks.getCount("H"), 0.0);
    
    // run many iterations
    pageRanks = RetrievalStats.pageRanks(links);
    Assert.assertEquals(8, pageRanks.size());
    
    // root nodes are all equal, A = B = E
    Assert.assertEquals(pageRanks.getCount("A"), pageRanks.getCount("B"));
    Assert.assertEquals(pageRanks.getCount("B"), pageRanks.getCount("E"));
    
    // C, D, F, G and H which have in-links are better than A, B and E
    Assert.assertTrue(pageRanks.getCount("C") > pageRanks.getCount("A"));
    Assert.assertTrue(pageRanks.getCount("D") > pageRanks.getCount("A"));
    Assert.assertTrue(pageRanks.getCount("F") > pageRanks.getCount("A"));
    Assert.assertTrue(pageRanks.getCount("G") > pageRanks.getCount("A"));
    Assert.assertTrue(pageRanks.getCount("H") > pageRanks.getCount("A"));

    // C with two links is better than F and G with one
    Assert.assertTrue(pageRanks.getCount("C") > pageRanks.getCount("F"));
    Assert.assertTrue(pageRanks.getCount("C") > pageRanks.getCount("G"));
    
    // D is better than H because the incoming link is stronger
    Assert.assertTrue(pageRanks.getCount("D") > pageRanks.getCount("H"));
  }
}
