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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.ie.machinereading.domains.ace.AceReader;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * Extracts {@code <COREF>} mentions from a file annotated in ACE format (ACE2004, ACE2005).
 *
 * @author Heeyoung Lee
 */
public class ACEMentionExtractor extends MentionExtractor {
  private AceReader aceReader;

  private String corpusPath;
  protected int fileIndex = 0;
  protected String[] files;

  private static final Logger logger = SieveCoreferenceSystem.logger;

  private static class EntityComparator implements Comparator<EntityMention> {
    @Override
    public int compare(EntityMention m1, EntityMention m2){
      if(m1.getExtentTokenStart() > m2.getExtentTokenStart()) return 1;
      else if(m1.getExtentTokenStart() < m2.getExtentTokenStart()) return -1;
      else if(m1.getExtentTokenEnd() > m2.getExtentTokenEnd()) return -1;
      else if(m1.getExtentTokenEnd() < m2.getExtentTokenEnd()) return 1;
      else return 0;
    }
  }

  public ACEMentionExtractor(Dictionaries dict, Properties props, Semantics semantics) throws Exception {
    super(dict, semantics);
    stanfordProcessor = loadStanfordProcessor(props);

    if(props.containsKey(Constants.ACE2004_PROP)) {
      corpusPath = props.getProperty(Constants.ACE2004_PROP);
      aceReader = new AceReader(stanfordProcessor, false, "ACE2004");
    }
    else if(props.containsKey(Constants.ACE2005_PROP)) {
      corpusPath = props.getProperty(Constants.ACE2005_PROP);
      aceReader = new AceReader(stanfordProcessor, false);
    }
    aceReader.setLoggerLevel(Level.INFO);

    if(corpusPath.charAt(corpusPath.length()-1)!= File.separatorChar) corpusPath+= File.separatorChar;

    files = new File(corpusPath).list();
  }
  
  public ACEMentionExtractor(Dictionaries dict, Properties props, Semantics semantics,
      LogisticClassifier<String, String> singletonModel) throws Exception {
    this(dict, props, semantics);
    singletonPredictor = singletonModel;
  }

  public void resetDocs() {
    super.resetDocs();
    fileIndex = 0;
  }

  public Document nextDoc() throws Exception {
    List<List<CoreLabel>> allWords = new ArrayList<>();
    List<List<Mention>> allGoldMentions = new ArrayList<>();
    List<List<Mention>> allPredictedMentions;
    List<Tree> allTrees = new ArrayList<>();

    Annotation anno;

    try {
      String filename="";
      while(files.length > fileIndex){
        if(files[fileIndex].contains("apf.xml")) {
          filename = files[fileIndex];
          fileIndex++;
          break;
        }
        else {
          fileIndex++;
          filename="";
        }
      }
      if(files.length <= fileIndex && filename.equals("")) return null;

      anno = aceReader.parse(corpusPath+filename);
      stanfordProcessor.annotate(anno);


      List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);

      for (CoreMap s : sentences){
        int i = 1;
        for(CoreLabel w : s.get(CoreAnnotations.TokensAnnotation.class)){
          w.set(CoreAnnotations.IndexAnnotation.class, i++);
          if(!w.containsKey(CoreAnnotations.UtteranceAnnotation.class)) {
            w.set(CoreAnnotations.UtteranceAnnotation.class, 0);
          }
        }
        allTrees.add(s.get(TreeCoreAnnotations.TreeAnnotation.class));
        allWords.add(s.get(CoreAnnotations.TokensAnnotation.class));
        EntityComparator comparator = new EntityComparator();
        extractGoldMentions(s, allGoldMentions, comparator);
      }

      if(Constants.USE_GOLD_MENTIONS) allPredictedMentions = allGoldMentions;
      else allPredictedMentions = mentionFinder.extractPredictedMentions(anno, maxID, dictionaries);

      printRawDoc(sentences, allGoldMentions, filename, true);
      printRawDoc(sentences, allPredictedMentions, filename, false);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    return arrange(anno, allWords, allTrees, allPredictedMentions, allGoldMentions, true);
  }

