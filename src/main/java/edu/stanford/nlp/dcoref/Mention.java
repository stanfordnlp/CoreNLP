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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.dcoref.Dictionaries.Animacy;
import edu.stanford.nlp.dcoref.Dictionaries.Gender;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.Dictionaries.Number;
import edu.stanford.nlp.dcoref.Dictionaries.Person;
import edu.stanford.nlp.ling.AbstractCoreLabel;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.*;

/**
 * One mention for the SieveCoreferenceSystem.
 *
 * @author Jenny Finkel, Karthik Raghunathan, Heeyoung Lee, Marta Recasens
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
  public IndexedWord headIndexedWord;

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
  public boolean isSingleton;

  public List<CoreLabel> sentenceWords;
  public List<CoreLabel> originalSpan;

  public Tree mentionSubTree;
  public Tree contextParseTree;
  public CoreLabel headWord;
  public SemanticGraph dependency;
  public Set<String> dependents = Generics.newHashSet();
  public List<String> preprocessedTerms;
  public Object synsets;

  /** Set of other mentions in the same sentence that are syntactic appositions to this */
  public Set<Mention> appositions = null;
  public Set<Mention> predicateNominatives = null;
  public Set<Mention> relativePronouns = null;

  /** Set of other mentions in the same sentence that below to this list */
  public Set<Mention> listMembers = null;
  /** Set of other mentions in the same sentence that I am a member of */
  public Set<Mention> belongToLists = null;

  // Mention is identified as being this speaker....
  public SpeakerInfo speakerInfo;

  transient private String spanString = null;
  transient private String lowercaseNormalizedSpanString = null;

  @Override
  public Class<Mention> getType() {
    return Mention.class;
  }

  public boolean isPronominal() {
    return mentionType == MentionType.PRONOMINAL;
  }

  @Override
  public String toString() {
    return spanToString();
  }

  public String spanToString() {
//    synchronized(this) {
      if (spanString == null) {
        StringBuilder os = new StringBuilder();
        for(int i = 0; i < originalSpan.size(); i ++){
          if(i > 0) os.append(" ");
          os.append(originalSpan.get(i).get(CoreAnnotations.TextAnnotation.class));
        }
        spanString = os.toString();
      }
//    }
    return spanString;
  }

  public String lowercaseNormalizedSpanString() {
//    synchronized(this) {
      if (lowercaseNormalizedSpanString == null) {
        // We always normalize to lowercase!!!
        lowercaseNormalizedSpanString = spanString.toLowerCase();
      }
//    }
    return lowercaseNormalizedSpanString;
  }

  // Retrieves part of the span that corresponds to the NER (going out from head)
  public List<CoreLabel> nerTokens() {
    if (nerString == null || "O".equals(nerString)) return null;

    int start = headIndex-startIndex;
    int end = headIndex-startIndex+1;
    while (start > 0) {
      CoreLabel prev = originalSpan.get(start-1);
      if (nerString.equals(prev.ner())) {
        start--;
      } else {
        break;
      }
    }
    while (end < originalSpan.size()) {
      CoreLabel next = originalSpan.get(end);
      if (nerString.equals(next.ner())) {
        end++;
      } else {
        break;
      }
    }
    return originalSpan.subList(start, end);
  }

  // Retrieves part of the span that corresponds to the NER (going out from head)
  public String nerName() {
    List<CoreLabel> t = nerTokens();
    return (t != null)? StringUtils.joinWords(t, " "):null;
  }

  /** Set attributes of a mention:
   * head string, mention type, NER label, Number, Gender, Animacy
   * @throws Exception
   */
  public void process(Dictionaries dict, Semantics semantics, MentionExtractor mentionExtractor) throws Exception {
    setHeadString();
    setType(dict);
    setNERString();
    List<String> mStr = getMentionString();
    setNumber(dict);
    setGender(dict, getGender(dict, mStr));
    setAnimacy(dict);
    setPerson(dict);
    setDiscourse();
    headIndexedWord = dependency.getNodeByIndexSafe(headWord.index());
    if(semantics!=null) setSemantics(dict, semantics, mentionExtractor);
  }

  public void process(Dictionaries dict, Semantics semantics, MentionExtractor mentionExtractor,
      LogisticClassifier<String, String> singletonPredictor) throws Exception {
    process(dict, semantics, mentionExtractor);
    if(singletonPredictor != null) setSingleton(singletonPredictor, dict);
  }

  private void setSingleton(LogisticClassifier<String, String> predictor, Dictionaries dict){
    double coreference_score = predictor.probabilityOf(
            new BasicDatum<>(getSingletonFeatures(dict), "1"));
    if(coreference_score < 0.2) this.isSingleton = true;
  }

  /**
   * Returns the features used by the singleton predictor (logistic
   * classifier) to decide whether the mention belongs to a singleton entity
   */
  protected ArrayList<String> getSingletonFeatures(Dictionaries dict){
    ArrayList<String> features = new ArrayList<>();
    features.add(mentionType.toString());
    features.add(nerString);
    features.add(animacy.toString());

    int personNum = 3;
    if(person.equals(Person.I) || person.equals(Person.WE)) personNum = 1;
    if(person.equals(Person.YOU)) personNum = 2;
    if(person.equals(Person.UNKNOWN)) personNum = 0;
    features.add(String.valueOf(personNum));
    features.add(number.toString());
    features.add(getPosition());
    features.add(getRelation());
    features.add(getQuantification(dict));
    features.add(String.valueOf(getModifiers(dict)));
    features.add(String.valueOf(getNegation(dict)));
    features.add(String.valueOf(getModal(dict)));
    features.add(String.valueOf(getReportEmbedding(dict)));
    features.add(String.valueOf(getCoordination()));
    return features;
  }

  private List<String> getMentionString() {
    List<String> mStr = new ArrayList<>();
    for(CoreLabel l : this.originalSpan) {
      mStr.add(l.get(CoreAnnotations.TextAnnotation.class).toLowerCase());
      if(l==this.headWord) break;   // remove words after headword
    }
    return mStr;
  }

  private Gender getGender(Dictionaries dict, List<String> mStr) {
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
      List<String> convertedStr = new ArrayList<>(2);
      convertedStr.add(mStr.get(firstNameIdx));
      convertedStr.add("!");
      if(dict.genderNumber.containsKey(convertedStr)) return dict.genderNumber.get(convertedStr);

      if(dict.genderNumber.containsKey(mStr.subList(firstNameIdx, firstNameIdx+1))) return dict.genderNumber.get(mStr.subList(firstNameIdx, firstNameIdx+1));
    }

    if(mStr.size() > 0 && dict.genderNumber.containsKey(mStr.subList(len-1, len))) return dict.genderNumber.get(mStr.subList(len-1, len));
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

    if (dep != null) {
      switch(dep) {
        case "nsubj":
        case "csubj":
          isSubject = true;
          break;
        case "obj":
        case "nsubjpass":
        case "nsubj:pass":
          isDirectObject = true;
          break;
        case "iobj":
          isIndirectObject = true;
          break;
        default:
          if (dep.startsWith("nmod")
                  && !dep.equals("nmod:npmod")
                  && !dep.equals("nmod:tmod")
                  && !dep.equals("nmod:poss")
                  && !dep.equals("nmod:agent")) {
            isPrepositionObject = true;
          }
      }
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
      preprocessedTerms = new ArrayList<>();
      preprocessedTerms.add(dict.statesAbbreviation.get(this.spanToString()));
    }

    Method meth = semantics.wordnet.getClass().getDeclaredMethod("findSynset", List.class);
    synsets = meth.invoke(semantics.wordnet, new Object[]{preprocessedTerms});

    if(this.isPronominal()) return;
  }

  /** Check list member? True if this mention is inside the other mention and the other mention is a list */
  public boolean isListMemberOf(Mention m) {
    if (this.equals(m)) return false;
    if (m.mentionType == MentionType.LIST && this.mentionType == MentionType.LIST) return false; // Don't handle nested lists
    if (m.mentionType == MentionType.LIST) {
      return this.includedIn(m);
    }
    return false;
  }

  public void addListMember(Mention m) {
    if(listMembers == null) listMembers = Generics.newHashSet();
    listMembers.add(m);
  }

  public void addBelongsToList(Mention m) {
    if(belongToLists == null) belongToLists = Generics.newHashSet();
    belongToLists.add(m);
  }

  public boolean isMemberOfSameList(Mention m) {
    Set<Mention> l1 = belongToLists;
    Set<Mention> l2 = m.belongToLists;
    if (l1 != null && l2 != null && CollectionUtils.containsAny(l1, l2)) {
      return true;
    } else {
      return false;
    }
  }

  private boolean isListLike() {
    // See if this mention looks to be a conjunction of things
    // Check for "or" and "and" and ","
    int commas = 0;
//    boolean firstLabelLike = false;
//    if (originalSpan.size() > 1) {
//      String w = originalSpan.get(1).word();
//      firstLabelLike = (w.equals(":") || w.equals("-"));
//    }
    String mentionSpanString = spanToString();
    String subTreeSpanString = StringUtils.joinWords(mentionSubTree.yieldWords(), " ");
    if (subTreeSpanString.equals(mentionSpanString)) {
      // subtree represents this mention well....
      List<Tree> children = mentionSubTree.getChildrenAsList();
      for (Tree t:children) {
        String label = t.value();
        String ner = null;
        if (t.isLeaf()) { ner = ((CoreLabel) t.getLeaves().get(0).label()).ner(); }
        if ("CC".equals(label)) {
          // Check NER type
          if (ner == null || "O".equals(ner)) {
            return true;
          }
        } else if (label.equals(",")) {
          if (ner == null || "O".equals(ner)) {
            commas++;
          }
        }
      }
    }

    if (commas <= 2) {
      // look at the string for and/or
      boolean first = true;
      for (CoreLabel t:originalSpan) {
        String tag = t.tag();
        String ner = t.ner();
        String w = t.word();
        if (tag.equals("TO") || tag.equals("IN") || tag.startsWith("VB")) {
          // prepositions and verbs are too hard for us
          return false;
        }
        if (!first) {
          if (w.equalsIgnoreCase("and") || w.equalsIgnoreCase("or")) {
            // Check NER type
            if (ner == null || "O".equals(ner)) {
              return true;
            }
          }
        }
        first = false;
      }
    }

    return (commas > 2);
  }

  private void setType(Dictionaries dict) {
    if (isListLike()) {
      mentionType = MentionType.LIST;
      SieveCoreferenceSystem.logger.finer("IS LIST: " + this);
    } else if (headWord.containsKey(CoreAnnotations.EntityTypeAnnotation.class)){    // ACE gold mention type
      if (headWord.get(CoreAnnotations.EntityTypeAnnotation.class).equals("PRO")) {
        mentionType = MentionType.PRONOMINAL;
      } else if (headWord.get(CoreAnnotations.EntityTypeAnnotation.class).equals("NAM")) {
        mentionType = MentionType.PROPER;
      } else {
        mentionType = MentionType.NOMINAL;
      }
    } else {    // MUC
      if(!headWord.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {   // temporary fix
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

  private void setGender(Dictionaries dict, Gender genderNumberResult) {
    gender = Gender.UNKNOWN;
    if(genderNumberResult!=null && this.number!=Number.PLURAL){
      gender = genderNumberResult;
      SieveCoreferenceSystem.logger.finer("[Gender number count] New gender assigned:\t" + gender + ":\t" +  headString + "\tspan:" + spanToString());
    }
    if (mentionType == MentionType.PRONOMINAL) {
      if (dict.malePronouns.contains(headString)) {
        gender = Gender.MALE;
      } else if (dict.femalePronouns.contains(headString)) {
        gender = Gender.FEMALE;
      }
    } else {
      // Bergsma or user provided list
      if(gender == Gender.UNKNOWN)  {
        if ("PERSON".equals(nerString) || "PER".equals(nerString)) {
          // Try to get gender of the named entity
          // Start with first name until we get gender...
          List<CoreLabel> nerToks = nerTokens();
          for (CoreLabel t:nerToks) {
            String name = t.word().toLowerCase();
            if(dict.maleWords.contains(name)) {
              gender = Gender.MALE;
              SieveCoreferenceSystem.logger.finer("[Bergsma List] New gender assigned:\tMale:\t" +  name + "\tspan:" + spanToString());
              break;
            }
            else if(dict.femaleWords.contains(name))  {
              gender = Gender.FEMALE;
              SieveCoreferenceSystem.logger.finer("[Bergsma List] New gender assigned:\tFemale:\t" +  name + "\tspan:" + spanToString());
              break;
            }
          }
        } else {
          if(dict.maleWords.contains(headString)) {
            gender = Gender.MALE;
            SieveCoreferenceSystem.logger.finer("[Bergsma List] New gender assigned:\tMale:\t" +  headString + "\tspan:" + spanToString());
          }
          else if(dict.femaleWords.contains(headString))  {
            gender = Gender.FEMALE;
            SieveCoreferenceSystem.logger.finer("[Bergsma List] New gender assigned:\tFemale:\t" +  headString + "\tspan:" + spanToString());
          }
          else if(dict.neutralWords.contains(headString))   {
            gender = Gender.NEUTRAL;
            SieveCoreferenceSystem.logger.finer("[Bergsma List] New gender assigned:\tNeutral:\t" +  headString + "\tspan:" + spanToString());
          }
        }
      }
    }
  }

  protected void setNumber(Dictionaries dict) {
    if (mentionType == MentionType.PRONOMINAL) {
      if (dict.pluralPronouns.contains(headString)) {
        number = Number.PLURAL;
      } else if (dict.singularPronouns.contains(headString)) {
        number = Number.SINGULAR;
      } else {
        number = Number.UNKNOWN;
      }
    } else if (mentionType == MentionType.LIST) {
      number = Number.PLURAL;
    } else if(! nerString.equals("O") && mentionType!=MentionType.NOMINAL){
      // Check to see if this is a list of things
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

      final String enumerationPattern = "NP < (NP=tmp $.. (/,|CC/ $.. NP))";

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
    } else {
      switch(nerString) {
        case "PERSON":
        case "PER":
        case "PERS":
          animacy = Animacy.ANIMATE;
          break;
        case "LOCATION":
        case "LOC":
          animacy = Animacy.INANIMATE;
          break;
        case "MONEY":
          animacy = Animacy.INANIMATE;
          break;
        case "NUMBER":
          animacy = Animacy.INANIMATE;
          break;
        case "PERCENT":
          animacy = Animacy.INANIMATE;
          break;
        case "DATE":
          animacy = Animacy.INANIMATE;
          break;
        case "TIME":
          animacy = Animacy.INANIMATE;
          break;
        case "MISC":
          animacy = Animacy.UNKNOWN;
          break;
        case "VEH":
        case "VEHICLE":
          animacy = Animacy.UNKNOWN;
          break;
        case "FAC":
        case "FACILITY":
          animacy = Animacy.INANIMATE;
          break;
        case "GPE":
          animacy = Animacy.INANIMATE;
          break;
        case "WEA":
        case "WEAPON":
          animacy = Animacy.INANIMATE;
          break;
        case "ORG":
        case "ORGANIZATION":
          animacy = Animacy.INANIMATE;
          break;
        default:
          animacy = Animacy.UNKNOWN;
      }
      if (Constants.USE_ANIMACY_LIST) {
        // Better heuristics using DekangLin:
        if(animacy == Animacy.UNKNOWN) {
          if (dict.animateWords.contains(headString))  {
            animacy = Animacy.ANIMATE;
            SieveCoreferenceSystem.logger.finest("Assigned Dekang Lin animacy:\tANIMATE:\t" + headString);
          }
          else if (dict.inanimateWords.contains(headString)) {
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
    String ner = headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
    if (ner != null && !ner.equals("O")) {
      // make sure that the head of a NE is not a known suffix, e.g., Corp.
      int start = headIndex - startIndex;
      if (originalSpan.size() > 0 && start >= originalSpan.size()) {
        throw new RuntimeException("Invalid start index " + start + "=" + headIndex + "-" + startIndex
                + ": originalSpan=[" + StringUtils.joinWords(originalSpan, " ") + "], head=" + headWord);
      }
      while (start >= 0) {
        String head = originalSpan.size() > 0 ? originalSpan.get(start).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
        if (knownSuffix(head)) {
          start --;
        } else {
          this.headString = head;
          this.headWord = originalSpan.get(start);
          this.headIndex = startIndex + start;
          break;
        }
      }
    }
  }

  private void setNERString() {
    if(headWord.containsKey(CoreAnnotations.EntityTypeAnnotation.class)){ // ACE
      if(headWord.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class) &&
              headWord.get(CoreAnnotations.EntityTypeAnnotation.class).equals("NAM")){
        this.nerString = headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      } else {
        this.nerString = "O";
      }
    }
    else{ // MUC
      if (headWord.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {
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
          switch (m.nerString) {
            case "O":
              return true;
            case "MISC":
              return true;
            case "ORGANIZATION":
              return dict.organizationPronouns.contains(headString);
            case "PERSON":
              return dict.personPronouns.contains(headString);
            case "LOCATION":
              return dict.locationPronouns.contains(headString);
            case "DATE":
            case "TIME":
              return dict.dateTimePronouns.contains(headString);
            case "MONEY":
            case "PERCENT":
            case "NUMBER":
              return dict.moneyPercentNumberPronouns.contains(headString);
            default:
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
    if(appositions == null) appositions = Generics.newHashSet();
    appositions.add(m);
  }

  /** Check apposition */
  public boolean isApposition(Mention m) {
    if(appositions != null && appositions.contains(m)) return true;
    return false;
  }
  /** Find predicate nominatives */
  public void addPredicateNominatives(Mention m) {
    if(predicateNominatives == null) predicateNominatives = Generics.newHashSet();
    predicateNominatives.add(m);
  }

  /** Check predicate nominatives */
  public boolean isPredicateNominatives(Mention m) {
    if(predicateNominatives != null && predicateNominatives.contains(m)) return true;
    return false;
  }

  /** Find relative pronouns */
  public void addRelativePronoun(Mention m) {
    if(relativePronouns == null) relativePronouns = Generics.newHashSet();
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
        } else if (this.endIndex < m.endIndex) {
          return false;
        } else if (this.headIndex != m.headIndex) {
          // Meaningless, but an arbitrary tiebreaker
          return this.headIndex < m.headIndex;
        } else if (this.mentionType != m.mentionType) {
          // Meaningless, but an arbitrary tiebreaker
          return this.mentionType.representativeness > m.mentionType.representativeness;
        } else {
          // Meaningless, but an arbitrary tiebreaker
          return this.hashCode() < m.hashCode();
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
    List<String> searchTerms = new ArrayList<>();
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
  public static String buildQueryText(List<String> terms) {
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
    if (m.dependency.getRoots().size() == 0) {
      return new Pair<>();
    }
    // would be nice to condense this pattern, but sadly =reln
    // always uses the last relation in the sequence, not the first
    SemgrexPattern pattern = SemgrexPattern.compile("{idx:" + (m.headIndex+1) + "} [ <=reln {tag:/^V.*/}=verb | <=reln ({} << {tag:/^V.*/}=verb) ]");
    SemgrexMatcher matcher = pattern.matcher(m.dependency);
    while (matcher.find()) {
      return Pair.makePair(matcher.getNode("verb"), matcher.getRelnString("reln"));
    }
    return new Pair<>();
  }

  public boolean insideIn(Mention m){
    return this.sentNum == m.sentNum
            && m.startIndex <= this.startIndex
            && this.endIndex <= m.endIndex;
  }

  public boolean moreRepresentativeThan(Mention m){
    if(m==null) return true;
    if (mentionType.representativeness > m.mentionType.representativeness) { return true; }
    else if (m.mentionType.representativeness > mentionType.representativeness) { return false; }
    else {
      // pick mention with better NER
      if (nerString != null && m.nerString == null) return true;
      if (nerString == null && m.nerString != null) return false;
      if (nerString != null && !nerString.equals(m.nerString)) {
        if ("O".equals(m.nerString)) return true;
        if ("O".equals(nerString)) return false;
        if ("MISC".equals(m.nerString)) return true;
        if ("MISC".equals(nerString)) return false;
      }
      // Ensure that both NER tags are neither MISC nor O, or are both not existent
      assert nerString == null || nerString.equals(m.nerString) || (!nerString.equals("O") && !nerString.equals("MISC") && !m.nerString.equals("O") && !m.nerString.equals("MISC"));
      // Return larger headIndex - startIndex
      if (headIndex - startIndex > m.headIndex - m.startIndex) { return true; }
      else if (headIndex - startIndex < m.headIndex - m.startIndex) { return false; }
      // Return earlier sentence number
      else if (sentNum < m.sentNum) { return true; }
      else if (sentNum > m.sentNum) { return false; }
      // Return earlier head index
      else if (headIndex < m.headIndex) { return true; }
      else if (headIndex > m.headIndex) { return false; }
      // If the mentions are short, take the longer one
      else if (originalSpan.size() <= 5 && originalSpan.size() > m.originalSpan.size()) { return true; }
      else if (originalSpan.size() <= 5 && originalSpan.size() < m.originalSpan.size()) { return false; }
      // If the mentions are long, take the shorter one (we're getting into the realm of nonsense by here)
      else if (originalSpan.size() < m.originalSpan.size()) { return true; }
      else if (originalSpan.size() > m.originalSpan.size()) { return false; }
      else {
        throw new IllegalStateException("Comparing a mention with itself for representativeness");
      }
    }
  }

  // Returns filtered premodifiers (no determiners or numerals)
  public ArrayList<ArrayList<IndexedWord>> getPremodifiers(){

    ArrayList<ArrayList<IndexedWord>> premod = new ArrayList<>();

    if(headIndexedWord == null) return premod;
    for(Pair<GrammaticalRelation,IndexedWord> child : dependency.childPairs(headIndexedWord)){
      String function = child.first().getShortName();
      if(child.second().index() < headWord.index()
          && !child.second.tag().equals("DT") && !child.second.tag().equals("WRB")
          && !function.endsWith("det") && !function.equals("nummod")
          && !function.startsWith("acl") && !function.startsWith("advcl")
          && !function.equals("punct")){
        ArrayList<IndexedWord> phrase = new ArrayList<>(dependency.descendants(child.second()));
        Collections.sort(phrase);
        premod.add(phrase);
      }
    }
    return premod;
  }

  // Returns filtered postmodifiers (no relative, -ed or -ing clauses)
  public ArrayList<ArrayList<IndexedWord>> getPostmodifiers(){

    ArrayList<ArrayList<IndexedWord>> postmod = new ArrayList<>();

    if(headIndexedWord == null) return postmod;
    for(Pair<GrammaticalRelation,IndexedWord> child : dependency.childPairs(headIndexedWord)){
      String function = child.first().getShortName();
      if(child.second().index() > headWord.index() &&
          ! function.endsWith("det") && ! function.equals("nummod")
          && ! function.startsWith("acl") && ! function.startsWith("advcl")
          && ! function.equals("punct") &&
          //possessive clitic
          ! (function.equals("case") && dependency.descendants(child.second()).size() == 1
              && child.second.tag().equals("POS"))){
        ArrayList<IndexedWord> phrase = new ArrayList<>(dependency.descendants(child.second()));
        Collections.sort(phrase);
        postmod.add(phrase);
      }
    }
    return postmod;
  }


  public String[] getSplitPattern(){

    ArrayList<ArrayList<IndexedWord>> premodifiers = getPremodifiers();

    String[] components = new String[4];

    components[0] = headWord.lemma();

    if(premodifiers.size() == 0){
      components[1] = headWord.lemma();
      components[2] = headWord.lemma();
    } else if(premodifiers.size() == 1){
      ArrayList<AbstractCoreLabel> premod = Generics.newArrayList();
      premod.addAll(premodifiers.get(premodifiers.size()-1));
      premod.add(headWord);
      components[1] = getPattern(premod);
      components[2] = getPattern(premod);
    } else {
      ArrayList<AbstractCoreLabel> premod1 = Generics.newArrayList();
      premod1.addAll(premodifiers.get(premodifiers.size()-1));
      premod1.add(headWord);
      components[1] = getPattern(premod1);

      ArrayList<AbstractCoreLabel> premod2 = Generics.newArrayList();
      for(ArrayList<IndexedWord> premodifier : premodifiers){
        premod2.addAll(premodifier);
      }
      premod2.add(headWord);
      components[2] = getPattern(premod2);
    }

    components[3] = getPattern();
    return components;
  }

  public String getPattern(){

    ArrayList<AbstractCoreLabel> pattern = Generics.newArrayList();
    for(ArrayList<IndexedWord> premodifier : getPremodifiers()){
      pattern.addAll(premodifier);
    }
    pattern.add(headWord);
    for(ArrayList<IndexedWord> postmodifier : getPostmodifiers()){
      pattern.addAll(postmodifier);
    }
    return getPattern(pattern);
  }

  public String getPattern(List<AbstractCoreLabel> pTokens){

    ArrayList<String> phrase_string = new ArrayList<>();
    String ne = "";
    for(AbstractCoreLabel token : pTokens){
      if(token.index() == headWord.index()){
        phrase_string.add(token.lemma());
        ne = "";

      } else if( (token.lemma().equals("and") || StringUtils.isPunct(token.lemma()))
          && pTokens.size() > pTokens.indexOf(token)+1
          && pTokens.indexOf(token) > 0
          && pTokens.get(pTokens.indexOf(token)+1).ner().equals(pTokens.get(pTokens.indexOf(token)-1).ner())){

      } else if(token.index() == headWord.index()-1
          && token.ner().equals(nerString)){
        phrase_string.add(token.lemma());
        ne = "";

      } else if(!token.ner().equals("O")){
        if(!token.ner().equals(ne)){
          ne = token.ner();
          phrase_string.add("<"+ne+">");
        }

      } else {
        phrase_string.add(token.lemma());
        ne = "";
      }
    }
    return StringUtils.join(phrase_string);
  }

  public boolean isCoordinated(){
    if(headIndexedWord == null) return false;
    for(Pair<GrammaticalRelation,IndexedWord> child : dependency.childPairs(headIndexedWord)){
      if(child.first().getShortName().equals("cc")) return true;
    }
    return false;
  }

  private static List<String> getContextHelper(List<? extends AbstractCoreLabel> words) {
    List<List<AbstractCoreLabel>> namedEntities = Generics.newArrayList();
    List<AbstractCoreLabel> ne = Generics.newArrayList();
    String previousNEType = "";
    int previousNEIndex = -1;
    for (int i = 0; i < words.size(); i++) {
      AbstractCoreLabel word = words.get(i);
      if(!word.ner().equals("O")) {
        if (!word.ner().equals(previousNEType) || previousNEIndex != i-1) {
          ne = Generics.newArrayList();
          namedEntities.add(ne);
        }
        ne.add(word);
        previousNEType = word.ner();
        previousNEIndex = i;
      }
    }

    List<String> neStrings = new ArrayList<>();
    Set<String> hs = Generics.newHashSet();
    for (List<AbstractCoreLabel> namedEntity : namedEntities) {
      String ne_str = StringUtils.joinWords(namedEntity, " ");
      hs.add(ne_str);
    }
    neStrings.addAll(hs);
    return neStrings;
  }

  public List<String> getContext() {
    return getContextHelper(sentenceWords);
  }

  public List<String> getPremodifierContext() {
    List<String> neStrings = new ArrayList<>();
    for (List<IndexedWord> words : getPremodifiers()) {
      neStrings.addAll(getContextHelper(words));
    }
    return neStrings;
  }

  /** Check relative pronouns */
  public boolean isRelativePronoun(Mention m) {
    return relativePronouns != null && relativePronouns.contains(m);
  }

  public boolean isRoleAppositive(Mention m, Dictionaries dict) {
    String thisString = this.spanToString();
    String thisStringLower = this.lowercaseNormalizedSpanString();
    if(this.isPronominal() || dict.allPronouns.contains(thisStringLower)) return false;
    if(!m.nerString.startsWith("PER") && !m.nerString.equals("O")) return false;
    if(!this.nerString.startsWith("PER") && !this.nerString.equals("O")) return false;
    if(!sameSentence(m) || !m.spanToString().startsWith(thisString)) return false;
    if(m.spanToString().contains("'") || m.spanToString().contains(" and ")) return false;
    if (!animaciesAgree(m) || this.animacy == Animacy.INANIMATE
         || this.gender == Gender.NEUTRAL || m.gender == Gender.NEUTRAL
         || !this.numbersAgree(m)) {
      return false;
    }
    if (dict.demonymSet.contains(thisStringLower)
         || dict.demonymSet.contains(m.lowercaseNormalizedSpanString())) {
      return false;
    }
    return true;
  }

  public boolean isDemonym(Mention m, Dictionaries dict) {
    String thisCasedString = this.spanToString();
    String antCasedString = m.spanToString();

    // The US state matching part (only) is done cased
    String thisNormed = dict.lookupCanonicalAmericanStateName(thisCasedString);
    String antNormed = dict.lookupCanonicalAmericanStateName(antCasedString);
    if (thisNormed != null && thisNormed.equals(antNormed)) {
      return true;
    }

    // The rest is done uncased
    String thisString = thisCasedString.toLowerCase(Locale.ENGLISH);
    String antString = antCasedString.toLowerCase(Locale.ENGLISH);
    if (thisString.startsWith("the ")) {
      thisString = thisString.substring(4);
    }
    if (antString.startsWith("the ")) {
      antString = antString.substring(4);
    }

    Set<String> thisDemonyms = dict.getDemonyms(thisString);
    Set<String> antDemonyms = dict.getDemonyms(antString);
    if (thisDemonyms.contains(antString) || antDemonyms.contains(thisString)) {
      return true;
    }
    return false;
  }

  public String getPosition() {
    int size = sentenceWords.size();
    if(headIndex == 0) {
      return "first";
    } else if (headIndex == size -1) {
      return "last";
    } else {
      if(headIndex > 0 && headIndex < size/3) {
        return "begin";
      } else if (headIndex >= size/3 && headIndex < 2 * size/3) {
        return "middle";
      } else if (headIndex >= 2 * size/3 && headIndex < size -1) {
        return "end";
      }
    }
    return null;
  }

  public String getRelation(){

    if(headIndexedWord == null) return null;

    if(dependency.getRoots().isEmpty()) return null;
    // root relation
    if(dependency.getFirstRoot().equals(headIndexedWord)) return "root";
    if(!dependency.containsVertex(dependency.getParent(headIndexedWord))) return null;
    GrammaticalRelation relation = dependency.reln(dependency.getParent(headIndexedWord), headIndexedWord);

    // adjunct relations
    if(relation != UniversalEnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER //do not match "acl:relcl"
        && relation != UniversalEnglishGrammaticalRelations.POSSESSION_MODIFIER //do not match "nmod:poss"
        && relation != UniversalEnglishGrammaticalRelations.NP_ADVERBIAL_MODIFIER //do not match "nmod:npmod"
        && relation != UniversalEnglishGrammaticalRelations.AGENT //do not match "nmod:agent"
        && (relation.toString().startsWith("nmod") //matches all regular nmod, nmods with prepositions in relation name, and "nmod:tmod"
        ||  relation.toString().startsWith("acl")
        ||  relation.toString().startsWith("advcl")
        ||  relation == UniversalEnglishGrammaticalRelations.ADVERBIAL_MODIFIER))
      return "adjunct";

    // subject relations
    if(relation == UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT
        || relation == UniversalEnglishGrammaticalRelations.CLAUSAL_SUBJECT)
      return "subject";
    if(relation == UniversalEnglishGrammaticalRelations.NOMINAL_PASSIVE_SUBJECT
        || relation == UniversalEnglishGrammaticalRelations.CLAUSAL_PASSIVE_SUBJECT)
      return "subject";

    // verbal argument relations
    if(relation == UniversalEnglishGrammaticalRelations.CLAUSAL_COMPLEMENT
        || relation == UniversalEnglishGrammaticalRelations.XCLAUSAL_COMPLEMENT
        || relation == UniversalEnglishGrammaticalRelations.AGENT
        || relation == UniversalEnglishGrammaticalRelations.DIRECT_OBJECT
        || relation == UniversalEnglishGrammaticalRelations.INDIRECT_OBJECT)
      return "verbArg";

    // noun argument relations
    if(relation == UniversalEnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER
        || relation == UniversalEnglishGrammaticalRelations.COMPOUND_MODIFIER
        || relation == UniversalEnglishGrammaticalRelations.ADJECTIVAL_MODIFIER
        || relation == UniversalEnglishGrammaticalRelations.APPOSITIONAL_MODIFIER
        || relation == UniversalEnglishGrammaticalRelations.POSSESSION_MODIFIER)
      return "nounArg";

    return null;
  }

  public int getModifiers(Dictionaries dict){

    if(headIndexedWord == null) return 0;

    int count = 0;
    List<Pair<GrammaticalRelation, IndexedWord>> childPairs = dependency.childPairs(headIndexedWord);
    for(Pair<GrammaticalRelation, IndexedWord> childPair : childPairs) {
      GrammaticalRelation gr = childPair.first;
      IndexedWord word = childPair.second;

      //adjectival modifiers, prepositional modifiers, relative clauses, and possessives if they are not a determiner
      if((gr == UniversalEnglishGrammaticalRelations.ADJECTIVAL_MODIFIER
          || gr == UniversalEnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER
          || gr.toString().startsWith("nmod")
          || gr.toString().startsWith("acl")
          || gr.toString().startsWith("advcl"))
          && !dict.determiners.contains(word.lemma())) {
        count++;
      }
      // add noun modifier when the mention isn't a NER
      if(nerString.equals("O") && gr == UniversalEnglishGrammaticalRelations.COMPOUND_MODIFIER) {
        count++;
      }
    }
    return count;
  }

  public String getQuantification(Dictionaries dict){

    if(headIndexedWord == null) return null;

    if(!nerString.equals("O")) return "definite";

    Set<IndexedWord> quant = dependency.getChildrenWithReln(headIndexedWord, UniversalEnglishGrammaticalRelations.DETERMINER);
    Set<IndexedWord> poss = dependency.getChildrenWithReln(headIndexedWord, UniversalEnglishGrammaticalRelations.POSSESSION_MODIFIER);
    if (!quant.isEmpty()) {
      for (IndexedWord word : quant) {
        String det = word.lemma();
        if (dict.determiners.contains(det)) {
          return "definite";
        } else if (dict.quantifiers2.contains(det)) {
          return "quantified";
        }
      }
    } else if (!poss.isEmpty()) {
      return "definite";
    } else {
      quant = dependency.getChildrenWithReln(headIndexedWord, UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER);
      if (!quant.isEmpty()) {
        return "quantified";
      }
    }
    return "indefinite";
  }

  public int getNegation(Dictionaries dict) {

    if(headIndexedWord == null) return 0;

    // direct negation in a child
    Collection<IndexedWord> children = dependency.getChildren(headIndexedWord);
    for(IndexedWord child : children) {
      if(dict.negations.contains(child.lemma())) return 1;
    }

    // or has a sibling
    for(IndexedWord sibling : dependency.getSiblings(headIndexedWord)) {
      if(dict.negations.contains(sibling.lemma())
          && !dependency.hasParentWithReln(headIndexedWord, UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT))
        return 1;
    }
    // check the parent
    List<Pair<GrammaticalRelation,IndexedWord>> parentPairs = dependency.parentPairs(headIndexedWord);
    if (!parentPairs.isEmpty()) {
      Pair<GrammaticalRelation,IndexedWord> parentPair = parentPairs.get(0);
      GrammaticalRelation gr = parentPair.first;
      // check negative prepositions
      if(dict.neg_relations.contains(gr.toString())) return 1;
    }
    return 0;
  }

  public int getModal(Dictionaries dict) {

    if(headIndexedWord == null) return 0;

    // direct modal in a child
    Collection<IndexedWord> children = dependency.getChildren(headIndexedWord);
    for(IndexedWord child : children) {
      if(dict.modals.contains(child.lemma())) return 1;
    }

    // check the parent
    IndexedWord parent = dependency.getParent(headIndexedWord);
    if (parent != null) {
      if(dict.modals.contains(parent.lemma())) return 1;
      // check the children of the parent (that is needed for modal auxiliaries)
      IndexedWord child = dependency.getChildWithReln(parent, UniversalEnglishGrammaticalRelations.AUX_MODIFIER);
      if(!dependency.hasParentWithReln(headIndexedWord, UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT)
          && child != null && dict.modals.contains(child.lemma())) return 1;
    }

    // look at the path to root
    List<IndexedWord> path = dependency.getPathToRoot(headIndexedWord);
    if(path == null) return 0;
    for(IndexedWord word : path) {
      if(dict.modals.contains(word.lemma())) return 1;
    }
    return 0;
  }

  public int getReportEmbedding(Dictionaries dict) {

    if(headIndexedWord == null) return 0;

    // check adverbial clause with marker "as"
    for(IndexedWord sibling : dependency.getSiblings(headIndexedWord)) {
      if(dict.reportVerb.contains(sibling.lemma()) && dependency.hasParentWithReln(sibling,UniversalEnglishGrammaticalRelations.ADV_CLAUSE_MODIFIER)) {
        IndexedWord marker = dependency.getChildWithReln(sibling,UniversalEnglishGrammaticalRelations.MARKER);
        if (marker != null && marker.lemma().equals("as")) {
          return 1;
        }
      }
    }

    // look at the path to root
    List<IndexedWord> path = dependency.getPathToRoot(headIndexedWord);
    if(path == null) return 0;
    boolean isSubject = false;

    // if the node itself is a subject, we will not take into account its parent in the path
    if(dependency.hasParentWithReln(headIndexedWord, UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT)) isSubject = true;

    for (IndexedWord word : path) {
      if(!isSubject && (dict.reportVerb.contains(word.lemma()) || dict.reportNoun.contains(word.lemma()))) {
        return 1;
      }
      // check how to put isSubject
      isSubject = dependency.hasParentWithReln(word, UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT);
    }
    return 0;
  }

  public int getCoordination() {

    if(headIndexedWord == null) return 0;

    Set<GrammaticalRelation> relations = dependency.childRelns(headIndexedWord);
    for (GrammaticalRelation rel : relations) {
      if(rel.toString().startsWith("conj:")) {
        return 1;
      }
    }

    Set<GrammaticalRelation> parent_relations = dependency.relns(headIndexedWord);
    for (GrammaticalRelation rel : parent_relations) {
      if(rel.toString().startsWith("conj:")) {
        return 1;
      }
    }
    return 0;
  }

}
