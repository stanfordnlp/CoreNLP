package edu.stanford.nlp.dcoref;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

public class DcorefSlowITest extends TestCase {

  protected void makePropsFile(String path, String workDir, String scorer) throws IOException {
    PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(path)));

    pw.println("annotators = pos, lemma, ner, parse");
    // WordNet is moved to more
    pw.println("dcoref.sievePasses = MarkRole, DiscourseMatch, ExactStringMatch, RelaxedExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, RelaxedHeadMatch, PronounMatch");
    // pw.println("dcoref.sievePasses = MarkRole, DiscourseMatch, ExactStringMatch, RelaxedExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, AliasMatch, RelaxedHeadMatch, LexicalChainMatch, PronounMatch");
    pw.println("dcoref.score = true");
    pw.println("dcoref.postprocessing = true");
    pw.println("dcoref.maxdist = -1");
    pw.println("dcoref.replicate.conll = true");
    pw.println("dcoref.conll.scorer = " + scorer);
    pw.println(String.format("dcoref.conll2011 = %s/conll-2011/v2/data/dev/data/english/annotations", TestPaths.testHome()));
    pw.println("dcoref.logFile = "+workDir + File.separator + "log.txt");
    pw.close();
  }

  public void testDcorefCoNLLResultV4() throws Exception {
    double finalScore = runDcoref(String.format("%s/conll-2012/scorer/v4/scorer.pl", TestPaths.testHome()));
    System.out.printf("Final Score (CoNLL 2012, scorer v4): (MUC+B^3+ceafe)/3 = %.2f%n", finalScore);
    assertEquals(59.3, finalScore, 0.3); // 2016-07: 59.45
  }

  public void testDcorefCoNLLResultV801() throws Exception {
    double finalScore = runDcoref(String.format("%s/conll-2012/scorer/v8.01/scorer.pl", TestPaths.testHome()));
    System.out.printf("Final Score (CoNLL 2012, scorer v8): (MUC+B^3+ceafe)/3 = %.2f%n", finalScore);
    assertEquals(54.0, finalScore, 0.3); // 2016-07: 54.13
  }

  protected double runDcoref(String scorer) throws Exception {
    final File WORK_DIR_FILE = File.createTempFile("DcorefITest", "");
    final String WORK_DIR = WORK_DIR_FILE.getPath();
    final String PROPS_PATH = WORK_DIR + File.separator + "coref.properties";

    System.err.println("Working in directory " + WORK_DIR);

    if (WORK_DIR_FILE.exists()) {
      if ( ! WORK_DIR_FILE.delete()) {
        throw new IOException("Couldn't delete existing work dir " + WORK_DIR_FILE);
      }
    }
    if ( ! WORK_DIR_FILE.mkdir()) {
      throw new IOException("Couldn't create new work dir " + WORK_DIR_FILE);
    }
    WORK_DIR_FILE.deleteOnExit();

    makePropsFile(PROPS_PATH, WORK_DIR, scorer);
    System.out.println("Made props file " + PROPS_PATH);

    Properties props = StringUtils.argsToProperties("-props", PROPS_PATH);
    SieveCoreferenceSystem corefSystem = new SieveCoreferenceSystem(props);

    String returnMsg = runCorefSystem(corefSystem, props, WORK_DIR);

    System.out.println(returnMsg);
    return getFinalScore(returnMsg);
  }

  private static String runCorefSystem(SieveCoreferenceSystem corefSystem, Properties props, String WORK_DIR) throws Exception {

    String conllOutputMentionGoldFile = WORK_DIR + File.separator+"conlloutput.gold.txt";
    String conllOutputMentionCorefPredictedFile = WORK_DIR + File.separator+ "conlloutput.coref.predicted.txt";

    PrintWriter writerGold = new PrintWriter(new FileOutputStream(conllOutputMentionGoldFile));
    PrintWriter writerPredictedCoref = new PrintWriter(new FileOutputStream(conllOutputMentionCorefPredictedFile));

    MentionExtractor mentionExtractor = new CoNLLMentionExtractor(corefSystem.dictionaries(), props, corefSystem.semantics());

    for (Document document; (document = mentionExtractor.nextDoc()) != null; ) {
      document.extractGoldCorefClusters();
      SieveCoreferenceSystem.printConllOutput(document, writerGold, true);
      corefSystem.coref(document);  // Do Coreference Resolution
      SieveCoreferenceSystem.printConllOutput(document, writerPredictedCoref, false, true);
    }
    writerGold.close();
    writerPredictedCoref.close();

    String summary = SieveCoreferenceSystem.getConllEvalSummary(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionCorefPredictedFile);

    return summary;
  }

  /** get the average score: (MUC + B^3 + CEAF_E)/3. */
  private static double getFinalScore(String summary) {
    Pattern f1 = Pattern.compile("Coreference:.*F1: (.*)%");
    Matcher f1Matcher = f1.matcher(summary);
    double[] F1s = new double[5];
    int i = 0;
    while (f1Matcher.find()) {
      F1s[i++] = Double.parseDouble(f1Matcher.group(1));
    }
    return (F1s[0]+F1s[1]+F1s[3])/3;
  }

}
