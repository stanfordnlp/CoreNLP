package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.ReversedList;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerObjectAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PossibleAnswersAnnotation;
import edu.stanford.nlp.stats.ClassicCounter;

import java.util.*;
import java.io.*;

/**
 * This is a convienience class to hold sequence data that is
 * divided into cliques.
 * It has {@link edu.stanford.nlp.util.Index}es for the labels,
 * the {@link CliqueDatum}s, the {@link Clique}s and the 
 * {@link LabeledClique}s.  
 * 
 * @author Jenny Finkel
 */

public class CliqueDataset implements Serializable {
  static final long serialVersionUID = -2162643681374762273L;
  private transient DatasetMetaInfo metaInfo = null;
//  private transient Type2FeatureFactory featureFactory;
  private transient SeqClassifierFlags flags = null;
  
  public CliqueDatum[] features;
  public int[] labels;
  public int[][] possibleLabels; // for each position and clique, gives clique label
  public LabeledClique[] maxCliqueLabels; // index into features/values arrays

  List<CoreLabel> sourceDoc;
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CliqueDataset of " + numDatums() + " datums*******");
    sb.append("\nmetaInfo: " + metaInfo);
    sb.append("\nfeatures: " + features +": ");
    if (features != null) {
      sb.append("\n\t"+features[0]);
    }
    sb.append("\nlabels: " + labels);
    sb.append("\npossibleLabels: " + possibleLabels);
    sb.append("\nmaxCliqueLabels: " + maxCliqueLabels);
    sb.append("\n************************************");
    return sb.toString();
  }
  
  private CliqueDataset() {}
    
  public CliqueDataset(DatasetMetaInfo metaInfo, SeqClassifierFlags flags, List<CoreLabel> doc) {
    this.metaInfo = metaInfo;    
//    this.featureFactory = metaInfo.featureFactory();
    this.flags = flags;
    if (flags.evalCmd != null && flags.evaluateIters > 0) {
      sourceDoc = doc;
    }
    setDocument(new PaddedList<CoreLabel>(doc, flags.pad));
  }

  public DatasetMetaInfo metaInfo() { return metaInfo; }
  protected void setMetaInfo(DatasetMetaInfo info) { 
    metaInfo = info;
//    featureFactory = info.featureFactory();
  }
  protected void setFlags(SeqClassifierFlags f) {
    flags = f;
  }
  
  public int numDatums() { return (features == null ? 0 : features.length); }
  
  private void setDocument(PaddedList<CoreLabel> doc) {
    features = new CliqueDatum[doc.size()];

    labels = new int[doc.size()];
    possibleLabels = new int[doc.size()][];
    maxCliqueLabels = new LabeledClique[doc.size()];

    // we have to add these all now instead of in setDatum()
    // cos the cliques may care about future labels and we need to
    // be sure that they are in the labelIndex
    boolean pa = false;
    for (int i = 0; i < doc.size(); i++) {
      String ans = doc.get(i).get(AnswerAnnotation.class);
      if (doc.get(i).get(PossibleAnswersAnnotation.class) != null && !pa) {
        if (i != 0) {
          throw new RuntimeException("Must specify possible answers for either all or none of the datums!");
        }
        pa = true;
      } else if (doc.get(i).get(PossibleAnswersAnnotation.class) == null && pa) {
        System.err.println(i+" >> "+doc.get(i));
        throw new RuntimeException("Must specify possible answers for either all or none of the datums!");
      }
      metaInfo.addLabel(ans);
      labels[i] = metaInfo.indexOfLabel(ans);
      if (labels[i] < 0) {
        // this is so the combo stuff works
        // id like to think of a more elegant solution
        labels[i] = metaInfo.backgroundIndex();
        //throw new RuntimeException("Label ("+ans+") not in labelIndex!");
      }
    }

    if (!flags.useObservedSequencesOnly) {
      metaInfo.setAllowedSequences(metaInfo.allSequences());
    }
    
    if (pa) {
      for (int i = 0; i < doc.size(); i++) {
        List possibleAnswers = Arrays.asList(doc.get(i).get(PossibleAnswersAnnotation.class).split(","));
        possibleLabels[i] = new int[possibleAnswers.size()];
        int j = 0;
        for (Object possibleAnswer : possibleAnswers) {
          metaInfo.addLabel(possibleAnswer);
          possibleLabels[i][j++] = metaInfo.indexOfLabel(possibleAnswer);
        }
      }
    } else {      
      int[] pl = new int[metaInfo.numLabels()];
      for (int i = 0; i < pl.length; i++) {
        pl[i] = i;
      }
      for (int i = 0; i < doc.size(); i++) {
        possibleLabels[i] = pl;
      }
      cacheMaxCliqueLabels = true;
    }

    for (int i = 0; i < doc.size(); i++) {
      setDatum(doc, i);
    }
  }

  private Map<LabeledClique, int[]> timitMap = new HashMap<LabeledClique, int[]>();
  int a = 0, b = 0;  
  private void setDatum(PaddedList<CoreLabel> doc, int datumNum) {
    this.maxCliqueLabels[datumNum] = LabeledClique.valueOf(metaInfo.getMaxClique(), labels, datumNum, metaInfo.backgroundIndex());
    List<LabeledClique> maxCliqueLabels = getMaxCliqueLabels(datumNum);

    CliqueDatum cd;
    Type2FeatureFactory featureFactory = metaInfo.featureFactory();
    if (featureFactory instanceof FeatureFactoryWrapper && (flags.memoryThrift || flags.timitDatum)) {
      if (flags.timitDatum) {
        cd = new TIMITCliqueDatum(datumNum < flags.maxLeft || flags.restrictTransitionsTimit);
      } else {
        cd = new CliqueDatumType1(metaInfo.fm, maxCliqueLabels);
      }
    } else {
      cd = new CliqueDatumType2();
    }
    this.features[datumNum] = cd;

    if (flags.timitDatum) {
      float[] values = null;
      for (LabeledClique maxCliqueLabel : maxCliqueLabels) {
        for (int i = 0; i < maxCliqueLabel.size(); i++) {
          doc.get(datumNum+maxCliqueLabel.clique.relativeIndex(i)).set(AnswerObjectAnnotation.class, metaInfo.getLabel(maxCliqueLabel.label(i)));
        }

        int[] features = timitMap.get(maxCliqueLabel);
        if (features == null || values == null) {
        
          ClassicCounter datumFeatures = featureFactory.getFeatures(doc, datumNum, maxCliqueLabel);
        
          metaInfo.addFeatures(datumFeatures.keySet());
        
          int[] tmpFeatures = new int[datumFeatures.keySet().size()];
          float[] tmpValues = new float[datumFeatures.keySet().size()];
        
          Collection keys = datumFeatures.keySet();
          List l = new ArrayList(keys);
          Collections.sort(l);
          keys = l;
        
          int featureNum = 0;      
          for (Object feature : keys) {
            
            int index = metaInfo.indexOfFeature(feature);
            if (index >= 0) {
              tmpFeatures[featureNum] = index;
              if (values == null) {
                tmpValues[featureNum] = (float)datumFeatures.getCount(feature);
              }
              featureNum++;
            }
          }
          features = new int[featureNum];
          System.arraycopy(tmpFeatures, 0, features, 0, featureNum);
          if (values == null) {
            values = new float[featureNum];    
            System.arraycopy(tmpValues, 0, values, 0, featureNum);
          }
          timitMap.put(maxCliqueLabel, features);
        }
        cd.setFeatures(maxCliqueLabel, features, values);
      }
    } else {
      for (LabeledClique maxCliqueLabel : maxCliqueLabels) {
        for (int i = 0; i < maxCliqueLabel.size(); i++) {
          doc.get(datumNum+maxCliqueLabel.clique.relativeIndex(i)).set(AnswerObjectAnnotation.class, metaInfo.getLabel(maxCliqueLabel.label(i)));
        }
        ClassicCounter datumFeatures;
        if (flags.useReverse) {
          int offset = maxCliqueLabel.clique.maxLeft()+maxCliqueLabel.clique.maxRight();
          datumFeatures = featureFactory.getFeatures(new PaddedList(new ReversedList(doc), doc.getPad()), doc.size()-1-datumNum-offset, maxCliqueLabel.reversedLabels());
        } else {         
          datumFeatures = featureFactory.getFeatures(doc, datumNum, maxCliqueLabel);
        }

        metaInfo.addFeatures(datumFeatures.keySet());
        
        int[] tmpFeatures = new int[datumFeatures.keySet().size()];
        float[] tmpValues = new float[datumFeatures.keySet().size()];
        
        Collection keys = datumFeatures.keySet();
        if (cd instanceof CliqueDatumType1 || cd instanceof TIMITCliqueDatum) {
          List l = new ArrayList(keys);
          Collections.sort(l);
          keys = l;
        }
        
        int featureNum = 0;      
        for (Object feature : keys) {
          
          int index = metaInfo.indexOfFeature(feature);
          if (index >= 0) {
            tmpFeatures[featureNum] = index;
            if (!flags.booleanFeatures) {
              tmpValues[featureNum] = (float)datumFeatures.getCount(feature);
            }
            featureNum++;
          }
        }
        int[] features = new int[featureNum];
        System.arraycopy(tmpFeatures, 0, features, 0, featureNum);
        if (flags.booleanFeatures) {
          cd.setFeatures(maxCliqueLabel, features);
        } else {
          float[] values = new float[featureNum];    
          System.arraycopy(tmpValues, 0, values, 0, featureNum);
          cd.setFeatures(maxCliqueLabel, features, values);
        }
      }
    }
  }
  
  private boolean cacheMaxCliqueLabels = false; // if all datums have the same set of possible labels
  private List<LabeledClique> maxCliqueLabelsCache = null;
  
  public List<LabeledClique> getMaxCliqueLabels(int datumNum) {

    if (flags.timitDatum && flags.restrictTransitionsTimit) {
      return getTimitMaxCliqueLabels(datumNum);
    }
    
    if (cacheMaxCliqueLabels && maxCliqueLabelsCache != null && datumNum > metaInfo.leftWindow() && datumNum < numDatums()-metaInfo.rightWindow()) { return maxCliqueLabelsCache; }
    
    Clique c = metaInfo.getMaxClique();

    int[] labels = new int[c.size()];
    int[] labelIndex = new int[c.size()];

    Arrays.fill(labelIndex, 0);

    List<LabeledClique> maxLabels = new ArrayList<LabeledClique>();
    
    while (true) {

      for (int i = 0; i < labels.length; i++) {
        if (datumNum+c.relativeIndex(i) < 0) {
          labels[i] = metaInfo.backgroundIndex();
        } else {
          labels[i] = possibleLabels[datumNum+c.relativeIndex(i)][labelIndex[i]];
        }
      }

      LabeledClique lc = LabeledClique.valueOf(c, labels);
      maxLabels.add(lc);
      
      boolean done = true;
      for (int i = 0; i < labelIndex.length; i++) {
        if (datumNum+c.relativeIndex(i) < 0 || labelIndex[i] == possibleLabels[datumNum+c.relativeIndex(i)].length-1) {
          labelIndex[i] = 0;
        } else {
          labelIndex[i]++;
          done = false;
          break;
        }
      }
      
      if (done) { break; }
    }
    
    metaInfo.retainAllowed(maxLabels);
    
    if (cacheMaxCliqueLabels && datumNum > metaInfo.leftWindow() && datumNum < numDatums()-metaInfo.rightWindow()) {
      maxCliqueLabelsCache = Collections.unmodifiableList(maxLabels);
    }
    
    return maxLabels;
  }

  public List<LabeledClique> getTimitMaxCliqueLabels(int datumNum) {
    flags.restrictTransitionsTimit = false;
    List<LabeledClique> allLabels = getMaxCliqueLabels(datumNum);
    flags.restrictTransitionsTimit = true;
    if (datumNum == 0 || labels[datumNum] != labels[datumNum-1]) { return allLabels; }
    List<LabeledClique> noTransitionLabels = new ArrayList<LabeledClique>();
    for (LabeledClique lc : allLabels) {
      boolean good = true;
      int first = lc.label(0);
      for (int i = 0; i < lc.clique.size(); i++) {
        if (lc.label(i) != first) {
          good = false;
          break;
        }
      }
      if (good) {
        noTransitionLabels.add(lc);
      }
    }
    return noTransitionLabels;
  }


  public List<LabeledClique> getTimitMaxCliqueConditionalLabels(int datumNum, LabeledClique labeledClique) {
    flags.restrictTransitionsTimit = false;
    List<LabeledClique> allLabels = getMaxCliqueConditionalLabels(datumNum, labeledClique);
    flags.restrictTransitionsTimit = true;
    if (datumNum == 0 || labels[datumNum] != labels[datumNum-1]) { return allLabels; }
    List<LabeledClique> noTransitionLabels = new ArrayList<LabeledClique>();
    for (LabeledClique lc : allLabels) {
      boolean good = true;
      int first = lc.label(0);
      for (int i = 0; i < lc.clique.size(); i++) {
        if (lc.label(i) != first) {
          good = false;
          break;
        }
      }
      if (good) {
        noTransitionLabels.add(lc);
      }
    }
    return noTransitionLabels;
  }

  
  private Map<LabeledClique, List<LabeledClique>> maxCliqueConditionalLabelsCache = new HashMap<LabeledClique,List<LabeledClique>>();
  
  public List<LabeledClique> getMaxCliqueConditionalLabels(int datumNum, LabeledClique lc) {

    if (flags.timitDatum && flags.restrictTransitionsTimit) {
      return getTimitMaxCliqueConditionalLabels(datumNum, lc);
    }


    if (cacheMaxCliqueLabels && maxCliqueConditionalLabelsCache.keySet().contains(lc) && datumNum > metaInfo.leftWindow() && datumNum < numDatums()-metaInfo.rightWindow()) {
      return maxCliqueConditionalLabelsCache.get(lc);
    }
    
    if (lc.clique != metaInfo.getMaxClique()) {
      throw new RuntimeException("This method is only valid for the maximum clique!");
    }
    
    Clique c = metaInfo.getMaxClique();

    int[] labels = new int[c.size()];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = lc.label(i);
    }

    List<LabeledClique> maxLabels = new ArrayList<LabeledClique>();
    int index = c.indexOfRelativeIndex(0);
    
    for (int i = 0; i < possibleLabels[datumNum].length; i++) {
      labels[index] = possibleLabels[datumNum][i];
      maxLabels.add(LabeledClique.valueOf(c, labels));
    }

    metaInfo.retainAllowed(maxLabels);
    
    if (cacheMaxCliqueLabels && datumNum > metaInfo.leftWindow() && datumNum < numDatums()-metaInfo.rightWindow()) {
      maxCliqueConditionalLabelsCache.put(lc, Collections.unmodifiableList(maxLabels));
    }
    
    return maxLabels;

  }
  
}
