package edu.stanford.nlp.hcoref.md;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphUtils;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;

public class DependencyCorefMentionFinder extends CorefMentionFinder {

  public DependencyCorefMentionFinder(Properties props) throws ClassNotFoundException, IOException {
    this.lang = CorefProperties.getLanguage(props);
    mdClassifier = (CorefProperties.isMentionDetectionTraining(props)) ?
        null : IOUtils.readObjectFromURLOrClasspathOrFileSystem(CorefProperties.getMentionDetectionModel(props));
  }

  public MentionDetectionClassifier mdClassifier = null;
  
  /** Main method of mention detection.
   *  Extract all NP, PRP or NE, and filter out by manually written patterns.
   */
  @Override
  public List<List<Mention>> findMentions(Annotation doc, Dictionaries dict, Properties props) {
    List<List<Mention>> predictedMentions = new ArrayList<>();
    Set<String> neStrings = Generics.newHashSet();
    List<Set<IntPair>> mentionSpanSetList = Generics.newArrayList();
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    
    for (CoreMap s : sentences) {
      List<Mention> mentions = new ArrayList<>();
      predictedMentions.add(mentions);
      Set<IntPair> mentionSpanSet = Generics.newHashSet();
      Set<IntPair> namedEntitySpanSet = Generics.newHashSet();

      extractPremarkedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      HybridCorefMentionFinder.extractNamedEntityMentions(s, mentions, mentionSpanSet, namedEntitySpanSet);
      extractNPorPRPFromDependency(s, mentions, mentionSpanSet, namedEntitySpanSet);
     
      addNamedEntityStrings(s, neStrings, namedEntitySpanSet);
      mentionSpanSetList.add(mentionSpanSet);
    }
//    extractNamedEntityModifiers(sentences, mentionSpanSetList, predictedMentions, neStrings);
    
    for(int i=0 ; i<sentences.size() ; i++ ) {
      findHead(sentences.get(i), predictedMentions.get(i));
    }
    // mention selection based on document-wise info
    removeSpuriousMentions(doc, predictedMentions, dict, CorefProperties.removeNested(props), lang);

    // if this is for MD training, skip classification
    if(!CorefProperties.isMentionDetectionTraining(props)) {
      mdClassifier.classifyMentions(predictedMentions, dict, props);
    }

    return predictedMentions;
  }

  protected static void assignMentionIDs(List<List<Mention>> predictedMentions, int maxID) {
    for(List<Mention> mentions : predictedMentions) {
      for(Mention m : mentions) {
        m.mentionID = (++maxID);
      }
    }
  }

  protected static void setBarePlural(List<Mention> mentions) {
    for (Mention m : mentions) {
      String pos = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if(m.originalSpan.size()==1 && pos.equals("NNS")) m.generic = true;
    }
  }

  private void extractNPorPRPFromDependency(CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    
    SemanticGraph basic = s.get(BasicDependenciesAnnotation.class);
    
    List<IndexedWord> nounsOrPrp = basic.getAllNodesByPartOfSpeechPattern("N.*|PRP.*|DT");    // DT is for "this, these, etc"
    Tree tree = s.get(TreeAnnotation.class);
    
    for(IndexedWord w : nounsOrPrp) {
      SemanticGraphEdge edge = basic.getEdge(basic.getParent(w), w);
      GrammaticalRelation rel = null;
      String shortname = "root";    // if edge is null, it's root
      if(edge!=null) {
        rel = edge.getRelation();
        shortname = rel.getShortName();
      }

      // TODO: what to remove? remove more?
      if(shortname.matches("det|nn")) {
        
//        // for debug  ---------------
//        Tree t = tree.getLeaves().get(w.index()-1);
//        for(Tree p : tree.pathNodeToNode(t, tree)) {
//          if(p.label().value().equals("NP")) {
//            HeadFinder headFinder = new SemanticHeadFinder();
//            Tree head = headFinder.determineHead(p);
//            if(head == t.parent(tree)) {
//              System.err.println();
//            }
//            break;
//          }
//        } // for debug -------------
        
        continue;
      } else {
        extractMentionForHeadword(w, basic, s, mentions, mentionSpanSet, namedEntitySpanSet);
      }
    }
  }
  
