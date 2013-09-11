// StanfordNamedEntityRecognizer -- a CMM named-entity recognizer
// Copyright (c) 2002, 2003 Leland Stanford Junior University
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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 4A
//    Stanford CA 94305-9040
//    USA
//    manning@cs.stanford.edu
//    http://nlp.stanford.edu/downloads/lex-parser.shtml

package edu.stanford.nlp.ie.ner.ui;

import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.io.ui.OpenPageDialog;
import edu.stanford.nlp.ling.BasicDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.process.DocumentProcessor;
import edu.stanford.nlp.process.StripTagsProcessor;
import edu.stanford.nlp.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;
import java.io.*;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/**
 * A simple UI for loading a serialized {@link CMMClassifier} and using it to
 * perform named-entity recognition on text.  Just run the main method.
 *
 * @author Huy Nguyen
 */
public class NER extends javax.swing.JFrame {
  /**
   * 
   */
  private static final long serialVersionUID = 7793114786775015291L;
  private JFileChooser jfc;
  private OpenPageDialog pageDialog;
  private CMMClassifier classifier;
  private TextFrame resultFrame;

  /**
   * Creates new form NER
   */
  public NER() {
    initComponents();
    jfc = new JFileChooser();
    pageDialog = new OpenPageDialog(new Frame(), true);
    pageDialog.setFileChooser(jfc);
  }

  private void loadClassifier(String path) {
    setStatus("Loading classifier from " + path + "...");
    try {
      InputStream in;
      if (path.endsWith(".gz")) {
        in = new GZIPInputStream(new FileInputStream(path));
      } else {
        in = new FileInputStream(path);
      }

      classifier = CMMClassifier.getClassifier(new BufferedInputStream(new ProgressMonitorInputStream(this, "Loading serialized classifier from " + path, in)));
    } catch (Exception e) {
      setStatus("Error loading classifier.");
      return;
    }
    setStatus("Classifier successfully loaded.");
    processButton.setEnabled(true);
  }

