package edu.stanford.nlp.trees.international.pennchinese;


/** A CTB TreeReaderFactory that deletes empty nodes.
 *  @author Christopher Manning
 */
public class NoEmptiesCTBTreeReaderFactory extends CTBTreeReaderFactory {

  public NoEmptiesCTBTreeReaderFactory() {
    super(new CTBErrorCorrectingTreeNormalizer(false, false, false, false), false);
  }

}
