package edu.stanford.nlp.process;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.BasicDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.Generics;

/**
 * A <code>Processor</code> whose <code>process</code> method deletes all
 * SGML/XML/HTML tags (tokens starting with <code>&lt;</code> and ending
 * with <code>&gt;</code>. Optionally, newlines can be inserted after the
 * end of block-level tags to roughly simulate where continuous text was
 * broken up (this helps finding sentence boundaries for example).
 *
 * @author Christopher Manning
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */
public class StripTagsProcessor<L, F> extends AbstractListProcessor<Word, Word, L, F> {

  private static final Set<String> BLOCKTAGS = Generics.newHashSet(Arrays.asList(
          "blockquote", "br", "div", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "li", "ol", "p", "pre", "table", "tr", "ul"));

  /**
   * Block-level HTML tags that are rendered with surrounding line breaks.
   */
  public static final Set<String> blockTags = BLOCKTAGS;

  /**
   * Whether to insert "\n" words after ending block tags.
   */
  private boolean markLineBreaks;

  /**
   * Constructs a new StripTagsProcessor that doesn't mark line breaks.
   */
  public StripTagsProcessor() {
    this(false);
  }

  /**
   * Constructs a new StripTagProcessor that marks line breaks as specified.
   */
  public StripTagsProcessor(boolean markLineBreaks) {
    setMarkLineBreaks(markLineBreaks);
  }

  /**
   * Returns whether the output of the processor will contain newline words
   * ("\n") at the end of block-level tags.
   *
   * @return Whether the output of the processor will contain newline words
   * ("\n") at the end of block-level tags.
   */
  public boolean getMarkLineBreaks() {
    return (markLineBreaks);
  }

  /**
   * Sets whether the output of the processor will contain newline words
   * ("\n") at the end of block-level tags.
   */
  public void setMarkLineBreaks(boolean markLineBreaks) {
    this.markLineBreaks = markLineBreaks;
  }

  /**
   * Returns a new Document with the same meta-data as <tt>in</tt>,
   * and the same words except tags are stripped.
   */
  public List<Word> process(List<? extends Word> in) {
    List<Word> out = new ArrayList<>();
    boolean justInsertedNewline = false; // to prevent contiguous newlines
    for (Word w : in) {
      String ws = w.word();
      if (ws.startsWith("<") && ws.endsWith(">")) {
        if (markLineBreaks && !justInsertedNewline) {
          // finds start and end of tag name (ignores brackets and /)
          // e.g. <p>, <br/>, or </table>
          //       se   s e        s    e

          int tagStartIndex = 1;
          while (tagStartIndex < ws.length() && !Character.isLetter(ws.charAt(tagStartIndex))) {
            tagStartIndex++;
          }
          if (tagStartIndex == ws.length()) {
            continue; // no tag text
          }

          int tagEndIndex = ws.length() - 1;
          while (tagEndIndex > tagStartIndex && !Character.isLetterOrDigit(ws.charAt(tagEndIndex))) {
            tagEndIndex--;
          }

          // looks up tag name in list of known block-level tags
          String tagName = ws.substring(tagStartIndex, tagEndIndex + 1).toLowerCase();
          if (blockTags.contains(tagName)) {
            out.add(new Word("\n")); // mark newline for block-level tags
            justInsertedNewline = true;
          }
        }
      } else {
        out.add(w); // normal word
        justInsertedNewline = false;
      }
    }
    return out;
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    new BasicDocument<String>();
    Document<String, Word, Word> htmlDoc = BasicDocument.init("top text <h1>HEADING text</h1> this is <p>new paragraph<br>next line<br/>xhtml break etc.");
    System.out.println("Before:");
    System.out.println(htmlDoc);
    Document<String, Word, Word> txtDoc = new StripTagsProcessor<String, Word>(true).processDocument(htmlDoc);
    System.out.println("After:");
    System.out.println(txtDoc);
    Document<String, Word, List<Word>> sentences = new WordToSentenceProcessor<Word>().processDocument(txtDoc);
    System.out.println("Sentences:");
    System.out.println(sentences);
  }
}
