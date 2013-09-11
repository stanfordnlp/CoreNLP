package edu.stanford.nlp.sequences;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RegExFileFilter;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerObjectAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.objectbank.ResettableReaderIteratorFactory;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Sampleable;
import edu.stanford.nlp.stats.Sampler;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;

public class SequenceClassifier implements Sampleable<List<CoreLabel>, List<CoreLabel>> {

  protected SeqClassifierFlags flags = null;
  private Set<String> knownLCWords = new HashSet<String>();
  protected DocumentReaderAndWriter<CoreLabel> readerAndWriter = null;
  protected QueriableSequenceModelFactory modelFactory = null;
  private transient Type2FeatureFactory featureFactory = null;

  public QueriableSequenceModelFactory getModelFactory() { return modelFactory; }

  /**
   * Does initialization based on the specified properties.
   */
  public void setFlags(SeqClassifierFlags flags) {

    flags.pad.set(AnswerAnnotation.class, flags.backgroundSymbol);
    this.flags = flags;

    try {
      readerAndWriter = (DocumentReaderAndWriter)(Class.forName(flags.readerAndWriter).newInstance());
      readerAndWriter.init(flags);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }


  /**
   * Read in a list of documents.  Read from <code>in</code> until
   * end-of-file is reached, using the reading method specified in
   * <code>flags.readerAndWriter</code>, and for some reader choices,
   * the column mapping given in <code>flags.map</code>.
   *
   * @param rif      Input data
   * @param quietly Print less message if this is true (use when calling
   *                it repeatedly on small bits of text)
   * @return The list of documents
   */
  private ObjectBank<List<CoreLabel>> getObjectBank(ReaderIteratorFactory rif, boolean quietly) {
    System.err.println(readerAndWriter.getClass());
    ObjectBank<List<CoreLabel>> ob = new ObjectBankWrapper<CoreLabel>(flags, new ObjectBank<List<CoreLabel>>(rif, readerAndWriter), knownLCWords);
    if (flags.keepOBInMemory) {
      ob.keepInMemory(true);
    }

    return ob;
  }


  /**
   * Read in a list of documents.  Read from the file <code>filename</code>
   * until end-of-file is reached, using the reading method specified in
   * <code>flags.readerAndWriter</code>, and for some reader choices,
   * the column mapping given in <code>flags.map</code>
   *
   * @param file      Input data
   * @return The list of documents
   */
  protected ObjectBank<List<CoreLabel>> getObjectBank(File file) {
    try {
      ObjectBank<List<CoreLabel>> ob = getObjectBank(new ResettableReaderIteratorFactory(file, flags.inputEncoding));

      if (flags.combo) {
        ((ComboFeatureFactory)featureFactory).dealWithData(ob, file);
      }

      return ob;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  private ObjectBank<List<CoreLabel>> getObjectBank(boolean train) {
    if (train) {
      if (flags.trainFiles == null) {
        return getObjectBank(new File(flags.trainFile));
      }
    } else {
      if (flags.testFiles == null) {
        return getObjectBank(new File(flags.testFile));
      }
    }

    try {
      File path = new File((train ? flags.baseTrainDir : flags.baseTestDir));
      FileFilter filter = new RegExFileFilter(Pattern.compile((train ? flags.trainFiles : flags.testFiles)));
      File[] origFiles = path.listFiles(filter);
      Collection<File> files = new ArrayList<File>();
      for (File file : origFiles) {
        if (file.isFile()) {
          files.add(file);
        }
      }
      System.err.println(files.size()+" files.");
      return getObjectBank(new ResettableReaderIteratorFactory(files, flags.inputEncoding));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Read in a list of documents.  Read from <code>in</code> until
   * end-of-file is reached, using the reading method specified in
   * <code>flags.readerAndWriter</code>, and for some reader choices,
   * the column mapping given in <code>flags.map</code>
   *
   * @param rif      Input data
   * @return The list of documents
   */
  protected ObjectBank<List<CoreLabel>> getObjectBank(ReaderIteratorFactory rif) {
    return getObjectBank(rif, false);
  }

  private Type2FeatureFactory createFeatureFactory() {
    Type2FeatureFactory featureFactory = null;

    try {
      Object ff = (Class.forName(flags.featureFactory).newInstance());
      if (ff instanceof FeatureFactory) {
        FeatureFactory<CoreLabel> ffcl = (FeatureFactory<CoreLabel>) ff;
        ffcl.init(flags);
        if (flags.iobWrapper) {
          featureFactory = new IOBFeatureFactoryWrapper(ffcl);
        } else {
          featureFactory = new FeatureFactoryWrapper<CoreLabel>(ffcl);
        }
      } else {
        featureFactory = (Type2FeatureFactory)ff;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    featureFactory.init(flags);

    return featureFactory;
  }

  private QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider getSequenceTypeDataProvider() {
    QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider dp;
    if (flags.type.equalsIgnoreCase("cmm")) {
      dp = new CMMFactory();
    } else if (flags.type.equalsIgnoreCase("crf")) {
      dp = new CRFFactory();
    }  else {
      throw new RuntimeException("Unknown type: "+flags.type);
    }
    return dp;
  }


  /**
   * Train the modelFactory using the given docs.
   */
  public void train(String serializedDocsDir) {
    QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider dp = getSequenceTypeDataProvider();
    //fix this
  }

  public void train(ObjectBank<List<CoreLabel>> docs) {
    QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider dp = getSequenceTypeDataProvider();
    modelFactory = new QueriableSequenceModelFactory(dp, flags, featureFactory, docs);
  }

  /**
   * Train the modelFactory using the given docs, but only with the intersection of features from trainDocs and testDocs
   */
  public void train(ObjectBank<List<CoreLabel>> trainDocs, ObjectBank<List<CoreLabel>> testDocs) {
    QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider dp = getSequenceTypeDataProvider();
    modelFactory = new QueriableSequenceModelFactory(dp, flags, featureFactory, trainDocs, testDocs);
  }

  public void trainHierarchical(boolean baseline, boolean doFE) {
    if (baseline && doFE) {
      throw new RuntimeException("can only do one of baseline and doFE.");
    }
    Map<String,ObjectBank<List<CoreLabel>>> trainingData = new HashMap<String,ObjectBank<List<CoreLabel>>>();
    for (String domainAndLocation : flags.trainHierarchical.split(",")) {
      String[] bits = domainAndLocation.split("=");
      String domain = bits[0];
      String location = bits[1];

      ObjectBank<List<CoreLabel>> data = getObjectBank(new File(location));
      trainingData.put(domain, data);
    }

    QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider dp = getSequenceTypeDataProvider();
    modelFactory = new QueriableSequenceModelFactory(dp, flags, featureFactory, trainingData, baseline, doFE);
  }


  public void adapt(ObjectBank<List<CoreLabel>> docs) {
    if (modelFactory == null) {
      throw new RuntimeException("Please train before you adapt");
    }
    modelFactory.adapt(docs);
  }

  public QueriableSequenceModel getSequenceModel(List<CoreLabel> doc) {
    return modelFactory.getModel(doc);
  }

    public void testSentence(List<CoreLabel> document) {
      testSentence(document, AnswerAnnotation.class);
    }

    public void testSentence(List<CoreLabel> document, Class<? extends CoreAnnotation<String>> answerField) {
      ObjectBankWrapper<CoreLabel> wrapper = new ObjectBankWrapper<CoreLabel>(flags, null, knownLCWords);
      wrapper.processDocument(document);
      test(document, false, answerField);
    }

  private void test(ObjectBank<List<CoreLabel>> docs) {
    for (List<CoreLabel> doc : docs) {
      test(doc);
    }
  }

  private void test(List<CoreLabel> doc) {
    test(doc, true, AnswerAnnotation.class);
  }

  private void test(List<CoreLabel> doc, boolean print, Class<? extends CoreAnnotation<String>> answerField) {
    PrintWriter out = print? new PrintWriter(System.out):null;
    test(doc, answerField, out);
  }

  protected void test(List<CoreLabel> doc, Class<? extends CoreAnnotation<String>> answerField, PrintWriter out) {
    if (flags.domain != null) {
      modelFactory.test(doc, flags.domain, answerField);
    } else {
      modelFactory.test(doc, answerField);
    }

    if (flags.mergeTags) {
      unmergeTags(doc);
    }

    if (out != null) {
      readerAndWriter.printAnswers(doc, out);
      out.flush();
    }

  }
  public Counter<List<CoreLabel>> testKBest(List<CoreLabel> doc, boolean print, Class<? extends CoreAnnotation<String>> answerField, int k) {
    Counter<List<CoreLabel>> kBest =  modelFactory.testKBest(doc, answerField, k);

    if (print) {
      for (List<CoreLabel> d : kBest.keySet()) {
        System.out.println(kBest.getCount(d));
        PrintWriter out = new PrintWriter(System.out);
        readerAndWriter.printAnswers(d, out);
        out.flush();
      }
      System.out.println();
    }

    return kBest;

  }

  public Counter<Pair<List<CoreLabel>,Counter<Integer>>> testKBestWithFeatures(List<CoreLabel> doc, boolean print, Class<? extends CoreAnnotation<String>> answerField, int k) {
    return modelFactory.testKBestWithFeatures(doc, answerField, k);
  }

  public void unmergeTags(List<CoreLabel> doc) {
    String lastTag = "";
    for (CoreLabel wi : doc) {
      String answer = wi.get(AnswerAnnotation.class);
      if (!answer.equals(flags.backgroundSymbol) && answer.indexOf('-') < 0) {
        if (!lastTag.equals(answer)) {
          answer = "B-" + answer;
        } else {
          answer = "I-" + answer;
        }
        lastTag = answer.substring(2);
      } else {
        lastTag = answer;
      }
      wi.set(AnswerAnnotation.class, answer);
    }
  }

  public void serializeClassifier(String serializePath) {
    System.err.print("Serializing classifier to " + serializePath + "...");

    try {
      ObjectOutputStream oos = IOUtils.writeStreamFromString(serializePath);
      writeObject(oos);

      oos.close();
      System.err.println("done.");

    } catch (Exception e) {
      throw new RuntimeException("Serialization failed", e);
    }

  }

  public void writeClassifier(ObjectOutputStream oos) throws IOException {
    writeObject(oos);
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.writeObject(flags);
    oos.writeObject(knownLCWords);
    oos.writeObject(readerAndWriter);
    oos.writeObject(modelFactory);
  }

  public void loadClassifier(String loadPath) throws IOException, ClassNotFoundException {
    Timing.startDoing("Loading classifier from " + loadPath);

    ObjectInputStream ois;
    if (loadPath.endsWith("gz")) {
      ois = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(loadPath))));
    } else {
      ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(loadPath)));
    }
    readObject(ois);
    ois.close();

    Timing.endDoing();
  }

  public static SequenceClassifier readClassifier(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    SequenceClassifier sc = new SequenceClassifier();
    sc.readObject(ois);
    return sc;
  }

  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    flags = (SeqClassifierFlags) ois.readObject();
    knownLCWords = (Set<String>) ois.readObject();
    readerAndWriter = (DocumentReaderAndWriter<CoreLabel>) ois.readObject();
    modelFactory = (QueriableSequenceModelFactory) ois.readObject();
    featureFactory = modelFactory.metaInfo().featureFactory();
  }

  public static SequenceClassifier getClassifier(String[] args) throws IOException, ClassNotFoundException {
    SequenceClassifier seq = new SequenceClassifier();
    Properties props = StringUtils.argsToProperties(args);

    // NOTE: if we are not serializing a classifier, we just want
    // to set the flags based on the props.  however, if we are
    // deserializing a classifier, we want to deserialize the
    // old flags and then just update that flags based
    // on the current props.  but it is a little confusing
    // because we need the current props to see if we
    // are deserializing or not.  so we make a flags object,
    // but dont get set it to be the flags for the classifier.
    // we then check if we are deserializing, and act accordingly. -JRF
    SeqClassifierFlags tmpFlags = new SeqClassifierFlags(props);
    String loadPath = tmpFlags.loadClassifier;


    /**
    String trainFile = flags.trainFile;
    String serializeTo = flags.serializeTo;

    String testFile = flags.testFile;
    String baseTestDir = flags.baseTestDir;
    String testFiles = flags.testFiles;
    String adaptFile = flags.adaptFile;
    boolean useSeenFeaturesOnly = flags.useSeenFeaturesOnly;
    */
    if (loadPath == null) {
      seq.setFlags(tmpFlags);
      if (seq.flags.loadDatasetsDir != null &&
          seq.flags.trainFiles == null &&
          seq.flags.trainFile == null) {
        seq.train(seq.flags.loadDatasetsDir);
      } else if (seq.flags.trainHierarchical != null) {
        seq.featureFactory = seq.createFeatureFactory();
        seq.trainHierarchical(seq.flags.baseline, seq.flags.doFE);
      } else {
        seq.featureFactory = seq.createFeatureFactory();
        ObjectBank<List<CoreLabel>> data = seq.getObjectBank(true);
        if (seq.flags.useSeenFeaturesOnly) {
          System.err.println("(DEBUG) flags.useSeenFeaturesOnly is true");
          ObjectBank<List<CoreLabel>> testData = seq.getObjectBank(false);
          seq.train(data, testData);
        } else {
          if (seq.flags.evaluateIters > 0) {
            ObjectBank<List<CoreLabel>> testData = seq.getObjectBank(false);
            seq.train(data, testData);
          } else {
            seq.train(data);
          }
        }
      }
      if (seq.flags.serializeTo != null) {
        seq.serializeClassifier(seq.flags.serializeTo);
      }
    } else {
      seq.loadClassifier(loadPath);
      System.err.printf("\nUpdating the props from %s\n", loadPath);
      seq.flags.setProperties(props);
    }

    if (seq.flags.adaptFile != null) {
      System.err.println("(DEBUG) MAP adaptation using adaptFile="+seq.flags.adaptFile);
      ObjectBank<List<CoreLabel>> adapt = seq.getObjectBank(new File(seq.flags.adaptFile));
      seq.adapt(adapt);
    }

    return seq;
  }


  public Sampler<List<CoreLabel>> getSampler(final List<CoreLabel> input) {
    return new Sampler<List<CoreLabel>>() {
      QueriableSequenceModel model = modelFactory.getModel(input);
      SequenceSampler sampler = new SequenceSampler();
      public List<CoreLabel> drawSample() {
        int[] sampleArray = sampler.bestSequence(model);
        List<CoreLabel> sample = new ArrayList<CoreLabel>();
        int i=0;
        for (CoreLabel word : input) {
          CoreLabel newWord = new CoreLabel(word);
          newWord.set(AnswerObjectAnnotation.class, modelFactory.metaInfo().getLabel(sampleArray[i++]));
          sample.add(newWord);
        }
        return sample;
      }
    };
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if(flags != null && modelFactory != null) {
      sb.append("*** SequenceClassifier Labels and Feature Weights ***\n");
      sb.append(modelFactory.metaInfo().toString());
      sb.append("Feature weights:\n");
      sb.append(modelFactory.getWeightsString());
    } else {
      sb.append("ERROR: Classifier not initialized\n");
    }

    return sb.toString();
  }

  public static void main(String[] args) throws Exception {
    System.err.println(StringUtils.toInvocationString("SequenceClassifier", args));

    SequenceClassifier seq = getClassifier(args);

    if (seq.flags.verboseMode) {
      System.err.println(seq.toString());
    }

    if (seq.flags.testFile != null || seq.flags.testFiles != null) {
      ObjectBank<List<CoreLabel>> data = seq.getObjectBank(false);

      if (seq.flags.useKBest) {
        for (List<CoreLabel> datum : data) {
          seq.testKBest(datum, true, CoreAnnotations.AnswerAnnotation.class, seq.flags.kBest);
        }
      } else {
        seq.test(data);
      }

     // seq.test(data);
    } else if (seq.flags.textFile != null) {
      // todo: This isn't threadsafe. Needs to be changed as ie.crf code has been
      DocumentReaderAndWriter<CoreLabel> oldRW = seq.readerAndWriter;
      seq.readerAndWriter = new PlainTextDocumentReaderAndWriter<CoreLabel>();
      seq.readerAndWriter.init(seq.flags);
      ObjectBank<List<CoreLabel>> data = seq.getObjectBank(new File(seq.flags.textFile));

      if (seq.flags.useKBest) {
        for (List<CoreLabel> datum : data) {
          seq.testKBest(datum, true, CoreAnnotations.AnswerAnnotation.class, seq.flags.kBest);
        }
      } else {
        seq.test(data);
      }
      seq.readerAndWriter = oldRW;
    }

  }
}