  private void extractMentionForHeadword(IndexedWord headword, SemanticGraph dep, CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph basic = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph collapsed = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    
    // pronoun
    if(headword.tag().startsWith("PRP")) {
      extractPronounForHeadword(headword, dep, s, mentions, mentionSpanSet, namedEntitySpanSet);
      return;
    }
    
    // add NP mention
    IntPair npSpan = getNPSpan(headword, dep, sent); 
    int beginIdx = npSpan.get(0);
    int endIdx = npSpan.get(1)+1;
    if (",".equals(sent.get(endIdx-1).word())) { endIdx--; } // try not to have span that ends with ,
    if ("IN".equals(sent.get(beginIdx).tag())) { beginIdx++; }  // try to remove first IN.
    addMention(beginIdx, endIdx, headword, mentions, mentionSpanSet, namedEntitySpanSet, sent, basic, collapsed);
      
    //
    // extract the first element in conjunction (A and B -> extract A here "A and B", "B" will be extracted above)
    //
    
    // to make sure we find the first conjunction
    Set<IndexedWord> conjChildren = dep.getChildrenWithReln(headword, EnglishGrammaticalRelations.CONJUNCT);
    if(conjChildren.size() > 0) {
      IndexedWord conjChild = dep.getChildWithReln(headword, EnglishGrammaticalRelations.CONJUNCT);
      for(IndexedWord c : conjChildren) {
        if(c.index() < conjChild.index()) conjChild = c;
      }
      IndexedWord left = SemanticGraphUtils.leftMostChildVertice(conjChild, dep);
      for(int endIdxFirstElement = left.index()-1 ; endIdxFirstElement > beginIdx ; endIdxFirstElement--) {
        if(!sent.get(endIdxFirstElement-1).tag().matches("CC|,")) {
          if(headword.index()-1 < endIdxFirstElement) {
            addMention(beginIdx, endIdxFirstElement, headword, mentions, mentionSpanSet, namedEntitySpanSet, sent, basic, collapsed);
          }
          break;
        }
      }
    }
  }

  /**
   *  return the left and right most node except copula relation (nsubj & cop) and some others (maybe discourse?)
   *  e.g., you are the person -> return "the person"
   */
  private IntPair getNPSpan(IndexedWord headword, SemanticGraph dep, List<CoreLabel> sent) {
    int headwordIdx = headword.index()-1;
    
    List<IndexedWord> children = dep.getChildList(headword);
//    if(children.size()==0) return new IntPair(headwordIdx, headwordIdx);    // the headword is the only word
    
    // check if we have copula relation
    IndexedWord cop = dep.getChildWithReln(headword, EnglishGrammaticalRelations.COPULA);
    int startIdx = (cop==null)? 0 : children.indexOf(cop)+1;
    
    // children which will be inside of NP
    List<IndexedWord> insideNP = Generics.newArrayList();
    
    for(int i=startIdx ; i < children.size() ; i++) {
      IndexedWord child = children.get(i);
      SemanticGraphEdge edge = dep.getEdge(headword, child);
      if(edge.getRelation().getShortName().matches("dep|discourse|punct")) {
        continue;  // skip
      } else {
        insideNP.add(child);
      }
    }
    
    if(insideNP.size()==0) return new IntPair(headwordIdx, headwordIdx);    // the headword is the only word
    
    Pair<IndexedWord, IndexedWord> firstChildLeftRight = SemanticGraphUtils.leftRightMostChildVertices(insideNP.get(0), dep);
    Pair<IndexedWord, IndexedWord> lastChildLeftRight = SemanticGraphUtils.leftRightMostChildVertices(insideNP.get(insideNP.size()-1), dep);
    
    // headword can be first or last word 
    int beginIdx = Math.min(headwordIdx, firstChildLeftRight.first.index()-1);
    int endIdx = Math.max(headwordIdx, lastChildLeftRight.second.index()-1);
    
    return new IntPair(beginIdx, endIdx);
  }
  
  private IntPair getNPSpanOld(IndexedWord headword, SemanticGraph dep, List<CoreLabel> sent) {
    IndexedWord cop = dep.getChildWithReln(headword, EnglishGrammaticalRelations.COPULA);
    Pair<IndexedWord, IndexedWord> leftRight = SemanticGraphUtils.leftRightMostChildVertices(headword, dep);
    
    // headword can be first or last word 
    int beginIdx = Math.min(headword.index()-1, leftRight.first.index()-1);
    int endIdx = Math.max(headword.index()-1, leftRight.second.index()-1);
    
    // no copula relation
    if(cop==null) return new IntPair(beginIdx, endIdx);
    
    // if we have copula relation
    List<IndexedWord> children = dep.getChildList(headword);
    int copIdx = children.indexOf(cop);
    
    if(copIdx+1 < children.size()) {
      beginIdx = Math.min(headword.index()-1, SemanticGraphUtils.leftMostChildVertice(children.get(copIdx+1), dep).index()-1);
    } else {
      beginIdx = headword.index()-1;
    }
    
    return new IntPair(beginIdx, endIdx);
  }

