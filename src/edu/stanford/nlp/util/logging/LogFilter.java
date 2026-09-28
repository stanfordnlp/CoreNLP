
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

  /**
   * Returns whether the record matches the criteria of this filter.
   *
   * @param message The record to check
   * @return Whether the record matches
   */
  boolean matches(Record message);

  /** Propagate records which have a certain channel (compared with equals()). */
  class HasChannel implements LogFilter {
    private Object matchingChannel;

    /**
     * Create a filter matching records which have the given channel.
     *
     * @param message The channel to look for
     */
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

    /**
     * Create a filter matching records whose content's toString() contains the given substring.
     *
     * @param message The substring to look for
     */
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

    /**
     * Create a filter matching records whose content equals the given message.
     *
     * @param message The message to match
     */
    public MatchesMessage(Object message) {
      this.message = message;
    }

    @Override
    public boolean matches(Record record) {
      return record.content.equals(message);
    }
  }
}
