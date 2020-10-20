package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;
import junit.framework.TestCase;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by sonalg on 10/8/14.
 */
public class PatternsForEachTokenTest extends TestCase {

  @Test
  public void testCreatingAndInserting() throws SQLException, IOException, ClassNotFoundException {
/*
  //Uncomment for testing

    Properties props = new Properties();

    props.setProperty("tableName","tempPatsTable");
    props.setProperty("useDBForTokenPatterns","true");
    props.setProperty("deleteExisiting","true");
    props.setProperty("createTable","true");
    props.putAll(StringUtils.argsToPropertiesWithResolve(new String[]{"-props","/home/sonalg/javanlp/test.props"}));

    System.out.println(props.toString());

    PatternsForEachToken p = new PatternsForEachToken(props);

    Set<Integer> pats = new HashSet<Integer>();
    pats.add(345);

//    p.addPatterns("sent1", 1, pats);
//
//    pats.add(466);
//    p.addPatterns("sent1", 2, pats);
//
//    pats.add(455);
//    p.addPatterns("sent1", 1, pats);
//
//    assertTrue(p.containsSentId("sent1"));

//    Map<Integer, Set<Integer>> pt = p.getPatternsForAllTokens("sent1");
//    assert pt.size() == 2;
//    assert pt.get(1).size() == 3;

    Map<String, Map<Integer, Set<Integer>>> sentpats = new HashMap<String, Map<Integer, Set<Integer>>>();
    Map<Integer, Set<Integer>> pats2 = new HashMap<Integer, Set<Integer>>();
    pats2.put(1, CollectionUtils.asSet(new Integer[]{345, 456}));
    pats2.put(2, CollectionUtils.asSet(new Integer[]{3451, 4561}));
    sentpats.put("sent2",pats2);

    Map<Integer, Set<Integer>> pats3 = new HashMap<Integer, Set<Integer>>();
    pats3.put(1, CollectionUtils.asSet(new Integer[]{34511, 45611}));
    pats3.put(2, CollectionUtils.asSet(new Integer[]{345111, 456111}));
    sentpats.put("sent3",pats3);

    p.addPatterns(sentpats);

    Map<Integer, Set<Integer>> pts = p.getPatternsForAllTokens("sent2");
    assert pts.size() == 2;
    assert pts.get(1).size() == 2 : "failed! ";

    pts.get(1).add(323);
    p.addPatterns("sent2", pts);
    Map<Integer, Set<Integer>> ptsup = p.getPatternsForAllTokens("sent2");
    assert ptsup.get(1).size() == 3;

    ConcurrentHashIndex<SurfacePattern> index = new ConcurrentHashIndex<SurfacePattern>();
    p.savePatternIndexInDB(index);
*/
  }
}
