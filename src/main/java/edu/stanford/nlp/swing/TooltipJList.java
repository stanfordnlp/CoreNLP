package edu.stanford.nlp.swing;

import java.awt.event.MouseEvent;

import javax.swing.JList;
import javax.swing.ListModel;

/**
 * Simple list class that extends JList and adds tool tip functionality to the list.  Tool tips are automatically
 * wrapped to a specific length (default 80 chars) while preserving word boundaries.
 *
 * @author Anna Rafferty
 *
 */
@SuppressWarnings("serial")
public class TooltipJList extends JList {

  // todo: generify once we move to Java 8, but JList wasn't generic in Java 6 so can't do now.

  private static int PROBLEM_LINE_LENGTH = 80;

  public TooltipJList() {
    super();
  }

  public TooltipJList(ListModel model) {
    this(model, PROBLEM_LINE_LENGTH);
  }

  public TooltipJList(ListModel model, int lineWrapLength) {
    super(model);
    PROBLEM_LINE_LENGTH = lineWrapLength;
  }


  @Override
  public String getToolTipText(MouseEvent evt) {
    int index = locationToIndex(evt.getPoint());
    if (-1 < index) {
      StringBuilder s = new StringBuilder();
      String text = getModel().getElementAt(index).toString();
      s.append("<html>");
      //separate out into lines
      String textLeft = text;
      boolean isFirstLine = true;
      while(textLeft.length() > 0) {
        String curLine = "";
        if(textLeft.length() > PROBLEM_LINE_LENGTH) {
           curLine = textLeft.substring(0, PROBLEM_LINE_LENGTH);
           textLeft = textLeft.substring(PROBLEM_LINE_LENGTH, textLeft.length());
           //check if we're at the end of a word - if not, get us there
           while(curLine.charAt(curLine.length()-1) != ' ' && textLeft.length() >0) {
             curLine = curLine + textLeft.substring(0,1);
             textLeft = textLeft.substring(1,textLeft.length());
           }
        } else {
          curLine = textLeft;
          textLeft = "";
        }
        if(!isFirstLine)
          s.append("<br>");
        s.append(curLine);
        if(!isFirstLine)
          // TODO: maybe just <br> ?
          s.append("</br>");
        else
          isFirstLine = false;
      }
      s.append("</html>");
      return s.toString();
    } else {
      return null;
    }
  }

}
