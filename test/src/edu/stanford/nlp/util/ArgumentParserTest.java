package edu.stanford.nlp.util;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

/**
 * A small test for the {@link ArgumentParser} class for loading command line options
 *
 * @author Gabor Angeli
 */
public class ArgumentParserTest {

  public static class StaticClass {
    @ArgumentParser.Option(name="option.static")
    public static int staticOption = -1;

    private StaticClass() { }
  }

  public static class NonstaticClass {
    @ArgumentParser.Option(name="option.nonstatic")
    public int staticOption = -1;
  }

  public static class MixedClass {
    @ArgumentParser.Option(name="option.static")
    public static int staticOption = -1;
    @ArgumentParser.Option(name="option.nonstatic")
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
    ArgumentParser.fillOptions(StaticClass.class, "-option.static", "42");
    assertEquals(42, StaticClass.staticOption);
  }

  @Test
  public void testFillStaticFieldFromProperties() {
    assertEquals(-1, StaticClass.staticOption);
    Properties props = new Properties();
    props.setProperty("option.static", "42");
    ArgumentParser.fillOptions(StaticClass.class, props);
    assertEquals(42, StaticClass.staticOption);
  }

  @Test
  public void fillNonstaticField() {
    NonstaticClass x = new NonstaticClass();
    assertEquals(-1, x.staticOption);
    ArgumentParser.fillOptions(x, "-option.nonstatic", "42");
    assertEquals(42, x.staticOption);
  }

  @Test
  public void fillNonstaticFieldFromProperties() {
    NonstaticClass x = new NonstaticClass();
    assertEquals(-1, x.staticOption);
    Properties props = new Properties();
    props.setProperty("option.nonstatic", "42");
    ArgumentParser.fillOptions(x, props);
    assertEquals(42, x.staticOption);
  }

  @Test
  public void fillMixedFieldsInstanceGiven() {
    MixedClass x = new MixedClass();
    assertEquals(-1, MixedClass.staticOption);
    assertEquals(-1, x.nonstaticOption);
    ArgumentParser.fillOptions(x, "-option.nonstatic", "42", "-option.static", "43");
    assertEquals(43, MixedClass.staticOption);
    assertEquals(42, x.nonstaticOption);
  }

  @Test
  public void fillMixedFieldsNoInstanceGiven() {
    MixedClass x = new MixedClass();
    assertEquals(-1, MixedClass.staticOption);
    assertEquals(-1, x.nonstaticOption);
    ArgumentParser.fillOptions(MixedClass.class, "-option.nonstatic", "42", "-option.static", "43");
    assertEquals(43, MixedClass.staticOption);
    assertEquals(-1, x.nonstaticOption);
  }

  /** Check that command-line arguments override properties. */
  @Test
  public void checkOptionsOverrideProperties() {
    NonstaticClass x = new NonstaticClass();
    assertEquals(-1, x.staticOption);
    Properties props = new Properties();
    props.setProperty("option.nonstatic", "78");
    ArgumentParser.fillOptions(x, props, "-option.nonstatic", "42");
    assertEquals(42, x.staticOption);
  }

}
