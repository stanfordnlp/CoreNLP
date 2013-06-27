package edu.stanford.nlp.util;

import junit.framework.TestCase;

/**
 * Test for intervals
 *
 * @author Angel Chang
 */
public class IntervalTest extends TestCase {
  private String toHexString(int n)
  {
    return String.format("%08x", n);
  }

  public void testIntervalOverlaps() throws Exception
  {
    Interval<Integer> i1_10 = Interval.toInterval(1,10);
    Interval<Integer> i2_9 = Interval.toInterval(2,9);
    Interval<Integer> i5_10 = Interval.toInterval(5,10);
    Interval<Integer> i1_5 = Interval.toInterval(1,5);
    Interval<Integer> i1_15 = Interval.toInterval(1,15);
    Interval<Integer> i5_20 = Interval.toInterval(5,20);
    Interval<Integer> i10_20 = Interval.toInterval(10,20);
    Interval<Integer> i15_20 = Interval.toInterval(15,20);
    Interval<Integer> i1_10b = Interval.toInterval(1,10);

    assertTrue(i1_10.overlaps(i2_9));
    assertTrue(i1_10.overlaps(i5_10));
    assertTrue(i1_10.overlaps(i1_5));
    assertTrue(i1_10.overlaps(i1_15));
    assertTrue(i1_10.overlaps(i5_20));
    assertTrue(i1_10.overlaps(i10_20));
    assertFalse(i1_10.overlaps(i15_20));
    assertTrue(i1_10.overlaps(i1_10b));

    assertTrue(i2_9.overlaps(i1_10));
    assertTrue(i5_10.overlaps(i1_10));
    assertTrue(i1_5.overlaps(i1_10));
    assertTrue(i1_15.overlaps(i1_10));
    assertTrue(i5_20.overlaps(i1_10));
    assertTrue(i10_20.overlaps(i1_10));
    assertFalse(i15_20.overlaps(i1_10));
    assertTrue(i1_10b.overlaps(i1_10));

    int openFlags = Interval.INTERVAL_OPEN_BEGIN | Interval.INTERVAL_OPEN_END;
    Interval<Integer> i1_10_open = Interval.toInterval(1,10, openFlags);
    Interval<Integer> i2_9_open = Interval.toInterval(2,9, openFlags);
    Interval<Integer> i5_10_open = Interval.toInterval(5,10, openFlags);
    Interval<Integer> i1_5_open = Interval.toInterval(1,5, openFlags);
    Interval<Integer> i1_15_open = Interval.toInterval(1,15, openFlags);
    Interval<Integer> i5_20_open = Interval.toInterval(5,20, openFlags);
    Interval<Integer> i10_20_open = Interval.toInterval(10,20, openFlags);
    Interval<Integer> i15_20_open = Interval.toInterval(15,20, openFlags);
    Interval<Integer> i1_10b_open = Interval.toInterval(1,10, openFlags);

    assertTrue(i1_10_open.overlaps(i2_9_open));
    assertTrue(i1_10_open.overlaps(i5_10_open));
    assertTrue(i1_10_open.overlaps(i1_5_open));
    assertTrue(i1_10_open.overlaps(i1_15_open));
    assertTrue(i1_10_open.overlaps(i5_20_open));
    assertFalse(i1_10_open.overlaps(i10_20_open));
    assertFalse(i1_10_open.overlaps(i15_20_open));
    assertTrue(i1_10_open.overlaps(i1_10b_open));

    assertTrue(i2_9_open.overlaps(i1_10_open));
    assertTrue(i5_10_open.overlaps(i1_10_open));
    assertTrue(i1_5_open.overlaps(i1_10_open));
    assertTrue(i1_15_open.overlaps(i1_10_open));
    assertTrue(i5_20_open.overlaps(i1_10_open));
    assertFalse(i10_20_open.overlaps(i1_10_open));
    assertFalse(i15_20_open.overlaps(i1_10_open));
    assertTrue(i1_10b_open.overlaps(i1_10_open));
  }