  private void loadFile(String path) {
    if (path == null) {
      return;
    }

    File file = new File(path);

    String urlOrFile = path;
    // if file can't be found locally, try prepending http:// and looking on web
    if (!file.exists() && path.indexOf("://") == -1) {
      urlOrFile = "http://" + path;
    }
    // else prepend file:// to handle local html file urls
    else if (path.indexOf("://") == -1) {
      urlOrFile = "file://" + path;
    }

    // load the document
    edu.stanford.nlp.ling.Document doc = null;
    try {
      if (urlOrFile.startsWith("http://") || urlOrFile.endsWith(".htm") || urlOrFile.endsWith(".html")) {
        // strip tags from html documents
        Document docPre = new BasicDocument().init(new URL(urlOrFile));
        DocumentProcessor noTags = new StripTagsProcessor();
        doc = noTags.processDocument(docPre);
      } else {
        doc = new BasicDocument().init(new InputStreamReader(new FileInputStream(path)));
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Could not load file " + path + ";" + e, null, JOptionPane.ERROR_MESSAGE);
      setStatus("Error loading document.");
      return;
    }

    // load the document into the text pane
    StringBuffer docStr = new StringBuffer();
    Iterator it = doc.iterator();
    while (it.hasNext()) {
      if (docStr.length() > 0) {
        docStr.append(' ');
      }
      docStr.append(it.next().toString());
    }
    textPane.setText(docStr.toString());
    textPane.setCaretPosition(0); // scroll to top
    setStatus("Ready to process.");
  }

  private void setStatus(String status) {
    statusLabel.setText(status);
  }

  private String convertTagsToXML(String text) {
    StringBuffer sb = new StringBuffer();
    String[] words = text.split(" ");
    String lastTag = "O";
    for (int i = 0; i < words.length; i++) {
      int index = words[i].lastIndexOf('/');
      if (index != -1) {
        String word = words[i].substring(0, index);
        String tag = words[i].substring(index + 1);
        if (i > 0) {
          sb.append(' ');
        }
        if (!lastTag.equals(tag)) {
          if (!"O".equals(lastTag)) {
            sb.append("</" + lastTag + ">");
            sb.append(' ');
          }
          if (!"O".equals(tag)) {
            sb.append("<" + tag + ">");
          }
        }
        lastTag = tag;
        sb.append(word);
      }
    }
    return sb.toString();
  }

  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  private void initComponents()//GEN-BEGIN:initComponents
  {
    jPanel1 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textPane = new javax.swing.JTextPane();
    jPanel2 = new javax.swing.JPanel();
    processButton = new javax.swing.JButton();
    jPanel3 = new javax.swing.JPanel();
    statusLabel = new javax.swing.JLabel();
    jMenuBar1 = new javax.swing.JMenuBar();
    jMenu1 = new javax.swing.JMenu();
    openFileItem = new javax.swing.JMenuItem();
    loadClassifierItem = new javax.swing.JMenuItem();
    jSeparator1 = new javax.swing.JSeparator();
    exitItem = new javax.swing.JMenuItem();

    setTitle("Stanford Named-Entity Recognizer");
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent evt) {
        exitForm(evt);
      }
    });

    jPanel1.setLayout(new java.awt.BorderLayout());

    jPanel1.setPreferredSize(new java.awt.Dimension(600, 400));
    jScrollPane1.setViewportView(textPane);

    jPanel1.add(jScrollPane1, java.awt.BorderLayout.CENTER);

    jPanel2.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    processButton.setMnemonic('p');
    processButton.setText("Process");
    processButton.setToolTipText("Process text and label named-entities.");
    processButton.setEnabled(false);
    processButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        processButtonActionPerformed(evt);
      }
    });

    jPanel2.add(processButton);

    jPanel1.add(jPanel2, java.awt.BorderLayout.NORTH);

    jPanel3.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

    statusLabel.setText("status");
    jPanel3.add(statusLabel);

    jPanel1.add(jPanel3, java.awt.BorderLayout.SOUTH);

    getContentPane().add(jPanel1, java.awt.BorderLayout.CENTER);

    jMenu1.setMnemonic('f');
    jMenu1.setText("File");
    openFileItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.ALT_MASK));
    openFileItem.setMnemonic('o');
    openFileItem.setText("Open Document");
    openFileItem.setToolTipText("Open a text file or URL to process.");
    openFileItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        openFileItemActionPerformed(evt);
      }
    });

    jMenu1.add(openFileItem);
    loadClassifierItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.ALT_MASK));
    loadClassifierItem.setMnemonic('n');
    loadClassifierItem.setText("Load NER");
    loadClassifierItem.setToolTipText("Load a serialized Named-Entity Recognizer");
    loadClassifierItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadClassifierItemActionPerformed(evt);
      }
    });

    jMenu1.add(loadClassifierItem);
    jMenu1.add(jSeparator1);
    exitItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.ALT_MASK));
    exitItem.setMnemonic('x');
    exitItem.setText("Exit");
    exitItem.setToolTipText("Exit program.");
    exitItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        exitItemActionPerformed(evt);
      }
    });

    jMenu1.add(exitItem);
    jMenuBar1.add(jMenu1);
    setJMenuBar(jMenuBar1);

    pack();
  }//GEN-END:initComponents

  private void processButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_processButtonActionPerformed
  {//GEN-HEADEREND:event_processButtonActionPerformed
    String text = textPane.getSelectedText();
    if (text == null) {
      text = textPane.getText();
    }
    setStatus("Processing...");
    if (resultFrame == null) {
      resultFrame = new TextFrame(jfc);
    }

    resultFrame.setText(convertTagsToXML(classifier.classifyToString(text)));
    resultFrame.setVisible(true);
    // add an offset if the result screen is overlapping
    if (resultFrame.getLocationOnScreen().equals(getLocationOnScreen())) {
      resultFrame.setLocation(getLocationOnScreen().x + 25, getLocationOnScreen().y + 25);
    }
    setStatus("Ready.");
  }//GEN-LAST:event_processButtonActionPerformed

  private void exitItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_exitItemActionPerformed
  {//GEN-HEADEREND:event_exitItemActionPerformed
    exitForm(null);
  }//GEN-LAST:event_exitItemActionPerformed

  private void loadClassifierItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadClassifierItemActionPerformed
  {//GEN-HEADEREND:event_loadClassifierItemActionPerformed
    setStatus("Select a serialized classifier.");
    jfc.setDialogTitle("Open Serialized Classifier");
    int ret = jfc.showOpenDialog(this);
    if (ret == JFileChooser.APPROVE_OPTION) {
      try {
        loadClassifier(jfc.getSelectedFile().getPath());
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Error opening " + jfc.getSelectedFile().getPath(), "Error", ImageObserver.ERROR);
      }
    }
  }//GEN-LAST:event_loadClassifierItemActionPerformed

  private void openFileItemActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_openFileItemActionPerformed
  {//GEN-HEADEREND:event_openFileItemActionPerformed
    setStatus("Select a text file or web page to process.");
    // centers dialog in panel
    pageDialog.setLocation(getLocationOnScreen().x + (getWidth() - pageDialog.getWidth()) / 2, getLocationOnScreen().y + (getHeight() - pageDialog.getHeight()) / 2);
    pageDialog.setVisible(true);

    if (pageDialog.getStatus() == OpenPageDialog.APPROVE_OPTION) {
      loadFile(pageDialog.getPage());
    }
  }//GEN-LAST:event_openFileItemActionPerformed

  /**
   * Exit the Application
   */
  private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
    System.exit(0);
  }//GEN-LAST:event_exitForm

  /**
   * Opens a GUI for doing Named-Entity Recognition.
   */
  public static void main(String args[]) {
    NER ner = new NER();
    ner.setVisible(true);

    Properties props = StringUtils.argsToProperties(args);
    if (props.containsKey("classifier")) {
      ner.loadClassifier(props.getProperty("classifier"));
    } else {
      ner.loadClassifierItemActionPerformed(null);
    }
    if (props.containsKey("file")) {
      ner.loadFile(props.getProperty("file"));
    }
  }


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton processButton;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel statusLabel;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JMenuItem openFileItem;
  private javax.swing.JMenuItem loadClassifierItem;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JTextPane textPane;
  private javax.swing.JMenu jMenu1;
  private javax.swing.JMenuItem exitItem;
  private javax.swing.JMenuBar jMenuBar1;
  // End of variables declaration//GEN-END:variables

}
