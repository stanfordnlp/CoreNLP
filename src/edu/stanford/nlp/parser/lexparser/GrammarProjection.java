package edu.stanford.nlp.parser.lexparser;

/** Maps between the states of a more split and less split grammar.
 *  (Sort of a precursor to the idea of "coarse-to-fine" parsing.)
 *
 *  @author Dan Klein
 */
public interface GrammarProjection {

  int project(int state);

  UnaryGrammar sourceUG();

  BinaryGrammar sourceBG();

  UnaryGrammar targetUG();

  BinaryGrammar targetBG();

}

