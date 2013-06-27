package edu.stanford.nlp.util;

import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * A do-nothing Preferences implementation so that we can avoid the hassles
 * of the JVM Preference implementations.
 * Taken from: http://www.allaboutbalance.com/disableprefs/index.html
 *
 * @author Robert Slifka
 * @version 2003/03/24
 */
public class DisabledPreferences extends AbstractPreferences {

  public DisabledPreferences() {
    super(null, "");
  }

  @Override
  protected void putSpi(String key, String value) {

  }

  @Override
  protected String getSpi(String key) {
    return null;
  }

  @Override
  protected void removeSpi(String key) {

  }

  @Override
  protected void removeNodeSpi() throws BackingStoreException {

  }

  @Override
  protected String[] keysSpi() throws BackingStoreException {
    return new String[0];
  }

  @Override
  protected String[] childrenNamesSpi() throws BackingStoreException {
    return new String[0];
  }

  @Override
  protected AbstractPreferences childSpi(String name) {
    return null;
  }

  @Override
  protected void syncSpi() throws BackingStoreException {

  }

  @Override
  protected void flushSpi() throws BackingStoreException {

  }
}
