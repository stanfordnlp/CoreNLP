package edu.stanford.nlp.process;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.TSVUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A callback function which operates on each line of a TSV file representing a collection of sentences.
 * This is a useful callback for processing a large batch of sentences; e.g., out of a Greenplum database.
 *
 * @author Gabor Angeli
 */
public interface TSVSentenceProcessor {

  /** A list of possible fields in the sentence table */
  enum SentenceField {
    ID,
    DEPENDENCIES_STANFORD,
    DEPENDENCIES_EXTRAS,
    DEPENDENCIES_MALT,
    DEPENDENCIES_MALT_ALT1,
    DEPENDENCIES_MALT_ALT2,
    WORDS,
    LEMMAS,
    POS_TAGS,
    NER_TAGS,
    DOC_ID,
    SENTENCE_INDEX,
    CORPUS_ID,
    DOC_CHAR_BEGIN,
    DOC_CHAR_END,
    GLOSS
  }

  /** The list of fields actually in the sentence table being passed as a query to ForEachSentence */
  List<SentenceField> DEFAULT_SENTENCE_TABLE = Collections.unmodifiableList(new ArrayList<SentenceField>() {{
    add(SentenceField.ID);
    add(SentenceField.DEPENDENCIES_STANFORD);
    add(SentenceField.DEPENDENCIES_EXTRAS);
    add(SentenceField.DEPENDENCIES_MALT);
    add(SentenceField.DEPENDENCIES_MALT_ALT1);
    add(SentenceField.DEPENDENCIES_MALT_ALT2);
    add(SentenceField.WORDS);
    add(SentenceField.LEMMAS);
    add(SentenceField.POS_TAGS);
    add(SentenceField.NER_TAGS);
    add(SentenceField.DOC_ID);
    add(SentenceField.SENTENCE_INDEX);
    add(SentenceField.CORPUS_ID);
    add(SentenceField.DOC_CHAR_BEGIN);
    add(SentenceField.DOC_CHAR_END);
    add(SentenceField.GLOSS);
  }});

  /**
   * Process a given sentence.
   *
   * @param id The sentence id (database id) of the sentence being processed.
   * @param doc The single-sentence document to annotate. This contains:
   *            <ul>
   *              <li>Tokens</li>
   *              <li>A parse tree (Collapsed dependencies)</li>
   *              <li>POS Tags</li>
   *              <li>NER tags</li>
   *              <li>Lemmas</li>
   *              <li>DocID</li>
   *              <li>Sentence index</li>
   *            </ul>
   */
  void process(long id, Annotation doc);


  /**
   * Runs the given implementation of ForEachSentence, and then exits with the appropriate error code
   * (that is, the number of exceptions encountered during processing)
   * @param in The input stream to read examples off of.
   * @param debugStream The stream to write debugging information to (e.g., stderr).
   * @param cleanup A function to run after annotation is over, to clean up open files, etc.
   *                Takes as input the candidate error code, and returns a new error code to exit on.
   * @param sentenceTableSpec The header of the sentence table fields being fed as input to this function.
   *                          By default, this can be {@link TSVSentenceProcessor#DEFAULT_SENTENCE_TABLE}.
   */
  default void runAndExit(InputStream in, PrintStream debugStream, Function<Integer, Integer> cleanup,
                          List<SentenceField> sentenceTableSpec) {
    int exceptions = 0;

    try {
      BufferedReader stdin = new BufferedReader(new InputStreamReader(in));
      String line;
      int linesProcessed = 0;
      long startTime = System.currentTimeMillis();

      while ((line = stdin.readLine()) != null) {
        long id = -1;
        try {
          // Parse line
          String[] fields = line.split("\t");
          id = Long.parseLong(fields[0]);

          // Create Annotation
          Annotation doc = TSVUtils.parseSentence(
              Optional.of(fields[sentenceTableSpec.indexOf(SentenceField.DOC_ID)]),
              Optional.of(fields[sentenceTableSpec.indexOf(SentenceField.SENTENCE_INDEX)]),
              fields[sentenceTableSpec.indexOf(SentenceField.GLOSS)],
              fields[sentenceTableSpec.indexOf(SentenceField.DEPENDENCIES_STANFORD)],
              fields[sentenceTableSpec.indexOf(SentenceField.DEPENDENCIES_MALT)],
              fields[sentenceTableSpec.indexOf(SentenceField.WORDS)],
              fields[sentenceTableSpec.indexOf(SentenceField.LEMMAS)],
              fields[sentenceTableSpec.indexOf(SentenceField.POS_TAGS)],
              fields[sentenceTableSpec.indexOf(SentenceField.NER_TAGS)],
              Optional.of(fields[sentenceTableSpec.indexOf(SentenceField.ID)])
          );

          // Process document
          process(id, doc);

          // Debug
          linesProcessed += 1;
          if (linesProcessed % 1000 == 0) {
            long currTime = System.currentTimeMillis();
            long sentPerSec = linesProcessed / ( (currTime - startTime)  / 1000 );
            debugStream.println("[" + Redwood.formatTimeDifference(currTime - startTime) + "] Processed " + linesProcessed + " sentences {" + sentPerSec + " sentences / second}... ");
          }
        } catch (Throwable t) {
          debugStream.println("CAUGHT EXCEPTION ON SENTENCE ID: " + id + " (-1 if not known)");
          t.printStackTrace(debugStream);
          exceptions += 1;
        }
      }

      // DONE
      debugStream.println("[" + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + "] DONE");
    } catch (Throwable t) {
      debugStream.println("FATAL EXCEPTION!");
      t.printStackTrace(debugStream);
      exceptions += 1;
    } finally {
      debugStream.flush();
      debugStream.close();
    }
    System.exit(cleanup.apply(exceptions));
  }

  /**
   * @see TSVSentenceProcessor#runAndExit(InputStream, PrintStream, Function, List)
   */
  default void runAndExit(InputStream in, PrintStream debugStream, Function<Integer, Integer> cleanup) {
    runAndExit(in, debugStream, cleanup, DEFAULT_SENTENCE_TABLE);
  }

}
