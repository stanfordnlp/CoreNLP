package edu.stanford.nlp.trees.tregex.gui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.event.MouseEvent;

import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.Highlight;

/**
 * Static utility methods for highlighting a span of text in a JTextField
 * using the positions of mouse events.
 */
public class HighlightUtils {
  //Non-instantiable
  private HighlightUtils() { }


  /**
   * Highlight the given label from the first mouse event to the second
   * Returns true if the highlight was successful, false otherwise.
   * Any existing highlights on the label are removed first.  The events
   * may be in either order; if they are at the same character offset,
   * nothing is highlighted and the existing highlights are left alone.
   *
   * @param label the text field to highlight
   * @param mouseEvent1 the event at one end of the highlight
   * @param mouseEvent2 the event at the other end of the highlight
   * @return true if a highlight was added
   */
  public static boolean addHighlight(JTextField label, MouseEvent mouseEvent1, MouseEvent mouseEvent2) {
    FontMetrics fm = label.getFontMetrics(label.getFont());
    int firstXpos = mouseEvent1.getX();
    int lastXpos = mouseEvent2.getX();
    int firstOffset = getCharOffset(fm, label.getText(), firstXpos);
    int lastOffset = getCharOffset(fm, label.getText(), lastXpos);
    if(lastOffset != firstOffset) {
      if(firstOffset > lastOffset) {
        int tmp = firstOffset;
        firstOffset = lastOffset;
        lastOffset = tmp;
      }
      try {
        label.getHighlighter().removeAllHighlights();
        label.getHighlighter().addHighlight(firstOffset, lastOffset, new DefaultHighlighter.DefaultHighlightPainter(Color.yellow));
        return true;
      } catch (BadLocationException e1) {
        return false;
      }
    } else
      return false;
  }

  /**
   * Returns true if the given mouse event occurred within a highlight h on label.
   * Only the first highlight in h is checked.
   *
   * @param e the mouse event
   * @param label the text field the event occurred in
   * @param h the highlighter to check
   * @return true if the event's position is within the first highlight of h
   */
  public static boolean isInHighlight(MouseEvent e, JTextField label, Highlighter h) {
    Highlight[] hls = h.getHighlights();
    if(hls == null || hls.length == 0)
      return false;
    Highlight hl = hls[0];
    FontMetrics fm = label.getFontMetrics(label.getFont());
    int offset = getCharOffset(fm, label.getText(), e.getX());
    return hl.getStartOffset() <= offset && offset < hl.getEndOffset();
  }

  private static int getCharOffset(FontMetrics fm, String characters, int xPos) {
    StringBuilder s = new StringBuilder();
    char[] sArray = characters.toCharArray();
    int i;
    for(i = 0; i < characters.length() && fm.stringWidth(s.toString()) < xPos; i++) {
      s.append(sArray[i]);
    }
    return i;
  }

}
