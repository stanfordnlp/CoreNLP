package edu.stanford.nlp.util.logging;

import org.slf4j.*;

/**
 * A handler for outputting to SLF4J rather than stderr.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unused")  // Called via reflection from RedwoodConfiguration
public class SLF4JHandler extends OutputHandler {

  @Override
  public void print(Object[] channel, String line) {
    // Parse the channels
    Class source = null;
    Redwood.Flag flag = Redwood.Flag.STDOUT;
    for (Object c : channel) {
      if (c instanceof Class) {
        source = (Class) c;
      } else if (c instanceof Redwood.Flag) {
        if (c != Redwood.Flag.FORCE) {
          flag = (Redwood.Flag) c;
        }
      }
    }

    // Get the logger
    Logger impl = null;
    if (channel.length == 0) {
      impl = LoggerFactory.getLogger("CoreNLP");
    } else if (channel[0] instanceof Class){
      impl = LoggerFactory.getLogger((Class) channel[0]);
    } else {
      impl = LoggerFactory.getLogger(channel[0].toString());
    }

    // Route the signal
    switch (flag) {
      case ERROR:
        impl.error(line);
        break;
      case WARN:
        impl.warn(line);
        break;
      case DEBUG:
        impl.debug(line);
        break;
      case STDOUT:
      case STDERR:
        impl.info(line);
        break;
      case FORCE:
        throw new IllegalStateException("Should not reach this switch case");
      default:
        throw new IllegalStateException("Unknown Redwood flag for slf4j integration: " + flag);
    }
  }
}
