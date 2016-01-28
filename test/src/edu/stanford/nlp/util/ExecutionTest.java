package edu.stanford.nlp.util;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

/**
 * A small test for the {@link Execution} class for loading command line options
 *
 * @author Gabor Angeli
 */
public class ExecutionTest {

  public static class StaticClass {
    @Execution.Option(name="option.static")
    public static int staticOption = -1;
  }

  public static class NonstaticClass {
    @Execution.Option(name="option.nonstatic")
    public int staticOption = -1;
  }

  public static class MixedClass {
    @Execution.Option(name="option.static")
    public static int staticOption = -1;
    @Execution.Option(name="option.nonstatic")
    public int nonstaticOption = -1;
  }

  @Before
  public void setUp() {
    StaticClass.staticOption = -1;
    MixedClass.staticOption = -1;
  }

  @Test
  public void testFillStaticField() {
    assertEquals(-1, StaticClass.staticOption);
    Execution.fillOptions(StaticClass.class, new String[]{ "-option.static", "42" });
    assertEquals(42, StaticClass.staticOption);
  }

  @Test
  public void testFillStaticFieldFromProperties() {
    assertEquals(-1, StaticClass.staticOption);
    Properties props = new Properties();
    props.setProperty("option.static", "42");
    Execution.fillOptions(StaticClass.class, props);
    assertEquals(42, StaticClass.staticOption);
  }

  @Test
  public void fillNonstaticField() {
    NonstaticClass x = new NonstaticClass();
    assertEquals(-1, x.staticOption);
    Execution.fillOptions(x, new String[]{ "-option.nonstatic", "42" });
    assertEquals(42, x.staticOption);
  }

  @Test
  public void fillNonstaticFieldFromProperties() {
    NonstaticClass x = new NonstaticClass();
    assertEquals(-1, x.staticOption);
    Properties props = new Properties();
    props.setProperty("option.nonstatic", "42");
    Execution.fillOptions(x, props);
    assertEquals(42, x.staticOption);
  }

  @Test
  public void fillMixedFieldsInstanceGiven() {
    MixedClass x = new MixedClass();
    assertEquals(-1, MixedClass.staticOption);
    assertEquals(-1, x.nonstaticOption);
    Execution.fillOptions(x, new String[]{ "-option.nonstatic", "42", "-option.static", "43" });
    assertEquals(43, MixedClass.staticOption);
    assertEquals(42, x.nonstaticOption);
  }

  @Test
  public void fillMixedFieldsNoInstanceGiven() {
    MixedClass x = new MixedClass();
    assertEquals(-1, MixedClass.staticOption);
    assertEquals(-1, x.nonstaticOption);
    Execution.fillOptions(MixedClass.class, new String[]{ "-option.nonstatic", "42", "-option.static", "43" });
    assertEquals(43, MixedClass.staticOption);
    assertEquals(-1, x.nonstaticOption);
  }
}
