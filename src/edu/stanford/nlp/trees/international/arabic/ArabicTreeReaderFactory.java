package edu.stanford.nlp.trees.international.arabic;

import java.io.Reader;
import java.io.Serializable;

import edu.stanford.nlp.trees.FilteringTreeReader;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.util.Filter;


/** Reads ArabicTreebank trees.  See {@link ArabicTreeNormalizer} for the
 *  meaning of the constructor parameters.
 *
 *  @author Roger Levy
 *  @author Christopher Manning
 *  @author Spence Green
 */
public class ArabicTreeReaderFactory implements TreeReaderFactory, Serializable {

  private static final long serialVersionUID = 1973767605277873017L;

  private final boolean retainNPTmp;
  private final boolean retainNPSbj;
  private final boolean retainPRD;
  private final boolean retainPPClr;
  private final boolean changeNoLabels;
  private final boolean filterX;
  private final boolean noNormalization;

  public ArabicTreeReaderFactory() {
    this(false, false, false, false, false, false, false);
  }

  public ArabicTreeReaderFactory(boolean retainNPTmp, boolean retainPRD,
      boolean changeNoLabels, boolean filterX,
      boolean retainNPSbj,
      boolean noNormalization, boolean retainPPClr) {
    
    this.retainNPTmp = retainNPTmp;
    this.retainPRD = retainPRD;
    this.changeNoLabels = changeNoLabels;
    this.filterX = filterX;
    this.retainNPSbj = retainNPSbj;
    this.noNormalization = noNormalization;
    this.retainPPClr = retainPPClr;
  }

  public TreeReader newTreeReader(Reader in) {
    TreeReader tr = null;
    if(noNormalization) {
      tr = new PennTreeReader(in, new LabeledScoredTreeFactory(), new TreeNormalizer(), new ArabicTreebankTokenizer(in));
    } else
      tr = new PennTreeReader(in, new LabeledScoredTreeFactory(), new ArabicTreeNormalizer(retainNPTmp,retainPRD,changeNoLabels, retainNPSbj, retainPPClr), new ArabicTreebankTokenizer(in));

    if (filterX)
      tr = new FilteringTreeReader(tr, new XFilter());

    return tr;
  }


  static class XFilter implements Filter<Tree> {

    private static final long serialVersionUID = -4522060160716318895L;

    public XFilter() {}

    public boolean accept(Tree t) {
      return ! (t.numChildren() == 1 && "X".equals(t.firstChild().value()));
    }

  }


  public static class ArabicRawTreeReaderFactory extends ArabicTreeReaderFactory {

    private static final long serialVersionUID = -5693371540982097793L;

    public ArabicRawTreeReaderFactory() {
      super(false, false, true, false, false, false, false);
    }

    public ArabicRawTreeReaderFactory(boolean noNormalization) {
      super(false, false, true, false, false, noNormalization, false);
    }
  }
}
