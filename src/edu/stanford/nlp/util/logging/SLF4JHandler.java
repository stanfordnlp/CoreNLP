package edu.stanford.nlp.util.logging;

import edu.stanford.nlp.util.Pair;
import org.slf4j.*;

import java.util.Collections;
import java.util.List;

/**
 * A handler for outputting to SLF4J rather than stderr.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unused")  // Called via reflection from RedwoodConfiguration
public class SLF4JHandler extends OutputHandler {


  private Pair<Logger, Redwood.Flag> getLoggerAndLevel(Object[] channel) {
    // Parse the channels
    Class source = null;  // The class the message is coming from
    Object backupSource = null;  // Another identifier for the message
    Redwood.Flag flag = Redwood.Flag.STDOUT;
    for (Object c : ((channel == null) ? new Object[0] : channel)) {
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
    Logger impl;
    if (source != null) {
      impl = LoggerFactory.getLogger(source);
    } else if (backupSource != null) {
      impl = LoggerFactory.getLogger(backupSource.toString());
    } else {
      impl = LoggerFactory.getLogger("CoreNLP");
    }

    return Pair.makePair(impl, flag);

  }

  /**
   * Override the raw handle method, as potentially we are dropping log levels in SLF4J
   * and we do not want to render the resulting message.
   *
   * @param record The record to handle.
   * @return Nothing -- this is the leaf of a tree.
   */
  @SuppressWarnings("unchecked")
  @Override
  public List<Redwood.Record> handle(Redwood.Record record) {
    // Get the implementing SLF4J logger
    Pair<Logger, Redwood.Flag> loggerAndLevel = getLoggerAndLevel(record.channels());

    // Potentially short-circuit
    switch (loggerAndLevel.second) {
      case FORCE:
        break;  // Always pass it on if explicitly forced
      case ERROR:
        if (!loggerAndLevel.first.isErrorEnabled()) {
          return Collections.EMPTY_LIST;
        }
        break;
      case WARN:
        if (!loggerAndLevel.first.isWarnEnabled()) {
          return Collections.EMPTY_LIST;
        }
        break;
      case DEBUG:
        if (!loggerAndLevel.first.isDebugEnabled()) {
          return Collections.EMPTY_LIST;
        }
        break;
      default:
        if (!loggerAndLevel.first.isInfoEnabled()) {
          return Collections.EMPTY_LIST;
        }
        break;
    }
    return super.handle(record);
  }

  @Override
  public void print(Object[] channel, String line) {
    // Get the implementing SLF4J logger
    Pair<Logger, Redwood.Flag> loggerAndLevel = getLoggerAndLevel(channel);

    // Format the line
    if (line.length() > 0 && line.charAt(line.length() - 1) == '\n') {
      line = line.substring(0, line.length() - 1);
    }

    // Route the signal
    switch (loggerAndLevel.second) {
      case ERROR:
        loggerAndLevel.first.error(line);
        break;
      case WARN:
        loggerAndLevel.first.warn(line);
        break;
      case DEBUG:
        loggerAndLevel.first.debug(line);
        break;
      case STDOUT:
      case STDERR:
        loggerAndLevel.first.info(line);
        break;
      case FORCE:
        throw new IllegalStateException("Should not reach this switch case");
      default:
        throw new IllegalStateException("Unknown Redwood flag for slf4j integration: " + loggerAndLevel.second);
    }
  }
}
