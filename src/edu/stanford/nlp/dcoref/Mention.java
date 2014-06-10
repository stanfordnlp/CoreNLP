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

package edu.stanford.nlp.dcoref;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.dcoref.Dictionaries.Animacy;
import edu.stanford.nlp.dcoref.Dictionaries.Gender;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.Dictionaries.Number;
import edu.stanford.nlp.dcoref.Dictionaries.Person;
import edu.stanford.nlp.dcoref.Semantics;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * One mention for the SieveCoreferenceSystem
 * @author Jenny Finkel, Karthik Raghunathan, Heeyoung Lee
 */
public class Mention implements CoreAnnotation<Mention>, Serializable {

  private static final long serialVersionUID = -7524485803945717057L;

  public Mention() {
  }
  public Mention(int mentionID, int startIndex, int endIndex, SemanticGraph dependency){
    this.mentionID = mentionID;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.dependency = dependency;
  }
  public Mention(int mentionID, int startIndex, int endIndex, SemanticGraph dependency, List<CoreLabel> mentionSpan){
    this.mentionID = mentionID;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.dependency = dependency;
    this.originalSpan = mentionSpan;
  }
  public Mention(int mentionID, int startIndex, int endIndex, SemanticGraph dependency, List<CoreLabel> mentionSpan, Tree mentionTree){
    this.mentionID = mentionID;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.dependency = dependency;
    this.originalSpan = mentionSpan;
    this.mentionSubTree = mentionTree;
  }

  public MentionType mentionType;
  public Number number;
  public edu.stanford.nlp.dcoref.Dictionaries.Gender gender;
  public Animacy animacy;
  public Person person;
  public String headString;
  public String nerString;

  public int startIndex;
  public int endIndex;
  public int headIndex;
  public int mentionID = -1;
  public int originalRef = -1;

  public int goldCorefClusterID = -1;
  public int corefClusterID = -1;
  public int sentNum = -1;
  public int utter = -1;
  public int paragraph = -1;
  public boolean isSubject;
  public boolean isDirectObject;
  public boolean isIndirectObject;
  public boolean isPrepositionObject;
  public IndexedWord dependingVerb;
  public boolean twinless = true;
  public boolean generic = false;   // generic pronoun or generic noun (bare plurals)

  public List<CoreLabel> sentenceWords;
  public List<CoreLabel> originalSpan;

  public Tree mentionSubTree;
  public Tree contextParseTree;
  public CoreLabel headWord;
  public SemanticGraph dependency;
  public Set<String> dependents = new HashSet<String>();
  public List<String> preprocessedTerms;
  public Object synsets;

  /** Set of other mentions in the same sentence that are syntactic appositions to this */
  public Set<Mention> appositions = null;
  public Set<Mention> predicateNominatives = null;
  public Set<Mention> relativePronouns = null;

  public Class<Mention> getType() {  return Mention.class; }

  public boolean isPronominal() {
    return mentionType == MentionType.PRONOMINAL;
  }

  @Override
  public String toString() {
    //    return headWord.toString();
    return spanToString();
  }

  public String spanToString() {
    StringBuilder os = new StringBuilder();
    for(int i = 0; i < originalSpan.size(); i ++){
      if(i > 0) os.append(" ");
      os.append(originalSpan.get(i).get(CoreAnnotations.TextAnnotation.class));
    }
    return os.toString();
  }

  /** Set attributes of a mention:
   * head string, mention type, NER label, Number, Gender, Animacy
   * @throws Exception */
  public void process(Dictionaries dict, Semantics semantics, MentionExtractor mentionExtractor) throws Exception {
    setHeadString();
    setType(dict);
    setNERString();
    List<String> mStr = getMentionString();
    setNumber(dict, getNumberCount(dict, mStr));
    setGender(dict, getGenderCount(dict, mStr));
    setAnimacy(dict);
    setPerson(dict);
    setDiscourse();

    if(semantics!=null) setSemantics(dict, semantics, mentionExtractor);
  }

