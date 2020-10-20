package edu.stanford.nlp.trees.tregex.gui;

import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;


/**
 * Class to load OSX specific settings for TregexGUI.  Made by consulting the Apple sample code
 * for a similar (and identically named) class at http://devworld.apple.com/samplecode/OSXAdapter/index.html.
 * @author rafferty
 *
 */
public class OSXAdapter extends ApplicationAdapter {

  private static OSXAdapter adapter;
  private static com.apple.eawt.Application app;

  private TregexGUI mainApp;
  
  private OSXAdapter (TregexGUI inApp) {
    mainApp = inApp;
  }
  
  // implemented handler methods.  These are basically hooks into existing 
  // functionality from the main app, as if it came over from another platform.
  @Override
  public void handleAbout(ApplicationEvent ae) {
    if (mainApp != null) {
      ae.setHandled(true);
      mainApp.about();
    } else {
      throw new IllegalStateException("handleAbout: TregexGUI instance detached from listener");
    }
  }
  
  @Override
  public void handlePreferences(ApplicationEvent ae) {
    if (mainApp != null) {
      mainApp.doPreferences();
      ae.setHandled(true);
    } else {
      throw new IllegalStateException("handlePreferences: TregexGUI instance detached from listener");
    }
  }
  
  @Override
  public void handleQuit(ApplicationEvent ae) {
    if (mainApp != null) {
      /*  
      / You MUST setHandled(false) if you want to delay or cancel the quit.
      / This is important for cross-platform development -- have a universal quit
      / routine that chooses whether or not to quit, so the functionality is identical
      / on all platforms.  This example simply cancels the AppleEvent-based quit and
      / defers to that universal method.
      */
      ae.setHandled(false);
      TregexGUI.doQuit();
    } else {
      throw new IllegalStateException("handleQuit: TregexGUI instance detached from listener");
    }
  }
  
  
  // The main entry-point for this functionality.  This is the only method
  // that needs to be called at runtime, and it can easily be done using
  // reflection (see MyApp.java) 
  public static void registerMacOSXApplication(TregexGUI inApp) {
    if (app == null) {
      app = new com.apple.eawt.Application();
    }     
    
    if (adapter == null) {
      adapter = new OSXAdapter(inApp);
    }
    app.addApplicationListener(adapter);
  }
  
  // Another static entry point for EAWT functionality.  Enables the 
  // "Preferences..." menu item in the application menu. 
  public static void enablePrefs(boolean enabled) {
    if (app == null) {
      app = new com.apple.eawt.Application();
    }
    app.setEnabledPreferencesMenu(enabled);
  }

}
