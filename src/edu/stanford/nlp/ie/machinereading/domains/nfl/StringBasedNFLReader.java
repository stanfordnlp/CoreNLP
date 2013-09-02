package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreLabel;

/**
 * NFLReader that uses string comparisons to match arguments to sentences instead of actual character offsets
 * Needed for sentences scraped from Yahoo! Sports, where the offsets were incorrect
 */
public class StringBasedNFLReader extends NFLReader {
  public StringBasedNFLReader() {
    this(true, true, true);
  }
  
  public StringBasedNFLReader(boolean preProcessSentences, boolean calculateHeadSpan, boolean forceGenerationOfIndexSpans) {
    super(preProcessSentences, calculateHeadSpan, forceGenerationOfIndexSpans);
  }
  
  @Override
  protected Span mapArgumentToSentence(String text, int start, int end, List<CoreLabel> tokens) {
    NFLTokenizer tokenizer = new NFLTokenizer(text);
    List<CoreLabel> argTokens = tokenizer.tokenize();
    List<Span> matches = new ArrayList<Span>();
    
    for(int offset = 0; offset < tokens.size() - argTokens.size(); offset ++){
      if(argMatches(argTokens, tokens, offset)){
        Span match = new Span(offset, offset + argTokens.size()); 
        matches.add(match);
      }
    }
    
    // return the match only if it is unambiguous
    if(matches.size() == 1) return matches.get(0);
    return null;
  }
  
  private boolean argMatches(List<CoreLabel> argTokens, List<CoreLabel> tokens, int offset) {
    for(int i = 0; i < argTokens.size(); i ++){
      if(! argTokens.get(i).word().equals(tokens.get(i + offset).word())){
        return false;
      }
    }
    return true;
  }
}
