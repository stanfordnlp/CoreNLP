package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;

import java.util.*;
import java.io.*;
import java.util.zip.*;

/**
 * This is a convenience class to hold sequence data that is
 * split into multiple documents and is divided into cliques.
 *
 * @author Jenny Finkel
 */

public class MultiDocumentCliqueDataset {

  private DatasetMetaInfo metaInfo = null;
  public List<CliqueDataset> datasets = null;
  private Type2FeatureFactory featureFactory;
  private SeqClassifierFlags flags = null;
  protected double[] Ehat = null; //need to keep stats when serializing
                         //for distributed.  additional thought
		                     //needed for how this will work if #features
                         //isn't constant.

  String actualSerializeDir, actualLoadDir, machine;
  

  public MultiDocumentCliqueDataset(DatasetMetaInfo metaInfo, SeqClassifierFlags flags,
                                    Iterable<List<CoreLabel>> dataOB) {

//    System.out.println("Creating new MultiDocumentCliqueDataset");
    this.metaInfo = metaInfo;
    this.flags = flags;
    if (flags.serializeDatasetsDir != null) {
      String hostname = System.getenv("HOST").toLowerCase();
      machine = hostname.substring(0, hostname.indexOf('.'));
      actualSerializeDir = flags.serializeDatasetsDir.replace("$MACH", machine);
      File sdir = new File(actualSerializeDir);
      sdir.mkdirs();
      System.out.println("Clearing "+actualSerializeDir);
      for (File f : sdir.listFiles()) {
        f.delete();
      }
    }
    if (flags.fakeDataset) {
      String hostname = System.getenv("HOST").toLowerCase();
      machine = hostname.substring(0, hostname.indexOf('.'));
      actualLoadDir = flags.loadDatasetsDir.replace("$MACH", machine);
      deserializeGlobalParams(actualLoadDir);
      this.flags = flags;
      datasets = new ArrayList<CliqueDataset>();
      File dir = new File(actualLoadDir);
      String[] filenames = dir.list(new CRFObjectiveFunction.FindFilter("dataset-"));
      if (filenames.length < 1) {
        throw new RuntimeException("MultiDocumentCliqueDataset: " +
                                   "Couldn't find any datasets.");
      }
      for (int i=0; i<filenames.length*flags.numDatasetsPerFile; i++) {
        datasets.add(new CliqueDataset(metaInfo, flags, new LinkedList<CoreLabel>()));
      }
    } else {
      setDocuments(dataOB);
    }
  }

  public MultiDocumentCliqueDataset(DatasetMetaInfo metaInfo, SeqClassifierFlags flags) {
    this.metaInfo = metaInfo;
    datasets = new ArrayList<CliqueDataset>();
    this.flags = flags;
  }

  public MultiDocumentCliqueDataset(String dir, boolean readInAll) {
    datasets = new ArrayList<CliqueDataset>();
    if (readInAll) {
      throw new UnsupportedOperationException();
    }
    deserializeGlobalParams(dir);
  }


  public DatasetMetaInfo metaInfo() {
    return metaInfo;
  }

  public SeqClassifierFlags getFlags() {
    return flags;
  }

  protected List<CliqueDataset> getDatasets() {
    return datasets;
  }

  public int numDocuments() { return (datasets == null ? 0 : datasets.size()); }

  public CliqueDataset getDocument(int docNum) {
    return datasets.get(docNum);
  }

