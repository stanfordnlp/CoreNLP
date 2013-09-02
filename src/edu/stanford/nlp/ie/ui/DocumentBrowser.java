package edu.stanford.nlp.ie.ui;

import edu.stanford.nlp.annotation.HtmlCleaner;
import edu.stanford.nlp.ie.ExtractorMediator;
import edu.stanford.nlp.ie.OntologyMediator;
import edu.stanford.nlp.ie.SlotEventListener;
import edu.stanford.nlp.swing.ButtonRolloverBorderAdapter;
import edu.stanford.nlp.swing.SwingUtils;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

/**
 * Basic web browser component for loading html pages, and selecting content
 * to fill slots.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class DocumentBrowser extends JComponent {
  /**
   * 
   */
  private static final long serialVersionUID = 1488514043812542802L;
  private final JTextField urlField;
  private final JTextPane editorPane;
  private final JButton extractButton, emButton, clearButton;
  private final JButton backButton, forwardButton, reloadButton;
  private final JPopupMenu popup;
  private final JMenuItem viewSourceItem;
  private final JLabel statusLabel = new JLabel();
  private final JFrame emFrame;
  private final EMPanel emPanel;

  private LinkedList<String> history;
  private int historyIndex; // the current index within the history
  String current = ""; // the current url or file

  private String cls;
  private Set<String> slotNames;

  private SimpleAttributeSet normalStyle;
  private SimpleAttributeSet[] highlightStyles;
  private static final int NUM_HIGHLIGHTERS = 6;

  // HTTP constants
  private static final int HTTP_REDIRECT_MIN = 300;
  private static final int HTTP_REDIRECT_MAX = 307;
  private static final int HTTP_ERROR_MIN = 400;

  private ArrayList<SlotEventListener> listeners;

  public DocumentBrowser() {
    history = new LinkedList<String>();
    historyIndex = -1;
    listeners = new ArrayList<SlotEventListener>();
    // don't follow redirects in order to resolve actual address
    HttpURLConnection.setFollowRedirects(false);

    setLayout(new BorderLayout());

    Container addressPanel = Box.createVerticalBox();
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JToolBar navBar = new JToolBar();
    navBar.setFloatable(false);
    backButton = new JButton("Back");
    backButton.setEnabled(false);
    backButton.setToolTipText("Back to previous page");
    backButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setPage(historyIndex - 1);
      }
    });
    navBar.add(backButton);

    forwardButton = new JButton("Forward");
    forwardButton.setEnabled(false);
    forwardButton.setToolTipText("Forward to next page");
    forwardButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setPage(historyIndex + 1);
      }
    });
    navBar.add(forwardButton);
    buttonPanel.add(navBar);

    reloadButton = new JButton("Reload");
    reloadButton.setToolTipText("Reload current page");
    reloadButton.setEnabled(false);
    reloadButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setPage(historyIndex);
      }
    });
    navBar.add(reloadButton);
    ButtonRolloverBorderAdapter.manageToolBar(navBar);
    buttonPanel.add(navBar);

    // tries to use standard java icons for toolbar buttons
    SwingUtils.loadButtonIcon(backButton, "toolbarButtonGraphics/navigation/Back24.gif", null);
    SwingUtils.loadButtonIcon(forwardButton, "toolbarButtonGraphics/navigation/Forward24.gif", null);
    SwingUtils.loadButtonIcon(reloadButton, "toolbarButtonGraphics/general/Refresh24.gif", null);

    JToolBar extractorBar = new JToolBar();
    extractorBar.setFloatable(false);
    extractButton = new JButton("Extract");
    extractButton.setToolTipText("Extract slots for selected instance or class from current page");
    extractButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doExtract();
      }
    });
    extractorBar.add(extractButton);

    clearButton = new JButton("Clear Highlight");
    clearButton.setToolTipText("Clears highlighting");
    clearButton.setEnabled(false);
    clearButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        clearHighlight();
      }
    });
    extractorBar.add(clearButton);

    // pop-up extractor mediator
    emPanel = new EMPanel();
    emFrame = new JFrame("Extractor Mediator");
    emFrame.getContentPane().add("Center", emPanel);
    emFrame.pack();

    emButton = new JButton("Extractor Mediator...");
    emButton.setToolTipText("Shows UI for creating IE components and mapping them into the ontology");
    emButton.addActionListener(new ActionListener() {
      private boolean neverShownBefore = true;

      public void actionPerformed(ActionEvent e) {
        if (neverShownBefore) {
          // centers em panel first time its shown
          emFrame.setLocation(getLocationOnScreen().x + (getWidth() - emFrame.getWidth()) / 2, getLocationOnScreen().y + (getHeight() - emFrame.getHeight()) / 2);
          neverShownBefore = false;
        }
        emFrame.setVisible(true);
      }
    });
    extractorBar.add(emButton);
    ButtonRolloverBorderAdapter.manageToolBar(extractorBar);

    buttonPanel.add(extractorBar);
    addressPanel.add(buttonPanel);

    JPanel addressBarPanel = new JPanel(new BorderLayout());
    addressBarPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    addressBarPanel.add("West", new JLabel("Address: "));
    urlField = new JTextField();
    urlField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setNewPage(urlField.getText());
      }
    });
    addressBarPanel.add("Center", urlField);
    addressPanel.add(addressBarPanel);
    add("North", addressPanel);

    viewSourceItem = new JMenuItem("View extraction text");
    viewSourceItem.setToolTipText("Shows the text actually seen by the information extraction system");
    viewSourceItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showSource();
      }
    });

    popup = new JPopupMenu();
    editorPane = new JTextPane();
    // not editable so we can catch hyperlink events
    editorPane.setEditable(false);
    editorPane.setDragEnabled(true);
    editorPane.setContentType("text/html");
    editorPane.setPreferredSize(new Dimension(400, 400));
    editorPane.setText("Type in a URL or open a file and the contents will be displayed here.");
    editorPane.addHyperlinkListener(new BrowserHyperlinkListener());
    editorPane.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        // button 3 is meant to be the right button
        if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
          popup.removeAll();
          // right now, load all possible slots.
          // In the future, maybe try to narrow down the list based on highlighted text
          if (editorPane.getSelectedText() != null && slotNames != null) {
            Iterator<String> iter = slotNames.iterator();
            while (iter.hasNext()) {
              popup.add(new PopupMenuItem(iter.next()));
            }
          }
          if (historyIndex >= 0) {
            if (popup.getComponentCount() > 0) {
              popup.addSeparator();
            }
            popup.add(viewSourceItem);
          }
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    });
    add("Center", new JScrollPane(editorPane));

    // status bar
    JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
    statusPanel.add(statusLabel);
    setStatus("");
    add("South", statusPanel);

    createHighlighters();
  }

  /**
   * Shows the HTML source of the currently loaded page.
   */
  private void showSource() {
    JFrame sourceFrame = new JFrame(current);
    JEditorPane sourcePane = new JEditorPane();
    sourcePane.setPreferredSize(new Dimension(400, 400));
    sourcePane.setContentType("text");
    try {
      sourcePane.setText(editorPane.getDocument().getText(0, editorPane.getDocument().getLength()));
    } catch (BadLocationException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    }
    sourceFrame.getContentPane().add(sourcePane, BorderLayout.CENTER);
    sourceFrame.pack();
    // centers the frame
    sourceFrame.setLocation(getLocationOnScreen().x + (getWidth() - sourceFrame.getWidth()) / 2, getLocationOnScreen().y + (getHeight() - sourceFrame.getHeight()) / 2);
    sourceFrame.setVisible(true);
  }

  /**
   * Performs the actual extraction.
   */
  private void doExtract() {
    ExtractorMediator mediator = getExtractorMediator();
    if (mediator != null) {
      if (cls != null) {
        String text = editorPane.getSelectedText();
        if (text == null) {
          try {
            text = editorPane.getDocument().getText(0, editorPane.getDocument().getLength());
          } catch (BadLocationException e) {
            e.printStackTrace();
          } // shouldn't happen
        }
        mediator.setOntologyMediator(getEMPanel().getOntologyMediator()); // make sure it's using the latest ontology
        Map<String,String> valuesBySlot = mediator.extractSlotFillers(cls, text);
        int highlightColor = 0;
        clearHighlight();
        for (Iterator<String> iter = valuesBySlot.keySet().iterator(); iter.hasNext();) {
          String slot = iter.next();
          String value = valuesBySlot.get(slot);
          if (value != null) {
            highlightText(value, highlightColor);
            highlightColor = (highlightColor + 1) % numHighlighters();
          }
        }
        fireAllSlotsFilled(valuesBySlot);
      } else {
        JOptionPane.showMessageDialog(this, "You must select a class to extract.", "No class selected", JOptionPane.ERROR_MESSAGE);
      }
    } else {
      JOptionPane.showMessageDialog(this, "You must load an extractor mediator before you can extract from text.", "No extractor mediator loaded", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Returns the extractor mediator being managed by the EMPanel.
   */
  public ExtractorMediator getExtractorMediator() {
    return (emPanel.getExtractorMediator());
  }

  /**
   * Loads the given extractor mediator in the EMPanel (clears it if null).
   */
  public void setExtractorMediator(ExtractorMediator mediator) {
    emPanel.setExtractorMediator(mediator);
  }

  /**
   * Returns the gui used by the browser to manage the extractor mediator.
   */
  public EMPanel getEMPanel() {
    return (emPanel);
  }

  /**
   * Sets the ontology mediator for the EMPanel to use for extractor assignments.
   */
  public void setOntologyMediator(OntologyMediator ontology) {
    emPanel.setOntologyMediator(ontology);
  }

  /* creates the SimpleAttributeSets for highlighting text */
  private void createHighlighters() {
    // for (un)highlighting text
    highlightStyles = new SimpleAttributeSet[NUM_HIGHLIGHTERS];
    for (int i = 0; i < NUM_HIGHLIGHTERS; i++) {
      highlightStyles[i] = new SimpleAttributeSet();
    }
    normalStyle = new SimpleAttributeSet();

    StyleConstants.setBackground(highlightStyles[0], Color.cyan);
    StyleConstants.setBackground(highlightStyles[1], Color.green);
    StyleConstants.setBackground(highlightStyles[2], Color.magenta);
    StyleConstants.setBackground(highlightStyles[3], Color.orange);
    StyleConstants.setBackground(highlightStyles[4], Color.pink);
    StyleConstants.setBackground(highlightStyles[5], Color.yellow);
  }

  /**
   * returns the number of highlighter colors available
   */
  public int numHighlighters() {
    return NUM_HIGHLIGHTERS;
  }

  /**
   * Clears the highlights from text
   */
  public void clearHighlight() {
    highlightText(0, editorPane.getText().length() - 1, normalStyle);
    clearButton.setEnabled(true);
  }

  /**
   * Highlights all occurrences of the given text using the highlight color
   * specified by the given index
   */
  public void highlightText(String text, int color) {
    if (text.length() == 0) {
      return;
    }
    Document doc = editorPane.getDocument();
    String docText = "";
    try {
      docText = doc.getText(0, doc.getLength());
    } catch (Exception e) {
      e.printStackTrace();
    }

    int fromIndex = 0;
    int start;
    while ((start = docText.indexOf(text, fromIndex)) != -1) {
      highlightText(start, start + text.length(), highlightStyles[color]);
      fromIndex = start + text.length();
      clearButton.setEnabled(true);
    }
  }

  /* highlights specified text region by changing the character attributes */
  private void highlightText(int start, int end, SimpleAttributeSet style) {
    if (end > start) {
      editorPane.getStyledDocument().setCharacterAttributes(start, end - start, style, false);
    }
  }

  /**
   * sets the current cls which can be extracted by the browser
   */
  public void setCls(String cls) {
    this.cls = cls;
    emPanel.setSelectedClass(cls); // so assignments will pre-select this class
  }

  /**
   * sets the slots which can be extracted by the browser
   */
  public void setSlotNames(Set<String> slotNames) {
    this.slotNames = slotNames;
  }

  /**
   * Sets the status bar text of the browser
   */
  public void setStatus(String status) {
    if (status == null || status.length() == 0) {
      status = " ";
    }
    statusLabel.setText(status);
  }

  /* sets the index of the current page within the history and
   * adjusts the back/forward buttons accordingly
   */
  private void setHistoryIndex(int historyIndex) {
    this.historyIndex = historyIndex;
    backButton.setEnabled(historyIndex > 0);
    forwardButton.setEnabled(historyIndex < history.size() - 1);
  }

  /* loads the content of urlOrFile into editor pane and adds it to the history */
  private void setNewPage(String urlOrFile) {
    if (setPage(urlOrFile)) {
      // add to history
      history.add(historyIndex + 1, urlOrFile);
      // if adding to center of history, remove urls following
      while (historyIndex + 1 < history.size() - 1) {
        history.removeLast();
      }
      setHistoryIndex(historyIndex + 1);
      reloadButton.setEnabled(true);
    }
  }

  /* load the page at the given index within the history */
  private void setPage(int historyIndex) {
    if (historyIndex >= 0 && historyIndex < history.size()) {
      setPage(history.get(historyIndex));
      setHistoryIndex(historyIndex);
    }
  }

  private boolean setPage(String urlOrFile) {
    if (urlOrFile.startsWith("/") || urlOrFile.startsWith("\\") || urlOrFile.startsWith(":", 1)) {
      urlOrFile = "file://" + urlOrFile;
    } else if (urlOrFile.indexOf("://") == -1) {
      urlOrFile = "http://" + urlOrFile;
    }
    urlField.setText(urlOrFile);
    current = urlOrFile;
    setStatus("Loading " + urlOrFile + "...");
    try {
      URL url = new URL(urlOrFile);
      URLConnection connection = url.openConnection();
      // checks to see if this url will be redirected
      if (connection instanceof HttpURLConnection) {
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        httpConnection.setRequestMethod("HEAD");
        httpConnection.connect();
        int response = httpConnection.getResponseCode();
        // if response code is redirect, load the page at the redirect url
        if (response >= HTTP_REDIRECT_MIN && response <= HTTP_REDIRECT_MAX) {
          String location = httpConnection.getHeaderField("Location");
          if (location != null && location.length() > 0) {
            return setPage(location);
          } else {
            return false;
          }
        } else if (response >= HTTP_ERROR_MIN) {
          editorPane.setText(httpConnection.getResponseMessage());
          setStatus("Error");
          return (true);
        }
      }
      InputStream in = url.openStream();
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      HtmlCleaner htmlCleaner = new HtmlCleaner(in, out);
      htmlCleaner.clean();

      in = new ByteArrayInputStream(out.toByteArray());
      // provide base url to resolve relative links against
      HTMLDocument htmlDoc = new HTMLDocument();
      htmlDoc.setBase(url);
      editorPane.read(in, htmlDoc);

      // scroll to top of document
      editorPane.setCaretPosition(0);
      //editorPane.setPage(urlOrFile);
      setStatus("Done.");
      return true;
    } catch (MalformedURLException mue) {
      setStatus("Malformed URL: " + urlOrFile);
    } catch (FileNotFoundException fnfex) {
      setStatus("File not found: " + urlOrFile);
    } catch (UnknownHostException uhe) {
      editorPane.setText("The page could not be found.");
      setStatus("Error");
      return (true);
    } catch (Exception ex) {
      editorPane.setText(ex.toString());
      setStatus("Error");
      return (true);
      //ex.printStackTrace();
    }
    return false;
  }

  // class for catching and handling hyperlink events in the editor pane
  private class BrowserHyperlinkListener implements HyperlinkListener {
    private String oldStatus;

    public BrowserHyperlinkListener() {
    }

    public void hyperlinkUpdate(HyperlinkEvent e) {
      HyperlinkEvent.EventType type = e.getEventType();
      if (type == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
        setNewPage(e.getURL().toString());
      } else if (type == HyperlinkEvent.EventType.ENTERED) {
        oldStatus = statusLabel.getText();
        if (e.getURL() != null) {
          setStatus(e.getURL().toString());
        }
      } else if (type == HyperlinkEvent.EventType.EXITED) {
        setStatus(oldStatus);
      }
    }
  }

  private class PopupMenuItem extends JMenuItem {
    /**
     * 
     */
    private static final long serialVersionUID = 5915560388118873331L;

    public PopupMenuItem(String label) {
      super(label);
      addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          fireSlotFilled(getText(), editorPane.getSelectedText());
        }
      });
    }
  }

  // observer-observed code
  public void addSlotEventListener(edu.stanford.nlp.ie.SlotEventListener listener) {
    listeners.add(listener);
  }

  public void removeSlotEventlistener(SlotEventListener listener) {
    listeners.remove(listener);
  }

  protected void fireSlotFilled(String slot, String value) {
    edu.stanford.nlp.ie.SlotEventListener listener;
    for (int i = 0; i < listeners.size(); i++) {
      listener = listeners.get(i);
      listener.slotFilled(slot, value);
    }
  }

  private void fireAllSlotsFilled(Map<String,String> valuesBySlot) {
    edu.stanford.nlp.ie.SlotEventListener listener;
    for (int i = 0; i < listeners.size(); i++) {
      listener = listeners.get(i);
      listener.allSlotsFilled(valuesBySlot);
    }
  }

  public static void main(String[] argv) {
    JFrame frame = new JFrame("HTML Document Browser");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add("Center", new DocumentBrowser());
    frame.pack();
    frame.setVisible(true);
  }
}
