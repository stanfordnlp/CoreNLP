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
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.dcoref.Constants;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.Dictionaries;
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
import edu.stanford.nlp.util.Pair;

/**
 *  Base class for a Coref Sieve.
 *  Each sieve extends this class, and set flags for its own options in the constructor.
 *
 *  @author heeyoung
 *  @author mihais
 */
public abstract class DeterministicCorefSieve  {

  public final SieveOptions flags;

  /** Initialize flagSet */
  public DeterministicCorefSieve() {
    flags = new SieveOptions();
  }

  public void init(Properties props) {
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
          && (m1.spanToString().toLowerCase().startsWith("a ") || m1.spanToString().toLowerCase().startsWith("an "))
          && !flags.USE_EXACTSTRINGMATCH)  {
        skip = true; // A noun phrase starting with an indefinite article - unlikely to have an antecedent (e.g. "A commission" was set up to .... )
      }
      if(dict.indefinitePronouns.contains(m1.spanToString().toLowerCase()))  {
        skip = true; // An indefinite pronoun - unlikely to have an antecedent (e.g. "Some" say that... )
      }
      for(String indef : dict.indefinitePronouns){
        if(m1.spanToString().toLowerCase().startsWith(indef + " ")) {
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
      CorefCluster mentionCluster,
      CorefCluster potentialAntecedent,
      Mention mention,
      Mention ant,
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
      Set<Mention> roleSet,
      Semantics semantics) throws Exception {

    boolean ret = false;
    Mention mention = mentionCluster.getRepresentativeMention();
    if(flags.DO_PRONOUN && Math.abs(mention2.sentNum-ant.sentNum) > 3
        && mention2.person!=Person.I && mention2.person!=Person.YOU) return false;
    if(mention2.spanToString().toLowerCase().equals("this") && Math.abs(mention2.sentNum-ant.sentNum) > 3) return false;
    if(mention2.person==Person.YOU && document.docType==DocType.ARTICLE
        && mention2.headWord.get(CoreAnnotations.SpeakerAnnotation.class).equals("PER0")) return false;
    if(document.conllDoc != null) {
      if(ant.generic && ant.person==Person.YOU) return false;
      if(mention2.generic) return false;
      if(mention2.insideIn(ant) || ant.insideIn(mention2)) return false;
    }

    if(flags.USE_DISCOURSEMATCH) {
      String mString = mention.spanToString().toLowerCase();
      String antString = ant.spanToString().toLowerCase();
      // (I - I) in the same speaker's quotation.
      if(dict.firstPersonPronouns.contains(mString) && mention.number==Number.SINGULAR
          && dict.firstPersonPronouns.contains(antString) && ant.number==Number.SINGULAR
          && Rules.entitySameSpeaker(document, mention, ant)){
        return true;
      }
      // (speaker - I)
      if(Rules.entityIsSpeaker(document, mention, ant, dict) &&
          ((dict.firstPersonPronouns.contains(mString) && mention.number==Number.SINGULAR)
              || (dict.firstPersonPronouns.contains(antString) && ant.number==Number.SINGULAR))) {
        return true;
      }
      if(Rules.entitySameSpeaker(document, mention, ant)
          && dict.secondPersonPronouns.contains(mString)
          && dict.secondPersonPronouns.contains(antString)) {
        return true;
      }
      // previous I - you or previous you - I in two person conversation
      if(((mention.person==Person.I && ant.person==Person.YOU
          || (mention.person==Person.YOU && ant.person==Person.I))
          && (mention.headWord.get(CoreAnnotations.UtteranceAnnotation.class)-ant.headWord.get(CoreAnnotations.UtteranceAnnotation.class) == 1)
          && document.docType==DocType.CONVERSATION)) {
        SieveCoreferenceSystem.logger.finest("discourse match: between two person");
        return true;
      }
      if(dict.reflexivePronouns.contains(mention.headString) && Rules.entitySubjectObject(mention, ant)){
        SieveCoreferenceSystem.logger.finest("reflexive pronoun: "+ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID==ant.goldCorefClusterID));
        return true;
      }
    }
    if(Constants.USE_DISCOURSE_CONSTRAINTS && !flags.USE_EXACTSTRINGMATCH && !flags.USE_RELAXED_EXACTSTRINGMATCH
        && !flags.USE_APPOSITION && !flags.USE_WORDS_INCLUSION) {
      for(Mention m : mentionCluster.getCorefMentions()) {
        for(Mention a : potentialAntecedent.getCorefMentions()){
          if(Rules.entityIsSpeaker(document, m, a, dict) && m.person!=Person.I && a.person!=Person.I) {
            SieveCoreferenceSystem.logger.finest("Incompatibles: not match(speaker): " +ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
            document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, a.mentionID), Math.max(m.mentionID, a.mentionID)));
            return false;
          }
          int dist = Math.abs(m.headWord.get(CoreAnnotations.UtteranceAnnotation.class) - a.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
          if(document.docType!=DocType.ARTICLE && dist==1 && !Rules.entitySameSpeaker(document, m, a)) {
            if(m.person==Person.I && a.person==Person.I) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: neighbor I: " +ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
              document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, a.mentionID), Math.max(m.mentionID, a.mentionID)));
              return false;
            }
            if(m.person==Person.YOU && a.person==Person.YOU) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: neighbor YOU: " +ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
              document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, a.mentionID), Math.max(m.mentionID, a.mentionID)));
              return false;
            }
            if(m.person==Person.WE && a.person==Person.WE) {
              SieveCoreferenceSystem.logger.finest("Incompatibles: neighbor WE: " +ant.spanToString()+"("+ant.mentionID + ") :: "+ mention.spanToString()+"("+mention.mentionID + ") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
              document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, a.mentionID), Math.max(m.mentionID, a.mentionID)));
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
              document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, a.mentionID), Math.max(m.mentionID, a.mentionID)));
              return false;
            }
          }
        }
      }
    }

    if(flags.USE_iwithini && Rules.entityIWithinI(mention, ant, dict)) {
      document.incompatibles.add(new Pair<Integer, Integer>(Math.min(mention.mentionID, ant.mentionID), Math.max(mention.mentionID, ant.mentionID)));
      return false;
    }
    if(flags.USE_EXACTSTRINGMATCH && Rules.entityExactStringMatch(mentionCluster, potentialAntecedent, dict, roleSet)){
      return true;
    }
    if(flags.USE_RELAXED_EXACTSTRINGMATCH && Rules.entityRelaxedExactStringMatch(mentionCluster, potentialAntecedent, mention, ant, dict, roleSet)){
      return true;
    }
    if(flags.USE_APPOSITION && Rules.entityIsApposition(mentionCluster, potentialAntecedent, mention, ant)) {
      SieveCoreferenceSystem.logger.finest("Apposition: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      return true;
    }
    if(flags.USE_PREDICATENOMINATIVES && Rules.entityIsPredicateNominatives(mentionCluster, potentialAntecedent, mention, ant)) {
      SieveCoreferenceSystem.logger.finest("Predicate nominatives: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      return true;
    }

    if(flags.USE_ACRONYM && Rules.entityIsAcronym(mentionCluster, potentialAntecedent)) {
      SieveCoreferenceSystem.logger.finest("Acronym: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      return true;
    }
    if(flags.USE_RELATIVEPRONOUN && Rules.entityIsRelativePronoun(mention, ant)){
      SieveCoreferenceSystem.logger.finest("Relative pronoun: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      return true;
    }
    if(flags.USE_DEMONYM && mention.isDemonym(ant, dict)){
      SieveCoreferenceSystem.logger.finest("Demonym: "+mention.spanToString()+"\tvs\t"+ant.spanToString());
      return true;
    }

    if(flags.USE_ROLEAPPOSITION && Rules.entityIsRoleAppositive(mentionCluster, potentialAntecedent, mention, ant, dict)){
      return true;
    }
    if(flags.USE_INCLUSION_HEADMATCH && Rules.entityHeadsAgree(mentionCluster, potentialAntecedent, mention, ant, dict)){
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
    if(flags.DO_PRONOUN){
      Mention m;
      if (mention.predicateNominatives!=null && mention.predicateNominatives.contains(mention2)) {
        m = mention2;
      } else {
        m = mention;
      }

      if((m.isPronominal() || dict.allPronouns.contains(m.toString())) && Rules.entityAttributesAgree(mentionCluster, potentialAntecedent)){

        if(dict.demonymSet.contains(ant.spanToString().toLowerCase()) && dict.notOrganizationPRP.contains(m.headString)){
          document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, ant.mentionID), Math.max(m.mentionID, ant.mentionID)));
          return false;
        }
        if(Constants.USE_DISCOURSE_CONSTRAINTS && Rules.entityPersonDisagree(document, mentionCluster, potentialAntecedent, dict)){
          SieveCoreferenceSystem.logger.finest("Incompatibles: Person Disagree: "+ant.spanToString()+"("+ant.mentionID+") :: "+mention.spanToString()+"("+mention.mentionID+") -> "+(mention.goldCorefClusterID!=ant.goldCorefClusterID));
          document.incompatibles.add(new Pair<Integer, Integer>(Math.min(m.mentionID, ant.mentionID), Math.max(m.mentionID, ant.mentionID)));
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
    List<Mention> orderedAntecedents = new ArrayList<Mention>();

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

  /** Divides a sentence into clauses and sort the antecedents for pronoun matching  */
  private static List<Mention> sortMentionsForPronoun(List<Mention> l, Mention m1, boolean sameSentence) {
    List<Mention> sorted = new ArrayList<Mention>();
    Tree tree = m1.contextParseTree;
    Tree current = m1.mentionSubTree;
    if(sameSentence){
      while(true){
        current = current.ancestor(1, tree);
        if(current.label().value().startsWith("S")){
          for(Mention m : l){
            if(!sorted.contains(m) && current.dominates(m.mentionSubTree)) sorted.add(m);
          }
        }
        if(current.label().value().equals("ROOT") || current.ancestor(1, tree)==null) break;
      }
      if(l.size()!=sorted.size()) {
        SieveCoreferenceSystem.logger.finest("sorting failed!!! -> parser error?? \tmentionID: "+m1.mentionID+" " + m1.spanToString());
        sorted=l;
      } else if(!l.equals(sorted)){
        SieveCoreferenceSystem.logger.finest("sorting succeeded & changed !! \tmentionID: "+m1.mentionID+" " + m1.spanToString());
        for(int i=0; i<l.size(); i++){
          Mention ml = l.get(i);
          Mention msorted = sorted.get(i);
          SieveCoreferenceSystem.logger.finest("\t["+ml.spanToString()+"]\t["+msorted.spanToString()+"]");
        }
      } else {
        SieveCoreferenceSystem.logger.finest("no changed !! \tmentionID: "+m1.mentionID+" " + m1.spanToString());
      }
    }
    return sorted;
  }

}



