// Tregex/Tsurgeon, TregexGUI - a GUI for tree search and modification
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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.List;

import javax.swing.*;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.swing.FontDetector;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.tregex.gui.MatchesPanel.MatchesPanelListener;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Main class for creating a tregex gui.  Manages the components and holds the menu bar.
 * A tregex gui (Interactive Tregex) allows users to perform tregex searches in a gui interface
 * and view the results of those searches.  Search results may be saved.
 * (This was initially written to target the old pre-Java 8 macOS (OS X) Java interface, but has
 * now been written to use the Java 11+ Desktop class and associated properties.
 *
 * @author Anna Rafferty
 * @author Christopher Manning
 */
@SuppressWarnings("serial")
public class TregexGUI extends JFrame implements ActionListener, MatchesPanelListener  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TregexGUI.class);

  private static TregexGUI instance; // = null;

  /** Menu item which opens the preferences dialog */
  private JMenuItem preferences;
  /** Menu item which loads tree files */
  private JMenuItem loadFiles;
  /** Menu item which saves the matched trees */
  private JMenuItem saveMatches;
  /** Menu item which saves the matched sentences */
  private JMenuItem saveSentences;
  /** Menu item which saves the search statistics */
  private JMenuItem saveHistory;
  /** Menu item which loads a Tsurgeon script */
  private JMenuItem loadTsurgeon;
  /** Check box menu item which turns Tdiff on and off */
  private JMenuItem tDiff;
  /** Menu item which exits; only added to the menu when not on a Mac */
  private JMenuItem quit; //for when we're not running on a mac
  /** Menu item which copies from the focused component */
  private JMenuItem copy;
  /** Menu item which runs the search */
  private JMenuItem searchMenuItem;
  /** Menu item which selects the previous matching tree */
  private JMenuItem prevMatch;
  /** Menu item which selects the next matching tree */
  private JMenuItem nextMatch;
  /** Menu item which scrolls to the previous match within the displayed tree */
  private JMenuItem prevTreeMatch;
  /** Menu item which scrolls to the next match within the displayed tree */
  private JMenuItem nextTreeMatch;
  /** Menu item which removes all of the loaded files */
  private JMenuItem clearFileList;

  //file choosing components for loading trees
  /** The chooser for loading files, created when first needed */
  private JFileChooser chooser; // = null;

  /** Transformer applied to trees as they are loaded, or null for none */
  final TreeTransformer transformer; // used in FileTreeModel

  //preferences, about panel so that we don't have to remake each time
  /** The preferences dialog, created when first needed */
  private PreferencesPanel preferenceDialog; // = null;
  /** The about dialog */
  private JDialog aboutBox; // = null;

  private static final String TRANSFORMER = "transformer";


  private JMenuBar getMenu() {
    JMenuBar mbar = new JMenuBar();

    //make file menu
    JMenu file = new JMenu("File");
    loadFiles = new JMenuItem("Load trees...");
    loadFiles.addActionListener(this);
    saveMatches = new JMenuItem("Save matched trees...");
    saveMatches.addActionListener(this);
    saveMatches.setEnabled(false);
    saveSentences = new JMenuItem("Save matched sentences...");
    saveSentences.addActionListener(this);
    saveSentences.setEnabled(false);
    saveHistory = new JMenuItem("Save statistics...");
    saveHistory.addActionListener(this);
    saveHistory.setEnabled(false);
    loadTsurgeon = new JMenuItem("Load Tsurgeon script...");
    loadTsurgeon.addActionListener(this);
    clearFileList = new JMenuItem("Clear tree file list");
    clearFileList.addActionListener(this);
    clearFileList.setEnabled(false);
    quit = new JMenuItem("Exit");
    quit.addActionListener(this);

    file.add(loadFiles);
    file.add(loadTsurgeon);
    file.addSeparator();
    file.add(clearFileList);
    file.addSeparator();
    file.add(saveMatches);
    file.add(saveSentences);
    file.add(saveHistory);
    if ( ! isMacOS()) {
      file.addSeparator();
      file.add(quit);
    }

    //make edit menu
    JMenu edit = new JMenu("Edit");
    copy = new JMenuItem("Copy");
    copy.setActionCommand((String)TransferHandler.getCopyAction().
        getValue(Action.NAME));
    copy.addActionListener(new TransferActionListener());
    edit.add(copy);

    JMenu search = new JMenu("Search");
    searchMenuItem = new JMenuItem("Search");
    searchMenuItem.addActionListener(this);
    search.add(searchMenuItem);
    prevMatch = new JMenuItem("Display previous match");
    prevMatch.addActionListener(this);
    search.add(prevMatch);
    nextMatch = new JMenuItem("Display next match");
    nextMatch.addActionListener(this);
    search.add(nextMatch);
    search.addSeparator();
    prevTreeMatch = new JMenuItem("Show previous match within tree");
    prevTreeMatch.addActionListener(this);
    search.add(prevTreeMatch);
    nextTreeMatch = new JMenuItem("Show next match within tree");
    nextTreeMatch.addActionListener(this);
    search.add(nextTreeMatch);

    preferences = new JMenuItem("Options...");
    preferences.addActionListener(this);
    tDiff = new JCheckBoxMenuItem("Tdiff");
    tDiff.addActionListener(this);

    JMenu tools = new JMenu("Tools");
    if ( ! isMacOS()) {
      tools.add(preferences);
    }
    tools.add(tDiff);

    mbar.add(file);
    mbar.add(edit);
    mbar.add(search);
    mbar.add(tools);

    setShortcutKeys(); // sets for appropriate operating system

    loadPreferences();

    return mbar;
  }


  private void setShortcutKeys() {
    if (isMacOS()) {
      setMacShortcutKeys();
    } else {
      setWindowsShortcutKeys();
    }
  }

  private void setMacShortcutKeys() {
    preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.META_DOWN_MASK));
    loadFiles.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.META_DOWN_MASK));
    saveMatches.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK));
    saveHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK+InputEvent.META_DOWN_MASK));
    quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.META_DOWN_MASK));
    copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK));

    searchMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK));
    prevMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.META_DOWN_MASK));
    nextMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.META_DOWN_MASK));
    prevTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK));
    nextTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK));
  }

  private void setWindowsShortcutKeys() {
    // preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, Event.CTRL_MASK)); // cdm: just skip this, I don't think Windows ever uses comma like this
    loadFiles.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    saveMatches.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    saveHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK+InputEvent.CTRL_DOWN_MASK));
    quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK)); // cdm: maybe should be Control or Alt F4
    copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));

    searchMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK));
    prevMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK));
    nextMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK));
    prevTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    nextTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
  }


  private void initAboutBox() {
    aboutBox = new JDialog(this, "About Tregex");
    aboutBox.getContentPane().setLayout(new BorderLayout());
    aboutBox.getContentPane().add(new JLabel("<html><h1>Tregex and Tsurgeon</h1></html>", SwingConstants.CENTER), BorderLayout.NORTH);

    aboutBox.getContentPane().add(new JLabel("<html><p>Tregex by Galen Andrew and Roger Levy<br>Tsurgeon by Roger Levy<br>Graphical interface by Anna Rafferty<br>Additional features and development by Chris Manning</p></html>", SwingConstants.CENTER), BorderLayout.CENTER);
    aboutBox.getContentPane().add(new JLabel("<html><p><font size=2>© 2007, 2025 The Board of Trustees of The Leland Stanford Junior University.<br>Distributed under the GNU General Public License</font></p></html>", SwingConstants.CENTER), BorderLayout.SOUTH);
  }

  /**
   * Used to change the status of the save file menu item to reflect
   * whether any trees are available to save.
   *
   * @param enabled whether the save matches and save sentences items are enabled
   */
  public void setSaveEnabled(boolean enabled) {
    if (saveMatches.isEnabled() != enabled) {
      saveMatches.setEnabled(enabled);
      saveSentences.setEnabled(enabled);
    }
  }

  /**
   * Used to change the status of the saveHistory file menu item to reflect
   * whether any search statistics are available to save
   *
   * @param enabled whether the save statistics item is enabled
   */
  public void setSaveHistoryEnabled(boolean enabled) {
    if (saveHistory.isEnabled() != enabled) {
      saveHistory.setEnabled(enabled);
    }
  }

  /**
   * Used to change the status of the tsurgeon file menu item to reflect
   * whether tsurgeon is enabled
   *
   * @param enabled whether the load Tsurgeon script item is enabled
   */
  public void setTsurgeonEnabled(boolean enabled) {
    if (loadTsurgeon.isEnabled() != enabled) {
      loadTsurgeon.setEnabled(enabled);
    }
  }

  private static void setMacProperties() {
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty( "apple.awt.application.name", "TregexGUI" );
    System.setProperty("apple.awt.textantialiasing", "true");
    System.setProperty( "apple.awt.application.appearance", "system" );
  }


  /**
   * This method duplicates the test done in jdk.internal.org.jline.utils.OSUtils
   *
   * @return whether the operating system is macOS
   */
  public static boolean isMacOS() {
    return System.getProperty("os.name").toLowerCase().startsWith("mac");
  }

  /**
   * Returns the main window.
   *
   * @return the window, or null if it has not been created yet
   */
  public static TregexGUI getInstance() {
    return instance;
  }

  /**
   * Sets up the file panel, input panel, and match panel
   */
  private static JSplitPane setUpTopPanels() {
    JPanel filePanel = FilePanel.getInstance();
    JPanel inputPanel = InputPanel.getInstance();
    JPanel matchesPanel = MatchesPanel.getInstance();
    JSplitPane inputAndMatchesPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, inputPanel, matchesPanel);
    inputAndMatchesPane.setDividerLocation(450);
    inputAndMatchesPane.setResizeWeight(.5);
    inputAndMatchesPane.setBorder(BorderFactory.createEmptyBorder());
    JSplitPane fullTopPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, filePanel, inputAndMatchesPane);
    fullTopPanel.setDividerLocation(275);
    fullTopPanel.setBorder(BorderFactory.createEmptyBorder());
    return fullTopPanel;
  }


  /** Constructor */
  private TregexGUI(Properties props, List<String> initialFiles) {
    super("Tregex");
    TregexGUI.instance = this;
    setDefaultLookAndFeelDecorated(true);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    String transformerClass = props.getProperty(TRANSFORMER, null);
    if (transformerClass == null) {
      transformer = null;
    } else {
      transformer = ReflectionLoading.loadByReflection(transformerClass);
    }

    initAboutBox();

    if (Desktop.isDesktopSupported()) {
      // Application Menu (About and Preferences)
      Desktop desk = Desktop.getDesktop();
      if (desk.isSupported(Desktop.Action.APP_ABOUT)) {
        desk.setAboutHandler(e -> about());
      }
      if (desk.isSupported(Desktop.Action.APP_PREFERENCES)) {
        desk.setPreferencesHandler(e -> doPreferences());
      }
    }


    Container content = getContentPane();
    content.setBackground(Color.lightGray);
    // NB: The menu has to exist before you can successfully create an input.  Bad side effect dependency!
    JMenuBar mbar = getMenu();
    JPanel displayMatchesPanel = DisplayMatchesPanel.getInstance();
    JSplitPane inputAndMatchesPanel = setUpTopPanels();
    MatchesPanel.getInstance().addListener(this);
    this.setFocusTraversalKeysEnabled(true);

    // stick it all together now
    setJMenuBar(mbar);
    content.setLayout(new BorderLayout());
    JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputAndMatchesPanel, displayMatchesPanel);
    verticalSplit.setResizeWeight(.2);
    this.add(verticalSplit, BorderLayout.CENTER);

    // make size
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    int begX = (int) (screenSize.width * 0.05);
    int begY = (int) (screenSize.height * 0.05);
    screenSize.width = (int) (screenSize.width * 0.9);
    screenSize.height = (int) (screenSize.height * 0.9);
    if (screenSize.width > 1200) { screenSize.width = 1200; }
    if (screenSize.height > 800) { screenSize.height = 800; }
    setPreferredSize(screenSize);
    Dimension displayMatchesSize = new Dimension((int) (screenSize.getWidth()),(int) (screenSize.getHeight()*3/4.));
    displayMatchesPanel.setPreferredSize(displayMatchesSize);
    // center it
    setBounds(begX, begY, screenSize.width, screenSize.height);
    pack();

    if ( ! initialFiles.isEmpty()) {
      File[] files = new File[initialFiles.size()];
      for (int i = 0; i < initialFiles.size(); ++i) {
        files[i] = new File(initialFiles.get(i));
      }
      startFileLoadingThread(new EnumMap<>(FilterType.class), files);
    }

    setVisible(true);
  }

  
  //Creates a new JFileChooser, doing the boilerplate
  // to start it in the current directory.
  private static JFileChooser createFileChooser() {
    final JFileChooser chooser = new JFileChooser();
    //  sets up default file view
    try {
      final File chooserFile = new File((new File(".").getCanonicalPath()));
      chooser.setCurrentDirectory(chooserFile);
    } catch (IOException e) {
      // go with default directory.
    }

    chooser.setMultiSelectionEnabled(true);
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    return chooser;
  }


  /**
   * Load and apply application preferences.
   */
  void loadPreferences() {
    //general parameters
    InputPanel.getInstance().enableTsurgeon(Preferences.getEnableTsurgeon());
    MatchesPanel.getInstance().setShowOnlyMatchedPortion(Preferences.getMatchPortionOnly());
    //display stuff
    MatchesPanel.getInstance().setHighlightColor(Preferences.getHighlightColor());
    InputPanel.getInstance().setNumRecentPatterns(Preferences.getHistorySize());
    MatchesPanel.getInstance().setMaxMatches(Preferences.getMaxMatches());

    //tree display stuff
    DisplayMatchesPanel.getInstance().setMatchedColor(Preferences.getMatchedColor());
    DisplayMatchesPanel.getInstance().setDefaultColor(Preferences.getTreeColor());
    DisplayMatchesPanel.getInstance().setFontName(Preferences.getFont());
    MatchesPanel.getInstance().setFontName(Preferences.getFont());

    int fontSize = Preferences.getFontSize();
    if(fontSize != 0)
      DisplayMatchesPanel.getInstance().setFontSize(Preferences.getFontSize());

    //advanced stuff
    HeadFinder hf = Preferences.getHeadFinder();
    InputPanel.getInstance().setHeadFinder(hf);

    TreeReaderFactory trf = Preferences.getTreeReaderFactory();
    FilePanel.getInstance().setTreeReaderFactory(trf);

    String hfName = hf.getClass().getSimpleName();
    String trfName = trf.getClass().getSimpleName();
    String encoding = Preferences.getEncoding();
    if (encoding != null && !encoding.isEmpty())
      FileTreeModel.setCurEncoding(encoding);
    if (PreferencesPanel.isChinese(hfName, trfName))
      setChineseFont();
    else if (PreferencesPanel.isArabic(hfName, trfName))
      setArabicFont();

    if (preferenceDialog == null) {
      preferenceDialog = new PreferencesPanel(this);
    }
    preferenceDialog.checkEncodingAndDisplay(hfName, trfName);
  }

  private static void setChineseFont() {
    Thread t = new Thread() {
      @Override
      public void run() {
        List<Font> fonts = FontDetector.supportedFonts(FontDetector.CHINESE);
        String fontName = "";
        if ( ! fonts.isEmpty()) {
          fontName = fonts.get(0).getName();
        } else if (FontDetector.hasFont("Watanabe Mincho")) {
          fontName = "Watanabe Mincho";
        }

        if ( ! fontName.isEmpty()) {
          DisplayMatchesPanel.getInstance().setFontName(fontName);
          MatchesPanel.getInstance().setFontName(fontName);
        }
      }
    };
    t.start();
  }

  private static void setArabicFont() {
    Thread t = new Thread(() -> {
      List<Font> fonts = FontDetector.supportedFonts(FontDetector.ARABIC);
      String fontName = "";
      if ( ! fonts.isEmpty()) {
        fontName = fonts.get(0).getName();
      }
      if ( ! fontName.isEmpty()) {
        DisplayMatchesPanel.getInstance().setFontName(fontName);
        MatchesPanel.getInstance().setFontName(fontName);
      }
    });
    t.start();
  }

  /*
   * Method for bringing up the load file dialog box and conveying
   * the chosen files to the JFilePanel.
   */
  private void doLoadFiles() {
    if (chooser == null) {
      chooser = createFileChooser();
    }
    String approveText = chooser.getApproveButtonText();
    chooser.setApproveButtonText("Load with file filters");
    int status = chooser.showOpenDialog(this);
    chooser.setApproveButtonText(approveText);
    if (status == JFileChooser.APPROVE_OPTION) {
      //now set up the file filters if there are directories
      File[] selectedFiles = chooser.getSelectedFiles();
      boolean haveDirectory = false;
      for (File f : selectedFiles) {
        if (f.isDirectory()) {
          haveDirectory = true;
          break;
        }
      }
      if (haveDirectory) {
        doFileFilters(selectedFiles);
      } else {
        startFileLoadingThread(new EnumMap<>(FilterType.class), selectedFiles);
      }
    }
  }

  
  /**
   * Action listener for copy menu items: tracks the component which has
   * the keyboard focus and runs that component's action for the command.
   */
  public static class TransferActionListener implements ActionListener, PropertyChangeListener {

    private JComponent focusOwner; // = null;

    //This code based on Java DnD tutorial
    /**
     * Creates the listener and registers it to follow the keyboard focus.
     */
    public TransferActionListener() {
      KeyboardFocusManager manager = KeyboardFocusManager.
      getCurrentKeyboardFocusManager();
      manager.addPropertyChangeListener("permanentFocusOwner", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
      Object o = e.getNewValue();
      if (o instanceof JComponent) {
        focusOwner = (JComponent)o;
      } else {
        focusOwner = null;
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (focusOwner == null)
        return;
      String action = e.getActionCommand();
      Action a = focusOwner.getActionMap().get(action);
      if (a != null) {
        a.actionPerformed(new ActionEvent(focusOwner,
            ActionEvent.ACTION_PERFORMED,
            null));
      }
    }
  }

  private void doFileFilters(File[] files) {
    final File[] cFiles = files;
    final JPanel fileFilterPanel = new JPanel();
    fileFilterPanel.setLayout(new BoxLayout(fileFilterPanel, BoxLayout.PAGE_AXIS));
    JPanel textPanel = makeConstraintsJPanel();
    fileFilterPanel.add(textPanel);
    fileFilterPanel.add(Box.createVerticalStrut(5));
    Box defaultFilter = getNewFilter();
    //defaultFilter.setBorder(BorderFactory.createLineBorder(Color.black));
    //fileFilterPanel.setBorder(BorderFactory.createLineBorder(Color.black));
    fileFilterPanel.add(defaultFilter);
    final JOptionPane fileFilterDialog = new JOptionPane();
    fileFilterDialog.setMessage(fileFilterPanel);
    JButton[] options = new JButton[3];

    JButton cancel = new JButton("Cancel");
    JButton add = new JButton("Add another filter");
    JButton okay = new JButton("OK");

    options[0] = okay;
    options[1] = add;
    options[2] = cancel;

    fileFilterDialog.setOptions(options);

    final JDialog dialog = fileFilterDialog.createDialog(null, "Set file filters");
    okay.addActionListener(arg0 -> {
      // first check if we have a file range option and make sure it's valid
      final EnumMap<FilterType,String> filters = getFilters(fileFilterPanel);
      if (filters.containsKey(FilterType.isInRange)) {
        try {
          // if we can create it, then it's not invalid!
          new NumberRangesFileFilter(filters.get(FilterType.isInRange), false);
        } catch (Exception e) {
          JOptionPane.showMessageDialog(dialog, new JLabel("<html>Please check the range you specified for the file numbers.  Ranges must be numerical, and disjoint <br>ranges should be separated by commas.  For example \"1-200,250-375\" is a valid range.</html>"), "Error in File Number Range", JOptionPane.ERROR_MESSAGE);
          return;
        }
      }
      dialog.setVisible(false);
      startFileLoadingThread(filters, cFiles);
    });
    add.addActionListener(e -> {
      fileFilterPanel.add(getNewFilter());
      dialog.pack();
    });
    cancel.addActionListener(e -> dialog.setVisible(false));
    dialog.getRootPane().setDefaultButton(okay);
    dialog.pack();
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private static JPanel makeConstraintsJPanel() {
    JLabel text = new JLabel("<html>Please indicate any constraints on the files you want to load. All files in specified folders that satisfy all of the given constraints will be loaded. Just press OK to load all files.</html>");
    //text.setBorder(BorderFactory.createLineBorder(Color.black));
    text.setAlignmentX(SwingConstants.LEADING);
    JPanel textPanel = new JPanel(new BorderLayout());
    textPanel.setPreferredSize(new Dimension(100,50));
    //textPanel.setBorder(BorderFactory.createLineBorder(Color.black));
    textPanel.add(text);
    return textPanel;
  }

  private void startFileLoadingThread(final EnumMap<FilterType,String> filters, final File[] cFiles) {
    Thread t = new Thread() {
      @Override
      public void run() {
        FilePanel.getInstance().loadFiles(filters, cFiles);
        SwingUtilities.invokeLater(() -> clearFileList.setEnabled(true));
      }
    };
    t.start();
  }

  private static EnumMap<FilterType,String> getFilters(JPanel panel) {
    EnumMap<FilterType,String> filters = new EnumMap<>(FilterType.class);
    Component[] components = panel.getComponents();
    for(Component c : components) {
      if (c.getClass() != Box.class) {
        continue;
      }
      JComboBox filterType = (JComboBox) ((Container) c).getComponent(0);
      JTextField filterValue = (JTextField) ((Container) c).getComponent(2);
      filters.put((FilterType) filterType.getSelectedItem(), filterValue.getText());
    }

    return filters;
  }

  private static Box getNewFilter() {
    Box filter = Box.createHorizontalBox();
    FilterType[] filterTypeOptions = FilterType.values();
    JComboBox filterTypes = new JComboBox(filterTypeOptions );
    filterTypes.setEditable(false);
    filter.add(filterTypes);
    filter.add(Box.createHorizontalGlue());
    JTextField filterInput = new JTextField();
    //filterInput.setMaximumSize(new Dimension(50,50));
    filterInput.setEditable(true);
    filter.add(filterInput);
    return filter;
  }

  /** The kinds of filter which can be applied to file names when loading a directory */
  public enum FilterType {
    /** No filter */
    none("None"),
    /** The file name must end with the given string */
    hasExtension("Has extension: "),
    /** The file name must start with the given string */
    hasPrefix("Has prefix: "),
//    hasNumGreaterThan("Has number greater than: "),
//    hasNumLessThan("Has number less than: ");
    /** The number in the file name must be in the given ranges */
    isInRange("Has number in range: ");

    private final String text;
    private FilterType(String string) {
      text = string;
    }

    @Override
    public String toString() {
      return text;
    }
  }


  /**
   * Method for saving the trees that match the current tregex expression
   */
  private void doSaveFile() {
    if(chooser == null)
      chooser = createFileChooser();
    int status = chooser.showSaveDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            //FileWriter out = new FileWriter(chooser.getSelectedFile());
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(chooser.getSelectedFile()), FileTreeModel.getCurEncoding()));
            String str = MatchesPanel.getInstance().getMatches();
            out.write(str);
            out.flush();
            out.close();
          } catch(Exception e) {
            log.info("Exception in save");
            e.printStackTrace();
          }
        }
      };
      t.start();
    }
  }

  /**
   * Method for saving the sentences with trees that match the current tregex expression
   */
  private void doSaveSentencesFile() {
    if (chooser == null)
      chooser = createFileChooser();
    int status = chooser.showSaveDialog(this);
    if(status == JFileChooser.APPROVE_OPTION) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(chooser.getSelectedFile()), FileTreeModel.getCurEncoding()));
            String str = MatchesPanel.getInstance().getMatchedSentences();
            out.write(str);
            out.flush();
            out.close();
          } catch(Exception e) {
            log.info("Exception in save");
            e.printStackTrace();
          }
        }
      };
      t.start();
    }
  }


  /**
   * Method for saving the statistics computed in our runs (unique matches, number of matched trees)
   */
  private void doSaveHistory() {
    if (chooser == null)
      chooser = createFileChooser();
    int status = chooser.showSaveDialog(this);
    if(status == JFileChooser.APPROVE_OPTION) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(chooser.getSelectedFile()), FileTreeModel.getCurEncoding()));
            String hist = InputPanel.getInstance().getHistoryString();
            out.write(hist);
            out.flush();
            out.close();
          } catch(Exception e) {
            log.info("Exception in save");
            e.printStackTrace();
          }
        }
      };
      t.start();
    }
  }


  private void loadTsurgeonScript() {
    if (chooser == null)
      chooser = createFileChooser();
    int status = chooser.showOpenDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            BufferedReader reader = new BufferedReader(new FileReader(chooser.getSelectedFile().toString()));
            final String tregexPatternString = Tsurgeon.getTregexPatternFromReader(reader);
            final String tsurgeonOperationsString = Tsurgeon.getTsurgeonTextFromReader(reader);
            SwingUtilities.invokeLater(() -> InputPanel.getInstance().setScriptAndPattern(tregexPatternString, tsurgeonOperationsString));
          } catch (IOException e) {
            log.err("Error parsing Tsurgeon file");
            //e.printStackTrace();
          }
        }
      };
      t.start();
    }
  }


  void doPreferences() {
    if (preferenceDialog == null) {
      preferenceDialog = new PreferencesPanel(this);
    }
    preferenceDialog.pack();
    preferenceDialog.setLocationRelativeTo(this);
    preferenceDialog.setVisible(true);
  }

  //Tdiff
  /** Whether Tdiff is on */
  private boolean doTdiff = false;
  /** The number of treebanks Tdiff compares: the first active one is the reference */
  public static final int MAX_TDIFF_TREEBANKS = 2;

  private void doTdiff() {
    doTdiff = !doTdiff;
    ((JCheckBoxMenuItem) tDiff).setState(doTdiff);

    // Only allow 2 active treebanks
    if (doTdiff) {
      List<FileTreeNode> activeTreebanks = FilePanel.getInstance().getActiveTreebanks();
      for (int i = 2; i < activeTreebanks.size(); i++)
        activeTreebanks.get(i).setActive(false);
    }
  }

  /**
   * Returns whether Tdiff is on, in which case matches are compared against the first active treebank.
   *
   * @return whether Tdiff is on
   */
  public boolean isTdiffEnabled() { return doTdiff; }

  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();

    if (source == loadFiles) {
      doLoadFiles();
    } else if (source == saveMatches) {
      doSaveFile();
    } else if (source == saveSentences) {
      doSaveSentencesFile();
    } else if (source == loadTsurgeon) {
      loadTsurgeonScript();
    } else if (source == preferences) {
      doPreferences();
    } else if (source == tDiff) {
      doTdiff();
    } else if (source == quit) {
      doQuit();
    } else if (source == saveHistory) {
      doSaveHistory();
    } else if (source == clearFileList) {
      doClearFileList();
    } else if (source == searchMenuItem) {
      InputPanel.getInstance().runSearch();
    } else if (source == prevMatch) {
      MatchesPanel.getInstance().selectPreviousMatch();
    } else if (source == nextMatch) {
      MatchesPanel.getInstance().selectNextMatch();
    } else if (source == prevTreeMatch) {
      DisplayMatchesPanel.getInstance().showPrevMatchedPart();
    } else if (source == nextTreeMatch) {
      DisplayMatchesPanel.getInstance().showNextMatchedPart();
    }
  }

  /**
   * Removes all of the loaded files.
   */
  public void doClearFileList() {
    FilePanel.getInstance().clearAll();
    clearFileList.setEnabled(false);
  }

  /**
   * Exits the program.
   */
  public static void doQuit() {
    System.exit(0);
  }


  /**
   * Called by MatchesPanel to alert the frame when the matching trees have changed
   */
  public void matchesChanged() {
    setSaveEnabled(!MatchesPanel.getInstance().isEmpty());
  }


  /**
   * Main method for launching a new tregex gui object
   * <br>
   * If the argument <code>-transformer class</code> is given, that
   * class is used as a TreeTransformer when loading in trees.
   * <br>
   * All other arguments will be interpreted as filenames to preload.
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      if (isMacOS()) {
        setMacProperties();
      }
      } catch (Exception e) {
      throw new RuntimeException(e);
    }

    Properties props = new Properties();
    List<String> filenames = Generics.newArrayList();
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-" + TRANSFORMER)) {
        props.setProperty(TRANSFORMER, args[argIndex + 1]);
        argIndex += 2;
      } else {
        filenames.add(args[argIndex++]);
      }
    }

    SwingUtilities.invokeLater(() -> {
      new TregexGUI(props, filenames);
    });
  }


  /**
   * Shows the about dialog.
   */
  public void about() {
    aboutBox.setSize(400, 240);
    aboutBox.setLocation((int)this.getLocation().getX() + 30, (int)this.getLocation().getY() + 30);
    aboutBox.setResizable(false);
    aboutBox.setVisible(true);
  }

} // end TregexGUI

