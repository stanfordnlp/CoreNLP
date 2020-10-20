/**
 * Contains the Redwood logging system that is the primary logging facade or system for Stanford CoreNLP.
 * <p>
 * With use of Redwood, logging will be done via another logger/facade like slf4j if it is available, but
 * the system will run fine with no logging libraries on the classpath.
 * <p>
 * Redwood also has some particular features such as logging tracks.
 *
 * @author Gabor Angeli
 */
package edu.stanford.nlp.util.logging;