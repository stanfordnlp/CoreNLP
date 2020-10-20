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

package edu.stanford.nlp.coref.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.coref.docreader.CoNLLDocumentReader;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.*;

public class Document implements Serializable {

  private static final long serialVersionUID = -4139866807494603953L;

  public enum DocType { CONVERSATION, ARTICLE }

  /** The type of document: conversational or article */
  public DocType docType;

  /** Document annotation */
  public Annotation annotation;

  /** for conll shared task 2011  */
  public CoNLLDocumentReader.CoNLLDocument conllDoc;

  /** The list of gold mentions */
  public List<List<Mention>> goldMentions;
  /** The list of predicted mentions */
  public List<List<Mention>> predictedMentions;

  /** return the list of predicted mentions */
  public List<List<Mention>> getOrderedMentions() {
    return predictedMentions;
  }

  /** Clusters for coreferent mentions */
  public Map<Integer, CorefCluster> corefClusters;

  /** Gold Clusters for coreferent mentions */
  public Map<Integer, CorefCluster> goldCorefClusters;

  /** All mentions in a document {@literal mentionID -> mention} */
  public Map<Integer, Mention> predictedMentionsByID;
  public Map<Integer, Mention> goldMentionsByID;

  /** Set of roles (in role apposition) in a document  */
  public Set<Mention> roleSet;

  /**
   * Position of each mention in the input matrix
   * Each mention occurrence with sentence # and position within sentence
   * (Nth mention, not Nth token)
   */
  public Map<Mention, IntTuple> positions;              // mentions may be removed from this due to post processing
  public Map<Mention, IntTuple> allPositions;           // all mentions (mentions will not be removed from this)

  public final Map<IntTuple, Mention> mentionheadPositions;

  /** List of gold links in a document by positions */
  private List<Pair<IntTuple,IntTuple>> goldLinks;

  /** UtteranceAnnotation {@literal ->} String (speaker): mention ID or speaker string
   *   e.g., the value can be "34" (mentionID), "Larry" (speaker string), or "PER3" (autoassigned speaker string)
   */
  public Map<Integer, String> speakers;

  /** Pair of mention id, and the mention's speaker id
   *  the second value is the "speaker mention"'s id.
   *  e.g., Larry said, "San Francisco is a city.": (id(Larry), id(San Francisco))
   */
  public Set<Pair<Integer, Integer>> speakerPairs;

  public boolean speakerInfoGiven;

  public int maxUtter;
  public int numParagraph;
  public int numSentences;

  /** Set of incompatible clusters pairs */
  private final Set<Pair<Integer, Integer>> incompatibles;
  private final Set<Pair<Integer, Integer>> incompatibleClusters;

  public Map<Pair<Integer, Integer>, Boolean> acronymCache;

  /** Map of speaker name/id to speaker info
   *  the key is the value of the variable 'speakers'
   */
  public Map<String, SpeakerInfo> speakerInfoMap = Generics.newHashMap();

  // public Counter<String> properNouns = new ClassicCounter<>();
  // public Counter<String> phraseCounter = new ClassicCounter<>();
  // public Counter<String> headwordCounter = new ClassicCounter<>();

  /** Additional information about the document. Can be used as features */
  public Map<String, String> docInfo;
    public Set<Triple<Integer,Integer,Integer>> filterMentionSet;

  public Document() {
    positions = Generics.newHashMap();
    mentionheadPositions = Generics.newHashMap();
    roleSet = Generics.newHashSet();
    corefClusters = Generics.newHashMap();
    goldCorefClusters = null;
    predictedMentionsByID = Generics.newHashMap();
//    goldMentionsByID = Generics.newHashMap();
    speakers = Generics.newHashMap();
    speakerPairs = Generics.newHashSet();
    incompatibles = Generics.newHashSet();
    incompatibleClusters = Generics.newHashSet();
    acronymCache = Generics.newHashMap();
  }

  public Document(Annotation anno, List<List<Mention>> predictedMentions, List<List<Mention>> goldMentions) {
    this();
    annotation = anno;
    this.predictedMentions = predictedMentions;
    this.goldMentions = goldMentions;
  }

