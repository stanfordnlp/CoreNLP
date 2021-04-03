package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.classify.KNNClassifier;
import edu.stanford.nlp.classify.KNNClassifierFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Triple;

public class KNNSceneGraphParser extends AbstractSceneGraphParser {

  KNNClassifier<String, String> classifier;


  public KNNSceneGraphParser(String modelPath) {
    if (modelPath != null) {
      try {
        this.classifier = IOUtils.readObjectFromFile(modelPath);
      } catch (ClassNotFoundException | IOException e) {
        e.printStackTrace();
      }
    }
  }


  @Override
  public SceneGraph parse(SemanticGraph sg) {




    return null;
  }


  public SceneGraphImageRegion parse(List<CoreLabel> tokens,  Map<Integer, SceneGraphImage> trainImages) throws IOException {


    Counter<String> features = new ClassicCounter<String>();
    for (CoreLabel token : tokens) {
      features.incrementCount(token.word());
    }
    RVFDatum<String, String> datum = new RVFDatum<String, String>(features);
    String[] idParts = this.classifier.classOf(datum).split("_");

    int imgId = Integer.parseInt(idParts[0]);
    int regionId = Integer.parseInt(idParts[1]);



    SceneGraphImage img = trainImages.get(imgId);
    if (img == null) return null;

    return img.regions.get(regionId);

  }


  private  Map<Integer, SceneGraphImage> loadImages(String trainFile) throws IOException {
    Map<Integer, SceneGraphImage> images = Generics.newHashMap();

    BufferedReader reader = IOUtils.readerFromString(trainFile);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }

      images.put(img.id, img);
    }


    return images;
  }


  private void train(String trainFile, String modelPath) throws IOException {

    Map<Integer, SceneGraphImage> images = loadImages(trainFile);

    KNNClassifierFactory<String, String> classifierFactory = new KNNClassifierFactory<String, String>(1, false, false);
    List<RVFDatum<String, String>> dataset = Generics.newLinkedList();

    for (Integer imgId : images.keySet()) {
      SceneGraphImage img = images.get(imgId);
      if (img == null) {
        continue;
      }


      for (int i = 0, sz = img.regions.size(); i < sz; i++) {
        SceneGraphImageRegion region = img.regions.get(i);
        Counter<String> features = new ClassicCounter<String>();
        for (CoreLabel token : region.tokens) {
          features.incrementCount(token.word());
        }
        RVFDatum<String, String> datum = new RVFDatum<String, String>(features, String.format("%d_%d", img.id, i));
        dataset.add(datum);
      }

    }

    KNNClassifier<String, String> classifier = classifierFactory.train(dataset);

    IOUtils.writeObjectToFile(classifier, modelPath);
  }


  public static void main(String args[]) throws IOException {

    if (args.length < 3 || ! args[2].equals("-train")) {
      KNNSceneGraphParser parser = new KNNSceneGraphParser(args[1]);
      Map<Integer, SceneGraphImage> trainImages = parser.loadImages(args[2]);

      BufferedReader reader = IOUtils.readerFromString(args[0]);

      PrintWriter predWriter = IOUtils.getPrintWriter(args[3]);
      PrintWriter goldWriter = IOUtils.getPrintWriter(args[4]);


      SceneGraphEvaluation evaluation = new SceneGraphEvaluation();

      double count = 0.0;
      double f1Sum = 0.0;


      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        SceneGraphImage img = SceneGraphImage.readFromJSON(line);

        for (SceneGraphImageRegion region : img.regions) {
          count += 1.0;
          SceneGraphImageRegion predicted = parser.parse(region.tokens, trainImages);
          Triple<Double, Double, Double> scores = evaluation.evaluate(predicted, region);
          evaluation.toSmatchString(predicted, region, predWriter, goldWriter);

          SceneGraphImage predictedImg = new SceneGraphImage();
          predictedImg.id = img.id;
          predictedImg.url = img.url;
          predictedImg.height = img.height;
          predictedImg.width = img.width;

          Set<Integer> objectIds = Generics.newHashSet();

          for (SceneGraphImageAttribute attr : region.attributes) {
            objectIds.add(img.objects.indexOf(attr.subject));
          }
          for (SceneGraphImageRelationship reln : region.relationships) {
            objectIds.add(img.objects.indexOf(reln.subject));
            objectIds.add(img.objects.indexOf(reln.object));
          }

          predictedImg.objects = Generics.newArrayList();
          for (Integer objectId : objectIds) {
            predictedImg.objects.add(img.objects.get(objectId));
          }

          SceneGraphImageRegion newRegion = new SceneGraphImageRegion();
          newRegion.phrase = region.phrase;
          newRegion.x = region.x;
          newRegion.y = region.y;
          newRegion.h = region.h;
          newRegion.w = region.w;
          newRegion.attributes = Generics.newHashSet();
          newRegion.relationships = Generics.newHashSet();

          predictedImg.regions = Generics.newArrayList();
          predictedImg.regions.add(newRegion);

          predictedImg.attributes = Generics.newLinkedList();
          for (SceneGraphImageAttribute attr : region.attributes) {
            SceneGraphImageAttribute attrCopy = attr.clone();
            attrCopy.region = newRegion;
            attrCopy.image = predictedImg;
            predictedImg.addAttribute(attrCopy);
          }

          predictedImg.relationships = Generics.newLinkedList();

          for (SceneGraphImageRelationship reln : region.relationships) {
            SceneGraphImageRelationship relnCopy = reln.clone();
            relnCopy.image = predictedImg;
            relnCopy.region = newRegion;
            predictedImg.addRelationship(relnCopy);

          }


          System.out.println(predictedImg.toJSON());
          System.err.printf("Prec: %f, Recall: %f, F1: %f%n", scores.first, scores.second, scores.third);
          f1Sum += scores.third;

        }


      }
      System.err.println("#########################################################");
      System.err.printf("Macro-averaged F1: %f%n", f1Sum/count);
      System.err.println("#########################################################");



    } else {
      KNNSceneGraphParser parser = new KNNSceneGraphParser(null);
      parser.train(args[0], args[1]);
    }


  }

}
