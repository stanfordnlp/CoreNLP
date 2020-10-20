package edu.stanford.nlp.parser.lexparser;

/** @author Dan Klein */
class NullGrammarProjection implements GrammarProjection {
  UnaryGrammar ug;
  BinaryGrammar bg;

  public int project(int state) {
    return state;
  }

  public UnaryGrammar sourceUG() {
    return ug;
  }

  public BinaryGrammar sourceBG() {
    return bg;
  }

  public UnaryGrammar targetUG() {
    return ug;
  }

  public BinaryGrammar targetBG() {
    return bg;
  }

  NullGrammarProjection(BinaryGrammar bg, UnaryGrammar ug) {
    this.ug = ug;
    this.bg = bg;
  }
}
