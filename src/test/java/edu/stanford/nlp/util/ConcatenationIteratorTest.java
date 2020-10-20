package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author Sebastian Riedel
 */
public class ConcatenationIteratorTest extends TestCase {

    public void testIterator(){
        Collection<String> c1 = Collections.singleton("a");
        Collection<String> c2 = Collections.singleton("b");
        Iterator<String> i = new ConcatenationIterator<>(c1.iterator(), c2.iterator());
        assertEquals("a",i.next());
        assertEquals("b",i.next());
        assertFalse(i.hasNext());
    }
}
