package edu.stanford.nlp.parser.charniak;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Runs the Charniak parser via the command line.
 *
 * @author Angel Chang
 */
public class CharniakParser {

  private static final Redwood.RedwoodChannels logger = Redwood.channels(CharniakParser.class);

  private static final String CHARNIAK_DIR = "/u/nlp/packages/bllip-parser/";
  // note: this is actually the parser+reranker (will use 2 CPUs)
  private static final String CHARNIAK_BIN = "./parse-50best.sh";

  private String dir = CHARNIAK_DIR;
  private String parserExecutable = CHARNIAK_BIN;

  /** Do not parse sentences larger than this sentence length */
  private int maxSentenceLength = 400;
  private int beamSize = 0;

  public CharniakParser() {}

  public CharniakParser(String dir, String parserExecutable) {
    this.parserExecutable = parserExecutable;
    this.dir = dir;
  }

  public int getBeamSize() {
    return beamSize;
  }

  public void setBeamSize(int beamSize) {
    this.beamSize = beamSize;
  }

  public int getMaxSentenceLength() {
    return maxSentenceLength;
  }

  public void setMaxSentenceLength(int maxSentenceLength) {
    this.maxSentenceLength = maxSentenceLength;
  }

  public Tree getBestParse(List<? extends HasWord> sentence) {
    ScoredObject<Tree> scoredParse = getBestScoredParse(sentence);
    return (scoredParse != null)? scoredParse.object():null;
  }

  public ScoredObject<Tree> getBestScoredParse(List<? extends HasWord> sentence) {
    List<ScoredObject<Tree>> kBestParses = getKBestParses(sentence, 1);
    if (kBestParses != null) {
      return kBestParses.get(0);
    }
    return null;
  }

  public List<ScoredObject<Tree>> getKBestParses(List<? extends HasWord> sentence, int k) {
    return getKBestParses(sentence, k, true);
  }

  public List<ScoredObject<Tree>> getKBestParses(List<? extends HasWord> sentence, int k, boolean deleteTempFiles) {
    try {
      File inFile = File.createTempFile("charniak.", ".in");
      if (deleteTempFiles) inFile.deleteOnExit();
      File outFile = File.createTempFile("charniak.", ".out");
      if (deleteTempFiles) outFile.deleteOnExit();
      File errFile = File.createTempFile("charniak.", ".err");
      if (deleteTempFiles) errFile.deleteOnExit();
      printSentence(sentence, inFile.getAbsolutePath());
      runCharniak(k, inFile.getAbsolutePath(), outFile.getAbsolutePath(), errFile.getAbsolutePath());
      Iterable<List<ScoredObject<Tree>>> iter = CharniakScoredParsesReaderWriter.readScoredTrees(outFile.getAbsolutePath());
      if (deleteTempFiles) {
        inFile.delete();
        outFile.delete();
        errFile.delete();
      }
      return iter.iterator().next();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public Iterable<List<ScoredObject<Tree>>> getKBestParses(Iterable<List<? extends HasWord>> sentences, int k) {
    return getKBestParses(sentences, k, true);
  }

  public Iterable<List<ScoredObject<Tree>>> getKBestParses(Iterable<List<? extends HasWord>> sentences,
                                                           int k, boolean deleteTempFiles) {
    try {
      File inFile = File.createTempFile("charniak.", ".in");
      if (deleteTempFiles) inFile.deleteOnExit();
      File outFile = File.createTempFile("charniak.", ".out");
      if (deleteTempFiles) outFile.deleteOnExit();
      File errFile = File.createTempFile("charniak.", ".err");
      if (deleteTempFiles) errFile.deleteOnExit();
      printSentences(sentences, inFile.getAbsolutePath());
      runCharniak(k, inFile.getAbsolutePath(), outFile.getAbsolutePath(), errFile.getAbsolutePath());
      Iterable<List<ScoredObject<Tree>>> iter = CharniakScoredParsesReaderWriter.readScoredTrees(outFile.getAbsolutePath());
      if (deleteTempFiles) {
        inFile.delete();
        outFile.delete();
        errFile.delete();
      }
      return new IterableIterator<>(iter.iterator());
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void printSentence(List<? extends HasWord> sentence, String filename) {
    List<List<? extends HasWord>> sentences = new ArrayList<>();
    sentences.add(sentence);
    printSentences(sentences, filename);
  }

  public void printSentences(Iterable<List<? extends HasWord>> sentences, String filename) {
    try {
      PrintWriter pw = IOUtils.getPrintWriter(filename);
      for (List<? extends HasWord> sentence:sentences) {
        pw.print("<s> ");   // Note: Use <s sentence-id > to identify sentences
        String sentString = SentenceUtils.listToString(sentence);
        if (sentence.size() > maxSentenceLength) {
          logger.warning("Sentence length=" + sentence.size() +
                  " is longer than maximum set length " + maxSentenceLength);
          logger.warning("Long Sentence: " + sentString);
        }
        pw.print(sentString);
        pw.println(" </s>");
      }
      pw.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void runCharniak(int n, String infile, String outfile, String errfile) {
    try {
      if (n == 1) n++;  // Charniak does not output score if n = 1?

      List<String> args = new ArrayList<>();
      args.add(parserExecutable);
      args.add(infile);
      ProcessBuilder process = new ProcessBuilder(args);
      process.directory(new File(this.dir));
      PrintWriter out = IOUtils.getPrintWriter(outfile);
      PrintWriter err = IOUtils.getPrintWriter(errfile);
      SystemUtils.run(process, out, err);
      out.close();
      err.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

}
