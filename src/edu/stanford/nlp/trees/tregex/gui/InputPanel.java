//Tregex/Tsurgeon, InputPanel - a GUI for tree search and modification
//Copyright (c) 2007-2008 The Board of Trustees of
//The Leland Stanford Junior University. All Rights Reserved.

//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//This code is a GUI interface to Tregex and Tsurgeon (which were
//written by Rogey Levy and Galen Andrew).

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tregex.shtml

package edu.stanford.nlp.trees.tregex.gui; 
import edu.stanford.nlp.util.logging.Redwood;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeVisitor;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Generics;


/**
 * Class representing the panel that gets input from the user and does (in a thread-safe manner)
 * the computation for finding tree matches and performing tsurgeon operations.  Also displays statistics.
 *
 * @author Anna Rafferty
 */
public class InputPanel extends JPanel implements ActionListener, ChangeListener  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(InputPanel.class);

  private static final long serialVersionUID = -8219840036914495876L;

  private static InputPanel inputPanel; // = null;

  private JLabel foundStats;
  private JButton findMatches;
  private JButton cancel;
  private JButton help;
  private JTextArea tregexPattern;
  private JComboBox<String> recentTregexPatterns;
  private DefaultComboBoxModel<String> recentTregexPatternsModel;
  private int numRecentPatterns = 5;// we save the last n patterns in our combo box, where n = numRecentPatterns
  private JTextArea tsurgeonScript;
  private TregexPatternCompiler compiler;//this should change only when someone changes the headfinder/basic category finder
  private List<HistoryEntry> historyList;
  private JFrame historyFrame; // = null;
  private JLabel scriptLabel;
  private boolean tsurgeonEnabled; // = false;
  private JButton tsurgeonHelp;
  private JButton cancelTsurgeon;
  private Thread searchThread;
  private JButton historyButton;
  private JProgressBar progressBar;
  private JButton browseButton;

  private JFrame helpFrame;

  private JFrame tsurgeonHelpFrame;

  private JButton runScript;

  public static synchronized InputPanel getInstance() {
    if (inputPanel == null)
      inputPanel = new InputPanel();
    return inputPanel;
  }


  private InputPanel() {
    //data stuff
    compiler = new TregexPatternCompiler();
    historyList = new ArrayList<>();

    //layout/image stuff
    this.setLayout(new GridBagLayout());
    this.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(),"Search pattern: "));

    //pattern/history area
    JPanel tregexInput = makeTregexPatternArea();

    //buttons
    JPanel buttonBox = makeTregexButtonBox();
    JPanel browseButtonBox = makeBrowseButtonBox();

    //interactive (if set in prefs) tsurgeon pattern
    Box tsurgeonBox = makeTSurgeonScriptArea();

    //tsurgeon buttons
    JPanel tsurgeonButtonBox = makeTSurgeonButtons();

    //enable/disable tsurgeon by default
    enableTsurgeonHelper(tsurgeonEnabled);//helper method used for initial call

    //statistics/status
    JPanel foundStatsBox = makeFoundStatsBox();

    //put it together
    JLabel bigEmptyLabel = new JLabel();
    //bigEmptyLabel.setBorder(BorderFactory.createEtchedBorder());

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx = 1.0;
    c.weighty = 3.0/12;
    c.gridx = 0;
    c.gridy = 0;
    this.add(tregexInput,c);

    c.weighty = 1.0/12;
    c.gridy = 1;
    this.add(buttonBox,c);

    c.weighty = 1.0/12;
    c.gridy = 2;
    this.add(browseButtonBox,c);

    c.weighty = 0.5/12;
    c.gridy = 3;
    this.add(bigEmptyLabel,c);

    c.weighty = 3.0/12;
    c.gridy = 4;
    c.anchor = GridBagConstraints.SOUTH;
    this.add(tsurgeonBox,c);

    c.weighty = 1.0/12;
    c.gridy = 5;
    //c.gridheight = GridBagConstraints.REMAINDER;
    c.anchor = GridBagConstraints.SOUTH;
    this.add(tsurgeonButtonBox,c);

    c.gridheight = GridBagConstraints.REMAINDER;
    c.weighty = 1.0/12;
    c.gridy = 6;
    this.add(foundStatsBox, c);
  }


  //separated out to make constructor more readable
  private JPanel makeFoundStatsBox() {
    JPanel foundStatsBox = new JPanel();
    foundStatsBox.setLayout(new GridBagLayout());
    Box labelBox = Box.createHorizontalBox();
    foundStats = new JLabel(" ");
    labelBox.add(foundStats);
    historyButton = new JButton("Statistics");
    historyButton.setEnabled(false);
    historyButton.addActionListener(this);

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.7;
    foundStatsBox.add(labelBox,c);
    c.weightx = .3;
    c.gridwidth = 1;
    foundStatsBox.add(historyButton);
    return foundStatsBox;

  }

  //separated out to make constructor more readable
  private JPanel makeTSurgeonButtons() {
    JPanel tsurgeonButtonBox = new JPanel();
    tsurgeonButtonBox.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    tsurgeonButtonBox.setLayout(new GridBagLayout());
    tsurgeonHelp = new JButton("Help");
    tsurgeonHelp.addActionListener(this);
    cancelTsurgeon = new JButton("Cancel");
    cancelTsurgeon.addActionListener(this);
    runScript = new JButton("Run script");
    runScript.addActionListener(this);

    //make constraints and add in
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.NORTHEAST;
    c.fill = GridBagConstraints.HORIZONTAL;
    tsurgeonButtonBox.add(runScript,c);
    tsurgeonButtonBox.add(cancelTsurgeon,c);
    tsurgeonButtonBox.add(tsurgeonHelp,c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx = 1.0;
    c.weighty = 1.0;
    tsurgeonButtonBox.add(new JLabel(), c);


    return tsurgeonButtonBox;
  }
  //separated out to make constructor more readable
  private Box makeTSurgeonScriptArea() {
    Box tsurgeonBox = Box.createHorizontalBox();
    scriptLabel = new JLabel("Tsurgeon script: ");
    tsurgeonScript = new JTextArea();
    tsurgeonScript.setBorder(BorderFactory.createEmptyBorder());
    tsurgeonScript.setFocusTraversalKeysEnabled(true);
    JScrollPane scriptScroller = new JScrollPane(tsurgeonScript);
    scriptScroller.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));

    //scriptScroller.setEnabled(tsurgeonEnabled);
    tsurgeonBox.add(scriptLabel);
    tsurgeonBox.add(scriptScroller);
    //tsurgeonBox.setBorder(BorderFactory.createEtchedBorder());
    return tsurgeonBox;
  }

  //separated out to make constructor more readable
  private JPanel makeTregexPatternArea() {
    //combo box with recent searches
    recentTregexPatternsModel = new DefaultComboBoxModel<>();
    recentTregexPatterns = new JComboBox<>(recentTregexPatternsModel);
    recentTregexPatterns.setMinimumSize(new Dimension(120, 24));
    recentTregexPatterns.addActionListener(this);

    JLabel recentLabel = new JLabel("Recent: ");
    //interactive tregex pattern
    JLabel patternLabel = new JLabel("Pattern: ");
    tregexPattern = new JTextArea();
    tregexPattern.setFocusTraversalKeysEnabled(true);
    tregexPattern.setLineWrap(true);

    JScrollPane patternScroller = new JScrollPane(tregexPattern);
    patternScroller.setBorder(BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));

    //add them into the panel
    JPanel tregexInput = new JPanel();
    tregexInput.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.gridx = 0;
    c.gridy = 0;
    tregexInput.add(recentLabel, c);
    c.weightx = 12.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridx = 1;
    tregexInput.add(recentTregexPatterns, c);
    c.weightx = 1.0;
    c.gridwidth = 1; //reset to default
    c.gridheight = GridBagConstraints.REMAINDER;
    c.gridx = 0;
    c.gridy = 1;
    tregexInput.add(patternLabel,c);
    c.gridx = 1;
    c.weightx = 12.0;
    c.weighty = 2.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    tregexInput.add(patternScroller,c);
    //tregexInput.setBorder(BorderFactory.createEtchedBorder());

    return tregexInput;
  }

  //separated out to make constructor more readable
  private JPanel makeTregexButtonBox() {
    JPanel buttonBox = new JPanel();
    buttonBox.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    buttonBox.setLayout(new GridBagLayout());
    //buttonBox.setBorder(BorderFactory.createEtchedBorder());
    help = new JButton("Help");
    help.addActionListener(this);
    cancel = new JButton("Cancel");
    cancel.setEnabled(false);
    cancel.addActionListener(this);
    findMatches = new JButton("Search");
    findMatches.addActionListener(this);
//  browseButton = new JButton("Browse Trees");
//  browseButton.addActionListener(this);
    JLabel emptyLabel = new JLabel();
   // JLabel emptyLabel2 = new JLabel();

    GridBagConstraints buttonConstraints = new GridBagConstraints();
    buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
    buttonBox.add(findMatches,buttonConstraints);
    buttonBox.add(cancel,buttonConstraints);
    buttonBox.add(help,buttonConstraints);

    buttonConstraints.gridwidth = GridBagConstraints.REMAINDER;
    buttonConstraints.weightx = 1.0;
    buttonConstraints.weighty = 1.0;
    buttonBox.add(emptyLabel, buttonConstraints);
//  buttonConstraints.gridwidth = 1;
//  buttonBox.add(browseButton, buttonConstraints);
//  buttonConstraints.gridwidth = GridBagConstraints.REMAINDER;
//  buttonBox.add(emptyLabel2, buttonConstraints);

    return buttonBox;
  }

  private JPanel makeBrowseButtonBox() {
    JPanel buttonBox = new JPanel();
    buttonBox.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    buttonBox.setLayout(new GridBagLayout());
    browseButton = new JButton("Browse Trees");
    browseButton.addActionListener(this);
    JLabel sizeLabel = new JLabel("Tree size:");
    JSlider fontSlider = new JSlider(2, 64, 12);
    fontSlider.addChangeListener(this);
    GridBagConstraints buttonConstraints = new GridBagConstraints();
    buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
    buttonConstraints.weightx = 0.2;
    buttonConstraints.weighty = 0.2;
    buttonBox.add(browseButton,buttonConstraints);
    buttonConstraints.weightx = 0.6;
    buttonBox.add(fontSlider, buttonConstraints);
    buttonConstraints.weightx = 0.2;
    buttonBox.add(sizeLabel, buttonConstraints);
    return buttonBox;
  }


  public void enableTsurgeon(boolean enable) {
    if(tsurgeonEnabled == enable)
      return;//nothing changes
    enableTsurgeonHelper(enable);
  }

  //Doesn't check if tsurgeon is already in this enable state - used by enableTsurgeon and for
  //initially enabling/disabling tsurgeon.
  private void enableTsurgeonHelper(boolean enable) {
    scriptLabel.setEnabled(enable);
    tsurgeonScript.setEnabled(enable);
    tsurgeonHelp.setEnabled(enable);
    cancelTsurgeon.setEnabled(false);//should always be off unless we're running a script
    runScript.setEnabled(enable);
    tsurgeonEnabled = enable;
    TregexGUI.getInstance().setTsurgeonEnabled(enable);
  }

  public List<HistoryEntry> getHistoryList() {
    return historyList;
  }

  public String getHistoryString() {
    StringBuilder sb = new StringBuilder();
    sb.append(HistoryEntry.header());
    sb.append('\n');
    for(HistoryEntry e : this.getHistoryList()) {
      sb.append(e.toString());
      sb.append('\n');
    }
    return sb.toString();
  }

  private void addToHistoryList(String pattern, int numTreesMatched, int numMatches) {
    if(!historyButton.isEnabled()) {
      historyButton.setEnabled(true);
      TregexGUI.getInstance().setSaveHistoryEnabled(true);
    }
    historyList.add(new HistoryEntry(pattern, numTreesMatched, numMatches));
  }

  public void setHeadFinder(HeadFinder hf) {
    compiler = new TregexPatternCompiler(hf);
  }

  /**
   * Updates the number of unique trees matched and the number of total trees matched by the last
   * pattern that was searched for.  Thread-safe.
   *
   * @param pattern The pattern
   * @param treeMatches count of unique trees matched by the pattern
   * @param totalMatches count of total matching instances
   */
  public void updateFoundStats(final String pattern, final int treeMatches, final int totalMatches) {
    final String txt = "<html>Match stats: " + treeMatches + " unique trees found with " + totalMatches + " total matches.</html>";
    SwingUtilities.invokeLater(() -> {
      foundStats.setPreferredSize(foundStats.getSize());
      foundStats.setText(txt);
      if(pattern != null)
        addToHistoryList(pattern, treeMatches, totalMatches);
    });
  }

  /**
   * Updates the number of total trees found in the selected tree files.
   *
   * @param treeMatches count of trees in the selected files
   */
  public void updateBrowseStats(final int treeMatches) {
    final String txt = "<html>Browse stats: " + treeMatches + " trees found in the selected files</html>";
    SwingUtilities.invokeLater(() -> {
      foundStats.setPreferredSize(foundStats.getSize());
      foundStats.setText(txt);
      });
  }

  public void useProgressBar(boolean useProgressBar) {
    if (useProgressBar) {//make sure we're in progress bar mode
      if (progressBar == null) {
        progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
        Container dad = foundStats.getParent();
        useProgressBarHelper(dad, progressBar, foundStats);
      }
    } else {//make sure we're in found stats mode
      if (progressBar != null) {
        Container dad = progressBar.getParent();
        useProgressBarHelper(dad, foundStats, progressBar);
        progressBar = null;
      }

    }
  }

  private static void useProgressBarHelper(Container parent, JComponent add, JComponent remove) {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.7;
    parent.remove(remove);
    parent.add(add,c);
    parent.validate();
    parent.repaint();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    if (source == findMatches) {
      runSearch();
    } else if (source == cancel) {
      if (searchThread != null) {
        searchThread.interrupt();
      }
      cancel.setEnabled(false);
    } else if (source == help) {
      displayHelp();
    } else if (source == tsurgeonHelp) {
      displayTsurgeonHelp();
    } else if (source == cancelTsurgeon) {
      if (searchThread != null) {
        searchThread.interrupt();
      }
      cancelTsurgeon.setEnabled(false);
    } else if (source == runScript) {
      runScript();
    } else if (source == recentTregexPatterns) {
      doRecent();
    } else if (source == historyButton) {
      showHistory();
    } else if (source == browseButton) {
      runBrowse();
    }
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    JSlider source = (JSlider) e.getSource();
    int fontSize = source.getValue();
    if ( ! source.getValueIsAdjusting()) {
      DisplayMatchesPanel.getInstance().setFontSizeRepaint(fontSize);
    }
  }

  private static class TregexGUITableModel extends DefaultTableModel {

    private static final long serialVersionUID = -8095682087502853273L;

    TregexGUITableModel(Object[][] entries, String[] columnNames) {
      super(entries, columnNames);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

  }


  private void showHistory() {
    if (historyFrame == null) {
      historyFrame = new JFrame("Statistics History");
    } else {
      historyFrame.setVisible(false);
      historyFrame= new JFrame("Statistics History");
    }
    historyFrame.setLayout(new GridLayout(1,0));
    Object[][] entries = new Object[historyList.size()][3];
    for(int i = 0; i < historyList.size(); i++) {
      entries[i] = historyList.get(i).toArray();
    }
    DefaultTableModel tableModel = new TregexGUITableModel(entries, HistoryEntry.columnNamesArray());
    JTable statTable = new JTable(tableModel);
    DefaultTableCellRenderer dtcr = (DefaultTableCellRenderer) statTable.getDefaultRenderer(String.class);
    dtcr.setHorizontalAlignment(DefaultTableCellRenderer.CENTER);

    JScrollPane scroller = new JScrollPane(statTable);

    historyFrame.add(scroller);
    historyFrame.pack();
    historyFrame.setLocationRelativeTo(TregexGUI.getInstance());
    historyFrame.setBackground(Color.WHITE);
    historyFrame.setVisible(true);
    historyFrame.repaint();

  }

  private void doRecent() {
    //this is called only when a user does something
    Object recent = recentTregexPatternsModel.getSelectedItem();
    if (recent != null) {
      String selected = recent.toString();
      if (selected.length() != 0) {
        tregexPattern.setText(selected);
      }
    }
  }

  private void runBrowse() {
    this.setPreferredSize(this.getSize());
    Thread browseThread = new Thread() {
      @Override
      public void run() {
        useProgressBar(true);

        try {
          final List<TreeFromFile> trees = new ArrayList<>();

          //Go through the treebanks and get all the trees
          List<FileTreeNode> treebanks = FilePanel.getInstance().getActiveTreebanks();

          //Tdiff
          if(TregexGUI.getInstance().isTdiffEnabled())
            treebanks.remove(0); //Remove the reference

          double multiplier = 100.0/treebanks.size();
          for (int i = 0; i < treebanks.size(); i++) {
            FileTreeNode treebank = treebanks.get(i);
            String filename = treebank.getFilename();
            for (Tree curTree : treebank.getTreebank()) {
              if (curTree == null) {
                // some of the treebanks have empty trees,
                // such as some versions of ontonotes
                continue;
              }
              trees.add(new TreeFromFile(curTree, filename));
            }
            updateProgressBar(multiplier*(i+1));
          }
          updateBrowseStats(trees.size());
          SwingUtilities.invokeLater(() -> {
              MatchesPanel.getInstance().setMatches(trees, null);
              MatchesPanel.getInstance().focusOnList();
              useProgressBar(false);
            });//end SwingUtilities.invokeLater
        } catch (Exception e) {
          doError("Sorry, but something went wrong.  Please post on https://github.com/stanfordnlp/CoreNLP if you think you have found a bug.", e);
        }
      } //end run
    };//end Thread

    browseThread.start();
  }


  void runSearch() {
    setTregexState(true);
    MatchesPanel.getInstance().removeAllMatches();
    this.setPreferredSize(this.getSize());
    searchThread = new Thread() {
      @Override
      public void run() {
        final String text = tregexPattern.getText().intern();
        SwingUtilities.invokeLater(() -> {
          InputPanel.this.addRecentTregexPattern(text);
          useProgressBar(true);
        });
        final TRegexGUITreeVisitor visitor = getMatchTreeVisitor(text,this);
        if (visitor != null) {

          SwingUtilities.invokeLater(() -> {
            useProgressBar(false);
            updateFoundStats(text, visitor.getMatches().size(), visitor.numUniqueMatches());
            //addToHistoryList(text, visitor.getMatches().size(), visitor.numUniqueMatches());
            MatchesPanel.getInstance().setMatches(visitor.getMatches(), visitor.getMatchedParts());
            MatchesPanel.getInstance().focusOnList();
          });
        }
        SwingUtilities.invokeLater(() -> {
          setTregexState(false);
          InputPanel.this.searchThread = null;
        });

      }
    };
    searchThread.start();

  }

  private void setTregexState(boolean running) {
    cancel.setEnabled(running);
    findMatches.setEnabled(!running);
    browseButton.setEnabled(!running);
  }

  //Assumes that it will be called only if Tsurgeon is already enabled
  private void setTsurgeonState(boolean running) {
    cancelTsurgeon.setEnabled(running);
    runScript.setEnabled(!running);
    findMatches.setEnabled(!running);
    browseButton.setEnabled(!running);
  }

  private void runScript() {
    setTsurgeonState(true);
    final String script = tsurgeonScript.getText();

    searchThread = new Thread() {
      @Override
      public void run() {
        try {
          BufferedReader reader = new BufferedReader(new StringReader(script));
          TsurgeonPattern operation = Tsurgeon.getTsurgeonOperationsFromReader(reader);

          final String text = tregexPattern.getText().intern();
          SwingUtilities.invokeLater(() -> {
            InputPanel.this.addRecentTregexPattern(text);
            useProgressBar(true);
          });
          final TRegexGUITreeVisitor visitor = getMatchTreeVisitor(text,this);
          if (visitor == null) return; //means the tregex errored out
          if (this.isInterrupted()) {
            returnToValidState(text, visitor, new ArrayList<>());
            return;
          }
          //log.info("Running Script with matches: " + visitor.getMatches());
          List<TreeFromFile> trees = visitor.getMatches();
          final List<TreeFromFile> modifiedTrees = new ArrayList<>();
          for (TreeFromFile tff : trees) {
            if (this.isInterrupted()) {
              returnToValidState(text, visitor, trees);
              return;
            }
            Tree modifiedTree = Tsurgeon.processPattern(visitor.getPattern(), operation, tff.getTree());
            modifiedTrees.add(new TreeFromFile(modifiedTree,tff.getFilename().intern()));
          }
          returnToValidState(text, visitor, modifiedTrees);
        } catch (Exception e) {
          doError("Sorry, there was an error compiling or running the Tsurgeon script.  Please press Help if you need assistance.", e);
          SwingUtilities.invokeLater(() -> {
            setTregexState(false);
            setTsurgeonState(false);
            InputPanel.this.searchThread = null;
          });
        }
      }
    };
    searchThread.start();
  }

  private void returnToValidState(final String pattern, final TRegexGUITreeVisitor visitor, final List<TreeFromFile> trees) {
    SwingUtilities.invokeLater(() -> {
      int numUniqueMatches = 0;
      if (trees.size() > 0) {
        numUniqueMatches = visitor.numUniqueMatches();
      }
      updateFoundStats(pattern, trees.size(), numUniqueMatches);
      MatchesPanel.getInstance().setMatches(trees, visitor.getMatchedParts());
      useProgressBar(false);
      setTsurgeonState(false);
    });
  }

  public void setScriptAndPattern(String tregexPatternString, String tsurgeonScriptString) {
    this.tregexPattern.setText(tregexPatternString);
    this.tsurgeonScript.setText(tsurgeonScriptString);
  }

  private void addRecentTregexPattern(String pattern) {
    // If pattern already exists, just move it to the top of the list
    int existingIndex = recentTregexPatternsModel.getIndexOf(pattern);
    if (existingIndex != -1) {
      recentTregexPatternsModel.removeElementAt(existingIndex);
      recentTregexPatternsModel.insertElementAt(pattern, 0);
      recentTregexPatterns.setSelectedIndex(0);

      return;
    }

    if(recentTregexPatternsModel.getSize() >= numRecentPatterns) {
      recentTregexPatternsModel.removeElementAt(numRecentPatterns - 1);
    }
    recentTregexPatternsModel.insertElementAt(pattern,0);
    recentTregexPatterns.setSelectedIndex(0);
    recentTregexPatterns.revalidate();
  }

  public void setNumRecentPatterns(int n) {
    numRecentPatterns = n;
    //shrink down the number of recent patterns if necessary
    while(recentTregexPatternsModel.getSize() > n) {
      int lastIndex = recentTregexPatternsModel.getSize() - 1;
      recentTregexPatternsModel.removeElementAt(lastIndex);
    }
  }


  /**
   * Check all active treebanks to find the trees that match the given pattern when interpreted
   * as a tregex pattern.
   *
   * @param patternString string version of the tregex pattern you wish to match
   * @param t The thread we are running on
   * @return tree visitor that contains the trees that were matched as well as the parts of those trees that matched
   */
  private TRegexGUITreeVisitor getMatchTreeVisitor(String patternString, Thread t) {
    TRegexGUITreeVisitor vis = null;
    try {
      TregexPattern pattern = compiler.compile(patternString);
      vis = new TRegexGUITreeVisitor(pattern); //handles);
      List<FileTreeNode> treebanks = FilePanel.getInstance().getActiveTreebanks();
      double multiplier = 100.0/treebanks.size();
      int treebankNum = 1;
      for (FileTreeNode treebank : treebanks) {
        if (t.isInterrupted()) { //get out as quickly as possible if interrupted
          t.interrupt();
          // cdm 2008: I added here resetting the buttons or else it didn't seem to happen; not quite sure this is the right place to do it but.
          SwingUtilities.invokeLater(() -> {
            setTregexState(false);
            InputPanel.this.searchThread = null;
          });
          return vis;
        }
        vis.setFilename(treebank.getFilename().intern());
        treebank.getTreebank().apply(vis);
        updateProgressBar(multiplier*treebankNum++);
      }
    } catch (OutOfMemoryError oome) {
      vis = null;
      doError("Sorry, search aborted as out of memory.\nTry either running Tregex with more memory or sticking to searches that don't produce thousands of matches.", oome);
    } catch (Exception e) {
      doError("Sorry, there was an error compiling or running the Tregex pattern.  Please press Help if you need assistance.", e);
    }
    return vis;
  }


  /**
   * Called when a pattern cannot be compiled or some other error occurs; resets gui to valid state.
   * Thread safe.
   *
   * @param txt Error message text (friendly text appropriate for users)
   * @param e The exception that caused the problem
   */
  public void doError(final String txt, final Throwable e) {
    SwingUtilities.invokeLater(() -> {
      String extraData = e.getLocalizedMessage() != null ? e.getLocalizedMessage(): (e.getClass() != null) ? e.getClass().toString(): "";
      JOptionPane.showMessageDialog(InputPanel.this, txt + '\n' + extraData, "Tregex Error", JOptionPane.ERROR_MESSAGE);
      e.printStackTrace(); // send to stderr for debugging
      useProgressBar(false);
      updateFoundStats(null, 0, 0);
    });
  }

  /**
   * Thread safe way to update how much progress we've made on the search so far
   * @param progress Percentage of the way through that we are.
   */
  public void updateProgressBar(final double progress) {
    if(progressBar == null)
      return;
    SwingUtilities.invokeLater(() -> progressBar.setValue((int) progress));
  }


  /**
   * Simple class for storing history objects that go nicely into a toString for saving
   * @author rafferty
   *
   */
  public static class HistoryEntry {

    private final String pattern;
    private final int numTreesMatched;
    private final int numMatches;

    public HistoryEntry(String pattern, int numTreesMatched, int numMatches) {
      this.pattern = pattern;
      this.numTreesMatched = numTreesMatched;
      this.numMatches = numMatches;
    }

    /**
     * Returns an array with pattern as the first entry, numTreesMatched as the second, and numMatches as the third
     * @return An array with pattern as the first entry, numTreesMatched as the second, and numMatches as the third
     */
    public Object[] toArray() {
      Object[] array = new Object[3];
      array[0] = pattern;
      array[1] = numTreesMatched;
      array[2] = numMatches;
      return array;
    }

    public static String[] columnNamesArray() {
      String[] names = {"Pattern","Trees Matched", "Total Matches"};
      return names;
    }

    @Override
    public String toString() {
      return pattern + '\t' + numTreesMatched + '\t' + numMatches + '\t';
    }

    /**
     * Gets the string header indicating the fields included in a HistoryEntry string representation
     * @return A String header
     */
    public static String header() {
      return "pattern\tnumTreesMatched\tnumMatches";
    }

  } // end class HistoryEntry


  public static class TRegexGUITreeVisitor implements TreeVisitor {

    private int totalMatches; // = 0;
    private final TregexPattern p;
    private final List<TreeFromFile> matchedTrees;
    private final Map<TreeFromFile,List<Tree>> matchedParts;
    private String filename = "";


    TRegexGUITreeVisitor(TregexPattern p) { //String[] handles) {
      this.p = p;
      //this.handles = handles;
      matchedTrees = new ArrayList<>();
      matchedParts = Generics.newHashMap();
    }

    public Map<TreeFromFile,List<Tree>> getMatchedParts() {
      return matchedParts;
    }

    public void visitTree(Tree t) {
      int numMatches = 0;
      TregexMatcher match = p.matcher(t);
      List<Tree> matchedPartList = null; // initialize lazily, since usually most trees don't match!
      while (match.find()) {
        Tree curMatch = match.getMatch();
        //System.out.println("Found match is: " + curMatch);
        if (matchedPartList == null) matchedPartList = new ArrayList<>();
        matchedPartList.add(curMatch);
        numMatches++;
      } // end while match.find()
      if(numMatches > 0) {
        TreeFromFile tff = new TreeFromFile(t, filename);
        matchedTrees.add(tff);
        matchedParts.put(tff,matchedPartList);
        totalMatches += numMatches;
      }
    } // end visitTree

    /**
     * Method for returning the number of matches found in the last tree
     * visited by this tree visitor.
     * @return number of matches found in previous tree
     */
    public int numUniqueMatches() {
      return totalMatches;
    }

    public List<TreeFromFile> getMatches() {
      return matchedTrees;
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String curFilename) {
      this.filename = curFilename.intern();
    }

    public TregexPattern getPattern() {
      return p;
    }

  } // end class TRegexTreeVisitor


  private void displayHelp() {
    if (helpFrame != null) {
      helpFrame.setVisible(true);
    } else {
      helpFrame = new JFrame("Tregex Help...");
      //JPanel helpPanel = new JPanel();
      JEditorPane helpText = new JEditorPane();
      helpText.setContentType("text/html");
      // StringBuilder s = new StringBuilder();
      // s.append(htmlHelp);
      helpText.setText(htmlHelp);
      helpText.setEditable(false);
      //helpPanel.add(helpText);

      JScrollPane scroller = new JScrollPane(helpText);
      helpText.setCaretPosition(0);
      scroller.setPreferredSize(new Dimension(500,500));
      helpFrame.add(scroller);
      helpFrame.pack();
      helpFrame.setBackground(Color.WHITE);
      helpFrame.setVisible(true);
      //helpFrame.repaint();
    }
  }

  private void displayTsurgeonHelp() {
    if(tsurgeonHelpFrame != null) {
      tsurgeonHelpFrame.setVisible(true);
    } else {
      tsurgeonHelpFrame = new JFrame("TSurgeon Help...");
      JEditorPane helpText = new JEditorPane();
      helpText.setContentType("text/html");
      // StringBuilder s = new StringBuilder();
      // s.append(htmlTsurgeonHelp);
      helpText.setText(htmlTsurgeonHelp);
      helpText.setEditable(false);

      JScrollPane scroller = new JScrollPane(helpText);
      helpText.setCaretPosition(0);
      scroller.setPreferredSize(new Dimension(500,500));
      tsurgeonHelpFrame.add(scroller);
      tsurgeonHelpFrame.pack();
      tsurgeonHelpFrame.setBackground(Color.WHITE);
      tsurgeonHelpFrame.setVisible(true);
    }
  }

  //help text for tsurgeon is basically just the javadoc main comment for tsurgeon
  private static final String htmlTsurgeonHelp = ( "<html><body>" +
  "<h1>Tsurgeon Script Syntax and Uses</h1><p>Tsurgeon is a tool that can modify the trees found by Tregex searches. " +
  " These modifications are specified via a script language, and the script language allows one to, among other" +
  " operations, rename, delete, move, and insert nodes in a tree.  " +
  " For example, if you want to excise an SBARQ node whenever it is the parent of an SQ node, and rename the SQ node " +
  " to S, your input would look like this:" +
  "<p>" +
  "Tregex pattern: " +
  " <blockquote>" +
  " <code>" +
  "    SBARQ=n1 &lt; SQ=n2<br></code>" +
  "    <br>" +
  " </blockquote>" +
  "TSurgeon script: " +
  " <blockquote>" +
  "<code>" +
  "    excise n1 n1 <br>" +
  "    relabel n2 S" +
  " </code>" +
  " </blockquote>" +
  " <h2>Legal operation syntax:</h2>" +
  " <dl>" +
  " <dt><code>delete &#60;name&#62;+</code></dt>  <dd>Deletes one or more nodes and everything below them, with names that were given in the TregexPattern (e.g., VP=x).</dd>" +
  " <dt><code>prune &#60;name&#62;+</code></dt>  <dd>Like delete, but if, after the pruning, the parent has no children anymore, the parent is pruned too.</dd>" +
  " <dt><code>excise &#60;name1&#62; &#60;name2&#62;</code></dt>" +
  "   <dd>The name1 node should either dominate or be the same as the name2 node.  This excises out everything from" +
  " name1 to name2 inclusive.  All the children of name2 go into the parent of name1, where name1 was.</dd>" +
  " <dt><code>relabel &#60;name&#62; &#60;new-label&#62;</code></dt> <dd>Relabels the node to have the new label." +
  "    There are three possible forms: <code>relabel nodeX VP</code> - for changing a node label to an alphanumeric string, " +
  "    <code>relabel nodeX /''/</code> - for relabeling a node to something that isn't a valid identifier without quoting, and " +
  "    <code>relabel nodeX /^VB(.*)$/verb\\/$1/</code> - for regular expression based relabeling. In the last case, all matches " +
  "    of the regular expression against the node label are replaced with the replacement String.  This has the semantics of" +
  "    Java/Perl's replaceAll: you may use capturing groups and put them in replacements with $n. Also, as in the example, " +
  "    you can escape a slash in the middle of the second and third forms with \\/ and \\\\.</dd>" +
  " <dt><code>insert &#60;name&#62; &#60;position&#62;</code></dt>" +
  " <dt><code>insert &lt;tree&gt; &#60;position&#62;</code></dt><dd>Inserts the named node or tree into the position specified. (giving a tree rather than a node is also valid)</dd>" +
  " <dt><code>move &#60;name&#62; &#60;position&#62;</code></dt> <dd>moves the named node into the specified position</dd>" +
  " <dd><dl><dt>Right now the  only ways to specify position are:</dt> " ) +

  "      <dd><code>$+ &#60;name&#62;</code>     the left sister of the named node</dd>" +
  "      <dd><code>$- &#60;name&#62;</code>     the right sister of the named node</dd>" +
  "      <dd><code>&gt;i</code> the <i>i</i><sup>th</sup> daughter of the named node</dd>" +
  "      <dd><code>&gt;-i</code> the <i>i</i><sup>th</sup> daughter, counting from the right, of the named node.</dd></dl></dd>" +
  " <dt><code>replace &#60;name1&#62; &#60;tree&#62;</code></dt>" +
  " <dt><code>replace &#60;name1&#62; &#60;name2&#62;</code></dt> <dd>deletes name1 and inserts a tree or a copy of name2 in its place.</dd>" +
  " <dt><code>createSubtree &#60;auxiliary-tree-or-label&#62; &#60;name1&#62; [&#60;name2&#62;]</code></dt>  <dd>Create a subtree out of all the nodes from <code>&#60;name1&#62;</code> through <code>&#60;name2&#62;</code>.The subtree is moved to the foot of the given auxiliary tree, and the tree is inserted where the nodes of the subtree used to reside. If a simple label is provided as the first argument, the subtree is given a single parent with a name corresponding to the label. To limit the operation to just one node, elide <code>&#60;name2&#62;</code>.</dd>" +
  " <dt><code>adjoin &#60;auxiliary_tree&#62; &lt;name&gt;</code></dt> <dd>Adjoins the specified auxiliary tree into the named node.  The daughters of the target node will become the daughters of the foot of the auxiliary tree.  (The node <code>name</code> is no longer accessible.)</dd>" +
  " <dt><code>adjoinH &#60;auxiliary_tree&#62; &lt;name&gt;</code></dt> <dd>Similar to adjoin, but preserves the target node and makes it the root of &lt;tree&gt;. (It is still accessible as <code>name</code>.  The root of the auxiliary tree is ignored.)</dd>" +
  " <dt><code>adjoinF &#60;auxiliary_tree&#62; &lt;name&gt;</code></dt> <dd> Similar to adjoin, but preserves the target node and makes it the foot of &lt;tree&gt;." +
  "  (It is still accessible as <code>name</code>, and retains its status as parent of its children. The foot of the auxiliary tree is ignored.)</dd>" +
  " <dt><code>coindex &#60;name1&#62; &#60;name2&#62; ... &#60;nameM&#62; </code></dt>  <dd>Puts a (Penn Treebank style) coindexation suffix of the form \"-N\" on" +
  "  each of nodes name_1 through name_m.  The value of N will be "+
  "  automatically generated in reference to the existing coindexations " +
  "  in the tree, so that there is never an accidental clash of " +
  "  indices across things that are not meant to be coindexed.</dd>"+
  " </dl> " +
  "<h2>Syntax for trees to be inserted or adjoined:</h2>" +
  "A tree to be adjoined in can be specified with LISP-like " +
  "parenthetical-bracketing tree syntax such as those used for the Penn " +
  "Treebank.  For example, for the NP \"the dog\" to be inserted you might " +
  "use the syntax" +
  "<br>" +
  " <blockquote>" +
  " <code>" +
  "(NP (Det the) (N dog))" +
  " </code>" +
  "</blockquote>" +
  "<br>" +
  "That's all that is necessary for a tree to be inserted.  Auxiliary trees " +
  "(a la Tree Adjoining Grammar) must also have exactly one frontier node " +
  "ending in the character \"@\", which marks it as the \"foot\" node for " +
  "adjunction.  Final instances of the character \"@\" in terminal node labels " +
  "will be removed from the actual label of the tree. " +
  "<p>" +
  "For example, if you wanted to adjoin the adverb \"breathlessly\" into a " +
  "VP, you might specify the following auxiliary tree: " +
  "<br>" +
  " <blockquote>" +
  " <code>" +
  "(VP (Adv breathlessly VP@ )" +
  " </code>" +
  "</blockquote>" +
  "<br>" +
  "All other instances of \"@\" in terminal nodes must be escaped (i.e., " +
  "appear as \\@); this escaping will be removed by tsurgeon. " +
  "<p>" +
  "In addition, any node of a tree can be named (the same way as in " +
  "tregex), by appending =<name> to the node label.  That name can be " +
  "referred to by subsequent Tsurgeon operations triggered by the same " +
  "match.  All other instances of \"=\" in node labels must be escaped " +
  "(i.e., appear as \\=); this escaping will be removed by Tsurgeon.  For " +
  "example, if you want to insert an NP trace somewhere and coindex it " +
  "with a node named \"antecedent\" you might say: " +
  "<br>" +
  " <blockquote>" +
  " <code>" +
  "insert (NP (-NONE- *T*=trace)) <node-location>" +
  "<br>"+
  "coindex trace antecedent $ " +
  " </blockquote>" +
  " </code>" +
  "<h2>Tips and Tricks</h2>" +
  "<ol>" +
  "<li>Be aware of how insert and replace work: Both of these operations look for matches again " +
  "after being applied.  This can cause infinite loops if your Tregex pattern continues to match the same " +
  "configuration regardless of how many times you insert your tree.  To avoid this, write Tregex patterns " +
  "that match before but not after an insert or move is applied.  For example, you could specify that the node " +
  "not have an NP right sister in the Tregex match if your insert involved inserting an NP as the right sister " +
  "of the node.</li>" +
  "<li>Use the cancel button when necessary: If you do end up writing an operation that might have cycles and " +
  "the Tregex GUI seems to be stuck in an infinite loop, press Cancel to stop the operation and recheck your Tregex pattern " +
  "and Tsurgeon commands.</li>" +
  "<li>Read the README: Three README files were included with this package - README-tregex.txt, README-tsurgeon.txt, " +
  "and README-gui.txt.  The Tsurgeon README includes examples of each operation as well as notes on the specifics of " +
  "writing each type of operation.</li>" +
  "</ol></body></html>";


  //help text is basically just the javadoc main comment for TregexPattern
  private static final String htmlHelp = ( "<html><h1>Tregex Pattern Syntax and Uses</h1><p>" +
  " Tregex is a program for finding syntactic trees of interest in a collection of parsed sentences " +
  " (a treebank).   For matching, it uses a pattern language for specifying partial syntactic tree configurations. " +
  " For example, the pattern <code>VP &lt; VBD</code> matches verb phrases headed" +
  " by a past tense verb. " +
  " Using a Tregex pattern, you can find only those trees that match the pattern you're " +
  " looking for. To get started, you first use the <code>File</code> menu to load a treebank of parsed trees " +
  " (not supplied with Tregex) and then you enter a pattern and press <code>Search</code>. " +
  " If your Treebank is not in UTF-8/ASCII or not in Penn Treebank format, you should" +
  " also specify the treebank format and encoding under the <code>Preferences</code> menu item <i>before</i> loading the treebank. " +
  " <p>" +
  "  The following table shows the symbols that are allowed in the pattern," +
  " and below there is more information about using these patterns." +
  "<p> <table border = \"1\"> <tr><th>Symbol<th>Meaning " +
  " <tr><td>A &lt;&lt; B <td>A dominates B" +
  " <tr><td>A &gt;&gt; B <td>A is dominated by B" +
  " <tr><td>A &lt; B <td>A immediately dominates B" +
  " <tr><td>A &gt; B <td>A is immediately dominated by B" +
  " <tr><td>A &#36; B <td>A is a sister of B (and not equal to B)" +
  " <tr><td>A .. B <td>A precedes B" +
  "<tr><td>A . B <td>A immediately precedes B" +
  "<tr><td>A ,, B <td>A follows B" +
  "<tr><td>A , B <td>A immediately follows B" +
  "<tr><td>A &lt;&lt;, B <td>B is a leftmost descendant of A" +
  "<tr><td>A &lt;&lt;- B <td>B is a rightmost descendant of A" +
  "<tr><td>A &gt;&gt;, B <td>A is a leftmost descendant of B" +
  "<tr><td>A &gt;&gt;- B <td>A is a rightmost descendant of B" +
  "<tr><td>A &lt;, B <td>B is the first child of A" +
  "<tr><td>A &gt;, B <td>A is the first child of B" +
  "<tr><td>A &lt;- B <td>B is the last child of A" +
  "<tr><td>A &gt;- B <td>A is the last child of B" +
  "<tr><td>A &lt;` B <td>B is the last child of A" +
  "<tr><td>A &gt;` B <td>A is the last child of B" +
  "<tr><td>A &lt;i B <td>B is the ith child of A (i > 0)" +
  "<tr><td>A &gt;i B <td>A is the ith child of B (i > 0)" +
  "<tr><td>A &lt;-i B <td>B is the ith-to-last child of A (i > 0)" +
  "<tr><td>A &gt;-i B <td>A is the ith-to-last child of B (i > 0)" +
  "<tr><td>A &lt;: B <td>B is the only child of A" +
  "<tr><td>A &gt;: B <td>A is the only child of B" +
  "<tr><td>A &lt;&lt;: B <td>A dominates B via an unbroken chain (length > 0) of unary local trees." +
  "<tr><td>A &gt;&gt;: B <td>A is dominated by B via an unbroken chain (length > 0) of unary local trees." +
  "<tr><td>A &#36;++ B <td>A is a left sister of B (same as &#36;.. for context-free trees)" +
  "<tr><td>A &#36;-- B <td>A is a right sister of B (same as &#36;,, for context-free trees)" +
  "<tr><td>A &#36;+ B <td>A is the immediate left sister of B (same as &#36;. for context-free trees)" +
  "<tr><td>A &#36;- B <td>A is the immediate right sister of B (same as &#36;, for context-free trees)" +
  "<tr><td>A &#36;.. B <td>A is a sister of B and precedes B" +
  "<tr><td>A &#36;,, B <td>A is a sister of B and follows B" +
  "<tr><td>A &#36;. B <td>A is a sister of B and immediately precedes B" +
  "<tr><td>A &#36;, B <td>A is a sister of B and immediately follows B" +
  "<tr><td>A &lt;+(C) B <td>A dominates B via an unbroken chain of (zero or more) nodes matching description C" +
  "<tr><td>A &gt;+(C) B <td>A is dominated by B via an unbroken chain of (zero or more) nodes matching description C" +
  "<tr><td>A .+(C) B <td>A precedes B via an unbroken chain of (zero or more) nodes matching description C" +
  "<tr><td>A ,+(C) B <td>A follows B via an unbroken chain of (zero or more) nodes matching description C" +
  "<tr><td>A &lt;&lt;&#35; B <td>B is a head of phrase A" +
  "<tr><td>A &gt;&gt;&#35; B <td>A is a head of phrase B" +
  "<tr><td>A &lt;&#35; B <td>B is the immediate head of phrase A" +
  "<tr><td>A &gt;&#35; B <td>A is the immediate head of phrase B" +
  "<tr><td>A == B <td>A and B are the same node" +
  "<tr><td>A : B<td>[this is a pattern-segmenting operator that places no constraints on the relationship between A and B]" +
  "</table>") +

  (" <p> Node label descriptions (represented by A, B, and C in the table above) " +
  " match either internal nodes or words in a sentence.  They can be plain strings, which much match labels" +
  " exactly, or regular expressions in regular expression slashes: /regex/." +
  " Literal string matching proceeds as String equality."+
  " In order to prevent ambiguity with other Tregex symbols, there are some restrictions " +
  " on characters in plain strings: you can use standard" +
  " \"identifiers\" - i.e., strings matching  [a-zA-Z]([a-zA-Z0-9_])* - " +
  " or any non-ASCII character, such as Arabic or Chinese letters." +
  " If you want to use other symbols, you can do so by using a regular " +
  " expression instead of a string, for example /^,$/ for a comma. " +
  " Note that strings only match a complete node label, while regular expressions match " +
  " if they match anywhere inside the node label. " +
  " A disjunctive list of literal strings can be given separated by '|'." +
  " The special string '__' (two underscores) can be used to match any" +
  " node.  (WARNING!!  Use of the '__' node description may seriously" +
  " slow down search.)  If a label description is preceded by '@', the" +
  " label will match any node whose <em>basicCategory</em> matches the" +
  " description.  <em>NB: A single '@' thus scopes over a disjunction" +
  " specified by '|': @NP|VP means things with basic category NP or VP." +
  " </em> Label description regular expressions are matched as a Java regex" +
  "  <code>find()</code>, as in Perl/tgrep;" +
  " you need to specify <code>^</code> or <code>$</code> to constrain matches" +
  " to the ends of node labels." +
  " <p> " +
  " In a chain of relations, all relations are relative to the first node in " +
  " the chain. Nodes can be grouped using parentheses '(' and ')' to change this. " +
  " For example, <code> (S &lt; VP &lt; NP) </code> means" +
  " \"an S over a VP and also over an NP\"." +
  " If instead what you want is an S above a VP above an NP, you should write" +
  " \"<code>S &lt; (VP &lt; NP)</code>\"." +
  " The expression <code> S &lt; (NP $++ VP) </code> matches an S" +
  " over an NP, where the NP has a VP as a right sister.") +

  (" <p><h3>Boolean relational operators</h3>" +
  " Relations can be combined using the '&amp;' and '|' operators," +
  " negated with the '!' operator, and made optional with the '?' operator.  Thus" +
  " <code> (NP &lt; NN | &lt; NNS) </code> will match an NP node dominating either" +
  " an NN or an NNS.  <code> (NP &gt; S &amp; $++ VP) </code> matches an NP that" +
  " is both under an S and has a VP as a right sister." +
  " <p> Relations can be grouped using brackets '[' and ']'.  So the" +
  " expression" +
  " <blockquote>" +
  " <code> NP [&lt; NN | &lt; NNS] &amp; &gt; S </code>" +
  " </blockquote>" +
  " matches an NP that (1) dominates either an NN or an NNS, and (2) is under an S.  Without" +
  " brackets, &amp; takes precidence over |, and equivalent operators are" +
  " left-associative.  Also note that &amp; is the default combining operator if the" +
  " operator is omitted in a chain of relations, so that these two patterns are equivalent:" +
  " <blockquote>" +
  " <code> (S &lt; VP &lt; NP) </code><br>" +
  " <code> (S &lt; VP &amp; &lt; NP) </code>" +
  " </blockquote>" +
  " As another example, <code> (VP &lt; VV | &lt; NP $ NP)" +
  " </code> can be written explicitly as <code> (VP [&lt; VV | [&lt; NP &amp; $ NP] ] )" +
  " </code>." +
  " <p> Relations can be negated with the '!' operator, in which case the" +
  " expression will match only if there is no node satisfying the relation." +
  " For example <code> (NP !&lt; NNP) </code> matches only NPs not dominating" +
  " an NNP.  Label descriptions can also be negated with '!': (NP &lt; !NNP|NNS) matches" +
  " NPs dominating some node that is not an NNP or an NNS." +
  " <p> Relations can be made optional with the '?' operator.  This way the" +
  " expression will match even if the optional relation is not satisfied.  This is useful when used together" +
  "  with node naming (see below).") +

  " <p><h3>Basic Categories</h3>" +
  " In order to consider only the \"basic category\" of a tree label," +
  " i.e., to ignore functional tags or other annotations on the label," +
  " prefix that node's description with the @ symbol.  For example" +
  " <code> (@NP &lt; @/NN.?/) </code>.  By default, the notion of basic category" +
  " works for Penn Treebank tree node labels.  This can only be used for individual nodes;" +
  " if you want all nodes to use the basic category, it would be more efficient" +
  " (for you and Tregex) to use a <code>TreeNormalizer</code> to remove functional" +
  " tags before passing the tree to the TregexPattern. (In the GUI, a TreeNormalizer" +
  " can be part of the functionality of a TreeReader specified in Preferences.)" +

  " <p><h3>Segmenting patterns</h3>" +
  " The \":\" operator allows you to segment a pattern into two pieces.  This can simplify your pattern writing." +
  " The semantics is that both patterns must match a tree, but the match of the first pattern" +
  "  is returned as the matching node. For example, the pattern" +
  " <blockquote>" +
  "   S : NP" +
  " </blockquote>" +
  " matches only those S nodes in trees that also have an NP node (somewhere in" +
  " the tree - it's location with respect to the S is unconstrained)." +

  (" <p><h3>Naming nodes</h3>" +
  " Nodes can be given names (a.k.a. handles) using '=', for example, " +
  " <code>ADJP=ap</code>.  A named node will be stored in a" +
  " map that maps names to nodes so that if a match is found, the node" +
  " corresponding to the named node can be extracted from the map.  For" +
  " example <code> (NP &lt; NNP=name) </code> will match an NP dominating an NNP." +
  " Node names have two purposes: they can be referred back to in the Tregex pattern, " +
  " and, programmatically, after a match is found, the map can be queried with the" +
  " name to retrieve the matched node using <code>TregexMatcher#getNode(String s)</code>" +
  " with argument \"name\" (<it>not</it> \"=name\")." +
  " Note that you are not allowed to name a node that is under the scope of a negation operator (the semantics would" +
  " be unclear, since you can't store a node that never gets matched to)." +
  " Trying to do so will cause a <code>ParseException</code> to be thrown. Named nodes <em>can</em> be put within the scope of an optionality operator." +
  " <p>Secondly, named nodes can be referred back to in a Tregex pattern." +
  " A named node that refers back to a previous named node need not have a node" +
  " description -- this is known as \"backreferencing\".  In this case, the expression" +
  " will match only when all instances of the same name get matched to the same tree node." +
  " For example: the pattern" +
  " <blockquote>" +
  " <code> (@NP &lt;, (@NP $+ (/,/ $+ (@NP $+ /,/=comma))) &lt;- =comma) </code>" +
  " </blockquote>" +
  " matches only an NP dominating exactly the sequence <code>NP , NP ,</code>" +
  " - the mother NP cannot have any other daughters. Multiple" +
  " backreferences are allowed.  If the node with no node description does not refer" +
  " to a previously named node, there will be no error; the expression simply will" +
  " not match anything." +
  " <p> Another way to refer to previously named nodes is with the \"link\" symbol: '~'." +
  " A link is like a backreference, except that instead of having to be <i>equal to</i> the" +
  " referred node, the current node only has to match the label of the referred to node." +
  " A link cannot have a node description, i.e. the '~' symbol must immediately follow a" +
  " relation symbol. For example, the pattern <code>ADJP=cat &lt, ~cat &lt;- ~cat</code> will" +
  " match all ADJP whose first and last child is also an ADJP. ") +

  (" <p><h3>Variable Groups</h3>" +
  " If you write a node description using a regular expression, you can assign its matching groups to variable names." +
  " If more than one node has a group assigned to the same variable name, then matching will only occur when all such groups" +
  " capture the same string.  This is useful for enforcing coindexation constraints.  The syntax is" +
  " <blockquote>" +
  " <code> / &lt;regex-stuff&gt; /#&lt;group-number&gt;%&lt;variable-name&gt;</code>" +
  " </blockquote>" +
  " For example, the following pattern (designed for Penn Treebank trees)" +
  " <blockquote>" +
  " <code> @SBAR &lt; /^WH.*-([0-9]+)$/#1%index &lt;&lt; (__=empty &lt; (/^-NONE-/ &lt; /^\\*T\\*-([0-9]+)$/#1%index)) </code>" +
  " </blockquote>" +
  " will match only such that the WH- node under the SBAR is coindexed with the" +
  " trace node that gets the name <code>empty</code>.") +

  ("<p><h3>Comparison with tgrep and tgrep2</h3>" +
  " Tregex is similar to tgrep and tgrep2.  Tregex supports all the standard operators of tgrep" +
  " (but not alternative symbols), but where the semantics of tgrep2 for those operators" +
  " differs, it follows tgrep2. Tregex implements many of the extensions of tgrep2, such as" +
  " boolean expressions, labeled nodes, segmented patterns, and the ? operator, but not the = and ~ operators," +
  " macros, nor many of the command-line options, such as for formatted output.  Tregex implements" +
  " some unique additions of its own, such as operators for being the head of, or domination or precedence along a constrained path," +
  " and the == operator. Another big difference is that tgrep and tgrep2 pre-index treebanks, while tregex iterates" +
  " through them at runtime.  The latter is slower but more flexible.  Two final differences are that tregex has a GUI" +
  " and a companion language Tsurgeon for editing trees.") +

  ("<p><h3>Tsurgeon</h3>" +
  " Tregex has a companion language Tsurgeon for altering trees according to rules.  To use Tsurgeon (and to see" +
  " the help for it), enable Tsurgeon using the <code>Preferences</code> menu item." ) +
  "<p></html>";

}
