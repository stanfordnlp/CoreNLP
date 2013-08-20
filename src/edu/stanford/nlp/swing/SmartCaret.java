package edu.stanford.nlp.swing;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import java.awt.event.MouseEvent;
import java.text.BreakIterator;

/**
 * Caret that implements smart-drag-selection ala MS Word. To use this with
 * a JTextComponent, call <tt>setCaret</tt>.
 * <p/>
 * TODO:
 * <ul>
 * <li>shift-click select shouldn't select whole word, only drag-select
 * <li>retreating selection should revert to char-by-char mode
 * <li>...but after moving past next word, go back to full word mode
 * <li>backwards selection should work like forwards selection
 * <li>starting in the middle of whitespace should select back to prev word
 * </ul>
 */
public class SmartCaret extends DefaultCaret {
  /**
   * 
   */
  private static final long serialVersionUID = -1010823438258225478L;
  private final BreakIterator bi = BreakIterator.getWordInstance();
  private int originalPos; // where selection started
  private int originalWordBoundary; // beginning of originally selected word
  private int nextWordBoundary; // word-boundary after initial selection
  boolean passedNextWord; // whether dragged selection has passed beginning of next word

  /**
   * Moves the caret to an appropriate position based on the drag event.
   * If the selection started in the middle of a word and the caret gets
   * dragged past the beginning of the next word, the selection is extended
   * back to include all of the original word. At this point forward selection
   * happens a word at a time, i.e. once a word is partially selected, it
   * becomes completely selected. This can be undone by moving the caret
   * backwards.
   */
  @Override
  protected void moveCaret(MouseEvent e) {
    int pos = getComponent().viewToModel(e.getPoint());
    //System.err.println("moveCaret: "+pos);
    if (!passedNextWord && nextWordBoundary != BreakIterator.DONE && pos > nextWordBoundary) {
      setDot(originalWordBoundary);
      passedNextWord = true;
    } else if (pos == nextWordBoundary && passedNextWord) {
      // retreat, or secondary assult
      setDot(originalPos);
    }

    // selects either the whole next word or up to the cursor position
    if (passedNextWord && pos > nextWordBoundary + 1 && pos < getText().length() && !bi.isBoundary(pos)) {
      moveDot(bi.following(pos)); // select whole word
    } else {
      moveDot(pos);
    }
  }

  /**
   * Records original caret position and next word boundary.
   */
  @Override
  protected void positionCaret(MouseEvent e) {
    originalPos = getComponent().viewToModel(e.getPoint());
    //System.err.println("positionCaret: "+originalPos);
    bi.setText(getText());
    nextWordBoundary = nextWordStartAfter(originalPos, getText());
    if (originalPos == 0 || originalPos == getText().length() || bi.isBoundary(originalPos)) {
      originalWordBoundary = originalPos; // at beginning of word
    } else {
      originalWordBoundary = bi.preceding(originalPos); // start of word
    }
    //System.err.println("< "+originalWordBoundary);
    passedNextWord = false;
    super.positionCaret(e);
  }

  /**
   * Gets the text of the document.
   */
  private String getText() {
    Document doc = getComponent().getDocument();
    try {
      return (doc.getText(0, doc.getLength()));
    } catch (BadLocationException e) {
      return (null);
    } // shouldn't happen
  }

  // adapted from BreakIterator javadocs
  public int nextWordStartAfter(int pos, String text) {
    if (pos >= text.length()) {
      return text.length();
    }
    int last = bi.following(pos);
    int current = bi.next();
    while (current != BreakIterator.DONE) {
      for (int p = last; p < current; p++) {
        if (!Character.isWhitespace(text.charAt(p))) {
          return last;
        }
      }
      last = current;
      current = bi.next();
    }
    return BreakIterator.DONE;
  }

}
