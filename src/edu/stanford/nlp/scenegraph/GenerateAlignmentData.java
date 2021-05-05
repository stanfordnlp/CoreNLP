package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

/**
 * Generates a bitext of sentences and corresponding
 * relations and attributes.
 */


public class GenerateAlignmentData {

  public static void main(String[] args) throws IOException {


    Properties props = new Properties();
    props.put("annotators", "tokenize,ssplit");
    props.put("ssplit.eolonly", "true");

    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    String filename = args[0];
    String sentences = args[1];
    String graphs = args[2];

    BufferedReader reader = IOUtils.readerFromString(filename);
    PrintWriter sentencesFile = IOUtils.getPrintWriter(sentences);
    PrintWriter graphsFile = IOUtils.getPrintWriter(graphs);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      for (SceneGraphImageRegion region : img.regions) {
        Annotation doc = new Annotation(region.phrase);
        pipeline.annotate(doc);
        CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        String tokenizedSentence = StringUtils.join(tokens.stream().map(CoreLabel::word), " ");
        for (SceneGraphImageAttribute attr : region.attributes) {
          sentencesFile.printf("%s%n", tokenizedSentence);
          graphsFile.printf("%s%n", StringUtils.join(attr.text));
        }
        for (SceneGraphImageRelationship reln : region.relationships) {
          sentencesFile.printf("%s%n", tokenizedSentence);
          graphsFile.printf("%s%n", StringUtils.join(reln.text));
        }
      }
    }
  }
}
