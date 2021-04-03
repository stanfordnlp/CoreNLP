package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.util.Generics;

public class GroundTruthConverter {

  public static void main(String[] args) throws IOException {

    BufferedReader reader = IOUtils.readerFromString(args[0]);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      for (SceneGraphImageRegion region : img.regions) {


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


      }
    }
  }

}
