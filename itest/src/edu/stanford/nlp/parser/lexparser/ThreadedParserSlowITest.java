package edu.stanford.nlp.parser.lexparser;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.LabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.util.TestPaths;

/**
 * Tests that the parser doesn't crash and comes up with the same
 * result when run in a variety of multithreaded situations
 */
public class ThreadedParserSlowITest {
  public static List<Tree> readTrees(String filename, String encoding) {
    ArrayList<Tree> trees = new ArrayList<>();
    try {
      TreeReaderFactory trf = new LabeledScoredTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(new InputStreamReader(
                        new FileInputStream(filename), encoding));
      Tree next;
      while ((next = tr.readTree()) != null) {
        trees.add(next);
      }
      System.out.println("Read " + trees.size() + " trees from " + filename);
      return trees;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Tree> processFile(LexicalizedParser parser,
                                       List<Tree> input) {
    List<Tree> results = new ArrayList<>();
    for (Tree tree : input) {
      List<HasWord> sentence = tree.yieldHasWord();
      Tree output = parser.parseTree(sentence);
      results.add(output);
      if (results.size() % 10 == 0 || results.size() == input.size()) {
        System.out.println("Processed " + results.size() + " trees");
      }
    }
    return results;
  }

  public static class ParserThread extends Thread {
    private final LexicalizedParser parser;
    private final List<Tree> input;
    private List<Tree> results;
    private List<Tree> expectedResults;

    public ParserThread(String parserFilename, List<Tree> input,
                        List<Tree> expectedResults) {
      parser = LexicalizedParser.loadModel(parserFilename);
      this.input = input;
      this.expectedResults = expectedResults;
    }

    public ParserThread(LexicalizedParser parser, List<Tree> input,
                        List<Tree> expectedResults) {
      this.parser = parser;
      this.input = input;
      this.expectedResults = expectedResults;
    }

    public void compareResults() {
      assertEquals(expectedResults.size(), results.size());
      for (int i = 0; i < expectedResults.size(); ++i) {
        assertEquals(expectedResults.get(i), results.get(i));
      }
    }

    @Override
    public void run() {
      results = processFile(parser, input);
    }
  }

  public static final String englishTrees = String.format("%s/lexparser/testtrees/engwsj160.mrg", TestPaths.testHome());
  public static final String englishEncoding = "utf-8";
  public static final String englishPCFG = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";

  public static final String germanTrees = String.format("%s/lexparser/testtrees/german133.mrg", TestPaths.testHome());
  public static final String germanEncoding = "ISO-8859-1";
  public static final String germanPCFG = "edu/stanford/nlp/models/lexparser/germanPCFG.ser.gz";

  public static final String frenchTrees = String.format("%s/lexparser/testtrees/french99.mrg", TestPaths.testHome());
  public static final String frenchEncoding = "UTF-8";

  public static final String arabicTrees = String.format("%s/lexparser/testtrees/arabic99.mrg", TestPaths.testHome());
  public static final String arabicEncoding = "UTF-8";

  public static final String chineseTrees = String.format("%s/lexparser/testtrees/chinese100.mrg", TestPaths.testHome());
  public static final String chineseEncoding = "utf-8";
  public static final String chinesePCFG = "edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz";

  public static Map<String, List<Tree>> expectedResults;
  public static Map<String, List<Tree>> inputTrees;

  public static void setupExpectedResults() {
    expectedResults = new HashMap<String, List<Tree>>();
    inputTrees = new HashMap<String, List<Tree>>();

    List<Tree> input = readTrees(englishTrees, englishEncoding);
    inputTrees.put(englishPCFG, input);
    //inputTrees.put(englishFactored, input);
    input = readTrees(germanTrees, germanEncoding);
    inputTrees.put(germanPCFG, input);
    //inputTrees.put(germanFactored, input);
    input = readTrees(frenchTrees, frenchEncoding);
    //inputTrees.put(frenchFactored, input);
    input = readTrees(arabicTrees, arabicEncoding);
    //inputTrees.put(arabicFactored, input);
    input = readTrees(chineseTrees, chineseEncoding);
    inputTrees.put(chinesePCFG, input);
    //inputTrees.put(chineseFactored, input);

    LexicalizedParser parser = LexicalizedParser.loadModel(englishPCFG);
    List<Tree> results = processFile(parser, inputTrees.get(englishPCFG));
    expectedResults.put(englishPCFG, results);

    //parser = LexicalizedParser.loadModel(englishFactored);
    //results = processFile(parser, inputTrees.get(englishFactored));
    //expectedResults.put(englishFactored, results);

    parser = LexicalizedParser.loadModel(germanPCFG);
    results = processFile(parser, inputTrees.get(germanPCFG));
    expectedResults.put(germanPCFG, results);

    //parser = LexicalizedParser.loadModel(germanFactored);
    //results = processFile(parser, inputTrees.get(germanFactored));
    //expectedResults.put(germanFactored, results);

    // TODO: Problem: too slow
    //parser = LexicalizedParser.loadModel(frenchFactored);
    // results = processFile(parser, inputTrees.get(frenchFactored));
    // expectedResults.put(frenchFactored, results);

    //parser = LexicalizedParser.loadModel(arabicFactored);
    //results = processFile(parser, inputTrees.get(arabicFactored));
    //expectedResults.put(arabicFactored, results);

    parser = LexicalizedParser.loadModel(chinesePCFG);
    results = processFile(parser, inputTrees.get(chinesePCFG));
    expectedResults.put(chinesePCFG, results);

    //parser = LexicalizedParser.loadModel(chineseFactored);
    // results = processFile(parser, inputTrees.get(chineseFactored));
    // expectedResults.put(chineseFactored, results);
  }

  @Before
  public void setUp() {
    synchronized(ThreadedParserSlowITest.class) {
      if (expectedResults == null) {
        setupExpectedResults();
      }
    }
  }

  public static void runFourTests(String pcfg, String factored)
    throws Exception
  {
    List<Tree> pcfgInput = inputTrees.get(pcfg);
    List<Tree> factoredInput = inputTrees.get(factored);

    List<Tree> pcfgResults = expectedResults.get(pcfg);
    List<Tree> factoredResults = expectedResults.get(factored);

    // Test two of the same PCFG
    LexicalizedParser parser = LexicalizedParser.loadModel(pcfg);
    runTest(new ParserThread(parser, pcfgInput, pcfgResults),
            new ParserThread(parser, pcfgInput, pcfgResults));

    // test two of the same factored
    //parser = LexicalizedParser.loadModel(factored);
    //runTest(new ParserThread(parser, factoredInput, factoredResults),
            //new ParserThread(parser, factoredInput, factoredResults));

    // test two different instantiations of the same pcfg
    runTest(new ParserThread(pcfg, pcfgInput, pcfgResults),
            new ParserThread(pcfg, pcfgInput, pcfgResults));

    // test one of each
    //runTest(new ParserThread(pcfg, pcfgInput, pcfgResults),
            //new ParserThread(factored, factoredInput, factoredResults));
  }

  public static void runTwoTests(String parserPath)
    throws Exception
  {
    List<Tree> input = inputTrees.get(parserPath);
    List<Tree> results = expectedResults.get(parserPath);

    // Test two of the same
    LexicalizedParser parser = LexicalizedParser.loadModel(parserPath);
    runTest(new ParserThread(parser, input, results),
            new ParserThread(parser, input, results));

    // test two different instantiations of the same model
    runTest(new ParserThread(parserPath, input, results),
            new ParserThread(parserPath, input, results));
  }

  @Test
  public void testEnglish()
    throws Exception
  {
    runTwoTests(englishPCFG);
  }

  @Test
  public void testGerman()
    throws Exception
  {
    runTwoTests(germanPCFG);
  }

  @Test
  public void testChinese()
    throws Exception
  {
    runTwoTests(chinesePCFG);
  }

  // TODO: problem: very slow
  // @Test
  // public void testFrench()
  //   throws Exception
  // {
  //   runTwoTests(frenchFactored);
  // }

  // TODO: problem: very slow
  //   @Test
  // public void testArabic()
  //   throws Exception
  // {
  //   runTwoTests(arabicFactored);
  // }

  public static void runTest(ParserThread ... threads) throws Exception {
    for (ParserThread thread : threads) {
      thread.start();
    }
    for (ParserThread thread : threads) {
      thread.join();
      thread.compareResults();
    }
  }
}
