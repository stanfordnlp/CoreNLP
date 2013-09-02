package edu.stanford.nlp.swing;

import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
 * UndoManager that maintains an undo and redo menu to the current undo state.
 * After an undoable action occurs, or an action is undone/redone, undo/redo
 * menus are updated with current text and set enabled/disabled as needed.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu) [taken from Summer 01 work at UCSD]
 */
public class MenuSavvyUndoManager extends UndoManager {
  /**
   * 
   */
  private static final long serialVersionUID = 1834040831306261115L;
  // undo and redo menu items that get maintained by this class
  private JMenuItem undoMenuItem;
  private JMenuItem redoMenuItem;

  public MenuSavvyUndoManager(JMenuItem undoMenuItem, JMenuItem redoMenuItem) {
    this.undoMenuItem = undoMenuItem;
    this.redoMenuItem = redoMenuItem;
  }

  @Override
  public void undoableEditHappened(UndoableEditEvent e) {
    super.undoableEditHappened(e);
    updateMenuItems();
  }

  @Override
  public void undo() throws CannotUndoException {
    super.undo();
    updateMenuItems();
  }

  @Override
  public void redo() throws CannotUndoException {
    super.redo();
    updateMenuItems();
  }

  @Override
  public void discardAllEdits() {
    super.discardAllEdits();
    updateMenuItems();
  }

  /**
   * Updates the text and enabled state of the undo and redo menu items.
   * Reflects the current state of the UndoManager.
   */
  private void updateMenuItems() {
    undoMenuItem.setText(getUndoPresentationName());
    undoMenuItem.setEnabled(canUndo());

    redoMenuItem.setText(getRedoPresentationName());
    redoMenuItem.setEnabled(canRedo());
  }
}


