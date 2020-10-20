package edu.stanford.nlp.international.french.scripts; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;

/**
 * Places predicted morphological analyses in the leaves of gold FTB parse trees.
 *
 * @author Spence Green
 *
 */
public final class MungeTreesWithMorfetteAnalyses  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MungeTreesWithMorfetteAnalyses.class);

  private static class MorfetteFileIterator implements Iterator<List<CoreLabel>> {

    private BufferedReader reader;
    private List<CoreLabel> nextList;
    private int lineId = 0;

    public MorfetteFileIterator(String filename) {
      try {
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
        primeNext();
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }

    private void primeNext() {
      try {
        nextList = new ArrayList<>(40);
        for (String line; (line = reader.readLine()) != null; ++lineId) {
          line = line.trim();
          if (line.equals("")) {
            ++lineId;
            break;
          }
          String[] toks = line.split("\\s+");
          if (toks.length != 3) {
            log.info(toks.length);
            log.info(line);
            log.info(lineId);
            throw new RuntimeException(String.format("line %d: Morfette format is |word lemma tag|: |%s|", lineId, line));
          }
          CoreLabel cl = new CoreLabel();
          String word = toks[0];
          String lemma = toks[1];
          String tag = toks[2];
          cl.setWord(word);
          cl.setValue(word);
          cl.setLemma(lemma);
          cl.setTag(tag);
          nextList.add(cl);
        }

        // File is exhausted
        if (nextList.size() == 0) {
          reader.close();
          nextList = null;
        }

      } catch (IOException e) {
        System.err.printf("Problem reading file at line %d%n", lineId);
        e.printStackTrace();
        nextList = null;
      }
    }

    @Override
    public boolean hasNext() {
      return nextList != null;
    }

    @Override
    public List<CoreLabel> next() {
      if (hasNext()) {
        List<CoreLabel> next = nextList;
        primeNext();
        return next;
      }
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.printf("Usage: java %s tree_file morfette_tnt_file%n", MungeTreesWithMorfetteAnalyses.class.getName());
      System.exit(-1);
    }

    String treeFile = args[0];
    String morfetteFile = args[1];
    TreeReaderFactory trf = new FrenchTreeReaderFactory();
    try {
      TreeReader tr = trf.newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8")));
      Iterator<List<CoreLabel>> morfetteItr = new MorfetteFileIterator(morfetteFile);
      for (Tree tree; (tree = tr.readTree()) != null && morfetteItr.hasNext();) {
        List<CoreLabel> analysis = morfetteItr.next();
        List<Label> yield = tree.yield();
        assert analysis.size() == yield.size();

        int yieldLen = yield.size();
        for (int i = 0; i < yieldLen; ++i) {
          CoreLabel tokenAnalysis = analysis.get(i);
          Label token = yield.get(i);
          String lemma = getLemma(token.value(), tokenAnalysis.lemma());
          String newLeaf = String.format("%s%s%s%s%s", token.value(),
              MorphoFeatureSpecification.MORPHO_MARK,
              lemma,
              MorphoFeatureSpecification.LEMMA_MARK,
              tokenAnalysis.tag());
          ((CoreLabel) token).setValue(newLeaf);
        }
        System.out.println(tree.toString());
      }

      if (tr.readTree() != null || morfetteItr.hasNext()) {
        log.info("WARNING: Uneven input files!");
      }

      tr.close();

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static final Pattern pIsPunct = Pattern.compile("\\p{Punct}+");
  private static final Pattern pAllUpper = Pattern.compile("\\p{Upper}+");
  private static String getLemma(String rawToken, String lemma) {
    boolean isUpper = Character.isUpperCase(rawToken.charAt(0));
    boolean isAllUpper = pAllUpper.matcher(rawToken).matches();
    boolean isParen = rawToken.equals("-RRB-") || rawToken.equals("-LRB-") || rawToken.equals("(") || rawToken.equals(")");
    boolean isPunc = pIsPunct.matcher(rawToken).matches();
    if (isParen || isPunc || isAllUpper) {
      return rawToken;
    }
    if (isUpper) {
      Character firstChar = Character.toUpperCase(lemma.charAt(0));
      lemma = firstChar + lemma.substring(1, lemma.length());
    }
    return lemma;
  }
}
