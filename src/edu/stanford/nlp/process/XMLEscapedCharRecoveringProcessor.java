package edu.stanford.nlp.process;

import edu.stanford.nlp.ling.HasWord;

import java.util.*;

/**
 * Class XMLEscapedCharRecoveringProcessor
 *
 * @author Teg Grenager
 */
public class XMLEscapedCharRecoveringProcessor implements ListProcessor<HasWord,HasWord> {

  private final Map<String,String> escapeSequenceMap;

  public List<HasWord> process(List<? extends HasWord> list) {
    List<HasWord> result = new ArrayList<HasWord>();
    for (HasWord word : list) {
      String token = word.word();
      if (token.charAt(0) == '&') {
        token = escapeSequenceMap.get(token);
        if (token == null) {
          throw new RuntimeException("no new mapping found for token " + word.word() + " please add to XMLEscapedCharRecoveringProcessor now!");
        }
        word.setWord(token);
      }
      result.add(word);
    }
    return result;
  }

  public XMLEscapedCharRecoveringProcessor() {
    escapeSequenceMap = new HashMap<String,String>();
    escapeSequenceMap.put("&quot;", "\"");
    escapeSequenceMap.put("&apos;", "'");
    escapeSequenceMap.put("&amp;", "&");
    escapeSequenceMap.put("&lt;", "<");
    escapeSequenceMap.put("&gt;", ">");
    // are there others?
  }

}
