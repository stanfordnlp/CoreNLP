// MaxentTaggerGUI -- StanfordMaxEnt, A Maximum Entropy Toolkit
// Copyright (c) 2002-2008 Leland Stanford Junior University
//
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
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tagger.shtml
package edu.stanford.nlp.tagger.maxent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


/** A very simple GUI for illustrating the POS tagger tagging text.
 *
 * Simple usage: <br>
 * <code>java -mx300m edu.stanford.nlp.tagger.maxent.MaxentTaggerGUI pathToPOSTaggerModel</code>
 * <p>
 *  <i>Note:</i> Could still use a fair bit of work, but probably a reasonable demo as of 16 Jan 08 (Anna).
 *
 * @author Kristina Toutanova
 * @author Anna Rafferty (improvements on original gui)
 * @version 1.1
 */
public class MaxentTaggerGUI extends JFrame {

  private static final long serialVersionUID = -2574711492469740892L;

  private final JTextArea inputBox = new JTextArea();
  private final JTextArea outputBox = new JTextArea();
  private final JButton tagButton = new JButton();

  // TODO: not likely to be an issue, but this should not be static...
  private static MaxentTagger tagger;

  public MaxentTaggerGUI() {
    super("Maximum Entropy Part of Speech Tagger");
    try {
      jbInit();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Run the simple tagger GUI. Usage:<br><code>
   *  java edu.stanford.nlp.tagger.maxent.MaxentTaggerGUI [modelPath]
   *  </code><br>
   *  If you don't specify a model, the code looks for one in a couple of
   *  canonical places.
   *
   *  @param args None or a modelPath, as above
   */
  public static void main(final String[] args) {

    Thread t = new Thread() {
      @Override
      public void run() {
        String file;

        try {
          if (args.length > 0) {
            file = args[args.length - 1];
          } else {
            file = MaxentTagger.DEFAULT_DISTRIBUTION_PATH;
          }
          tagger = new MaxentTagger(file);
        } catch (Exception e) {
          try {
            file = MaxentTagger.DEFAULT_NLP_GROUP_MODEL_PATH;
            tagger = new MaxentTagger(file);
          } catch (Exception e2) {
            e.printStackTrace();
          }
        }
      }
    };
    t.start();

    MaxentTaggerGUI mainFrame1 = new MaxentTaggerGUI();
    mainFrame1.setPreferredSize(new Dimension(400, 200));
    mainFrame1.pack();
    mainFrame1.setVisible(true);
  }

  private void jbInit() {
    //pf = new PrintFile("out");
    this.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });


    //Set up the input/output fields and let them scroll.
    inputBox.setLineWrap(true);
    inputBox.setWrapStyleWord(true);
    outputBox.setLineWrap(true);
    outputBox.setWrapStyleWord(true);
    outputBox.setEditable(false);
    JScrollPane scroll1 = new JScrollPane(inputBox);
    JScrollPane scroll2 = new JScrollPane(outputBox);
    scroll1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Type a sentence to tag: "));
    scroll2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Tagged sentence: "));

    //Set up the button for starting tagging
    JPanel buttonPanel = new JPanel();
    buttonPanel.setBackground(Color.WHITE);
    buttonPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    FlowLayout fl = new FlowLayout();
    fl.setAlignment(FlowLayout.RIGHT);
    buttonPanel.setLayout(fl);
    tagButton.setText("Tag sentence!");
    tagButton.setBackground(Color.WHITE);
    buttonPanel.add(tagButton);

    tagButton.addActionListener(e -> performTagAction(e));


    //Lay it all out
    this.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx = 4.0;
    c.weighty = 4.0;

    this.add(scroll1, c);
    c.weighty = 1.0;
    this.add(buttonPanel, c);
    c.weighty = 4.0;
    c.gridheight = GridBagConstraints.REMAINDER;
    this.add(scroll2, c);
  }

  @SuppressWarnings("UnusedDeclaration")
  private void performTagAction(ActionEvent e) {
    final String s = inputBox.getText();
    Thread t = new Thread() {
      @Override
      public void run() {
        final String taggedStr = tagger.tagString(s);
        SwingUtilities.invokeLater(() -> outputBox.setText(taggedStr));
      }
    };
    t.start();
  }

}
