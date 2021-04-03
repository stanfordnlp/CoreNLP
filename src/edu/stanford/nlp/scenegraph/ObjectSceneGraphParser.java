package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.semgraph.SemanticGraph;

public class ObjectSceneGraphParser extends AbstractSceneGraphParser {

  private EntityClassifier entityClassifier;

  public ObjectSceneGraphParser(String entityClassifierPath) {
    this.entityClassifier = new EntityClassifier(entityClassifierPath);
  }

  private Embedding embeddings;


  @Override
  public SceneGraph parse(SemanticGraph sg) {
    SemanticGraphEnhancer.enhance(sg);

    List<IndexedWord> objects = EntityExtractor.extractEntities(sg);

    SceneGraph scene = new SceneGraph();

    for (IndexedWord obj : objects) {
      entityClassifier.predictEntity(obj, embeddings);
      scene.addNode(obj);
    }


    return scene;
  }

  public static void main(String[] args) throws IOException {
    ObjectSceneGraphParser parser = new ObjectSceneGraphParser(args[1]);

    BufferedReader reader = IOUtils.readerFromString(args[0]);

    SceneGraphEvaluation eval = new SceneGraphEvaluation();

    String evalFilePrefix = args[2];

    String embeddingsPath = args[3];

    parser.embeddings = new Embedding(embeddingsPath);


    PrintWriter predWriter = null;
    PrintWriter goldWriter = null;
    if (evalFilePrefix != null) {
      String predEvalFilePath = evalFilePrefix + ".smatch";
      String goldEvalFilePath = evalFilePrefix + "_gold.smatch";
      predWriter = IOUtils.getPrintWriter(predEvalFilePath);
      goldWriter = IOUtils.getPrintWriter(goldEvalFilePath);
    }

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      for (SceneGraphImageRegion region : img.regions) {
        SemanticGraph sg = region.getEnhancedSemanticGraph();
        SceneGraph scene = parser.parse(sg);
        System.out.println(scene.toJSON(img.id, img.url, region.phrase));
        if (evalFilePrefix != null) {
          eval.toSmatchString(scene, region, predWriter, goldWriter);
        }
      }
    }
  }
}
