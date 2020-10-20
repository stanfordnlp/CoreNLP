
package edu.stanford.nlp.util.logging;

import java.util.Properties;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class StanfordRedwoodConfiguration extends RedwoodConfiguration {

  /**
   * Private constructor to prevent use of "new StanfordRedwoodConfiguration()"
   */
  private StanfordRedwoodConfiguration() {
    super();
  }

  /**
   * Configures the Redwood logger using a reasonable set of defaults,
   * which can be overruled by the supplied Properties file.
   *
   * @param props The properties file to overrule or augment the default configuration
   */
  public static void apply(Properties props){
    //--Tweak Properties
    //(output to stderr)
    if (props.getProperty("log.output") == null) {
      props.setProperty("log.output", "stderr");
    }
    //(capture system streams)
    if (props.getProperty("log.captureStderr") == null) {
      props.setProperty("log.captureStderr", "true");
    }
    //(apply properties)
    RedwoodConfiguration.apply(props);

    //--Strange Tweaks
    //(adapt legacy logging systems)
    JavaUtilLoggingAdaptor.adapt();
  }

  /**
   * Set up the Redwood logger with Stanford's default configuration
   */
  public static void setup(){
    apply(new Properties());
  }

  public static void minimalSetup(){
    Properties props = new Properties();
    props.setProperty("log.output", "stderr");
    RedwoodConfiguration.apply(props);
  }

}
