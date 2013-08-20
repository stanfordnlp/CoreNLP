package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.io.IOUtils;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A class that computes the probability of a sentence given a
 * subcategorization for one of the verbs
 * using a edu.stanford.nlp.parser.lexparser.SentenceProbabilityParser.
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class SubcatProbabilityMetric {

  private SentenceProbabilityParser parser; // LexicalizedParser parser; //


  public SubcatProbabilityMetric(String parserPath, Options op) {
    // load the grammar/parser
    op.testOptions.maxLength = 50;
    op.testOptions.doRecovery = false;
    parser = new SentenceProbabilityParser(parserPath); // new LexicalizedParser(parserPath); //
  }


  /**
   * Output log probabilities.
   */
  public ClassicCounter<Subcategory> getLogSubcatProbs(List<String> s) {
    ClassicCounter<Subcategory> result = new ClassicCounter<Subcategory>();
    s = new ArrayList<String>(s); // don't want to modify the original
    s.add(Lexicon.BOUNDARY);
    // parse the sentence
    parser.parse(s);
    double biggest = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < Subcategory.SUBCATEGORIES.size(); i++) {
      // create the start symbol for the subcategory
      String goalStr = "ROOT_" + i;
      double logProbOfSequence = Double.NEGATIVE_INFINITY;
      //      try {
      // see what the prob of making our start symbol is
      logProbOfSequence = parser.getSentenceProbability(goalStr); // parser.getPCFGScore(goalStr); //
      //        System.err.println("Score for " + goalStr + ": " + logProbOfSequence);
      //        Tree t = lp.getBestPCFGParse(false);
      //        if (t != null) t.pennPrint();
      if (logProbOfSequence > biggest) {
        biggest = logProbOfSequence;
      }
      //      } catch (Throwable e) {
      //        System.err.println("Couldn't parse instance: " + e);
      //        e.printStackTrace();
      //      }
      result.incrementCount(Subcategory.SUBCATEGORIES.get(i), logProbOfSequence);
    }
    if (biggest == Double.NEGATIVE_INFINITY) {
      throw new RuntimeException("No parses found for sentence: " + s);
    }
    return result;
  }

}

class SubcategorizingLexicon extends BaseLexicon {

  /**
   * 
   */
  private static final long serialVersionUID = -2776237669150966317L;

  public SubcategorizingLexicon(Index<String> wordIndex, Index<String> tagIndex) {
    super(wordIndex, tagIndex);
  }

  @Override
  public float score(IntTaggedWord iTW, int loc, String wordString, String featureSpec) {
    String tagString = tagIndex.get(iTW.tag());
    boolean tagIsMarked = tagString.lastIndexOf('_') >= 0; // expensive
    boolean wordIsMarked = wordString.lastIndexOf('^') >= 0; // expensive
    if (wordIsMarked && !tagIsMarked) {
      //      System.err.println("Not allowing word/tag: " + wordString + "/" + tagString);
      return Float.NEGATIVE_INFINITY;
    }
    if (tagIsMarked && !wordIsMarked) {
      //      System.err.println("Not allowing word/tag: " + wordString + "/" + tagString);
      return Float.NEGATIVE_INFINITY;
    }
    // let him do the smoothing otherwise
    return super.score(iTW, loc, wordString, featureSpec);
  }

}
