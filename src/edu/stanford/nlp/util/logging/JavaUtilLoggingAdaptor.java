package edu.stanford.nlp.util.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


/**
 * Reroutes java.util.logging messages to the Redwood logging system.
 *
 * @author David McClosky
 */
public class JavaUtilLoggingAdaptor {

  private static boolean addedRedwoodHandler; // = false;

  private JavaUtilLoggingAdaptor() {
  }

  public static void adapt() {
    // get the top Logger:
    Logger topLogger = Logger.getLogger("");
    Handler oldConsoleHandler = null;

    // see if there is already a console handler
    // hopefully reasonable assumption: there's only one ConsoleHandler
    // TODO confirm that this will always give us all handlers (i.e. do we need to loop over all Loggers in java.util.LogManager and do this for each one?)
    for (Handler handler : topLogger.getHandlers()) {
      if (handler instanceof ConsoleHandler && !(handler instanceof RedwoodHandler)) {
        // found the console handler
        oldConsoleHandler = handler;
        break;
      }
    }

    if (oldConsoleHandler != null) {
      // it's safe to call this after it's been removed
      topLogger.removeHandler(oldConsoleHandler);
    }

    if (!addedRedwoodHandler) {
      Handler redwoodHandler = new JavaUtilLoggingAdaptor.RedwoodHandler();
      topLogger.addHandler(redwoodHandler);
      addedRedwoodHandler = true;
    }
  }

  /**
   * This is the bridge class which actually adapts java.util.logging calls to Redwood calls.
   */
  public static class RedwoodHandler extends ConsoleHandler {

    /**
     * This is a no-op since Redwood doesn't have this.
     */
    @Override
    public void close() throws SecurityException {
    }

    /**
     * This is a no-op since Redwood doesn't have this.
     */
    @Override
    public void flush() {
    }

    /**
     * Convert a java.util.logging call to its equivalent Redwood logging call.
     * Currently, the WARNING log level becomes Redwood WARNING flag, the SEVERE log level becomes Redwood.ERR, and anything at FINE or lower becomes Redwood.DBG
     * CONFIG and INFO don't map to a Redwood tag.
     */
    @Override
    public void publish(LogRecord record) {
      String message = record.getMessage();
      Level level = record.getLevel();
      Object tag = null;
      if (level == Level.WARNING) {
        tag = Redwood.WARN;
      } else if (level == Level.SEVERE) {
        tag = Redwood.ERR;
      } else if (level.intValue() <= Level.FINE.intValue()) {
        tag = Redwood.DBG;
      }

      if (tag == null) {
        Redwood.log(message);
      } else {
        Redwood.log(tag, message);
      }
    }

  }

  /**
   * Simple test case.
   */
  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("redwood")) {
      Redwood.log(Redwood.DBG, "at the top");
      Redwood.startTrack("Adaptor test controlled by redwood");

      Logger topLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
      topLogger.warning("I'm warning you!");
      topLogger.severe("Now I'm using my severe voice.");
      topLogger.info("FYI");

      Redwood.log(Redwood.DBG, "adapting");
      JavaUtilLoggingAdaptor.adapt();
      topLogger.warning("I'm warning you in Redwood!");
      JavaUtilLoggingAdaptor.adapt(); // should be safe to call this twice
      topLogger.severe("Now I'm using my severe voice in Redwood!");
      topLogger.info("FYI: Redwood rocks");

      // make sure original java.util.logging levels are respected
      topLogger.setLevel(Level.OFF);
      topLogger.severe("We shouldn't see this message.");

      Redwood.log(Redwood.DBG, "at the bottom");
      Redwood.endTrack("Adaptor test controlled by redwood");
    } else {
      // Reverse mapping
      Logger topLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); // Can be Logger.getGlobal() in jdk1.7
      // topLogger.addHandler(new ConsoleHandler());
      Logger logger = Logger.getLogger(JavaUtilLoggingAdaptor.class.getName());
      topLogger.info("Starting test");
      logger.log(Level.INFO, "Hello from the class logger");

      Redwood.log("Hello from Redwood!");
      Redwood.rootHandler().addChild(
        RedirectOutputHandler.fromJavaUtilLogging(topLogger));
      Redwood.log("Hello from Redwood -> Java!");
      Redwood.log("Hello from Redwood -> Java again!");
      logger.log(Level.INFO, "Hello again from the class logger");
      Redwood.startTrack("a track");
      Redwood.log("Inside a track");
      logger.log(Level.INFO, "Hello a third time from the class logger");
      Redwood.endTrack("a track");
      logger.log(Level.INFO, "Hello a fourth time from the class logger");
    }
  }

}
