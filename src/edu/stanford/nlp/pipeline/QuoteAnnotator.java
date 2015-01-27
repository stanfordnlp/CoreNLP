package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.MultiTokenTag;
import edu.stanford.nlp.ling.tokensregex.SequenceMatcher;
import edu.stanford.nlp.process.ListProcessor;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Timing;

import java.util.*;

/**
 * @author Grace Muzny
 */
public class QuoteAnnotator implements Annotator {

  private final boolean VERBOSE;
  private final boolean DEBUG = true;



  public QuoteAnnotator() {
    this(true);
  }

  public QuoteAnnotator(boolean verbose) {
    VERBOSE = verbose;
    Timing timer = null;
    if (VERBOSE) {
      timer = new Timing();
      System.err.print("Preparing quote annotator...");
    }
    if (VERBOSE) {
      timer.stop("done.");
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      System.err.print("Adding Quote annotation...");
    }

    if (annotation.containsKey(CoreAnnotations.TokensAnnotation.class)) {
      // get text and tokens from the document
      String text = annotation.get(CoreAnnotations.TextAnnotation.class);
      String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      // System.err.println("Tokens are: " + tokens);


      boolean inQuote = false;
      boolean newline = false;
      for (CoreLabel token: tokens) {
        // both `` and '' can be the starts or the ends of quotes
        if (isAnyQuote(token)) {
          // If we weren't in a quote before we probably are now
          if (!inQuote) {
            inQuote = true;
          }
        }
      }

      if (VERBOSE) {
        System.err.println("done. Output: " + tokens);
      }

      // assemble the quote annotations
      int tokenOffset = 0;
      int lineNumber = 0;
      // section annotations to mark sentences with
      CoreMap sectionAnnotations = null;
      List<CoreMap> quotes = new ArrayList<CoreMap>();
      List<List<CoreLabel>> quoteTokensOverall = process(tokens);
      System.out.println(quoteTokensOverall);
      for (List<CoreLabel> quoteTokens: quoteTokensOverall) {
        if (quoteTokens.isEmpty()) {
          continue;
        }

        // get the quote text from the first and last character offsets
        int begin = quoteTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int last = quoteTokens.size() - 1;
        int end = quoteTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        String quoteText = text.substring(begin, end);

        // create a quote annotation with text and token offsets
        Annotation quote = new Annotation(quoteText);
        quote.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
        quote.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
        quote.set(CoreAnnotations.TokensAnnotation.class, quoteTokens);
        quote.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
        tokenOffset += quoteTokens.size();
        quote.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset);
        quote.set(CoreAnnotations.SentenceIndexAnnotation.class, quotes.size());


        // Annotate sentence with section information.
        // Assume section start and end appear as first and last tokens of sentence
        CoreLabel quoteStartToken = quoteTokens.get(0);
        CoreLabel quoteEndToken = quoteTokens.get(quoteTokens.size() - 1);

        CoreMap sectionStart = quoteStartToken.get(CoreAnnotations.SectionStartAnnotation.class);
        if (sectionStart != null) {
          // Section is started
          sectionAnnotations = sectionStart;
        }
        if (sectionAnnotations != null) {
          // transfer annotations over to quote
          ChunkAnnotationUtils.copyUnsetAnnotations(sectionAnnotations, quote);
        }
        String sectionEnd = quoteEndToken.get(CoreAnnotations.SectionEndAnnotation.class);
        if (sectionEnd != null) {
          sectionAnnotations = null;
        }

        if (docID != null) {
          quote.set(CoreAnnotations.DocIDAnnotation.class, docID);
        }

        int index = 1;
        for (CoreLabel token : quoteTokens) {
          token.setIndex(index++);
          token.setSentIndex(quotes.size());
          if (docID != null) {
            token.setDocID(docID);
          }
        }

        // add the sentence to the list
        quotes.add(quote);
      }

      // add the quotations annotations to the document
      annotation.set(CoreAnnotations.QuotationsAnnotation.class, quotes);
    } else {
      throw new RuntimeException("unable to find tokens in: " + annotation);
    }
  }

  /**
   * Returns a List of Lists where each element is built from a run
   * of Words in the input Document. Specifically, reads through each word in
   * the input document and breaks off a quote after finding a valid
   * ending quote token or end of file.
   * Note that for this to work, the words in the
   * input document must have been tokenized with a tokenizer that makes
   * quotes their own tokens (e.g., {@link edu.stanford.nlp.process.PTBTokenizer}).
   *
   * @param words A list of already tokenized words (must implement HasWord or be a String).
   * @return A list of quotes.
   */
  public List<List<CoreLabel>> process(List<CoreLabel> words) {
    IdentityHashMap<Object, Boolean> isSentenceBoundary = null; // is null unless used by sentenceBoundaryMultiTokenPattern

    // Split tokens into sentences!!!
    List<List<CoreLabel>> quotes = Generics.newArrayList();
    List<CoreLabel> currentQuote = new ArrayList<>();
    boolean insideRegion = false;
    boolean lastTokenWasNewline = false;
    for (CoreLabel o: words) {
      boolean isQuote = isAnyQuote(o);
      String word = o.word();

      if (!insideRegion) {
        // Enter a quote region
        if (isQuote) {
          insideRegion = true;
          if (DEBUG) {
            System.err.println("  beginning region");
          }
        }
      }

      if (DEBUG) {
        EncodingPrintWriter.err.print(word + " ", "UTF-8");
      }

      if (insideRegion) {
        currentQuote.add(o);
      } else {
        if (DEBUG) {
          System.err.println("  outside region; deleted");
        }
      }

      // See if we should stop
      //TODO: make this wayyyyyy less hacky
      if (insideRegion && isQuote &&
          currentQuote.size() != 1 &&
          !lastTokenWasNewline) {
        if (DEBUG) {
          System.err.println("  ending current sentence");
        }
        insideRegion = false;
        quotes.add(currentQuote);
        currentQuote = new ArrayList<>();
      }

      // now allow lines to begin with quotes and not end the current one
      if (insideRegion && isNewline(o)) {
        lastTokenWasNewline = true;
      }

    }

    // add any unfinished quotes at the end
    if (! currentQuote.isEmpty()) {
      quotes.add(currentQuote); // adds last sentence
    }
    return quotes;
  }


  private static boolean isAnyQuote(CoreLabel token) {
    return token.word().equals("``") || token.word().equals("''");
  }

  private static boolean isNewline(CoreLabel token) {
    //TODO replace with appropriate constant
    return token.word().equals("*NL*");
  }

  @Override
  public Set<Requirement> requires() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(QUOTE_REQUIREMENT);
  }

}
