
package edu.stanford.nlp.util.logging;

import java.io.File;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.util.Generics;

/**
 * A class which encapsulates configuration settings for Redwood.
 * The class operates on the builder model; that is, you can chain method
 * calls.
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class RedwoodConfiguration {

  /**
   * A list of tasks to run when the configuration is applied
   */
  private LinkedList<Runnable> tasks = new LinkedList<Runnable>();

  private OutputHandler outputHandler = Redwood.ConsoleHandler.out();
  private File defaultFile = new File("/dev/null");
  private int channelWidth = 0;

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
   * Capture a system stream
   * @param stream The stream to capture; one of System.out or System.err
   * @return this
   */
  public RedwoodConfiguration capture(final OutputStream stream) {
    if (stream == System.out) {
      tasks.add(new Runnable() { public void run() { Redwood.captureSystemStreams(true, Redwood.realSysErr == System.err); } });
    } else if (stream == System.err) {
      tasks.add(new Runnable() { public void run() { Redwood.captureSystemStreams(Redwood.realSysOut == System.out, true); } });
    } else {
      throw new IllegalArgumentException("Must capture one of stderr or stdout");
    }
    return this;
  }

  public RedwoodConfiguration restore(final OutputStream stream) {
    if (stream == System.out) {
      tasks.add(new Runnable() { public void run() { Redwood.captureSystemStreams(false, Redwood.realSysErr == System.err); } });
    } else if (stream == System.err) {
      tasks.add(new Runnable() { public void run() { Redwood.captureSystemStreams(Redwood.realSysOut == System.out, false); } });
    } else {
      throw new IllegalArgumentException("Must capture one of stderr or stdout");
    }
    return this;
  }

  /**
   * Determine where, in the end, console output should go.
   * The default is stdout.
   * @param method An output, one of: stdout, stderr, or java.util.logging
   * @return this
   */
  public RedwoodConfiguration output(final String method) {
    if (method.equalsIgnoreCase("stdout") || method.equalsIgnoreCase("out")){
      edu.stanford.nlp.util.logging.JavaUtilLoggingAdaptor.adapt();
      this.outputHandler = Redwood.ConsoleHandler.out();
    } else if (method.equalsIgnoreCase("stderr") || method.equalsIgnoreCase("err")) {
        edu.stanford.nlp.util.logging.JavaUtilLoggingAdaptor.adapt();
        this.outputHandler = Redwood.ConsoleHandler.err();
    } else if (method.equalsIgnoreCase("java.util.logging")){
      edu.stanford.nlp.util.logging.JavaUtilLoggingAdaptor.adapt();
      this.outputHandler = RedirectOutputHandler.fromJavaUtilLogging(Logger.getLogger("``error``"));
    } else {
      throw new IllegalArgumentException("Unknown value for log.method");
    }
    return this;
  }

  /**
   * Set the width of the channels (or 0 to not show channels)
   * @param width The left margin in which to show channels
   * @return this
   */
  public RedwoodConfiguration channelWidth(final int width) {
    tasks.add(new Runnable() {
      @Override
      public void run() {
        RedwoodConfiguration.this.channelWidth = width;
      }
    });
    return this;
  }

  /**
   * Clear any custom configurations to Redwood
   * @return this
   */
  public RedwoodConfiguration clear(){
    this.tasks = new LinkedList<Runnable>();
    this.tasks.add(new Runnable() {
      public void run() {
        Redwood.clearHandlers();
        Redwood.restoreSystemStreams();
      }
    });
    this.outputHandler = Redwood.ConsoleHandler.out();
    return this;
  }



  public static interface Thunk {
    public void apply(RedwoodConfiguration config, Redwood.RecordHandlerTree root);
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Handlers {
    //
    // Leaf destinations
    //
    /**
     * Output to a file. This is a leaf node.
     * Consider using "defaultFile" instead.
     * @param path The file to write to
     */
    public static Thunk file(final String path) {
      return new Thunk() {
        @Override
        public void apply(final RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
          root.addChild(new Redwood.FileHandler(path){{ this.leftMargin = config.channelWidth; }});
        }
      };
    }
    /**
     * Output to a file. This is a leaf node.
     * Consider using "defaultFile" instead.
     * @param path The file to write to
     */
    public static Thunk file(File path) { return file(path.getPath()); }
    /**
     * Output to a file. This is a leaf node.
     * Consider using this instead of specifying a custom path.
     */
    public static final Thunk defaultFile = new Thunk() {
      @Override
      public void apply(final RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
        root.addChild(new Redwood.FileHandler(config.defaultFile.getPath()){{ this.leftMargin = config.channelWidth; }});
      }
    };
    /**
     * Output to a standard output. This is a leaf node.
     * Consider using "output" instead, unless you really
     * want to log only to stdout now and forever in the future.
     */
    public static final Thunk stdout = new Thunk() {
      @Override
      public void apply(RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
        Redwood.ConsoleHandler handler = Redwood.ConsoleHandler.out();
        handler.leftMargin = config.channelWidth;
        root.addChild(handler);
      }
    };
    /**
     * Output to a standard error. This is a leaf node.
     * Consider using "output" instead, unless you really
     * want to log only to stderr now and forever in the future.
     */
    public static final Thunk stderr = new Thunk() {
      @Override
      public void apply(RedwoodConfiguration config,Redwood.RecordHandlerTree root) {
        Redwood.ConsoleHandler handler = Redwood.ConsoleHandler.err();
        handler.leftMargin = config.channelWidth;
        root.addChild(handler);
      }
    };
    /**
     * Output to the default location specified by the output() method.
     * Consider using this rather than stderr or stdout.
     */
    public static final Thunk output = new Thunk() {
      @Override
      public void apply(RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
        config.outputHandler.leftMargin = config.channelWidth;
        root.addChild(config.outputHandler);
      }
    };

    //
    // Filters
    //
    /**
     * Hide the debug channel only.
     */
    public static final LogRecordHandler hideDebug = new VisibilityHandler() {{
      alsoHide(Redwood.DBG);
    }};
    /**
     * Hide the error channel only.
     */
    public static final LogRecordHandler hideError = new VisibilityHandler() {{
      alsoHide(Redwood.ERR);
    }};
    /**
     * Hide the warning channel only.
     */
    public static final LogRecordHandler hideWarn = new VisibilityHandler() {{
      alsoHide(Redwood.WARN);
    }};
    /**
     * Show only errors (e.g., to send them to an error file)
     */
    public static final LogRecordHandler showOnlyError = new VisibilityHandler() {{
      hideAll();
      alsoShow(Redwood.ERR);
    }};
    /**
     * Hide these channels, in addition to anything already hidden by upstream handlers.
     */
    public static LogRecordHandler hideChannels(final Object... channelsToHide) {
      return new VisibilityHandler() {{
        for (Object channel : channelsToHide) {
          alsoHide(channel);
        }
      }};
    }
    /**
     * Show only these channels, as far as downstream handlers are concerned.
     */
    public static LogRecordHandler showOnlyChannels(final Object... channelsToShow) {
      return new VisibilityHandler() {{
        hideAll();
        for (Object channel : channelsToShow) {
          alsoShow(channel);
        }
      }};
    }
    /**
     * Rename a channel to be something else
     */
    public static LogRecordHandler reroute(final Object src, final Object dst) {
      return new RerouteChannel(src, dst);
    }

    /**
     * Collapse records in a heuristic way to make reading easier. This is particularly relevant to branches which
     * go to a physical console, or a file which you'd like to keep small.
     */
    public static final LogRecordHandler collapseApproximate = new RepeatedRecordHandler(RepeatedRecordHandler.APPROXIMATE);
    /**
     * Collapse records which are duplicates into a single message, followed by a message detailing how many times
     * it was repeated.
     */
    public static final LogRecordHandler collapseExact = new RepeatedRecordHandler(RepeatedRecordHandler.EXACT);

    //
    // Combinators
    //
    /**
     * Send any incoming messages multiple ways.
     * For example, you may want to send the same output to console and a file.
     * @param destinations The destinations for log messages coming into this node.
     */
    public static Thunk branch(final Thunk... destinations) {
      return new Thunk() {
        @Override
        public void apply(RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
          for (Thunk destination : destinations) {
            destination.apply(config, root);
          }
        }
      };
    }

    /**
     * Apply each of the handlers to incoming log messages, in sequence.
     * @param handlers The handlers to apply
     * @param destination The final destination of the messages, after processing
     */
    public static Thunk chain(final LogRecordHandler[] handlers, final Thunk destination) {
      return new Thunk() {
        private Redwood.RecordHandlerTree buildChain(RedwoodConfiguration config, LogRecordHandler[] handlers, int i) {
          Redwood.RecordHandlerTree rtn = new Redwood.RecordHandlerTree(handlers[i]);
          if (i < handlers.length - 1) {
            rtn.addChildTree( buildChain(config, handlers, i + 1) );
          } else {
            destination.apply(config, rtn);
          }
          return rtn;
        }
        @Override
        public void apply(RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
          if (handlers.length == 0) {
            destination.apply(config, root);
          } else {
            root.addChildTree(buildChain(config, handlers, 0));
          }
        }
      };
    }

    /** @see Handlers#chain(LogRecordHandler[], Thunk) */
    public static Thunk chain(LogRecordHandler handler1, Thunk destination) { return chain(new LogRecordHandler[]{ handler1 }, destination); }
    /** @see Handlers#chain(LogRecordHandler[], Thunk) */
    public static Thunk chain(LogRecordHandler handler1, LogRecordHandler handler2, Thunk destination) { return chain(new LogRecordHandler[]{ handler1, handler2 }, destination); }
    /** @see Handlers#chain(LogRecordHandler[], Thunk) */
    public static Thunk chain(LogRecordHandler handler1, LogRecordHandler handler2, LogRecordHandler handler3, Thunk destination) { return chain(new LogRecordHandler[]{ handler1, handler2, handler3 }, destination); }
    /** @see Handlers#chain(LogRecordHandler[], Thunk) */
    public static Thunk chain(LogRecordHandler handler1, LogRecordHandler handler2, LogRecordHandler handler3, LogRecordHandler handler4, Thunk destination) { return chain(new LogRecordHandler[]{ handler1, handler2, handler3, handler4 }, destination); }
    /** @see Handlers#chain(LogRecordHandler[], Thunk) */
    public static Thunk chain(LogRecordHandler handler1, LogRecordHandler handler2, LogRecordHandler handler3, LogRecordHandler handler4, LogRecordHandler handler5, Thunk destination) { return chain(new LogRecordHandler[]{ handler1, handler2, handler3, handler4, handler5 }, destination); }


    /**
     * A NOOP, as the name implies. Useful for appending to the end of lists to make commas match.
     */
    public static Thunk noop = new Thunk() {
      @Override
      public void apply(RedwoodConfiguration config, Redwood.RecordHandlerTree root) {
      }
    };
  }

  /**
   * <p><Add handlers to Redwood. This is the main way to tell Redwood to do stuff.
   * Use this by calling a combination of methods in Handlers. It may be useful
   * to "import static RedwoodConfiguration.Handlers.*"</p>
   *
   * <p>For example:</p>
   * <pre>
   *   handlers(branch(
   *     chain( hideDebug, collapseApproximate, branch( output, file("stderr.log") ),
   *     chain( showOnlyError, file("err.log") ).
   *     chain( showOnlyChannels("results", "evaluate"), file("results.log") ),
   *     chain( file("redwood.log") ),
   *   noop))
   * </pre>
   *
   * @param paths A number of paths to add.
   * @return this
   */
  public RedwoodConfiguration handlers(Thunk... paths) {
    for (final Thunk thunk : paths) {
      tasks.add(new Runnable() {
        @Override
        public void run() {
          thunk.apply(RedwoodConfiguration.this, Redwood.rootHandler());
        }
      });
    }
    return this;
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
    return new RedwoodConfiguration().clear().handlers(
        Handlers.chain(Handlers.hideChannels(), Handlers.stderr)
    );
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
    Object cand = p.get(key);
    String rtn;
    if (cand == null) {
      rtn = p.getProperty(key, defaultValue);
    } else {
      rtn = cand.toString();
    }
    used.add(key);
    return rtn;
  }

  /**
   * Configure Redwood (from scratch) based on a Properties file.
   * Currently recognized properties are:
   * <ul>
   *   <li>log.captureStreams = {true,false}: Capture stdout and stderr and route them through Redwood</li>
   *   <li>log.captureStdout = {true,false}: Capture stdout and route it through Redwood</li>
   *   <li>log.captureStderr = {true,false}: Capture stdout and route it through Redwood</li>
   *   <li>log.channels.width = {number}: Show the channels being logged to, at this width (default: 0; recommended: 20)</li>
   *   <li>log.channels.debug = {true,false}: Show the debugging channel</li>
   *   <li>log.file = By default, write to this file.
   *   <li>log.neatExit = {true,false}: Clean up logs on exception or regular system exit</li>
   *   <li>log.output = {stderr,stdout,java.util.logging}: Output messages to either stderr or stdout by default.</li>
   * </ul>
   * @param props The properties to use in configuration
   * @return A new Redwood Configuration based on the passed properties, ignoring any existing custom configuration
   */
  public static RedwoodConfiguration parse(Properties props){
    RedwoodConfiguration config = new RedwoodConfiguration().clear();
    Set<String> used = Generics.newHashSet();

    //--Capture Streams
    if(get(props,"log.captureStreams","false",used).equalsIgnoreCase("true")){
      config = config.capture(System.out).capture(System.err);
    }
    if(get(props,"log.captureStdout","false",used).equalsIgnoreCase("true")){
      config = config.capture(System.out);
    }
    if(get(props,"log.captureStderr","false",used).equalsIgnoreCase("true")){
      config = config.capture(System.err);
    }

    //--Collapse
    String collapse = get(props, "log.collapse", "none", used);
    List<LogRecordHandler> chain = new LinkedList<LogRecordHandler>();
    if (collapse.equalsIgnoreCase("exact")) {
      chain.add(new RepeatedRecordHandler(RepeatedRecordHandler.EXACT));
    } else if (collapse.equalsIgnoreCase("approximate")) {
      chain.add(new RepeatedRecordHandler(RepeatedRecordHandler.APPROXIMATE));
    } else if (!collapse.equalsIgnoreCase("none")) {
      throw new IllegalArgumentException("Unknown collapse mode (Redwood): " + collapse);
    }

    //--Channels.Debug
    boolean debug = Boolean.parseBoolean(get(props, "log.channels.debug", "true", used));
    if (!debug) {
      chain.add(Handlers.hideDebug);
    }

    //--Channels.Width
    config.channelWidth( Integer.parseInt(get(props, "log.channels.width", "0", used)) );

    //--Neat exit
    if(get(props,"log.neatExit","false",used).equalsIgnoreCase("true")){
      config = config.neatExit();
    }

    //--File
    String outputFile = get(props, "log.file", null, used);
    if (outputFile != null) {
      config.defaultFile = new File(outputFile);
      config = config.handlers(Handlers.defaultFile);
    }

    //--Console
    config = config.output(get(props, "log.output", "stdout", used));

    //--Console
    config = config.handlers(Handlers.chain(chain.toArray(new LogRecordHandler[chain.size()]), Handlers.output));

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


  /*
  public static void main(String[] args) {
    RedwoodConfiguration.empty().neatExit().capture(System.out).capture(System.err)
        .channelWidth(20)
        .handlers(
            Handlers.chain(Handlers.hideDebug, Handlers.output),
            Handlers.file("/tmp/redwood.log"))
        .apply();
    Redwood.log("foo");
    Redwood.log(Redwood.DBG, "debug");
    System.out.println("Bar");
    System.err.println("Baz");
  }
  */
}
