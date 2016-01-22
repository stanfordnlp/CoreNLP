
package edu.stanford.nlp.util.logging;

import java.util.List;

import edu.stanford.nlp.util.logging.Redwood.Record;

/**
 * Basic support for filtering records via LogFilter objects.  Can be used in both conjunctive and disjunctive mode.
 *
 * @author David McClosky
 */
public class FilterHandler extends BooleanLogRecordHandler {
  private List<LogFilter> filters;
  private boolean disjunctiveMode;
  
  public FilterHandler(List<LogFilter> filters, boolean disjunctiveMode) {
    this.filters = filters;
    this.disjunctiveMode = disjunctiveMode;
  }
    
  @Override
  public boolean propagateRecord(Record record) {
    for (LogFilter filter : filters) {
      boolean match = filter.matches(record);
      if (match && disjunctiveMode) {
        return true;
      }
      if (!match && !disjunctiveMode) {
        return false;
      }
    }

    return !disjunctiveMode;
  }
}
