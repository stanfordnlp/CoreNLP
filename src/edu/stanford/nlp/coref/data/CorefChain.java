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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.coref.CorefCoreAnnotations;

import edu.stanford.nlp.coref.data.Dictionaries.Animacy;
import edu.stanford.nlp.coref.data.Dictionaries.Gender;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Dictionaries.Number;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.IntTuple;

/**
 * Output of (deterministic) coref system.  Each CorefChain represents a set
 * of mentions in the text which should all correspond to the same actual
 * entity.  There is a representative mention, which stores the best
 * mention of an entity, and then there is a List of all mentions
 * that are coreferent with that mention. The mentionMap maps from pairs of
 * a sentence number and a head word index to a CorefMention. The chainID is
 * an arbitrary integer for the chain number.
 *
 * @author Heeyoung Lee
 */
public class CorefChain implements Serializable {

  private final int chainID;
  private final List<CorefMention> mentions;
  private final Map<IntPair, Set<CorefMention>> mentionMap;

  /** The most representative mention in this cluster */
  private final CorefMention representative;

  @Override
  public boolean equals(Object aThat) {
    if (this == aThat)
      return true;
    if (!(aThat instanceof CorefChain))
      return false;
    CorefChain that = (CorefChain) aThat;
    if (chainID != that.chainID)
      return false;
    if (!mentions.equals(that.mentions))
      return false;
    if (representative == null && that.representative == null) {
      return true;
    }
    if (representative == null || that.representative == null ||
        ! representative.equals(that.representative)) {
      return false;
    }
    // mentionMap is another view of mentions, so no need to compare
    // that once we've compared mentions
    return true;
  }

  @Override
  public int hashCode() {
    return mentions.hashCode();
  }

  /** get List of CorefMentions */
  public List<CorefMention> getMentionsInTextualOrder() { return mentions; }

  /** get CorefMentions by position (sentence number, headIndex) Can be multiple mentions sharing headword */
  public Set<CorefMention> getMentionsWithSameHead(IntPair position) { return mentionMap.get(position); }

  /** get CorefMention by position */
  public Set<CorefMention> getMentionsWithSameHead(int sentenceNumber, int headIndex) {
    return mentionMap.get(new IntPair(sentenceNumber, headIndex));
  }

  public Map<IntPair, Set<CorefMention>> getMentionMap() { return mentionMap; }

  /** Return the most representative mention in the chain.
   *  Proper mention and a mention with more pre-modifiers are preferred.
   */
  public CorefMention getRepresentativeMention() { return representative; }
  public int getChainID() { return chainID; }

  /** Mention for coref output.  This is one instance of the entity
   * referred to by a given CorefChain.
   */
  public static class CorefMention implements Serializable {
    public final MentionType mentionType;
    public final Number number;
    public final Gender gender;
    public final Animacy animacy;

    /**
     * Starting word number within the sentence, indexed from 1
     */
    public final int startIndex;
    /**
     * One past the end word number within the sentence, indexed from 1
     */
    public final int endIndex;
    /**
     * Head word of the mention
     */
    public final int headIndex;
    public final int corefClusterID;
    public final int mentionID;
    /**
     * Sentence number in the document containing this mention,
     * indexed from 1.
     */
    public final int sentNum;
    /**
     * Position is a binary tuple of (sentence number, mention number
     * in that sentence).  This is used for indexing by mention.
     */
    public final IntTuple position;
    public final String mentionSpan;

    public CorefMention(MentionType mentionType,
            Number number,
            Gender gender,
            Animacy animacy,
            int startIndex,
            int endIndex,
            int headIndex,
            int corefClusterID,
            int mentionID,
            int sentNum,
            IntTuple position,
            String mentionSpan) {
      this.mentionType = mentionType;
      this.number = number;
      this.gender = gender;
      this.animacy = animacy;
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.headIndex = headIndex;
      this.corefClusterID = corefClusterID;
      this.mentionID = mentionID;
      this.sentNum = sentNum;
      this.position = position;
      this.mentionSpan = mentionSpan;
    }

    public CorefMention(Mention m, IntTuple pos){
      mentionType = m.mentionType;
      number = m.number;
      gender = m.gender;
      animacy = m.animacy;
      startIndex = m.startIndex + 1;
      endIndex = m.endIndex + 1;
      headIndex = m.headIndex + 1;
      corefClusterID = m.corefClusterID;
      sentNum = m.sentNum + 1;
      mentionID = m.mentionID;
      mentionSpan = m.spanToString();

      // index starts from 1
      position = new IntTuple(2);
      position.set(0, pos.get(0)+1);
      position.set(1, pos.get(1)+1);

      m.headWord.set(CorefCoreAnnotations.CorefClusterIdAnnotation.class, corefClusterID);
    }

