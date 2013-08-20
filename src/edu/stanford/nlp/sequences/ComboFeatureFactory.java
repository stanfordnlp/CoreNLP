package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.util.*;
import java.io.File;

/**
 * This is a feature factory for generating features with real values
 * instead of just with boolean values.
 *
 * @author Jenny Finkel
 */
public abstract class ComboFeatureFactory extends Type2FeatureFactory<CoreLabel> {

  private static final long serialVersionUID = -5170995585456062462L;

  protected transient List<SequenceClassifier> classifiersToCombine = null;
  protected transient TwoDimensionalMap<List<CoreLabel>,SequenceClassifier,List<CoreLabel>> dataMap = null;
  protected transient TwoDimensionalMap<LabeledClique,SequenceClassifier,LabeledClique> lcMap = new TwoDimensionalMap<LabeledClique,SequenceClassifier,LabeledClique>();
  private List<String> comboProps = null;
  private DatasetMetaInfo metaInfo;

  @Override
  public void init (SeqClassifierFlags flags) {
    super.init(flags);

    System.err.println("in init");
    System.err.println(this.comboProps);
    this.comboProps = flags.comboProps;
    classifiersToCombine = new ArrayList<SequenceClassifier>();
    for (String prop : new HashSet<String>(comboProps)) {
      System.err.println("--> "+prop);
      try {
        SequenceClassifier sc = SequenceClassifier.getClassifier(new String[]{"-loadClassifier", prop});
        classifiersToCombine.add(sc);
      } catch (Exception e) { throw new RuntimeException(e); }
    }
  }

  protected Object readResolve() {
    init(flags);
    lcMap = new TwoDimensionalMap<LabeledClique,SequenceClassifier,LabeledClique>();
    return this;
  }

  public void dealWithData(ObjectBank<List<CoreLabel>> data, File file) {
    System.err.println("==> "+file);
    dataMap = new TwoDimensionalMap<List<CoreLabel>,SequenceClassifier,List<CoreLabel>>(MapFactory.<List<CoreLabel>,Map<SequenceClassifier,List<CoreLabel>>>identityHashMapFactory(),
            MapFactory.<SequenceClassifier,List<CoreLabel>>identityHashMapFactory());
    for (SequenceClassifier sc : classifiersToCombine) {
      sc.flags.inputEncoding = flags.inputEncoding;
      ObjectBank<List<CoreLabel>> scData = sc.getObjectBank(file);
      Iterator<List<CoreLabel>> mainIter = data.iterator();
      Iterator<List<CoreLabel>> scIter = scData.iterator();
      int ii = 0;
      while (mainIter.hasNext()) {
        List<CoreLabel> mainDatum = mainIter.next();
        List<CoreLabel> scDatum = scIter.next();
        if (mainDatum.size() != scDatum.size()) {
          System.err.println(ii);
          System.err.println(mainDatum.size()+"\t"+scDatum.size());
          for (int i = 0; i < 10; i++) {
            System.err.print(mainDatum.get(i).get(AnswerAnnotation.class));
            System.err.print(" ");
          }
          System.err.println();
          for (int i = 0; i < 10; i++) {
            System.err.print(scDatum.get(i).get(AnswerAnnotation.class)+" ");
          }
          System.err.println();
          throw new RuntimeException();
        }
        dataMap.put(mainDatum, sc, scDatum);
      }
    }
  }

  public void setMetaInfo(DatasetMetaInfo metaInfo) {
    this.metaInfo = metaInfo;
  }

  public List<CoreLabel> getData(List<CoreLabel> origData, SequenceClassifier sc) {
    return dataMap.get(origData,sc);
  }

  public LabeledClique getLabeledClique(LabeledClique origLC, SequenceClassifier sc) {
    LabeledClique otherLC = lcMap.get(origLC, sc);
    if (otherLC == null) {
      int[] labels = new int[origLC.size()];
      for (int i = 0; i < labels.length; i++) {
        Object label = metaInfo.getLabel(origLC.label(i));
        int newIndex = sc.modelFactory.metaInfo().indexOfLabel(label);
        if (newIndex == -1) { return null; }
        labels[i] = newIndex;
      }
      otherLC = LabeledClique.valueOf(origLC.clique, labels);
      lcMap.put(origLC, sc, otherLC);
    }
    return otherLC;
  }


}