  private List<String> getMentionString() {
    List<String> mStr = new ArrayList<String>();
    for(CoreLabel l : this.originalSpan) {
      mStr.add(l.get(CoreAnnotations.TextAnnotation.class).toLowerCase());
      if(l==this.headWord) break;   // remove words after headword
    }
    return mStr;
  }
  private int[] getNumberCount(Dictionaries dict, List<String> mStr) {
    int len = mStr.size();
    if(len > 1) {
      for(int i = 0 ; i < len-1 ; i++) {
        if(dict.genderNumber.containsKey(mStr.subList(i, len))) return dict.genderNumber.get(mStr.subList(i, len));
      }

      // find converted string with ! (e.g., "dr. martin luther king jr. boulevard" -> "! boulevard")
      List<String> convertedStr = new ArrayList<String>();
      convertedStr.add("!");
      convertedStr.add(mStr.get(len-1));
      if(dict.genderNumber.containsKey(convertedStr)) return dict.genderNumber.get(convertedStr);
    }
    if(dict.genderNumber.containsKey(mStr.subList(len-1, len))) return dict.genderNumber.get(mStr.subList(len-1, len));

    return null;
  }
  private int[] getGenderCount(Dictionaries dict, List<String> mStr) {
    int len = mStr.size();
    char firstLetter = headWord.get(CoreAnnotations.TextAnnotation.class).charAt(0);
    if(len > 1 && Character.isUpperCase(firstLetter) && nerString.startsWith("PER")) {
      int firstNameIdx = len-2;
      String secondToLast = mStr.get(firstNameIdx);
      if(firstNameIdx > 1 && (secondToLast.length()==1 || (secondToLast.length()==2 && secondToLast.endsWith(".")))) {
        firstNameIdx--;
      }

      for(int i = 0 ; i <= firstNameIdx ; i++){
        if(dict.genderNumber.containsKey(mStr.subList(i, len))) return dict.genderNumber.get(mStr.subList(i, len));
      }

      // find converted string with ! (e.g., "dr. martin luther king jr. boulevard" -> "dr. !")
      List<String> convertedStr = new ArrayList<String>();
      convertedStr.add(mStr.get(firstNameIdx));
      convertedStr.add("!");
      if(dict.genderNumber.containsKey(convertedStr)) return dict.genderNumber.get(convertedStr);

      if(dict.genderNumber.containsKey(mStr.subList(firstNameIdx, firstNameIdx+1))) return dict.genderNumber.get(mStr.subList(firstNameIdx, firstNameIdx+1));
    }

    if(dict.genderNumber.containsKey(mStr.subList(len-1, len))) return dict.genderNumber.get(mStr.subList(len-1, len));
    return null;
  }
  private void setDiscourse() {
    utter = headWord.get(CoreAnnotations.UtteranceAnnotation.class);

    Pair<IndexedWord, String> verbDependency = findDependentVerb(this);
    String dep = verbDependency.second();
    dependingVerb = verbDependency.first();

    isSubject = false;
    isDirectObject = false;
    isIndirectObject = false;
    isPrepositionObject = false;

    if(dep==null) {
      return ;
    } else if(dep.equals("nsubj") || dep.equals("csubj")) {
      isSubject = true;
    } else if(dep.equals("dobj")){
      isDirectObject = true;
    } else if(dep.equals("iobj")){
      isIndirectObject = true;
    } else if(dep.equals("pobj")){
      isPrepositionObject = true;
    }
  }

  private void setPerson(Dictionaries dict) {
    // only do for pronoun
    if(!this.isPronominal()) person = Person.UNKNOWN;
    String spanToString = this.spanToString().toLowerCase();

    if(dict.firstPersonPronouns.contains(spanToString)) {
      if (number == Number.SINGULAR) {
        person = Person.I;
      } else if (number == Number.PLURAL) {
        person = Person.WE;
      } else {
        person = Person.UNKNOWN;
      }
    } else if(dict.secondPersonPronouns.contains(spanToString)) {
      person = Person.YOU;
    } else if(dict.thirdPersonPronouns.contains(spanToString)) {
      if (gender == Gender.MALE && number == Number.SINGULAR) {
        person = Person.HE;
      } else if (gender == Gender.FEMALE && number == Number.SINGULAR) {
        person = Person.SHE;
      } else if ((gender == Gender.NEUTRAL || animacy == Animacy.INANIMATE) && number == Number.SINGULAR) {
        person = Person.IT;
      } else if (number == Number.PLURAL) {
        person = Person.THEY;
      } else {
        person = Person.UNKNOWN;
      }
    } else {
      person = Person.UNKNOWN;
    }
  }

