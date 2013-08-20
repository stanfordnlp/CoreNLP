package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.util.Index;

import java.util.Iterator;
import java.util.List;

class TagScorer implements Scorer {

  protected Lexicon lex;

  protected List words;

  protected double[][] iScore;
  protected double[][] oScore;

  protected final Index<String> wordIndex;


  protected void buildScores() {
    // find the best tags
    double[] bestTag = new double[words.size()];
    for (int pos = 0; pos < words.size(); pos++) {
      // get all taggings
      bestTag[pos] = Double.NEGATIVE_INFINITY;
      for (Iterator tI = lex.ruleIteratorByWord(words.get(pos).toString(), pos, null); tI.hasNext();) {
        IntTaggedWord ur = (IntTaggedWord) tI.next();
        double score = lex.score(ur, pos, wordIndex.get(ur.word), null);
        if (score > bestTag[pos]) {
          bestTag[pos] = score;
        }
      }
    }
    // aggregate spans
    iScore = new double[words.size() + 1][words.size() + 1];
    for (int start = 0; start < words.size(); start++) {
      double bestSum = 0;
      for (int end = start + 1; end <= words.size(); end++) {
        bestSum += bestTag[end - 1];
        iScore[start][end] = bestSum;
      }
    }
    oScore = new double[words.size() + 1][words.size() + 1];
    for (int start = 0; start < words.size(); start++) {
      for (int end = start + 1; end <= words.size(); end++) {
        oScore[start][end] = iScore[0][words.size()] - iScore[start][end];
      }
    }
  }


  public double oScore(Edge edge) {
    return oScore[edge.start][edge.end];
  }

  public double iScore(Edge edge) {
    return iScore[edge.start][edge.end];
  }

  public boolean iPossible(Hook hook) {
    return true;
  }

  public boolean oPossible(Hook hook) {
    return true;
  }

  public boolean parse(List words) {
    this.words = words;
    buildScores();
    return true;
  }

  public TagScorer(Lexicon lex, Index<String> wordIndex) {
    this.lex = lex;
    this.wordIndex = wordIndex;
  }

}


