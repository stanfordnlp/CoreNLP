package edu.stanford.nlp.util.logging;


import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.util.Pair;

/**
 * An outputter that writes to Java Util Logging logs.
 *
 * @author Gabor Angeli
 */
public class JavaUtilLoggingHandler extends OutputHandler {

  @Override
  public void print(Object[] channel, String line) {
    // Parse the channels
    Pair<String, Redwood.Flag> pair = getSourceStringAndLevel(channel);

    // Get the logger
    Logger impl = Logger.getLogger(pair.first());

    // Route the signal
    switch (pair.second()) {
      case ERROR:
        impl.log(Level.SEVERE, line);
        break;
      case WARN:
        impl.log(Level.WARNING, line);
        break;
      case DEBUG:
        impl.log(Level.FINE, line);
        break;
      case STDOUT:
      case STDERR:
        impl.info(line);
        break;
      case FORCE:
        throw new IllegalStateException("Should not reach this switch case");
      // Not possible as now enum
      // default:
      //   throw new IllegalStateException("Unknown Redwood flag for j.u.l integration: " + flag);
    }
  }

}
