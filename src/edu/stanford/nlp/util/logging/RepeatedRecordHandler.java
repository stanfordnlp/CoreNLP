
package edu.stanford.nlp.util.logging;

import edu.stanford.nlp.util.logging.Redwood.Record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

/**
 * Filters repeated messages and replaces them with the number of times they were logged.
 *
 * @author David McClosky,
 * @author Gabor Angeli (angeli at cs.stanford): approximate record equality, repeated tracks squashed
 */
public class RepeatedRecordHandler extends LogRecordHandler {

  private final Stack<RepeatedRecordInfo> stack = new Stack<>();
  RepeatedRecordInfo current = new RepeatedRecordInfo();
  private final RepeatSemantics repeatSemantics;

  /**
   * Create a new repeated log message handler, using the given semantics for what
   * constitutes a repeated record.
   * @param repeatSemantics The semantics for what constitutes a repeated record
   */
  public RepeatedRecordHandler(RepeatSemantics repeatSemantics){
    this.repeatSemantics = repeatSemantics;
  }

  private void flush(RepeatedRecordInfo info, List<Record> willReturn) {
    //(suppress all printing)
    if(info.suppressRecord){ return; }
    //(get time)
    int repeatedRecordCount = info.timesSeen - info.timesPrinted;
    if (repeatedRecordCount > 0) {
      //(send message record)
      //((add force tag))
      Object[] newTags = new Object[info.lastRecord.channels().length+1];
      System.arraycopy(info.lastRecord.channels(),0,newTags,1,info.lastRecord.channels().length);
      newTags[0] = Redwood.FORCE;
      //((create record))
      Record newRecord = new Record(
          repeatSemantics.message(repeatedRecordCount),
          newTags,
          info.lastRecord.depth,
          info.lastRecord.timesstamp);
      //((pass record))
      willReturn.add(newRecord);
      info.timesSeen = 0;
      info.timesPrinted = 0;
    }
  }

  private void flushParents(List<Record> willReturn){
    Stack<RepeatedRecordInfo> reverseStack = new Stack<>();
      while(!stack.isEmpty()){
        reverseStack.push(stack.pop());
      }
      while(!reverseStack.isEmpty()){
        RepeatedRecordInfo info = reverseStack.pop();
        info.timesSeen -= 1;
        flush(info, willReturn);
        stack.push(info);
      }
  }

  private boolean recordVerdict(Record r, boolean isRepeat, boolean shouldPrint, List<Record> willReturn){
    if(r.force()){
      flushParents(willReturn);
      if(isRepeat){ flush(current,willReturn); } //if not repeat, will flush below
      shouldPrint = true;
    }
    if(!isRepeat) {
      flush(current,willReturn);
      current.lastRecord = r;
    }
    if(shouldPrint){
      current.timeOfLastPrintedRecord = r.timesstamp;
      current.timesPrinted += 1;
    }
    current.timesSeen += 1;
    current.somethingPrinted = true;
    //(return)
    return shouldPrint;
  }

