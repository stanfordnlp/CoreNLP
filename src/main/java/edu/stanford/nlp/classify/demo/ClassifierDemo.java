package edu.stanford.nlp.classify.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.ColumnDataClassifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Pair;

public class ClassifierDemo {

  private static String where = "";

  private ClassifierDemo() {} // Static methods

  public static void main(String[] args) throws Exception {
    if (args.length > 0) {
      where = args[0] + File.separator;
    }

    System.out.println("Training ColumnDataClassifier");
    ColumnDataClassifier cdc = new ColumnDataClassifier(where + "examples/cheese2007.prop");
    cdc.trainClassifier(where + "examples/cheeseDisease.train");

    System.out.println();
    System.out.println("Testing predictions of ColumnDataClassifier");
    for (String line : ObjectBank.getLineIterator(where + "examples/cheeseDisease.test", "utf-8")) {
      // instead of the method in the line below, if you have the individual elements
      // already you can use cdc.makeDatumFromStrings(String[])
      Datum<String,String> d = cdc.makeDatumFromLine(line);
      System.out.printf("%s  ==>  %s (%.4f)%n", line, cdc.classOf(d), cdc.scoresOf(d).getCount(cdc.classOf(d)));
    }

    System.out.println();
    System.out.println("Testing accuracy of ColumnDataClassifier");
    Pair<Double, Double> performance = cdc.testClassifier(where + "examples/cheeseDisease.test");
    System.out.printf("Accuracy: %.3f; macro-F1: %.3f%n", performance.first(), performance.second());

    demonstrateSerialization();
    demonstrateSerializationColumnDataClassifier();
  }


  private static void demonstrateSerialization()
    throws IOException, ClassNotFoundException {
    System.out.println();
    System.out.println("Demonstrating working with a serialized classifier");
    ColumnDataClassifier cdc = new ColumnDataClassifier(where + "examples/cheese2007.prop");
    Classifier<String,String> cl =
            cdc.makeClassifier(cdc.readTrainingExamples(where + "examples/cheeseDisease.train"));

    // Exhibit serialization and deserialization working. Serialized to bytes in memory for simplicity
    System.out.println();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(cl);
    oos.close();

    byte[] object = baos.toByteArray();
    ByteArrayInputStream bais = new ByteArrayInputStream(object);
    ObjectInputStream ois = new ObjectInputStream(bais);
    LinearClassifier<String,String> lc = ErasureUtils.uncheckedCast(ois.readObject());
    ois.close();
    ColumnDataClassifier cdc2 = new ColumnDataClassifier(where + "examples/cheese2007.prop");

    // We compare the output of the deserialized classifier lc versus the original one cl
    // For both we use a ColumnDataClassifier to convert text lines to examples
    System.out.println();
    System.out.println("Making predictions with both classifiers");
    for (String line : ObjectBank.getLineIterator(where + "examples/cheeseDisease.test", "utf-8")) {
      Datum<String,String> d = cdc.makeDatumFromLine(line);
      Datum<String,String> d2 = cdc2.makeDatumFromLine(line);
      System.out.printf("%s  =origi=>  %s (%.4f)%n", line, cl.classOf(d), cl.scoresOf(d).getCount(cl.classOf(d)));
      System.out.printf("%s  =deser=>  %s (%.4f)%n", line, lc.classOf(d2), lc.scoresOf(d).getCount(lc.classOf(d)));
    }
  }

  private static void demonstrateSerializationColumnDataClassifier()
    throws IOException, ClassNotFoundException {
    System.out.println();
    System.out.println("Demonstrating working with a serialized classifier using serializeTo");
    ColumnDataClassifier cdc = new ColumnDataClassifier(where + "examples/cheese2007.prop");
    cdc.trainClassifier(where + "examples/cheeseDisease.train");

    // Exhibit serialization and deserialization working. Serialized to bytes in memory for simplicity
    System.out.println();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    cdc.serializeClassifier(oos);
    oos.close();

    byte[] object = baos.toByteArray();
    ByteArrayInputStream bais = new ByteArrayInputStream(object);
    ObjectInputStream ois = new ObjectInputStream(bais);
    ColumnDataClassifier cdc2 = ColumnDataClassifier.getClassifier(ois);
    ois.close();

    // We compare the output of the deserialized classifier cdc2 versus the original one cl
    // For both we use a ColumnDataClassifier to convert text lines to examples
    System.out.println("Making predictions with both classifiers");
    for (String line : ObjectBank.getLineIterator(where + "examples/cheeseDisease.test", "utf-8")) {
      Datum<String,String> d = cdc.makeDatumFromLine(line);
      Datum<String,String> d2 = cdc2.makeDatumFromLine(line);
      System.out.printf("%s  =origi=>  %s (%.4f)%n", line, cdc.classOf(d), cdc.scoresOf(d).getCount(cdc.classOf(d)));
      System.out.printf("%s  =deser=>  %s (%.4f)%n", line, cdc2.classOf(d2), cdc2.scoresOf(d).getCount(cdc2.classOf(d)));
    }
  }

}
