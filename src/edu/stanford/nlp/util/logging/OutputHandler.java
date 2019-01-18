
package edu.stanford.nlp.util.logging;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood.Record;
import edu.stanford.nlp.util.Generics;

/**
 * An abstract handler incorporating the logic of outputting a log message,
 * to some source. This class is responsible for printing channel information,
 * formatting tracks, and writing the actual log messages.
 *
 * Classes overriding this class should implement the print() method based
 * on their output source.
 *
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public abstract class OutputHandler extends LogRecordHandler {

  /**
   * A list of tracks which have been started but not yet printed as no
   * log messages are in them yet.
   */
  protected LinkedList<Record> queuedTracks = new LinkedList<>();
  /**
   * Information about the current and higher level tracks
   */
  protected Stack<TrackInfo> trackStack = new Stack<>();
  /**
   * The current track info; used to avoid trackStack.peek() calls
   */
  protected TrackInfo info;
  /**
   * The tab character
   */
  protected String tab = "  ";
  /**
   * Character used to join multiple channel names
   */
  protected char channelSeparatorChar = ' ';
  /**
   * The length of the left margin in which to print channel information.
   * If this is set to a value < 3, then no channel information is printed.
   */
  protected int leftMargin = 0;
  /**
   * Number of lines above which the closing brace of a track shows the name of the
   * track
   */
  protected int minLineCountForTrackNameReminder = 50;

  /**
   * True if we have not printed the opening bracket for a track yet
   */
  private boolean missingOpenBracket = false;

  /**
   * The color to use for track beginning and ends
   */
  protected Color trackColor = Color.NONE;
  protected Map<String,Color> channelColors = null;
  protected boolean addRandomColors = false;

  /**
   * The style to use for track beginning and ends
   */
  protected Style trackStyle = Style.NONE;
  protected Map<String,Style> channelStyles = null;

  static Pair<String,Redwood.Flag> getSourceStringAndLevel(Object[] channel) {
    // Parse the channels
    Class source = null;  // The class the message is coming from
    Object backupSource = null;  // Another identifier for the message
    Redwood.Flag flag = Redwood.Flag.STDOUT;
    if (channel != null) {
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
    }

    // Get the sourceString. Do at end because there is then an imposed priority ordering
    String sourceString;
    if (source != null) {
      sourceString = source.getName();
    } else if (backupSource != null) {
      sourceString = backupSource.toString();
    } else {
      sourceString = "CoreNLP";
    }
    return new Pair<>(sourceString, flag);
  }

  /**
   * Print a string to an output without the trailing newline.
   * Many output handlers can get by with just implementing this method.
   * @param channel The channels this message was printed on; in most cases
   *                  an implementing handler should not have to do anything with
   *                  this. The channels should not be printed here.
   *                  The channels may be null.
   * @param line The string to be printed.
   */
  public abstract void print(Object[] channel, String line);


  /**
   * Color the tag for a particular channel this color
   * @param channel The channel to color
   * @param color The color to use
   */
  public void colorChannel(String channel, Color color){
    if(this.channelColors == null){
      this.channelColors = Generics.newHashMap();
    }
    this.channelColors.put(channel.toLowerCase(Locale.ENGLISH),color);
  }

  /**
   * Style the tag for a particular channel this style
   * @param channel The channel to style
   * @param style The style to use
   */
  public void styleChannel(String channel, Style style){
    if(this.channelStyles == null){
      this.channelStyles = Generics.newHashMap();
    }
    this.channelStyles.put(channel.toLowerCase(Locale.ENGLISH),style);
  }

  public void setColorChannels(boolean colorChannels){
    this.addRandomColors = colorChannels;
    if(colorChannels){ this.channelColors = Generics.newHashMap(); }
  }

  /**
   * Style a particular String segment, according to a color and style
   * @param b The string builder to append to (for efficiency)
   * @param line The String to be wrapped
   * @param color The color to color as
   * @param style The style to use
   * @return The SringBuilder b
   */
  protected StringBuilder style(StringBuilder b, String line, Color color, Style style){
    if(color != Color.NONE || style != Style.NONE){
      if (Redwood.supportsAnsi && this.supportsAnsi()) {
        b.append(color.ansiCode);
        b.append(style.ansiCode);
      }
      b.append(line);
      if (Redwood.supportsAnsi && this.supportsAnsi()) {
        b.append("\033[0m");
      }
    } else {
      b.append(line);
    }
    return b;
  }

  /**
   * Specify whether this output handler supports ansi output
   * @return False by default, unless overwritten.
   */
  protected boolean supportsAnsi() {
    return false;
  }

  /**
   *  Format a channel
   * @param b The StringBuilder to append to
   * @param channelStr The [possibly truncated and/or modified] string
   *                   to actually print to the StringBuilder
   * @param channel The original channel
   * @return |true| if the channel was printed (that is, appended to the StringBuilder)
   */
  protected boolean formatChannel(StringBuilder b, String channelStr, Object channel){
    if(this.channelColors == null && this.channelStyles == null){
      //(regular concat)
      b.append(channelStr);
    } else {
      String channelToString = channel.toString().toLowerCase(Locale.ENGLISH);
      //(default: no style)
      Color color = Color.NONE;
      Style style = Style.NONE;
      //(get color)
      if(this.channelColors != null){
        Color candColor = this.channelColors.get(channelToString);
        if(candColor != null){
          //((case: found a color))
          color = candColor;
        } else if(addRandomColors){
          //((case: random colors))
          color = Color.values()[SloppyMath.pythonMod(channelToString.hashCode(), (Color.values().length-3))+3];
          if(channelToString.equals(Redwood.ERR.toString().toLowerCase())){
            color = Color.RED;
          } else if(channelToString.equals(Redwood.WARN.toString().toLowerCase())){
            color = Color.YELLOW;
          }
          this.channelColors.put(channelToString, color);
        }
      }
      //(get style)
      if(this.channelStyles != null){
        Style candStyle = this.channelStyles.get(channelToString);
        if(candStyle != null){ style = candStyle; }
      }
      //(format)
      style(b,channelStr,color,style);
    }
    return true;  // Unless this method is overwritten, channel is always printed
  }


  private void writeContent(int depth, Object content, StringBuilder b){
    if(leftMargin > 2){ b.append(tab); }
    //(write tabs)
    for(int i=0; i<depth; i++){
      b.append(tab);
    }
    //(write content)
    b.append(content);
  }

  private void updateTracks(int untilDepth){
    while(!queuedTracks.isEmpty()){
      //(get record to update)
      Record signal = queuedTracks.removeFirst();
      if(signal.depth >= untilDepth){ queuedTracks.add(signal); return; }
      //(begin record message)
      StringBuilder b = new StringBuilder();
      if(missingOpenBracket){
        b.append("{\n");
      }
      //(write margin)
      for (int i=0; i<leftMargin; i++) {
        b.append(' ');
      }
      //(write name)
      writeContent(signal.depth,signal.content,b);
      if ( ! signal.content.toString().isEmpty()) { b.append(' '); }
      //(print)
      print(null, this.style(new StringBuilder(), b.toString(), trackColor, trackStyle).toString() );
      this.missingOpenBracket = true;  //only set to false if actually updated track state
      //(update lines printed)
      if(info != null){
        info.numElementsPrinted += 1;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> handle(Record record) {
    StringBuilder b = new StringBuilder(1024);

    //--Special case for Exceptions
    String[] content;
    if (record.content instanceof Throwable) {
      //(vars)
      List<String> lines = new ArrayList<>();
      //(root message)
      Throwable exception = (Throwable) record.content;
      lines.add(record.content.toString());
      StackTraceElement[] trace = exception.getStackTrace();
      StackTraceElement topTraceElement = trace.length > 0 ? trace[0] : null;
      for (StackTraceElement e : exception.getStackTrace()) {
        lines.add(tab + e);
      }
      //(causes)
      while (exception.getCause() != null) {
        // System.out.println("TOP ELEMENT: " + topTraceElement);
        //((variables))
        exception = exception.getCause();
        trace = exception.getStackTrace();
        lines.add("Caused by: " + exception.getClass()+": " + exception.getMessage());
        for(int i=0; i<trace.length; i++){
          //((add trace element))
          StackTraceElement e = trace[i];
          lines.add(tab + e);
          //((don't print redundant elements))
          if(topTraceElement != null &&
              e.getClassName().equals(topTraceElement.getClassName()) &&
              e.getMethodName().equals(topTraceElement.getMethodName())){
            lines.add(tab+"..."+(trace.length-i-1) + " more");
            break;
          }
        }
        //((update top element))
        topTraceElement = trace.length > 0 ? trace[0] : null;
      }
      //(set content array)
      content = new String[lines.size()];
      content = lines.toArray(content);
    } else if(record.content == null){
      content = new String[]{"null"};
    } else {
      String toStr;
      if (record.content instanceof Supplier) {
        //noinspection unchecked
        toStr = ((Supplier<Object>) record.content).get().toString();
      } else {
        toStr = record.content.toString();
      }
      if (toStr == null) {
        content = new String[]{"<null toString()>"};
      } else {
        content = toStr.split("\n"); //would be nice to get rid of this 'split()' call at some point
      }
    }

    //--Handle Tracks
    updateTracks(record.depth);
    if(this.missingOpenBracket){
      this.style(b, "{\n", trackColor, trackStyle);
      this.missingOpenBracket = false;
    }
    //--Process Record
    //(variables)
    int cursorPos = 0;
    int contentLinesPrinted = 0;
    //(loop)
    Color color = Color.NONE;
    Style style = Style.NONE;
    //(get channels)
    ArrayList<Object> printableChannels = new ArrayList<>();
    for(Object chan : record.channels()){
      if(chan instanceof Color){ color = (Color) chan; }
      else if(chan instanceof Style){ style = (Style) chan; }
      else if(chan != Redwood.FORCE){ printableChannels.add(chan); }
    }
    //--Write Channels
    if(leftMargin > 2) {	//don't print if not enough space
      //((print channels)
      b.append('[');
      cursorPos += 1;
      Object lastChan = null;
      boolean wasAnyChannelPrinted = false;
      for(int i=0; i<printableChannels.size(); i++) {
        Object chan = printableChannels.get(i);
        if(chan.equals(lastChan)){ continue; } //skip duplicate channels
        lastChan = chan;
        //(get channel)
        String toPrint = chan.toString();
        if(toPrint.length() > leftMargin-1){ toPrint = toPrint.substring(0,leftMargin-2); }
        if(cursorPos+toPrint.length() >= leftMargin){
          //(case: doesn't fit)
          while (cursorPos < leftMargin) { b.append(' '); cursorPos += 1; }
          if (contentLinesPrinted < content.length) {
            writeContent(record.depth, style(new StringBuilder(),content[contentLinesPrinted],color,style).toString(), b);
            contentLinesPrinted += 1;
          }
          b.append("\n ");
          cursorPos = 1;
        }
        //(print flag)
        boolean wasChannelPrinted = formatChannel(b, toPrint, chan);
        wasAnyChannelPrinted = wasAnyChannelPrinted || wasChannelPrinted;
        if(wasChannelPrinted && i < printableChannels.size()-1){ b.append(channelSeparatorChar); cursorPos += 1; }
        cursorPos += toPrint.length();
      }
      if (wasAnyChannelPrinted) {
        b.append(']');
        cursorPos += 1;
      } else {
        b.setLength(b.length() - 1);  // remove leading "["
        cursorPos -= 1;
      }
    }
    //--Content
    //(write content)
    while (contentLinesPrinted < content.length) {
      while (cursorPos < leftMargin) { b.append(' '); cursorPos += 1; }
      writeContent(record.depth, style(new StringBuilder(),content[contentLinesPrinted],color,style).toString(), b);
      contentLinesPrinted += 1;
      if(contentLinesPrinted < content.length){ b.append('\n'); cursorPos = 0; }
    }
    //(print)
    if (b.length() == 0 || b.charAt(b.length() - 1) != '\n') {
      b.append('\n');
    }
    print(record.channels(), b.toString());
    //--Continue
    if(info != null){
      info.numElementsPrinted += 1;
    }
    ArrayList<Record> rtn = new ArrayList<>();
    rtn.add(record);
    return rtn;
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> signalStartTrack(Record signal) {
    //(queue track)
    this.queuedTracks.addLast(signal);
    //(push info)
    if(info != null){
      this.trackStack.push(info);
    }
    info = new TrackInfo(signal.content.toString(), signal.timesstamp);
    //(force print)
    if(signal.force()){
      updateTracks(signal.depth+1);
    }
    //(return)
    return EMPTY; //don't send extra records
  }

  /** {@inheritDoc} */
  @Override
  public List<Record> signalEndTrack(int newDepth, long timeOfEnd) {
    //(pop info)
    TrackInfo childInfo = this.info;
    if (childInfo == null) {
      throw new IllegalStateException("OutputHandler received endTrack() without matching startTrack() --" +
          "are your handlers mis-configured?");
    }
    if(trackStack.empty()){
      this.info = null;
    } else {
      this.info = this.trackStack.pop();
      this.info.numElementsPrinted += childInfo.numElementsPrinted;
    }
    //(handle track)
    if(this.queuedTracks.isEmpty()){
      StringBuilder b = new StringBuilder();
      if (!this.missingOpenBracket) {
        //(write margin)
        for(int i=0; i<this.leftMargin; i++) {
          b.append(' ');
        }
        //(null content)
        writeContent(newDepth, "", b);
        //(write bracket)
        b.append("} ");
      }
      this.missingOpenBracket = false;
      //(write matching line)
      if (childInfo.numElementsPrinted > this.minLineCountForTrackNameReminder) {
        b.append("<< ").append(childInfo.name).append(' ');
      }
      //(write time)
      if (timeOfEnd-childInfo.beginTime > 100) {
        b.append('[');
        Redwood.formatTimeDifference(timeOfEnd-childInfo.beginTime,b);
        b.append(']');
      }
      //(print)
      b.append('\n');
      print(null, this.style(new StringBuilder(), b.toString(), trackColor, trackStyle).toString());
    } else {
      this.queuedTracks.removeLast();
    }
    return EMPTY; //don't send extra records
  }

  /**
   * Relevant information about printing the start, and particularly
   * the end, of a track
   */
  private static class TrackInfo {

    public final long beginTime;
    public final String name;
    protected int numElementsPrinted; // = 0;

    private TrackInfo(String name, long timestamp) {
      this.name = name;
      this.beginTime = timestamp;
    }

  }

}