  public Document(InputDoc input, List<List<Mention>> mentions) {
    this();
    this.annotation = input.annotation;
    this.predictedMentions = mentions;
    this.goldMentions = input.goldMentions;
    this.docInfo = input.docInfo;
    this.numSentences = input.annotation.get(SentencesAnnotation.class).size();
    this.conllDoc = input.conllDoc;   // null if it's not conll input
    this.filterMentionSet = input.filterMentionSet;
  }

  /**
   * Returns list of sentences, where token in the sentence is a list of strings (tags) associated with the sentence
   * @return
   */
  public List<List<String[]>> getSentenceWordLists() {
    if (this.conllDoc != null) {
      return this.conllDoc.sentenceWordLists;
    } else {
      List<List<String[]>> sentWordLists = new ArrayList<>();
      List<CoreMap> sentences = this.annotation.get(CoreAnnotations.SentencesAnnotation.class);
      String docId = this.annotation.get(CoreAnnotations.DocIDAnnotation.class);
      for (CoreMap sentence : sentences) {
        List<String[]> sentWordList = new ArrayList<>();
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for (CoreLabel token : tokens) {
          // Last column is coreference
          String[] strs = {docId, "-", String.valueOf(token.index()), token.word(), token.tag(), "-", "-",
                  token.getString(CoreAnnotations.SpeakerAnnotation.class, ""), token.ner(), "-"};
          sentWordList.add(strs);
        }
        sentWordLists.add(sentWordList);
      }
      return sentWordLists;
    }
  }

  public boolean isIncompatible(CorefCluster c1, CorefCluster c2) {
    // Was any of the pairs of mentions marked as incompatible
    int cid1 = Math.min(c1.clusterID, c2.clusterID);
    int cid2 = Math.max(c1.clusterID, c2.clusterID);
    return incompatibleClusters.contains(Pair.makePair(cid1,cid2));
  }

  // Update incompatibles for two clusters that are about to be merged
  public void mergeIncompatibles(CorefCluster to, CorefCluster from) {
    List<Pair<Pair<Integer,Integer>, Pair<Integer,Integer>>> replacements =
            new ArrayList<>();
    for (Pair<Integer, Integer> p:incompatibleClusters) {
      Integer other = null;
      if (p.first == from.clusterID) {
        other = p.second;
      } else if (p.second == from.clusterID) {
        other = p.first;
      }
      if (other != null && other != to.clusterID) {
        int cid1 = Math.min(other, to.clusterID);
        int cid2 = Math.max(other, to.clusterID);
        replacements.add(Pair.makePair(p, Pair.makePair(cid1, cid2)));
      }
    }
    for (Pair<Pair<Integer,Integer>, Pair<Integer,Integer>> r:replacements)  {
      incompatibleClusters.remove(r.first);
      incompatibleClusters.add(r.second);
    }
  }
  public void mergeAcronymCache(CorefCluster to, CorefCluster from) {
    Map<Pair<Integer, Integer>, Boolean> replacements = Generics.newHashMap();
    for(Pair<Integer, Integer> p : acronymCache.keySet()) {
      if(acronymCache.get(p)) {
        Integer other = null;
        if(p.first==from.clusterID){
          other = p.second;
        } else if(p.second==from.clusterID) {
          other = p.first;
        }
        if(other != null && other != to.clusterID) {
          int cid1 = Math.min(other, to.clusterID);
          int cid2 = Math.max(other, to.clusterID);
          replacements.put(Pair.makePair(cid1, cid2), true);
        }
      }
    }
    for(Pair<Integer, Integer> p : replacements.keySet()) {
      acronymCache.put(p, replacements.get(p));
    }
  }

  public boolean isIncompatible(Mention m1, Mention m2) {
    int mid1 = Math.min(m1.mentionID, m2.mentionID);
    int mid2 = Math.max(m1.mentionID, m2.mentionID);
    return incompatibles.contains(Pair.makePair(mid1,mid2));
  }