  public void testIntervalContains() throws Exception
  {
    Interval<Integer> i1_10 = Interval.toInterval(1,10);
    Interval<Integer> i2_9 = Interval.toInterval(2,9);
    Interval<Integer> i5_10 = Interval.toInterval(5,10);
    Interval<Integer> i1_5 = Interval.toInterval(1,5);
    Interval<Integer> i1_15 = Interval.toInterval(1,15);
    Interval<Integer> i5_20 = Interval.toInterval(5,20);
    Interval<Integer> i10_20 = Interval.toInterval(10,20);
    Interval<Integer> i15_20 = Interval.toInterval(15,20);
    Interval<Integer> i1_10b = Interval.toInterval(1,10);

    assertTrue(i1_10.contains(i2_9));
    assertTrue(i1_10.contains(i5_10));
    assertTrue(i1_10.contains(i1_5));
    assertFalse(i1_10.contains(i1_15));
    assertFalse(i1_10.contains(i5_20));
    assertFalse(i1_10.contains(i10_20));
    assertFalse(i1_10.contains(i15_20));
    assertTrue(i1_10.contains(i1_10b));

    assertFalse(i2_9.contains(i1_10));
    assertFalse(i5_10.contains(i1_10));
    assertFalse(i1_5.contains(i1_10));
    assertTrue(i1_15.contains(i1_10));
    assertFalse(i5_20.contains(i1_10));
    assertFalse(i10_20.contains(i1_10));
    assertFalse(i15_20.contains(i1_10));
    assertTrue(i1_10b.contains(i1_10));

    int openFlags = Interval.INTERVAL_OPEN_BEGIN | Interval.INTERVAL_OPEN_END;
    Interval<Integer> i1_10_open = Interval.toInterval(1,10, openFlags);
    Interval<Integer> i2_9_open = Interval.toInterval(2,9, openFlags);
    Interval<Integer> i5_10_open = Interval.toInterval(5,10, openFlags);
    Interval<Integer> i1_5_open = Interval.toInterval(1,5, openFlags);
    Interval<Integer> i1_15_open = Interval.toInterval(1,15, openFlags);
    Interval<Integer> i5_20_open = Interval.toInterval(5,20, openFlags);
    Interval<Integer> i10_20_open = Interval.toInterval(10,20, openFlags);
    Interval<Integer> i15_20_open = Interval.toInterval(15,20, openFlags);
    Interval<Integer> i1_10b_open = Interval.toInterval(1,10, openFlags);

    assertTrue(i1_10_open.contains(i2_9_open));
    assertTrue(i1_10_open.contains(i5_10_open));
    assertTrue(i1_10_open.contains(i1_5_open));
    assertFalse(i1_10_open.contains(i1_15_open));
    assertFalse(i1_10_open.contains(i5_20_open));
    assertFalse(i1_10_open.contains(i10_20_open));
    assertFalse(i1_10_open.contains(i15_20_open));
    assertTrue(i1_10_open.contains(i1_10b_open));

    assertFalse(i2_9_open.contains(i1_10_open));
    assertFalse(i5_10_open.contains(i1_10_open));
    assertFalse(i1_5_open.contains(i1_10_open));
    assertTrue(i1_15_open.contains(i1_10_open));
    assertFalse(i5_20_open.contains(i1_10_open));
    assertFalse(i10_20_open.contains(i1_10_open));
    assertFalse(i15_20_open.contains(i1_10_open));
    assertTrue(i1_10b_open.contains(i1_10_open));

    int openClosedFlags = Interval.INTERVAL_OPEN_BEGIN;
    Interval<Integer> i1_10_openClosed = Interval.toInterval(1,10, openClosedFlags);
    Interval<Integer> i2_9_openClosed = Interval.toInterval(2,9, openClosedFlags);
    Interval<Integer> i5_10_openClosed = Interval.toInterval(5,10, openClosedFlags);
    Interval<Integer> i1_5_openClosed = Interval.toInterval(1,5, openClosedFlags);
//    Interval<Integer> i1_15_openClosed = Interval.toInterval(1,15, openClosedFlags);
//    Interval<Integer> i5_20_openClosed = Interval.toInterval(5,20, openClosedFlags);
//    Interval<Integer> i10_20_openClosed = Interval.toInterval(10,20, openClosedFlags);
//    Interval<Integer> i15_20_openClosed = Interval.toInterval(15,20, openClosedFlags);
    Interval<Integer> i1_10b_openClosed = Interval.toInterval(1,10, openClosedFlags);

    int closedOpenFlags = Interval.INTERVAL_OPEN_END;
    Interval<Integer> i1_10_closedOpen = Interval.toInterval(1,10, closedOpenFlags);
    Interval<Integer> i2_9_closedOpen = Interval.toInterval(2,9, closedOpenFlags);
    Interval<Integer> i5_10_closedOpen = Interval.toInterval(5,10, closedOpenFlags);
    Interval<Integer> i1_5_closedOpen = Interval.toInterval(1,5, closedOpenFlags);
//    Interval<Integer> i1_15_closedOpen = Interval.toInterval(1,15, closedOpenFlags);
//    Interval<Integer> i5_20_closedOpen = Interval.toInterval(5,20, closedOpenFlags);
//    Interval<Integer> i10_20_closedOpen = Interval.toInterval(10,20, closedOpenFlags);
//    Interval<Integer> i15_20_closedOpen = Interval.toInterval(15,20, closedOpenFlags);
    Interval<Integer> i1_10b_closedOpen = Interval.toInterval(1,10, closedOpenFlags);

    assertTrue(i1_10_closedOpen.contains(i2_9_openClosed));
    assertTrue(i1_10.contains(i2_9_openClosed));
    assertTrue(i1_10_openClosed.contains(i2_9_openClosed));

    assertTrue(i1_10_closedOpen.contains(i2_9_closedOpen));
    assertTrue(i1_10.contains(i2_9_closedOpen));
    assertTrue(i1_10_openClosed.contains(i2_9_closedOpen));

    assertFalse(i1_10_closedOpen.contains(i5_10_openClosed));
    assertTrue(i1_10.contains(i5_10_openClosed));
    assertTrue(i1_10_openClosed.contains(i5_10_openClosed));

    assertTrue(i1_10_closedOpen.contains(i5_10_closedOpen));
    assertTrue(i1_10.contains(i5_10_closedOpen));
    assertTrue(i1_10_openClosed.contains(i5_10_closedOpen));

    assertTrue(i1_10_closedOpen.contains(i1_5_openClosed));
    assertTrue(i1_10.contains(i1_5_openClosed));
    assertTrue(i1_10_openClosed.contains(i1_5_openClosed));

    assertTrue(i1_10_closedOpen.contains(i1_5_closedOpen));
    assertTrue(i1_10.contains(i1_5_closedOpen));
    assertFalse(i1_10_openClosed.contains(i1_5_closedOpen));

    assertTrue(i1_10_openClosed.contains(i1_10b_openClosed));
    assertFalse(i1_10_openClosed.contains(i1_10b_closedOpen));
    assertFalse(i1_10_closedOpen.contains(i1_10b_openClosed));
    assertTrue(i1_10_closedOpen.contains(i1_10b_closedOpen));

  }

