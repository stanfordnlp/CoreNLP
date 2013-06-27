package edu.stanford.nlp.parser.lexparser;

/**
 * 
 * @author Spence Green
 *
 */
public class FactoredLexiconEvent {

  private final int wordId;
  private final int lemmaId;
  private final int tagId;
  private final int morphId;
  private final int loc;
  private final String word;
  private final String featureStr;
  
  public FactoredLexiconEvent(int wordId, int tagId, int lemmaId, int morphId, int loc, String word, String featureStr) {
    this.wordId = wordId;
    this.tagId = tagId;
    this.lemmaId = lemmaId;
    this.morphId = morphId;
    this.loc = loc;
    this.word = word;
    this.featureStr = featureStr;
  }
  
  public int wordId() { return wordId; }
  public int tagId() { return tagId; }
  public int morphId() { return morphId; }
  public int lemmaId() { return lemmaId; }
  public int getLoc() { return loc; }
  public String word() { return word; }
  
  public String featureStr() { return featureStr; }
  
  @Override
  public String toString() {
    return word;
  }
}
