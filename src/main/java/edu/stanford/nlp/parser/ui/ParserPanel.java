// StanfordLexicalizedParser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002, 2003, 2004, 2005 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/lex-parser.html

package edu.stanford.nlp.parser.ui;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.ui.OpenPageDialog;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.swing.FontDetector;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.international.pennchinese.ChineseTreebankLanguagePack;
import edu.stanford.nlp.ui.JarFileChooser;
import edu.stanford.nlp.util.logging.Redwood;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * Provides a simple GUI Panel for Parsing.  Allows a user to load a parser
 * created using lexparser.LexicalizedParser, load a text data file or type
 * in text, parse sentences within the input text, and view the resultant
 * parse tree.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class ParserPanel extends JPanel  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ParserPanel.class);

  private static final long serialVersionUID = -2118491857333662471L;

  // constants for language specification
  public static final int UNTOKENIZED_ENGLISH = 0;
  public static final int TOKENIZED_CHINESE = 1;
  public static final int UNTOKENIZED_CHINESE = 2;

  private static TreebankLanguagePack tlp;
  private String encoding = "UTF-8";

  // one second in milliseconds
  private static final int ONE_SECOND = 1000;
  // parser takes approximately a minute to load
  private static final int PARSER_LOAD_TIME = 60;
  // parser takes 5-60 seconds to parse a sentence
  private static final int PARSE_TIME = 30;

  // constants for finding nearest sentence boundary
  private static final int SEEK_FORWARD = 1;
  private static final int SEEK_BACK = -1;

  private final JFileChooser jfc;
  private final JFileChooserLocation jfcLocation;
  private final JarFileChooser chooseJarParser;
  private OpenPageDialog pageDialog;

  // for highlighting
  private SimpleAttributeSet normalStyle, highlightStyle;
  private int startIndex, endIndex;

  private TreeJPanel treePanel;
  private ParserGrammar parser;

  // worker threads to handle long operations
  private LoadParserThread lpThread;
  private ParseThread parseThread;

  // to monitor progress of long operations
  private javax.swing.Timer timer;
  //private ProgressMonitor progressMonitor;
  private int count; // progress count
  // use glass pane to block input to components other than progressMonitor
  private Component glassPane;

  /** Whether to scroll one sentence forward after parsing. */
  private boolean scrollWhenDone;

  /**
   * Creates new form ParserPanel
   */
  public ParserPanel() {
    initComponents();

    // create dialogs for file selection
    jfc = new JFileChooser(System.getProperty("user.dir"));
    pageDialog = new OpenPageDialog(new Frame(), true);
    pageDialog.setFileChooser(jfc);

    jfcLocation = new JFileChooserLocation(jfc);

    tlp = new PennTreebankLanguagePack();
    encoding = tlp.getEncoding();
    setFont();

    // create a timer
    timer = new javax.swing.Timer(ONE_SECOND, new TimerListener());

    // for (un)highlighting text
    highlightStyle = new SimpleAttributeSet();
    normalStyle = new SimpleAttributeSet();
    StyleConstants.setBackground(highlightStyle, Color.yellow);
    StyleConstants.setBackground(normalStyle, textPane.getBackground());

    this.chooseJarParser = new JarFileChooser(".*\\.ser\\.gz", this);
  }

  /**
   * Scrolls back one sentence in the text
   */
  public void scrollBack() {
    highlightSentence(startIndex - 1);
    // scroll to highlight location
    textPane.setCaretPosition(startIndex);
  }

  /**
   * Scrolls forward one sentence in the text
   */
  public void scrollForward() {
    highlightSentence(endIndex + 1);
    // scroll to highlight location
    textPane.setCaretPosition(startIndex);
  }

  /**
   * Highlights specified text region by changing the character attributes
   */
  private void highlightText(int start, int end, SimpleAttributeSet style) {
    if (start < end) {
      textPane.getStyledDocument().setCharacterAttributes(start, end - start + 1, style, false);
    }
  }

  /**
   * Finds the sentence delimited by the closest sentence delimiter preceding
   * start and closest period following start.
   */
  private void highlightSentence(int start) {
    highlightSentence(start, -1);
  }

  /**
   * Finds the sentence delimited by the closest sentence delimiter preceding
   * start and closest period following end.  If end is less than start
   * (or -1), sets right boundary as closest period following start.
   * Actually starts search for preceding sentence delimiter at (start-1)
   */
  private void highlightSentence(int start, int end) {
    // clears highlight.  paints over entire document because the document may have changed
    highlightText(0, textPane.getText().length(), normalStyle);

    // if start<1 set startIndex to 0, otherwise set to index following closest preceding period
    startIndex = (start < 1) ? 0 : nearestDelimiter(textPane.getText(), start, SEEK_BACK) + 1;

    // if end<startIndex, set endIndex to closest period following startIndex
    // else, set it to closest period following end
    endIndex = nearestDelimiter(textPane.getText(), (end < startIndex) ? startIndex : end, SEEK_FORWARD);
    if (endIndex == -1) {
      endIndex = textPane.getText().length() - 1;
    }

    highlightText(startIndex, endIndex, highlightStyle);

    // enable/disable scroll buttons as necessary
    backButton.setEnabled(startIndex != 0);
    forwardButton.setEnabled(endIndex != textPane.getText().length() - 1);
    parseNextButton.setEnabled(forwardButton.isEnabled() && parser != null);
  }

  /**
   * Finds the nearest delimiter starting from index start. If <tt>seekDir</tt>
   * is SEEK_FORWARD, finds the nearest delimiter after start.  Else, if it is
   * SEEK_BACK, finds the nearest delimiter before start.
   */
  private int nearestDelimiter(String text, int start, int seekDir) {
    if (seekDir != SEEK_BACK && seekDir != SEEK_FORWARD) {
      throw new IllegalArgumentException("Unknown seek direction " +
                                         seekDir);
    }
    StringReader reader = new StringReader(text);
    DocumentPreprocessor processor = new DocumentPreprocessor(reader);
    TokenizerFactory<? extends HasWord> tf = tlp.getTokenizerFactory();
    processor.setTokenizerFactory(tf);
    List<Integer> boundaries = new ArrayList<>();
    for (List<HasWord> sentence : processor) {
      if (sentence.size() == 0)
        continue;
      if (!(sentence.get(0) instanceof HasOffset)) {
        throw new ClassCastException("Expected HasOffsets from the " +
                                     "DocumentPreprocessor");
      }
      if (boundaries.size() == 0) {
        boundaries.add(0);
      } else {
        HasOffset first = (HasOffset) sentence.get(0);
        boundaries.add(first.beginPosition());
      }
    }
    boundaries.add(text.length());
    for (int i = 0; i < boundaries.size() - 1; ++i) {
      if (boundaries.get(i) <= start && start < boundaries.get(i + 1)) {
        if (seekDir == SEEK_BACK) {
          return boundaries.get(i) - 1;
        } else if (seekDir == SEEK_FORWARD) {
          return boundaries.get(i + 1) - 1;
        }
      }
    }
    // The cursor position at the end is actually one past the text length.
    // We might as well highlight the last interval in that case.
    if (boundaries.size() >= 2 && start >= text.length()) {
      if (seekDir == SEEK_BACK) {
        return boundaries.get(boundaries.size() - 2) - 1;
      } else if (seekDir == SEEK_FORWARD) {
        return boundaries.get(boundaries.size() - 1) - 1;
      }
    }
    return -1;
  }

  /**
   * Highlights the sentence that is currently being selected by user
   * (via mouse highlight)
   */
  private void highlightSelectedSentence() {
    highlightSentence(textPane.getSelectionStart(), textPane.getSelectionEnd());
  }

  /**
   * Highlights the sentence that is currently being edited
   */
  private void highlightEditedSentence() {
    highlightSentence(textPane.getCaretPosition());

  }

  /**
   * Sets the status text at the bottom of the ParserPanel.
   */
  public void setStatus(String status) {
    statusLabel.setText(status);
  }

  private void setFont() {
    if (tlp instanceof ChineseTreebankLanguagePack) {
      setChineseFont();
    } else {
      textPane.setFont(new Font("Sans Serif", Font.PLAIN, 14));
      treePanel.setFont(new Font("Sans Serif", Font.PLAIN, 14));
    }
  }

  private void setChineseFont() {
    java.util.List<Font> fonts = FontDetector.supportedFonts(FontDetector.CHINESE);
    if (fonts.size() > 0) {
      Font font = new Font(fonts.get(0).getName(), Font.PLAIN, 14);
      textPane.setFont(font);
      treePanel.setFont(font);
      log.info("Selected font " + font);
    } else if (FontDetector.hasFont("Watanabe Mincho")) {
      textPane.setFont(new Font("Watanabe Mincho", Font.PLAIN, 14));
      treePanel.setFont(new Font("Watanabe Mincho", Font.PLAIN, 14));
    } else {
      textPane.setFont(new Font("Sans Serif", Font.PLAIN, 14));
      treePanel.setFont(new Font("Sans Serif", Font.PLAIN, 14));
    }
  }


  /**
   * Tokenizes the highlighted text (using a tokenizer appropriate for the
   * selected language, and initiates the ParseThread to parse the tokenized
   * text.
   */
  public void parse() {
    if (textPane.getText().length() == 0) {
      return;
    }

    // use endIndex+1 because substring subtracts 1
    String text = textPane.getText().substring(startIndex, endIndex + 1).trim();

    if (parser != null && text.length() > 0) {
      //Tokenizer<? extends HasWord> toke = tlp.getTokenizerFactory().getTokenizer(new CharArrayReader(text.toCharArray()));
      Tokenizer<? extends HasWord> toke = tlp.getTokenizerFactory().getTokenizer(new StringReader(text));
      List<? extends HasWord> wordList = toke.tokenize();
      parseThread = new ParseThread(wordList);
      parseThread.start();
      startProgressMonitor("Parsing", PARSE_TIME);
    }
  }

  /**
   * Opens dialog to load a text data file
   */
  public void loadFile() {
    // centers dialog in panel
    pageDialog.setLocation(getLocationOnScreen().x + (getWidth() - pageDialog.getWidth()) / 2, getLocationOnScreen().y + (getHeight() - pageDialog.getHeight()) / 2);
    pageDialog.setVisible(true);

    if (pageDialog.getStatus() == OpenPageDialog.APPROVE_OPTION) {
      loadFile(pageDialog.getPage());
    }
  }

  /**
   * Loads a text or html file from a file path or URL.  Treats anything
   * beginning with <tt>http:\\</tt>,<tt>.htm</tt>, or <tt>.html</tt> as an
   * html file, and strips all tags from the document
   */
  public void loadFile(String filename) {
    if (filename == null) {
      return;
    }

    File file = new File(filename);

    String urlOrFile = filename;
    // if file can't be found locally, try prepending http:// and looking on web
    if (!file.exists() && filename.indexOf("://") == -1) {
      urlOrFile = "http://" + filename;
    }
    // else prepend file:// to handle local html file urls
    else if (filename.indexOf("://") == -1) {
      urlOrFile = "file://" + filename;
    }

    // TODO: why do any of this instead of just reading the file?  THIS SHOULD BE UPDATED FOR 2017!
    // Also, is this working correctly still?
    // load the document
    Document<Object, Word, Word> doc;
    try {
      if (urlOrFile.startsWith("http://") || urlOrFile.endsWith(".htm") || urlOrFile.endsWith(".html")) {
        // strip tags from html documents
        Document<Object, Word, Word> docPre = new BasicDocument<>().init(new URL(urlOrFile));
        DocumentProcessor<Word, Word, Object, Word> noTags = new StripTagsProcessor<>();
        doc = noTags.processDocument(docPre);
      } else {
        doc = new BasicDocument<>(this.getTokenizerFactory()).init(new InputStreamReader(new FileInputStream(filename), encoding));
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Could not load file " + filename + "\n" + e, null, JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
      setStatus("Error loading document");
      return;
    }

    // load the document into the text pane
    StringBuilder docStr = new StringBuilder();
    for (Word aDoc : doc) {
      if (docStr.length() > 0) {
        docStr.append(' ');
      }
      docStr.append(aDoc.toString());
    }
    textPane.setText(docStr.toString());
    dataFileLabel.setText(urlOrFile);

    highlightSentence(0);
    forwardButton.setEnabled(endIndex != textPane.getText().length() - 1);
    // scroll to top of document
    textPane.setCaretPosition(0);

    setStatus("Done");
  }

  // TreebankLanguagePack returns a TokenizerFactory<? extends HasWord>
  // which isn't close enough in the type system, but is probably okay in practice
  @SuppressWarnings("unchecked")
  private static TokenizerFactory<Word> getTokenizerFactory() {
    return (TokenizerFactory<Word>)tlp.getTokenizerFactory();
  }

  /**
   * Opens a dialog and saves the output of the parser on the current
   * text.  If there is no current text, yell at the user and make
   * them feel bad instead.
   */
  public void saveOutput() {
    if (textPane.getText().trim().length() == 0) {
      JOptionPane.showMessageDialog(this, "No text to parse ", null,
                                    JOptionPane.ERROR_MESSAGE);
      return;
    }

    jfc.setDialogTitle("Save file");
    int status = jfc.showSaveDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      saveOutput(jfc.getSelectedFile().getPath());
    }
  }

  /**
   * Saves the results of applying the parser to the current text to
   * the specified filename.
   */
  public void saveOutput(String filename) {
    if (filename == null || filename.equals("")) {
      return;
    }

    String text = textPane.getText();
    StringReader reader = new StringReader(text);
    DocumentPreprocessor processor = new DocumentPreprocessor(reader);
    TokenizerFactory<? extends HasWord> tf = tlp.getTokenizerFactory();
    processor.setTokenizerFactory(tf);
    List<List<HasWord>> sentences = new ArrayList<>();
    for (List<HasWord> sentence : processor) {
      sentences.add(sentence);
    }

    JProgressBar progress = new JProgressBar(0, sentences.size());
    JButton cancel = new javax.swing.JButton();

    JDialog dialog = new JDialog(new Frame(), "Parser Progress", true);

    dialog.setSize(300, 150);
    dialog.add(BorderLayout.NORTH,
               new JLabel("Parsing " + sentences.size() + " sentences"));
    dialog.add(BorderLayout.CENTER, progress);
    dialog.add(BorderLayout.SOUTH, cancel);
    //dialog.add(progress);

    final SaveOutputThread thread =
      new SaveOutputThread(filename, progress, dialog, cancel, sentences);

    cancel.setText("Cancel");
    cancel.setToolTipText("Cancel");
    cancel.addActionListener(evt -> thread.cancelled = true);

    thread.start();

    dialog.setVisible(true);
  }

  /**
   * This class does the processing of the dialog box to a file.  It
   * also checks the cancelled variable after each processing to see
   * if the user has chosen to cancel.  After running, it changes the
   * label on the "cancel" button, waits a couple seconds, and then
   * hides whatever dialog was passed in when originally created.
   */
  class SaveOutputThread extends Thread {
    String filename;
    JProgressBar progress;
    JDialog dialog;
    JButton button;
    List<List<HasWord>> sentences;

    boolean cancelled;

    public SaveOutputThread(String filename, JProgressBar progress,
                            JDialog dialog, JButton button,
                            List<List<HasWord>> sentences) {
      this.filename = filename;
      this.progress = progress;
      this.dialog = dialog;
      this.button = button;
      this.sentences = sentences;
    }

    public void run() {
      int failures = 0;
      try {
        FileOutputStream fos = new FileOutputStream(filename);
        OutputStreamWriter ow = new OutputStreamWriter(fos, "utf-8");
        BufferedWriter bw = new BufferedWriter(ow);

        for (List<HasWord> sentence : sentences) {
          Tree tree = parser.parseTree(sentence);
          if (tree == null) {
            ++failures;
            log.info("Failed on sentence " + sentence);
          } else {
            bw.write(tree.toString());
            bw.newLine();
          }

          progress.setValue(progress.getValue() + 1);
          if (cancelled) {
            break;
          }
        }
        bw.flush();
        bw.close();
        ow.close();
        fos.close();
      } catch (IOException e) {
        JOptionPane.showMessageDialog(ParserPanel.this, "Could not save file " + filename + "\n" + e, null, JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
        setStatus("Error saving parsed document");
      }

      if (failures == 0) {
        button.setText("Success!");
      } else {
        button.setText("Done.  " + failures + " parses failed");
      }
      if (cancelled && failures == 0) {
        dialog.setVisible(false);
      } else {
        button.addActionListener(evt -> dialog.setVisible(false));
      }
    }

  } // end class SaveOutputThread

  /**
   * Opens dialog to load a serialized parser
   */
  public void loadParser() {
    jfc.setDialogTitle("Load parser");
    int status = jfc.showOpenDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      String filename = jfc.getSelectedFile().getPath();
      if (filename.endsWith(".jar")) {
        String model = chooseJarParser.show(filename, jfcLocation.location);
        if (model != null) {
          loadJarParser(filename, model);
        }
      } else {
        loadParser(filename);
      }
    }
  }

  public void loadJarParser(String jarFile, String model) {
    lpThread = new LoadParserThread(jarFile, model);
    lpThread.start();
    startProgressMonitor("Loading Parser", PARSER_LOAD_TIME);
  }

  /**
   * Loads a serialized parser specified by given path
   */
  public void loadParser(String filename) {
    if (filename == null) {
      return;
    }

    // check if file exists before we start the worker thread and progress monitor
    boolean exists = false;
    try {
      IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(filename);
      exists = true;
    } catch (IOException e) {
      // exists will stay false
    }
    if (exists) {
      lpThread = new LoadParserThread(filename);
      lpThread.start();
      startProgressMonitor("Loading Parser", PARSER_LOAD_TIME);
    } else {
      JOptionPane.showMessageDialog(this, "Could not find file " + filename, null, JOptionPane.ERROR_MESSAGE);
      setStatus("Error loading parser");
    }
  }

  /**
   * Initializes the progress bar with the status text, and the expected
   * number of seconds the process will take, and starts the timer.
   */
  private void startProgressMonitor(String text, int maxCount) {
    if (glassPane == null) {
      if (getRootPane() != null) {
        glassPane = getRootPane().getGlassPane();
        glassPane.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evt) {
            Toolkit.getDefaultToolkit().beep();
          }
        });
      }
    }
    if (glassPane != null) {
      glassPane.setVisible(true); // block input to components
    }

    statusLabel.setText(text);
    progressBar.setMaximum(maxCount);
    progressBar.setValue(0);
    count = 0;
    timer.start();
    progressBar.setVisible(true);
  }

  /**
   * At the end of a task, shut down the progress monitor
   */
  private void stopProgressMonitor() {
    timer.stop();
    /*if(progressMonitor!=null) {
        progressMonitor.setProgress(progressMonitor.getMaximum());
        progressMonitor.close();
    }*/
    progressBar.setVisible(false);
    if (glassPane != null) {
      glassPane.setVisible(false); // restore input to components
    }
    lpThread = null;
    parseThread = null;
  }

  /**
   * Worker thread for loading the parser.  Loading a parser usually
   * takes ~15s
   */
  private class LoadParserThread extends Thread {
    final String zipFilename;
    final String filename;

    LoadParserThread(String filename) {
      this.filename = filename;
      this.zipFilename = null;
    }

    LoadParserThread(String zipFilename, String filename) {
      this.zipFilename = zipFilename;
      this.filename = filename;
    }

    @Override
    public void run() {
      try {
        if (zipFilename != null) {
          parser = ParserGrammar.loadModelFromZip(zipFilename, filename);
        } else {
          parser = ParserGrammar.loadModel(filename);
        }
      } catch (Exception ex) {
        JOptionPane.showMessageDialog(ParserPanel.this, "Error loading parser: " + filename, null, JOptionPane.ERROR_MESSAGE);
        setStatus("Error loading parser");
        parser = null;
      } catch (OutOfMemoryError e) {
        JOptionPane.showMessageDialog(ParserPanel.this, "Could not load parser. Out of memory.", null, JOptionPane.ERROR_MESSAGE);
        setStatus("Error loading parser");
        parser = null;
      }

      stopProgressMonitor();
      if (parser != null) {
        setStatus("Loaded parser.");
        parserFileLabel.setText("Parser: " + filename);
        parseButton.setEnabled(true);
        parseNextButton.setEnabled(true);
        saveOutputButton.setEnabled(true);

        tlp = parser.getOp().langpack();
        encoding = tlp.getEncoding();
      }
    }
  }

  /**
   * Worker thread for parsing.  Parsing a sentence usually takes ~5-60 sec
   */
  private class ParseThread extends Thread {

    List<? extends HasWord> sentence;

    public ParseThread(List<? extends HasWord> sentence) {
      this.sentence = sentence;
    }

    @Override
    public void run() {
      boolean successful;
      ParserQuery parserQuery = parser.parserQuery();
      try {
        successful = parserQuery.parse(sentence);
      } catch (Exception e) {
        stopProgressMonitor();
        JOptionPane.showMessageDialog(ParserPanel.this, "Could not parse selected sentence\n(sentence probably too long)", null, JOptionPane.ERROR_MESSAGE);
        setStatus("Error parsing");
        return;
      }

      stopProgressMonitor();
      setStatus("Done");
      if (successful) {
        // display the best parse
        Tree tree = parserQuery.getBestParse();
        //tree.pennPrint();
        treePanel.setTree(tree);
        clearButton.setEnabled(true);
      } else {
        JOptionPane.showMessageDialog(ParserPanel.this, "Could not parse selected sentence", null, JOptionPane.ERROR_MESSAGE);
        setStatus("Error parsing");
        treePanel.setTree(null);
        clearButton.setEnabled(false);
      }
      if (scrollWhenDone) {
        scrollForward();
      }
    }
  }

  private static class JFileChooserLocation implements AncestorListener {
    Point location;

    JFileChooser jfc;

    JFileChooserLocation(JFileChooser jfc) {
      this.jfc = jfc;
      jfc.addAncestorListener(this);
    }

    public void ancestorAdded(AncestorEvent event) {
      location = jfc.getTopLevelAncestor().getLocationOnScreen();
    }

    public void ancestorMoved(AncestorEvent event) {
      location = jfc.getTopLevelAncestor().getLocationOnScreen();
    }

    public void ancestorRemoved(AncestorEvent event) { }
  }

  /**
   * Simulates a timer to update the progress monitor
   */
  private class TimerListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      //progressMonitor.setProgress(Math.min(count++,progressMonitor.getMaximum()-1));
      progressBar.setValue(Math.min(count++, progressBar.getMaximum() - 1));
    }
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  private void initComponents()//GEN-BEGIN:initComponents
  {
    splitPane = new javax.swing.JSplitPane();
    topPanel = new javax.swing.JPanel();
    buttonsAndFilePanel = new javax.swing.JPanel();
    loadButtonPanel = new javax.swing.JPanel();
    loadFileButton = new javax.swing.JButton();
    loadParserButton = new javax.swing.JButton();
    saveOutputButton = new javax.swing.JButton();
    buttonPanel = new javax.swing.JPanel();
    backButton = new javax.swing.JButton();
    if (getClass().getResource("/edu/stanford/nlp/parser/ui/leftarrow.gif") != null) {
      backButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/edu/stanford/nlp/parser/ui/leftarrow.gif")));
    } else {
      backButton.setText("< Prev");
    }
    forwardButton = new javax.swing.JButton();
    if (getClass().getResource("/edu/stanford/nlp/parser/ui/rightarrow.gif") != null) {
      forwardButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/edu/stanford/nlp/parser/ui/rightarrow.gif")));
    } else {
      forwardButton.setText("Next >");
    }
    parseButton = new javax.swing.JButton();
    parseNextButton = new javax.swing.JButton();
    clearButton = new javax.swing.JButton();
    dataFilePanel = new javax.swing.JPanel();
    dataFileLabel = new javax.swing.JLabel();
    textScrollPane = new javax.swing.JScrollPane();
    textPane = new javax.swing.JTextPane();
    treeContainer = new javax.swing.JPanel();
    parserFilePanel = new javax.swing.JPanel();
    parserFileLabel = new javax.swing.JLabel();
    statusPanel = new javax.swing.JPanel();
    statusLabel = new javax.swing.JLabel();
    progressBar = new javax.swing.JProgressBar();
    progressBar.setVisible(false);

    setLayout(new java.awt.BorderLayout());

    splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    topPanel.setLayout(new java.awt.BorderLayout());

    buttonsAndFilePanel.setLayout(new javax.swing.BoxLayout(buttonsAndFilePanel, javax.swing.BoxLayout.Y_AXIS));

    loadButtonPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    loadFileButton.setText("Load File");
    loadFileButton.setToolTipText("Load a data file.");
    loadFileButton.addActionListener(evt -> loadFileButtonActionPerformed(evt));

    loadButtonPanel.add(loadFileButton);

    loadParserButton.setText("Load Parser");
    loadParserButton.setToolTipText("Load a serialized parser.");
    loadParserButton.addActionListener(evt -> loadParserButtonActionPerformed(evt));

    loadButtonPanel.add(loadParserButton);

    saveOutputButton.setText("Save Output");
    saveOutputButton.setToolTipText("Save the processed output.");
    saveOutputButton.setEnabled(false);
    saveOutputButton.addActionListener(evt -> saveOutputButtonActionPerformed(evt));

    loadButtonPanel.add(saveOutputButton);

    buttonsAndFilePanel.add(loadButtonPanel);

    buttonPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    backButton.setToolTipText("Scroll backward one sentence.");
    backButton.setEnabled(false);
    backButton.addActionListener(evt -> backButtonActionPerformed(evt));

    buttonPanel.add(backButton);

    forwardButton.setToolTipText("Scroll forward one sentence.");
    forwardButton.setEnabled(false);
    forwardButton.addActionListener(evt -> forwardButtonActionPerformed(evt));

    buttonPanel.add(forwardButton);

    parseButton.setText("Parse");
    parseButton.setToolTipText("Parse selected sentence.");
    parseButton.setEnabled(false);
    parseButton.addActionListener(evt -> parseButtonActionPerformed(evt));

    buttonPanel.add(parseButton);

    parseNextButton.setText("Parse >");
    parseNextButton.setToolTipText("Parse selected sentence and then scrolls forward one sentence.");
    parseNextButton.setEnabled(false);
    parseNextButton.addActionListener(evt -> parseNextButtonActionPerformed(evt));

    buttonPanel.add(parseNextButton);

    clearButton.setText("Clear");
    clearButton.setToolTipText("Clears parse tree.");
    clearButton.setEnabled(false);
    clearButton.addActionListener(evt -> clearButtonActionPerformed(evt));

    buttonPanel.add(clearButton);

    buttonsAndFilePanel.add(buttonPanel);

    dataFilePanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    dataFilePanel.add(dataFileLabel);

    buttonsAndFilePanel.add(dataFilePanel);

    topPanel.add(buttonsAndFilePanel, java.awt.BorderLayout.NORTH);

    textPane.setPreferredSize(new java.awt.Dimension(250, 250));
    textPane.addFocusListener(new java.awt.event.FocusAdapter() {
      @Override
      public void focusLost(java.awt.event.FocusEvent evt) {
        textPaneFocusLost(evt);
      }
    });

    textPane.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        textPaneMouseClicked(evt);
      }
    });

    textPane.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
      @Override
      public void mouseDragged(java.awt.event.MouseEvent evt) {
        textPaneMouseDragged(evt);
      }
    });

    textScrollPane.setViewportView(textPane);

    topPanel.add(textScrollPane, java.awt.BorderLayout.CENTER);

    splitPane.setLeftComponent(topPanel);

    treeContainer.setLayout(new java.awt.BorderLayout());

    treeContainer.setBackground(new java.awt.Color(255, 255, 255));
    treeContainer.setBorder(new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED));
    treeContainer.setForeground(new java.awt.Color(0, 0, 0));
    treeContainer.setPreferredSize(new java.awt.Dimension(200, 200));
    treePanel = new TreeJPanel();
    treeContainer.add("Center", treePanel);
    treePanel.setBackground(Color.white);
    parserFilePanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    parserFilePanel.setBackground(new java.awt.Color(255, 255, 255));
    parserFileLabel.setText("Parser: None");
    parserFilePanel.add(parserFileLabel);

    treeContainer.add(parserFilePanel, java.awt.BorderLayout.NORTH);

    splitPane.setRightComponent(treeContainer);

    add(splitPane, java.awt.BorderLayout.CENTER);

    statusPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    statusLabel.setText("Ready");
    statusPanel.add(statusLabel);

    progressBar.setName("");
    statusPanel.add(progressBar);

    add(statusPanel, java.awt.BorderLayout.SOUTH);

    //Roger -- test to see if I can get a bit of a fix with new font

  }//GEN-END:initComponents

  private void textPaneFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_textPaneFocusLost
  {//GEN-HEADEREND:event_textPaneFocusLost
    // highlights the sentence containing the current location of the cursor
    // note that the cursor is set to the beginning of the sentence when scrolling
    highlightEditedSentence();
  }//GEN-LAST:event_textPaneFocusLost

  private void parseNextButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_parseNextButtonActionPerformed
  {//GEN-HEADEREND:event_parseNextButtonActionPerformed
    parse();
    scrollWhenDone = true;
  }//GEN-LAST:event_parseNextButtonActionPerformed

  private void clearButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_clearButtonActionPerformed
  {//GEN-HEADEREND:event_clearButtonActionPerformed
    treePanel.setTree(null);
    clearButton.setEnabled(false);
  }//GEN-LAST:event_clearButtonActionPerformed

  private void textPaneMouseDragged(java.awt.event.MouseEvent evt)//GEN-FIRST:event_textPaneMouseDragged
  {//GEN-HEADEREND:event_textPaneMouseDragged
    highlightSelectedSentence();
  }//GEN-LAST:event_textPaneMouseDragged

  private void textPaneMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_textPaneMouseClicked
  {//GEN-HEADEREND:event_textPaneMouseClicked
    highlightSelectedSentence();
  }//GEN-LAST:event_textPaneMouseClicked

  private void parseButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_parseButtonActionPerformed
  {//GEN-HEADEREND:event_parseButtonActionPerformed
    parse();
    scrollWhenDone = false;
  }//GEN-LAST:event_parseButtonActionPerformed

  private void loadParserButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadParserButtonActionPerformed
  {//GEN-HEADEREND:event_loadParserButtonActionPerformed
    loadParser();
  }//GEN-LAST:event_loadParserButtonActionPerformed

  private void saveOutputButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveOutputButtonActionPerformed
  {//GEN-HEADEREND:event_saveOutputButtonActionPerformed
    saveOutput();
  }//GEN-LAST:event_saveOutputButtonActionPerformed

  private void loadFileButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadFileButtonActionPerformed
  {//GEN-HEADEREND:event_loadFileButtonActionPerformed
    loadFile();
  }//GEN-LAST:event_loadFileButtonActionPerformed

  private void backButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_backButtonActionPerformed
  {//GEN-HEADEREND:event_backButtonActionPerformed
    scrollBack();
  }//GEN-LAST:event_backButtonActionPerformed

  private void forwardButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_forwardButtonActionPerformed
  {//GEN-HEADEREND:event_forwardButtonActionPerformed
    scrollForward();
  }//GEN-LAST:event_forwardButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel dataFileLabel;
  private javax.swing.JPanel treeContainer;
  private javax.swing.JPanel topPanel;
  private javax.swing.JScrollPane textScrollPane;
  private javax.swing.JButton backButton;
  private javax.swing.JLabel statusLabel;
  private javax.swing.JButton loadFileButton;
  private javax.swing.JPanel loadButtonPanel;
  private javax.swing.JPanel buttonsAndFilePanel;
  private javax.swing.JButton parseButton;
  private javax.swing.JButton parseNextButton;
  private javax.swing.JButton forwardButton;
  private javax.swing.JLabel parserFileLabel;
  private javax.swing.JButton clearButton;
  private javax.swing.JSplitPane splitPane;
  private javax.swing.JPanel statusPanel;
  private javax.swing.JPanel dataFilePanel;
  private javax.swing.JPanel buttonPanel;
  private javax.swing.JTextPane textPane;
  private javax.swing.JProgressBar progressBar;
  private javax.swing.JPanel parserFilePanel;
  private javax.swing.JButton loadParserButton;
  private javax.swing.JButton saveOutputButton;
  // End of variables declaration//GEN-END:variables
}