  public void testIntervalRelations() throws Exception
  {
    Interval<Integer> i1_10 = Interval.toInterval(1,10);
    Interval<Integer> i2_9 = Interval.toInterval(2,9);
    Interval<Integer> i5_10 = Interval.toInterval(5,10);
    Interval<Integer> i1_5 = Interval.toInterval(1,5);
    Interval<Integer> i1_15 = Interval.toInterval(1,15);
    Interval<Integer> i5_20 = Interval.toInterval(5,20);
    Interval<Integer> i10_20 = Interval.toInterval(10,20);
    Interval<Integer> i15_20 = Interval.toInterval(15,20);
    Interval<Integer> i1_10b = Interval.toInterval(1,10);

    Interval.RelType rel = i1_10.getRelation(null);
    assertEquals(Interval.RelType.NONE, rel);
    int flags = i1_10.getRelationFlags(null);
    assertEquals(0, flags);

    rel = i1_10.getRelation(i2_9);
    assertEquals(Interval.RelType.CONTAIN, rel);
    flags = i1_10.getRelationFlags(i2_9);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_BEFORE | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_AFTER |
                 Interval.REL_FLAGS_INTERVAL_CONTAIN | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_10.getRelation(i1_5);
    assertEquals(Interval.RelType.CONTAIN, rel);
    flags = i1_10.getRelationFlags(i1_5);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_SAME | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_AFTER |
                 Interval.REL_FLAGS_INTERVAL_CONTAIN | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_10.getRelation(i1_15);
    assertEquals(Interval.RelType.INSIDE, rel);
    flags = i1_10.getRelationFlags(i1_15);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_SAME | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_BEFORE |
                 Interval.REL_FLAGS_INTERVAL_INSIDE | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_10.getRelation(i5_10);
    assertEquals(Interval.RelType.CONTAIN, rel);
    flags = i1_10.getRelationFlags(i5_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_BEFORE | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_SAME |
                 Interval.REL_FLAGS_INTERVAL_CONTAIN | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_10.getRelation(i5_20);
    assertEquals(Interval.RelType.OVERLAP, rel);
    flags = i1_10.getRelationFlags(i5_20);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_BEFORE | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_BEFORE |
                 Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_10.getRelation(i10_20);
    assertEquals(Interval.RelType.END_MEET_BEGIN, rel);
    flags = i1_10.getRelationFlags(i10_20);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_BEFORE | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_SAME | Interval.REL_FLAGS_EE_BEFORE |
                 Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_10.getRelation(i15_20);
    assertEquals(Interval.RelType.BEFORE, rel);
    flags = i1_10.getRelationFlags(i15_20);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_BEFORE | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_BEFORE | Interval.REL_FLAGS_EE_BEFORE |
                 Interval.REL_FLAGS_INTERVAL_BEFORE ),
                 toHexString(flags));