  private void setSemantics(Dictionaries dict, Semantics semantics, MentionExtractor mentionExtractor) throws Exception {

    preprocessedTerms = this.preprocessSearchTerm();

    if(dict.statesAbbreviation.containsKey(this.spanToString())) {  // states abbreviations
      preprocessedTerms = new ArrayList<String>();
      preprocessedTerms.add(dict.statesAbbreviation.get(this.spanToString()));
    }

    Method meth = semantics.wordnet.getClass().getDeclaredMethod("findSynset", List.class);
    synsets = meth.invoke(semantics.wordnet, new Object[]{preprocessedTerms});

    if(this.isPronominal()) return;
  }
  private void setType(Dictionaries dict) {
    if (headWord.has(CoreAnnotations.EntityTypeAnnotation.class)){    // ACE gold mention type
      if (headWord.get(CoreAnnotations.EntityTypeAnnotation.class).equals("PRO")) {
        mentionType = MentionType.PRONOMINAL;
      } else if (headWord.get(CoreAnnotations.EntityTypeAnnotation.class).equals("NAM")) {
        mentionType = MentionType.PROPER;
      } else {
        mentionType = MentionType.NOMINAL;
      }
    } else {    // MUC
      if(!headWord.has(CoreAnnotations.NamedEntityTagAnnotation.class)) {   // temporary fix
        mentionType = MentionType.NOMINAL;
        SieveCoreferenceSystem.logger.finest("no NamedEntityTagAnnotation: "+headWord);
      } else if (headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("PRP")
          || (originalSpan.size() == 1 && headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("O")
              && (dict.allPronouns.contains(headString) || dict.relativePronouns.contains(headString) ))) {
        mentionType = MentionType.PRONOMINAL;
      } else if (!headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("O") || headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NNP")) {
        mentionType = MentionType.PROPER;
      } else {
        mentionType = MentionType.NOMINAL;
      }
    }
  }

  private void setGender(Dictionaries dict, int[] genderNumberCount) {
    gender = Gender.UNKNOWN;
    if (mentionType == MentionType.PRONOMINAL) {
      if (dict.malePronouns.contains(headString)) {
        gender = Gender.MALE;
      } else if (dict.femalePronouns.contains(headString)) {
        gender = Gender.FEMALE;
      }
    } else {
      if(Constants.USE_GENDER_LIST){
        // Bergsma list
        if(gender == Gender.UNKNOWN)  {
          if(dict.maleWords.contains(headString)) {
            gender = Gender.MALE;
            SieveCoreferenceSystem.logger.finest("[Bergsma List] New gender assigned:\tMale:\t" +  headString);
          }
          else if(dict.femaleWords.contains(headString))  {
            gender = Gender.FEMALE;
            SieveCoreferenceSystem.logger.finest("[Bergsma List] New gender assigned:\tFemale:\t" +  headString);
          }
          else if(dict.neutralWords.contains(headString))   {
            gender = Gender.NEUTRAL;
            SieveCoreferenceSystem.logger.finest("[Bergsma List] New gender assigned:\tNeutral:\t" +  headString);
          }
        }
      }
      if(genderNumberCount!=null && this.number!=Number.PLURAL){
        double male = genderNumberCount[0];
        double female = genderNumberCount[1];
        double neutral = genderNumberCount[2];

        if (male * 0.5 > female + neutral && male > 2) {
          this.gender = Gender.MALE;
        } else if (female * 0.5 > male + neutral && female > 2) {
          this.gender = Gender.FEMALE;
        } else if (neutral * 0.5 > male + female && neutral > 2)
          this.gender = Gender.NEUTRAL;
      }
    }
  }

