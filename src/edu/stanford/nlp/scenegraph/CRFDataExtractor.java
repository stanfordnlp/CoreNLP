package edu.stanford.nlp.scenegraph;

//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.List;
//import java.util.Set;
//
//import edu.illinois.cs.cogcomp.core.io.IOUtils;
//import edu.stanford.nlp.classify.Dataset;
//import edu.stanford.nlp.classify.RVFDataset;
//import edu.stanford.nlp.ling.IndexedWord;
//import edu.stanford.nlp.ling.RVFDatum;
//import edu.stanford.nlp.scenegraph.BoWExample.FEATURE_SET;
//import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
//import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
//import edu.stanford.nlp.semgraph.SemanticGraph;
//import edu.stanford.nlp.util.Generics;
//import edu.stanford.nlp.util.Triple;

public class CRFDataExtractor {

  /*
  private static FEATURE_SET[] featureSets = {FEATURE_SET.LEMMA_BOW, FEATURE_SET.WORD_BOW, FEATURE_SET.TREE_FEAT};



  public static RVFDataset<String, String> getTrainingExamples(String trainingFile, boolean sampleNeg) throws IOException {
    RVFDataset<String, String> dataset = new RVFDataset<String, String>();

    RVFDataset<String, String> negDataset = new RVFDataset<String, String>();


    List<SceneGraphImage> images = BoWSceneGraphParser.loadImages(trainingFile);
    for (SceneGraphImage image : images) {
      for (SceneGraphImageRegion region : image.regions) {
        SemanticGraph sg = region.getBasicSemanticGraph();
        Set<Integer> entityPairs = Generics.newHashSet();
        List<Triple<IndexedWord, IndexedWord, String>> relationTriples = SceneGraphSentenceMatcher.getRelationTriples(region);
        for (Triple<IndexedWord, IndexedWord, String> triple : relationTriples) {
          SemanticGraphEnhancer.resolvePronouns(sg);
          //entityClassifer.predictEntity(triple.first);
          //entityClassifer.predictEntity(triple.second);
          BoWExample example = new BoWExample(triple.first, triple.second, region.getBasicSemanticGraph());
          String subjectClass = triple.first.getString(SceneGraphCoreAnnotations.SceneGraphEntitiyAnnotation.class);
          String objectClass = triple.second.getString(SceneGraphCoreAnnotations.SceneGraphEntitiyAnnotation.class);
          dataset.add(new RVFDatum<String,String>(example.extractSubjectFeatures(featureSets), "s_" + subjectClass));
          dataset.add(new RVFDatum<String,String>(example.extractRelationFeatures(featureSets), "r_" + triple.third));
          dataset.add(new RVFDatum<String,String>(example.extractObjectFeatures(featureSets), "o_" + objectClass));
          entityPairs.add((triple.first.index() << 4) + triple.second.index());
        }
        // Add negative examples.

        List<IndexedWord> entities = EntityExtractor.extractEntities(sg);
        List<IndexedWord> attributes = EntityExtractor.extractAttributes(sg);

//        for (IndexedWord e : entities) {
//          entityClassifer.predictEntity(e);
//        }

//        for (IndexedWord a : attributes) {
//          entityClassifer.predictEntity(a);
//        }

        for (IndexedWord e1 : entities) {
          for (IndexedWord e2 : entities) {
            if (e1.index() == e2.index()) {
              continue;
            }

            int entityPair = (e1.index() << 4) + e2.index();
            if ( ! entityPairs.contains(entityPair) && SceneGraphUtils.inSameSubTree(sg, e1, e2)) {
              BoWExample example = new BoWExample(e1, e2, sg);
              String subjectClass = "s_" + BoWSceneGraphParser.NONE_RELATION;
              String objectClass = "o_" + BoWSceneGraphParser.NONE_RELATION;
              negDataset.add(new RVFDatum<String,String>(example.extractSubjectFeatures(featureSets), "s_" + subjectClass));
              negDataset.add(new RVFDatum<String,String>(example.extractObjectFeatures(featureSets), "o_" + objectClass));
              negDataset.add(new RVFDatum<String,String>(example.extractRelationFeatures(featureSets), BoWSceneGraphParser.NONE_RELATION));

            }

          }
        }

        for (IndexedWord e : entities) {
          for (IndexedWord a : attributes) {
            int entityPair = (e.index() << 4) + a.index();
            if ( ! entityPairs.contains(entityPair) && SceneGraphUtils.inSameSubTree(sg, e, a)) {
              BoWExample example = new BoWExample(e, a, sg);
              String subjectClass = "s_" + BoWSceneGraphParser.NONE_RELATION;
              String objectClass = "o_" + BoWSceneGraphParser.NONE_RELATION;
              negDataset.add(new RVFDatum<String,String>(example.extractSubjectFeatures(featureSets), "s_" + subjectClass));
              negDataset.add(new RVFDatum<String,String>(example.extractObjectFeatures(featureSets), "o_" + objectClass));
              negDataset.add(new RVFDatum<String,String>(example.extractRelationFeatures(featureSets), BoWSceneGraphParser.NONE_RELATION));
            }
          }
        }
      }
    }

    if (sampleNeg && dataset.size() < negDataset.size()) {
      //negDataset = negDataset.getRandomSubDataset(dataset.size() * 1.0 / negDataset.size(),3, 42);
    }
    dataset.addAll(negDataset);

    return dataset;
  }

  public static void main(String[] args) throws IOException {
    RVFDataset<String, String> dataset = getTrainingExamples(args[0], true);
    dataset.printSparseFeatureValues(new PrintWriter(System.out));
    dataset.featureIndex.saveToFilename("featureIndex.txt");
  }
*/
}
