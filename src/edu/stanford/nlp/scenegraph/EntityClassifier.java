package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.neural.Embedding;
import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Triple;

public class EntityClassifier {


  private static double REG_STRENGTH = 1.0;


  private Classifier<String, String> classifier;


  public EntityClassifier (String modelPath) {
    try {
      this.classifier = IOUtils.readObjectFromURLOrClasspathOrFileSystem(modelPath);
    } catch (ClassNotFoundException | IOException e) {
      e.printStackTrace();
    }
  }

  public void predictEntity(IndexedWord word, Embedding embeddings) {
    String entity = classifier.classOf(getDatum(word, null, embeddings));
    word.set(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class, entity);
  }

  private static RVFDatum<String, String> getDatum(IndexedWord word, String entity, Embedding embeddings) {
    Counter<String> features = new ClassicCounter<String>();

    features.incrementCount(String.format("w:%s", word.word()));
    features.incrementCount(String.format("l:%s", word.lemma()));

//    String[] wordParts = word.lemma().split("\\s+");
//    String[] lemmaParts = word.lemma().split("\\s+");
//    String headWord;
//    String headLemma;
//    if (word.tag().startsWith("V")) {
//      headWord = wordParts[0];
//      headLemma = lemmaParts[0];
//    } else {
//      headWord = wordParts[wordParts.length - 1];
//      headLemma = lemmaParts[lemmaParts.length - 1];
//    }
//
//    features.incrementCount(String.format("hl:%s", headLemma));
//    features.incrementCount(String.format("hw:%s", headWord));


    String compoundWord = word.get(SceneGraphCoreAnnotations.CompoundWordAnnotation.class);
    compoundWord = compoundWord == null ? word.word() : compoundWord;
    String compoundLemma = word.get(SceneGraphCoreAnnotations.CompoundLemmaAnnotation.class);
    compoundLemma = compoundLemma == null ? word.word() : compoundLemma;
    features.incrementCount(String.format("cw:%s", compoundWord));
    features.incrementCount(String.format("cl:%s", compoundLemma));

    SimpleMatrix vector = embeddings.get(word.word());
    if (vector != null) {
      for (int i = 0; i < vector.numRows(); i++) {
        features.setCount(String.format("e%d",i), vector.get(i, 0));
      }
    }

    RVFDatum<String, String> datum;
    if (entity != null) {
      datum = new RVFDatum<String, String>(features, entity);
    } else {
      datum = new RVFDatum<String, String>(features);
    }

    return datum;

  }


  private static void train(List<SceneGraphImage> images, String modelPath, Embedding embeddings) throws IOException {

    RVFDataset<String, String> dataset = new RVFDataset<String, String>();
    SceneGraphSentenceMatcher sentenceMatcher = new SceneGraphSentenceMatcher(embeddings);

    for (SceneGraphImage img : images) {
      for (SceneGraphImageRegion region : img.regions) {
        SemanticGraph sg = region.getEnhancedSemanticGraph();
        SemanticGraphEnhancer.enhance(sg);
        List<Triple<IndexedWord, IndexedWord, String>> relationTriples = sentenceMatcher.getRelationTriples(region);
        for (Triple<IndexedWord, IndexedWord, String> relation : relationTriples) {
          IndexedWord w1 = sg.getNodeByIndexSafe(relation.first.index());
          if (w1 != null) {
            dataset.add(getDatum(w1, relation.first.get(SceneGraphCoreAnnotations.GoldEntityAnnotation.class), embeddings));
          }
        }
      }
    }

    LinearClassifierFactory<String, String> classifierFactory = new LinearClassifierFactory<String, String>(new QNMinimizer(15), 1e-4, false, REG_STRENGTH);

    Classifier<String, String> classifier = classifierFactory.trainClassifier(dataset);

    IOUtils.writeObjectToFile(classifier, modelPath);


    System.err.println(classifier.evaluateAccuracy(dataset));

  }

  public static void main(String[] args) throws IOException {
    String filename = args[0];
    String modelPath = args[1];
    String embeddingsPath = args[2];
    Embedding embeddings = new Embedding(args[2]);

    BufferedReader reader = IOUtils.readerFromString(filename);


    List<SceneGraphImage> images = Generics.newLinkedList();

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      images.add(img);
    }

    train(images, modelPath, embeddings);


  }


}
