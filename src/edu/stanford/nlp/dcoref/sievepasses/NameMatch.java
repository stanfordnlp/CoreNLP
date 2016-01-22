package edu.stanford.nlp.dcoref.sievepasses;

import edu.stanford.nlp.dcoref.*;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.ReflectionLoading;

import java.util.Properties;
import java.util.Set;

/**
 * Use name matcher - match full names only
 *
 * @author Angel Chang
 */
public class NameMatch extends DeterministicCorefSieve {
  protected MentionMatcher mentionMatcher = null;
  protected int minTokens = 0; // Minimum number of tokens in name before attempting match
  protected boolean ignoreGender = true;

  private Set<String> supportedNerTypes = Generics.newHashSet();

  public NameMatch() {
    super();
    flags.USE_iwithini = true;
    flags.USE_NAME_MATCH = true;

    // Stick with mainly person and organizations
    supportedNerTypes.add("ORG");
    supportedNerTypes.add("ORGANIZATION");
    supportedNerTypes.add("PER");
    supportedNerTypes.add("PERSON");
    supportedNerTypes.add("MISC");
  }

  @Override
  public void init(Properties props) {
    // TODO: Can get custom mention matcher
    mentionMatcher = ReflectionLoading.loadByReflection("edu.stanford.nlp.kbp.entitylinking.classify.namematcher.RuleBasedNameMatcher",
            "dcoref.mentionMatcher", props);
  }

  private static boolean isNamedMention(Mention m, Dictionaries dict, Set<Mention> roleSet) {
    return m.mentionType == Dictionaries.MentionType.PROPER;
  }

  @Override
  public boolean checkEntityMatch(
          Document document,
          CorefCluster mentionCluster,
          CorefCluster potentialAntecedent,
          Dictionaries dict,
          Set<Mention> roleSet)
  {
    Boolean matched = false;
    Mention mainMention = mentionCluster.getRepresentativeMention();
    Mention antMention = potentialAntecedent.getRepresentativeMention();
    // Check if the representative mentions are compatible
    if (isNamedMention(mainMention, dict, roleSet) && isNamedMention(antMention, dict, roleSet)) {
      if (mainMention.originalSpan.size() > minTokens || antMention.originalSpan.size() > minTokens) {
        if (Rules.entityAttributesAgree(mentionCluster, potentialAntecedent, ignoreGender)) {
          if (supportedNerTypes.contains(mainMention.nerString) || supportedNerTypes.contains(antMention.nerString)) {
            matched = mentionMatcher.isCompatible(mainMention, antMention);
            if (matched != null) {
              //Redwood.log("Match '" + mainMention + "' with '" + antMention + "' => " + matched);
              if (!matched) {
                document.addIncompatible(mainMention, antMention);
              }
            } else {
              matched = false;
            }
          }
        }
      }
    }
    return matched;
  }

}
