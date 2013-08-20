package edu.stanford.nlp.swing;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MouseListener to only show the border of a button on rollover.
 * A common UI practice, especially in toolbars, is to normally render buttons
 * with their icon/text but no surrounding border, and only to show the border
 * when the mouse is over the button. Java buttons don't normally do this. To
 * enable that behavior, call {@link #manageButton ButtonRolloverBorderAdapter.manageButton}
 * on your button (this only works for subclasses of <tt>AbstractButton</tt>, i.e.
 * most swing buttons but not AWT). As a convinience, you can also call
 * {@link #manageToolBar ButtonRolloverBorderAdapter.manageToolBar} to enable
 * this behavior for all buttons on the given toolbar.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class ButtonRolloverBorderAdapter extends MouseAdapter {
  /**
   * Shared instance to assign to buttons.
   */
  private static final ButtonRolloverBorderAdapter sharedInstance = new ButtonRolloverBorderAdapter();

  /**
   * Private constructor to prevent direct instantiation.
   */
  private ButtonRolloverBorderAdapter() {
  }

  /**
   * Adds rollover-border behavior to the given button. Specifically, adds a
   * shared instance of <tt>ButtonRolloverBorderAdapter</tt> as a MouseListener
   * to the given button and turns its border off (otherwise it wouldn't get
   * turned off until the first mouse exit). Also does some other minor
   * UI tweaks like not painting focus and narrowing the margin around the
   * icon (makes it look like toolbar icons in Forte).
   */
  public static void manageButton(AbstractButton button) {
    button.addMouseListener(sharedInstance);
    button.setBorderPainted(false); // turns border initially off
    button.setFocusPainted(false);
    button.setMargin(new java.awt.Insets(1, 1, 1, 1));
  }

  /**
   * Adds rollover-border behavior to all the buttons in the given toolbar.
   * Convinience method since normally one does this for a set of buttons
   * in a toolbar.
   *
   * @see #manageButton
   */
  public static void manageToolBar(JToolBar toolBar) {
    for (int i = 0; i < toolBar.getComponentCount(); i++) {
      if (toolBar.getComponent(i) instanceof AbstractButton) {
        manageButton((AbstractButton) toolBar.getComponent(i));
      }
    }
  }

  /**
   * If <tt>e.getSource</tt> is an <tt>AbstractButton</tt>, hides its border.
   */
  @Override
  public void mouseExited(MouseEvent e) {
    if (e.getSource() instanceof AbstractButton) {
      ((AbstractButton) e.getSource()).setBorderPainted(false);
    }
  }

  /**
   * If <tt>e.getSource</tt> is an <tt>AbstractButton</tt>, shows its border.
   * Don't show the border if the button is disabled, as this looks bad and is
   * generally not done.
   */
  @Override
  public void mouseEntered(MouseEvent e) {
    if (e.getSource() instanceof AbstractButton && ((AbstractButton) e.getSource()).isEnabled()) {
      ((AbstractButton) e.getSource()).setBorderPainted(true);
    }
  }
}
