
package edu.stanford.nlp.ie.machinereading.domains.ace.reader; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Vector;

import edu.stanford.nlp.trees.Span;

/**
 * Implements the ACE {@literal <charseq>} construct.
 *
 * @author David McClosky
 * @author Andrey Gusev
 */
public class AceCharSeq  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AceCharSeq.class);

  /** The exact text matched by this sequence */
  private String mText;

  /** Offset in the document stream */
  private Span mByteOffset;

  /** Span of tokens that match this char sequence */
  private Span mTokenOffset;

  /**
   * Token that incorporates this whole char sequence, e.g.
   * "George_Bush/NNP_NNP" for the text "George Bush" XXX: not used anymore
   */
  // private AceToken mPhrase;

  public AceCharSeq(String text, int start, int end) {
    mText = text;
    mByteOffset = new Span(start, end);
    mTokenOffset = null;
    // mPhrase = null;
  }

  public String toXml(String label, int offset) {
    StringBuilder builder = new StringBuilder();
    AceElement.appendOffset(builder, offset);
    builder.append('<').append(label).append(">\n");

    AceElement.appendOffset(builder, offset + 2);
    builder.append("<charseq START=\"").append(mByteOffset.start()).append("\" END=\"").append(mByteOffset.end()).append("\">");
    builder.append(mText).append("</charseq>");
    builder.append('\n');

    AceElement.appendOffset(builder, offset);
    builder.append("</").append(label).append('>');
    return builder.toString();
  }

  public String toXml(int offset) {
    StringBuilder builder = new StringBuilder();

    AceElement.appendOffset(builder, offset + 2);
    builder.append("<charseq START=\"").append(mByteOffset.start()).append("\" END=\"").append(mByteOffset.end()).append("\">");
    builder.append(mText).append("</charseq>");
    return builder.toString();
  }

  public String getText() {
    return mText;
  }

  public int getByteStart() {
    return mByteOffset.start();
  }

  public int getByteEnd() {
    return mByteOffset.end();
  }

  public Span getByteOffset() {
    return mByteOffset;
  }

  public int getTokenStart() {
    if (mTokenOffset == null)
      return -1;
    return mTokenOffset.start();
  }

  public int getTokenEnd() {
    if (mTokenOffset == null)
      return -1;
    return mTokenOffset.end();
  }

  public Span getTokenOffset() {
    return mTokenOffset;
  }

  // public AceToken getPhrase() { return mPhrase; }

  /**
   * Matches this char seq against the full token stream As a result of this
   * method mTokenOffset is initialized
   */
  public void match(Vector<AceToken> tokens) throws MatchException {
    int start = -1;
    int end = -1;

    for (int i = 0; i < tokens.size(); i++) {
      //
      // we found the starting token
      //
      if (tokens.get(i).getByteOffset().start() == mByteOffset.start()) {
        start = i;
      }

      //
      // we do not tokenize dashed-words, hence the start may be inside a token
      // e.g. Saddam => pro-Saddam
      // the same situation will happen due to (uncommon) annotation errors
      //
      else if (mByteOffset.start() > tokens.get(i).getByteOffset().start()
          && mByteOffset.start() < tokens.get(i).getByteOffset().end()) {
        start = i;
      }

      //
      // we found the ending token
      // Note: ACE is inclusive for the end position, my tokenization is not
      // in ACE: end position == position of last byte in token
      // in .sgm.pre: end position == position of last byte + 1
      //
      if (tokens.get(i).getByteOffset().end() == mByteOffset.end() + 1) {
        end = i;
        break;
      }

      //
      // we do not tokenize dashed-words, hence the end may be inside a token
      // e.g. Conference => Conference-leading
      // the same situation will happen due to (uncommon) annotation errors
      //
      else if (mByteOffset.end() >= tokens.get(i).getByteOffset().start()
          && mByteOffset.end() < tokens.get(i).getByteOffset().end() - 1) {
        end = i;
        break;
      }
    }

    if (start >= 0 && end >= 0) {
      mTokenOffset = new Span(start, end);
      // mPhrase = makePhrase(tokens, mTokenOffset);
    } else {
      throw new MatchException("Match failed!");
    }
  }

  @Override
  public String toString() {
    return "AceCharSeq [mByteOffset=" + mByteOffset + ", mText=" + mText
        + ", mTokenOffset=" + mTokenOffset + ']';
  }

  /*
   * private AceToken makePhrase(Vector<AceToken> tokens, Span span) {
   * StringBuilder word = new StringBuilder(); StringBuilder lemma = new
   * StringBuilder(); StringBuilder pos = new StringBuilder(); StringBuilder chunk =
   * new StringBuilder(); StringBuilder nerc = new StringBuilder();
   *
   * for(int i = span.mStart; i <= span.mEnd; i ++){ if(i > span.mStart){
   * word.append("_"); lemma.append("_"); pos.append("_"); chunk.append("_");
   * nerc.append("_"); }
   *
   * AceToken tok = tokens.get(i);
   * word.append(AceToken.WORDS.get(tok.getWord()));
   * lemma.append(AceToken.LEMMAS.get(tok.getLemma()));
   * pos.append(AceToken.OTHERS.get(tok.getPos()));
   * chunk.append(AceToken.OTHERS.get(tok.getChunk()));
   * nerc.append(AceToken.OTHERS.get(tok.getNerc())); }
   *
   * AceToken phrase = new AceToken(word.toString(), lemma.toString(),
   * pos.toString(), chunk.toString(), nerc.toString(), null, null, -1);
   *
   * //log.info("Constructed phrase: " + phrase.display()); return
   * phrase; }
   */

}
