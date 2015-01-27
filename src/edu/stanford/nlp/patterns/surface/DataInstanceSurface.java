package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.DataInstance;

import java.util.List;

/**
 * Created by sonalg on 11/1/14.
 */
public class DataInstanceSurface extends DataInstance {

  List<CoreLabel> tokens;
  public DataInstanceSurface(List<CoreLabel> toks){
    this.tokens = toks;
  }

  @Override
  public List<CoreLabel> getTokens() {
    return tokens;
  }
}
