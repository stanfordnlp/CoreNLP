package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.scenegraph.BoWExample.FEATURE_SET;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;


/**
 *
 * A MaxEnt classifier-based scene graph parser.
 *
 * Takes a dependency parse, performs some enhancements using
 * {@link SemanticGraphEnhancer}, extracts objects and attributes using the
 * {@link EntityExtractor} and a {@link EntityClassifier}
 * and predicts the relations between theese objects and attributes
 * to build a {@link SceneGraph}.
 *
 * @author Sebastian Schuster
 *
 */


public class BoWSceneGraphParser extends AbstractSceneGraphParser {

  Classifier<String, String> classifier;
  EntityClassifier entityClassifer;
  boolean enforceSubtree;
  boolean includeAllObjects;
  private Embedding embeddings;
  private SceneGraphSentenceMatcher sentenceMatcher;


  public static final String NONE_RELATION = "----NONE----";
  public static final String IS_RELATION = "is";

  public static final String DEFAULT_MODEL_PATH = "edu/stanford/nlp/models/scenegraph/bow_model_final_subtree.gz";
  public static final String DEFAULT_ENTITY_MODEL_PATH = "edu/stanford/nlp/models/scenegraph/entityModel.gz";


    private static final double REG_STRENGTH = 1.0;

  private static FEATURE_SET[] featureSets = {FEATURE_SET.LEMMA_BOW, FEATURE_SET.WORD_BOW, FEATURE_SET.TREE_FEAT};


  /**
   * Constructor.
   *
   * @param model Path to relation prediction model.
   * @param entityModel Path to entity model.
   */
  public BoWSceneGraphParser(String model, String entityModel, Embedding embeddings) {
    if (model != null) {
      try {
        this.classifier = IOUtils.readObjectFromURLOrClasspathOrFileSystem(model);
      } catch (ClassNotFoundException | IOException e) {
        e.printStackTrace();
      }
    }

    if (entityModel != null) {
      entityClassifer = new EntityClassifier(entityModel);
    }

    this.embeddings = embeddings;
    this.sentenceMatcher = new SceneGraphSentenceMatcher(embeddings);
  }

  @Override
  public SceneGraph parse(SemanticGraph sg) {


    /* Enhance semantic graph */
    SemanticGraphEnhancer.enhance(sg);

    /* Extract objects and attributes. */
    List<IndexedWord> entities = EntityExtractor.extractEntities(sg);
    List<IndexedWord> attributes = EntityExtractor.extractAttributes(sg);


    /* Predict object and attributes. */
    for (IndexedWord e : entities) {
      entityClassifer.predictEntity(e, this.embeddings);
    }

    for (IndexedWord a : attributes) {
      entityClassifer.predictEntity(a, this.embeddings);
    }

    /* Generate relation and attribute candidates. */
    List<BoWExample> examples = Generics.newLinkedList();
    for (IndexedWord e1 : entities) {
      for (IndexedWord e2 : entities) {
        if (e1.index() == e2.index() || (enforceSubtree && ! SceneGraphUtils.inSameSubTree(sg, e1, e2))) {
          continue;
        }
        examples.add(new BoWExample(e1, e2, sg));
      }
    }

    for (IndexedWord e : entities) {
      for (IndexedWord a : attributes) {
        if ( ! enforceSubtree || SceneGraphUtils.inSameSubTree(sg, e, a)) {
          examples.add(new BoWExample(e, a, sg));
        }
      }
    }

    SceneGraph scene = new SceneGraph();
    /* Build scene graph based on predictions. */
    for (BoWExample example : examples) {
      String reln = classifier.classOf(new BasicDatum<String,String>(example.extractFeatures(featureSets)));
      if (! reln.equals(NONE_RELATION)) {
        if (reln.equals(IS_RELATION)) {
          SceneGraphNode node = scene.getOrAddNode(example.w1);
          node.addAttribute(example.w2);
        } else {
          SceneGraphNode node1 = scene.getOrAddNode(example.w1);
          SceneGraphNode node2 = scene.getOrAddNode(example.w2);
          scene.addEdge(node1, node2, reln);
        }
      }
    }

    /* Fallback: If no relation or attribute was extracted, add
     * all extracted entities to the scene graph to prevent
     * empty scene graphs. */
    if (includeAllObjects || scene.nodeListSorted().isEmpty()) {
      for (IndexedWord e : entities) {
        scene.getOrAddNode(e);
      }
    }
    return scene;
  }