  protected void setNumber(Dictionaries dict, int[] genderNumberCount) {
    if (mentionType == MentionType.PRONOMINAL) {
      if (dict.pluralPronouns.contains(headString)) {
        number = Number.PLURAL;
      } else if (dict.singularPronouns.contains(headString)) {
        number = Number.SINGULAR;
      } else {
        number = Number.UNKNOWN;
      }
    } else if(! nerString.equals("O") && mentionType!=MentionType.NOMINAL){
      if(! (nerString.equals("ORGANIZATION") || nerString.startsWith("ORG"))){
        number = Number.SINGULAR;
      } else {
        // ORGs can be both plural and singular
        number = Number.UNKNOWN;
      }
    } else {
      String tag = headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if (tag.startsWith("N") && tag.endsWith("S")) {
        number = Number.PLURAL;
      } else if (tag.startsWith("N")) {
        number = Number.SINGULAR;
      } else {
        number = Number.UNKNOWN;
      }
    }

    if(mentionType != MentionType.PRONOMINAL) {
      if(Constants.USE_NUMBER_LIST){
        if(number == Number.UNKNOWN){
          if(dict.singularWords.contains(headString)) {
            number = Number.SINGULAR;
            SieveCoreferenceSystem.logger.finest("[Bergsma] Number set to:\tSINGULAR:\t" + headString);
          }
          else if(dict.pluralWords.contains(headString))  {
            number = Number.PLURAL;
            SieveCoreferenceSystem.logger.finest("[Bergsma] Number set to:\tPLURAL:\t" + headString);
          }
        }
      }

      String enumerationPattern = "NP < (NP=tmp $.. (/,|CC/ $.. NP))";

      TregexPattern tgrepPattern = TregexPattern.compile(enumerationPattern);
      TregexMatcher m = tgrepPattern.matcher(this.mentionSubTree);
      while (m.find()) {
        //        Tree t = m.getMatch();
        if(this.mentionSubTree==m.getNode("tmp")
           && this.spanToString().toLowerCase().contains(" and ")) {
          number = Number.PLURAL;
        }
      }
    }
  }

  private void setAnimacy(Dictionaries dict) {
    if (mentionType == MentionType.PRONOMINAL) {
      if (dict.animatePronouns.contains(headString)) {
        animacy = Animacy.ANIMATE;
      } else if (dict.inanimatePronouns.contains(headString)) {
        animacy = Animacy.INANIMATE;
      } else {
        animacy = Animacy.UNKNOWN;
      }
    } else if (nerString.equals("PERSON") || nerString.startsWith("PER")) {
      animacy = Animacy.ANIMATE;
    } else if (nerString.equals("LOCATION")|| nerString.startsWith("LOC")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.equals("MONEY")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.equals("NUMBER")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.equals("PERCENT")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.equals("DATE")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.equals("TIME")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.equals("MISC")) {
      animacy = Animacy.UNKNOWN;
    } else if (nerString.startsWith("VEH")) {
      animacy = Animacy.UNKNOWN;
    } else if (nerString.startsWith("FAC")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.startsWith("GPE")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.startsWith("WEA")) {
      animacy = Animacy.INANIMATE;
    } else if (nerString.startsWith("ORG")) {
      animacy = Animacy.INANIMATE;
    } else {
      animacy = Animacy.UNKNOWN;
    }
    if(mentionType != MentionType.PRONOMINAL) {
      if(Constants.USE_ANIMACY_LIST){
        // Better heuristics using DekangLin:
        if(animacy == Animacy.UNKNOWN)  {
          if(dict.animateWords.contains(headString))  {
            animacy = Animacy.ANIMATE;
            SieveCoreferenceSystem.logger.finest("Assigned Dekang Lin animacy:\tANIMATE:\t" + headString);
          }
          else if(dict.inanimateWords.contains(headString)) {
            animacy = Animacy.INANIMATE;
            SieveCoreferenceSystem.logger.finest("Assigned Dekang Lin animacy:\tINANIMATE:\t" + headString);
          }
        }
      }
    }
  }

  private static final String [] commonNESuffixes = {
    "Corp", "Co", "Inc", "Ltd"
  };
  private static boolean knownSuffix(String s) {
    if(s.endsWith(".")) s = s.substring(0, s.length() - 1);
    for(String suff: commonNESuffixes){
      if(suff.equalsIgnoreCase(s)){
        return true;
      }
    }
    return false;
  }

