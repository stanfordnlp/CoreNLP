package edu.stanford.nlp.time;

import junit.framework.TestSuite;
import org.joda.time.Period;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class JodaTimeTests extends TestSuite {

  @Test
  public void timexDurationValue(){
    JodaTimeUtils.ConversionOptions opts = new JodaTimeUtils.ConversionOptions();
    //--2 Decades
    assertEquals("P2E", JodaTimeUtils.timexDurationValue(Period.years(20), opts));
    opts.forceUnits = new String[]{"Y"};
    assertEquals("P20Y", JodaTimeUtils.timexDurationValue(Period.years(20), opts));
    opts.forceUnits = new String[]{"L"};
    assertEquals("P2E", JodaTimeUtils.timexDurationValue(Period.years(20), opts));
    opts.approximate = true;
    assertEquals("PXE", JodaTimeUtils.timexDurationValue(Period.years(20), opts));
    opts.forceUnits = new String[]{"Y"};
    assertEquals("PXY", JodaTimeUtils.timexDurationValue(Period.years(20), opts));
    opts = new JodaTimeUtils.ConversionOptions();
    //--Quarters
    assertEquals("P2Q", JodaTimeUtils.timexDurationValue(Period.months(6), opts));
    opts.forceUnits = new String[]{"M"};
    assertEquals("P6M", JodaTimeUtils.timexDurationValue(Period.months(6), opts));
    opts.approximate = true;
    assertEquals("PXM", JodaTimeUtils.timexDurationValue(Period.months(6), opts));
    opts = new JodaTimeUtils.ConversionOptions();
    //--Others go here...
  }
}
