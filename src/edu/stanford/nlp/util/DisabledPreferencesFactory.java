package edu.stanford.nlp.util;

import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Returns do-nothing Preferences implementation.  We don't use this
 * facility, so we want to avoid the hassles that come with the JVM's
 * implementation.
 * Taken from: http://www.allaboutbalance.com/disableprefs/index.html
 *
 * @author Robert Slifka
 * @author Christopher Manning
 * @version 2003/03/24
 */
public class DisabledPreferencesFactory implements PreferencesFactory {

  /** Creates a factory which returns do-nothing Preferences. */
  public DisabledPreferencesFactory() { }

  public Preferences systemRoot() {
    return new DisabledPreferences();
  }

  public Preferences userRoot() {
    return new DisabledPreferences();
  }

  /**
   * Sets the {@code java.util.prefs.PreferencesFactory} system property to this class,
   * so that the JVM uses do-nothing Preferences.  Silently does nothing if a
   * SecurityManager forbids setting the property.
   */
  public static void install() {
    try {
      System.setProperty("java.util.prefs.PreferencesFactory", "edu.stanford.nlp.util.DisabledPreferencesFactory");
    } catch (SecurityException e) {
      // oh well we couldn't do it...
    }
  }

}
