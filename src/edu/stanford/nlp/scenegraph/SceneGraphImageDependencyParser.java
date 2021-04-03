package edu.stanford.nlp.scenegraph;


import java.io.BufferedReader;
import java.io.IOException;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;


/**
 * Parses all the phrases in a SceneGraphImage.
 */


public class SceneGraphImageDependencyParser {

  public static final String DEP_MODEL = "/Users/sebschu/Dropbox/Uni/RA/VisualGenome/parser-models/run0.gz";

  public static void main(String args[]) throws IOException {
    DependencyParser parser = DependencyParser.loadFromModelFile(DependencyParser.DEFAULT_MODEL);

    String filename = args[0];

    BufferedReader reader = IOUtils.readerFromString(filename);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      for (SceneGraphImageRegion region : img.regions) {
        if (region.tokens != null) {
          region.gs = parser.predict(region.tokens);
        }
      }
      System.out.println(img.toJSON());
    }
  }





}