  private void addMention(int beginIdx, int endIdx, IndexedWord headword, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet, List<CoreLabel> sent, SemanticGraph basic, SemanticGraph collapsed) {
    IntPair mSpan = new IntPair(beginIdx, endIdx);
    if(!mentionSpanSet.contains(mSpan) && (!insideNE(mSpan, namedEntitySpanSet)) ) {
      int dummyMentionId = -1;
      Mention m = new Mention(dummyMentionId, beginIdx, endIdx, sent, basic, collapsed, new ArrayList<>(sent.subList(beginIdx, endIdx)));
      m.headIndex = headword.index()-1;
      m.headWord = sent.get(m.headIndex);
      m.headString = m.headWord.word().toLowerCase(Locale.ENGLISH);
      mentions.add(m);
      mentionSpanSet.add(mSpan);
    }
  }

  private void extractPronounForHeadword(IndexedWord headword, SemanticGraph dep, CoreMap s, List<Mention> mentions, Set<IntPair> mentionSpanSet, Set<IntPair> namedEntitySpanSet) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph basic = s.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph collapsed = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    int beginIdx = headword.index()-1;
    int endIdx = headword.index();
    
    // handle "you all", "they both" etc
    if(sent.size() > headword.index() && sent.get(headword.index()).word().matches("all|both")) {
      IndexedWord c = dep.getNodeByIndex(headword.index()+1);
      SemanticGraphEdge edge = dep.getEdge(headword, c);
      if(edge!=null) endIdx++;
    }
    
    IntPair mSpan = new IntPair(beginIdx, endIdx);
    if(!mentionSpanSet.contains(mSpan) && (!insideNE(mSpan, namedEntitySpanSet)) ) {
      int dummyMentionId = -1;
      Mention m = new Mention(dummyMentionId, beginIdx, endIdx, sent, basic, collapsed, new ArrayList<>(sent.subList(beginIdx, endIdx)));
      m.headIndex = headword.index()-1;
      m.headWord = sent.get(m.headIndex);
      m.headString = m.headWord.word().toLowerCase(Locale.ENGLISH);
      mentions.add(m);
      mentionSpanSet.add(mSpan);
    }
    
    // when pronoun is a part of conjunction (e.g., you and I)
    Set<IndexedWord> conjChildren = dep.getChildrenWithReln(headword, EnglishGrammaticalRelations.CONJUNCT);
    if(conjChildren.size() > 0) {
      IntPair npSpan = getNPSpan(headword, dep, sent);
      beginIdx = npSpan.get(0);
      endIdx = npSpan.get(1)+1;
      if (",".equals(sent.get(endIdx-1).word())) { endIdx--; } // try not to have span that ends with ,
      addMention(beginIdx, endIdx, headword, mentions, mentionSpanSet, namedEntitySpanSet, sent, basic, collapsed);
    }
  }
  public static void findHeadInDependency(CoreMap s, List<Mention> mentions) {
    for (Mention m : mentions){
      findHeadInDependency(s, m);
    }
  }
  public void findHead(CoreMap s, List<Mention> mentions) {
    for (Mention m : mentions){
      findHeadInDependency(s, m);
    }
  }
  
  // TODO: still errors in head finder
  public static void findHeadInDependency(CoreMap s, Mention m) {
    List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
    SemanticGraph basicDep = s.get(BasicDependenciesAnnotation.class);
    if(m.headWord == null) {
      
      // when there's punctuation, no node found in the dependency tree
      int curIdx;
      IndexedWord cur = null;
      for(curIdx = m.endIndex-1 ; curIdx >= m.startIndex ; curIdx--) {
        if((cur = basicDep.getNodeByIndexSafe(curIdx+1)) != null) break; 
      }
      
      if(cur==null) curIdx = m.endIndex-1;
      while(cur!=null) {
        IndexedWord p = basicDep.getParent(cur);
        if(p==null || p.index()-1 < m.startIndex || p.index()-1 >= m.endIndex) break;
        curIdx = p.index()-1;
        cur = basicDep.getNodeByIndexSafe(curIdx+1);
      }
//      for(IndexedWord p : basicDep.getPathToRoot(basicDep.getNodeByIndex(curIdx+1))) {
//        if(p.index()-1 < m.startIndex || p.index()-1 >= m.endIndex) {
//          break;
//        }
//        curIdx = p.index()-1;
//      }
      m.headIndex = curIdx;
      m.headWord = sent.get(m.headIndex);
      m.headString = m.headWord.word().toLowerCase(Locale.ENGLISH);
    }
  }

