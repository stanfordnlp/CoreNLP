package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.optimization.CmdEvaluator;
import edu.stanford.nlp.stats.MultiClassChunkEvalStats;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.Collection;
import java.util.List;

/**
 * Evaluates a CRFClassifier on a set of data.
 * This can be called by QNMinimizer periodically.
 * If evalCmd is set, it runs the command line specified by evalCmd,
 * otherwise it does evaluation internally.
 * NOTE: when running conlleval with exec on Linux, linux will first
 *          fork process by duplicating memory of current process.  So if the
 *          JVM has lots of memory, it will all be duplicated when
 *          child process is initially forked, which can be unfortunate.
 *
 * @author Angel Chang
 */
public class CRFClassifierEvaluator<IN extends CoreMap> extends CmdEvaluator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CRFClassifierEvaluator.class);

  private final CRFClassifier<IN> classifier;
  /** NOTE: Default uses -r, specify without -r if IOB. */
  private String cmdStr = "/u/nlp/bin/conlleval -r";
  private String[] cmd;

  // TODO: Use data structure to hold data + features
  // Cache already featurized documents
  // Original object bank
  Collection<List<IN>> data;
  // Featurized data
  List<Triple<int[][][], int[], double[][][]>> featurizedData;

  public CRFClassifierEvaluator(String description,
                                CRFClassifier<IN> classifier,
                                Collection<List<IN>> data,
                                List<Triple<int[][][], int[], double[][][]>> featurizedData) {
    this.description = description;
    this.classifier = classifier;
    this.data = data;
    this.featurizedData = featurizedData;
    cmd = getCmd(cmdStr);
    saveOutput = true;
  }

  public CRFClassifierEvaluator(String description,
                                CRFClassifier<IN> classifier) {
    this.description = description;
    this.classifier = classifier;
    saveOutput = true;
  }

  /**
   * Set the data to test on
   */
  public void setTestData(Collection<List<IN>> data, List<Triple<int[][][], int[], double[][][]>> featurizedData) {
    this.data = data;
    this.featurizedData = featurizedData;
  }

  /**
   * Set the evaluation command (set to null to skip evaluation using command line)
   * @param evalCmd
   */
  public void setEvalCmd(String evalCmd) {
    log.info("setEvalCmd to " + evalCmd);
    this.cmdStr = evalCmd;
    if (cmdStr != null) {
      cmdStr = cmdStr.trim();
      if (cmdStr.isEmpty()) { cmdStr = null; }
    }
    cmd = getCmd(cmdStr);
  }

  @Override
  public void setValues(double[] x)
  {
    classifier.updateWeightsForTest(x);
  }

  @Override
  public String[] getCmd()
  {
    return cmd;
  }

  private double interpretCmdOutput() {
    String output = getOutput();
    String[] parts = output.split("\\s+");
    int fScoreIndex = 0;
    for (; fScoreIndex < parts.length; fScoreIndex++)
      if (parts[fScoreIndex].equals("FB1:"))
        break;
    fScoreIndex += 1;
    if (fScoreIndex < parts.length)
      return Double.parseDouble(parts[fScoreIndex]);
    else {
      log.error("in CRFClassifierEvaluator.interpretCmdOutput(), cannot find FB1 score in output:\n"+output);
      return -1;
    }
  }

  @Override
  public void outputToCmd(OutputStream outputStream)
  {
    try {
      PrintWriter pw = IOUtils.encodedOutputStreamPrintWriter(outputStream, null, true);
      classifier.classifyAndWriteAnswers(data, featurizedData, pw,
                                         classifier.makeReaderAndWriter());
    } catch (IOException ex) {
      throw new RuntimeIOException(ex);
    }
  }

  @Override
  public double evaluate(double[] x) {
    double score; // initialized below
    setValues(x);
    if (getCmd() != null) {
      evaluateCmd(getCmd());
      score = interpretCmdOutput();
    } else {
      try {
        // TODO: Classify in memory instead of writing to tmp file
        File f = File.createTempFile("CRFClassifierEvaluator","txt");
        f.deleteOnExit();
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(f));
        PrintWriter pw = IOUtils.encodedOutputStreamPrintWriter(outputStream, null, true);
        classifier.classifyAndWriteAnswers(data, featurizedData, pw,
                                           classifier.makeReaderAndWriter());
        outputStream.close();
        BufferedReader br = new BufferedReader(new FileReader(f));
        MultiClassChunkEvalStats stats = new MultiClassChunkEvalStats("O");
        score = stats.score(br, "\t");
        log.info(stats.getConllEvalString());
        f.delete();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
    return score;
  }

}
