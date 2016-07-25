package edu.stanford.nlp.time;

import edu.stanford.nlp.util.Pair;
import junit.framework.TestCase;
import org.joda.time.Partial;
import org.junit.Test;

/**
 * Tests basic SUTime operations
 *
 * @author Angel Chang
 */
public class SUTimeTest extends TestCase {
  void resolveAndCheckRange(String message, SUTime.Temporal t, SUTime.Time anchor, String expected) {
    SUTime.Temporal res = t.resolve(anchor);
    SUTime.Range range = res.getRange();
    assertEquals(message, expected, range.toISOString());
  }

  @Test
  public void testResolveDowToDay() {
    Partial p = new Partial(JodaTimeUtils.standardISOWeekFields, new int[]{2016,1,1,0,0,0,0});
    assertEquals("[year=2016, weekOfWeekyear=1, dayOfWeek=1, hourOfDay=0, minuteOfHour=0, secondOfMinute=0, millisOfSecond=0]", p.toString());

    Partial p2 = JodaTimeUtils.resolveDowToDay(p);
    assertEquals("2016-01-04T00:00:00.000", p2.toString());
  }

  @Test
  public void testNext() {
    SUTime.Time anchorTime = new SUTime.IsoDate(2016, 6, 19); // Sunday

    Pair<SUTime.Temporal, String>[] testPairs = new Pair[]{
      Pair.makePair(SUTime.MONDAY, "2016-06-20/2016-06-20"),
      Pair.makePair(SUTime.TUESDAY, "2016-06-21/2016-06-21"),
      Pair.makePair(SUTime.WEDNESDAY, "2016-06-22/2016-06-22"),
      Pair.makePair(SUTime.THURSDAY, "2016-06-23/2016-06-23"),
      Pair.makePair(SUTime.FRIDAY, "2016-06-24/2016-06-24"),
      Pair.makePair(SUTime.SATURDAY, "2016-06-25/2016-06-25"),
      Pair.makePair(SUTime.SUNDAY, "2016-06-26/2016-06-26"),

      Pair.makePair(SUTime.MORNING, "2016-06-20T06:00:00.000/2016-06-20T12:00"),
      Pair.makePair(SUTime.AFTERNOON, "2016-06-20T12:00:00.000/PT6H"),  // TODO: Check this...
      Pair.makePair(SUTime.EVENING, "2016-06-20T18:00:00.000/PT2H"),    // TODO: Check this...
      Pair.makePair(SUTime.NIGHT, "2016-06-20T14:00:00.000/2016-06-21T00:00:00.000"),

      Pair.makePair(SUTime.DAY, "2016-06-20/2016-06-20"),
      Pair.makePair(SUTime.WEEK, "2016-06-20/2016-06-26"),
      Pair.makePair(SUTime.MONTH, "2016-07-01/2016-07-31"),
      Pair.makePair(SUTime.MONTH.multiplyBy(3), "2016-06-19/2016-12-19"),
      //Pair.makePair(SUTime.QUARTER, "2016-07-01/2016-07-31"),  // TODO: Fix!!!!
      Pair.makePair(SUTime.YEAR, "2017-01-01/2017-12-31"),

      Pair.makePair(SUTime.WINTER, "2017-12-01/2017-03"),
      Pair.makePair(SUTime.SUMMER, "2017-06-01/2017-09"),
      Pair.makePair(SUTime.SPRING, "2017-03-01/2017-06"),
      Pair.makePair(SUTime.FALL, "2017-09-01/2017-12"),
    };

    for (int i = 0; i < testPairs.length; i++) {
      Pair<SUTime.Temporal, String> p = testPairs[i];
      SUTime.RelativeTime rel1 = new SUTime.RelativeTime(SUTime.TIME_REF, SUTime.TemporalOp.NEXT, p.first);
      resolveAndCheckRange("Next for " + p.first.toString() + " (" + i + ")", rel1, anchorTime, p.second);
    }
  }
}
