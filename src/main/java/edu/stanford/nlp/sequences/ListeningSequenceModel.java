package edu.stanford.nlp.sequences;

/** This is simply a conjunctive interface for something that is
 *  a SequenceModel and a SequenceListener. This is useful to have
 *  because models used in Gibbs sampling have to implement both
 *  these interfaces.
 *
 *  @author Christopher Manning
 */
public interface ListeningSequenceModel extends SequenceModel, SequenceListener {

}
