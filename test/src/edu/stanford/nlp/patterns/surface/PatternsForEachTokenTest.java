package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.util.StringUtils;
import junit.framework.TestCase;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;

/**
 * Created by sonalg on 10/8/14.
 */
public class PatternsForEachTokenTest extends TestCase {

  public void testCreatingAndInserting() throws SQLException, IOException, ClassNotFoundException {

    Properties props = StringUtils.argsToPropertiesWithResolve(new String[]{"-props","/home/sonalg/javanlp/test.props"});

    props.setProperty("tableName","tempPatsTable");
    props.setProperty("useDB","true");
    props.setProperty("deleteExisiting","true");
    props.setProperty("createTable","true");
    System.out.println(props.toString());
    PatternsForEachToken p = new PatternsForEachToken(props);
    p.addPattern("sent1",1, new HashSet<Integer>());
    System.out.println(p.getPatterns("sent1",1));
  }
}
