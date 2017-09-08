package old.edu.stanford.nlp.process;

import java.util.*;
import java.util.regex.Pattern;

import old.edu.stanford.nlp.io.EncodingPrintWriter;
import old.edu.stanford.nlp.ling.Document;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.ling.CoreAnnotations;
import old.edu.stanford.nlp.util.CoreMap;
import old.edu.stanford.nlp.util.Generics;

/**
 * Transforms a Document of Words into a Document of Sentences by grouping the
 * Words.  The word stream is assumed to already be adequately tokenized,
 * and this class just divides the list into sentences, perhaps discarding
 * some separator tokens based on the setting of the following three sets:
 * <ul>
 * <li>sentenceBoundaryTokens are tokens that are left in a sentence, but are
 * to be regarded as ending a sentence.  A canonical example is a period.
 * If two of these follow each other, the second will be a sentence
 * consisting of only the sentenceBoundaryToken.
 * <li>sentenceBoundaryFollowers are tokens that are left in a sentence, and
 * which can follow a sentenceBoundaryToken while still belonging to
 * the previous sentence.  They cannot begin a sentence (except at the
 * beginning of a document).  A canonical example is a close parenthesis
 * ')'.
 * <li>sentenceBoundaryToDiscard are tokens which separate sentences and
 * which should be thrown away.  In web documents, a typical example would
 * be a '{@code <p>}' tag.  If two of these follow each other, they are
 * coalesced: no empty Sentence is output.  The end-of-file is not
 * represented in this Set, but the code behaves as if it were a member.
 * <li>sentenceRegionBeginPattern A regular expression for marking the start
 * of a sentence region.  Not included in the sentence.
 * <li>sentenceRegionEndPattern A regular expression for marking the end
 * of a sentence region.  Not included in the sentence.
 * </ul>
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Christopher Manning
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <IN> The type of the tokens in the sentences
 */
public class WordToSentenceProcessor<IN> implements ListProcessor<IN, List<IN>> {

  private static final boolean DEBUG = false;

  /**
   * Set of tokens (Strings) that qualify as sentence-final tokens.
   */
  private Set<String> sentenceBoundaryTokens;

  /**
   * Set of tokens (Strings) that qualify as tokens that can follow
   * what normally counts as an end of sentence token, and which are
   * attributed to the preceding sentence.  For example ")" coming after
   * a period.
   */
  private Set<String> sentenceBoundaryFollowers;

  /**
   * Set of tokens (Strings) that are sentence boundaries to be discarded.
   */
  private Set<String> sentenceBoundaryToDiscard;

  private Pattern sentenceRegionBeginPattern;

  private Pattern sentenceRegionEndPattern;


  /**
   * Returns a List of Sentences where each element is built from a run
   * of Words in the input Document. Specifically, reads through each word in
   * the input document and breaks off a sentence after finding a valid
   * sentence boundary token or end of file.
   * Note that for this to work, the words in the
   * input document must have been tokenized with a tokenizer that makes
   * sentence boundary tokens their own tokens (e.g., {@link PTBTokenizer}).
   *
   * @param words A list of already tokenized words (must implement HasWord or be a String)
   * @return A list of Sentence
   * @see #WordToSentenceProcessor(Set, Set, Set)
   * @see edu.stanford.nlp.ling.Sentence
   */
  public List<List<IN>> process(List<? extends IN> words) {
    List<List<IN>> sentences = Generics.newArrayList();
    List<IN> currentSentence = null;
    List<IN> lastSentence = null;
    boolean insideRegion = false;
    for (IN o: words) {
      String word;
      if (o instanceof HasWord) {
        HasWord h = (HasWord) o;
        word = h.word();
      } else if (o instanceof String) {
        word = (String) o;
      } else if (o instanceof CoreMap) {
        word = ((CoreMap)o).get(CoreAnnotations.WordAnnotation.class);
      } else {
        throw new RuntimeException("Expected token to be either Word or String.");
      }
      if (DEBUG) {
        EncodingPrintWriter.err.println("Word is " + word, "UTF-8");
      }
      if (currentSentence == null) {
        currentSentence = new ArrayList<IN>();
      }
      if (sentenceRegionBeginPattern != null && ! insideRegion) {
        if (sentenceRegionBeginPattern.matcher(word).matches()) {
          insideRegion = true;
        }
        if (DEBUG) {
          System.err.println("  outside region");
        }
        continue;
      }
      if (sentenceBoundaryFollowers.contains(word) && lastSentence != null && currentSentence.isEmpty()) {
        lastSentence.add(o);
        if (DEBUG) {
          System.err.println("  added to last");
        }
      } else {
        boolean newSent = false;
        if (sentenceBoundaryToDiscard.contains(word)) {
          newSent = true;
        } else if (sentenceRegionEndPattern != null && sentenceRegionEndPattern.matcher(word).matches()) {
          insideRegion = false;
          newSent = true;
        } else if (sentenceBoundaryTokens.contains(word)) {
          currentSentence.add(o);
          if (DEBUG) {
            System.err.println("  is sentence boundary; added to current");
          }
          newSent = true;
        } else {
          currentSentence.add(o);
          if (DEBUG) {
            System.err.println("  added to current");
          }
        }
        if (newSent && currentSentence.size() > 0) {
          if (DEBUG) {
            System.err.println("  beginning new sentence");
          }
          sentences.add(currentSentence);
          // adds this sentence now that it's complete
          lastSentence = currentSentence;
          currentSentence = null; // clears the current sentence
        }
      }
    }

    // add any words at the end, even if there isn't a sentence
    // terminator at the end of file
    if (currentSentence != null && currentSentence.size() > 0) {
      sentences.add(currentSentence); // adds last sentence
    }
    return sentences;
  }



