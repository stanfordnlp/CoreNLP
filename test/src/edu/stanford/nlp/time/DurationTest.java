package edu.stanford.nlp.time;

import static org.junit.Assert.*;
import junit.framework.TestSuite;

import org.junit.Test;

import edu.stanford.nlp.time.SUTime;
import edu.stanford.nlp.time.SUTime.IsoDate;


public class DurationTest extends TestSuite {


	@Test
	public void testDurationContainsDuration(){
		SUTime.Range range1 = new SUTime.Range(new IsoDate(1990, 2, 1), new IsoDate(1990, 2, 28)); // 1.2.1990 - 28.2.1990
		SUTime.Range range2 = new SUTime.Range(new IsoDate(1990, 2, 3), new IsoDate(1990, 2, 25)); // 3.2.1990 - 25.2.1990
		SUTime.Range range3 = new SUTime.Range(new IsoDate(1990, 1, 3), new IsoDate(1990, 2, 25)); // 3.1.1990 - 25.2.1990
		SUTime.Range range4 = new SUTime.Range(new IsoDate(1990, 2, 3), new IsoDate(1990, 3, 25)); // 3.2.1990 - 25.3.1990

		assertEquals(range1.contains(range2), true); // 1-28. February contains 3-25 February
		
		assertEquals(range1.contains(range1), true); // 1-28. Feb. contains 1-28 Feb
		
		assertEquals(range2.contains(range1), false); //3-25  February contains not 1-28. February		
		
		assertEquals(range1.contains(range3), false); //1-28 Feb. contains not 3.1 - 25.2 (partially overlapping before)
		assertEquals(range3.contains(range1), false);
		
		assertEquals(range1.contains(range4), false); //1-28 Feb. contains not 3.2 - 25.3 (partially overlapping after)
		assertEquals(range1.contains(range4), false); 
	}
	
	@Test
	public void testDurationContainsTime(){
		
		SUTime.Range range1 = new SUTime.Range(new IsoDate(1990, 2, 1), new IsoDate(1990, 2, 28)); // 1.2.1990 - 28.2.1990
		
		assertEquals(range1.contains(new IsoDate(1990, 2, 1)), true); 
		assertEquals(range1.contains(new IsoDate(1990, 2, 2)), true);
		
		assertEquals(range1.contains(new IsoDate(1990, 1, 2)), false);
		assertEquals(range1.contains(new IsoDate(1990, 3, 1)), false);		
	}

}
