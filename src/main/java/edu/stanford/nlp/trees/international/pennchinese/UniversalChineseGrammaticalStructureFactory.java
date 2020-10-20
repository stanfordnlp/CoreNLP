package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import java.util.function.Predicate;

public class UniversalChineseGrammaticalStructureFactory implements GrammaticalStructureFactory {

  private final Predicate<String> puncFilter;
  private final HeadFinder hf;

  public UniversalChineseGrammaticalStructureFactory() {
    this(null, null);
  }

  public UniversalChineseGrammaticalStructureFactory(Predicate<String> puncFilter) {
    this(puncFilter, null);
  }

  public UniversalChineseGrammaticalStructureFactory(Predicate<String> puncFilter, HeadFinder hf) {
    this.puncFilter = puncFilter;
    this.hf = hf;
  }

  @Override
  public UniversalChineseGrammaticalStructure newGrammaticalStructure(Tree t) {
    if (puncFilter == null && hf == null) {
      return new UniversalChineseGrammaticalStructure(t);
    } else if (hf == null) {
      return new UniversalChineseGrammaticalStructure(t, puncFilter);
    } else {
      return new UniversalChineseGrammaticalStructure(t, puncFilter, hf);
    }
  }

}