  private void setHeadString() {
    this.headString = headWord.get(CoreAnnotations.TextAnnotation.class).toLowerCase();
    if(headWord.has(CoreAnnotations.NamedEntityTagAnnotation.class)) {
      // make sure that the head of a NE is not a known suffix, e.g., Corp.
      int start = headIndex - startIndex;
      if (start >= originalSpan.size()) {
        throw new RuntimeException("Invalid start index " + start + "=" + headIndex + "-" + startIndex
                + ": originalSpan=[" + StringUtils.joinWords(originalSpan, " ") + "], head=" + headWord);
      }
      while(start >= 0){
        String head = originalSpan.get(start).get(CoreAnnotations.TextAnnotation.class).toLowerCase();
        if(knownSuffix(head) == false){
          this.headString = head;
          break;
        } else {
          start --;
        }
      }
    }
  }

  private void setNERString() {
    if(headWord.has(CoreAnnotations.EntityTypeAnnotation.class)){ // ACE
      if(headWord.has(CoreAnnotations.NamedEntityTagAnnotation.class) && headWord.get(CoreAnnotations.EntityTypeAnnotation.class).equals("NAM")){
        this.nerString = headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      } else {
        this.nerString = "O";
      }
    }
    else{ // MUC
      if (headWord.has(CoreAnnotations.NamedEntityTagAnnotation.class)) {
        this.nerString = headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      } else {
        this.nerString = "O";
      }
    }
  }

  public boolean sameSentence(Mention m) {
    return m.sentenceWords == sentenceWords;
  }

  private static boolean included(CoreLabel small, List<CoreLabel> big) {
    if(small.tag().equals("NNP")){
      for(CoreLabel w: big){
        if(small.word().equals(w.word()) ||
            small.word().length() > 2 && w.word().startsWith(small.word())){
          return true;
        }
      }
    }
    return false;
  }

  protected boolean headsAgree(Mention m) {
    // we allow same-type NEs to not match perfectly, but rather one could be included in the other, e.g., "George" -> "George Bush"
    if (!nerString.equals("O") && !m.nerString.equals("O") && nerString.equals(m.nerString) &&
            (included(headWord, m.originalSpan) || included(m.headWord, originalSpan))) {
      return true;
    }
    return headString.equals(m.headString);
  }

  public boolean numbersAgree(Mention m){
    return numbersAgree(m, false);
  }
  private boolean numbersAgree(Mention m, boolean strict) {
    if (strict) {
      return number == m.number;
    } else {
      return number == Number.UNKNOWN ||
              m.number == Number.UNKNOWN ||
              number == m.number;
    }
  }

  public boolean gendersAgree(Mention m){
    return gendersAgree(m, false);
  }
  public boolean gendersAgree(Mention m, boolean strict) {
    if (strict) {
      return gender == m.gender;
    } else {
      return gender == Gender.UNKNOWN ||
              m.gender == Gender.UNKNOWN ||
              gender == m.gender;
    }
  }

  public boolean animaciesAgree(Mention m){
    return animaciesAgree(m, false);
  }
  public boolean animaciesAgree(Mention m, boolean strict) {
    if (strict) {
      return animacy == m.animacy;
    } else {
      return animacy == Animacy.UNKNOWN ||
              m.animacy == Animacy.UNKNOWN ||
              animacy == m.animacy;
    }
  }

  public boolean entityTypesAgree(Mention m, Dictionaries dict){
    return entityTypesAgree(m, dict, false);
  }