//  /** Filter out all spurious mentions 
//   * @param goldMentionsByID */
//  @Override
//  public void removeSpuriousMentionsEn(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict) {
//    
//    Set<String> standAlones = new HashSet<String>();
//    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
//    
//    for(int i=0 ; i < predictedMentions.size() ; i++) {
//      CoreMap s = sentences.get(i);
//      List<Mention> mentions = predictedMentions.get(i);
//      
//      Tree tree = s.get(TreeCoreAnnotations.TreeAnnotation.class);
//      List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
//      Set<Mention> remove = Generics.newHashSet();
//      
//      for(Mention m : mentions){
//        String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
//        String headNE = m.headWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
//
//        // non word such as 'hmm'
//        if(dict.nonWords.contains(m.headString)) remove.add(m);
//        
//        // quantRule : not starts with 'any', 'all' etc
//        if (m.originalSpan.size() > 0) { 
//          String firstWord = m.originalSpan.get(0).get(CoreAnnotations.TextAnnotation.class).toLowerCase(Locale.ENGLISH);
//          if(firstWord.matches("none|no|nothing|not")) {
//            remove.add(m);
//          }
////          if(dict.quantifiers.contains(firstWord)) remove.add(m);
//        }
//        
//        // partitiveRule
//        if (partitiveRule(m, sent, dict)) {
//          remove.add(m);
//        }
//        
//        // bareNPRule
//        if (headPOS.equals("NN") && !dict.temporals.contains(m.headString)
//            && (m.originalSpan.size()==1 || m.originalSpan.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("JJ"))) {
//          remove.add(m);
//        }
//        
//        // remove generic rule
////          if(m.generic==true) remove.add(m);
//        
//        if (m.headString.equals("%")) {
//          remove.add(m);
//        }
//        if (headNE.equals("PERCENT") || headNE.equals("MONEY")) {
//          remove.add(m);
//        }
//        
//        // adjective form of nations
//        // the [American] policy -> not mention
//        // speak in [Japanese] -> mention
//        // check if the mention is noun and the next word is not noun
//        if (dict.isAdjectivalDemonym(m.spanToString())) {
//          remove.add(m);
//        }
//        
//        // stop list (e.g., U.S., there)
//        if (inStopList(m)) remove.add(m);
//      }
//      
//      // nested mention with shared headword (except apposition, enumeration): pick larger one
//      for (Mention m1 : mentions){
//        for (Mention m2 : mentions){
//          if (m1==m2 || remove.contains(m1) || remove.contains(m2)) continue;
//          if (m1.sentNum==m2.sentNum && m1.headWord==m2.headWord && m2.insideIn(m1)) {
//            if (m2.endIndex < sent.size() && (sent.get(m2.endIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals(",")
//                || sent.get(m2.endIndex).get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CC"))) {
//              continue;
//            }
//            remove.add(m2);
//          }
//        }
//      }
//      mentions.removeAll(remove);
//    }
//  }
  /** Filter out all spurious mentions  */
  @Override
  public void removeSpuriousMentionsEn(Annotation doc, List<List<Mention>> predictedMentions, Dictionaries dict) {
    
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    
    for(int i=0 ; i < predictedMentions.size() ; i++) {
      CoreMap s = sentences.get(i);
      List<Mention> mentions = predictedMentions.get(i);
      
      List<CoreLabel> sent = s.get(CoreAnnotations.TokensAnnotation.class);
      Set<Mention> remove = Generics.newHashSet();
      
      for(Mention m : mentions){
        String headPOS = m.headWord.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        
        // non word such as 'hmm'
        if(dict.nonWords.contains(m.headString)) remove.add(m);
        
        // adjective form of nations
        // the [American] policy -> not mention
        // speak in [Japanese] -> mention
        // check if the mention is noun and the next word is not noun
        if (dict.isAdjectivalDemonym(m.spanToString())) {
          if(!headPOS.startsWith("N") 
              || (m.endIndex < sent.size() && sent.get(m.endIndex).tag().startsWith("N")) ) {
            remove.add(m);
          }
        }
        
        // stop list (e.g., U.S., there)
        if (inStopList(m)) remove.add(m);
      }
      mentions.removeAll(remove);
    }
  }
}
