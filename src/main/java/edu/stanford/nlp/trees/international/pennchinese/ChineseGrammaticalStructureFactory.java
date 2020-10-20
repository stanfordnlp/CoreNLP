package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import java.util.function.Predicate;

public class ChineseGrammaticalStructureFactory implements GrammaticalStructureFactory {

  private final Predicate<String> puncFilter;
  private final HeadFinder hf;

  public ChineseGrammaticalStructureFactory() {
    this(null, null);
  }

  public ChineseGrammaticalStructureFactory(Predicate<String> puncFilter) {
    this(puncFilter, null);
  }

  public ChineseGrammaticalStructureFactory(Predicate<String> puncFilter, HeadFinder hf) {
    this.puncFilter = puncFilter;
    this.hf = hf;
  }

  public ChineseGrammaticalStructure newGrammaticalStructure(Tree t) {
    if (puncFilter == null && hf == null) {
      return new ChineseGrammaticalStructure(t);
    } else if (hf == null) {
      return new ChineseGrammaticalStructure(t, puncFilter);
    } else {
      return new ChineseGrammaticalStructure(t, puncFilter, hf);
    }
  }
  
}
