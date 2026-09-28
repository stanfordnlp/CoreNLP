package edu.stanford.nlp.util.logging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import edu.stanford.nlp.util.Generics;

/**
 *  A class to redirect the output of Redwood to another logging mechanism,
 *  e.g., java.util.logging.
 *
 *  @author Gabor Angeli
 *  @param <LoggerClass> The type of the logger to redirect to
 *  @param <ChannelEquivalent> The type the logger uses in place of Redwood channels,
 *      such as java.util.logging.Level
 */
public class RedirectOutputHandler<LoggerClass, ChannelEquivalent> extends OutputHandler {

  /** The logger to send output to. */
  public final LoggerClass logger;
  /** The method invoked on {@link #logger} to log each line. */
  public final Method loggingMethod;
  private final Map<Object, ChannelEquivalent> channelMapping;
  private final ChannelEquivalent defaultChannel;

  /**
   * Create a redirect handler, with a logging class, ignoring logging
   * levels.
   * @param logger The class to use for logging. For example, java.util.logging.Logger
   * @param loggingMethod A method which takes a *single* String argument
   *                         and logs that string using the |logger| class.
   */
  public RedirectOutputHandler(LoggerClass logger, Method loggingMethod) {
    this(logger, loggingMethod, null, null);
  }

  /**
   * Create a redirect handler, with a logging class, redirecting both the logging
   * message, and the channel that it came from
   * @param logger The class to use for logging. For example,
   *                 java.util.logging.Logger
   * @param loggingMethod A method which takes a *single* String argument
   *                         and logs that string using the |logger| class.
   * @param channelMapping The mapping from Redwood channels, to the native Channel equivalent.
   *                       If null, channels are ignored and loggingMethod is called with only the line.
   * @param defaultChannel The native channel to use for a message whose channels
   *                       are not in channelMapping.  If channelMapping is non-null, loggingMethod
   *                       must take this channel type and a String.
   */
  public RedirectOutputHandler(LoggerClass logger, Method loggingMethod,
                               Map<Object, ChannelEquivalent> channelMapping,
                               ChannelEquivalent defaultChannel) {
    this.logger = logger;
    this.loggingMethod = loggingMethod;
    this.channelMapping = channelMapping;
    this.defaultChannel = defaultChannel;
  }

  private boolean shouldLogChannels() {
    return channelMapping != null;
  }

  @Override
  public void print(Object[] channels, String line) {
    if (line.endsWith("\n")) {
      line = line.substring(0, line.length() - 1);
    }
    if (shouldLogChannels()) {
      // -- Case: log with channel
      // (get channel to publish on)
      ChannelEquivalent channel = null;
      if (channels == null) {
        // (case: no channel provided)
        channel = defaultChannel;
      } else {
        for (Object candidate : channels) {
          if (channel == null) {
            // (case: channel found in mapping)
            channel = channelMapping.get(candidate);
          }
        }
        if (channel == null) {
          // (case: no channel found in mapping)
          channel = this.defaultChannel;
        }
      }
      // (publish message)
      try {
        this.loggingMethod.invoke(this.logger, channel, line);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException(e.getCause());
      }
    } else {
      // -- Case: log without channel
      try {
        this.loggingMethod.invoke(this.logger, line);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException(e.getCause());
      }
    }
  }

  /**
   * Ensure that we don't print duplicate channels when adapting to another logging framework.
   */
  @Override
  protected boolean formatChannel(StringBuilder b, String channelStr, Object channel){
    return !(channelMapping != null && channelMapping.containsKey(channel));
  }

  //
  // LOGGER IMPLEMENTATIONS
  //

  /**
   * Creates a handler which redirects output to a java.util.logging Logger.
   * Redwood's WARN, DBG and ERR channels map to the WARNING, FINE and SEVERE levels;
   * other messages are logged at level INFO.
   *
   * @param logger The logger to send output to
   * @return A new handler
   */
  public static RedirectOutputHandler<java.util.logging.Logger, java.util.logging.Level> fromJavaUtilLogging(java.util.logging.Logger logger) {
    Map <Object, java.util.logging.Level> channelMapping = Generics.newHashMap();
    channelMapping.put(Redwood.WARN, java.util.logging.Level.WARNING);
    channelMapping.put(Redwood.DBG,  java.util.logging.Level.FINE);
    channelMapping.put(Redwood.ERR,  java.util.logging.Level.SEVERE);
    try {
      return new RedirectOutputHandler<>(
              logger,
              java.util.logging.Logger.class.getMethod("log", java.util.logging.Level.class, String.class),
              channelMapping,
              java.util.logging.Level.INFO
      );
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
