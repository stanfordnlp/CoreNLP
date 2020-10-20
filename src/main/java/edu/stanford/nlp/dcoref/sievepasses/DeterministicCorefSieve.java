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

package edu.stanford.nlp.dcoref.sievepasses;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

import edu.stanford.nlp.dcoref.Constants;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.Dictionaries.Number;
import edu.stanford.nlp.dcoref.Dictionaries.Person;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.Document.DocType;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.dcoref.Rules;
import edu.stanford.nlp.dcoref.Semantics;
import edu.stanford.nlp.dcoref.SieveCoreferenceSystem;
import edu.stanford.nlp.dcoref.SieveOptions;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.Tree;

/**
 *  Base class for a Coref Sieve.
 *  Each sieve extends this class, and set flags for its own options in the constructor.
 *
 *  @author heeyoung
 *  @author mihais
 */
public abstract class DeterministicCorefSieve  {

  public final SieveOptions flags;
  protected Locale lang;

  /** Initialize flagSet */
  public DeterministicCorefSieve() {
    flags = new SieveOptions();
  }

  public void init(Properties props) {
    lang = Locale.forLanguageTag(props.getProperty(Constants.LANGUAGE_PROP, "en"));
  }

  public String flagsToString() { return flags.toString(); }

  public boolean useRoleSkip() { return flags.USE_ROLE_SKIP; }

