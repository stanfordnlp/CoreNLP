
package edu.stanford.nlp.util.logging;

import edu.stanford.nlp.util.logging.Redwood.Record;

/**
 * Simple interface to determine if a Record matches a set of criteria.
 * Inner classes provide some common filtering operations.  Other simple
 * and generate purpose LogFilters should be added here as well.
 *
 * @author David McClosky
 */
public interface LogFilter {

  boolean matches(Record message);

  class HasChannel implements LogFilter {
    private Object matchingChannel;

    public HasChannel(Object message) {
      this.matchingChannel = message;
    }

    @Override
    public boolean matches(Record record) {
      for (Object tag : record.channels()) {
        if (tag.equals(matchingChannel)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Propagate records containing certain substrings.  Note that this
   * doesn't require Records to have String messages since it will call
   * toString() on them anyway.
   */
  class ContainsMessage implements LogFilter {
    private String substring;

    public ContainsMessage(String message) {
      this.substring = message;
    }

    @Override
    public boolean matches(Record record) {
      String content = record.content.toString();
      return content.contains(this.substring);
    }
  }

  /**
   * Propagate records when Records match a specific message exactly (equals() is used for comparisons)
   */
  class MatchesMessage implements LogFilter {
    private Object message;

    public MatchesMessage(Object message) {
      this.message = message;
    }

    @Override
    public boolean matches(Record record) {
      return record.content.equals(message);
    }
  }
}
