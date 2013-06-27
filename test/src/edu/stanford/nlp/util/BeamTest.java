package edu.stanford.nlp.util;

import junit.framework.TestCase;

/**
 * @author Sebastian Riedel
 */
public class BeamTest extends TestCase {
    protected Beam<ScoredObject<String>> beam;
    protected ScoredObject<String> object1;
    protected ScoredObject<String> object0;
    protected ScoredObject<String> object2;
    protected ScoredObject<String> object3;

    @Override
    protected void setUp() {
        beam = new Beam<ScoredObject<String>>(2, ScoredComparator.ASCENDING_COMPARATOR);
        object1 = new ScoredObject<String>("1", 1.0);
        object2 = new ScoredObject<String>("2", 2.0);
        object3 = new ScoredObject<String>("3", 3.0);
        object0 = new ScoredObject<String>("0", 0.0);
        beam.add(object1);
        beam.add(object2);
        beam.add(object3);
        beam.add(object0);
    }

    public void testSize(){
        assertEquals(2,beam.size());    
    }

    public void testContent(){
        assertTrue(beam.contains(object2));
        assertTrue(beam.contains(object3));
        assertFalse(beam.contains(object1));
        assertFalse(beam.contains(object0));
    }

}
