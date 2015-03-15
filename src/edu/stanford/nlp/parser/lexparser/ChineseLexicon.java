package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.util.Index;


/**
 * A lexicon class for Chinese.  Extends the (English) BaseLexicon class,
 * overriding its score and train methods to include a
 * ChineseUnknownWordModel.
 *
 * @author Roger Levy
 */
public class ChineseLexicon extends BaseLexicon {

  private static final long serialVersionUID = -7836464391021114960L;

  public final boolean useCharBasedUnknownWordModel;
  // public static final boolean useMaxentUnknownWordModel;
  public final boolean useGoodTuringUnknownWordModel;

  //private ChineseUnknownWordModel unknown;
  // private ChineseMaxentLexicon cml;
  private static final int STEPS = 1;
  private RandomWalk probRandomWalk;


  public ChineseLexicon(Options op, ChineseTreebankParserParams params, Index<String> wordIndex, Index<String> tagIndex) {
    super(op, wordIndex, tagIndex);
    useCharBasedUnknownWordModel = params.useCharBasedUnknownWordModel;
    useGoodTuringUnknownWordModel = params.useGoodTuringUnknownWordModel;
    // if (useMaxentUnknownWordModel) {
    //  cml = new ChineseMaxentLexicon();
    // } else {
    //unknown = new ChineseUnknownWordModel();
    //this.setUnknownWordModel(new ChineseUnknownWordModel(op));
    // this.getUnknownWordModel().setLexicon(this);
    // }
  }



  @Override
  public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
    double c_W = seenCounter.getCount(iTW);
    boolean seen = (c_W > 0.0);

    if (seen) {
      return super.score(iTW, loc, word, featureSpec);
    } else {
      float score;
      // if (useMaxentUnknownWordModel) {
      //  score = cml.score(iTW, 0);
      // } else {
      score = this.getUnknownWordModel().score(iTW, loc, 0.0, 0.0, 0.0, word); // ChineseUnknownWordModel doesn't use the final three params
      // }
      return score;
    }
  }
}