  public void addIncompatible(Mention m1, Mention m2) {
    int mid1 = Math.min(m1.mentionID, m2.mentionID);
    int mid2 = Math.max(m1.mentionID, m2.mentionID);
    incompatibles.add(Pair.makePair(mid1,mid2));
    int cid1 = Math.min(m1.corefClusterID, m2.corefClusterID);
    int cid2 = Math.max(m1.corefClusterID, m2.corefClusterID);
    incompatibleClusters.add(Pair.makePair(cid1,cid2));
  }

  public List<Pair<IntTuple, IntTuple>> getGoldLinks() {
    if(goldLinks==null) this.extractGoldLinks();
    return goldLinks;
  }

  /** Extract gold coref link information */
  protected void extractGoldLinks() {
    //    List<List<Mention>> orderedMentionsBySentence = this.getOrderedMentions();
    List<Pair<IntTuple, IntTuple>> links = new ArrayList<>();

    // position of each mention in the input matrix, by id
    Map<Integer, IntTuple> positions = Generics.newHashMap();
    // positions of antecedents
    Map<Integer, List<IntTuple>> antecedents = Generics.newHashMap();
    for(int i = 0; i < goldMentions.size(); i ++){
      for(int j = 0; j < goldMentions.get(i).size(); j ++){
        Mention m = goldMentions.get(i).get(j);
        int id = m.mentionID;
        IntTuple pos = new IntTuple(2);
        pos.set(0, i);
        pos.set(1, j);
        positions.put(id, pos);
        antecedents.put(id, new ArrayList<>());
      }
    }

//    SieveCoreferenceSystem.debugPrintMentions(System.err, "", goldOrderedMentionsBySentence);
    for (List<Mention> mentions : goldMentions) {
      for (Mention m : mentions) {
        int id = m.mentionID;
        IntTuple src = positions.get(id);

        assert (src != null);
        if (m.originalRef >= 0) {
          IntTuple dst = positions.get(m.originalRef);
          if (dst == null) {
            throw new RuntimeException("Cannot find gold mention with ID=" + m.originalRef);
          }

          // to deal with cataphoric annotation
          while (dst.get(0) > src.get(0) || (dst.get(0) == src.get(0) && dst.get(1) > src.get(1))) {
            Mention dstMention = goldMentions.get(dst.get(0)).get(dst.get(1));
            m.originalRef = dstMention.originalRef;
            dstMention.originalRef = id;

            if (m.originalRef < 0) break;
            dst = positions.get(m.originalRef);
          }
          if (m.originalRef < 0) continue;

          // A B C: if A<-B, A<-C => make a link B<-C
          for (int k = dst.get(0); k <= src.get(0); k++) {
            for (int l = 0; l < goldMentions.get(k).size(); l++) {
              if (k == dst.get(0) && l < dst.get(1)) continue;
              if (k == src.get(0) && l > src.get(1)) break;
              IntTuple missed = new IntTuple(2);
              missed.set(0, k);
              missed.set(1, l);
              if (links.contains(new Pair<>(missed, dst))) {
                antecedents.get(id).add(missed);
                links.add(new Pair<>(src, missed));
              }
            }
          }

          links.add(new Pair<>(src, dst));

          assert (antecedents.get(id) != null);
          antecedents.get(id).add(dst);

          List<IntTuple> ants = antecedents.get(m.originalRef);
          assert (ants != null);
          for (IntTuple ant : ants) {
            antecedents.get(id).add(ant);
            links.add(new Pair<>(src, ant));
          }
        }
      }
    }
    goldLinks = links;
  }

  public SpeakerInfo getSpeakerInfo(String speaker) {
    return speakerInfoMap.get(speaker);
  }

  public int numberOfSpeakers() {
    return speakerInfoMap.size();
  }

  public boolean isCoref(Mention m1, Mention m2) {
    return this.goldMentionsByID.containsKey(m1.mentionID)
        && this.goldMentionsByID.containsKey(m2.mentionID)
        && this.goldMentionsByID.get(m1.mentionID).goldCorefClusterID == this.goldMentionsByID.get(m2.mentionID).goldCorefClusterID;
  }

}
