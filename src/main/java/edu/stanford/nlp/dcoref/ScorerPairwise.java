package edu.stanford.nlp.dcoref;

import java.util.*;

public class ScorerPairwise extends CorefScorer {

  public ScorerPairwise(){
    super(ScoreType.Pairwise);
  }

  @Override
  protected void calculateRecall(Document doc) {
    int rDen = 0;
    int rNum = 0;
    Map<Integer, Mention> predictedMentions = doc.allPredictedMentions;

    for(CorefCluster g : doc.goldCorefClusters.values()) {
      int clusterSize = g.getCorefMentions().size();
      rDen += clusterSize*(clusterSize-1)/2;
      for(Mention m1 : g.getCorefMentions()){
        Mention predictedM1 = predictedMentions.get(m1.mentionID);
        if(predictedM1 == null) {
          continue;
        }
        for(Mention m2 : g.getCorefMentions()) {
          if(m1.mentionID >= m2.mentionID) continue;
          Mention predictedM2 = predictedMentions.get(m2.mentionID);
          if(predictedM2 == null) {
            continue;
          }
          if(predictedM1.corefClusterID == predictedM2.corefClusterID){
            rNum++;
          }
        }
      }
    }
    recallDenSum += rDen;
    recallNumSum += rNum;
  }

  @Override
  protected void calculatePrecision(Document doc) {
    int pDen = 0;
    int pNum = 0;

    Map<Integer, Mention> goldMentions = doc.allGoldMentions;

    for(CorefCluster c : doc.corefClusters.values()){
      int clusterSize = c.getCorefMentions().size();
      pDen += clusterSize*(clusterSize-1)/2;
      for(Mention m1 : c.getCorefMentions()){
        Mention goldM1 = goldMentions.get(m1.mentionID);
        if(goldM1 == null) {
          continue;
        }
        for(Mention m2 : c.getCorefMentions()) {
          if(m1.mentionID >= m2.mentionID) continue;
          Mention goldM2 = goldMentions.get(m2.mentionID);
          if(goldM2 == null) {
            continue;
          }
          if(goldM1.goldCorefClusterID == goldM2.goldCorefClusterID){
            pNum++;
          }
        }
      }
    }
    precisionDenSum += pDen;
    precisionNumSum += pNum;
  }
}
