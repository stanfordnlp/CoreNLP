package edu.stanford.nlp.dcoref;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.Generics;

/**
 * Run the dcoref system on a particular input file from the DEFT
 * project.  Check that the output is an exact match for the output we
 * expect to get.
 *
 * Expected results are represented in a data file in the source tree.
 * Rather than try to rebuild the CorefChain objects from the expected
 * results, we keep an internal class which represents them in a very
 * simple manner.  Also included are utility methods to rewrite the
 * expected results file if we change the sample input used.
 *
 * Assuming the test file has not changed, the command line to rebuild
 * the expected output is
 *
 * {@code java edu.stanford.nlp.dcoref.DcorefExactOutputITest projects/core/data/edu/stanford/nlp/dcoref/STILLALONEWOLF_20050102.1100.eng.LDC2005E83.sgm projects/core/data/edu/stanford/nlp/dcoref/STILLALONEWOLF_20050102.1100.eng.LDC2005E83.expectedcoref }
 *
 * @author John Bauer
 */
public class DcorefExactOutputITest {

  private static StanfordCoreNLP pipeline; // = null;

  @Before
  public void setUp() {
    synchronized (DcorefExactOutputITest.class) {
      if (pipeline == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref");
        pipeline = new StanfordCoreNLP(props);
      }
    }
  }

  private static class ExpectedMention {
    int sentNum;
    String mentionSpan;

    ExpectedMention(String line) {
      String[] pieces = line.trim().split(" +", 2);
      sentNum = Integer.valueOf(pieces[0]);
      mentionSpan = pieces[1];
    }

    @Override
    public String toString() {
      return sentNum + ": " + mentionSpan;
    }
  }

  private static Map<Integer, List<ExpectedMention>> loadExpectedResults(String filename) {
    Map<Integer, List<ExpectedMention>> results = Generics.newHashMap();

    int id = -1;
    List<String> mentionLines = new ArrayList<>();
    for (String line : IOUtils.readLines(filename)) {
      if (line.trim().isEmpty()) {
        if (mentionLines.isEmpty()) {
          if (id != -1) {
            throw new RuntimeException("Found coref chain without any mentions, id " + id);
          }
          continue;
        }
        List<ExpectedMention> mentions = new ArrayList<>();
        for (String mentionLine : mentionLines) {
          mentions.add(new ExpectedMention(mentionLine));
        }
        results.put(id, mentions);
        id = -1;
        mentionLines.clear();
        continue;
      }

      if (id == -1) {
        id = Integer.parseInt(line);
      } else {
        mentionLines.add(line.trim());
      }
    }

    return results;
  }

  private static void saveResults(String filename, Map<Integer, CorefChain> chains) throws IOException {
    FileWriter fout = new FileWriter(filename);
    BufferedWriter bout = new BufferedWriter(fout);

    List<Integer> keys = new ArrayList<>(chains.keySet());
    Collections.sort(keys);

    for (Integer key : keys) {
      saveKey(bout, key, chains.get(key));
    }

    bout.flush();
    bout.close();
    fout.close();
  }

  private static void saveKey(BufferedWriter bout, Integer key, CorefChain chain) throws IOException {
    bout.write(key.toString());
    bout.newLine();
    for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
      bout.write(mention.sentNum + " " + mention.mentionSpan);
      bout.newLine();
    }
    bout.newLine();
  }

  private static boolean compareChain(List<ExpectedMention> expectedChain, CorefChain chain) {
    for (ExpectedMention expectedMention : expectedChain) {
      boolean found = false;
      for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
        if (mention.sentNum == expectedMention.sentNum && mention.mentionSpan.equals(expectedMention.mentionSpan)) {
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

  private static void compareResults(Map<Integer, List<ExpectedMention>> expected, Map<Integer, CorefChain> chains) {
    // Note that we don't insist on the chain ID numbers being the same
    for (Map.Entry<Integer, List<ExpectedMention>> mapEntry : expected.entrySet()) {
      boolean found = false;
      List<ExpectedMention> expectedChain = mapEntry.getValue();
      for (CorefChain chain : chains.values()) {
        if (compareChain(expectedChain, chain)) {
          found = true;
          break;
        }
      }
      Assert.assertTrue("Could not find expected coref chain " + mapEntry.getKey() + ' ' + expectedChain + " in the results", found);
    }

    for (Map.Entry<Integer, CorefChain> integerCorefChainEntry : chains.entrySet()) {
      boolean found = false;
      CorefChain chain = integerCorefChainEntry.getValue();
      for (List<ExpectedMention> expectedChain : expected.values()) {
        if (compareChain(expectedChain, chain)) {
          found = true;
          break;
        }
      }
      Assert.assertTrue("Dcoref produced chain " + chain + " which was not in the expected results", found);
    }

    Assert.assertEquals("Unexpected difference in number of chains", expected.size(), chains.size());
  }

  @Test
  public void testCoref() throws IOException {
    String doc = IOUtils.slurpFile("edu/stanford/nlp/dcoref/STILLALONEWOLF_20050102.1100.eng.LDC2005E83.sgm");
    Annotation annotation = pipeline.process(doc);
    Map<Integer, CorefChain> chains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    Map<Integer, List<ExpectedMention>> expected = loadExpectedResults("edu/stanford/nlp/dcoref/STILLALONEWOLF_20050102.1100.eng.LDC2005E83.expectedcoref");
    compareResults(expected, chains);
  }

  /**
   * If run as a program, writes the expected output of args[0] to args[1].
   * This is useful for updating the desired test results when CoreNLP changes.
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Expected args <input> <output>");
      throw new IllegalArgumentException();
    }

    String input = args[0];
    String output = args[1];

    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // for example
    // "edu/stanford/nlp/dcoref/STILLALONEWOLF_20050102.1100.eng.LDC2005E83.sgm"
    String doc = IOUtils.slurpFile(input);
    Annotation annotation = pipeline.process(doc);
    Map<Integer, CorefChain> chains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    saveResults(output, chains);
  }

}