// NOTES
//
// java -Xdock:name= <applicationName> -Xdock:icon= <iconPath> -jar <jar file path>
//
// In 2025, Mac has Settings not Preferences any more!
//         Menu systemMenu = Display.getDefault().getSystemMenu();
//
//
//  javapackager (JDK 8) / jpackage (JDK 14 and later): These are the official tools included with the Java Development Kit (JDK) for creating native application packages. They can produce platform-specific installers (e.g., DMG/PKG for macOS, MSI/EXE for Windows, RPM/DEB for Linux) that include a bundled Java Runtime Environment (JRE). This ensures users have the correct Java version without needing a pre-installed JRE.
//
//  Code
//
//    # Example using jpackage (for creating a macOS DMG)
// jpackage --name "MyJavaApp" \
//        --input "path/to/your/jar" \
//        --main-jar "yourApp.jar" \
//        --main-class "com.example.YourMainClass" \
//        --type dmg \
//        --dest "output/directory"
//
// Alternative: Launch4j Executable Wrapper Files
//
// Fairly up to date: https://gist.github.com/wiverson/5308a91fcb41a59a0b802e7953891ed9
//
//   "apple.awt.showGrowBox"
// "apple.awt.antialiasing"
// "swing.aatext"
//    "apple.awt.brushMetalLook",
// "apple.awt.graphics.EnableQ2DX"
//
//   public static void writeTextToClipboard(String s) {
//     Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
//     Transferable transferable = new StringSelection(s);
//     clipboard.setContents(transferable, null);
// }
//
 //
 //     System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Tregex"); // not any more. Deprecated, use: apple.awt.application.name
