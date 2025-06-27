package edu.stanford.nlp.trees.tregex.gui;


import java.awt.*;

/**
 * Class to load macOS-specific settings for TregexGUI.  This was initially written to use the (pre-2014, OS X)
 * {@code com.apple.eawt.*} library (AppleJavaExtensions.jar), but now uses the Java 9+ methods in
 * the {@code java.awt.Desktop} class.
 *
 * @author rafferty
 * @author Christopher Manning
 *
 */
public class OSXAdapter {

  private static OSXAdapter adapter;
  private static Desktop desktop;
  private static TregexGUI mainApp;


  private OSXAdapter (TregexGUI inApp) {
    mainApp = inApp;
  }
  
  // implemented handler methods.  These are basically hooks into existing 
  // functionality from the main app, as if it came over from another platform.

  public void handleAbout() {
    if (mainApp != null) {
      // ae.setHandled(true);
      mainApp.about();
    } else {
      throw new IllegalStateException("handleAbout: TregexGUI instance detached from listener");
    }
  }
  
  public void handlePreferences() {
    if (mainApp != null) {
      mainApp.doPreferences();
      // ae.setHandled(true);
    } else {
      throw new IllegalStateException("handlePreferences: TregexGUI instance detached from listener");
    }
  }
  
  public void handleQuit() {
    if (mainApp != null) {
      /*  
      / You MUST setHandled(false) if you want to delay or cancel the quit.
      / This is important for cross-platform development -- have a universal quit
      / routine that chooses whether or not to quit, so the functionality is identical
      / on all platforms.  This example simply cancels the AppleEvent-based quit and
      / defers to that universal method.
      */
      // ae.setHandled(false);
      TregexGUI.doQuit();
    } else {
      throw new IllegalStateException("handleQuit: TregexGUI instance detached from listener");
    }
  }
  

  /** The main method for this macOS-specific functionality. */
  public static void registerMacOSApplication(TregexGUI inApp) {
    System.setProperty( "apple.laf.useScreenMenuBar", "true" );
    System.setProperty( "apple.awt.application.name", "TregexGUI" );
    System.setProperty( "apple.awt.application.appearance", "system" );
    // if (app == null) {
    //   app = new com.apple.eawt.Application();
    // }
    
    if (adapter == null) {
      adapter = new OSXAdapter(inApp);
    }
    // app.addApplicationListener(adapter);
  }
  
  // Another static entry point for EAWT functionality.  Enables the 
  // "Preferences..." menu item in the application menu. 
  public static void enablePrefs(boolean enabled) {
    System.setProperty( "apple.laf.useScreenMenuBar", "true" );
    System.setProperty( "apple.awt.application.name", "TregexGUI" );
    // if (app == null) {
    //   app = new com.apple.eawt.Application();
    // }
    // app.setEnabledPreferencesMenu(enabled);
  }

}