  private void extractGoldMentions(CoreMap s, List<List<Mention>> allGoldMentions, EntityComparator comparator) {
    List<Mention> goldMentions = new ArrayList<>();
    allGoldMentions.add(goldMentions);
    List<EntityMention> goldMentionList = s.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
    List<CoreLabel> words = s.get(CoreAnnotations.TokensAnnotation.class);

    TreeSet<EntityMention> treeForSortGoldMentions = new TreeSet<>(comparator);
    if(goldMentionList!=null) treeForSortGoldMentions.addAll(goldMentionList);
    if(!treeForSortGoldMentions.isEmpty()){
      for(EntityMention e : treeForSortGoldMentions){
        Mention men = new Mention();
        men.dependency = s.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
        if (men.dependency == null) {
          men.dependency = s.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        }
        men.startIndex = e.getExtentTokenStart();
        men.endIndex = e.getExtentTokenEnd();

        String[] parseID = e.getObjectId().split("-");
        men.mentionID = Integer.parseInt(parseID[parseID.length-1]);
        String[] parseCorefID = e.getCorefID().split("-E");
        men.goldCorefClusterID = Integer.parseInt(parseCorefID[parseCorefID.length-1]);
        men.originalRef = -1;

        for(int j=allGoldMentions.size()-1 ; j>=0 ; j--){
          List<Mention> l = allGoldMentions.get(j);
          for(int k=l.size()-1 ; k>=0 ; k--){
            Mention m = l.get(k);
            if(men.goldCorefClusterID == m.goldCorefClusterID){
              men.originalRef = m.mentionID;
            }
          }
        }
        goldMentions.add(men);
        if(men.mentionID > maxID) maxID = men.mentionID;

        // set ner type
        for(int j = e.getExtentTokenStart() ; j < e.getExtentTokenEnd() ; j++){
          CoreLabel word = words.get(j);
          String ner = e.getType() +"-"+ e.getSubType();
          if(Constants.USE_GOLD_NE){
            word.set(CoreAnnotations.EntityTypeAnnotation.class, e.getMentionType());
            if(e.getMentionType().equals("NAM")) word.set(CoreAnnotations.NamedEntityTagAnnotation.class, ner);
          }
        }
      }
    }
  }

  private static void printRawDoc(List<CoreMap> sentences, List<List<Mention>> allMentions, String filename, boolean gold) throws FileNotFoundException {
    StringBuilder doc = new StringBuilder();
    int previousOffset = 0;
    Counter<Integer> mentionCount = new ClassicCounter<>();
    for(List<Mention> l : allMentions) {
      for(Mention m : l) {
        mentionCount.incrementCount(m.goldCorefClusterID);
      }
    }

    for(int i = 0 ; i<sentences.size(); i++) {
      CoreMap sentence = sentences.get(i);
      List<Mention> mentions = allMentions.get(i);

      String[] tokens = sentence.get(CoreAnnotations.TextAnnotation.class).split(" ");
      String sent = "";
      List<CoreLabel> t = sentence.get(CoreAnnotations.TokensAnnotation.class);
      if(previousOffset+2 < t.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) sent += "\n";
      previousOffset = t.get(t.size()-1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      Counter<Integer> startCounts = new ClassicCounter<>();
      Counter<Integer> endCounts = new ClassicCounter<>();
      Map<Integer, Set<Integer>> endID = Generics.newHashMap();
      for (Mention m : mentions) {
        startCounts.incrementCount(m.startIndex);
        endCounts.incrementCount(m.endIndex);
        if(!endID.containsKey(m.endIndex)) endID.put(m.endIndex, Generics.<Integer>newHashSet());
        endID.get(m.endIndex).add(m.goldCorefClusterID);
      }
      for (int j = 0 ; j < tokens.length; j++){
        if(endID.containsKey(j)) {
          for(Integer id : endID.get(j)){
            if(mentionCount.getCount(id)!=1 && gold) sent += "]_"+id;
            else sent += "]";
          }
        }
        for (int k = 0 ; k < startCounts.getCount(j) ; k++) {
          if(!sent.endsWith("[")) sent += " ";
          sent += "[";
        }
        sent += " ";
        sent = sent + tokens[j];
      }
      for(int k = 0 ; k <endCounts.getCount(tokens.length); k++) {
        sent += "]";
      }
      sent += "\n";
      doc.append(sent);
    }
    if (gold) logger.fine("New DOC: (GOLD MENTIONS) ==================================================");
    else logger.fine("New DOC: (Predicted Mentions) ==================================================");
    logger.fine(doc.toString());
  }
}
