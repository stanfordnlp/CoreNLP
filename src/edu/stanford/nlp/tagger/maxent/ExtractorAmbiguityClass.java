package edu.stanford.nlp.tagger.maxent;

// TODO: maybe we can get rid of this... it's not used anywhere and it
// relies on code already commented to be "rotted" in AmbiguityClasses.java

public class ExtractorAmbiguityClass extends Extractor{

  private static final long serialVersionUID = 5602673039477411525L;

  private transient Dictionary dict;
  private transient TTags ttags;
  private transient int veryCommonWordThresh;
  private AmbiguityClasses ambClasses;

  @Override
  protected void setGlobalHolder(MaxentTagger maxentTagger) {
    super.setGlobalHolder(maxentTagger);
    this.dict = maxentTagger.dict;
    this.ttags = maxentTagger.tags;
    this.veryCommonWordThresh = maxentTagger.veryCommonWordThresh;
    this.ambClasses = maxentTagger.ambClasses;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    // Extract the ambiguity class to which this word belongs
    String cword = pH.getWord(h, 0);
    // todo: Can we change this to reuse the pre-calculation of the ambiguity class in the Dictionary
    int ambClass = ambClasses.getClass(cword, dict,
                                       veryCommonWordThresh, ttags);
    return String.valueOf(ambClass);
  }

  public ExtractorAmbiguityClass() {
    super();
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }


}
