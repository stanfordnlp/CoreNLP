package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.optimization.CmdEvaluator;
import edu.stanford.nlp.stats.MultiClassChunkEvalStats;

import java.io.*;
import java.util.List;

/**
 * Evaluates SequenceClassifier on a set of data
 * - called by QNMinimizer periodically
 * - If evalCmd is set, runs command line specified by evalCmd
 *                      otherwise does evaluation internally
 *   NOTE: when running conlleval with exec on Linux, linux will first
 *          fork process by duplicating memory of current process.  So if
 *          JVM has lots of memory, it will all be duplicated when
 *          child process is initially forked.
 * @author Angel Chang
 */
public class SequenceClassifierEvaluator extends CmdEvaluator {

  private QueriableSequenceModelFactory modelFactory;
  // NOTE: Defalt uses -r, specify without -r if IOB
  private String cmdStr = "/u/nlp/bin/conlleval -r";
  private String[] cmd;

  // TODO: Use data structure to hold data + features
  // Cache already featurized documents
  // Original object bank
//  Collection<List<CoreLabel>> data;
  MultiDocumentCliqueDataset dataset;

  public SequenceClassifierEvaluator(String description,
                                     QueriableSequenceModelFactory modelFactory,
                                     MultiDocumentCliqueDataset dataset)
  {
    this.description = description;
    this.modelFactory = modelFactory;
    this.dataset = dataset;
    cmd = getCmd(cmdStr);
  }

  public SequenceClassifierEvaluator(String description,
                                     QueriableSequenceModelFactory modelFactory)
  {
    this.description = description;
    this.modelFactory = modelFactory;
  }

  /**
   * Set the data to test on
   */
  public void setTestData(MultiDocumentCliqueDataset dataset)
  {
    this.dataset = dataset;
  }

  /**
   * Set the evaluation command (set to null to skip evaluation using command line)
   * @param evalCmd
   */
  public void setEvalCmd(String evalCmd)
  {
    this.cmdStr = evalCmd;
    if (cmdStr != null) {
      cmdStr = cmdStr.trim();
      if (cmdStr.length() == 0) { cmdStr = null; }
    }
    cmd = getCmd(cmdStr);
  }

  public void setValues(double[] x)
  {
    modelFactory.setWeights(x);
  }

  public String[] getCmd()
  {
    return cmd;
  }

  public void outputToCmd(OutputStream outputStream)
  {
    try {
      classifyAndWriteAnswers(outputStream);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void classifyAndWriteAnswers(OutputStream outputStream)
  {
    PrintWriter pw = new PrintWriter(outputStream);
    for (CliqueDataset c:dataset.getDatasets()) {
      modelFactory.test(c);
      for (CoreLabel label:c.sourceDoc) {
        pw.println(label.word() + "\t" + label.get(CoreAnnotations.GoldAnswerAnnotation.class) + "\t"
            + label.get(CoreAnnotations.AnswerAnnotation.class));
      }
      pw.println();
    }
    pw.flush();
  }

  public double evaluate(double[] x) {
    double score = 0;
    setValues(x);
    if (getCmd() != null) {
      evaluateCmd(getCmd());
    } else {
      try {
        MultiClassChunkEvalStats stats = new MultiClassChunkEvalStats("O");
        stats.clearCounts();
        for (CliqueDataset c:dataset.getDatasets()) {
          List<String> gold = modelFactory.getGold(c);
          List<String> guesses = modelFactory.getGuesses(c);
          stats.addGuesses(guesses, gold);
        }
        score = stats.score();

        System.err.println(stats.getConllEvalString());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
    return score;
  }

}
