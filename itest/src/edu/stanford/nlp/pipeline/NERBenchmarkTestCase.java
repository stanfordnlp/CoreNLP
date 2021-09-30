package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

import static org.junit.Assert.assertEquals;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

public abstract class NERBenchmarkTestCase {

  /** official CoNLL NER evaluation script **/
  public static final String NER_EVAL_SCRIPT = String.format("%s/ner/benchmark/eval_conll.sh", TestPaths.testHome());

  /** regex for matching F1 score line in official script output **/
  private static final Pattern FB1_Pattern = Pattern.compile("FB1:  (\\d+\\.\\d+)");

  public String language;
  public Properties pipelineProperties;
  public StanfordCoreNLP pipeline;

  public String workingDir;
  public String devGoldFile;
  public String testGoldFile;
  public String devPredictedFile;
  public String testPredictedFile;

  public Double expectedDevScore;
  public Double expectedTestScore;

  @Before
  public void setUp() {
    languageSpecificSetUp();
    setUpPaths();
    buildPipeline();
  }

  public void addLanguageSpecificProperties() {}

  public abstract void languageSpecificSetUp();

  public void setUpPaths() {
    // it is assumed the working dir has a benchmark dir in it for storing benchmarks
    devPredictedFile = workingDir+"/benchmark/"+devGoldFile+".predicted";
    testPredictedFile = workingDir+"/benchmark/"+testGoldFile+".predicted";
    devGoldFile = workingDir+"/"+devGoldFile;
    testGoldFile = workingDir+"/"+testGoldFile;
  }

  public void buildPipeline() {
    pipelineProperties = StringUtils.argsToProperties("-lang", language);
    pipelineProperties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    pipelineProperties.setProperty("tokenize.whitespace", "true");
    addLanguageSpecificProperties();
    pipeline = new StanfordCoreNLP(pipelineProperties);
  }

  /**
   * Load CoNLL style NER data, transform into sentence, named entity tag list pairs.
   * @param filePath
   * @return
   */
  public static List<Pair<String, List<String>>> loadSentencesFromCoNLLFile(String filePath) {
    // set up return list
    List<Pair<String, List<String>>> returnList = new ArrayList<Pair<String, List<String>>>();
    // load lines from file
    List<String> conllLines = IOUtils.linesFromFile(filePath);
    conllLines.add("");
    // build sentences
    String currSentence = "";
    List<String> currNERTags = new ArrayList<String>();
    for (String conllLine : conllLines) {
      if (conllLine.equals("")) {
        if (currSentence.length() > 0) {
          returnList.add(new Pair<>(currSentence.trim(), currNERTags));
        }
        currSentence = ""; currNERTags = new ArrayList<String>();
      } else {
        String[] conllFields = conllLine.split("\t");
        currSentence += (conllFields[0]+" ");
        currNERTags.add(conllFields[1]);
      }
    }
    return returnList;
  }

  /**
   * Apply NER tags with the given pipeline, produce final CoNLL output file.  Assumes
   * pipeline will honor the tokenization of the input (i.e. by using tokenize.whitespace = true)
   * @param filePath
   * @return CoNLL style output with "word\tgold tag\tguessed tag"
   */
  public String tagCoNLLFileWithPipeline(String filePath) {
    String finalCoNLLOutput = "";
    // load CoNLL data
    List<Pair<String, List<String>>> loadedSentences = loadSentencesFromCoNLLFile(filePath);
    // tag sentences
    for (Pair<String, List<String>> sentence : loadedSentences) {
      CoreDocument annotatedSentence = new CoreDocument(pipeline.process(sentence.first()));
      int tokenIndex = 0;
      for (CoreLabel token : annotatedSentence.tokens()) {
        finalCoNLLOutput += (token.word() + "\t" + sentence.second().get(tokenIndex) + "\t" + token.ner()+"\n");
        tokenIndex++;
      }
      finalCoNLLOutput += "\n";
    }
    return finalCoNLLOutput;
  }

  /**
   * Run official CoNLL eval script on a file with predicted tags.
   * @param predictedCoNLLFile
   * @return CoNLL eval script output
   * @throws IOException
   */

  public String runEvalScript(String predictedCoNLLFile) throws IOException {
    String result = null;
    String evalCmd = NER_EVAL_SCRIPT+" "+predictedCoNLLFile;
    System.out.println(evalCmd);
    Process p = Runtime.getRuntime().exec(evalCmd);
    BufferedReader in =
        new BufferedReader(new InputStreamReader(p.getInputStream()));
    String inputLine;
    while ((inputLine = in.readLine()) != null) {
      System.out.println(inputLine);
      result += inputLine + "\n";
    }
    in.close();
    return result;
  }

  /**
   * Parse CoNLL NER eval scripts results, get overall F1 score as double.
   * @param conllEvalScriptResults
   * @return overall F1 score as a double
   */
  public double parseResults(String conllEvalScriptResults) {
    String[] resultLines = conllEvalScriptResults.split("\n");
    double foundF1Score = 0.0;
    for (String resultLine : resultLines) {
      Matcher m = FB1_Pattern.matcher(resultLine);
      // Should parse the F1 after "FB1:"
      if (m.find()) {
        String f1 = m.group(1);
        foundF1Score = Double.parseDouble(f1);
        break;
      }
    }
    return foundF1Score;
  }

  /**
   * Given a prediction file, run CoNLL eval script, parse results, return a double representing
   * overall F1.
   *
   * @param predictedCoNLLFile
   * @return overall F1 score as a double
   * @throws IOException
   */
  public double getF1Score(String predictedCoNLLFile) throws IOException {
    String evalScriptResults = runEvalScript(predictedCoNLLFile);
    return parseResults(evalScriptResults);
  }

  public void runTest(String goldFilePath, String predictedFilePath, Double expectedScore) throws IOException {
    String annotatedCoNLL = tagCoNLLFileWithPipeline(goldFilePath);
    try {
      assert(predictedFilePath.endsWith(".predicted"));
      Files.delete(Paths.get(predictedFilePath));
      System.err.println(String.format("Deleted file: %s", predictedFilePath));
    } catch (NoSuchFileException e) {
      System.err.println(String.format("Predicted file: %s not present, clean up unnecessary.", predictedFilePath));
    }
    IOUtils.writeStringToFile(annotatedCoNLL, predictedFilePath, "UTF-8");
    assertEquals(expectedScore, getF1Score(predictedFilePath), 0);
  }

  @Test
  public void testDev() throws IOException {
    runTest(devGoldFile, devPredictedFile, expectedDevScore);
  }

  @Test
  public void testTest() throws IOException {
    runTest(testGoldFile, testPredictedFile, expectedTestScore);
  }

}
