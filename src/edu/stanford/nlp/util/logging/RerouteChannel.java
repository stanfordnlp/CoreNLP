
package edu.stanford.nlp.util.logging;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.logging.Redwood.Record;

/**
 * A LogRecordHandler which replaces the first occurrence of one channel name
 * in each record with another channel name.
 */
public class RerouteChannel extends LogRecordHandler {
  
  private Object oldChannelName;
  private Object newChannelName;

  /**
   * Creates a handler that reroutes records from one channel to another.
   *
   * @param oldChannelName the channel to reroute from
   * @param newChannelName the channel to reroute to
   */
  public RerouteChannel(Object oldChannelName, Object newChannelName) {
    this.oldChannelName = oldChannelName;
    this.newChannelName = newChannelName;
  }

  public List<Record> handle(Record record) {
    List<Record> results = new ArrayList<>();
    
    Object[] channels = record.channels();
    for (int i = 0; i < channels.length; i++) {
      Object channel = channels[i];
      if (oldChannelName.equals(channel)) {
        // make a new version of the Record with a different channel name
        channels[i] = newChannelName;
        Record reroutedRecord = new Record(record.content, channels, record.depth, record.timesstamp);
        results.add(reroutedRecord);
        return results;
      }
    }
    
    // didn't find any matching records, so just return the original one
    results.add(record);
    return results;
  }
}
