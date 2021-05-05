package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class SceneGraphImagePCFGParser {

  public static final String PCFG_MODEL = "edu/stanford/nlp/models/scenegraph/englishPCFG-3.5.2+brown.ser.gz";

  public static void main(String args[]) throws IOException {

    LexicalizedParser parser = LexicalizedParser.getParserFromSerializedFile(PCFG_MODEL);
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

    String filename = args[0];

    BufferedReader reader = IOUtils.readerFromString(filename);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      for (SceneGraphImageRegion region : img.regions) {
        if (region.tokens != null) {

          for (CoreLabel token : region.tokens) {
            token.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
          }
          Tree t = parser.apply(region.tokens);
          region.gs = gsf.newGrammaticalStructure(t);
        }
      }
      System.out.println(img.toJSON());
    }
  }

}