  /** Skip this mention? (search pruning) */
  public boolean skipThisMention(Document document, Mention m1, CorefCluster c, Dictionaries dict) {
    boolean skip = false;

    // only do for the first mention in its cluster
    if(!flags.USE_EXACTSTRINGMATCH && !flags.USE_ROLEAPPOSITION && !flags.USE_PREDICATENOMINATIVES
        && !flags.USE_ACRONYM && !flags.USE_APPOSITION && !flags.USE_RELATIVEPRONOUN
        && !c.getFirstMention().equals(m1)) {
      return true;
    }

    if(Constants.USE_DISCOURSE_SALIENCE)  {
      SieveCoreferenceSystem.logger.finest("DOING COREF FOR:\t" + m1.spanToString());
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

      if(skip) {
        SieveCoreferenceSystem.logger.finest("MENTION SKIPPED:\t" + m1.spanToString() + "(" + m1.sentNum + ")"+"\toriginalRef: "+m1.originalRef + " in discourse "+m1.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
      }
    }

    return skip;
  }

  public boolean checkEntityMatch(
          Document document,
          CorefCluster mentionCluster,
          CorefCluster potentialAntecedent,
          Dictionaries dict,
          Set<Mention> roleSet) {
    return false;
  }

  /**
   * Checks if two clusters are coreferent according to our sieve pass constraints.
   *
   * @param document
   * @throws Exception
   */
  public boolean coreferent(Document document, CorefCluster mentionCluster,
      CorefCluster potentialAntecedent,
      Mention mention2,
      Mention ant,
      Dictionaries dict,
      Set<Mention> roleSet,
      Semantics semantics) throws Exception {

    boolean ret = false;
    Mention mention = mentionCluster.getRepresentativeMention();
    if (flags.USE_INCOMPATIBLES) {
      // Check our list of incompatible mentions and don't cluster them together
      // Allows definite no's from previous sieves to propagate down
      if (document.isIncompatible(mentionCluster, potentialAntecedent)) {
        SieveCoreferenceSystem.logger.finest("INCOMPATIBLE clusters: not match: " +ant.spanToString()+"("+ant.mentionID +
                ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
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
    if(mention2.insideIn(ant) || ant.insideIn(mention2)) return false;

    if(flags.USE_DISCOURSEMATCH) {
      String mString = mention.lowercaseNormalizedSpanString();
      String antString = ant.lowercaseNormalizedSpanString();

      // mention and ant both belong to the same speaker cluster
      if (mention.speakerInfo != null && mention.speakerInfo == ant.speakerInfo) {
        SieveCoreferenceSystem.logger.finest("discourse match: maps to same speaker: " + mention.spanToString() + "\tmatched\t" + ant.spanToString());
        return true;
      }

      // (I - I) in the same speaker's quotation.
      if (mention.number==Number.SINGULAR && dict.firstPersonPronouns.contains(mString)
          && ant.number==Number.SINGULAR && dict.firstPersonPronouns.contains(antString)
          && Rules.entitySameSpeaker(document, mention, ant)){
        SieveCoreferenceSystem.logger.finest("discourse match: 1st person same speaker: " + mention.spanToString() + "\tmatched\t" + ant.spanToString());
        return true;
      }
      // (speaker - I)
      if ((mention.number==Number.SINGULAR && dict.firstPersonPronouns.contains(mString))
              && Rules.antecedentIsMentionSpeaker(document, mention, ant, dict)) {
        SieveCoreferenceSystem.logger.finest("discourse match: 1st person mention speaker match antecedent: " + mention.spanToString() + "\tmatched\t" + ant.spanToString());
        if (mention.speakerInfo == null && ant.speakerInfo != null) { mention.speakerInfo = ant.speakerInfo; }
        return true;
      }
      // (I - speaker)
      if ((ant.number==Number.SINGULAR && dict.firstPersonPronouns.contains(antString))
              && Rules.antecedentIsMentionSpeaker(document, ant, mention, dict)) {
        SieveCoreferenceSystem.logger.finest("discourse match: 1st person antecedent speaker match mention: " + mention.spanToString() + "\tmatched\t" + ant.spanToString());
        if (ant.speakerInfo == null && mention.speakerInfo != null) { ant.speakerInfo = mention.speakerInfo; }
        return true;
      }
      // Can be iffy if more than two speakers... but still should be okay most of the time
      if (dict.secondPersonPronouns.contains(mString)
          && dict.secondPersonPronouns.contains(antString)
          && Rules.entitySameSpeaker(document, mention, ant)) {
        SieveCoreferenceSystem.logger.finest("discourse match: 2nd person same speaker: " + mention.spanToString() + "\tmatched\t" + ant.spanToString());
        return true;
      }
      // previous I - you or previous you - I in two person conversation
      if (((mention.person==Person.I && ant.person==Person.YOU
          || (mention.person==Person.YOU && ant.person==Person.I))
          && (mention.headWord.get(CoreAnnotations.UtteranceAnnotation.class)-ant.headWord.get(CoreAnnotations.UtteranceAnnotation.class) == 1)
          && document.docType==DocType.CONVERSATION)) {
        SieveCoreferenceSystem.logger.finest("discourse match: between two person: " + mention.spanToString() + "\tmatched\t" + ant.spanToString());
        return true;
      }
      if (dict.reflexivePronouns.contains(mention.headString) && Rules.entitySubjectObject(mention, ant)){
        SieveCoreferenceSystem.logger.finest("discourse match: reflexive pronoun: " + ant.spanToString() + "(" + ant.mentionID + ") :: " + mention.spanToString() + "(" + mention.mentionID + ") -> " + (mention.goldCorefClusterID == ant.goldCorefClusterID));
        return true;
      }
    }
    if (Constants.USE_DISCOURSE_CONSTRAINTS && !flags.USE_EXACTSTRINGMATCH && !flags.USE_RELAXED_EXACTSTRINGMATCH
        && !flags.USE_APPOSITION && !flags.USE_WORDS_INCLUSION) {
      for(Mention m : mentionCluster.getCorefMentions()) {
        for(Mention a : potentialAntecedent.getCorefMentions()){
          // angelx - not sure about the logic here, disable (code was also refactored from original)
          // vv gabor - re-enabled code (seems to improve performance) vv
          if(m.person!=Person.I && a.person!=Person.I &&
            (Rules.antecedentIsMentionSpeaker(document, m, a, dict) || Rules.antecedentIsMentionSpeaker(document, a, m, dict))) {
            SieveCoreferenceSystem.logger.finest("Incompatibles: not match(speaker): " +ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
            document.addIncompatible(m, a);
            return false;
          }
          // ^^ end block of code in question ^^
          int dist = Math.abs(m.headWord.get(CoreAnnotations.UtteranceAnnotation.class) - a.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
          if(document.docType!=DocType.ARTICLE && dist==1 && !Rules.entitySameSpeaker(document, m, a)) {
            String mSpeaker = document.speakers.get(m.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
            String aSpeaker = document.speakers.get(a.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
            if(m.person==Person.I && a.person==Person.I) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: neighbor I: " + ant.spanToString() + "(" + ant.mentionID + "," + aSpeaker + ") :: "
                      + mention.spanToString() + "(" + mention.mentionID + "," + mSpeaker + ") -> " + (mention.goldCorefClusterID != ant.goldCorefClusterID));
              document.addIncompatible(m, a);
              return false;
            }
            if(m.person==Person.YOU && a.person==Person.YOU) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: neighbor YOU: " + ant.spanToString() + "(" + ant.mentionID + "," + aSpeaker + ") :: "
                      + mention.spanToString() + "(" + mention.mentionID + "," + mSpeaker +  ") -> " + (mention.goldCorefClusterID != ant.goldCorefClusterID));
              document.addIncompatible(m, a);
              return false;
            }
            // This is weak since we can refer to both speakers
            if(m.person==Person.WE && a.person==Person.WE) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: neighbor WE: " + ant.spanToString() + "(" + ant.mentionID + "," + aSpeaker + ") :: "
                      + mention.spanToString() + "(" + mention.mentionID + "," + mSpeaker +  ") -> " + (mention.goldCorefClusterID != ant.goldCorefClusterID));
              document.addIncompatible(m, a);
              return false;
            }
          }
        }
      }
      if(document.docType==DocType.ARTICLE) {
        for(Mention m : mentionCluster.getCorefMentions()) {
          for(Mention a : potentialAntecedent.getCorefMentions()){
            if(Rules.entitySubjectObject(m, a)) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: subject-object: "+ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
              document.addIncompatible(m, a);
              return false;
            }
          }
        }
      }
    }

    // Incompatibility constraints - do before match checks
    if(flags.USE_iwithini && Rules.entityIWithinI(mention, ant, dict)) {
      SieveCoreferenceSystem.logger.finest("Incompatibles: iwithini: "+ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
      document.addIncompatible(mention, ant);
      return false;
    }

    // Match checks
    if(flags.USE_EXACTSTRINGMATCH && Rules.entityExactStringMatch(mentionCluster, potentialAntecedent, dict, roleSet)){
      return true;
    }
    if (flags.USE_NAME_MATCH && checkEntityMatch(document, mentionCluster, potentialAntecedent, dict, roleSet)) {
      ret = true;
    }

    if(flags.USE_RELAXED_EXACTSTRINGMATCH && Rules.entityRelaxedExactStringMatch(mentionCluster, potentialAntecedent, mention, ant, dict, roleSet)){
      return true;
    }
    if(flags.USE_APPOSITION && Rules.entityIsApposition(mentionCluster, potentialAntecedent, mention, ant)) {
      SieveCoreferenceSystem.logger.finest("Apposition: " + mention.spanToString() + "\tvs\t" + ant.spanToString());
      return true;
    }
    if(flags.USE_PREDICATENOMINATIVES && Rules.entityIsPredicateNominatives(mentionCluster, potentialAntecedent, mention, ant)) {
      SieveCoreferenceSystem.logger.finest("Predicate nominatives: " + mention.spanToString() + "\tvs\t" + ant.spanToString());
      return true;
    }

    if(flags.USE_ACRONYM && Rules.entityIsAcronym(document, mentionCluster, potentialAntecedent)) {
      SieveCoreferenceSystem.logger.finest("Acronym: " + mention.spanToString() + "\tvs\t" + ant.spanToString());
      return true;
    }
    if(flags.USE_RELATIVEPRONOUN && Rules.entityIsRelativePronoun(mention, ant)){
      SieveCoreferenceSystem.logger.finest("Relative pronoun: " + mention.spanToString() + "\tvs\t" + ant.spanToString());
      return true;
    }
    if(flags.USE_DEMONYM && mention.isDemonym(ant, dict)){
      SieveCoreferenceSystem.logger.finest("Demonym: " + mention.spanToString() + "\tvs\t" + ant.spanToString());
      return true;
    }

    if(flags.USE_ROLEAPPOSITION && lang != Locale.CHINESE && Rules.entityIsRoleAppositive(mentionCluster, potentialAntecedent, mention, ant, dict)){
      SieveCoreferenceSystem.logger.finest("Role Appositive: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      ret = true;
    }
    if(flags.USE_INCLUSION_HEADMATCH && Rules.entityHeadsAgree(mentionCluster, potentialAntecedent, mention, ant, dict)){
      SieveCoreferenceSystem.logger.finest("Entity heads agree: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      ret = true;
    }
    if(flags.USE_RELAXED_HEADMATCH && Rules.entityRelaxedHeadsAgreeBetweenMentions(mentionCluster, potentialAntecedent, mention, ant) ){
      ret = true;
    }

    if(flags.USE_WORDS_INCLUSION && ret && ! Rules.entityWordsIncluded(mentionCluster, potentialAntecedent, mention, ant)) {
      return false;
    }

    if(flags.USE_INCOMPATIBLE_MODIFIER && ret && Rules.entityHaveIncompatibleModifier(mentionCluster, potentialAntecedent)) {
      return false;
    }
    if(flags.USE_PROPERHEAD_AT_LAST && ret && !Rules.entitySameProperHeadLastWord(mentionCluster, potentialAntecedent, mention, ant)) {
      return false;
    }
    if(flags.USE_ATTRIBUTES_AGREE && !Rules.entityAttributesAgree(mentionCluster, potentialAntecedent)) {
      return false;
    }
    if(flags.USE_DIFFERENT_LOCATION
        && Rules.entityHaveDifferentLocation(mention, ant, dict)) {
      if(flags.USE_PROPERHEAD_AT_LAST  && ret && mention.goldCorefClusterID!=ant.goldCorefClusterID) {
        SieveCoreferenceSystem.logger.finest("DIFFERENT LOCATION: "+ant.spanToString()+" :: "+mention.spanToString());
      }
      return false;
    }
    if(flags.USE_NUMBER_IN_MENTION
        && Rules.entityNumberInLaterMention(mention, ant)) {
      if(flags.USE_PROPERHEAD_AT_LAST  && ret && mention.goldCorefClusterID!=ant.goldCorefClusterID) {
        SieveCoreferenceSystem.logger.finest("NEW NUMBER : "+ant.spanToString()+" :: "+mention.spanToString());
      }
      return false;
    }
    if(flags.USE_WN_HYPERNYM) {
      Method meth = semantics.wordnet.getClass().getMethod("checkHypernym", CorefCluster.class, CorefCluster.class, Mention.class, Mention.class);
      if((Boolean) meth.invoke(semantics.wordnet, mentionCluster, potentialAntecedent, mention, ant)) {
        ret = true;
      } else if (mention.goldCorefClusterID == ant.goldCorefClusterID
          && !mention.isPronominal() && !ant.isPronominal()){
        SieveCoreferenceSystem.logger.finest("not hypernym in WN");
        SieveCoreferenceSystem.logger.finest("False Negatives:: " + ant.spanToString() +" <= "+mention.spanToString());
      }
    }
    if(flags.USE_WN_SYNONYM) {
      Method meth = semantics.wordnet.getClass().getMethod("checkSynonym", new Class[]{Mention.class, Mention.class});
      if((Boolean) meth.invoke(semantics.wordnet, mention, ant)) {
        ret = true;
      } else if (mention.goldCorefClusterID == ant.goldCorefClusterID
          && !mention.isPronominal() && !ant.isPronominal()){
        SieveCoreferenceSystem.logger.finest("not synonym in WN");
        SieveCoreferenceSystem.logger.finest("False Negatives:: " + ant.spanToString() +" <= "+mention.spanToString());
      }
    }

    try {
      if(flags.USE_ALIAS && Rules.entityAlias(mentionCluster, potentialAntecedent, semantics, dict)){
        return true;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    if(flags.USE_DISTANCE && Rules.entityTokenDistance(mention2, ant)){
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
      if(Rules.contextIncompatible(mention2, ant, dict)) return false;

      // Constraint: sentence context incompatibility when the mentions are common nouns
      if(Rules.sentenceContextIncompatible(mention2, ant, dict)) return false;

      if(Rules.entityClusterAllCorefDictionary(mentionCluster, potentialAntecedent, dict, 1, 8)) return true;
      if(Rules.entityCorefDictionary(mention, ant, dict, 2, 2)) return true;
      if(Rules.entityCorefDictionary(mention, ant, dict, 3, 2)) return true;
      if(Rules.entityCorefDictionary(mention, ant, dict, 4, 2)) return true;
    }

    if(flags.DO_PRONOUN){
      Mention m;
      if (mention.predicateNominatives!=null && mention.predicateNominatives.contains(mention2)) {
        m = mention2;
      } else {
        m = mention;
      }

      if((m.isPronominal() || dict.allPronouns.contains(m.toString())) && Rules.entityAttributesAgree(mentionCluster, potentialAntecedent)){

        if(dict.demonymSet.contains(ant.lowercaseNormalizedSpanString()) && dict.notOrganizationPRP.contains(m.headString)){
          document.addIncompatible(m, ant);
          return false;
        }
        if(Constants.USE_DISCOURSE_CONSTRAINTS && Rules.entityPersonDisagree(document, mentionCluster, potentialAntecedent, dict)){
          SieveCoreferenceSystem.logger.finest("Incompatibles: Person Disagree: "+ant.spanToString()+"("+ant.mentionID+") :: "+mention.spanToString()+"("+mention.mentionID+") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
          document.addIncompatible(m, ant);
          return false;
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
      if(flags.DO_PRONOUN && corefClusters.get(m1.corefClusterID).isSinglePronounCluster(dict)) {
        orderedAntecedents = sortMentionsForPronoun(orderedAntecedents, m1, true);
      }
      if(dict.relativePronouns.contains(m1.spanToString())) Collections.reverse(orderedAntecedents);
    } else {    // previous sentence
      orderedAntecedents.addAll(orderedMentionsBySentence.get(antecedentSentence));
    }

    return orderedAntecedents;
  }

  /** Divides a sentence into clauses and sorts the antecedents for pronoun matching. */
  private static List<Mention> sortMentionsForPronoun(List<Mention> l, Mention m1, boolean sameSentence) {
    List<Mention> sorted = new ArrayList<>();
    if (sameSentence) {
      Tree tree = m1.contextParseTree;
      Tree current = m1.mentionSubTree;
      current = current.parent(tree);
      while (current != null) {
        if (current.label().value().startsWith("S")) {
          for (Mention m : l) {
            if (!sorted.contains(m) && current.dominates(m.mentionSubTree)) {
              sorted.add(m);
            }
          }
        }
        current = current.parent(tree);
      }
      if (SieveCoreferenceSystem.logger.isLoggable(Level.FINEST)) {
        if (l.size()!=sorted.size()) {
          SieveCoreferenceSystem.logger.finest("sorting failed!!! -> parser error?? \tmentionID: "+m1.mentionID+" " + m1.spanToString());
          sorted = l;
        } else if ( ! l.equals(sorted)) {
          SieveCoreferenceSystem.logger.finest("sorting succeeded & changed !! \tmentionID: "+m1.mentionID+" " + m1.spanToString());
          for (int i=0; i<l.size(); i++) {
            Mention ml = l.get(i);
            Mention msorted = sorted.get(i);
            SieveCoreferenceSystem.logger.finest("\t["+ml.spanToString()+"]\t["+msorted.spanToString()+"]");
          }
        } else {
          SieveCoreferenceSystem.logger.finest("no changed !! \tmentionID: "+m1.mentionID+" " + m1.spanToString());
        }
      }
    }
    return sorted;
  }

}
