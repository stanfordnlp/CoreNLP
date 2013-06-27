package edu.stanford.nlp.tagger.maxent;

import junit.framework.TestCase;
//import edu.stanford.nlp.tagger.maxent.TTags;

import java.util.Set;
import edu.stanford.nlp.util.Generics;

public class TTagsTest extends TestCase {
    private TTags tt;

    @Override
    protected void setUp() {
	tt = new TTags();
    }

    public void testUniqueness() {
	int a = tt.add("one");
	int b = tt.add("two");
	assertTrue(a != b);
    }

    public void testSameness() {
	int a = tt.add("goat");
	int b = tt.add("goat");
	assertEquals(a, b);
    }

    public void testPreservesString() {
	int a = tt.add("monkey");
	String s = tt.getTag(a);
	assertEquals(s, "monkey");
    }

    public void testPreservesIndex() {
	int a = tt.add("spunky");
	int b = tt.getIndex("spunky");
	assertEquals(a, b);
    }

    public void testCanCount() {
	int s = tt.getSize();
	tt.add("asdfdsaefasfdsaf");
	int s2 = tt.getSize();
	assertEquals(s + 1, s2);
    }

    public void testHoldsLotsOfStuff() {
	try {
	    for(int i = 0; i < 1000; i++) {
		tt.add("fake" + Integer.toString(i));
	    }
	} catch(Exception e) {
	    fail("couldn't put lots of stuff in:" + e.getMessage());
	}
    }

    public void testClosed() {
	tt.add("java");

	assertFalse(tt.isClosed("java"));
	tt.markClosed("java");
	assertTrue(tt.isClosed("java"));
    }

    public void testSerialization() {
	for(int i = 0; i < 100; i++) {
	    tt.add("fake" + Integer.toString(i));
	}
	tt.markClosed("fake44");
	tt.add("boat");
	tt.save("testoutputfile", Generics.<String, Set<String>>newHashMap());
	TTags t2 = new TTags();
	t2.read("testoutputfile");
	assertEquals(tt.getSize(), t2.getSize());
	assertEquals(tt.getIndex("boat"), t2.getIndex("boat"));
	assertEquals(t2.getTag(tt.getIndex("boat")), "boat");

	assertFalse(t2.isClosed("fake43"));
	assertTrue(t2.isClosed("fake44"));
	/* java=lame */
	(new java.io.File("testoutputfile")).delete();
    }
}
