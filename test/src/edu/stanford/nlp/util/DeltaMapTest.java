package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/**
 * @author Sebastian Riedel
 */
public class DeltaMapTest extends TestCase {

    private Map<Integer,Integer> originalMap;
    private Map<Integer,Integer> originalCopyMap;
    private Map<Integer,Integer> deltaCopyMap;
    private Map<Integer,Integer> deltaMap;
    private static final int BOUND3 = 100;
    private static final int BOUND2 = 90;
    private static final int BOUND4 = 110;
    private static final int BOUND1 = 10;

    @Override
    @SuppressWarnings("unchecked")
    protected void setUp(){
        originalMap = new HashMap<>();
        Random r = new Random();
        for (int i = 0; i < BOUND3; i++) {
          originalMap.put(i, r.nextInt(BOUND3));
        }
        originalCopyMap = new HashMap<>(originalMap);
        deltaCopyMap = new HashMap<>(originalMap);
        deltaMap = new DeltaMap(originalMap);
        // now make a lot of changes to deltaMap;
        // add and change some stuff
        for (int i = BOUND2; i < BOUND4; i++) {
          Integer rInt = r.nextInt(BOUND3);
            //noinspection unchecked
            deltaMap.put(i, rInt);
          deltaCopyMap.put(i, rInt);
        }
        // remove some stuff
        for (int i = 0; i < BOUND1; i++) {
          Integer rInt = r.nextInt(BOUND4);
          deltaMap.remove(rInt);
          deltaCopyMap.remove(rInt);
        }
        // set some stuff to null
        for (int i = 0; i < BOUND1; i++) {
          Integer rInt = r.nextInt(BOUND4);
          deltaMap.put(rInt, null);
          deltaCopyMap.put(rInt, null);
        }

    }

    public void testOriginalPreserved(){
        assertEquals(originalCopyMap,originalMap);
    }

    public void testDeltaAccurate(){
        assertEquals(deltaCopyMap, deltaMap);
    }

}
