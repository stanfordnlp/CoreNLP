package edu.stanford.nlp.util.logging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.util.Generics;

/**
 *  A class to redirect the output of Redwood to another logging mechanism,
 *  e.g., java.util.logging.
 *
 *  @author Gabor Angeli
 */
public class RedirectOutputHandler<LoggerClass, ChannelEquivalent> extends OutputHandler {

  public final LoggerClass logger;
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
   * @inheritDoc
   */
  @Override
  protected boolean formatChannel(StringBuilder b, String channelStr, Object channel){
    return !(channelMapping != null && channelMapping.containsKey(channel));
  }

  //
  // LOGGER IMPLEMENTATIONS
  //

  public static RedirectOutputHandler<Logger, Level> fromJavaUtilLogging(Logger logger) {
    Map <Object, Level> channelMapping = Generics.newHashMap();
    channelMapping.put(Redwood.WARN, Level.WARNING);
    channelMapping.put(Redwood.DBG, Level.FINE);
    channelMapping.put(Redwood.ERR, Level.SEVERE);
    try {
      return new RedirectOutputHandler<Logger, Level>(
          logger,
          Logger.class.getMethod("log", Level.class, String.class),
          channelMapping,
          Level.INFO
          );
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

}
