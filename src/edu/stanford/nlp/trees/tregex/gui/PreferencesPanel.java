// Tregex/Tsurgeon, PreferencesPanel - a GUI for tree search and modification
// Copyright (c) 2007-2008 The Board of Trustees of
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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// This code is a GUI interface to Tregex and Tsurgeon (which were
// written by Rogey Levy and Galen Andrew).
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tregex.shtml

package edu.stanford.nlp.trees.tregex.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import edu.stanford.nlp.trees.*;

/**
 * Class for creating the preferences panel which holds user definable preferences (e.g., tree display size,
 * highlight color) and syncs these preferences with the appropriate data structures. This class only needs to be
 * instantiated once for a given instance of the gui.
 *
 * @author Anna Rafferty
 */
@SuppressWarnings("serial")
public class PreferencesPanel extends JDialog {

  private static final String FONT_ERROR = "font";//error code if font size given is not an int > 0
  private static final String HISTORY_ERROR = "history";//error code if history size is not an int >0
  private static final String MAX_MATCH_ERROR = "maxMatch";//error code if history size is not an int >0

  private TregexGUI gui;

  final JButton highlightButton;
  private JTextField setEncoding;//declared here because may change in different places

  public PreferencesPanel(TregexGUI gui) {
    super(gui, "Preferences");

    this.gui = gui;

    this.setResizable(false);
    final JPanel prefPanel = new JPanel();
    prefPanel.setLayout(new GridBagLayout());

    //display prefs box
    Box displayPrefs = Box.createVerticalBox();
    displayPrefs.setBorder(BorderFactory.createTitledBorder("Display"));
    JPanel displayOptions = new JPanel();

    displayOptions.setLayout(new GridLayout(3,2,0,2));

    JLabel historyLabel = new JLabel("Recent matches length: ");
    final JTextField historySizeField =
      new JTextField(Integer.toString(Preferences.getHistorySize()));
    displayOptions.add(historyLabel);
    displayOptions.add(historySizeField);

    JLabel maxMatchesLabel = new JLabel("Max displayed trees: ");
    final JTextField maxMatchesSizeField
      = new JTextField(Integer.toString(Preferences.getMaxMatches()));
    displayOptions.add(maxMatchesLabel);
    displayOptions.add(maxMatchesSizeField);

    JLabel highlightLabel = new JLabel("Highlight color:");
    highlightButton = makeColorButton("Pick a new highlight color: ",
                                      Preferences.getHighlightColor(), prefPanel);
    highlightButton.putClientProperty("JButton.buttonType","icon");
    displayOptions.add(highlightLabel);
    displayOptions.add(highlightButton);
    displayPrefs.add(displayOptions);

    //tree display prefs box
    Box treeDisplayPrefs = Box.createVerticalBox();
    treeDisplayPrefs.setBorder(BorderFactory.createTitledBorder("Tree Display"));
    JPanel treeDisplayOptions = new JPanel();
    treeDisplayOptions.setLayout(new GridLayout(4,2));
    JLabel fontName = new JLabel("Font: ");
    final JComboBox fontPicker = new JComboBox(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
    fontPicker.setSelectedItem(Preferences.getFont());

    JLabel sizeLabel = new JLabel("Font size: ");
    final JTextField size =
      new JTextField(Integer.toString(Preferences.getFontSize()));

    treeDisplayOptions.add(fontName);
    treeDisplayOptions.add(fontPicker);
    treeDisplayOptions.add(sizeLabel);
    treeDisplayOptions.add(size);

    JLabel defaultColorLabel = new JLabel("Tree color: ");
    final JButton defaultColorButton = makeColorButton("Pick a new tree color: ",
                                                       Preferences.getTreeColor(), prefPanel);
    treeDisplayOptions.add(defaultColorLabel);
    treeDisplayOptions.add(defaultColorButton);

    JLabel matchedLabel = new JLabel("Matched node color: ");
    final JButton matchedButton = makeColorButton("Pick a new color for matched nodes: ",
                                                  Preferences.getMatchedColor(),
                                                  prefPanel);
    treeDisplayOptions.add(matchedLabel);
    treeDisplayOptions.add(matchedButton);

    //----add to tree display box
    treeDisplayPrefs.add(treeDisplayOptions);

    //advanced preferences - headfinder, tree reader factory
    JPanel advOptions = new JPanel();
    advOptions.setBorder(BorderFactory.createTitledBorder("Advanced "));
    advOptions.setLayout(new GridLayout(3,2,0,4));
    JLabel headfinderName = new JLabel("Head finder:");
    final JComboBox headfinderPicker = new JComboBox(new String[] {"ArabicHeadFinder", "BikelChineseHeadFinder", "ChineseHeadFinder", "ChineseSemanticHeadFinder", "CollinsHeadFinder", "DybroFrenchHeadFinder", "LeftHeadFinder", "ModCollinsHeadFinder", "NegraHeadFinder", "SemanticHeadFinder", "SunJurafskyChineseHeadFinder", "TueBaDZHeadFinder", "UniversalSemanticHeadFinder"}); //
    headfinderPicker.setEditable(true);
    headfinderPicker.setSelectedItem(Preferences.getHeadFinder()
                                     .getClass().getSimpleName());
    JLabel treeReaderFactoryName = new JLabel("Tree reader factory:");
    final JComboBox trfPicker = new JComboBox(new String[] {"ArabicTreeReaderFactory", "ArabicTreeReaderFactory.ArabicRawTreeReaderFactory", "CTBTreeReaderFactory", "Basic categories only (LabeledScoredTreeReaderFactory)", "FrenchTreeReaderFactory","NoEmptiesCTBTreeReaderFactory", "PennTreeReaderFactory", "TregexTreeReaderFactory" });
    trfPicker.setEditable(true);
    trfPicker.setSelectedItem(Preferences.getTreeReaderFactory()
                              .getClass().getSimpleName());
    JLabel encodingLabel = new JLabel("Character encoding: ");
    setEncoding = new JTextField(Preferences.getEncoding());
    setEncoding.setPreferredSize(headfinderName.getPreferredSize());
    advOptions.add(headfinderName);
    advOptions.add(headfinderPicker);
    advOptions.add(treeReaderFactoryName);
    advOptions.add(trfPicker);
    advOptions.add(encodingLabel);
    advOptions.add(setEncoding);
    //tsurgeon enabled box
    final JCheckBox tsurgeonCheck = new JCheckBox("Enable Tsurgeon");
    tsurgeonCheck.setSelected(Preferences.getEnableTsurgeon());

    //matched portions only box
    final JCheckBox matchPortion = new JCheckBox("Show only matched portions of tree");
    matchPortion.setSelected(Preferences.getMatchPortionOnly());

    //add everything
    GridBagConstraints c = new GridBagConstraints();
    c.ipady = 3;
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1;
    c.gridwidth = GridBagConstraints.REMAINDER;
    prefPanel.add(displayPrefs,c);
    prefPanel.add(treeDisplayPrefs,c);
    prefPanel.add(advOptions,c);
    prefPanel.add(tsurgeonCheck,c);
    c.gridheight = GridBagConstraints.REMAINDER;
    prefPanel.add(matchPortion,c);

    JButton[] options = new JButton[2];
    JButton okay = new JButton("Okay");


    JButton cancel = new JButton("Cancel");

    options[1] = cancel;
    options[0] = okay;


    final JOptionPane prefPane = new JOptionPane();
    prefPane.setMessage(prefPanel);

    prefPane.setOptions(options);
    prefPane.setOpaque(true);
    this.setContentPane(prefPane);
    this.getRootPane().setDefaultButton(okay);
  //--------- wire buttons
    okay.addActionListener(new ActionListener() {

      public void actionPerformed(ActionEvent arg0) {
        try {
          //check appropriate headfinder/tree reader
          HeadFinder hf = Preferences.lookupHeadFinder(headfinderPicker.getSelectedItem().toString());
          if (hf == null) {
            JOptionPane.showMessageDialog(PreferencesPanel.this, "Sorry, there was an error finding or instantiating the head finder. Please choose another head finder.", "Head Finder Error", JOptionPane.ERROR_MESSAGE);
            throw new Exception("Headfinder error");
          }

          TreeReaderFactory trf = Preferences.lookupTreeReaderFactory(trfPicker.getSelectedItem().toString());
          if (trf == null) {
            JOptionPane.showMessageDialog(PreferencesPanel.this, "Sorry, there was an error finding or instantiating the tree reader factory. Please choose another tree reader factory.", "Tree Reader Factory Error", JOptionPane.ERROR_MESSAGE);
            throw new Exception("Tree reader factory error");
          }

          //check appropriate number formats
          Integer historySize = checkNumberFormat(historySizeField, PreferencesPanel.HISTORY_ERROR);
          Integer maxMatchSize = checkNumberFormat(maxMatchesSizeField, PreferencesPanel.MAX_MATCH_ERROR);
          Integer textSize = checkNumberFormat(size, PreferencesPanel.FONT_ERROR);

          syncFromPrefPanel(fontPicker.getSelectedItem().toString(),
              textSize,
              ((ColorIcon) defaultColorButton.getIcon()).getColor(),
              ((ColorIcon) matchedButton.getIcon()).getColor(),
              ((ColorIcon) highlightButton.getIcon()).getColor(),
              historySize,
              maxMatchSize,
              tsurgeonCheck.isSelected(), matchPortion.isSelected(), hf, trf, setEncoding.getText().trim());
          PreferencesPanel.this.setVisible(false);
        } catch(NumberFormatException e) {
         //System.out.println("Error is: " + e.getMessage());
          if (e.getMessage() == PreferencesPanel.FONT_ERROR)
            JOptionPane.showMessageDialog(prefPanel, "Please enter an integer greater than 0 for the font size.", "Font size error", JOptionPane.ERROR_MESSAGE);
          else if (e.getMessage() == PreferencesPanel.HISTORY_ERROR)
            JOptionPane.showMessageDialog(prefPanel, "Please enter an integer greater than or equal to 0 for the number of recent matches to remember.", "History size error", JOptionPane.ERROR_MESSAGE);
          else if (e.getMessage() == PreferencesPanel.HISTORY_ERROR)
            JOptionPane.showMessageDialog(prefPanel, "Please enter an integer greater than or equal to 0 for the maximum number of matches to display.", "Max Matches size error", JOptionPane.ERROR_MESSAGE);
          else
            JOptionPane.showMessageDialog(prefPanel, "Please check that the font size, max matches to display, and number of recent matches to remember are all integers greater than 0.", "Size error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
          // ignore
        }

      }

    });

    cancel.addActionListener(arg0 -> PreferencesPanel.this.setVisible(false));

  }

  private static Integer checkNumberFormat(JTextField component, String errorType) throws NumberFormatException {
    Integer number = null;
    String txt = component.getText();
    if(txt!= null && !"".equals(txt)) {
      try {
        number = Integer.parseInt(txt);
        if(number <= 0)
          throw new NumberFormatException(errorType);
      } catch(NumberFormatException e) {//we catch and throw so that we catch both the one we threw and the one that could be thrown by Integer.parseInt
        throw new NumberFormatException(errorType);
      }
    }
    return number;
  }

  public static void alignLeft(JComponent box) {
    for(Component comp: box.getComponents()) {
      ((JComponent) comp).setAlignmentX(Box.LEFT_ALIGNMENT);
    }
  }

  private void syncFromPrefPanel(String font, Integer fontSize, Color treeColor, Color matchedColor, Color highlightColor,
      Integer historySize, Integer maxMatches, boolean enableTsurgeon, boolean matchPortionOnly, HeadFinder hf, TreeReaderFactory trf, String encoding) {
    Preferences.setFont(font);
    Preferences.setFontSize(fontSize == null ? 0 : fontSize);
    Preferences.setTreeColor(treeColor);
    Preferences.setMatchedColor(matchedColor);
    Preferences.setHighlightColor(highlightColor);
    Preferences.setHistorySize(historySize == null ? 0 : historySize);
    Preferences.setMaxMatches(maxMatches == null ? 0 : maxMatches);
    Preferences.setEnableTsurgeon(enableTsurgeon);
    Preferences.setMatchPortionOnly(matchPortionOnly);
    Preferences.setHeadFinder(hf);
    Preferences.setTreeReaderFactory(trf);
    Preferences.setEncoding(encoding);

    gui.loadPreferences();
  }

  void checkEncodingAndDisplay(String headFinder, String trf) {
    boolean prompt = false;
    String defaultEncoding = "";
    String curEncoding = FileTreeModel.getCurEncoding();
    if(isChinese(headFinder, trf)) {
      if(!curEncoding.equals(FileTreeModel.DEFAULT_CHINESE_ENCODING)) {
        prompt = true;
        defaultEncoding = FileTreeModel.DEFAULT_CHINESE_ENCODING;
      }
    } else if(isNegra(headFinder, trf)) {
      if(!curEncoding.equals(FileTreeModel.DEFAULT_NEGRA_ENCODING)) {
        prompt = true;
        defaultEncoding = FileTreeModel.DEFAULT_NEGRA_ENCODING;
      }
    } else if(!curEncoding.equals(FileTreeModel.DEFAULT_ENCODING)) {
      prompt = true;
      defaultEncoding = FileTreeModel.DEFAULT_ENCODING;
    }


    if(prompt) {
      doEncodingPrompt(defaultEncoding, curEncoding);
    }
  }


  private void doEncodingPrompt(final String encoding, final String oldEncoding) {

    final JPanel encodingPanel = new JPanel();
    encodingPanel.setLayout(new BoxLayout(encodingPanel, BoxLayout.PAGE_AXIS));
    JLabel text = new JLabel("<html>A head finder or tree reader was selected that has the default encoding " + encoding
        + "; this differs from " + oldEncoding + ", which was being used. If the encoding is changed, all newly loaded" +
        "treebanks will be read using the new encoding. Choosing an encoding that is not the true encoding of your tree " +
        "files may cause errors and unexpected behavior.</html>");
    //text.setBorder(BorderFactory.createLineBorder(Color.black));
    text.setAlignmentX(SwingConstants.LEADING);
    JPanel textPanel = new JPanel(new BorderLayout());
    textPanel.setPreferredSize(new Dimension(100,100));
    textPanel.add(text);
    encodingPanel.add(textPanel);
    encodingPanel.add(Box.createVerticalStrut(5));
    final JOptionPane fileFilterDialog = new JOptionPane();
    fileFilterDialog.setMessage(encodingPanel);
    JButton[] options = new JButton[3];
    JButton useNewEncoding = new JButton("Use " + encoding);
    JButton useOldEncoding = new JButton("Use " + oldEncoding);
    JButton useAnotherEncoding = new JButton("Use encoding...");

    options[0] = useNewEncoding;
    options[1] = useOldEncoding;
    options[2] = useAnotherEncoding;

    fileFilterDialog.setOptions(options);

    final JDialog dialog = fileFilterDialog.createDialog(null, "Default encoding changed...");
    useNewEncoding.addActionListener(arg0 -> {
      FileTreeModel.setCurEncoding(encoding);
      if(setEncoding == null)
        System.out.println("encoding null!!");
      setEncoding.setText(encoding);
      dialog.setVisible(false);
    });
    useOldEncoding.addActionListener(e -> dialog.setVisible(false));
    useAnotherEncoding.addActionListener(e -> {
      //need to prompt for an encoding
      dialog.setVisible(false);
      alternateEncodingPrompt(encoding);
    });
    dialog.getRootPane().setDefaultButton(useNewEncoding);
    dialog.pack();
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  /**
   * Prompts the user to enter a new encoding for loading tree files
   */
  private void alternateEncodingPrompt(String newDefaultEncoding) {
    String response = (String) JOptionPane.showInputDialog(this,"Please enter a text encoding: ", "Set Encoding...",
        JOptionPane.QUESTION_MESSAGE,null,null,newDefaultEncoding);
    FileTreeModel.setCurEncoding(response.trim());
    setEncoding.setText(response.trim());
  }

  /**
   * Checks if the given head finder or tree reader factory are for Negra (German).
   */
  static boolean isNegra(String headFinder, String trf) {
    return headFinder.startsWith("Negra");
  }

  /**
   * Checks if the given head finder or tree reader factory are for Chinese; if so, the font chosen in prefs
   * will be overridden for a Chinese compatible font
   */
  static boolean isChinese(String headFinder, String trf) {
    return headFinder.startsWith("Chinese") || headFinder.startsWith("OldChinese") || trf.equalsIgnoreCase("CTBTreeReaderFactory") || trf.equalsIgnoreCase("NoEmptiesCTBTreeReaderFactory");
  }

  /**
   * Checks if the given head finder or tree reader factory are for Arabic; if so, the font chosen in prefs
   * will be overridden for a Arabic compatible font
   */
  static boolean isArabic(String headFinder, String trf) {
    return headFinder.startsWith("Arabic") || trf.startsWith("Arabic");
  }

  /**
   * Makes a color choosing button that displays only an icon with a square of the given color
   */
  public static JButton makeColorButton(final String promptText, Color iconColor, final JPanel parent) {
    final ColorIcon icon = new ColorIcon(iconColor);
    final JButton button = new JButton(icon);
    button.addActionListener(arg0 -> {
      Color newColor = JColorChooser.showDialog(parent,promptText, icon.getColor());
      if (newColor != null) {
        icon.setColor(newColor);
        parent.repaint();
      }
    });
    return button;
  }


  private static class ColorIcon implements Icon {

    private static final int iconHeight = 8;
    private static final int iconWidth = 15;
    private Color color;

    public ColorIcon(Color c) {
      this.color = c;
    }
    public int getIconHeight() {
      return iconHeight;
    }

    public int getIconWidth() {
      return iconWidth;
    }

    public void setColor(Color c) {
      this.color = c;
    }

    public Color getColor() {
      return color;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      Color oldColor = g.getColor();
      g.setColor(color);
      g.fillRect(x, y, getIconWidth(), getIconHeight());
      g.setColor(oldColor);
    }

  } // end static class ColorIcon

}
