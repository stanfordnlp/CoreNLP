package edu.stanford.nlp.swing;

import javax.swing.*;
import java.net.URL;

/**
 * Collection of static utility methods for working with swing guis.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class SwingUtils {
  /**
   * Private constructor to prevent direct instantiation.
   */
  private SwingUtils() {
  }

  /**
   * Attempts to load an image icon from the given file and stick it on the
   * given button. If successful, just uses the icon, otherwise uses the text
   * as a standin (unless the text is null, in which case it does nothing).
   * The iconFilename path is for a system resource (i.e., a file inside a
   * jar or otherwise in your classpath). The idea is that specifying
   * absolute path names kills portability, so you stick your images in
   * a jar or with your classes (or use existing icons in companion
   * jar files like the Java L&F Graphics Repository)
   * and refer to them via system resource paths (relative to the root of the
   * jar file or classes dir). The problem is sometimes these resources can't
   * be found (e.g. if the jar is missing) so this method fails gracefully so
   * you can still have a text label on your button if not an icon. For
   * a more
   * general way to gracefully get an icon from a system resource (that this
   * method utilizes), see {@link #loadImageIcon}).
   * <p/>
   * Standard icons from Sun are included in the JavaNLP libraries as
   * <tt>jlfgr-1_0.jar</tt>.
   *
   * @see <a href="http://developer.java.sun.com/developer/techDocs/hi/repository/">Java Look and Feel Graphics Repository</a>
   */
  public static void loadButtonIcon(JButton button, String iconFilename, String defaultText) {
    ImageIcon icon = loadImageIcon(iconFilename);
    if (icon != null) {
      button.setIcon(icon);
      button.setText(null);
    } else if (defaultText != null) {
      button.setText(defaultText);
    }
  }

  /**
   * Loads the given icon image as a system resource and returns a new ImageIcon.
   * Returns null if the image can't be found. This is a convinient way of
   * trying to add icons to components but failing gracefully. For example,
   * to try and add an "Open" icon using the standard Java Look and Feel
   * Graphics repository:
   * <pre>
   * JButton openButton = new JButton();
   * openButton.setIcon(SwingUtils.loadImageIcon("toolbarButtonGraphics/general/Open16.gif"));
   * </pre>
   * For the special case of trying to load an icon for a button and defaulting
   * a text label instead, consider using {@link #loadButtonIcon} (also contains
   * a more detailed explanation of why you should use system resources).
   * <p/>
   * Standard icons from Sun are included in the JavaNLP libraries as
   * <tt>jlfgr-1_0.jar</tt>.
   *
   * @see <a href="http://developer.java.sun.com/developer/techDocs/hi/repository/">Java Look and Feel Graphics Repository</a>
   */
  public static ImageIcon loadImageIcon(String iconFilename) {
    ClassLoader.getSystemClassLoader();
    URL imageURL = ClassLoader.getSystemResource(iconFilename);
    if (imageURL == null) {
      return (null);
    }
    return (new ImageIcon(imageURL));
  }


}