  /**
   * Loads scene graphs from JSON file.
   *
   * @param filename Path to JSON file.
   * @return List with images parsed to SceneGraphImage.
   * @throws IOException
   */
  public static List<SceneGraphImage> loadImages(String filename) throws IOException {
    List<SceneGraphImage> images = Generics.newLinkedList();

    BufferedReader reader = IOUtils.readerFromString(filename);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      images.add(img);
    }
    return images;
  }

  /**
   *
   * Generate training examples.
   *
   * @param trainingFile Path to JSON file with training images and scene graphs.
   * @param sampleNeg Whether to sample the same number of negative examples as positive examples.
   * @return Dataset to train a classifier.
   * @throws IOException
   */
  public Dataset<String, String> getTrainingExamples(String trainingFile, boolean sampleNeg) throws IOException {
    Dataset<String, String> dataset = new Dataset<String, String>();

    Dataset<String, String> negDataset = new Dataset<String, String>();

    /* Load images. */
    List<SceneGraphImage> images = loadImages(trainingFile);

    for (SceneGraphImage image : images) {
      for (SceneGraphImageRegion region : image.regions) {
        SemanticGraph sg = region.getEnhancedSemanticGraph();
        SemanticGraphEnhancer.processQuanftificationModifiers(sg);
        SemanticGraphEnhancer.collapseCompounds(sg);
        SemanticGraphEnhancer.collapseParticles(sg);
        SemanticGraphEnhancer.resolvePronouns(sg);

        Set<Integer> entityPairs = Generics.newHashSet();
        List<Triple<IndexedWord, IndexedWord, String>> relationTriples = this.sentenceMatcher.getRelationTriples(region);
        for (Triple<IndexedWord, IndexedWord, String> triple : relationTriples) {
          IndexedWord iw1 = sg.getNodeByIndexSafe(triple.first.index());
          IndexedWord iw2 = sg.getNodeByIndexSafe(triple.second.index());
          if (iw1 != null && iw2 != null && (! enforceSubtree || SceneGraphUtils.inSameSubTree(sg, iw1, iw2))) {
            entityClassifer.predictEntity(iw1, this.embeddings);
            entityClassifer.predictEntity(iw2, this.embeddings);
            BoWExample example = new BoWExample(iw1, iw2, sg);
            dataset.add(example.extractFeatures(featureSets), triple.third);
          }
          entityPairs.add((triple.first.index() << 4) + triple.second.index());
        }
        /* Add negative examples. */

        List<IndexedWord> entities = EntityExtractor.extractEntities(sg);
        List<IndexedWord> attributes = EntityExtractor.extractAttributes(sg);

        for (IndexedWord e : entities) {
          entityClassifer.predictEntity(e, this.embeddings);
        }

        for (IndexedWord a : attributes) {
          entityClassifer.predictEntity(a, this.embeddings);
        }

        for (IndexedWord e1 : entities) {
          for (IndexedWord e2 : entities) {
            if (e1.index() == e2.index()) {
              continue;
            }

            int entityPair = (e1.index() << 4) + e2.index();
            if ( ! entityPairs.contains(entityPair) && ( ! enforceSubtree || SceneGraphUtils.inSameSubTree(sg, e1, e2))) {
              BoWExample example = new BoWExample(e1, e2, sg);
              negDataset.add(example.extractFeatures(featureSets), NONE_RELATION);

            }

          }
        }

        for (IndexedWord e : entities) {
          for (IndexedWord a : attributes) {
            int entityPair = (e.index() << 4) + a.index();
            if ( ! entityPairs.contains(entityPair) &&  ( ! enforceSubtree || SceneGraphUtils.inSameSubTree(sg, e, a))) {
              BoWExample example = new BoWExample(e, a, sg);
              negDataset.add(example.extractFeatures(featureSets), NONE_RELATION);
            }
          }
        }
      }
    }

    /* Sample from negative examples to make the training set
     * more balanced. */
    if (sampleNeg && dataset.size() < negDataset.size()) {
      negDataset = negDataset.getRandomSubDataset(dataset.size() * 1.0 / negDataset.size(), 42);
    }
    dataset.addAll(negDataset);

    return dataset;
  }

  /**
   * Trains a classifier using the examples in trainingFile and saves
   * it to modelPath.
   *
   * @param trainingFile Path to JSON file with images and scene graphs.
   * @param modelPath
   * @throws IOException
   */
  public void train(String trainingFile, String modelPath) throws IOException {

    LinearClassifierFactory<String, String> classifierFactory = new LinearClassifierFactory<String, String>(new QNMinimizer(15), 1e-4, false, REG_STRENGTH);

    /* Create dataset. */
    Dataset<String, String> dataset = getTrainingExamples(trainingFile, true);

    /* Train the classifier. */
    Classifier<String, String> classifier = classifierFactory.trainClassifier(dataset);

    /* Save classifier to disk. */
    IOUtils.writeObjectToFile(classifier, modelPath);

  }

  private static final Map<String, Integer> numArgs = new HashMap<>();
  static {
    numArgs.put("model", 1);
    numArgs.put("entityModel", 1);
    numArgs.put("evalFilePrefix", 1);
    numArgs.put("input", 1);

  }

  public static void main(String[] args) throws IOException {

    Properties props = StringUtils.argsToProperties(args, numArgs);
    boolean train = PropertiesUtils.getBool(props, "train", false);
    boolean enforceSubtree = PropertiesUtils.getBool(props, "enforceSubtree", true);
    boolean includeAllObjects = PropertiesUtils.getBool(props, "includeAllObjects", false);

    boolean verbose = PropertiesUtils.getBool(props, "verbose", false);
    String modelPath = PropertiesUtils.getString(props, "model", DEFAULT_MODEL_PATH);
    String entityModelPath = PropertiesUtils.getString(props, "entityModel", DEFAULT_ENTITY_MODEL_PATH);
    String inputPath = PropertiesUtils.getString(props, "input", null);
    String embeddingsPath = PropertiesUtils.getString(props, "embeddings", null);


    if (modelPath == null || entityModelPath == null || embeddingsPath == null) {
      System.err.printf("Usage java %s -model <model.gz>"
          + " -entityModel <entityModel.gz> -embeddings <wordVectors.gz> [-input <input.json> -train -verbose -enforceSubtree -evalFilePrefix <run0>]%n", BoWSceneGraphParser.class.getCanonicalName());
      return;
    }

    boolean interactive = (inputPath == null);


    Embedding embeddings = new Embedding(embeddingsPath);

    if ( ! train) {

      BoWSceneGraphParser parser = new BoWSceneGraphParser(modelPath, entityModelPath, embeddings);
      parser.enforceSubtree = enforceSubtree;
      parser.includeAllObjects = includeAllObjects;

      if ( ! interactive) {

        BufferedReader reader = IOUtils.readerFromString(inputPath);

        SceneGraphEvaluation eval = new SceneGraphEvaluation();

        String evalFilePrefix = PropertiesUtils.getString(props, "evalFilePrefix", null);

        PrintWriter predWriter = null;
        PrintWriter goldWriter = null;
        if (evalFilePrefix != null) {
            String predEvalFilePath = evalFilePrefix + ".smatch";
            String goldEvalFilePath = evalFilePrefix + "_gold.smatch";
            predWriter = IOUtils.getPrintWriter(predEvalFilePath);
            goldWriter = IOUtils.getPrintWriter(goldEvalFilePath);
        }

        double count = 0.0;
        double f1Sum = 0.0;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            SceneGraphImage img = SceneGraphImage.readFromJSON(line);
            if (img == null) {
                continue;
            }
            for (SceneGraphImageRegion region : img.regions) {
                count += 1.0;
                SemanticGraph sg = region.getEnhancedSemanticGraph();
                SceneGraph scene = parser.parse(sg);
                System.out.println(scene.toJSON(img.id, img.url, region.phrase));
                Triple<Double, Double, Double> scores = eval.evaluate(scene, region);
                if (evalFilePrefix != null) {
                    eval.toSmatchString(scene, region, predWriter, goldWriter);
                }
                if (verbose) {
                    System.err.println(region.phrase);
                    System.err.println(scene.toReadableString());
                    System.err.println(region.toReadableString());
                    System.err.printf("Prec: %f, Recall: %f, F1: %f%n", scores.first, scores.second, scores.third);
                    System.err.println("------------------------");
                }
                f1Sum += scores.third;
            }
        }

        System.err.println("#########################################################");
        System.err.printf("Macro-averaged F1: %f%n", f1Sum / count);
         System.err.println("#########################################################");
      } else {
        System.err.println("Processing from stdin. Enter one sentence per line.");
        System.err.print("> ");
        Scanner scanner = new Scanner(System.in);
        String line;
        while ( (line = scanner.nextLine()) != null ) {
            SceneGraph scene = parser.parse(line);
            System.err.println(scene.toReadableString());

            System.err.println("------------------------");
            System.err.print("> ");

        }

        scanner.close();
      }
    } else {
      BoWSceneGraphParser parser = new BoWSceneGraphParser(null, entityModelPath, embeddings);
      parser.enforceSubtree = enforceSubtree;
      parser.train(inputPath, modelPath);
    }
  }
}
