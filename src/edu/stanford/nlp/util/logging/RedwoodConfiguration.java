
package edu.stanford.nlp.util.logging;

import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.util.Generics;

/**
 * A class which encapsulates configuration settings for Redwood.
 * The class operates on the builder model; that is, you can chain method
 * calls.
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class RedwoodConfiguration {
  //-- Properties Regular Expressions --
  private static Pattern consoleColor = Pattern.compile("^log\\.console\\.(.*?)Color$");
  private static Pattern fileColor = Pattern.compile("^log\\.file\\.(.*?)Color$");
  private static Pattern consoleStyle = Pattern.compile("^log\\.console\\.(.*?)Style$");
  private static Pattern fileStyle = Pattern.compile("^log\\.file\\.(.*?)Style$");

  /**
   * A list of tasks to run when the configuration is applied
   */
  private LinkedList<Runnable> tasks = new LinkedList<Runnable>();

  /**
   * Private constructor to prevent use of "new RedwoodConfiguration()"
   */
  protected RedwoodConfiguration(){}

  /**
   * Apply this configuration to Redwood
   */
  public void apply(){
    for(Runnable task : tasks){ task.run(); }
  }


  /**
   * Clear any custom configurations to Redwood
   * @return this
   */
  public RedwoodConfiguration clear(){
    tasks = new LinkedList<Runnable>();
    tasks.add(new Runnable(){ public void run(){
      Redwood.clearHandlers();
      Redwood.restoreSystemStreams();
      Redwood.clearLoggingClasses();
    } });
    return this;
  }

  /**
   * Add a console pipeline to the Redwood handler tree,
   * printing to stdout.
   * Calling this multiple times will result in messages being printed
   * multiple times.
   * @return this
   */
  public RedwoodConfiguration stdout(){
    LogRecordHandler visibility = new VisibilityHandler();
    LogRecordHandler console = Redwood.ConsoleHandler.out();
    return this
        .rootHandler(visibility)
        .handler(visibility, console);
  }

  /**
   * Add a console pipeline to the Redwood handler tree,
   * printing to stderr.
   * Calling this multiple times will result in messages being printed
   * multiple times.
   * @return this
   */
  public RedwoodConfiguration stderr(){
    LogRecordHandler visibility = new VisibilityHandler();
    LogRecordHandler console = Redwood.ConsoleHandler.err();
    return this
        .rootHandler(visibility)
        .handler(visibility, console);
  }

  /**
   * Add a console pipeline to the Redwood handler tree,
   * Calling this multiple times will result in messages being printed
   * multiple times.
   * @return this
   */
  public RedwoodConfiguration console(){ return stdout(); }

  /**
   * Add a file pipeline to the Redwood handler tree.
   * That is, print log messages to a given file.
   * Calling this multiple times will result in messages being printed
   * to multiple files (or, multiple times to the same file).
   * @param file The path of the file to log to. This path is not checked
   * for correctness here.
   * @return this
   */
  public RedwoodConfiguration file(String file, String ... channels){
    LogRecordHandler visibility = new VisibilityHandler(channels);
    LogRecordHandler console = new Redwood.FileHandler(file);
    return this
        .rootHandler(visibility)
        .handler(visibility, console);
  }

  /**
   * Add a custom Log Record Handler to the root of the tree
   * @param handler The handler to add
   * @return this
   */
  public RedwoodConfiguration rootHandler(final LogRecordHandler handler){
    tasks.add(new Runnable(){ public void run(){ Redwood.appendHandler(handler); } });
    Redwood.appendHandler(handler);
    return this;
  }

  /**
   * Add a handler to as a child of an existing parent
   * @param parent The handler to extend
   * @param child The new handler to add
   * @return this
   */
  public RedwoodConfiguration handler(final LogRecordHandler parent, final LogRecordHandler child){
    tasks.add(new Runnable() { public void run() { Redwood.appendHandler(parent, child);} });
    return this;
  }

  /**
   * Add a handler to as a child of an existing parent
   * @param parent The handler to extend
   * @param toAdd The new handler to add
   * @param grandchild The subtree to attach to the new handler
   * @return this
   */
  public RedwoodConfiguration splice(final LogRecordHandler parent, final LogRecordHandler toAdd, final LogRecordHandler grandchild){
    tasks.add(new Runnable() { public void run() { Redwood.spliceHandler(parent, toAdd, grandchild);} });
    return this;
  }

  /**
   * Set a Java classname path to ignore when printing stack traces
   * @param classToIgnoreInTraces The class name (with packages, etc) to ignore.
   * @return this
   */
  public RedwoodConfiguration loggingClass(final String classToIgnoreInTraces){
    tasks.add(new Runnable() { public void run() { Redwood.addLoggingClass(classToIgnoreInTraces); } });
    return this;
  }
  /**
   * Set a Java class to ignore when printing stack traces
   * @param classToIgnoreInTraces The class to ignore.
   * @return this
   */
  public RedwoodConfiguration loggingClass(final Class<?> classToIgnoreInTraces){
    tasks.add(new Runnable() { public void run() { Redwood.addLoggingClass(classToIgnoreInTraces.getName()); } });
    return this;
  }

  /**
   * Collapse repeated records, using an approximate notion of equality
   * (i.e. records begin or end with the same substring)
   * This is useful for cases such as tracking iterations ("iter 1" and "iter 2" are considered the same record).
   * @return this
   */
  public RedwoodConfiguration collapseApproximate(){
    tasks.add(new Runnable() { public void run() { Redwood.spliceHandler(VisibilityHandler.class, new RepeatedRecordHandler(RepeatedRecordHandler.APPROXIMATE),OutputHandler.class); } });
    return this;
  }

  /**
   * Collapse repeated records, using exact string match on the record.
   * This is generally useful for making very verbose logs more readable.
   * @return this
   */
  public RedwoodConfiguration collapseExact(){
    tasks.add(new Runnable() { public void run() { Redwood.spliceHandler(VisibilityHandler.class, new RepeatedRecordHandler(RepeatedRecordHandler.EXACT), OutputHandler.class); } });
    return this;
  }

  /**
   * Do not collapse repeated records
   * @return this
   */
  public RedwoodConfiguration collapseNone(){
    tasks.add(new Runnable() { public void run() { Redwood.removeHandler(RepeatedRecordHandler.class); } });
    return this;
  }

  /**
   * Capture stdout and route them through Redwood
   * @return this
   */
  public RedwoodConfiguration captureStdout(){
    tasks.add(new Runnable() { public void run() { Redwood.captureSystemStreams(true, false); } });
    return this;
  }

  /**
   * Capture stderr and route them through Redwood
   * @return this
   */
  public RedwoodConfiguration captureStderr(){
    tasks.add(new Runnable() { public void run() { Redwood.captureSystemStreams(false, true); } });
    return this;
  }

  /**
   * Capture stdout and stderr and route them through Redwood
   * @return this
   */
  public RedwoodConfiguration captureStreams(){
    return this.captureStdout().captureStderr();
  }

  /**
   * Close tracks when the JVM shuts down.
   * @return this
   */
  public RedwoodConfiguration neatExit(){
    tasks.add(new Runnable() { public void run() {
      Runtime.getRuntime().addShutdownHook(new Thread(){
        @Override public void run(){ Redwood.stop(); }
      });
    }});
    return this;
  }

  /**
   * Print channels to the left of log messages
   * @param width The width (in characters) to print the channels
   * @return this
   */
  public RedwoodConfiguration printChannels(final int width){
     tasks.add(new Runnable() { public void run() { Redwood.Util.printChannels(width);} });
    return this;
  }

  /**
   * Hide the following channels.
   * @param channels The names of the channels to hide.
   * @return this
   */
  public RedwoodConfiguration hideChannels(final Object[] channels){
     tasks.add(new Runnable() { public void run() { Redwood.hideChannels(channels); } });
    return this;
  }

  /**
   * Show only the following channels.
   * @param channels The names of the channels to show.
   * @return this
   */
  public RedwoodConfiguration showOnlyChannels(final Object[] channels){
     tasks.add(new Runnable() { public void run() { Redwood.showOnlyChannels(channels); } });
    return this;
  }

  /**
   * An empty Redwood configuration.
   * Note that without a Console Handler, Redwood will not print anything
   * @return An empty Redwood Configuration object.
   */
  public static RedwoodConfiguration empty(){
    return new RedwoodConfiguration().clear();
  }

  /**
   * The default Redwood configuration, which prints to the console.
   * This is the usual starting point for new configurations.
   * @return  A basic Redwood Configuration.
   */
  public static RedwoodConfiguration standard(){
    return new RedwoodConfiguration().clear().console().loggingClass(Redwood.class);
  }

  /**
   * The current Redwood configuration; this is used to make incremental changes
   * to an existing custom configuration.
   * @return The current Redwood configuration.
   */
  public static RedwoodConfiguration current(){
    return new RedwoodConfiguration();
  }


  /**
   * Helper for parsing properties
   * @param p The properties object
   * @param key The key to retrieve
   * @param defaultValue The default value if the key does not exist
   * @param used The set of keys we have seen
   * @return The value of the property at the key
   */
  private static String get(Properties p, String key, String defaultValue, Set<String> used){
    String rtn = p.getProperty(key, defaultValue);
    used.add(key);
    return rtn;
  }

  /**
   * Configure Redwood (from scratch) based on a Properties file.
   * Currently recognized properties are:
   * <ul>
   *   <li>log.method = {default, redwood, java.util.logging}: All logging output will go to this adapter; "default" means it will go to the method specified by the function call</li>
   *   <li>log.method.name = [string]: A name for the java.util.logging logger</li>
   *   <li>log.toStderr = {true,false}: Print to stderr rather than stdout</li>
   *   <li>log.file = [filename]: Dump the output of the log to the given filename</li>
   *   <li>log.collapse = {exact,approximate,none}: Collapse repeated records (based on either exact or approximate equality)</li>
   *   <li>log.neatExit = {true,false}: Clean up logs on exception or regular system exit
   *   <li>log.{console,file}.colorChannels = {true,false}: If true, randomly assign colors to different channels</li>
   *   <li>log.{console,file}.{track,[channel]}]Color = {NONE,BLACK,RED,GREEN,YELLOW,BLUE,MAGENTA,CYAN,WHITE}: Color for printing tracks (e.g. log.file.trackColor = BLUE)
   *   <li>log.{console,file}.{track,[channel]}Style = {NONE,BOLD,DIM,ITALIC,UNDERLINE,BLINK,CROSS_OUT}: Style for printing tracks (e.g. log.console.errStyle = BOLD)
   *   <li>log.captureStreams = {true,false}: Capture stdout and stderr and route them through Redwood</li>
   *   <li>log.captureStdout = {true,false}: Capture stdout and route it through Redwood</li>
   *   <li>log.captureStderr = {true,false}: Capture stdout and route it through Redwood</li>
   *   <li>log.channels.hide = [channels]: Hide these channels (comma-separated list)</li>
   *   <li>log.channels.show = [channels]: Show only these channels (comma-separated list)</li>
   *   <li>log.channels.width = [int]: If nonzero, the channels for each logging statement will be printed to their left</li>
   *   <li>log.channels.debug = {true,false}: Turn the debugging channel on or off</li>
   * </ul>
   * @param props The properties to use in configuration
   * @return A new Redwood Configuration based on the passed properties, ignoring any existing custom configuration
   */
  public static RedwoodConfiguration parse(Properties props){
    Set<String> used = Generics.newHashSet();
    //--Construct Pipeline
    //(handlers)
    Redwood.ConsoleHandler console = get(props,"log.toStderr","false",used).equalsIgnoreCase("true") ? Redwood.ConsoleHandler.err() : Redwood.ConsoleHandler.out();
    VisibilityHandler visibility = new VisibilityHandler();
    RepeatedRecordHandler repeat = null;
    //(initialize pipeline)
    RedwoodConfiguration config = new RedwoodConfiguration().clear().rootHandler(visibility);
    //(collapse)
    String collapseSetting = get(props,"log.collapse","none",used);
    if(collapseSetting.equalsIgnoreCase("exact")){
      repeat = new RepeatedRecordHandler(RepeatedRecordHandler.EXACT);
      config = config.handler(visibility,repeat);
    } else if(collapseSetting.equalsIgnoreCase("approximate")){
      repeat = new RepeatedRecordHandler(RepeatedRecordHandler.APPROXIMATE);
      config = config.handler(visibility, repeat);
    } else if(collapseSetting.equalsIgnoreCase("none")){
      //do nothing
    } else {
      throw new IllegalArgumentException("Unknown collapse type: " + collapseSetting);
    }
    //--Console
    //((track color))
    console.trackColor = Color.valueOf(get(props,"log.console.trackColor","NONE",used).toUpperCase());
    console.trackStyle = Style.valueOf(get(props,"log.console.trackStyle","NONE",used).toUpperCase());
    //((other colors))
    for(Object propAsObj : props.keySet()) {
      String prop = propAsObj.toString();
      // color
      Matcher m = consoleColor.matcher(prop);
      if(m.find()){
        String channel = m.group(1);
        console.colorChannel(channel, Color.valueOf(get(props,prop,"NONE",used)));
      }
      // style
      m = consoleStyle.matcher(prop);
      if(m.find()){
        String channel = m.group(1);
        console.styleChannel(channel, Style.valueOf(get(props,prop,"NONE",used)));
      }
    }
    //((random colors))
    console.setColorChannels(Boolean.parseBoolean(get(props, "log.console.colorChannels", "false", used)));
    //--File
    String logFilename = get(props,"log.file",null,used);
    if(logFilename != null){
      Redwood.FileHandler file = new Redwood.FileHandler(logFilename);
      config.handler(repeat == null ? visibility : repeat, file);
      //((track colors))
      file.trackColor = Color.valueOf(get(props,"log.file.trackColor","NONE",used).toUpperCase());
      file.trackStyle = Style.valueOf(get(props,"log.file.trackStyle","NONE",used).toUpperCase());
      //((other colors))
      for(Object propAsObj : props.keySet()) {
        String prop = propAsObj.toString();
        // color
        Matcher m = fileColor.matcher(prop);
        if(m.find()){
          String channel = m.group(1);
          file.colorChannel(channel, Color.valueOf(get(props,prop,"NONE",used)));
        }
        // style
        m = fileStyle.matcher(prop);
        if(m.find()){
          String channel = m.group(1);
          file.styleChannel(channel, Style.valueOf(get(props,prop,"NONE",used)));
        }
      }
      //((random colors))
      file.setColorChannels(Boolean.parseBoolean(get(props,"log.file.colorChannels","false",used)));
    }

    //--Method
    String method = get(props,"log.method","default",used).toLowerCase();
    if(method.equalsIgnoreCase("redwood")){
      edu.stanford.nlp.util.logging.JavaUtilLoggingAdaptor.adapt();
      config = config.handler(repeat == null ? visibility : repeat, console);
    } else if(method.equalsIgnoreCase("java.util.logging")){
      edu.stanford.nlp.util.logging.JavaUtilLoggingAdaptor.adapt();
      String loggerName = get(props,"log.method.name","``error``",used);
      if (loggerName.equals("``error``")) {
        throw new IllegalArgumentException("Logger name (log.method.name) required to adapt with java.util.logging");
      }
      RedirectOutputHandler<Logger, Level> adapter = RedirectOutputHandler.fromJavaUtilLogging(Logger.getLogger(loggerName));
      config = config.handler(repeat == null ? visibility : repeat, adapter);
    } else if (method.equalsIgnoreCase("default")) {
      config = config.handler(repeat == null ? visibility : repeat, console);
    } else {
      throw new IllegalArgumentException("Unknown value for log.method");
    }
    
    //--System Streams
    if(get(props,"log.captureStreams","false",used).equalsIgnoreCase("true")){
      config = config.captureStreams();
    }
    if(get(props,"log.captureStdout","false",used).equalsIgnoreCase("true")){
      config = config.captureStdout();
    }
    if(get(props,"log.captureStderr","false",used).equalsIgnoreCase("true")){
      config = config.captureStderr();
    }
    //--Neat exit
    if(get(props,"log.neatExit","false",used).equalsIgnoreCase("true")){
      config = config.neatExit();
    }
    //--Channel Visibility
    // (parse properties)
    String channelsToShow = get(props,"log.channels.show",null,used);
    String channelsToHide = get(props,"log.channels.hide",null,used);
    boolean channelsDebug = Boolean.parseBoolean(get(props,"log.channels.debug","true",used));
    int channelWidth = Integer.parseInt(get(props, "log.channels.width", "10", used));
    if (channelsToShow != null && channelsToHide != null) {
      throw new IllegalArgumentException("Can't specify both log.channels.show and log.channels.hide");
    }
    // (set visibility)
    if (channelsToShow != null) {
      if(channelsToShow.equalsIgnoreCase("true")){
        config = config.printChannels(channelWidth);
      } else {
        config = config.printChannels(channelWidth).showOnlyChannels(channelsToShow.split(","));
      }
    } else if (channelsToHide != null) {
      config = config.printChannels(channelWidth).hideChannels(channelsToHide.split(","));
    }
    if (!channelsDebug) {
      config = config.hideChannels(new Object[]{Redwood.Flag.DEBUG});
    }
    //--Error Check
    for(Object propAsObj : props.keySet()) {
      String prop = propAsObj.toString();
      if(prop.startsWith("log.") && !used.contains(prop)){
        throw new IllegalArgumentException("Could not find Redwood log property: " + prop);
      }
    }
    //--Return
    return config;
  }

  /**
   * Parses a properties file and applies it immediately to Redwood
   * @param props The properties to apply
   */
  public static void apply(Properties props){
    parse(props).apply();
  }
}
