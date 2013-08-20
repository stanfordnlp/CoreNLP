package edu.stanford.nlp.ling;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for dealing with Word and String. Mostly so we can get rid of the 
 * godawful if(w instanceOf String) stuff.
 * @author dlwh
 *
 */
public class Words {
  private Words() {}
  
  public static List<String> toStrings(List<? extends HasWord> words) {
    List<String> ret = new ArrayList<String>();
    for(HasWord w: words) {
      ret.add(w.word());
    }
    return ret;
  }
  
  
  public static List<Word> fromStrings(List<String> words) {
    List<Word> ret = new ArrayList<Word>();
    for(String w: words) {
      ret.add(new Word(w));
    }
    return ret;
  }

}
