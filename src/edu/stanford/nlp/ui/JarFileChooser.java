package edu.stanford.nlp.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.*;

/**
 * This class shows a dialog which lets the user select a file from
 * among a list of files contained in a given jar file.  (This should
 * work for zip files as well, actually.)
 *
 * @author John Bauer
 */
public class JarFileChooser {
  String pattern;
  Frame frame;
  JPanel panel;

  public JarFileChooser(String pattern, JPanel panel) {
    this.pattern = pattern;
    this.panel = panel;
  }

  public String show(String filename, Point location) {
    File jarFile = new File(filename);
    if (!jarFile.exists()) {
      JOptionPane.showMessageDialog(panel, "Filename " + jarFile + " does not exist", null, JOptionPane.ERROR_MESSAGE);
      return null;
    }

    List<String> files;
    try {
      files = getFiles(jarFile);
    } catch (Exception e) {
      // Something went wrong reading the file.
      JOptionPane.showMessageDialog(panel, "Filename " + jarFile + " had an error:\n" + e, null, JOptionPane.ERROR_MESSAGE);
      return null;
    }
    if (files.size() == 0) {
      JOptionPane.showMessageDialog(panel, "Filename " + jarFile + " does not contain any models", null, JOptionPane.ERROR_MESSAGE);
      return null;
    }

    return showListSelectionDialog(files, location);
  }

  public String showListSelectionDialog(List<String> files, Point location) {
    Frame frame = new Frame();
    //System.out.println(location);
    //frame.setLocation(location);
    final JDialog dialog = new JDialog(frame, "Jar File Chooser", true);
    dialog.setLocation(location);
    final JList<String> fileList = new JList<>(new Vector<>(files));
    fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    MouseListener mouseListener = new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            // double clicked
            dialog.setVisible(false);
          }
        }
      };
    fileList.addMouseListener(mouseListener);

    final JScrollPane scroll = new JScrollPane(fileList);

    JButton okay = new javax.swing.JButton();
    okay.setText("Okay");
    okay.setToolTipText("Okay");
    okay.addActionListener(evt -> dialog.setVisible(false));
    JButton cancel = new javax.swing.JButton();
    cancel.setText("Cancel");
    cancel.setToolTipText("Cancel");
    cancel.addActionListener(evt -> {
      fileList.clearSelection();
      dialog.setVisible(false);
    });

    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints constraints = new GridBagConstraints();

    dialog.setLayout(gridbag);

    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    gridbag.setConstraints(scroll, constraints);
    dialog.add(scroll);

    constraints.gridwidth = GridBagConstraints.RELATIVE;
    constraints.fill = GridBagConstraints.NONE;
    constraints.weighty = 0.0;
    gridbag.setConstraints(okay, constraints);
    dialog.add(okay);

    constraints.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(cancel, constraints);
    dialog.add(cancel);
    dialog.pack();
    dialog.setSize(dialog.getPreferredSize());
    dialog.setVisible(true);

    if (fileList.isSelectionEmpty())
      return null;

    return files.get(fileList.getSelectedIndex());
  }

  public List<String> getFiles(File jarFile)
    throws ZipException, IOException
  {
    //System.out.println("Looking at " + jarFile);
    List<String> files = new ArrayList<>();

    try (ZipFile zin = new ZipFile(jarFile)) {
      Enumeration<? extends ZipEntry> entries = zin.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.matches(pattern)) {
          files.add(name);
        }
      }
      Collections.sort(files);
    }
    return files;
  }
}
