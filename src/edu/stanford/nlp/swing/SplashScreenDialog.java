package edu.stanford.nlp.swing;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;

/** SplashScreenDialog is a simple general class for putting up a splash screen
 *  picture, loading progress messages, and a JProgressBar.
 *
 *  @author Christopher Manning
 */
public class SplashScreenDialog extends JFrame {

  /**
   *
   */
  private static final long serialVersionUID = 2199391519922758853L;
  private static final String SC_CONFIRM_EXIT = "Confirm exit";
  private static final String SC_CONFIRM_EXIT_TEXT =
    "Do you want to exit the application?\n(Cancel will only close the progress bar.)";

  private JLabel progressLabel;
  private JProgressBar progressBar;
  private final int max;
  private boolean disposed; // = false;

  /** Create a SplashScreen
   *
   *  @param title The Dialog Window title
   *  @param filename The splash screen picture that is loaded (as ImageIcon)
   *  @param status Initial message to display about loading
   *  @param max The range of values of the progress meter.  If this is
   *     positive, the creator will manually advance the progress meter.
   *     If it is negative, the progress meter will be on auto pilot to 90%
   *     over -max seconds.
   */
  public SplashScreenDialog(String title, String filename, String status, int max) {
    super(title);
    if (max > 0) {
      this.max = max;
    } else {
      this.max = 100;
    }

    JPanel progressPanel = new JPanel();
    progressPanel.setLayout(new BoxLayout(progressPanel,
                                          BoxLayout.Y_AXIS));
    progressPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JLabel splashLabel = new JLabel(new ImageIcon(filename));
    splashLabel.setAlignmentX(CENTER_ALIGNMENT);

    progressLabel = new JLabel(status + " ...");
    progressLabel.setAlignmentX(CENTER_ALIGNMENT);

    progressBar = new JProgressBar(0, this.max);  // initialized to min value
    progressLabel.setLabelFor(progressBar);
    progressBar.setAlignmentX(CENTER_ALIGNMENT);

    progressPanel.add(splashLabel);
    progressPanel.add(Box.createVerticalStrut(12));
    progressPanel.add(progressLabel);
    progressPanel.add(Box.createVerticalStrut(12));
    progressPanel.add(progressBar);
    progressPanel.add(Box.createVerticalStrut(8));
    getContentPane().add(progressPanel);

    addWindowListener(new MyWindowListener(this));
    // Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    pack();

    // show the frame
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension frameSize = getPreferredSize();
    setBounds((screenSize.width - frameSize.width)/2,
              (screenSize.height - frameSize.height)/2,
              frameSize.width, frameSize.height);
    // Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    setVisible(true);
    /*
      final SplashScreenDialog copy = this;
      EventQueue.invokeLater(new Runnable() {
      public void run() {
      copy.pack();
      copy.setVisible(true);
      // copy.show();
      }
      });
    */
    if (max < 0) {
      setAutoPilot(-max);
    }
  }


  public void setMessage(final String message) {
    final String msg = message + " ...";
    try {
      EventQueue.invokeLater(new Runnable() {
          public void run() {
            progressLabel.setText(msg);
          }
        });
    } catch (Exception ie) {
      ie.printStackTrace();
    }
  }


  /** Set the progress bar to advance itself on autopilot until it
   *  is up to 90% complete.
   *
   *  @param seconds The number of seconds to take to get to 90%.
   */
  private void setAutoPilot(final int seconds) {
    AutoPilot ap = new AutoPilot(seconds);
    Thread progress = new Thread(ap);
    progress.start();
  }

  private class AutoPilot implements Runnable {

    private int secs;
    AutoPilot(int seconds) {
      secs = seconds;
    }

    public void run() {
      int wait = secs * 1000 / 18;
      try {
        while (getValue() < (max - 10)) {
          Thread.sleep(wait);
          incrementValue(5);
        }
      } catch (InterruptedException tie) {
        // just exit
      }
      // end thread
    }

  }


  public int getValue() {
    return progressBar.getValue();
  }

  public void incrementValue(int n) {
    setValue(progressBar.getValue() + n);
  }

  public void incrementValue() {
    incrementValue(1);
  }

  public void incrementValue(String s) {
    setMessage(s);
    incrementValue(1);
  }

  /** Get the splash screen to disappear.  (You should either call this
   *  or call incrementValue() until the value of the splash screen reaches
   *  the maximum value.  If the splash screen is on auto pilot, then you
   *  must call this method (or switch to manually advancing the counter).
   */
  public void finished() {
    setValue(max);
  }

  public void setValue(final int v) {
    if ( ! disposed) {
      try {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
              if ( ! disposed) {
                progressBar.setValue(v);
                if (v >= max) {
                  dispose();
                  disposed = true;
                }
              }
            }
          });
      } catch (Exception ie) {
        ie.printStackTrace();
      }
    }
  }


  private static class MyWindowListener extends WindowAdapter {

    private Container frame;

    MyWindowListener(final Container c) {
      frame = c;
    }

    @Override
    public void windowClosing(WindowEvent we) {
      int option = JOptionPane.showConfirmDialog(frame,
                                                 SC_CONFIRM_EXIT_TEXT,
                                                 SC_CONFIRM_EXIT,
                                                 JOptionPane.YES_NO_CANCEL_OPTION,
                                                 JOptionPane.QUESTION_MESSAGE);
      if (option == JOptionPane.YES_OPTION) {
        System.exit(0);
      } else if (option == JOptionPane.CANCEL_OPTION) {
        we.getWindow().setVisible(false);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    SplashScreenDialog ssd = new SplashScreenDialog("Test example", "/u/apache/htdocs/img/nlp-logo-small.gif", "Pretending to load", 3);
    Thread.sleep(2000);
    ssd.incrementValue("Working");
    Thread.sleep(2000);
    ssd.incrementValue("Still working");
    Thread.sleep(2000);
    ssd.finished();
  }

}