  private boolean internalHandle(Record record, List<Record> willReturn){
    // We are passing the record through a number of filters,
    // ordered by priority, to determine whether or not
    // to continue passing it on

    //--Special Cases
    //--Regular Cases
    //(ckeck squashing)
    if(this.current.suppressRecord){
      return recordVerdict(record, false, false, willReturn); //arg 2 is irrelevant here
    }
    //(check first record printed)
    if(this.current.lastRecord == null){
      return recordVerdict(record,false, true, willReturn);
    }
    //(check equality)
    if(this.repeatSemantics.equals(current.lastRecord,record)){
      //(check time)
      long currentTime = record.timesstamp;
      if(currentTime - this.current.timeOfLastPrintedRecord > this.repeatSemantics.maxWaitTimeInMillis()){
        return recordVerdict(record, true, true, willReturn);
      }
      //(check num printed)
      if(this.current.timesSeen < this.repeatSemantics.numToForcePrint()){
        return recordVerdict(record, true, true, willReturn);
      } else {
        return recordVerdict(record, true, false, willReturn);
      }
    } else {
      //(different record)
      return recordVerdict(record, false, true, willReturn);
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> handle(Record record) {
    List<Record> willReturn = new ArrayList<>();
    if(internalHandle(record, willReturn)){
      willReturn.add(record);
    }
    return willReturn;
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> signalStartTrack(Record signal) {
    //(handle record)
    List<Record> willReturn = new ArrayList<>();
    boolean isPrinting = internalHandle(signal, willReturn);
    //(adjust state for track)
    if(!signal.force()){
      if(isPrinting){
        current.trackCountPending = PendingType.PRINTING;
        current.timesPrinted -= 1;
      }else{
        current.trackCountPending = PendingType.SEEN;
      }
      current.timesSeen -= 1;
    }
    //(push stack)
    stack.push(current);
    current = new RepeatedRecordInfo();
    if(!isPrinting){ current.suppressRecord = true; }
    return willReturn;
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> signalEndTrack(int newDepth, long timeEnded) {
    List<Record> willReturn = new ArrayList<>();
    //(get state info)
    boolean trackWasNonempty = current.somethingPrinted;
    //(flush)
    flush(current,willReturn);
    current = stack.pop();
    //(update seen counts)
    if(trackWasNonempty){
      if(current.trackCountPending == PendingType.PRINTING){
        //((track was in fact printed))
        current.timesPrinted += 1;
      }
      if(current.trackCountPending != PendingType.NONE){
        //((track was in fact seen))
        current.timesSeen += 1;
      }
      //((track is nonempty))
      current.somethingPrinted = true;
    }
    //(update this track)
    current.trackCountPending = PendingType.NONE;
    return willReturn;
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> signalShutdown(){
    List<Record> willReturn = new ArrayList<>();
    flush(current,willReturn);
    return willReturn;
  }


  private enum PendingType { NONE, PRINTING, SEEN }


  private static class RepeatedRecordInfo {
    private Record lastRecord = null;
    private int timesSeen = 0;
    private int timesPrinted = 0;
    private long timeOfLastPrintedRecord = 0L;
    private boolean suppressRecord = false;
    private boolean somethingPrinted = false;
    private PendingType trackCountPending = PendingType.NONE;
  }


  /**
   * Determines the semantics of what constitutes a repeated record
   */
  public interface RepeatSemantics {
    boolean equals(Record lastRecord, Record newRecord);
    long maxWaitTimeInMillis();
    int numToForcePrint();
    String message(int linesOmitted);
  }


  /**
   *  Judges two records to be equal if they come from the same place,
   *  and begin with the same string, modulo numbers
   */
  public static class ApproximateRepeatSemantics implements RepeatSemantics {
    private static boolean sameMessage(String last, String current){
      String lastNoNumbers = last.replaceAll("[0-9\\.\\-]+","#");
      String currentNoNumbers = current.replaceAll("[0-9\\.\\-]+","#");
      return lastNoNumbers.startsWith(currentNoNumbers.substring(0, Math.min(7, currentNoNumbers.length())));
    }
    @Override
    public boolean equals(Record lastRecord, Record record) {
      return Arrays.equals(record.channels(), lastRecord.channels()) &&
          sameMessage(
            lastRecord.content == null ? "null" : lastRecord.content.toString(),
            record.content == null ? "null" : record.content.toString()
          );
    }
    @Override
    public long maxWaitTimeInMillis() {
      return 1000;
    }
    @Override
    public int numToForcePrint(){
      return 3;
    }
    @Override
    public String message(int linesOmitted){
      return "... "+linesOmitted+" similar messages";
    }
  }

  public static final ApproximateRepeatSemantics APPROXIMATE = new ApproximateRepeatSemantics();


  /**
   * Judges two records to be equal if they are from the same place,
   * and have the same message
   */
  public static class ExactRepeatSemantics implements RepeatSemantics {
    @Override
    public boolean equals(Record lastRecord, Record record) {
      return Arrays.equals(record.channels(), lastRecord.channels()) &&
          ( (record.content == null && lastRecord.content == null) ||
            (record.content != null && record.content.equals(lastRecord.content)) );
    }
    @Override
    public long maxWaitTimeInMillis() {
      return Long.MAX_VALUE;
    }
    @Override
    public int numToForcePrint(){
      return 1;
    }
    @Override
    public String message(int linesOmitted){
      return "(last message repeated " + linesOmitted + " times)";
    }
  }

  public static final ExactRepeatSemantics EXACT = new ExactRepeatSemantics();

}