  public boolean entityTypesAgree(Mention m, Dictionaries dict, boolean strict) {
    if (strict) {
      return nerString.equals(m.nerString);
    } else {
      if (isPronominal()) {
        if (nerString.contains("-") || m.nerString.contains("-")) { //for ACE with gold NE
          if (m.nerString.equals("O")) {
            return true;
          } else if (m.nerString.startsWith("ORG")) {
            return dict.organizationPronouns.contains(headString);
          } else if (m.nerString.startsWith("PER")) {
            return dict.personPronouns.contains(headString);
          } else if (m.nerString.startsWith("LOC")) {
            return dict.locationPronouns.contains(headString);
          } else if (m.nerString.startsWith("GPE")) {
            return dict.GPEPronouns.contains(headString);
          } else if (m.nerString.startsWith("VEH") || m.nerString.startsWith("FAC") || m.nerString.startsWith("WEA")) {
            return dict.facilityVehicleWeaponPronouns.contains(headString);
          } else {
            return false;
          }
        } else {  // ACE w/o gold NE or MUC
          if (m.nerString.equals("O")) {
            return true;
          } else if (m.nerString.equals("MISC")) {
            return true;
          } else if (m.nerString.equals("ORGANIZATION")) {
            return dict.organizationPronouns.contains(headString);
          } else if (m.nerString.equals("PERSON")) {
            return dict.personPronouns.contains(headString);
          } else if (m.nerString.equals("LOCATION")) {
            return dict.locationPronouns.contains(headString);
          } else if (m.nerString.equals("DATE") || m.nerString.equals("TIME")) {
            return dict.dateTimePronouns.contains(headString);
          } else if (m.nerString.equals("MONEY") || m.nerString.equals("PERCENT") || m.nerString.equals("NUMBER")) {
            return dict.moneyPercentNumberPronouns.contains(headString);
          } else {
            return false;
          }
        }
      }
      return nerString.equals("O") ||
              m.nerString.equals("O") ||
              nerString.equals(m.nerString);
    }
  }



  /**
   * Verifies if this mention's tree is dominated by the tree of the given mention
   */
  public boolean includedIn(Mention m) {
    if (!m.sameSentence(this)) {
      return false;
    }
    if(this.startIndex < m.startIndex || this.endIndex > m.endIndex) return false;
    for (Tree t : m.mentionSubTree.subTrees()) {
      if (t == mentionSubTree) {
        return true;
      }
    }
    return false;
  }

  /**
   * Detects if the mention and candidate antecedent agree on all attributes respectively.
   * @param potentialAntecedent
   * @return true if all attributes agree between both mention and candidate, else false.
   */
  public boolean attributesAgree(Mention potentialAntecedent, Dictionaries dict){
    return (this.animaciesAgree(potentialAntecedent) &&
        this.entityTypesAgree(potentialAntecedent, dict) &&
        this.gendersAgree(potentialAntecedent) &&
        this.numbersAgree(potentialAntecedent));
  }

  /** Find apposition */
  public void addApposition(Mention m) {
    if(appositions == null) appositions = new HashSet<Mention>();
    appositions.add(m);
  }

  /** Check apposition */
  public boolean isApposition(Mention m) {
    if(appositions != null && appositions.contains(m)) return true;
    return false;
  }
  /** Find predicate nominatives */
  public void addPredicateNominatives(Mention m) {
    if(predicateNominatives == null) predicateNominatives = new HashSet<Mention>();
    predicateNominatives.add(m);
  }

  /** Check predicate nominatives */
  public boolean isPredicateNominatives(Mention m) {
    if(predicateNominatives != null && predicateNominatives.contains(m)) return true;
    return false;
  }

  /** Find relative pronouns */
  public void addRelativePronoun(Mention m) {
    if(relativePronouns == null) relativePronouns = new HashSet<Mention>();
    relativePronouns.add(m);
  }

  /** Find which mention appears first in a document */
  public boolean appearEarlierThan(Mention m){
    if (this.sentNum < m.sentNum) {
      return true;
    } else if (this.sentNum > m.sentNum) {
      return false;
    } else {
      if (this.startIndex < m.startIndex) {
        return true;
      } else if (this.startIndex > m.startIndex) {
        return false;
      } else {
        if (this.endIndex > m.endIndex) {
          return true;
        } else {
          return false;
        }
      }
    }
  }

  public String longestNNPEndsWithHead (){
    String ret = "";
    for (int i = headIndex; i >=startIndex ; i--){
      String pos = sentenceWords.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(!pos.startsWith("NNP")) break;
      if(!ret.equals("")) ret = " "+ret;
      ret = sentenceWords.get(i).get(CoreAnnotations.TextAnnotation.class)+ret;
    }
    return ret;
  }