    rel = i1_10.getRelation(i1_10b);
    assertEquals(Interval.RelType.EQUAL, rel);
    flags = i1_10.getRelationFlags(i1_10b);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_SAME | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_SAME |
                 Interval.REL_FLAGS_INTERVAL_SAME | Interval.REL_FLAGS_INTERVAL_OVERLAP |
                 Interval.REL_FLAGS_INTERVAL_CONTAIN | Interval.REL_FLAGS_INTERVAL_INSIDE ),
                 toHexString(flags));

    ///////////////////////////////////////////////////

    rel = i2_9.getRelation(i1_10);
    assertEquals(Interval.RelType.INSIDE, rel);
    flags = i2_9.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_AFTER | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_BEFORE |
                 Interval.REL_FLAGS_INTERVAL_INSIDE | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_5.getRelation(i1_10);
    assertEquals(Interval.RelType.INSIDE, rel);
    flags = i1_5.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_SAME | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_BEFORE |
                 Interval.REL_FLAGS_INTERVAL_INSIDE | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i1_15.getRelation(i1_10);
    assertEquals(Interval.RelType.CONTAIN, rel);
    flags = i1_15.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_SAME | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_AFTER |
                 Interval.REL_FLAGS_INTERVAL_CONTAIN | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i5_10.getRelation(i1_10);
    assertEquals(Interval.RelType.INSIDE, rel);
    flags = i5_10.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_AFTER | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_SAME |
                 Interval.REL_FLAGS_INTERVAL_INSIDE | Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i5_20.getRelation(i1_10);
    assertEquals(Interval.RelType.OVERLAP, rel);
    flags = i5_20.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_AFTER | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_AFTER |
                 Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i10_20.getRelation(i1_10);
    assertEquals(Interval.RelType.BEGIN_MEET_END, rel);
    flags = i10_20.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_AFTER | Interval.REL_FLAGS_SE_SAME |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_AFTER |
                 Interval.REL_FLAGS_INTERVAL_OVERLAP ),
                 toHexString(flags));

    rel = i15_20.getRelation(i1_10);
    assertEquals(Interval.RelType.AFTER, rel);
    flags = i15_20.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_AFTER | Interval.REL_FLAGS_SE_AFTER |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_AFTER |
                 Interval.REL_FLAGS_INTERVAL_AFTER ),
                 toHexString(flags));

    rel = i1_10b.getRelation(i1_10);
    assertEquals(Interval.RelType.EQUAL, rel);
    flags = i1_10b.getRelationFlags(i1_10);
    assertEquals(toHexString(Interval.REL_FLAGS_SS_SAME | Interval.REL_FLAGS_SE_BEFORE |
                 Interval.REL_FLAGS_ES_AFTER | Interval.REL_FLAGS_EE_SAME |
                 Interval.REL_FLAGS_INTERVAL_SAME | Interval.REL_FLAGS_INTERVAL_OVERLAP |
                 Interval.REL_FLAGS_INTERVAL_CONTAIN | Interval.REL_FLAGS_INTERVAL_INSIDE ),
                 toHexString(flags));

  }
}
