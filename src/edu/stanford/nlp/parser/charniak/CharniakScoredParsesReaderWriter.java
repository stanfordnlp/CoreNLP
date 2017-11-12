package edu.stanford.nlp.parser.charniak;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.util.IterableIterator;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.Timing;

/**
 * Utility routines for printing/reading scored parses for the Charniak Parser
 *
 * @author Angel Chang
 */
public class CharniakScoredParsesReaderWriter {
  private final static Logger logger = Logger.getLogger(CharniakScoredParsesReaderWriter.class.getName());

  private final static Pattern wsDelimiter = Pattern.compile("\\s+");

  /**
   * Reads scored parses from the charniak parser
   *
   * File format of the scored parses
   * <# of parses>\t<sentenceid>
   * <score>
   * <parse>
   * <score>
   * <parse>
   * ...
   *
   * @param filename  - File to read parses from
   * @return iterable with list of scored parse trees
   */
  public Iterable<List<ScoredObject<Tree>>> readScoredTrees(String filename)
  {
    try {
      ScoredParsesIterator iter = new ScoredParsesIterator(filename);
      return new IterableIterator<>(iter);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Reads scored parses from the charniak parser
   *
   * @param inputDesc - Description of input used in log messages
   * @param br - input reader
   * @return iterable with list of scored parse trees
   */
  public Iterable<List<ScoredObject<Tree>>> readScoredTrees(String inputDesc, BufferedReader br)
  {
    ScoredParsesIterator iter = new ScoredParsesIterator(inputDesc, br);
    return new IterableIterator<>(iter);
  }

  /**
   * Convert string representing scored parses (in the charniak parser output format)
   *   to list of scored parse trees
   * @param parseStr
   * @return list of scored parse trees
   */
  public List<ScoredObject<Tree>> stringToParses(String parseStr)
  {
    try {
      BufferedReader br = new BufferedReader(new StringReader(parseStr));
      Iterable<List<ScoredObject<Tree>>> trees = readScoredTrees("", br);
      List<ScoredObject<Tree>> res = null;
      if (trees != null) {
        Iterator<List<ScoredObject<Tree>>> iter = trees.iterator();
        if (iter != null && iter.hasNext()) {
          res = iter.next();
        }
      }
      br.close();
      return res;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }


  /**
   * Convert list of scored parse trees to string representing scored parses
   *   (in the charniak parser output format)
   * @param parses - list of scored parse trees
   * @return string representing scored parses
   */
  public String parsesToString(List<ScoredObject<Tree>> parses)
  {
    if (parses == null) return null;
    StringOutputStream os = new StringOutputStream();
    PrintWriter pw = new PrintWriter(os);
    printScoredTrees(pw, 0, parses);
    pw.close();
    return os.toString();
  }

  /**
   * Print scored parse trees in format used by charniak parser
   * @param trees - trees to output
   * @param filename - file to output to
   */
  public void printScoredTrees(Iterable<List<ScoredObject<Tree>>> trees, String filename)
  {
    try {
      PrintWriter pw = IOUtils.getPrintWriter(filename);
      int i = 0;
      for (List<ScoredObject<Tree>> treeList:trees) {
        printScoredTrees(pw, i, treeList);
        i++;
      }
      pw.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Print scored parse trees for one sentence in format used by charniak parser
   *
   * @param pw - printwriter
   * @param id - sentence id
   * @param trees - trees to output
   */
  public void printScoredTrees(PrintWriter pw, int id, List<ScoredObject<Tree>> trees) {
    pw.println(trees.size() + "\t" + id);
    trees
        .stream()
        .map(
            scoredTree -> {
              pw.println(scoredTree.score());
              return scoredTree;
            })
        .forEach(
            scoredTree -> {
              pw.println(scoredTree.object());
            });
    }

  private static class ScoredParsesIterator extends AbstractIterator<List<ScoredObject<Tree>>> {
    String inputDesc;
    BufferedReader br;
    List<ScoredObject<Tree>> next;
    Timing timing;
    int processed = 0;
    boolean done = false;
    boolean closeBufferNeeded = true;
    boolean expectConsecutiveSentenceIds = true;
    int lastSentenceId = -1;

    private ScoredParsesIterator(String filename) throws IOException {
      this(filename, IOUtils.getBufferedFileReader(filename));
    }


    private ScoredParsesIterator(String inputDesc, BufferedReader br) {
      this.inputDesc = inputDesc;
      this.br = br;
      logger.info("Reading cached parses from " + inputDesc);
      timing = new Timing();
      timing.start();
      next = getNext();
      done = next == null;
    }

    private List<ScoredObject<Tree>> getNext()
    {
      try {
        String line;
        int parsesExpected = 0;
        int sentenceId = lastSentenceId;
        ScoredObject<Tree> curParse = null;
        Double score = null;
        List<ScoredObject<Tree>> curParses = null;
        while ((line = br.readLine()) != null) {
          line = line.trim();
          if (line.length() > 0) {
            if (parsesExpected == 0) {
              // Finished processing parses
              String[] fields = wsDelimiter.split(line, 2);
              parsesExpected = Integer.parseInt(fields[0]);
              sentenceId = Integer.parseInt(fields[1]);
              if (expectConsecutiveSentenceIds) {
               if (sentenceId != lastSentenceId+1) {
                  if (lastSentenceId < sentenceId) {
                    StringBuilder sb = new StringBuilder("Missing sentences");
                    for (int i = lastSentenceId+1; i < sentenceId; i++) {
                      sb.append(" ").append(i);
                    }
                    logger.warning(sb.toString());
                  } else {
                    logger.warning("sentenceIds are not increasing (last="
                          + lastSentenceId + ", curr=" + sentenceId + ")");
                  }
                }
              }
              lastSentenceId = sentenceId;
              curParses = new ArrayList<>(parsesExpected);
            } else {
              if (score == null) {
                // read score
                score = Double.parseDouble(wsDelimiter.split(line, 2)[0]);
              } else {
                // Reading a parse
                curParse = new ScoredObject<>(Trees.readTree(line), score);
                curParses.add(curParse);
                curParse = null;
                score = null;
                parsesExpected--;
                if (parsesExpected == 0) {
                  return curParses;
                }
              }
            }
          }
        }
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      return null;
    }

    public boolean hasNext()
    {
      return !done;
    }

    public List<ScoredObject<Tree>> next()
    {
      if (!done) {
        List<ScoredObject<Tree>> cur = next;
        next = getNext();
        processed++;
        if (next == null) {
          logger.finer("Read " + processed + " trees, from "
                  + inputDesc + " in " + timing.toSecondsString() + " secs");
          done = true;
          if (closeBufferNeeded) {
            try { br.close();  } catch (IOException ex) {};
          }
        }
        return cur;
      } else {
        throw new NoSuchElementException("No more elements from " + inputDesc);
      }
    }
  }

}
