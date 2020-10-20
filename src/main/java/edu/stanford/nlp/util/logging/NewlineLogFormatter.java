package edu.stanford.nlp.util.logging;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** Simply format and put a newline after each log message.
 *
 *  @author Heeyoung Lee
 */
public class NewlineLogFormatter extends Formatter {

  @Override
  public String format(LogRecord rec) {
    return formatMessage(rec) + '\n';
  }

}
