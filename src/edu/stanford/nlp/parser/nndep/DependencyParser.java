
/* 
*   @Author:  Danqi Chen
*   @Email:  danqi@cs.stanford.edu
*   @Created:  2014-08-25
*   @Last Modified:  2014-08-25
*/

package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;

public class DependencyParser {

  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);

    NNParser parser = new NNParser(props);

    if (props.containsKey("trainFile"))
      parser.train(props.getProperty("trainFile"), props.getProperty("devFile"), props.getProperty("model"),
          props.getProperty("embedFile"));

    if (props.containsKey("testFile"))
      parser.test(props.getProperty("testFile"), props.getProperty("model"), props.getProperty("outFile"));
  }

}
