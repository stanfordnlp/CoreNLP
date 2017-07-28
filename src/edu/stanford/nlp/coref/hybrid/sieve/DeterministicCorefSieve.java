//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright (c) 2009-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.coref.hybrid.sieve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.CorefRules;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Dictionaries.Number;
import edu.stanford.nlp.coref.data.Dictionaries.Person;
import edu.stanford.nlp.coref.data.Document.DocType;
import edu.stanford.nlp.coref.hybrid.HybridCorefPrinter;
import edu.stanford.nlp.coref.hybrid.HybridCorefProperties;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.logging.Redwood;

/**
 *  Base class for a Coref Sieve.
 *  Each sieve extends this class, and set flags for its own options in the constructor.
 *
 *  @author heeyoung
 *  @author mihais
 */
public abstract class DeterministicCorefSieve extends Sieve  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DeterministicCorefSieve.class);

  public final DcorefSieveOptions flags;

  public DeterministicCorefSieve() {
    super();
    this.classifierType = ClassifierType.RULE;
    flags = new DcorefSieveOptions();
  }
  public DeterministicCorefSieve(Properties props) {
    super(props);
    this.classifierType = ClassifierType.RULE;
    flags = new DcorefSieveOptions();
  }

  public void findCoreferentAntecedent(Mention m, int mIdx, Document document, Dictionaries dict, Properties props, StringBuilder sbLog) throws Exception {

    // check for skip: first mention only, discourse salience
    if(!this.flags.USE_SPEAKERMATCH && !this.flags.USE_DISCOURSEMATCH && !this.flags.USE_APPOSITION && !this.flags.USE_PREDICATENOMINATIVES
        && this.skipThisMention(document, m, document.corefClusters.get(m.corefClusterID), dict)) {
      return;
    }

    Set<Mention> roleSet = document.roleSet;
    for (int sentJ = m.sentNum; sentJ >= 0; sentJ--) {
      List<Mention> l = Sieve.getOrderedAntecedents(m, sentJ, mIdx, document.predictedMentions, dict);
      if(maxSentDist != -1 && m.sentNum - sentJ > maxSentDist) continue;

      // TODO: do we need this?
      // Sort mentions by length whenever we have two mentions beginning at the same position and having the same head
      for(int i = 0; i < l.size(); i++) {
        for(int j = 0; j < l.size(); j++) {
          if(l.get(i).headString.equals(l.get(j).headString) &&
              l.get(i).startIndex == l.get(j).startIndex &&
              l.get(i).sameSentence(l.get(j)) && j > i &&
              l.get(i).spanToString().length() > l.get(j).spanToString().length()) {
            l.set(j, l.set(i, l.get(j)));
//              log.info("antecedent ordering changed!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
          }
        }
      }

      for (Mention ant : l) {
        if(skipForAnalysis(ant, m, props)) continue;

        // m2 - antecedent of m1

        // Skip singletons according to the singleton predictor
        // (only for non-NE mentions)
        // Recasens, de Marneffe, and Potts (NAACL 2013)
        if (m.isSingleton && m.mentionType != MentionType.PROPER && ant.isSingleton && ant.mentionType != MentionType.PROPER) continue;
        if (m.corefClusterID == ant.corefClusterID) continue;

        if(!mType.contains(m.mentionType) || !aType.contains(ant.mentionType)) continue;
        if(m.mentionType == MentionType.PRONOMINAL) {
          if(!matchedMentionType(m, mTypeStr)) continue;
          if(!matchedMentionType(ant, aTypeStr)) continue;
        }
        CorefCluster c1 = document.corefClusters.get(m.corefClusterID);
        CorefCluster c2 = document.corefClusters.get(ant.corefClusterID);
        assert(c1 != null);
        assert(c2 != null);

        if (this.useRoleSkip()) {
          if (m.isRoleAppositive(ant, dict)) {
            roleSet.add(m);
          } else if (ant.isRoleAppositive(m, dict)) {
            roleSet.add(ant);
          }
          continue;
        }
        if (this.coreferent(document, c1, c2, m, ant, dict, roleSet)) {
          // print logs for analysis
//            if (doScore()) {
//              printLogs(c1, c2, m1, m2, document, currentSieve);
//            }

          // print dcoref log
          if(HybridCorefProperties.debug(props)) {
            sbLog.append(HybridCorefPrinter.printErrorLogDcoref(m, ant, document, dict, mIdx, this.getClass().getName()));
          }

          int removeID = c1.clusterID;
//          log.info("Merging ant "+c2+" with "+c1);
          CorefCluster.mergeClusters(c2, c1);
          document.mergeIncompatibles(c2, c1);
          document.mergeAcronymCache(c2, c1);
//            log.warning("Removing cluster " + removeID + ", merged with " + c2.getClusterID());
          document.corefClusters.remove(removeID);
          return;
        }
      }
    } // End of "LOOP"
  }

  public String flagsToString() { return flags.toString(); }

  public boolean useRoleSkip() { return flags.USE_ROLE_SKIP; }

  /** Skip this mention? (search pruning) */
  public boolean skipThisMention(Document document, Mention m1, CorefCluster c, Dictionaries dict) {
    boolean skip = false;

    // only do for the first mention in its cluster
//    if(!flags.USE_EXACTSTRINGMATCH && !flags.USE_ROLEAPPOSITION && !flags.USE_PREDICATENOMINATIVES
    if(!flags.USE_ROLEAPPOSITION && !flags.USE_PREDICATENOMINATIVES   // CHINESE CHANGE
        && !flags.USE_ACRONYM && !flags.USE_APPOSITION && !flags.USE_RELATIVEPRONOUN
        && !c.getFirstMention().equals(m1)) {
      return true;
    }

    if(m1.appositions == null && m1.predicateNominatives == null
        && (m1.lowercaseNormalizedSpanString().startsWith("a ") || m1.lowercaseNormalizedSpanString().startsWith("an "))
        && !flags.USE_EXACTSTRINGMATCH)  {
      skip = true; // A noun phrase starting with an indefinite article - unlikely to have an antecedent (e.g. "A commission" was set up to .... )
    }
    if(dict.indefinitePronouns.contains(m1.lowercaseNormalizedSpanString()))  {
      skip = true; // An indefinite pronoun - unlikely to have an antecedent (e.g. "Some" say that... )
    }
    for(String indef : dict.indefinitePronouns){
      if(m1.lowercaseNormalizedSpanString().startsWith(indef + " ")) {
        skip = true; // A noun phrase starting with an indefinite adjective - unlikely to have an antecedent (e.g. "Another opinion" on the topic is...)
        break;
      }
    }

    return skip;
  }

  public boolean checkEntityMatch(
          Document document,
          CorefCluster mentionCluster,
          CorefCluster potentialAntecedent,
          Dictionaries dict,
          Set<Mention> roleSet)
  {
    return false;
  }
  /**
   * Checks if two clusters are coreferent according to our sieve pass constraints
   * @param document
   * @throws Exception
   */
  public boolean coreferent(Document document, CorefCluster mentionCluster,
      CorefCluster potentialAntecedent,
      Mention mention2,
      Mention ant,
      Dictionaries dict,
      Set<Mention> roleSet) throws Exception {

    boolean ret = false;
    Mention mention = mentionCluster.getRepresentativeMention();
    if (flags.USE_INCOMPATIBLES) {
      // Check our list of incompatible mentions and don't cluster them together
      // Allows definite no's from previous sieves to propagate down
      if (document.isIncompatible(mentionCluster, potentialAntecedent)) {
        return false;
      }
    }
    if (flags.DO_PRONOUN && Math.abs(mention2.sentNum-ant.sentNum) > 3 &&
        mention2.person!=Person.I && mention2.person!=Person.YOU) {
      return false;
    }
    if (mention2.lowercaseNormalizedSpanString().equals("this") && Math.abs(mention2.sentNum-ant.sentNum) > 3) {
      return false;
    }
    if (mention2.person==Person.YOU && document.docType==DocType.ARTICLE &&
        mention2.headWord.get(CoreAnnotations.SpeakerAnnotation.class).equals("PER0")) {
      return false;
    }
    if (document.conllDoc != null) {
      if (ant.generic && ant.person==Person.YOU) return false;
      if (mention2.generic) return false;
    }

    // chinese newswire contains coref nested NPs with shared headword  Chen & Ng
    if(lang != Locale.CHINESE || document.docInfo == null || !document.docInfo.getOrDefault("DOC_ID","").contains("nw")) {
      if(mention2.insideIn(ant) || ant.insideIn(mention2)) return false;
    }

    if(flags.USE_SPEAKERMATCH) {
      String mSpeaker = mention2.headWord.get(SpeakerAnnotation.class);
      String aSpeaker = ant.headWord.get(SpeakerAnnotation.class);

      // <I> from same speaker
      if(mention2.person == Person.I && ant.person == Person.I) return (mSpeaker.equals(aSpeaker));

      // <I> - speaker
      if( (mention2.person == Person.I && mSpeaker.equals(Integer.toString(ant.mentionID)))
          || (ant.person == Person.I && aSpeaker.equals(Integer.toString(mention2.mentionID))) ) return true;
    }
    if(flags.USE_DISCOURSEMATCH) {
      String mString = mention.lowercaseNormalizedSpanString();
      String antString = ant.lowercaseNormalizedSpanString();

      // mention and ant both belong to the same speaker cluster
      if (mention.speakerInfo != null && mention.speakerInfo == ant.speakerInfo) {
        return true;
      }

      // (I - I) in the same speaker's quotation.
      if (mention.number==Number.SINGULAR && dict.firstPersonPronouns.contains(mString)
          && ant.number==Number.SINGULAR && dict.firstPersonPronouns.contains(antString)
          && CorefRules.entitySameSpeaker(document, mention, ant)){
        return true;
      }
      // (speaker - I)
      if ((mention.number==Number.SINGULAR && dict.firstPersonPronouns.contains(mString))
              && CorefRules.antecedentIsMentionSpeaker(document, mention, ant, dict)) {
        if (mention.speakerInfo == null && ant.speakerInfo != null) { mention.speakerInfo = ant.speakerInfo; }
        return true;
      }
      // (I - speaker)
      if ((ant.number==Number.SINGULAR && dict.firstPersonPronouns.contains(antString))
              && CorefRules.antecedentIsMentionSpeaker(document, ant, mention, dict)) {
        if (ant.speakerInfo == null && mention.speakerInfo != null) { ant.speakerInfo = mention.speakerInfo; }
        return true;
      }
      // Can be iffy if more than two speakers... but still should be okay most of the time
      if (dict.secondPersonPronouns.contains(mString)
          && dict.secondPersonPronouns.contains(antString)
          && CorefRules.entitySameSpeaker(document, mention, ant)) {
        return true;
      }
      // previous I - you or previous you - I in two person conversation
      if (((mention.person==Person.I && ant.person==Person.YOU
          || (mention.person==Person.YOU && ant.person==Person.I))
          && (mention.headWord.get(CoreAnnotations.UtteranceAnnotation.class)-ant.headWord.get(CoreAnnotations.UtteranceAnnotation.class) == 1)
          && document.docType==DocType.CONVERSATION)) {
        return true;
      }
      if (dict.reflexivePronouns.contains(mention.headString) && CorefRules.entitySubjectObject(mention, ant)){
        return true;
      }
    }
    if (!flags.USE_EXACTSTRINGMATCH && !flags.USE_RELAXED_EXACTSTRINGMATCH
        && !flags.USE_APPOSITION && !flags.USE_WORDS_INCLUSION) {
      for(Mention m : mentionCluster.getCorefMentions()) {
        for(Mention a : potentialAntecedent.getCorefMentions()){
          // angelx - not sure about the logic here, disable (code was also refactored from original)
          // vv gabor - re-enabled code (seems to improve performance) vv
          if(m.person!=Person.I && a.person!=Person.I &&
            (CorefRules.antecedentIsMentionSpeaker(document, m, a, dict) || CorefRules.antecedentIsMentionSpeaker(document, a, m, dict))) {
            document.addIncompatible(m, a);
            return false;
          }
          // ^^ end block of code in question ^^
          int dist = Math.abs(m.headWord.get(CoreAnnotations.UtteranceAnnotation.class) - a.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
          if(document.docType!=DocType.ARTICLE && dist==1 && !CorefRules.entitySameSpeaker(document, m, a)) {
            String mSpeaker = document.speakers.get(m.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
            String aSpeaker = document.speakers.get(a.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
            if(m.person==Person.I && a.person==Person.I) {
              document.addIncompatible(m, a);
              return false;
            }
            if(m.person==Person.YOU && a.person==Person.YOU) {
              document.addIncompatible(m, a);
              return false;
            }
            // This is weak since we can refer to both speakers
            if(m.person==Person.WE && a.person==Person.WE) {
              document.addIncompatible(m, a);
              return false;
            }
          }
        }
      }
      if(document.docType==DocType.ARTICLE) {
        for(Mention m : mentionCluster.getCorefMentions()) {
          for(Mention a : potentialAntecedent.getCorefMentions()){
            if(CorefRules.entitySubjectObject(m, a)) {
              document.addIncompatible(m, a);
              return false;
            }
          }
        }
      }
    }

    // Incompatibility constraints - do before match checks
    if(flags.USE_iwithini && CorefRules.entityIWithinI(mention, ant, dict)) {
      document.addIncompatible(mention, ant);
      return false;
    }

    // Match checks
    if(flags.USE_EXACTSTRINGMATCH && CorefRules.entityExactStringMatch(mention, ant, dict, roleSet)){
      return true;
    }
//    if(flags.USE_EXACTSTRINGMATCH && Rules.entityExactStringMatch(mentionCluster, potentialAntecedent, dict, roleSet)){
//      return true;
//    }
    if (flags.USE_NAME_MATCH && checkEntityMatch(document, mentionCluster, potentialAntecedent, dict, roleSet)) {
      ret = true;
    }

    if(flags.USE_RELAXED_EXACTSTRINGMATCH && CorefRules.entityRelaxedExactStringMatch(mentionCluster, potentialAntecedent, mention, ant, dict, roleSet)){
      return true;
    }
    if(flags.USE_APPOSITION && CorefRules.entityIsApposition(mentionCluster, potentialAntecedent, mention, ant)) {
      return true;
    }
    if(flags.USE_PREDICATENOMINATIVES && CorefRules.entityIsPredicateNominatives(mentionCluster, potentialAntecedent, mention, ant)) {
      return true;
    }

    if(flags.USE_ACRONYM && CorefRules.entityIsAcronym(document, mentionCluster, potentialAntecedent)) {
      return true;
    }
    if(flags.USE_RELATIVEPRONOUN && CorefRules.entityIsRelativePronoun(mention, ant)){
      return true;
    }
    if(flags.USE_DEMONYM && mention.isDemonym(ant, dict)){
      return true;
    }

    if(flags.USE_ROLEAPPOSITION){
      if(lang==Locale.CHINESE)
        ret = false;
      else
        if(CorefRules.entityIsRoleAppositive(mentionCluster, potentialAntecedent, mention, ant, dict))
          ret = true;
    }
    if(flags.USE_INCLUSION_HEADMATCH && CorefRules.entityHeadsAgree(mentionCluster, potentialAntecedent, mention, ant, dict)){
      ret = true;
    }
    if(flags.USE_RELAXED_HEADMATCH && CorefRules.entityRelaxedHeadsAgreeBetweenMentions(mentionCluster, potentialAntecedent, mention, ant) ){
      ret = true;
    }

    if(flags.USE_WORDS_INCLUSION && ret && ! CorefRules.entityWordsIncluded(mentionCluster, potentialAntecedent, mention, ant)) {
      return false;
    }

    if(flags.USE_INCOMPATIBLE_MODIFIER && ret && CorefRules.entityHaveIncompatibleModifier(mentionCluster, potentialAntecedent)) {
      return false;
    }
    if(flags.USE_PROPERHEAD_AT_LAST && ret && !CorefRules.entitySameProperHeadLastWord(mentionCluster, potentialAntecedent, mention, ant)) {
      return false;
    }
    if(flags.USE_ATTRIBUTES_AGREE && !CorefRules.entityAttributesAgree(mentionCluster, potentialAntecedent)) {
      return false;
    }
    if(flags.USE_DIFFERENT_LOCATION
        && CorefRules.entityHaveDifferentLocation(mention, ant, dict)) {
      if(flags.USE_PROPERHEAD_AT_LAST  && ret && mention.goldCorefClusterID!=ant.goldCorefClusterID) {
      }
      return false;
    }
    if(flags.USE_NUMBER_IN_MENTION
        && CorefRules.entityNumberInLaterMention(mention, ant)) {
      if(flags.USE_PROPERHEAD_AT_LAST  && ret && mention.goldCorefClusterID!=ant.goldCorefClusterID) {
      }
      return false;
    }

    if(flags.USE_DISTANCE && CorefRules.entityTokenDistance(mention2, ant)){
      return false;
    }

    if(flags.USE_COREF_DICT){

      // Head match
      if(ant.headWord.lemma().equals(mention2.headWord.lemma())) return false;

      // Constraint: ignore pairs commonNoun - properNoun
      if (ant.mentionType != MentionType.PROPER &&
              ( mention2.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")
                      || ! mention2.headWord.word().substring(1).equals(mention2.headWord.word().substring(1).toLowerCase()) ) ) {
        return false;
      }

      // Constraint: ignore plurals
      if(ant.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNS")
          && mention2.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNS")) return false;

      // Constraint: ignore mentions with indefinite determiners
      if(dict.indefinitePronouns.contains(ant.originalSpan.get(0).lemma())
          || dict.indefinitePronouns.contains(mention2.originalSpan.get(0).lemma())) return false;

      // Constraint: ignore coordinated mentions
      if(ant.isCoordinated() || mention2.isCoordinated()) return false;

      // Constraint: context incompatibility
      if(CorefRules.contextIncompatible(mention2, ant, dict)) return false;

      // Constraint: sentence context incompatibility when the mentions are common nouns
      if(CorefRules.sentenceContextIncompatible(mention2, ant, dict)) return false;

      if(CorefRules.entityClusterAllCorefDictionary(mentionCluster, potentialAntecedent, dict, 1, 8)) return true;
      if(CorefRules.entityCorefDictionary(mention, ant, dict, 2, 2)) return true;
      if(CorefRules.entityCorefDictionary(mention, ant, dict, 3, 2)) return true;
      if(CorefRules.entityCorefDictionary(mention, ant, dict, 4, 2)) return true;
    }

    if(flags.DO_PRONOUN){
      Mention m;
      if (mention.predicateNominatives!=null && mention.predicateNominatives.contains(mention2)) {
        m = mention2;
      } else {
        m = mention;
      }

      boolean mIsPronoun = (m.isPronominal() || dict.allPronouns.contains(m.toString()));
      boolean attrAgree = HybridCorefProperties.useDefaultPronounAgreement(props)?
          CorefRules.entityAttributesAgree(mentionCluster, potentialAntecedent):
            CorefRules.entityAttributesAgree(mentionCluster, potentialAntecedent, lang);

      if(mIsPronoun && attrAgree){

        if(dict.demonymSet.contains(ant.lowercaseNormalizedSpanString()) && dict.notOrganizationPRP.contains(m.headString)){
          document.addIncompatible(m, ant);
          return false;
        }
        if(CorefRules.entityPersonDisagree(document, mentionCluster, potentialAntecedent, dict)){
          document.addIncompatible(m, ant);
          return false;
        }
        return true;
      }
    }

    if(flags.USE_CHINESE_HEAD_MATCH) {
      if (mention2.headWord == ant.headWord && mention2.insideIn(ant)) {
        if(!document.isCoref(mention2, ant)) {
          // TODO: exclude conjunction
          // log.info("error in chinese head match: "+mention2.spanToString()+"\t"+ant.spanToString());
        }
        return true;
      }
    }

    return ret;
  }

  /**
   * Orders the antecedents for the given mention (m1)
   * @param antecedentSentence
   * @param mySentence
   * @param orderedMentions
   * @param orderedMentionsBySentence
   * @param m1
   * @param m1Position
   * @param corefClusters
   * @param dict
   * @return An ordering of potential antecedents depending on same/different sentence, etc.
   */
  public List<Mention> getOrderedAntecedents(
      int antecedentSentence,
      int mySentence,
      List<Mention> orderedMentions,
      List<List<Mention>> orderedMentionsBySentence,
      Mention m1,
      int m1Position,
      Map<Integer, CorefCluster> corefClusters,
      Dictionaries dict) {
    List<Mention> orderedAntecedents = new ArrayList<>();

    // ordering antecedents
    if (antecedentSentence == mySentence) {   // same sentence
      orderedAntecedents.addAll(orderedMentions.subList(0, m1Position));
      if(flags.DO_PRONOUN && m1.isPronominal()) {    // TODO
        orderedAntecedents = sortMentionsForPronoun(orderedAntecedents, m1);
      }
      if(dict.relativePronouns.contains(m1.spanToString())) Collections.reverse(orderedAntecedents);
    } else {    // previous sentence
      orderedAntecedents.addAll(orderedMentionsBySentence.get(antecedentSentence));
    }

    return orderedAntecedents;
  }

  /** Divides a sentence into clauses and sort the antecedents for pronoun matching  */
  private static List<Mention> sortMentionsForPronoun(List<Mention> l, Mention m1) {
    List<Mention> sorted = new ArrayList<>();
    Tree tree = m1.contextParseTree;
    Tree current = m1.mentionSubTree;
    if(tree==null || current==null) return l;
    while(true){
      current = current.ancestor(1, tree);
      if(current.label().value().startsWith("S")){
        for(Mention m : l){
          if(!sorted.contains(m) && current.dominates(m.mentionSubTree)) sorted.add(m);
        }
      }
      if(current.ancestor(1, tree)==null) break;
    }
    if(l.size()!=sorted.size()) {
      sorted=l;
    } else if(!l.equals(sorted)){
      for(int i=0; i<l.size(); i++){
        Mention ml = l.get(i);
        Mention msorted = sorted.get(i);
      }
    } else {
    }
    return sorted;
  }

}



