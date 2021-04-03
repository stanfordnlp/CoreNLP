package edu.stanford.nlp.scenegraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageAttribute;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRelationship;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

public class SceneGraphImageFilter {

  private static Counter<String> attributeCounter = new ClassicCounter<String>();
  private static Counter<String> relationCounter = new ClassicCounter<String>();
  private static Counter<String> entityCounter = new ClassicCounter<String>();


  private static void countAll(List<SceneGraphImage> images) {
    for (SceneGraphImage img : images) {
      for (SceneGraphImageAttribute attr : img.attributes) {
        entityCounter.incrementCount(attr.subjectLemmaGloss());
        attributeCounter.incrementCount(attr.attributeLemmaGloss());
      }
      for (SceneGraphImageRelationship attr : img.relationships) {
        entityCounter.incrementCount(attr.subjectLemmaGloss());
        relationCounter.incrementCount(attr.predicateLemmaGloss());
        entityCounter.incrementCount(attr.objectLemmaGloss());
      }
    }
  }

  private static void filterRegions(List<SceneGraphImage> images, int threshold) {
    int regionCount = 0;
    int filterCount = 0;
    int imgCount = 0;
    int removedEntireImgCount = 0;
    int removedPartialImgCount = 0;

    for (SceneGraphImage img : images) {
      imgCount++;
      List<SceneGraphImageRegion> toDelete = Generics.newLinkedList();

      for (SceneGraphImageRegion region : img.regions) {
        regionCount++;
        boolean delete = false;
        for (SceneGraphImageAttribute attr : region.attributes) {
          if (attributeCounter.getCount(attr.attributeLemmaGloss()) < threshold
              || entityCounter.getCount(attr.subjectLemmaGloss()) < threshold ) {
            delete = true;
            break;
          }
        }

        if (delete) {
          toDelete.add(region);
          continue;
        }

        for (SceneGraphImageRelationship reln : region.relationships) {
          if (entityCounter.getCount(reln.objectLemmaGloss()) < threshold
              || entityCounter.getCount(reln.subjectLemmaGloss()) < threshold
              || relationCounter.getCount(reln.predicateLemmaGloss()) < threshold) {
            delete = true;
            break;
          }
        }

        if (delete) {
          toDelete.add(region);
          continue;
        }
      }

      for (SceneGraphImageRegion region : toDelete) {
        img.removeRegion(region);
        filterCount++;
      }

      if ( ! toDelete.isEmpty()) {
        removedPartialImgCount++;
      }

      if (img.regions.isEmpty()) {
        removedEntireImgCount++;
      }
    }

    System.err.printf("%d\t%f\t%f\t%f %n", filterCount, filterCount * 100.0 / regionCount, removedPartialImgCount * 100.0 / imgCount, removedEntireImgCount * 100.0 / imgCount);
  }



  public static void main(String[] args) throws IOException {
    String filename = args[0];
    int threshold = Integer.parseInt(args[1]);

    BufferedReader reader = IOUtils.readerFromString(filename);

    List<SceneGraphImage> images = Generics.newLinkedList();

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = SceneGraphImage.readFromJSON(line);
      if (img == null) {
        continue;
      }
      images.add(img);
    }

    countAll(images);

    filterRegions(images, threshold);

    for (SceneGraphImage img : images) {
      if ( ! img.regions.isEmpty()) {
        System.out.println(img.toJSON());
      }
    }

  }

}
