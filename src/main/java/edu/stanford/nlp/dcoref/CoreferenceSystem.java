package edu.stanford.nlp.dcoref;

import java.util.Map;

// todo [cdm 2017]: JointCorefSystem is the only coref system that has every implemented this. Just disband it. Nothing uses it.
/**
 * abstract class for coreference resolution system
 *
 * @author heeyoung
 *
 */
public abstract class CoreferenceSystem {

  public abstract Map<Integer, CorefChain> coref(Document document) throws Exception;

}