//
 //       System.setProperty("apple.awt.application.icon", "/u/manning/git/CoreNLP/doc/tregex/nlp-logo-6x6.icns"); // Deprecated in 2025; use jpackage
//
//         desk.setPreferencesHandler(e -> JOptionPane.showMessageDialog(this, "Preferences for My Java App"));
//
    /*
SwingUtilities.invokeLater(new Runnable() {
    public void run() {
        createAndShowGUI();
    }
});

If you need to determine whether your code is running on the event dispatch thread, invoke javax.swing.SwingUtilities.isEventDispatchThread.

When a Swing program needs to execute a long-running task, it usually uses one of the worker threads, also known as the background threads. Each task running on a worker thread is represented by an instance of javax.swing.SwingWorker. SwingWorker itself is an abstract class; you must define a subclass in order to create a SwingWorker object; anonymous inner classes are often useful for creating very simple SwingWorker objects.

SwingWorker provides a number of communication and control features:

The SwingWorker subclass can define a method, done, which is automatically invoked on the event dispatch thread when the background task is finished.
SwingWorker implements java.util.concurrent.Future. This interface allows the background task to provide a return value to the other thread. Other methods in this interface allow cancellation of the background task and discovering whether the background task has finished or been cancelled.
The background task can provide intermediate results by invoking SwingWorker.publish, causing SwingWorker.process to be invoked from the event dispatch thread.
The background task can define bound properties. Changes to these properties trigger events, causing event-handling methods to be invoked on the event dispatch thread.

Here is the code that defines and executes the SwingWorker object.

SwingWorker worker = new SwingWorker<ImageIcon[], Void>() {
    @Override
    public ImageIcon[] doInBackground() {
        final ImageIcon[] innerImgs = new ImageIcon[nimgs];
        for (int i = 0; i < nimgs; i++) {
            innerImgs[i] = loadImage(i+1);
        }
        return innerImgs;
    }

    @Override
    public void done() {
        //Remove the "Loading images" label.
        animator.removeAll();
        loopslot = -1;
        try {
            imgs = get();
        } catch (InterruptedException ignore) {}
        catch (java.util.concurrent.ExecutionException e) {
            String why = null;
            Throwable cause = e.getCause();
            if (cause != null) {
                why = cause.getMessage();
            } else {
                why = e.getMessage();
            }
            System.err.println("Error retrieving file: " + why);
        }
    }
};


        SwingUtilities.invokeLater( () -> {
        FlatLightLaf.setup();

        // create and show UI
    } );
    if( SystemInfo.isMacFullWindowContentSupported )
    frame.getRootPane().putClientProperty( "apple.awt.transparentTitleBar", true );
    // For merging content into title bar ... complex
    if( SystemInfo.isMacFullWindowContentSupported ) {
    frame.getRootPane().putClientProperty( "apple.awt.fullWindowContent", true );
    frame.getRootPane().putClientProperty( "apple.awt.transparentTitleBar", true );
    frame.getRootPane().putClientProperty( "apple.awt.windowTitleVisible", false );
    frame.getRootPane().putClientProperty( "apple.awt.fullscreenable", true );
     */
