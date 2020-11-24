package edu.stanford.nlp.patterns;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class CandidatePhraseTest {

  @Test
  public void testStaticMethodCreateOrGetWithBranchCoverage() {
    //Create branch
    CandidatePhrase candidatePhrase = CandidatePhrase.createOrGet("a");
    Assert.assertEquals(candidatePhrase.getPhrase(), String.valueOf("a"));
    //Get branch
    CandidatePhrase.createOrGet("a");
    Assert.assertEquals(candidatePhrase.getPhrase(), String.valueOf("a"));
  }

  @Test
  public void testStaticMethodCreateOrGetWithLemmaBranchCoverage() {
    //Create branch
    CandidatePhrase candidatePhrase = CandidatePhrase.createOrGet("run", "runs");
    Assert.assertEquals(candidatePhrase.getPhrase(), String.valueOf("run"));
    Assert.assertEquals(candidatePhrase.getPhraseLemma(), String.valueOf("runs"));
    //Get branch
    candidatePhrase = CandidatePhrase.createOrGet("run", "runs");
    Assert.assertEquals(candidatePhrase.getPhrase(), String.valueOf("run"));
    Assert.assertEquals(candidatePhrase.getPhraseLemma(), String.valueOf("runs"));
  }

  @Test
  public void testStaticMethodCreateOrGetWithLemmaAndCounterBranchCoverage() {
    //get branch
    //emptyFeatures
    CandidatePhrase.createOrGet("run", "runs");

    Counter<String> counterA = new ClassicCounter<>();
    counterA.setCount("a", 1.0);
    Assert.assertEquals(counterA.size(), 1);

    CandidatePhrase candidatePhrase = CandidatePhrase.createOrGet("run", "runs", counterA);
    Assert.assertEquals(candidatePhrase.getFeatures().size(), 1);
    Assert.assertEquals(candidatePhrase.getPhrase(), "run");
    Assert.assertEquals(candidatePhrase.getPhraseLemma(), "runs");

    //create branch
    Counter<String> counterPQRS = new ClassicCounter<>();
    counterPQRS.setCount("p", 1.0);
    counterPQRS.setCount("q", 2.0);
    counterPQRS.setCount("r", 3.0);
    counterPQRS.setCount("s", 4.0);
    //Create branch
    candidatePhrase = CandidatePhrase.createOrGet("fight", "fights", counterPQRS);
    Assert.assertEquals(candidatePhrase.getPhrase(), "fight");
    Assert.assertEquals(candidatePhrase.getPhraseLemma(), "fights");
    Assert.assertEquals(candidatePhrase.getFeatures().size(), 4);
    Assert.assertEquals(candidatePhrase.getFeatureValue("p"), 1.0, 0d);
    Assert.assertEquals(candidatePhrase.getFeatureValue("q"), 2.0, 0d);
    Assert.assertEquals(candidatePhrase.getFeatureValue("r"), 3.0, 0d);
    Assert.assertEquals(candidatePhrase.getFeatureValue("s"), 4.0, 0d);
  }

  @Test
  public void testConvertStringPhrases() {
    List<String> stringList = new LinkedList();
    stringList.add("sleep");
    stringList.add("eat");

    List<CandidatePhrase> candidateList = CandidatePhrase.convertStringPhrases(stringList);
    Assert.assertEquals(candidateList.get(0).getPhrase(), "sleep");
    Assert.assertEquals(candidateList.get(1).getPhrase(), "eat");

    Assert.assertEquals(stringList.size(), candidateList.size());
  }

  @Test
  public void testConvertToString() {
    List<CandidatePhrase> candidateList = new LinkedList();
    candidateList.add(CandidatePhrase.createOrGet("sleep"));
    candidateList.add(CandidatePhrase.createOrGet("drink"));

    List<String> stringCandidateList = CandidatePhrase.convertToString(candidateList);
    Assert.assertEquals(stringCandidateList.get(0), "sleep");
    Assert.assertEquals(stringCandidateList.get(1), "drink");

    Assert.assertEquals(stringCandidateList.size(), candidateList.size());
  }
}
