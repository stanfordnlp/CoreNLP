package edu.stanford.nlp.util.logging;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility class for logging using slf4j. This is a library class and cannot be instantiated.
 *
 * @author Victor Zhong (vzhong@cs.stanford.edu)
 */
public class Logging {

    private Logging() {}

    private static String getCallerClassName() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i=1; i<stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().equals(Logging.class.getName()) && ste.getClassName().indexOf("java.lang.Thread")!=0) {
                return ste.getClassName();
            }
        }
        return null;
    }

    public static Logger logger(Class c) {
        return LoggerFactory.getLogger(c.getClass().getName());
    }

    public static Logger logger() {
        return LoggerFactory.getLogger(getCallerClassName());
    }
}
