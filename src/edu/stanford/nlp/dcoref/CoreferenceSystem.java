package edu.stanford.nlp.dcoref;

import java.util.Map;

/**
 * abstract class for coreference resolution system
 * 
 * @author heeyoung
 *
 */
public abstract class CoreferenceSystem {
  
  public abstract Map<Integer, CorefChain> coref(Document document) throws Exception;
  
}
