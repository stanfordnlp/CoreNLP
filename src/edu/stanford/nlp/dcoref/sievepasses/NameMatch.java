package edu.stanford.nlp.dcoref.sievepasses;

import edu.stanford.nlp.dcoref.MentionMatcher;
import edu.stanford.nlp.util.MetaClass;
import edu.stanford.nlp.util.ReflectionLoading;

import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * Use name matcher
 *
 * @author Angel Chang
 */
public class NameMatch extends DeterministicCorefSieve {
  MentionMatcher mentionMatcher = null;

  public NameMatch() {
    super();
    flags.USE_iwithini = true;
    flags.USE_NAME_MATCH = true;
  }

  public MentionMatcher getMentionMatcher() { return mentionMatcher; }

  public void init(Properties props) {
    // TODO: Can get custom mention matcher
    mentionMatcher = ReflectionLoading.loadByReflection("edu.stanford.nlp.kbp.entitylinking.classify.namematcher.RuleBasedNameMatcher",
            "dcoref.mentionMatcher", props);
  }

}
