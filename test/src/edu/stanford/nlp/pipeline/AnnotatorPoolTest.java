package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import junit.framework.TestCase;
import junit.framework.Assert;

/**
 * Makes sure that the pool creates new Annotators when the signature properties change
 */
public class AnnotatorPoolTest extends TestCase {
  static class SampleAnnotatorFactory extends AnnotatorFactory {
    private static final long serialVersionUID = 1L;
    public SampleAnnotatorFactory(Properties props) {
      super(props);
    }
    @Override
    public Annotator create() {
      return new Annotator() {
        @Override
        public void annotate(Annotation annotation) {
          // empty body; we don't actually use it here
        }

        @Override
        public Set<Requirement> requirementsSatisfied() {
          // empty body; we don't actually use it here
          return Collections.emptySet();
        }

        @Override
        public Set<Requirement> requires() {
          // empty body; we don't actually use it here
          return Collections.emptySet();
        }
      };
    }
    @Override
    public String signature() {
      // keep track of all relevant properties for this annotator here!
      StringBuilder os = new StringBuilder();
      os.append("sample.prop = " + properties.getProperty("sample.prop", ""));
      return os.toString();
    }
  }

  public void testSignature() throws Exception {
    Properties props = new Properties();
    props.put("sample.prop", "v1");
    AnnotatorPool pool = new AnnotatorPool();
    pool.register("sample", new SampleAnnotatorFactory(props));
    Annotator a1 = pool.get("sample");
    System.out.println("First annotator: " + a1);
    pool.register("sample", new SampleAnnotatorFactory(props));
    Annotator a2 = pool.get("sample");
    System.out.println("Second annotator: " + a2);
    Assert.assertTrue(a1 == a2);

    props.put("sample.prop", "v2");
    pool.register("sample", new SampleAnnotatorFactory(props));
    Annotator a3 = pool.get("sample");
    System.out.println("Third annotator: " + a3);
    Assert.assertTrue(a1 != a3);
  }
}
