package edu.stanford.nlp.trees;

import java.util.function.Predicate;

public class UniversalEnglishGrammaticalStructureFactory implements GrammaticalStructureFactory {

  private final Predicate<String> puncFilter;
  private final HeadFinder hf;

  public UniversalEnglishGrammaticalStructureFactory() {
    this(null, null);
  }

  public UniversalEnglishGrammaticalStructureFactory(Predicate<String> puncFilter) {
    this(puncFilter, null);
  }

  public UniversalEnglishGrammaticalStructureFactory(Predicate<String> puncFilter, HeadFinder hf) {
    this.puncFilter = puncFilter;
    this.hf = hf;
  }

  @Override
  public UniversalEnglishGrammaticalStructure newGrammaticalStructure(Tree t) {
    if (puncFilter == null && hf == null) {
      return new UniversalEnglishGrammaticalStructure(t);
    } else if (hf == null) {
      return new UniversalEnglishGrammaticalStructure(t, puncFilter);
    } else {
      return new UniversalEnglishGrammaticalStructure(t, puncFilter, hf);
    }
  }

}
