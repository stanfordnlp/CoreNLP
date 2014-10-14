package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.util.StringUtils;
import junit.framework.TestCase;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Created by sonalg on 10/8/14.
 */
public class PatternsForEachTokenTest extends TestCase {

  public void testCreatingAndInserting() throws SQLException, IOException, ClassNotFoundException {

    Properties props = new Properties();

    props.setProperty("tableName","tempPatsTable");
    props.setProperty("useDB","true");
    props.setProperty("deleteExisiting","true");
    props.setProperty("createTable","true");
    props.putAll(StringUtils.argsToPropertiesWithResolve(new String[]{"-props","/home/sonalg/javanlp/test.props"}));
    System.out.println(props.toString());
    PatternsForEachToken p = new PatternsForEachToken(props);
    Set<Integer> pats = new HashSet<Integer>();
    pats.add(345);
    p.addPattern("sent1",1, pats);
    pats.add(466);
    p.addPattern("sent1",2, pats);
    System.out.println("Done adding pattern");
    System.out.println(p.getPatterns("sent1",1));
  }
}
