package edu.stanford.nlp.tagger;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.WordFactory;
import edu.stanford.nlp.tagger.maxent.TestSentence;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.*;

import java.io.Reader;
import java.util.ArrayList;


/**
 * Tags text from the Penn Treebank.  Uses Kristina's "MaxEnt" or conditional
 * loglinear tagger and the <code>trees</code> for accessing the Treebank.
 *
 * @author Christopher Manning
 */
public class TreebankTaggerTester {

  private TreebankTaggerTester() {
  }


  /**
   * Tag text and test results from the Penn Treebank.  Prints the
   * treebank tagged text (from the trees), and where the tagger output
   * differs, prints the tagger tag <i>after</i> the treebank tag, with a
   * trailing underscore.  Output is one sentence per line.
   *
   * @param args An array of arguments. Usage:<br>
   *             <code>java edu.stanford.nlp.tagger.TreebankTaggerTester
   *             modelFileName treeFilesPath
   *             </code><br>
   *             There should be a working pre-trained tagger in
   *             <code>/u/nlp/data/tagger.params/wsj0-21.holder</code>
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("usage: java edu.stanford.nlp.tagger.TreebankTaggerTester modelFileName treeFilesPath");
      System.exit(0);
    }

    Treebank treebank = new DiskTreebank(new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in, new LabeledScoredTreeFactory(new WordFactory()), new BobChrisTreeNormalizer());
      }
    });
    treebank.loadPath(args[1]);

    MaxentTagger tagger = new MaxentTagger(args[0]);
    TestSentence ts = new TestSentence(tagger);

    int numRight = 0, numWrong = 0;
    for (Tree t : treebank) {
      ArrayList<Word> sent = t.yieldWords();
      ArrayList<TaggedWord> tsent = ts.tagSentence(sent, false);
      ArrayList<TaggedWord> gsent = t.taggedYield();
      for (int i = 0; i < gsent.size(); i++) {
        String word = gsent.get(i).word();
        String gtag = gsent.get(i).tag();
        String ttag = tsent.get(i).tag();
        if (gtag.equals(ttag)) {
          System.out.print(word + '_' + gtag + ' ');
          numRight++;
        } else {
          System.out.print(word + '_' + gtag + '_' + ttag + "_ ");
          numWrong++;
        }
      }
      System.out.println();
    }
    System.out.println();
    System.out.println();
    java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(3);
    System.out.println((numRight + numWrong) + " words tagged");
    System.out.println(numRight + " correct tags (" + nf.format((numRight * 100.0) / (numRight + numWrong)) + "%)");
    System.out.println(numWrong + " incorrect tags (" + nf.format((numWrong * 100.0) / (numRight + numWrong)) + "%)");
  }

}
