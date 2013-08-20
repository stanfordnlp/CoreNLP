/*
 * SlotEventListener.java
 *
 * Created on March 6, 2003, 10:42 AM
 */

package edu.stanford.nlp.ie;

import java.util.Map;

/**
 * @author Huy Nguyen
 */
public interface SlotEventListener {
  public void slotFilled(String slot, String value);

  public void allSlotsFilled(Map valuesBySlot);
}

