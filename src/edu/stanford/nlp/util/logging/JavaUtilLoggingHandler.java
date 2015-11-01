package edu.stanford.nlp.util.logging;


import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An outputter that writes to Java Util Logging logs.
 *
 * @author Gabor Angeli
 */
public class JavaUtilLoggingHandler extends OutputHandler {
  @Override
  public void print(Object[] channel, String line) {
    // Parse the channels
    Class source = null;  // The class the message is coming from
    Object backupSource = null;  // Another identifier for the message
    Redwood.Flag flag = Redwood.Flag.STDOUT;
    for (Object c : channel) {
      if (c instanceof Class) {
        source = (Class) c;  // This is a class the message is coming from
      } else if (c instanceof Redwood.Flag) {
        if (c != Redwood.Flag.FORCE) {  // This is a Redwood flag
          flag = (Redwood.Flag) c;
        }
      } else {
        backupSource = c;  // This is another "source" for the log message
      }
    }

    // Get the logger
    Logger impl = null;
    if (source != null) {
      impl = Logger.getLogger(source.getName());
    } else if (backupSource != null) {
      impl = Logger.getLogger(backupSource.toString());
    } else {
      impl = Logger.getLogger("CoreNLP");

    }

    // Route the signal
    switch (flag) {
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
      default:
        throw new IllegalStateException("Unknown Redwood flag for slf4j integration: " + flag);
    }

  }
}
