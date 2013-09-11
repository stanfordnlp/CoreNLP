 package edu.stanford.nlp.ie.crf;

 import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
 import edu.stanford.nlp.ling.CoreLabel;
 import edu.stanford.nlp.maxent.Convert;
 import edu.stanford.nlp.objectbank.ObjectBank;
 import edu.stanford.nlp.optimization.Function;
 import edu.stanford.nlp.optimization.QNMinimizer;
 import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
 import edu.stanford.nlp.sequences.ExactBestSequenceFinder;
 import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter;
 import edu.stanford.nlp.util.Index;
 import edu.stanford.nlp.util.Triple;
 import edu.stanford.nlp.util.StringUtils;
 import edu.stanford.nlp.util.HashIndex;

 import java.io.*;
 import java.util.*;
 import java.util.zip.GZIPInputStream;
 import java.util.zip.GZIPOutputStream;

 public class MAPCRFClassifier extends CRFClassifier<CoreLabel> {

  Index<String> testIndex;
  List selfTrainDatums = null;

  public static boolean VERBOSE = false;

  protected MAPCRFClassifier() {
    super();
  }

  public MAPCRFClassifier(Properties props) {
    super(props);
  }




  /**
   * Returns a Triple, where the first element is an int[][][] representing the data
   * and the second element is an int[] representing the labels, and the thired (optional)
   * element represents the feature non-binary values
   */
  @Override
  public Triple<int[][][],int[], double[][][]> documentToDataAndLabels(List<CoreLabel> document) {
    int docSize = document.size();
    // first index is position in the document also the index of the clique/factor table
    // second index is the number of elements in the clique/window thase features are for (starting with last element)
    // third index is position of the feature in the array that holds them
    // element in data[j][k][m] is the index of the mth feature occurring in position k of the jth clique
    int[][][] data = new int[docSize][windowSize][];
    // index is the position in the document
    // element in labels[j] is the index of the correct label (if it exists) at position j of document
    int[] labels = new int[docSize];

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    testIndex = new HashIndex<String>();
    testIndex.addAll(featureIndex.objectsList()); // mg2009: TODO: check
    for (int j = 0; j < docSize; j++) {
      CRFDatum<List<String>, CRFLabel> d = makeDatum(document, j, featureFactory);

      List<List<String>> features = d.asFeatures();
      for (int k = 0, fSize = features.size(); k < fSize; k++) {
        Collection<String> cliqueFeatures = features.get(k);
        data[j][k] = new int[cliqueFeatures.size()];
        Iterator<String> iter = cliqueFeatures.iterator();
        int m = 0;
        while (iter.hasNext()) {
          String feature = iter.next();
          int index = featureIndex.indexOf(feature);

          //Add all features extracted to the data representation
          //While testing ignore features that are not in the feature index
          //This helps extract new features from the self-training point of view
          //data[j][k][m++] = index;

           if (index >= 0) {
             data[j][k][m] = index;
             m++;
           } else if (testIndex.indexOf(feature) < 0) {
             testIndex.add(feature);
             // this is where we end up when we do feature threshhold cutoffs
           }
        }
        if (m < data[j][k].length) {
          int[] f = new int[m];
          System.arraycopy(data[j][k], 0, f, 0, m);
          data[j][k] = f;
        }
      }

      CoreLabel wi = document.get(j);
      labels[j] = classIndex.indexOf(wi.get(AnswerAnnotation.class));

    }

    System.err.println("TESTINDEX: " + testIndex.size());

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    // 	System.err.println("numClasses: "+classIndex.size()+" "+classIndex);
    // 	System.err.println("numDocuments: 1");
    // 	System.err.println("numDatums: "+data.length);
    // 	System.err.println("numFeatures: "+featureIndexsize());

    return new Triple<int[][][],int[], double[][][]>(data, labels, null);
  }


  protected void makeAnswerArraysAndTagIndex(Iterable<List<CoreLabel>> wordInfos) {

    HashSet<String>[] featureIndices = new HashSet[windowSize];
    for (int i = 0; i < windowSize; i++) {
      featureIndices[i] = new HashSet<String>();
    }

    boolean labelIndicesExists = false;
    if (labelIndices == null) {
      labelIndices = new ArrayList<Index<CRFLabel>>(windowSize);
      for (int i = 0; i < labelIndices.size(); i++) {
        labelIndices.add(new HashIndex<CRFLabel>());
      }
    } else {
      System.err.println("TEST: using old labelIndices");
      labelIndicesExists = true;
    }

    Index<CRFLabel> labelIndex = labelIndices.get(windowSize - 1);

    boolean classIndexExists = false;
    if (classIndex == null) {
      classIndex = new HashIndex<String>();
      //classIndex.add("O");
      classIndex.add(flags.backgroundSymbol);


      //creating the full set of labels in classIndex
      //note: update to use addAll later
      for (List<CoreLabel> doc : wordInfos) {

        for (int j = 0; j < doc.size(); j++) {
          String ans = (doc.get(j)).get(AnswerAnnotation.class);
          classIndex.add(ans);
        }
      }
    } else {
      System.err.println("Using old ClassIndex, size: " + classIndex.size());
      classIndexExists = true;
    }

    HashSet[] seenBackgroundFeatures = new HashSet[2];
    seenBackgroundFeatures[0] = new HashSet();
    seenBackgroundFeatures[1] = new HashSet();

    for (List<CoreLabel> doc : wordInfos) {

      if (flags.useReverse) {
        Collections.reverse(doc);
      }

      //creating the full set of labels in classIndex
      //note: update to use addAll later
      //       for (int j = 0; j < doc.size(); j++) {
      //         String ans = ((WordInfo) doc.get(j)).get(AnswerAnnotation.class);
      //         classIndex.add(ans);
      //       }


      for (int j = 0; j < doc.size(); j++) {

        CRFDatum<List<String>,CRFLabel> d = makeDatum(doc, j, featureFactory);
        labelIndex.add(d.label());

        List<List<String>> features = d.asFeatures();
        for (int k = 0; k < features.size(); k++) {
          Collection<String> cliqueFeatures = features.get(k);
          if (k < 2 && flags.removeBackgroundSingletonFeatures) {
            String ans = (doc.get(j)).get(AnswerAnnotation.class);
            boolean background = ans.equals(flags.backgroundSymbol);
            if (k == 1 && j > 0 && background) {
              ans = (doc.get(j - 1)).get(AnswerAnnotation.class);
              background = ans.equals(flags.backgroundSymbol);
            }
            if (background) {
              for (String f : cliqueFeatures) {
                if (!featureIndices[k].contains(f)) {
                  if (seenBackgroundFeatures[k].contains(f)) {
                    seenBackgroundFeatures[k].remove(f);
                    featureIndices[k].add(f);
                  } else {
                    seenBackgroundFeatures[k].add(f);
                  }
                }
              }
            } else {
              seenBackgroundFeatures[k].removeAll(cliqueFeatures);
              featureIndices[k].addAll(cliqueFeatures);
            }
          } else {
            featureIndices[k].addAll(cliqueFeatures);
          }
        }
      }

      if (flags.useReverse) {
        Collections.reverse(doc);
      }
    }

    //     String[] fs = new String[featureIndices[0].size()];
    //     for (Iterator iter = featureIndices[0].iterator(); iter.hasNext(); ) {
    //       System.err.println(iter.next());
    //     }

    int numFeatures = 0;
    for (int i = 0; i < windowSize; i++) {
      numFeatures += featureIndices[i].size();
    }

    //Index oldIndex = new Index();
    if (featureIndex == null) {
      featureIndex = new HashIndex<String>();
      map = new int[numFeatures];
      Arrays.fill(map, -1);
    } else {
      int oldFeatureSize = featureIndex.size();
      //Index countIndex = featureIndex;
      for (int i = 0; i < windowSize; i++) {
        for (String o: featureIndices[i]) {
          if (featureIndex.add(o)) {
            System.err.println("NEW " + o);
          }
          else {
            System.err.println("OLD " + o);
          }
        }

        //featureIndex.addAll(featureIndices[i]);
      }

      System.err.println("TEST: numFeatures b4  " + oldFeatureSize);

      //update featurecount
      numFeatures = featureIndex.size();
      //oldIndex = featureIndex;

      //resize map
      int[] oldMap = map;
      map = new int[numFeatures];
      System.arraycopy(oldMap, 0, map, 0, oldMap.length);
      for (int i = oldMap.length; i < numFeatures; i++) {
        map[i] = -1;
      }

      System.err.println("TEST: oldMap size     " + oldMap.length);
      System.err.println("TEST: oldIndex size   " + oldFeatureSize);
      System.err.println("TEST: newIndex size   " + featureIndex.size());
    }

    for (int i = 0; i < windowSize; i++) {
      featureIndex.addAll(featureIndices[i]);

      Iterator<String> fIter = featureIndices[i].iterator();
      while (fIter.hasNext()) {
        String feature = fIter.next();
        if (map[featureIndex.indexOf(feature)] == -1) {
          map[featureIndex.indexOf(feature)] = i;
        }
      }
    }

    if (!labelIndicesExists) {

      if (flags.useObservedSequencesOnly) {
        for (int i = 0; i < labelIndex.size(); i++) {
          CRFLabel label = labelIndex.get(i);
          for (int j = windowSize - 2; j >= 0; j--) {
            label = label.getOneSmallerLabel();
            labelIndices.get(j).add(label);
          }
        }
      } else {
        for (int i = 0; i < labelIndices.size(); i++) {
          labelIndices.set(i, allLabels(i + 1, classIndex));
        }
      }

    }
    //DEBUG CODE: Remove
    //     int domainDim = 0;
    //     for (int i = 0; i < map.length; i++) {
    //         //System.out.println("      " + labelIndices[map[i]]);
    //         domainDim += labelIndices[map[i]].size();
    //     }
    //     System.out.println("MakeAnswerArrays: Domain Dimension: " + domainDim);


    if (VERBOSE) {
      for (int i = 0; i < featureIndex.size(); i++) {
        System.out.println(i + ": " + featureIndex.get(i));
      }
    }
  }


  //TODO: Currently the feature set considered is for the entire list of docs
  //instead of the ones found in the confident datums (i.e. the ones that become
  //part of the annotated data. Fixing this requires a modified implementation of
  //makeAnswerArraysAndTagIndex which can work on datums instead of wordInfos.

  //TODO: Feature pruning is not implemented currently: Should we just run
  //dropFeaturesBelowThreshold at the end of each self-training iteration?
  /**
     This method takes in a list of documents to do self-training on
     and a parameter which specifies the number of iterations for doing self-training

     (Should we look to do this till convergence i.e. until at least one extra high
     confidence datum is extracted????)

     It tags the input docs (like test docs) and then uses confidence thresholds to
     choose high-confidence datums for self-training
  */
  public void selfTrainMaxEnt(ObjectBank<List<CoreLabel>> documents, int gramSize, double threshold, int numIterations) {

    List<List<CoreLabel>> docs = new ArrayList<List<CoreLabel>>();
    for (List<CoreLabel> doc : documents) {
      docs.add(doc);
    }

    //Repeat for desired number of iterations
    for (int nIter = 0; nIter < numIterations; nIter++) {

      System.err.println("SelfTraining: NumIteration: " + nIter);

      selfTrainDatums = new ArrayList<CRFDatum<? extends Collection<String>, ? extends CharSequence>>();

      for (int j = 0; j < docs.size(); j++) {

        List<CoreLabel> document = docs.get(j);

        //Code for computing highest confidence estimates for gramSize within the document
        Triple<int[][][],int[],double[][][]> p = documentToDataAndLabels(document);
        int[][][] data = p.first();

        CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
        CRFCliqueTree<String> cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(data, labelIndices, classIndex.size(), classIndex, flags.backgroundSymbol, cliquePotentialFunc, null);


        //Label the globally best sequence -- this is needed for extracting
        //labels of the words in context of the high-confidence estimates
        //Is there something better that can be done?
        TestSequenceModel testSequenceModel = new TestSequenceModel(cliqueTree);

        ExactBestSequenceFinder tagInference = new ExactBestSequenceFinder();
        int[] bestSequence = tagInference.bestSequence(testSequenceModel);

        if (flags.useReverse) {
          Collections.reverse(document);
        }
        for (int l = 0, docSize = document.size(); l < docSize; l++) {
          CoreLabel wi = document.get(l);
          String guess = classIndex.get(bestSequence[l + windowSize - 1]);
          wi.set(AnswerAnnotation.class, guess);
        }

        if (flags.useReverse) {
          Collections.reverse(document);
        }

        featureIndex = testIndex;

        p = documentToDataAndLabels(document);
        data = p.first();

        System.err.println("---Extracted Datums---\n");

        int[] labels = new int[gramSize];
        for (int docIndex = 0; docIndex < cliqueTree.length(); docIndex++) {

          System.arraycopy(bestSequence, docIndex, labels, 0, gramSize);
          double prob = cliqueTree.prob(docIndex, labels);

          boolean nonOther = false;
          for (int lIndex = 0; lIndex < gramSize; lIndex++){
            if (!classIndex.get(labels[lIndex]).equals(flags.backgroundSymbol)) {
              nonOther = true;
              break;
            }
          }

          //Check threshold
          if (prob >= threshold && nonOther) {
            int beginPos = Math.max(0, docIndex - gramSize + 1);

            //extractDatumSequence needs endPosition + 1
            List<CRFDatum<? extends Collection<String>, ? extends CharSequence>> datums =
                    extractDatumSequence(data, beginPos, docIndex, document);

            System.err.print(docIndex +  "  ");
            for (int ind = beginPos; ind <= docIndex; ind++)
              System.err.print((document.get(ind)).word() + "  " );
            for (CRFDatum datum:datums)
              System.err.print(datum.label() + " " );
            System.err.println(" prob " + prob);

            selfTrainDatums.add(datums);
          }

          //     selftraindatums.add(datums);
          //     trainMaxEnt(docs, selftraindatums);
          //     saveProcessedData(selftraindatums, "selftraindatums");
        }
        docs.set(j, document);
      }


      //DEBUG ONLY: Remove
      System.err.println("SelfTraining: NumIteration: " + nIter + " NumDatumsAdded: " + selfTrainDatums.size());

      //Now we have a list of list of datums that are 'high confidence' based on our threshold
      //Train using MAP re-estimation on these
      trainMaxEnt(docs, selfTrainDatums);

      saveProcessedData(selfTrainDatums, "selfTrainDatums" + nIter);

      File featIndexFile = null;
      if (flags.saveFeatureIndexToDisk) {
        try {
          System.err.println("SelfTraining: Writing feature index to temporary file.");
          featIndexFile = File.createTempFile("selfTrainFeatIndex" + nIter, ".tmp");
          featIndexFile.deleteOnExit();
          ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(featIndexFile))));
          oos.writeObject(featureIndex);
          oos.close();
          featureIndex = null;
        } catch (IOException e) {
          throw new RuntimeException("Could not open temporary feature index file for writing.");
        }
      }

      //dropFeaturesBelowThreshold
      // dropFeaturesBelowThreshold(flags.featureDiffThresh);
      // 	    System.err.println("Removing features with weight below " + flags.featureDiffThresh + " and retraining...");


    }//end of self-training iteration
  }


  private class ResultStoringMonitor implements Function {
    GeneralizedCRFLogConditionalObjectiveFunction func;
    int i = 0;

    public ResultStoringMonitor(GeneralizedCRFLogConditionalObjectiveFunction func) {
      this.func = func;
    }

    public double valueAt(double[] x) {
      if (++i % flags.interimOutputFreq == 0) {
        String name;
        if (flags.serializeTo.lastIndexOf(".") >= 0) {
          name = flags.serializeTo.substring(0, flags.serializeTo.lastIndexOf("."));
        } else {
          name = flags.serializeTo;
        }
        name += ".ddat";
        System.err.print("Storing interim (double) weights to " + name + " ... ");
        try {
          DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(name))));
          Convert.saveDoubleArr(dos, x);
        } catch (IOException e) {
          System.err.println("ERROR!");
        }
        System.err.println("DONE.");
      }
      return 0;
    }

    public int domainDimension() {
      return 0;
    }
  }


  public List<List<CoreLabel>> trainMaxEnt(List<List<CoreLabel>> docs, List datums) {
    //creates the union of features and indices
    makeAnswerArraysAndTagIndex(docs);

    //get the data and labels from datums
    int[][][][] data = new int[datums.size()][][][];
    int[][] labels = new int[datums.size()][];
    //if (featureIndex == null) System.out.println("MAPCRF: FeatureIndex is NULL!");
    addProcessedData(datums, data, labels, null, 0);

    //find the size of the mean and sigma vectors
    int arraysize = 0;
    for (int count = 0; count < map.length; count++) {
      arraysize += labelIndices.get(map[count]).size();
    }

    System.err.println("trainMaxEnt: new arraysize for mean: " + arraysize);

    //set the mean and sigma to default values
    double[] mean = new double[arraysize];
    double[] sigma = new double[arraysize];
    for (int count = 0; count < mean.length; count++) {
      mean[count] = 0;
      sigma[count] = 1.0;
    }

    //if we have weights from a previous training use those for means
    if (weights != null) {
      int wIndex = 0;
      for (int count = 0; count < weights.length; count++) {
        System.arraycopy(weights[count], 0, mean, wIndex, weights[count].length);
        wIndex += weights[count].length;
      }
    }

    //load our objective function
    GeneralizedCRFLogConditionalObjectiveFunction func = new GeneralizedCRFLogConditionalObjectiveFunction(data, labels, featureIndex, windowSize, classIndex, labelIndices, map, flags.backgroundSymbol, mean, sigma);
    func.crfType = flags.crfType;


    //load minimizer
    QNMinimizer minimizer;
    if (flags.interimOutputFreq != 0) {
      Function monitor = new ResultStoringMonitor(func);
      minimizer = new QNMinimizer(monitor);
    } else {
      minimizer = new QNMinimizer();
    }

    //set intitial weights
    double[] initialWeights;
    if (flags.initialWeights == null) {
      initialWeights = func.initial();
    } else {
      try {
        System.err.println("Reading initial weights from file " + flags.initialWeights);
        DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(flags.initialWeights))));
        initialWeights = Convert.readDoubleArr(dis);
      } catch (IOException e) {
        throw new RuntimeException("Could not read from double initial weight file " + flags.initialWeights);
      }
    }
    System.err.println("numWeights: " + initialWeights.length);
    double[] weights = minimizer.minimize(func, flags.tolerance, initialWeights);

    //     for (int wIndex = 0; wIndex < mean.length; wIndex++){
    //       if (mean[wIndex] != weights[wIndex]){
    //         System.err.println("-->REESIMATION<--");
    //         break;
    //       }
    //     }

    this.weights = func.to2D(weights);

    return docs;
  }


  public ObjectBank<List<CoreLabel>> trainMaxEnt(ObjectBank<List<CoreLabel>> docs) {

    //loads up the indices, map, etc...
    makeAnswerArraysAndTagIndex(docs);
    Random r = new Random();

    for (int i = 0; i <= flags.numTimesPruneFeatures; i++) {

      Triple<int[][][][],int[][], double[][][][]> dataAndLabels = documentsToDataAndLabels(docs);

      // save feature index to disk and read in later
      if (flags.saveFeatureIndexToDisk) {
        try {
          System.err.println("Writing feature index to temporary file.");
          File featIndexFile = File.createTempFile("featIndex" + i, ".tmp");
          featIndexFile.deleteOnExit();
          ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(featIndexFile))));
          oos.writeObject(featureIndex);
          oos.close();
        } catch (IOException e) {
          throw new RuntimeException("Could not open temporary feature index file for writing.");
        }
      }

      // first index is the number of the document
      // second index is position in the document also the index of the clique/factor table
      // third index is the number of elements in the clique/window thase features are for (starting with last element)
      // fourth index is position of the feature in the array that holds them
      // element in data[i][j][k][m] is the index of the mth feature occurring in position k of the jth clique of the ith document
      int[][][][] data = dataAndLabels.first();
      // first index is the number of the document
      // second index is the position in the document
      // element in labels[i][j] is the index of the correct label (if it exists) at position j in document i
      int[][] labels = dataAndLabels.second();

      // DEBUG only : Remove
      System.err.println("TrainMaxEnt: Loadprocessed: " + flags.loadProcessedData);

      if (flags.loadProcessedData != null) {
        List<List<CRFDatum<Collection<String>, String>>> processedData = loadProcessedData(flags.loadProcessedData);
        if (processedData != null) {
          // enlarge the data and labels array
          int[][][][] allData = new int[data.length + processedData.size()][][][];
          int[][] allLabels = new int[labels.length + processedData.size()][];
          System.arraycopy(data, 0, allData, 0, data.length);
          System.arraycopy(labels, 0, allLabels, 0, labels.length);
          // add to the data and labels array
          addProcessedData(processedData, allData, allLabels, null, data.length);
          data = allData;
          labels = allLabels;
        }
      }

      int arraysize = 0;

      for (int count = 0; count < map.length; count++) {
        arraysize += labelIndices.get(map[count]).size();
      }

      double[] mean = new double[arraysize];
      double[] sigma = new double[arraysize];
      for (int count = 0; count < mean.length; count++) {
        mean[count] = 0;
        sigma[count] = 1.0;
      }


      //Is this needed??
      if (weights != null) {
        int wIndex = 0;
        for (int count = 0; count < weights.length; count++) {
          System.arraycopy(weights[count], 0, mean, wIndex, weights[count].length);
          wIndex += weights[count].length;
        }
      }

      GeneralizedCRFLogConditionalObjectiveFunction func = new GeneralizedCRFLogConditionalObjectiveFunction(data, labels, featureIndex, windowSize, classIndex, labelIndices, map, flags.backgroundSymbol, mean, sigma);
      func.crfType = flags.crfType;

      QNMinimizer minimizer;
      if (flags.interimOutputFreq != 0) {
        Function monitor = new ResultStoringMonitor(func);
        minimizer = new QNMinimizer(monitor);
      } else {
        minimizer = new QNMinimizer();
      }

      if (i == 0) {
        minimizer.setM(flags.QNsize);
      } else {
        minimizer.setM(flags.QNsize2);
      }

      double[] initialWeights;
      if (flags.initialWeights == null) {
        initialWeights = func.initial();
      } else {
        try {
          System.err.println("Reading initial weights from file " + flags.initialWeights);
          DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(flags.initialWeights))));
          initialWeights = Convert.readDoubleArr(dis);
        } catch (IOException e) {
          throw new RuntimeException("Could not read from double initial weight file " + flags.initialWeights);
        }
      }
      System.err.println("numWeights: " + initialWeights.length);
      double[] weights = minimizer.minimize(func, flags.tolerance, initialWeights);
      this.weights = func.to2D(weights);

      // save feature index to disk and read in later
      //       if (flags.saveFeatureIndexToDisk) {
      //         try {
      // 	    System.err.println("Reading temporary feature index file.");
      // 	    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(featIndexFile))));
      // 	    featureIndex = (Index)ois.readObject();
      // 	    ois.close();
      //         } catch (Exception e) {
      // 	    throw new RuntimeException("Could not open temporary feature index file for reading.");
      //         }
      //       }

      if (i != flags.numTimesPruneFeatures) {
        dropFeaturesBelowThreshold(flags.featureDiffThresh);
        System.err.println("Removing features with weight below " + flags.featureDiffThresh + " and retraining...");
      }
    }
    return docs;
  }




  public static MAPCRFClassifier getClassifierNoExceptions(File file) {

    MAPCRFClassifier crf = new MAPCRFClassifier();
    crf.loadClassifierNoExceptions(file);
    return crf;

  }

  public static MAPCRFClassifier getClassifier(File file) throws IOException, ClassCastException, ClassNotFoundException {

    MAPCRFClassifier crf = new MAPCRFClassifier();
    crf.loadClassifier(file);
    return crf;

  }

  public static MAPCRFClassifier getClassifierNoExceptions(String loadPath) {

    MAPCRFClassifier crf = new MAPCRFClassifier();
    crf.loadClassifierNoExceptions(loadPath);
    return crf;

  }

  public static MAPCRFClassifier getClassifier(String loadPath) throws IOException, ClassCastException, ClassNotFoundException {

    MAPCRFClassifier crf = new MAPCRFClassifier();
    crf.loadClassifier(loadPath);
    return crf;

  }

  public static MAPCRFClassifier getClassifierNoExceptions(InputStream in) {
    MAPCRFClassifier crf = new MAPCRFClassifier();
    crf.loadClassifierNoExceptions(new BufferedInputStream(in), null);
    return crf;
  }

  public static MAPCRFClassifier getClassifier(InputStream in) throws IOException, ClassCastException, ClassNotFoundException {
    MAPCRFClassifier crf = new MAPCRFClassifier();
    crf.loadClassifier(new BufferedInputStream(in));
    return crf;
  }

  public static void main(String[] args) throws Exception {

    Properties props = StringUtils.argsToProperties(args);
    MAPCRFClassifier crf = new MAPCRFClassifier(props);
    //crf.setProperties(StringUtils.argsToProperties(args));
    String trainFile = crf.flags.trainFile;
    String testFile = crf.flags.testFile;
    String textFile = crf.flags.textFile;
    String loadPath = crf.flags.loadClassifier;
    String serializeTo = crf.flags.serializeTo;

    String selfTrainFile = crf.flags.selfTrainFile;

    if (trainFile != null) {
      crf.train(trainFile, crf.makeReaderAndWriter());
    } else if (loadPath != null) {
      crf.loadClassifierNoExceptions(loadPath);
      crf.flags.setProperties(props);
    }

    if (serializeTo != null) {
      crf.serializeClassifier(serializeTo);
    }

    if ((crf.flags.crfType.equalsIgnoreCase("maxent")) &&
        selfTrainFile != null) {
      ObjectBank<List<CoreLabel>> docs =
        crf.makeObjectBankFromFile(selfTrainFile, crf.makeReaderAndWriter());
      crf.selfTrainMaxEnt(docs, crf.flags.selfTrainWindowSize,
                          crf.flags.selfTrainConfidenceThreshold,
                          crf.flags.selfTrainIterations);
    }


    if (testFile != null) {
      DocumentReaderAndWriter<CoreLabel> readerAndWriter = crf.makeReaderAndWriter();
      if (crf.flags.printFirstOrderProbs) {
        crf.printFirstOrderProbs(testFile, readerAndWriter);
      } else if (crf.flags.printProbs) {
        crf.printProbs(testFile, readerAndWriter);
      } else {
        crf.classifyAndWriteAnswers(testFile, readerAndWriter);
      }
    }

    if (textFile != null) {
      DocumentReaderAndWriter<CoreLabel> readerAndWriter =
        new PlainTextDocumentReaderAndWriter<CoreLabel>();
      crf.classifyAndWriteAnswers(textFile, readerAndWriter);
    }
  }

}
