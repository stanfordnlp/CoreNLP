package edu.stanford.nlp.scenegraph;

import java.io.StringReader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import static org.junit.Assert.assertEquals;

import edu.stanford.nlp.util.Sets;
import edu.stanford.nlp.util.Triple;

/**
 * Verifies that a bunch of one line test cases come back as expected.
 * Based on a list of test cases provided by Sebastian.
 *
 * @author John Bauer
 */
public class RuleBasedParserITest {

  public static AbstractSceneGraphParser parser;

  @BeforeClass
  public static void initParser() {
    parser = new RuleBasedParser();
  }

  @AfterClass
  public static void disposeParser() {
    parser = null;
  }

  /**
   * The format of a test is:
   * - text to process
   * - list of source/reln/target lines, in that order
   * - list of nodes
   *   - each node is a list
   *   - the list starts with the text of the node
   *   - if the node should have attributes, that makes up the rest of the list
   */
  void runTest(AbstractSceneGraphParser parser,
               String text,
               List<Triple<String, String, String>> expectedEdges,
               List<List<String>> expectedNodes) {
    SceneGraph scene = parser.parse(text);
    assertEquals("Number of relations in the scene is not as expected", expectedEdges.size(), scene.relationListSorted().size());
    assertEquals("Number of nodes in the scene is not as expected", expectedNodes.size(), scene.nodeListSorted().size());

    // These are sorted, so hopefully no special logic is needed
    // to make sure the order is consistent
    List<SceneGraphRelation> edges = scene.relationListSorted();
    for (int i = 0; i < expectedEdges.size(); ++i) {
      Triple<String, String, String> expected = expectedEdges.get(i);
      SceneGraphRelation predicted = edges.get(i);
      assertEquals(expected.first,  predicted.getSource().toString());
      assertEquals(expected.second, predicted.getRelation());
      assertEquals(expected.third,  predicted.getTarget().toString());
    }

    // Check the nodes and check the attributes on each node
    List<SceneGraphNode> nodes = scene.nodeListSorted();
    for (int i = 0; i < expectedNodes.size(); ++i) {
      List<String> expected = expectedNodes.get(i);
      SceneGraphNode predicted = nodes.get(i);
      assertEquals(expected.get(0), predicted.toString());

      Set<String> expectedAttributes = new HashSet<>(expected.subList(1, expected.size()));
      Set<String> attributes = Sets.map(predicted.getAttributes(), (x) -> x.toString());
      assertEquals(expectedAttributes, attributes);
    }
  }

  @Test
  public void testSceneGraph() {
    runTest(parser,
            "A man is riding a horse.",
            Arrays.asList(new Triple<>("man-2", "ride", "horse-6")),
            Arrays.asList(Arrays.asList("man-2"),
                          Arrays.asList("horse-6")));
    runTest(parser,
            "A woman is smiling.",
            Collections.emptyList(),
            Arrays.asList(Arrays.asList("woman-2", "smile")));
    runTest(parser,
            "The man is a rider.",
            Collections.emptyList(),
            Arrays.asList(Arrays.asList("man-2", "rider")));
    runTest(parser,
            "A smart woman.",
            Collections.emptyList(),
            Arrays.asList(Arrays.asList("woman-3", "smart")));
    runTest(parser,
            "The man is tall.",
            Collections.emptyList(),
            Arrays.asList(Arrays.asList("man-2", "tall")));
    runTest(parser,
            "A tall woman is in the cold house.",
            Arrays.asList(new Triple<>("woman-3", "in", "house-8")),
            Arrays.asList(Arrays.asList("woman-3", "tall"),
                          Arrays.asList("house-8", "cold")));
    runTest(parser,
            "The man's watch.",
            Arrays.asList(new Triple<>("man-2", "have", "watch-4")),
            Arrays.asList(Arrays.asList("man-2"),
                          Arrays.asList("watch-4")));
    runTest(parser,
            "The grass was eaten by a lot of horses.",
            Arrays.asList(new Triple<>("horse-9", "eat", "grass-2")),
            Arrays.asList(Arrays.asList("grass-2"),
                          Arrays.asList("horse-9")));
    runTest(parser,
            "A cat eating a fish.",
            Arrays.asList(new Triple<>("cat-2", "eat", "fish-5")),
            Arrays.asList(Arrays.asList("cat-2"),
                          Arrays.asList("fish-5")));
  }

  @Test
  public void testJson() {
    String text = "A smiling man is riding a horse.";
    SceneGraph scene = parser.parse(text);

    // This was how the simple-json library output the json
    // Actually, since json is order-free, we need to process the
    // expected results and the actual results back into maps and
    // compare those maps
    String expectedJSONtext = "{\"relationships\":[{\"predicate\":\"ride\",\"subject\":0,\"text\":[\"man\",\"ride\",\"horse\"],\"object\":1}],\"phrase\":\"A smiling man is riding a horse.\",\"objects\":[{\"names\":[\"man\"]},{\"names\":[\"horse\"]}],\"attributes\":[{\"predicate\":\"is\",\"subject\":0,\"attribute\":\"smile\",\"text\":[\"man\",\"is\",\"smile\"],\"object\":\"smile\"}],\"id\":1,\"url\":\"www.stanford.edu\"}";

    StringReader reader = new StringReader(expectedJSONtext);
    JsonReader parser = Json.createReader(reader);
    JsonObject expectedJSON = parser.readObject();

    String convertedText = scene.toJSON(1, "www.stanford.edu", text);
    reader = new StringReader(convertedText);
    parser = Json.createReader(reader);
    JsonObject converted = parser.readObject();

    assertEquals(expectedJSON, converted);

    // The json for the nodes is just the word of the node
    List<String> expectedNodes = Arrays.asList("man", "horse");
    List<SceneGraphNode> nodes = scene.nodeListSorted();
    assertEquals(expectedNodes.size(), nodes.size());
    for (int i = 0; i < nodes.size(); ++i) {
      assertEquals(expectedNodes.get(i), nodes.get(i).toJSONString());
    }
  }
}
