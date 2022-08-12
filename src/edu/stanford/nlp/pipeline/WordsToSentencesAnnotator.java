package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * This class assumes that there is a {@code List<CoreLabel>} under the {@code TokensAnnotation} field,
 * and runs it through {@link edu.stanford.nlp.process.WordToSentenceProcessor}
 * and puts the new {@code List<Annotation>} under the {@code SentencesAnnotation} field.
 *
 * @author Jenny Finkel
 * @author Christopher Manning
 */
public class WordsToSentencesAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(WordsToSentencesAnnotator.class);

  private final WordToSentenceProcessor<CoreLabel> wts;

  private final boolean VERBOSE;

  private final boolean countLineNumbers;

  private boolean loggedExtraSplit = false;

  public WordsToSentencesAnnotator() {
    this(false);
  }


  public WordsToSentencesAnnotator(Properties properties) {
    boolean nlSplitting = Boolean.parseBoolean(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
    if (nlSplitting) {
      boolean whitespaceTokenization = Boolean.parseBoolean(properties.getProperty("tokenize.whitespace", "false"));
      WordToSentenceProcessor<CoreLabel> wts1;
      if (whitespaceTokenization) {
        if (System.lineSeparator().equals("\n")) {
          // this constructor will keep empty lines as empty sentences
          wts1 = new WordToSentenceProcessor<>(ArrayUtils.asImmutableSet(new String[]{"\n", AbstractTokenizer.NEWLINE_TOKEN}));
        } else {
          // throw "\n" in just in case files use that instead of
          // the system separator
          // this constructor will keep empty lines as empty sentences
          wts1 = new WordToSentenceProcessor<>(ArrayUtils.asImmutableSet(new String[]{System.lineSeparator(), "\n", AbstractTokenizer.NEWLINE_TOKEN}));
        }
      } else {
        // this constructor will keep empty lines as empty sentences
        wts1 = new WordToSentenceProcessor<>(ArrayUtils.asImmutableSet(new String[]{PTBTokenizer.getNewlineToken()}));
      }
      this.countLineNumbers = true;
      this.wts = wts1;

    } else {
      String isOneSentence = properties.getProperty("ssplit.isOneSentence");
      if (Boolean.parseBoolean(isOneSentence)) { // this method treats null as false
        // Treat as one sentence: You get a no-op sentence splitter that always returns all tokens as one sentence.
        WordToSentenceProcessor<CoreLabel> wts1 = new WordToSentenceProcessor<>(true);
        this.countLineNumbers = false;
        this.wts = wts1;

      } else {
        // multi token sentence boundaries
        String boundaryMultiTokenRegex = properties.getProperty("ssplit.boundaryMultiTokenRegex");

        // Discard these tokens without marking them as sentence boundaries
        String tokenPatternsToDiscardProp = properties.getProperty("ssplit.tokenPatternsToDiscard");
        Set<String> tokenRegexesToDiscard = null;
        if (tokenPatternsToDiscardProp != null) {
          String[] toks = tokenPatternsToDiscardProp.split(",");
          tokenRegexesToDiscard = Generics.newHashSet(Arrays.asList(toks));
        }
        // regular boundaries
        String boundaryTokenRegex = properties.getProperty("ssplit.boundaryTokenRegex");

        String boundaryFollowersRegex = properties.getProperty("ssplit.boundaryFollowersRegex");

        // newline boundaries which are discarded.
        Set<String> boundariesToDiscard = null;
        String bounds = properties.getProperty("ssplit.boundariesToDiscard");
        if (bounds != null) {
          String[] toks = bounds.split(",");
          boundariesToDiscard = Generics.newHashSet(Arrays.asList(toks));
        }
        Set<String> htmlElementsToDiscard = null;
        // HTML boundaries which are discarded
        bounds = properties.getProperty("ssplit.htmlBoundariesToDiscard");
        if (bounds != null) {
          String[] elements = bounds.split(",");
          htmlElementsToDiscard = Generics.newHashSet(Arrays.asList(elements));
        }
        String nlsb = properties.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY,
            StanfordCoreNLP.DEFAULT_NEWLINE_IS_SENTENCE_BREAK);

        this.countLineNumbers = false;
        this.wts = new WordToSentenceProcessor<>(boundaryTokenRegex, boundaryFollowersRegex,
            boundariesToDiscard, htmlElementsToDiscard,
            WordToSentenceProcessor.stringToNewlineIsSentenceBreak(nlsb),
            (boundaryMultiTokenRegex != null) ? TokenSequencePattern.compile(boundaryMultiTokenRegex) : null, tokenRegexesToDiscard);
      }
    }
    VERBOSE = Boolean.parseBoolean(properties.getProperty("ssplit.verbose", "false"));
  }

  public WordsToSentencesAnnotator(boolean verbose) {
    this(verbose, false, new WordToSentenceProcessor<>());
  }

  public WordsToSentencesAnnotator(boolean verbose, String boundaryTokenRegex,
                                   Set<String> boundaryToDiscard, Set<String> htmlElementsToDiscard,
                                   String newlineIsSentenceBreak, String boundaryMultiTokenRegex,
                                   Set<String> tokenRegexesToDiscard) {
    this(verbose, false,
            new WordToSentenceProcessor<>(boundaryTokenRegex, null,
                    boundaryToDiscard, htmlElementsToDiscard,
                    WordToSentenceProcessor.stringToNewlineIsSentenceBreak(newlineIsSentenceBreak),
                    (boundaryMultiTokenRegex != null) ? TokenSequencePattern.compile(boundaryMultiTokenRegex) : null, tokenRegexesToDiscard));
  }

  private WordsToSentencesAnnotator(boolean verbose, boolean countLineNumbers,
                                    WordToSentenceProcessor<CoreLabel> wts) {
    VERBOSE = verbose;
    this.countLineNumbers = countLineNumbers;
    this.wts = wts;
  }


  /** Return a WordsToSentencesAnnotator that splits on newlines (only), which are then deleted.
   *  This constructor counts the lines by putting in empty token lists for empty lines.
   *  It tells the underlying splitter to return empty lists of tokens
   *  and then treats those empty lists as empty lines.  We don't
   *  actually include empty sentences in the annotation, though. But they
   *  are used in numbering the sentence. Only this constructor leads to
   *  empty sentences.
   *
   *  @param  nlToken Zero or more new line tokens, which might be a {@literal \n} or the fake
   *                 newline tokens returned from the tokenizer.
   *  @return A WordsToSentenceAnnotator.
   */
  public static WordsToSentencesAnnotator newlineSplitter(String... nlToken) {
    // this constructor will keep empty lines as empty sentences
    WordToSentenceProcessor<CoreLabel> wts =
            new WordToSentenceProcessor<>(ArrayUtils.asImmutableSet(nlToken));
    return new WordsToSentencesAnnotator(false, true, wts);
  }


  /** Return a WordsToSentencesAnnotator that never splits the token stream. You just get one sentence.
   *
   *  @return A WordsToSentenceAnnotator.
   */
  public static WordsToSentencesAnnotator nonSplitter() {
    WordToSentenceProcessor<CoreLabel> wts = new WordToSentenceProcessor<>(true);
    return new WordsToSentencesAnnotator(false, false, wts);
  }


  /**
   * If setCountLineNumbers is set to true, we count line numbers by
   * telling the underlying splitter to return empty lists of tokens
   * and then treating those empty lists as empty lines.  We don't
   * actually include empty sentences in the annotation, though.
   */
  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Sentence splitting ... " + annotation);
    }
    if (!annotation.containsKey(CoreAnnotations.TokensAnnotation.class)) {
      throw new IllegalArgumentException("WordsToSentencesAnnotator: unable to find words/tokens in: " + annotation);
    }

    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      if (!loggedExtraSplit) {
        log.error("Multiple WordsToSentencesAnnotator or other sentence splitters are operating on this document!");
        loggedExtraSplit = true;
      }
      return;
    }

    // get text and tokens from the document
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    if (VERBOSE) {
      log.info("Tokens are: " + tokens);
    }

    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
    // assemble the sentence annotations
    int lineNumber = 0;
    // section annotations to mark sentences with
    CoreMap sectionAnnotations = null;
    List<CoreMap> sentences = new ArrayList<>();
    // keep track of current section to assign sentences to sections
    int currSectionIndex = 0;
    List<CoreMap> sections = annotation.get(CoreAnnotations.SectionsAnnotation.class);
    for (List<CoreLabel> sentenceTokens: wts.process(tokens)) {
      if (countLineNumbers) {
        ++lineNumber;
      }
      if (sentenceTokens.isEmpty()) {
        if (!countLineNumbers) {
          throw new IllegalStateException("unexpected empty sentence: " + sentenceTokens);
        } else {
          continue;
        }
      }

      // get the sentence text from the first and last character offsets
      int begin = sentenceTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int last = sentenceTokens.size() - 1;
      int end = sentenceTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      String sentenceText = text.substring(begin, end);

      // create a sentence annotation with text and token offsets
      Annotation sentence = new Annotation(sentenceText);
      sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
      sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
      sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
      sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentences.size());

      if (countLineNumbers) {
        sentence.set(CoreAnnotations.LineNumberAnnotation.class, lineNumber);
      }

      // Annotate sentence with section information.
      // Assume section start and end appear as first and last tokens of sentence
      CoreLabel sentenceStartToken = sentenceTokens.get(0);
      CoreLabel sentenceEndToken = sentenceTokens.get(sentenceTokens.size()-1);

      CoreMap sectionStart = sentenceStartToken.get(CoreAnnotations.SectionStartAnnotation.class);
      if (sectionStart != null) {
        // Section is started
        sectionAnnotations = sectionStart;
      }
      if (sectionAnnotations != null) {
        // transfer annotations over to sentence
        ChunkAnnotationUtils.copyUnsetAnnotations(sectionAnnotations, sentence);
      }
      String sectionEnd = sentenceEndToken.get(CoreAnnotations.SectionEndAnnotation.class);
      if (sectionEnd != null) {
        sectionAnnotations = null;
      }

      // determine section index for this sentence if keeping track of sections
      if (sections != null) {
        // try to find a section that ends after this sentence ends, check if it encloses sentence
        // if it doesn't, that means this sentence is in two sections
        while (currSectionIndex < sections.size()) {
          int currSectionCharBegin = sections.get(currSectionIndex).get(
              CoreAnnotations.CharacterOffsetBeginAnnotation.class);
          int currSectionCharEnd = sections.get(currSectionIndex).get(
              CoreAnnotations.CharacterOffsetEndAnnotation.class);
          if (currSectionCharEnd < end) {
            currSectionIndex++;
          } else {
            // if the sentence falls in this current section, link it to this section
            if (currSectionCharBegin <= begin) {
              // ... but first check if it's in one of this sections quotes!
              // if so mark it as quoted
              for (CoreMap sectionQuote : sections.get(currSectionIndex).get(CoreAnnotations.QuotesAnnotation.class)) {
                if (sectionQuote.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) <= begin &&
                    end <= sectionQuote.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
                  sentence.set(CoreAnnotations.QuotedAnnotation.class, true);
                  // set the author to the quote author
                  sentence.set(CoreAnnotations.AuthorAnnotation.class,
                      sectionQuote.get(CoreAnnotations.AuthorAnnotation.class));
                }
              }
              // add the sentence to the section's sentence list
              sections.get(currSectionIndex).get(CoreAnnotations.SentencesAnnotation.class).add(sentence);
              // set sentence's section date
              String sectionDate = sections.get(currSectionIndex).get(CoreAnnotations.SectionDateAnnotation.class);
              sentence.set(CoreAnnotations.SectionDateAnnotation.class, sectionDate);
              // set sentence's section index
              sentence.set(CoreAnnotations.SectionIndexAnnotation.class, currSectionIndex);
            }
            break;
          }
        }
      }

      if (docID != null) {
        sentence.set(CoreAnnotations.DocIDAnnotation.class, docID);
      }

      int index = 1;
      for (CoreLabel token : sentenceTokens) {
        token.setIndex(index++);
        token.setSentIndex(sentences.size());
        if (docID != null) {
          token.setDocID(docID);
        }
      }

      // add the sentence to the list
      sentences.add(sentence);
    }

    // after sentence splitting, remove newline tokens, set token and
    // sentence indexes, and update before and after text appropriately
    // at end of this annotator, it should be as though newline tokens
    // were never used
    // reset token indexes
    List<CoreLabel> finalTokens = new ArrayList<>();
    int tokenIndex = 0;
    CoreLabel prevToken = null;
    for (CoreLabel currToken : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      if (!currToken.isNewline()) {
        finalTokens.add(currToken);
        currToken.set(CoreAnnotations.TokenBeginAnnotation.class, tokenIndex);
        currToken.set(CoreAnnotations.TokenEndAnnotation.class, tokenIndex + 1);
        tokenIndex++;
        // fix before text for this token
        if (prevToken != null && prevToken.isNewline() &&
            currToken.get(CoreAnnotations.BeforeAnnotation.class) != null) {
          String prevNewlineTokenText = prevToken.get(CoreAnnotations.OriginalTextAnnotation.class);
          currToken.set(CoreAnnotations.BeforeAnnotation.class, prevNewlineTokenText);
        }
      } else {
        String newlineText = currToken.get(CoreAnnotations.OriginalTextAnnotation.class);
        // fix after text for last token
        if (prevToken != null && prevToken.get(CoreAnnotations.AfterAnnotation.class) != null) {
          prevToken.set(CoreAnnotations.AfterAnnotation.class, newlineText);
        }
      }
      prevToken = currToken;
    }
    annotation.set(CoreAnnotations.TokensAnnotation.class, finalTokens);
    // set sentence token begin and token end values
    for (CoreMap sentence : sentences) {
      List<CoreLabel> sentenceTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      int sentenceTokenBegin = sentenceTokens.get(0).get(CoreAnnotations.TokenBeginAnnotation.class);
      int sentenceTokenEnd = sentenceTokens.get(sentenceTokens.size()-1).get(
          CoreAnnotations.TokenEndAnnotation.class);
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class, sentenceTokenBegin);
      sentence.set(CoreAnnotations.TokenEndAnnotation.class, sentenceTokenEnd);
    }

    // add the sentences annotations to the document
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.ValueAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.IsNewlineAnnotation.class,
        CoreAnnotations.TokenBeginAnnotation.class,
        CoreAnnotations.TokenEndAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return new HashSet<>(Arrays.asList(
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class
    ));
  }

}
