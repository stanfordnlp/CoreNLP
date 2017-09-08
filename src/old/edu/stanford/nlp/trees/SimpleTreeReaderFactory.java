package old.edu.stanford.nlp.trees;

import java.io.Reader;

/**
 * This class implements a simple default <code>TreeReaderFactory</code>.
 * <p/>
 * <i>NB: A SimpleTree stores tree geometries but no node labels.  Make sure
 * this is what you really want.</i>
 *
 * @author Christopher Manning
 */
public class SimpleTreeReaderFactory implements TreeReaderFactory {

  /**
   * Returns a new <code>TreeReader</code>.
   * Implements the <code>TreeReaderFactory</code> interface.
   * It creates a simple, default <code>TreeReader</code> which literally
   * reproduces trees in the treebank.  It uses all the defaults of the
   * <code>TreeReader</code> class, which means that you get a
   * <code>SimpleTreeFactory</code>, no <code>TreeNormalizer</code>, and
   * a <code>PennTreebankTokenizer</code>.
   */
  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in);
  }

}
