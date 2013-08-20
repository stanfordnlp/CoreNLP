package edu.stanford.nlp.classify;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.SystemUtils;

public class SVMLightRegression<F> implements Regressor<F>{

  private static final long serialVersionUID = -8654429697969292128L;
  private File modelFile;
  private Index<F> featureIndex;
  private String classifyCommand;

  /**
   * @param classifyCommand The SVM-light classify command, e.g. svm_classify
   */
  public SVMLightRegression(File modelFile, Index<F> featureIndex, String classifyCommand) {
    this.modelFile = modelFile;
    this.featureIndex = featureIndex;
    this.classifyCommand = classifyCommand;
  }

  /**
   * Create an SVM-light regression with the default classify command, svm_classify.
   */
  public SVMLightRegression(File modelFile, Index<F> featureIndex) {
    this(modelFile, featureIndex, "svm_classify");
  }

  /**
   * Predict the value of this datum.
   *
   * WARNING: This method writes files and calls out to svm_classify. As such,
   * this is extremely inefficient to do many times. If you have many data
   * points, you should instead use {@link #valuesOf(GeneralDataset)}.
   *
   * @param datum The item whose value is to be predicted.
   * @return      The predicted value.
   */
  public double valueOf(Datum<Double, F> datum) {
    RVFDataset<Double, F> data;
    data = new RVFDataset<Double, F>(this.featureIndex, new HashIndex<Double>());
    data.add(datum);
    return this.valuesOf(data).get(0);
  }

  /**
   * Predict values for each of the items in the dataset.
   * Faster than valueOf(datum) because there is a single file write/read for all the datums unlike valueOf(datum).
   *
   * @param data The items whose values are to be predicted.
   * @return     One predicted value for each item.
   */
  public List<Double> valuesOf(GeneralDataset<Double, F> data) {
    // we create a new dataset so that the featureIndex of the test dataset matches with that of the training set
  	// before we write out the dataset in SVMLight format.
    if (data.featureIndex() != this.featureIndex) {
      this.featureIndex.lock();
      RVFDataset<Double, F> newData;

      newData = new RVFDataset<Double, F>(this.featureIndex, data.labelIndex());
      for (int index = 0; index < data.size; ++index) {
        newData.add(data.getRVFDatum(index));
      }
      data = newData;
      this.featureIndex.unlock();
    }

    // write the data in SVM-format to the testing file
    File testFile;
    File outputFile;
    try {
      testFile = File.createTempFile("svm-light-classify", ".input");
      outputFile = File.createTempFile("svm-light-classify", ".output");
      writeSVMLightFormat(data, testFile);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    // run the SVM-light classify command
    try {
      SystemUtils.run(new ProcessBuilder(
          this.classifyCommand,
          "-v", "0",
          testFile.getPath(),
          this.modelFile.getPath(),
          outputFile.getPath()));

      // parse the predictions out of the output file
      List<Double> values = new ArrayList<Double>();
      for (String line: IOUtils.readLines(outputFile)) {
        values.add(Double.parseDouble(line));
      }
      return values;
    }

    // clean up temp files created for input and output
    finally {
      testFile.delete();
      outputFile.delete();
    }
  }

  /**
   * Write a regression dataset in SVM-light format.
   *
   * A strict SVM-light format will be written, where features are encoded as
   * integers, using the feature index of the dataset.
   *
   * @param data The dataset.
   * @param file The location where the dataset should be written.
   */
  public static <F> void writeSVMLightFormat(GeneralDataset<Double, F> data, File file)
  throws IOException, FileNotFoundException {
    PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
    try {
      Index<F> featureIndex = data.featureIndex();
      for (int i = 0; i < data.size(); ++i) {
        RVFDatum<Double, F> datum = data.getRVFDatum(i);
        Double label = datum.label();
        writer.print(label == null ? 0.0 : label);
        Counter<F> features = datum.asFeaturesCounter();

        ////// inserted the following code because SVMLight expects sorted featureIDs.

        Counter<Integer> printCounter = new ClassicCounter<Integer>();
        for (F f : features.keySet()) {
          printCounter.setCount(featureIndex.indexOf(f), features.getCount(f));
        }
        Integer[] fIDs = printCounter.keySet().toArray(new Integer[printCounter.keySet().size()]);
        Arrays.sort(fIDs);
        for (int fID: fIDs) {
          double count = printCounter.getCount(fID);
          int index = fID + 1; // add one cuz SVMLight doesn't allow ids to start from 0.
          writer.format(" %s:%f", index, count);
        }
        ///// end of inserted code. -Ramesh

        writer.println();
      }
    } finally {
      writer.close();
    }
  }



}
