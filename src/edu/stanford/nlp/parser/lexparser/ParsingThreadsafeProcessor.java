package edu.stanford.nlp.parser.lexparser;

import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Takes a sentence and returns a ParserQuery with the given sentence parsed.  
 * Can be used in a MulticoreWrapper.
 *
 * @author John Bauer
 */
class ParsingThreadsafeProcessor implements ThreadsafeProcessor<List<? extends HasWord>, ParserQuery> {
  ParserQueryFactory pqFactory;
  PrintWriter pwErr;

  ParsingThreadsafeProcessor(ParserQueryFactory pqFactory) {
    this(pqFactory, null);
  }

  ParsingThreadsafeProcessor(ParserQueryFactory pqFactory, PrintWriter pwErr) {
    this.pqFactory = pqFactory;
    this.pwErr = pwErr;
  }

  @Override
  public ParserQuery process(List<? extends HasWord> sentence) {
    ParserQuery pq = pqFactory.parserQuery();
    if (pwErr != null) {
      pq.parseAndReport(sentence, pwErr);
    } else {
      pq.parse(sentence);
    }
    return pq;
  }

  @Override
  public ThreadsafeProcessor<List<? extends HasWord>, ParserQuery> newInstance() {
    // ParserQueryFactories should be threadsafe
    return this;
  }
}