  public <L, F> Document<L, F, List<IN>> processDocument(Document<L, F, IN> in) {
    Document<L, F, List<IN>> doc = in.blankDocument();
    doc.addAll(process(in));
    return doc;
  }

  /**
   * Create a <code>WordToSentenceProcessor</code> using a sensible default
   * list of tokens to split on.  The default set is: {".","?","!"}.
   */
  public WordToSentenceProcessor() {
    this(new HashSet<String>(Arrays.asList(".", "?", "!")));
  }

  /**
   * Flexibly set the set of acceptable sentence boundary tokens, but with
   * a default set of allowed boundary following tokens (based on English
   * and Penn Treebank encoding).
   * The allowed set of boundary followers is:
   * {")","]","\"","\'", "''", "-RRB-", "-RSB-", "-RCB-"}.
   *
   * @param boundaryTokens The set of boundary tokens
   */
  public WordToSentenceProcessor(Set<String> boundaryTokens) {
    this(boundaryTokens, Generics.newHashSet(Arrays.asList(")", "]", "\"", "\'", "''", "-RRB-", "-RSB-", "-RCB-")));
  }

  /**
   * Flexibly set the set of acceptable sentence boundary tokens and
   * also the set of tokens commonly following sentence boundaries, and
   * the set of discarded separator tokens.
   * The default set of discarded separator tokens is: {"\n"}.
   */
  public WordToSentenceProcessor(Set<String> boundaryTokens, Set<String> boundaryFollowers) {
    this(boundaryTokens, boundaryFollowers, Collections.singleton("\n"));
  }


  /**
   * Flexibly set the set of acceptable sentence boundary tokens,
   * the set of tokens commonly following sentence boundaries, and also
   * the set of tokens that are sentences boundaries that should be
   * discarded.
   */
  public WordToSentenceProcessor(Set<String> boundaryTokens, Set<String> boundaryFollowers,
                                 Set<String> boundaryToDiscard) {
    this(boundaryTokens, boundaryFollowers, boundaryToDiscard, null, null);
  }

  public WordToSentenceProcessor(Pattern regionBeginPattern, Pattern regionEndPattern) {
    this(Collections.<String>emptySet(), Collections.<String>emptySet(),
         Collections.<String>emptySet(), regionBeginPattern, regionEndPattern);
  }

  /**
   * Flexibly set the set of acceptable sentence boundary tokens,
   * the set of tokens commonly following sentence boundaries, and also
   * the set of tokens that are sentences boundaries that should be
   * discarded.
   * This is private because it is a dangerous constructor. It's not clear what the semantics
   * should be if there are both boundary token sets, and patterns to match.
   */
  private WordToSentenceProcessor(Set<String> boundaryTokens, Set<String> boundaryFollowers, Set<String> boundaryToDiscard, Pattern regionBeginPattern, Pattern regionEndPattern) {
    sentenceBoundaryTokens = boundaryTokens;
    sentenceBoundaryFollowers = boundaryFollowers;
    sentenceBoundaryToDiscard = boundaryToDiscard;
    sentenceRegionBeginPattern = regionBeginPattern;
    sentenceRegionEndPattern = regionEndPattern;
    if (DEBUG) {
      EncodingPrintWriter.err.println("WordToSentenceProcessor: boundaryTokens=" + boundaryTokens, "UTF-8");
      EncodingPrintWriter.err.println("  boundaryFollowers=" + boundaryFollowers, "UTF-8");
      EncodingPrintWriter.err.println("  boundaryToDiscard=" + boundaryToDiscard, "UTF-8");
    }
  }


  /* -- for testing only
  private void printSet(Set s) {
    for (Iterator i = s.iterator(); i.hasNext();) {
      System.out.print(i.next() + " ");
    }
    System.out.println();
  }
  -- */


  /**
   * This will print out as sentences some text.  It can be used to
   * test sentence division.  <br>
   * Usage: java edu.stanford.nlp.process.WordToSentenceProcessor fileOrUrl+
   *
   * @param args Command line argument: files or URLs
   */
//   public static void main(String[] args) {
//     if (args.length == 0) {
//       System.out.println("usage: java edu.stanford.nlp.process.WordToSentenceProcessor fileOrUrl");
//       System.exit(0);
//     }
//     try {
//       for (String filename : args) {
//         Document<String, Word, Word> d; // always initialized below
//         if (filename.startsWith("http://")) {
//           Document dpre = new BasicDocument().init(new URL(filename));
//           DocumentProcessor notags = new StripTagsProcessor();
//           d = notags.processDocument(dpre);
//         } else {
//           d = new BasicDocument().init(new File(filename));
//         }
//         WordToSentenceProcessor<Word, String, Word> proc = new WordToSentenceProcessor();
//         Document<?,?,List<Word>> sentd = proc.processDocument(d);
//         for (List<Word> sent : sentd) {
//           System.out.println(sent);
//         }
//       }
//     } catch (Exception e) {
//       e.printStackTrace();
//     }
//   }

}