  protected static void serializeObject(String filename, Object o){
    System.out.println("Serializing to "+filename+"...");
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(fos)));
      out.writeObject(o);
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void serializeGlobalParams() {
    String filename = actualSerializeDir+"/globalDatasetParams.ser.gz";
    GlobalDatasetParams globalParams = new GlobalDatasetParams();
    globalParams.metaInfo = metaInfo;
    globalParams.flags = flags;
    globalParams.timitFeatureMap = TIMITCliqueDatum.featureMap;
    globalParams.Ehat = Ehat;
    serializeObject(filename, globalParams);
  }

  LinkedList<CliqueDataset> l = null;
  int numFiles = 0;
  protected void serializeDataset(CliqueDataset d) {
    if (d == null && l != null && l.size() > 0) {
      String filename = actualSerializeDir + "/dataset-"+(++numFiles)+".ser.gz";
      serializeObject(filename, l);
    } else if (l == null) {
      l = new LinkedList<CliqueDataset>();
      l.add(d);
      if (flags.numDatasetsPerFile == 1) {
        String filename = actualSerializeDir + "/dataset-"+(++numFiles)+".ser.gz";
        serializeObject(filename, l);
        l.clear();
      }
    } else if (l.size() == flags.numDatasetsPerFile - 1 && d != null) {
      l.add(d);
      String filename = actualSerializeDir + "/dataset-"+(++numFiles)+".ser.gz";
      serializeObject(filename, l);
      l.clear();
    } else {
      l.add(d);
    }
  }


  protected GlobalDatasetParams deserializeGlobalParams(String dir) {
    String filename = dir+"/globalDatasetParams.ser.gz";
    System.out.println("Reading in "+filename+"...");
    try {
      FileInputStream fis = new FileInputStream(filename);
      ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
      GlobalDatasetParams globalParams = (GlobalDatasetParams)in.readObject();
      in.close();
      metaInfo = globalParams.metaInfo;
      flags = globalParams.flags;
      TIMITCliqueDatum.featureMap = globalParams.timitFeatureMap;
      Ehat = globalParams.Ehat;
      return globalParams;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }


  /**
   * Sets the documents for this dataset.  If called repeatedly then
   * the new docs will replace the old ones, but the meta info
   * (and hence all the features) will remain.  It is recomended to
   * only call this method once per instance.
   */
  private void setDocuments(Iterable<List<CoreLabel>> docs) {

    // add all the labels to the meta info
    for (List<CoreLabel> doc : docs) {
      for (CoreLabel word : doc) {
        metaInfo.addLabel(word.get(AnswerAnnotation.class));
      }
    }

    System.err.println(metaInfo.getLabels());

    datasets = new ArrayList<CliqueDataset>();
    int i = 0;
    CliqueDataset dataset = null;


    for (List<CoreLabel> doc : docs) {
      System.err.println("DOC "+(i++)+" ("+doc.size()+" words)");
      dataset = new CliqueDataset(metaInfo, flags, doc);
      if (!flags.purgeDatasets) {
        datasets.add(dataset);
      } else {
        if (flags.serializeDatasetsDir != null) {
          serializeDataset(dataset);
          if (i==1) {
            Ehat = new double[metaInfo.numFeatures()];
          }
          ObjectiveFunction.updateEhatForDataset(Ehat, dataset);
        }
        datasets.add(new CliqueDataset(metaInfo, flags, new LinkedList<CoreLabel>()));
      }
      checkNumFeatures();
//      System.err.println("numFeatures: "+metaInfo.numFeatures());
    }

    if (flags.serializeDatasetsDir != null) {
      serializeGlobalParams();
      if (!flags.purgeDatasets) {
        for (CliqueDataset d : datasets) {
          serializeDataset(d);
        }
      }
      serializeDataset(null);
    }

  }

  /**
   * Sometimes there are just too damn many features and so this method will
   * check if you are above the threshold and if so remove all singleton
   * features up to the current point.  so it is possible that you could end
   * up removing some features that occur more than other features which
   * aren't removed, depending on when they show up, but in general you
   * won't end up removing popular features.
   */
  private void checkNumFeatures() {
    if (flags.purgeFeatures > 0 && metaInfo.numFeatures() > flags.purgeFeatures) {
//      System.err.println(metaInfo().featureIndex.getClass().getName());
      // get good features
      int[] counts = new int[metaInfo().numFeatures()];

      for (int docNum = 0; docNum < numDocuments(); docNum++) {
        CliqueDataset doc = getDocument(docNum);
        for (int datumNum = 0; datumNum < doc.numDatums(); datumNum++) {
          int[] features = doc.features[datumNum].get(doc.maxCliqueLabels[datumNum]).features;
          for (int featureNum = 0; featureNum < features.length; featureNum++) {
            counts[features[featureNum]]++;
          }
        }
      }

      boolean[] featuresToKeep = new boolean[counts.length];
      for (int i = 0; i < featuresToKeep.length; i++) {
        featuresToKeep[i] = (counts[i] > 1);
      }

      // now remove the singletons

      HashIndex newFeatureIndex;
      if (metaInfo.hasOldStyleFeatureIndex()) {
        newFeatureIndex = metaInfo().getOldStyleFeatureIndex();
      } else {
        newFeatureIndex = new HashIndex();
      }
      for (int i = 0; i < featuresToKeep.length; i++) {
        if (featuresToKeep[i]) {
          newFeatureIndex.add(metaInfo().getFeature(i));
        }
      }

      for (int docNum = 0; docNum < numDocuments(); docNum++) {
        CliqueDataset doc = getDocument(docNum);
        for (int datumNum = 0; datumNum < doc.numDatums(); datumNum++) {
          for (LabeledClique lc : doc.features[datumNum].keySet()) {
            Features featureInfo = doc.features[datumNum].get(lc);
            int[] features = featureInfo.features;
            double[] values = new double[features.length];
            int num = 0;
            for (int featureNum = 0; featureNum < features.length; featureNum++) {
              if (featuresToKeep[features[featureNum]]) { num++; }
            }
            boolean isBoolean = featureInfo.isBoolean();
            int index = 0;
            for (int featureNum = 0; featureNum < features.length; featureNum++) {
              int newIndex = newFeatureIndex.indexOf(metaInfo().getFeature(features[featureNum]));
              if (newIndex >= 0) {
                features[index] = newIndex;
                if (!isBoolean) {
                  values[index] = featureInfo.value(featureNum);
                }
                index++;
              }
            }

            int[] newFeatures = new int[index];
            System.arraycopy(features, 0, newFeatures, 0, index);
            featureInfo.features = newFeatures;
            float[] newValues = null;
            if (!isBoolean) {
              newValues = new float[index];
              System.arraycopy(values, 0, newValues, 0, index);
              featureInfo.setValues(newValues);
            }
          }
        }
      }

      metaInfo().setFeatureIndex(newFeatureIndex);

    }
  }

  /**
   * This method is used when a document is too large and must be
   * split up into several smaller documents.  It uses flags.maxDocSize
   * though in reality docs can be slightly larger than this, because if
   * it is in the middle of an entity (non-background symbol) it
   * will keep adding until it exits the entity.
   */
  private List<List<CoreLabel>> splitDoc(List<CoreLabel> doc) {
    List<List<CoreLabel>> docs = new ArrayList<List<CoreLabel>>();

    List<CoreLabel> newDoc = new ArrayList<CoreLabel>();
    for (int i = 0; i < doc.size(); i++) {
      if (i % flags.maxDocSize == 0) {
        while (!doc.get(i).get(AnswerAnnotation.class).equals(flags.backgroundSymbol)) {
          newDoc.add(doc.get(i++));
        }
        newDoc.add(doc.get(i));
        docs.add(newDoc);
        newDoc = new ArrayList<CoreLabel>();
      } else {
        newDoc.add(doc.get(i));
      }
    }

    if (newDoc.size() > 0) {
      docs.add(newDoc);
    }

    return docs;
  }

  /**
   * This method prints a summary of the dataset.  Its format is
   * subject to change, but currently it displays the number of
   * documents, number of features, number of labels, and the
   * labels themselves.
   */
  public void printStats(PrintWriter out) {
    out.println("===================================================");
    out.println("numDocuments: "+datasets.size());
    out.println("numFeatures: "+metaInfo.numFeatures());
    out.println("numLabels: "+metaInfo.numLabels()+" -- "+metaInfo.getLabels());
    out.println("===================================================");
  }

}