  public String lowestNPIncludesHead (){
    String ret = "";
    Tree head = this.contextParseTree.getLeaves().get(this.headIndex);
    Tree lowestNP = head;
    String s;
    while(true) {
      if(lowestNP==null) return ret;
      s = ((CoreLabel) lowestNP.label()).get(CoreAnnotations.ValueAnnotation.class);
      if(s.equals("NP") || s.equals("ROOT")) break;
      lowestNP = lowestNP.ancestor(1, this.contextParseTree);
    }
    if (s.equals("ROOT")) lowestNP = head;
    for (Tree t : lowestNP.getLeaves()){
      if (!ret.equals("")) ret = ret + " ";
      ret = ret + ((CoreLabel) t.label()).get(CoreAnnotations.TextAnnotation.class);
    }
    if(!this.spanToString().contains(ret)) return this.sentenceWords.get(this.headIndex).get(CoreAnnotations.TextAnnotation.class);
    return ret;
  }

  public String stringWithoutArticle(String str) {
    String ret = (str==null)? this.spanToString() : str;
    if (ret.startsWith("a ") || ret.startsWith("A ")) {
      return ret.substring(2);
    } else if (ret.startsWith("an ") || ret.startsWith("An ")) {
      return ret.substring(3);
    } else if (ret.startsWith("the ") || ret.startsWith("The "))
      return ret.substring(4);
    return ret;
  }

  public List<String> preprocessSearchTerm (){
    List<String> searchTerms = new ArrayList<String>();
    String[] terms = new String[4];

    terms[0] = this.stringWithoutArticle(this.removePhraseAfterHead());
    terms[1] = this.stringWithoutArticle(this.lowestNPIncludesHead());
    terms[2] = this.stringWithoutArticle(this.longestNNPEndsWithHead());
    terms[3] = this.headString;

    for (String term : terms){

      if(term.contains("\"")) term = term.replace("\"", "\\\"");
      if(term.contains("(")) term = term.replace("(","\\(");
      if(term.contains(")")) term = term.replace(")", "\\)");
      if(term.contains("!")) term = term.replace("!", "\\!");
      if(term.contains(":")) term = term.replace(":", "\\:");
      if(term.contains("+")) term = term.replace("+", "\\+");
      if(term.contains("-")) term = term.replace("-", "\\-");
      if(term.contains("~")) term = term.replace("~", "\\~");
      if(term.contains("*")) term = term.replace("*", "\\*");
      if(term.contains("[")) term = term.replace("[", "\\[");
      if(term.contains("]")) term = term.replace("]", "\\]");
      if(term.contains("^")) term = term.replace("^", "\\^");
      if(term.equals("")) continue;

      if(term.equals("") || searchTerms.contains(term)) continue;
      if(term.equals(terms[3]) && !terms[2].equals("")) continue;
      searchTerms.add(term);
    }
    return searchTerms;
  }
  public String buildQueryText(List<String> terms) {
    String query = "";
    for (String t : terms){
      query += t + " ";
    }
    return query.trim();
  }

  /** Remove any clause after headword */
  public String removePhraseAfterHead(){
    String removed ="";
    int posComma = -1;
    int posWH = -1;
    for(int i = 0 ; i < this.originalSpan.size() ; i++){
      CoreLabel w = this.originalSpan.get(i);
      if(posComma == -1 && w.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals(",")) posComma = this.startIndex + i;
      if(posWH == -1 && w.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("W")) posWH = this.startIndex + i;
    }
    if(posComma!=-1 && this.headIndex < posComma){
      StringBuilder os = new StringBuilder();
      for(int i = 0; i < posComma-this.startIndex; i++){
        if(i > 0) os.append(" ");
        os.append(this.originalSpan.get(i).get(CoreAnnotations.TextAnnotation.class));
      }
      removed = os.toString();
    }
    if(posComma==-1 && posWH != -1 && this.headIndex < posWH){
      StringBuilder os = new StringBuilder();
      for(int i = 0; i < posWH-this.startIndex; i++){
        if(i > 0) os.append(" ");
        os.append(this.originalSpan.get(i).get(CoreAnnotations.TextAnnotation.class));
      }
      removed = os.toString();
    }
    if(posComma==-1 && posWH == -1){
      removed = this.spanToString();
    }
    return removed;
  }
  
  public static String removeParenthesis(String text) {
    if (text.split("\\(").length > 0) {
      return text.split("\\(")[0].trim();
    } else {
      return "";
    }
  }

