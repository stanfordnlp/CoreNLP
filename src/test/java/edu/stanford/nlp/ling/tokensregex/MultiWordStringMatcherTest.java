package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.StringUtils;
import junit.framework.TestCase;

import java.util.List;

/**
 * Test methods in MultiWordStringMatcher.
 *
 * @author Angel Chang
 */
public class MultiWordStringMatcherTest extends TestCase {

  public void testExctWsMatching() throws Exception {
    MultiWordStringMatcher entityMatcher = new MultiWordStringMatcher(MultiWordStringMatcher.MatchType.EXCTWS);
    String targetString = "Al-Ahram";
    String context = "the government Al-Ahram newspaper";
    List<IntPair> offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertEquals("entityOffsets", "[15 23]", "[" + StringUtils.join(offsets, ",") + "]");
    context = "the government Al- Ahram newspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertEquals("entityOffsets", "[15 24]", "[" + StringUtils.join(offsets, ",") + "]");

    targetString = "Al -Ahram";
    offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertTrue("entityOffsets", offsets == null || offsets.isEmpty());
    context = "the government Al-Ahramnewspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertTrue("entityOffsets", offsets == null || offsets.isEmpty());

    context = "the government AlAhram newspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertTrue("entityOffsets", offsets == null || offsets.isEmpty());
    context = "the government alahram newspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertTrue("entityOffsets", offsets == null || offsets.isEmpty());

    context = "NZ Oil &amp;amp; Gas";
    targetString = "NZ Oil &amp;amp; Gas";
    offsets = entityMatcher.findTargetStringOffsets(context, targetString);
    assertEquals("entityOffsets", "[0 20]", "[" + StringUtils.join(offsets, ",") + "]");
  }

  public void testLnrmMatching() throws Exception {
    MultiWordStringMatcher entityMatcher = new MultiWordStringMatcher(MultiWordStringMatcher.MatchType.LNRM);
    String entityName = "Al-Ahram";
    String context = "the government Al-Ahram newspaper";
    List<IntPair> offsets = entityMatcher.findTargetStringOffsets(context, entityName);
    assertEquals("entityOffsets", "[15 23]", "[" + StringUtils.join(offsets, ",") + "]");
    context = "the government Al- Ahram newspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, entityName);
    assertEquals("entityOffsets", "[15 24]", "[" + StringUtils.join(offsets, ",") + "]");
    entityName = "Al -Ahram";
    offsets = entityMatcher.findTargetStringOffsets(context, entityName);
    assertEquals("entityOffsets", "[15 24]", "[" + StringUtils.join(offsets, ",") + "]");
    context = "the government Al-Ahramnewspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, entityName);
    assertTrue("entityOffsets", offsets == null || offsets.isEmpty());

    context = "the government AlAhram newspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, entityName);
    assertEquals("entityOffsets", "[15 22]", "[" + StringUtils.join(offsets, ",") + "]");
    context = "the government alahram newspaper";
    offsets = entityMatcher.findTargetStringOffsets(context, entityName);
    assertEquals("entityOffsets", "[15 22]", "[" + StringUtils.join(offsets, ",") + "]");
  }

}
