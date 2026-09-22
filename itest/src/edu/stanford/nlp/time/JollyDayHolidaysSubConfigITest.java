package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks that {@link JollyDayHolidays} collects holidays from nested configurations.
 *
 * <p>A jollyday calendar may nest {@code SubConfigurations} inside a configuration, usually
 * to hold regional holidays under a national one.
 * {@code JollyDayHolidays.getAllHolidays(Configuration, Set)} recurses into them, and every
 * holiday it finds is bound into the environment under the {@code JH_} prefix so the rules
 * can refer to it.
 *
 * <p>The calendar SUTime ships, Holidays_sutime.xml, is flat, so nothing else exercises the
 * recursion. Anyone pointing {@code sutime.binder.1.xml} at their own calendar can nest, and
 * the tests here cover that. The calendars are written to temporary files rather than kept
 * as test resources, so the nesting under test is visible in one place.
 */
public class JollyDayHolidaysSubConfigITest {

  private static final String HEADER =
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<tns:Configuration hierarchy=\"test\" description=\"Test\"\n"
          + "    xmlns:tns=\"http://www.example.org/Holiday\"\n"
          + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
          + "    xsi:schemaLocation=\"http://www.example.org/Holiday /Holiday.xsd\">\n";

  /** A calendar with one top-level holiday and, optionally, nested configurations. */
  private static Path calendar(String name, String topLevel, String... subConfigurations) {
    StringBuilder xml = new StringBuilder(HEADER);
    xml.append("  <tns:Holidays>\n").append(topLevel).append("  </tns:Holidays>\n");
    for (String sub : subConfigurations) {
      xml.append(sub);
    }
    xml.append("</tns:Configuration>\n");
    try {
      Path file = Files.createTempFile(name, ".xml");
      file.toFile().deleteOnExit();
      Files.write(file, xml.toString().getBytes(StandardCharsets.UTF_8));
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String fixed(int month, int day, String key) {
    return "    <tns:Fixed month=\"" + monthName(month) + "\" day=\"" + day
            + "\" descriptionPropertiesKey=\"" + key + "\"/>\n";
  }

  private static String monthName(int month) {
    return new String[] { "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
            "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER" }[month - 1];
  }

  private static String subConfiguration(String hierarchy, String holidays) {
    return "  <tns:SubConfigurations hierarchy=\"" + hierarchy + "\" description=\"" + hierarchy
            + "\">\n    <tns:Holidays>\n" + holidays + "    </tns:Holidays>\n"
            + "  </tns:SubConfigurations>\n";
  }

  /** Load a calendar and return the environment its holidays were bound into. */
  private static Env bind(Path calendar) {
    Properties props = new Properties();
    props.setProperty("sutime.binder.1.xml", calendar.toAbsolutePath().toString());
    props.setProperty("sutime.binder.1.pathtype", "file");
    JollyDayHolidays binder = new JollyDayHolidays();
    binder.init("sutime.binder.1.", props);
    Env env = TokenSequencePattern.getNewEnv();
    binder.bind(env);
    return env;
  }

  private static SUTime.Time holiday(Env env, String key) {
    Object value = env.get("JH_" + key);
    return (value instanceof SUTime.Time) ? (SUTime.Time) value : null;
  }

  @Test
  public void testHolidaysInNestedConfigurationsAreBound() {
    Env env = bind(calendar("nested",
            fixed(1, 1, "TOP_LEVEL"),
            subConfiguration("region-a", fixed(3, 3, "REGION_A")),
            subConfiguration("region-b", fixed(4, 4, "REGION_B"))));
    assertNotNull("top-level holiday missing", holiday(env, "TOP_LEVEL"));
    assertNotNull("holiday from the first sub-configuration missing", holiday(env, "REGION_A"));
    assertNotNull("holiday from the second sub-configuration missing", holiday(env, "REGION_B"));
  }

  @Test
  public void testNestingRecursesMoreThanOneLevel() {
    // A sub-configuration may itself contain sub-configurations.
    String inner = subConfiguration("inner", fixed(6, 6, "INNER"));
    String outer = "  <tns:SubConfigurations hierarchy=\"outer\" description=\"outer\">\n"
            + "    <tns:Holidays>\n" + fixed(5, 5, "OUTER") + "    </tns:Holidays>\n"
            + inner.replace("  <tns:SubConfigurations", "    <tns:SubConfigurations")
                   .replace("  </tns:SubConfigurations>", "    </tns:SubConfigurations>")
            + "  </tns:SubConfigurations>\n";
    Env env = bind(calendar("deep", fixed(1, 1, "TOP_LEVEL"), outer));
    assertNotNull(holiday(env, "TOP_LEVEL"));
    assertNotNull(holiday(env, "OUTER"));
    assertNotNull("holiday two levels down missing", holiday(env, "INNER"));
  }

  @Test
  public void testTopLevelHolidaysResolveToRealDates() {
    Env env = bind(calendar("dates",
            fixed(1, 1, "TOP_LEVEL"),
            subConfiguration("region-a", fixed(3, 3, "REGION_A"))));
    SUTime.Time reference = new SUTime.IsoDate(2017, 6, 15);
    assertEquals("2017-01-01", holiday(env, "TOP_LEVEL").resolve(reference, 0).toISOString());
  }

  @Test
  public void testNestedHolidaysResolveToRealDates() {
    // A nested holiday is computed against the branch it was declared in: the manager is
    // asked for that hierarchy, not just the top level.
    Env env = bind(calendar("dates",
            fixed(1, 1, "TOP_LEVEL"),
            subConfiguration("region-a", fixed(3, 3, "REGION_A")),
            subConfiguration("region-b", fixed(4, 4, "REGION_B"))));
    SUTime.Time reference = new SUTime.IsoDate(2017, 6, 15);
    assertEquals("2017-03-03", holiday(env, "REGION_A").resolve(reference, 0).toISOString());
    assertEquals("2017-04-04", holiday(env, "REGION_B").resolve(reference, 0).toISOString());
  }

  @Test
  public void testHolidaysNestedTwoLevelsDeepResolve() {
    String inner = subConfiguration("inner", fixed(6, 6, "INNER"));
    String outer = "  <tns:SubConfigurations hierarchy=\"outer\" description=\"outer\">\n"
            + "    <tns:Holidays>\n" + fixed(5, 5, "OUTER") + "    </tns:Holidays>\n"
            + inner.replace("  <tns:SubConfigurations", "    <tns:SubConfigurations")
                   .replace("  </tns:SubConfigurations>", "    </tns:SubConfigurations>")
            + "  </tns:SubConfigurations>\n";
    Env env = bind(calendar("deepdates", fixed(1, 1, "TOP_LEVEL"), outer));
    SUTime.Time reference = new SUTime.IsoDate(2017, 6, 15);
    assertEquals("2017-05-05", holiday(env, "OUTER").resolve(reference, 0).toISOString());
    assertEquals("2017-06-06", holiday(env, "INNER").resolve(reference, 0).toISOString());
  }

  @Test
  public void testAFlatCalendarStillWorks() {
    // The shipped calendar has no nesting, so this is the shape everything else uses.
    Env env = bind(calendar("flat", fixed(1, 1, "TOP_LEVEL") + fixed(2, 2, "SECOND")));
    assertNotNull(holiday(env, "TOP_LEVEL"));
    assertNotNull(holiday(env, "SECOND"));
    assertNull("nothing should be bound for a key the calendar does not define",
            holiday(env, "REGION_A"));
  }

  @Test
  public void testEveryBoundHolidayUsesThePrefix() {
    Env env = bind(calendar("prefix",
            fixed(1, 1, "TOP_LEVEL"),
            subConfiguration("region-a", fixed(3, 3, "REGION_A"))));
    assertTrue(env.get("JH_REGION_A") instanceof SUTime.Time);
    assertNull("the unprefixed name must not be bound", env.get("REGION_A"));
  }

}
