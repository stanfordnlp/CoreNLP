package edu.stanford.nlp.dcoref;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;
import edu.stanford.nlp.util.StringUtils;

public class DcorefSlowITest extends TestCase {

  static void makePropsFile(String path, String workDir) throws IOException {
    FileWriter fout = new FileWriter(path);
    BufferedWriter bout = new BufferedWriter(fout);

    bout.write("annotators = pos, lemma, ner, parse");
    bout.newLine();
    // WordNet is moved to more
    bout.write("dcoref.sievePasses = MarkRole, DiscourseMatch, ExactStringMatch, RelaxedExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, RelaxedHeadMatch, PronounMatch");
    //bout.write("dcoref.sievePasses = MarkRole, DiscourseMatch, ExactStringMatch, RelaxedExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, AliasMatch, RelaxedHeadMatch, LexicalChainMatch, PronounMatch");
    bout.newLine();
    bout.write("dcoref.score = true");
    bout.newLine();
    bout.write("dcoref.postprocessing = true");
    bout.newLine();
    bout.write("dcoref.maxdist = -1");
    bout.newLine();
    bout.write("dcoref.replicate.conll = true");
    bout.newLine();
    bout.write("dcoref.conll.scorer = /scr/nlp/data/conll-2011/scorer/v4/scorer.pl");
    bout.newLine();
    bout.write("dcoref.conll2011 = /scr/nlp/data/conll-2011/v2/data/dev/data/english/annotations");
    bout.newLine();
    bout.write("dcoref.logFile = "+workDir + File.separator + "log.txt");
    bout.newLine();
    bout.flush();
    fout.close();
  }

  public void testDcorefCoNLLResult() throws Exception {
    final File WORK_DIR_FILE = File.createTempFile("DcorefITest", "");
    final String WORK_DIR = WORK_DIR_FILE.getPath();
    final String PROPS_PATH = WORK_DIR + File.separator + "coref.properties";

    System.out.println("Working in directory " + WORK_DIR);

    WORK_DIR_FILE.delete();
    WORK_DIR_FILE.mkdir();
    WORK_DIR_FILE.deleteOnExit();

    makePropsFile(PROPS_PATH, WORK_DIR);
    System.out.println("Made props file " + PROPS_PATH);

    Properties props = StringUtils.argsToProperties(new String[]{"-props", PROPS_PATH});
    SieveCoreferenceSystem corefSystem = new SieveCoreferenceSystem(props);

    String returnMsg = runCorefSystem(corefSystem, props, WORK_DIR);

    double finalScore = getFinalScore(returnMsg);
    System.out.println(returnMsg);
    System.out.println("Final Score: (MUC+B^3+ceafe)/3 = "+(new DecimalFormat("#.##")).format(finalScore));

    assertEquals(59.2, finalScore, 1.0);
  }

  private static String runCorefSystem(SieveCoreferenceSystem corefSystem, Properties props, String WORK_DIR) throws Exception {

    String conllOutputMentionGoldFile = WORK_DIR + File.separator+"conlloutput.gold.txt";
    String conllOutputMentionCorefPredictedFile = WORK_DIR + File.separator+ "conlloutput.coref.predicted.txt";

    PrintWriter writerGold = new PrintWriter(new FileOutputStream(conllOutputMentionGoldFile));
    PrintWriter writerPredictedCoref = new PrintWriter(new FileOutputStream(conllOutputMentionCorefPredictedFile));

    MentionExtractor mentionExtractor = new CoNLLMentionExtractor(corefSystem.dictionaries(), props, corefSystem.semantics());

    Document document;
    while((document = mentionExtractor.nextDoc()) != null) {
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

  /** get the average score: (MUC + B^3 + CEAF_E)/3 */
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