  // the mention is 'the + commonNoun' form
  protected boolean isTheCommonNoun() {
    if (this.mentionType == MentionType.NOMINAL
         && this.spanToString().toLowerCase().startsWith("the ")
         && this.spanToString().split(" ").length == 2) {
      return true;
    } else {
      return false;
    }
  }

  private static Pair<IndexedWord, String> findDependentVerb(Mention m) {
    Pair<IndexedWord, String> ret = new Pair<IndexedWord, String>();
    int headIndex = m.headIndex+1;
    try {
      IndexedWord w = m.dependency.getNodeByIndex(headIndex);
      if(w==null) return ret;
      while (true) {
        IndexedWord p = null;
        for(Pair<GrammaticalRelation,IndexedWord> parent : m.dependency.parentPairs(w)){
          if(ret.second()==null) {
            String relation = parent.first().getShortName();
            ret.setSecond(relation);
          }
          p = parent.second();
        }
        if(p==null || p.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("V")) {
          ret.setFirst(p);
          break;
        }
        if(w==p) return ret;
        w = p;
      }
    } catch (Exception e) {
      return ret;
    }
    return ret;
  }
  public boolean insideIn(Mention m){
    if (this.sentNum == m.sentNum
         && m.startIndex <= this.startIndex
         && this.endIndex <= m.endIndex) {
      return true;
    } else {
      return false;
    }
  }
  public boolean moreRepresentativeThan(Mention m){
    if(m==null) return true;
    if(mentionType!=m.mentionType) {
      if ((mentionType == MentionType.PROPER && m.mentionType != MentionType.PROPER)
           || (mentionType == MentionType.NOMINAL && m.mentionType == MentionType.PRONOMINAL)) {
        return true;
      } else {
        return false;
      }
    } else {
      if (headIndex - startIndex > m.headIndex - m.startIndex) {
        return true;
      } else if (sentNum < m.sentNum || (sentNum == m.sentNum && headIndex < m.headIndex)) {
        return true;
      } else {
        return false;
      }
    }
  }
  /** Check relative pronouns */
  public boolean isRelativePronoun(Mention m) {
    if(relativePronouns != null && relativePronouns.contains(m)) return true;
    return false;
  }

  public boolean isRoleAppositive(Mention m, Dictionaries dict) {
    String thisString = this.spanToString();
    if(this.isPronominal() || dict.allPronouns.contains(thisString.toLowerCase())) return false;
    if(!m.nerString.startsWith("PER") && !m.nerString.equals("O")) return false;
    if(!this.nerString.startsWith("PER") && !this.nerString.equals("O")) return false;
    if(!sameSentence(m) || !m.spanToString().startsWith(thisString)) return false;
    if(m.spanToString().contains("'") || m.spanToString().contains(" and ")) return false;
    if (!animaciesAgree(m) || this.animacy == Animacy.INANIMATE
         || this.gender == Gender.NEUTRAL || m.gender == Gender.NEUTRAL
         || !this.numbersAgree(m)) {
      return false;
    }
    if (dict.demonymSet.contains(thisString.toLowerCase())
         || dict.demonymSet.contains(m.spanToString().toLowerCase())) {
      return false;
    }
    return true;
  }

  public boolean isDemonym(Mention m, Dictionaries dict){
    String thisString = this.spanToString().toLowerCase();
    String antString = m.spanToString().toLowerCase();
    if(thisString.startsWith("the ") || thisString.startsWith("The ")) {
      thisString = thisString.substring(4);
    }
    if(antString.startsWith("the ") || antString.startsWith("The ")) antString = antString.substring(4);

    if (dict.statesAbbreviation.containsKey(m.spanToString()) && dict.statesAbbreviation.get(m.spanToString()).equals(this.spanToString())
         || dict.statesAbbreviation.containsKey(this.spanToString()) && dict.statesAbbreviation.get(this.spanToString()).equals(m.spanToString())) {
      return true;
    }

    if(dict.demonyms.get(thisString)!=null){
      if(dict.demonyms.get(thisString).contains(antString)) return true;
    } else if(dict.demonyms.get(antString)!=null){
      if(dict.demonyms.get(antString).contains(thisString)) return true;
    }
    return false;
  }

}