    @Override
    public boolean equals(Object aThat) {
      if (this == aThat)
        return true;
      if (!(aThat instanceof CorefMention))
        return false;
      CorefMention that = (CorefMention) aThat;
      if (mentionType != that.mentionType)
        return false;
      if (number != that.number)
        return false;
      if (gender != that.gender)
        return false;
      if (animacy != that.animacy)
        return false;
      if (startIndex != that.startIndex)
        return false;
      if (endIndex != that.endIndex)
        return false;
      if (headIndex != that.headIndex)
        return false;
      if (corefClusterID != that.corefClusterID)
        return false;
      if (mentionID != that.mentionID)
        return false;
      if (sentNum != that.sentNum)
        return false;
      if (!position.equals(that.position))
        return false;
      // we ignore MentionSpan as it is constructed from the tokens
      // the mention is a span of, so if we know those spans are the
      // same, we should be able to ignore the actual text
      return true;
    }

    @Override
    public int hashCode() {
      return position.hashCode();
    }

    @Override
    public String toString() {
      return '"' + mentionSpan + '"' + " in sentence " + sentNum;
      //      return "(sentence:" + sentNum + ", startIndex:" + startIndex + "-endIndex:" + endIndex + ")";
    }

    private boolean moreRepresentativeThan(CorefMention m) {
      if (m==null) return true;
      if (mentionType != m.mentionType) {
        return (mentionType == MentionType.PROPER)
            || (mentionType == MentionType.NOMINAL && m.mentionType == MentionType.PRONOMINAL);
      } else {
        // First, check length
        if (headIndex - startIndex > m.headIndex - m.startIndex) return true;
        if (headIndex - startIndex < m.headIndex - m.startIndex) return false;
        if (endIndex - startIndex > m.endIndex - m.startIndex) return true;
        if (endIndex - startIndex < m.endIndex - m.startIndex) return false;
        // Now check relative position
        if (sentNum < m.sentNum) return true;
        if (sentNum > m.sentNum) return false;
        if (headIndex < m.headIndex) return true;
        if (headIndex > m.headIndex) return false;
        if (startIndex < m.startIndex) return true;
        if (startIndex > m.startIndex) return false;
        // At this point they're equal...
        return false;
      }
    }

    private static final long serialVersionUID = 3657691243504173L;
  }

  public static class CorefMentionComparator implements Comparator<CorefMention> {
    @Override
    public int compare(CorefMention m1, CorefMention m2) {
      if(m1.sentNum < m2.sentNum) return -1;
      else if(m1.sentNum > m2.sentNum) return 1;
      else{
        if(m1.startIndex < m2.startIndex) return -1;
        else if(m1.startIndex > m2.startIndex) return 1;
        else {
          if( m1.endIndex > m2.endIndex) return -1;
          else if(m1.endIndex < m2.endIndex) return 1;
          else return 0;
        }
      }
    }
  }

  public static class MentionComparator implements Comparator<Mention> {
    @Override
    public int compare(Mention m1, Mention m2) {
      if (m1.sentNum < m2.sentNum) return -1;
      else if (m1.sentNum > m2.sentNum) return 1;
      else{
        if (m1.startIndex < m2.startIndex) return -1;
        else if (m1.startIndex > m2.startIndex) return 1;
        else {
          if (m1.endIndex > m2.endIndex) return -1;
          else if(m1.endIndex < m2.endIndex) return 1;
          else return 0;
        }
      }
    }
  }

  public CorefChain(CorefCluster c, Map<Mention, IntTuple> positions){
    chainID = c.clusterID;
    // Collect mentions
    mentions = new ArrayList<>();
    mentionMap = Generics.newHashMap();
    CorefMention represents = null;
    for (Mention m : c.getCorefMentions()) {
      CorefMention men = new CorefMention(m, positions.get(m));
      mentions.add(men);
    }
    mentions.sort(new CorefMentionComparator());
    // Find representative mention
    for (CorefMention men : mentions) {
      IntPair position = new IntPair(men.sentNum, men.headIndex);
      if (!mentionMap.containsKey(position)) mentionMap.put(position, Generics.newHashSet());
      mentionMap.get(position).add(men);
      if (men.moreRepresentativeThan(represents)) {
        represents = men;
      }
    }
    representative = represents;
  }

  /** Constructor required by CustomAnnotationSerializer */
  public CorefChain(int cid,
                    Map<IntPair, Set<CorefMention>> mentionMap,
                    CorefMention representative) {
    this.chainID = cid;
    this.representative = representative;
    this.mentionMap = mentionMap;
    this.mentions = new ArrayList<>();
    for (Set<CorefMention> ms : mentionMap.values()) {
      this.mentions.addAll(ms);
    }
    mentions.sort(new CorefMentionComparator());
  }

  /**
   * Delete a mention from this coreference chain.
   * @param m The mention to delete.
   */
  public void deleteMention(CorefMention m) {
    this.mentions.remove(m);
    IntPair position = new IntPair(m.sentNum, m.headIndex);
    this.mentionMap.remove(position);
  }

  public String toString(){
    return "CHAIN" + this.chainID + '-' + mentions;
  }

  private static final long serialVersionUID = 3657691243506528L;

}
