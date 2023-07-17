package edu.stanford.nlp.scenegraph.image;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Test the json conversion of the SceneGraphImage.
 *
 * @author John Bauer
 */
public class SceneGraphImageTest {

  @Test
  public void testConvertJSON() {
    String json = ("{'relationships':" +
                   "[{'predicate':'ride','subject':0,'text':['man','ride','horse'],'object':1}]," +
                   "'phrase':'A smiling man is riding a horse.'," +
                   "'objects':" +
                   " [{'names':['man'], 'bbox':{'h': 50, 'w': 50, 'x': 50, 'y': 50}}," +
                   "  {'names':['horse'], 'bbox':{'h': 50, 'w': 50, 'x': 50, 'y': 50}}]," +
                   "'attributes':[{'predicate':'is','subject':0,'attribute':'smile','text':['man','is','smile']," +
                   "'object':'smile'}]," +
                   "'id':1," +
                   "'height': 150," +
                   "'width': 150," +
                   "'url':'www.stanford.edu'}").replace("'", "\"");

    SceneGraphImage image = SceneGraphImage.readFromJSON(json);

    String converted = image.toJSON();
    SceneGraphImage image2 = SceneGraphImage.readFromJSON(converted);

    assertEquals(image.id, image2.id);
    assertEquals(image.url, image2.url);    

    assertEquals(image.height, image2.height);
    assertEquals(image.width, image2.width);

    assertEquals(image.objects.size(), image2.objects.size());
    for (int i = 0; i < image.objects.size(); ++i) {
      assertEquals(image.objects.get(i).boundingBox, image2.objects.get(i).boundingBox);
      assertEquals(image.objects.get(i).names, image2.objects.get(i).names);
      assertEquals(image.objects.get(i).labels, image2.objects.get(i).labels);
    }

    assertEquals(image.regions.size(), image2.regions.size());

    assertEquals(image.relationships.size(), image2.relationships.size());
    for (int i = 0; i < image.relationships.size(); ++i) {
      assertEquals(image.relationships.get(i).predicate, image2.relationships.get(i).predicate);
      assertEquals(image.relationships.get(i).subject, image2.relationships.get(i).subject);
      assertEquals(image.relationships.get(i).object, image2.relationships.get(i).object);
    }

    assertEquals(image.attributes.size(), image2.attributes.size());
    for (int i = 0; i < image.attributes.size(); ++i) {
      assertEquals(image.attributes.get(i).attribute, image2.attributes.get(i).attribute);
      assertEquals(image.attributes.get(i).object, image2.attributes.get(i).object);
      assertEquals(image.attributes.get(i).predicate, image2.attributes.get(i).predicate);
      assertEquals(image.attributes.get(i).subject, image2.attributes.get(i).subject);
    }
  }
}
