package edu.stanford.nlp.trees;

import java.util.function.Predicate;

public class EnglishGrammaticalStructureFactory implements GrammaticalStructureFactory {

  private final Predicate<String> puncFilter;
  private final HeadFinder hf;

  public EnglishGrammaticalStructureFactory() {
    this(null, null);
  }

  public EnglishGrammaticalStructureFactory(Predicate<String> puncFilter) {
    this(puncFilter, null);
  }

  public EnglishGrammaticalStructureFactory(Predicate<String> puncFilter, HeadFinder hf) {
    this.puncFilter = puncFilter;
    this.hf = hf;
  }

  public EnglishGrammaticalStructure newGrammaticalStructure(Tree t) {
    if (puncFilter == null && hf == null) {
      return new EnglishGrammaticalStructure(t);
    } else if (hf == null) {
      return new EnglishGrammaticalStructure(t, puncFilter);
    } else {
      return new EnglishGrammaticalStructure(t, puncFilter, hf);
    }
  }
  
}
