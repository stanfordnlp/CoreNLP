
package edu.stanford.nlp.util.logging;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.logging.Redwood.Record;

/**
 * A log message handler designed for filtering.  This is a convenience class to handle the common case of filtering messages.
 * 
 * @author David McClosky
 */
public abstract class BooleanLogRecordHandler extends LogRecordHandler {

  /**
   * For BooleanLogRecordHandler, you should leave this alone and implement propagateRecord instead.
   */
  public List<Record> handle(Record record) {
    boolean keep = propagateRecord(record);
    if (keep) {
      ArrayList<Record> records = new ArrayList<>();
      records.add(record);
      return records;
    } else {
      return LogRecordHandler.EMPTY;
    }
  }

  /**
   * Given a record, return true if it should be propagated to later handlers.
   */
  public abstract boolean propagateRecord(Record record);
}
