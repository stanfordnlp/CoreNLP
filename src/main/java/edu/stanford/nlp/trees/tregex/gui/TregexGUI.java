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
import edu.stanford.nlp.util.logging.Redwood;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
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
import java.lang.reflect.Method;
import java.util.*;

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

/**
 * Main class for creating a tregex gui.  Manages the components and holds the menu bar.
 * A tregex gui (Interactive Tregex) allows users to perform tregex searches in a gui interface
 * and view the results of those searches.  Search results may be saved.
 *
 * @author Anna Rafferty
 */
@SuppressWarnings("serial")
public class TregexGUI extends JFrame implements ActionListener, MatchesPanelListener  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TregexGUI.class);

  private static TregexGUI instance; // = null;

  private JMenuItem preferences;
  private JMenuItem loadFiles;
  private JMenuItem saveMatches;
  private JMenuItem saveSentences;
  private JMenuItem saveHistory;
  private JMenuItem loadTsurgeon;
  private JMenuItem tDiff;
  private JMenuItem quit; //for when we're not running on a mac
  private JMenuItem copy;
  private JMenuItem searchMenuItem;
  private JMenuItem prevMatch;
  private JMenuItem nextMatch;
  private JMenuItem prevTreeMatch;
  private JMenuItem nextTreeMatch;
  private JMenuItem clearFileList;

  //file choosing components for loading trees
  private JFileChooser chooser; // = null;

  final TreeTransformer transformer;

  //preferences, about panel so that we don't have to remake each time
  private PreferencesPanel preferenceDialog; // = null;
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
    if ( ! isMacOSX()) {
      file.addSeparator();
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
    if ( ! isMacOSX()) {
      tools.add(preferences);
    }
    tools.add(tDiff);

    mbar.add(file);
    mbar.add(edit);
    mbar.add(search);
    mbar.add(tools);

    setShortcutKeys(); //sets for appropriate operating system

    loadPreferences();

    return mbar;
  }

  private void setShortcutKeys() {
    if (isMacOSX()) {
      setMacShortcutKeys();
    } else {
      setWindowsShortcutKeys();
    }
  }

  private void setMacShortcutKeys() {
    preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.META_MASK));
    loadFiles.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.META_MASK));
    saveMatches.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_MASK));
    saveHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_MASK+InputEvent.META_MASK));
    quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.META_MASK));
    copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_MASK));

    searchMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_MASK));
    prevMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.META_MASK));
    nextMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.META_MASK));
    prevTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_MASK | InputEvent.META_DOWN_MASK));
    nextTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_MASK | InputEvent.META_MASK));

  }

  private void setWindowsShortcutKeys() {
    // preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, Event.CTRL_MASK)); // cdm: just skip this, I don't think Windows ever uses comma like this
    loadFiles.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK));
    saveMatches.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK));
    saveHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_MASK+InputEvent.CTRL_MASK));
    quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_MASK)); // cdm: maybe should be Control or Alt F4
    copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK));

    searchMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK));
    prevMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_MASK));
    nextMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_MASK));
    prevTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK));
    nextTreeMatch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK));

  }

  private void initAboutBox() {
    aboutBox = new JDialog(this, "About Tregex");
    aboutBox.getContentPane().setLayout(new BorderLayout());
    aboutBox.getContentPane().add(new JLabel("<html><b>Tregex and Tsurgeon</b></html>", SwingConstants.CENTER), BorderLayout.NORTH);

    aboutBox.getContentPane().add(new JLabel("<html>Tregex by Galen Andrew and Roger Levy<br>Tsurgeon by Roger Levy<br>Graphical interface by Anna Rafferty<br>Additional features and development by Chris Manning<br></html>", SwingConstants.CENTER), BorderLayout.CENTER);
    aboutBox.getContentPane().add(new JLabel("<html><font size=2>\u00A92007 The Board of Trustees of The Leland Stanford Junior University.<br>Distributed under the GNU General Public License</font></html>", SwingConstants.CENTER), BorderLayout.SOUTH);

  }

  /**
   * Used to change the status of the save file menu item to reflect
   * whether any trees are available to save.
   */
  public void setSaveEnabled(boolean enabled) {
    if(saveMatches.isEnabled() != enabled) {
      saveMatches.setEnabled(enabled);
      saveSentences.setEnabled(enabled);
    }
  }

  /**
   * Used to change the status of the saveHistory file menu item to reflect
   * whether any search statistics are available to save
   */
  public void setSaveHistoryEnabled(boolean enabled) {
    if(saveHistory.isEnabled() != enabled)
      saveHistory.setEnabled(enabled);
  }

  /**
   * Used to change the status of the tsurgeon file menu item to reflect
   * whether tsurgeon is enabled
   */
  public void setTsurgeonEnabled(boolean enabled) {
    if (loadTsurgeon.isEnabled() != enabled)
      loadTsurgeon.setEnabled(enabled);
  }

  private static void setMacProperties() {
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Tregex");
    System.setProperty("apple.laf.useScreenMenuBar", "true");

  }

  public static boolean isMacOSX() {
    return System.getProperty("os.name").toLowerCase().startsWith("mac os x");
  }

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
    Container content = getContentPane();
    content.setBackground(Color.lightGray);
    // NB: The menu has to exist before you can successfully create an input.  Bad side effect dependency!
    JMenuBar mbar = getMenu();
    JPanel displayMatchesPanel = DisplayMatchesPanel.getInstance();
    JSplitPane inputAndMatchesPanel = setUpTopPanels();
    MatchesPanel.getInstance().addListener(this);
    this.setFocusTraversalKeysEnabled(true);
    macOSXRegistration();
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

    if (initialFiles.size() > 0) {
      File[] files = new File[initialFiles.size()];
      for (int i = 0; i < initialFiles.size(); ++i) {
        files[i] = new File(initialFiles.get(i));
      }
      startFileLoadingThread(new EnumMap<>(FilterType.class), files);
    }

    setVisible(true);
  }

  // Generic registration with the Mac OS X application menu.  Checks the platform, then attempts
  // to register with the Apple EAWT.
  // This is based heavily on the Apple sample code for dealing with this issue
  private void macOSXRegistration() {
    if (isMacOSX()) {
      try {
        Class<?> osxAdapter = ClassLoader.getSystemClassLoader().loadClass("edu.stanford.nlp.trees.tregex.gui.OSXAdapter");

        Class<?>[] defArgs = {TregexGUI.class};
        Method registerMethod = osxAdapter.getDeclaredMethod("registerMacOSXApplication", defArgs);
        if (registerMethod != null) {
          Object[] args = { this };
          registerMethod.invoke(osxAdapter, args);
        }
        defArgs[0] = boolean.class;
        Method prefsEnableMethod =  osxAdapter.getDeclaredMethod("enablePrefs", defArgs);
        if (prefsEnableMethod != null) {
          Object[] args = {Boolean.TRUE};
          prefsEnableMethod.invoke(osxAdapter, args);
        }
      } catch (NoClassDefFoundError e) {
        // This will be thrown first if the OSXAdapter is loaded on a system without the EAWT
        // because OSXAdapter extends ApplicationAdapter in its def
        log.info("This version of Mac OS X does not support the Apple EAWT.  Application Menu handling has been disabled (" + e + ")");
      } catch (ClassNotFoundException e) {
        // This shouldn't be reached; if there's a problem with the OSXAdapter we should get the
        // above NoClassDefFoundError first.
        log.info("This version of Mac OS X does not support the Apple EAWT.  Application Menu handling has been disabled (" + e + ")");
      } catch (Exception e) {
        log.info("Exception while loading the OSXAdapter:");
        e.printStackTrace();
      }
    }
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
    if(encoding != null && !encoding.equals(""))
      FileTreeModel.setCurEncoding(encoding);
    if (PreferencesPanel.isChinese(hfName, trfName))
      setChineseFont();
    else if (PreferencesPanel.isArabic(hfName, trfName))
      setArabicFont();

    if (preferenceDialog == null)
      preferenceDialog = new PreferencesPanel(this);
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

        if(!fontName.equals("")) {
          DisplayMatchesPanel.getInstance().setFontName(fontName);
          MatchesPanel.getInstance().setFontName(fontName);
        }
      }
    };
    t.start();
  }

  private static void setArabicFont() {
    Thread t = new Thread() {
      @Override
      public void run() {
        List<Font> fonts = FontDetector.supportedFonts(FontDetector.ARABIC);
        String fontName = "";
        if (fonts.size() > 0) {
          fontName = fonts.get(0).getName();
        }
        if(!fontName.equals("")) {
          DisplayMatchesPanel.getInstance().setFontName(fontName);
          MatchesPanel.getInstance().setFontName(fontName);
        }
      }
    };
    t.start();
  }

  /*
   * Method for bringing up the load file dialog box and conveying
   * the chosen files to the filepanel
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

  public static class TransferActionListener implements ActionListener, PropertyChangeListener {

    private JComponent focusOwner; // = null;

    //This code based on Java DnD tutorial
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
    //System.out.println("Doing file filters");

    final File[] cFiles = files;
    final JPanel fileFilterPanel = new JPanel();
    fileFilterPanel.setLayout(new BoxLayout(fileFilterPanel, BoxLayout.PAGE_AXIS));
    JLabel text = new JLabel("<html>Please indicate any constraints on the files you want to load. All files in specified folders that satisfy all of the given constraints will be loaded. Just press Okay to load all files.</html>");
    //text.setBorder(BorderFactory.createLineBorder(Color.black));
    text.setAlignmentX(SwingConstants.LEADING);
    JPanel textPanel = new JPanel(new BorderLayout());
    textPanel.setPreferredSize(new Dimension(100,50));
    //textPanel.setBorder(BorderFactory.createLineBorder(Color.black));
    textPanel.add(text);
    fileFilterPanel.add(textPanel);
    fileFilterPanel.add(Box.createVerticalStrut(5));
    Box defaultFilter = getNewFilter();
    //defaultFilter.setBorder(BorderFactory.createLineBorder(Color.black));
    //fileFilterPanel.setBorder(BorderFactory.createLineBorder(Color.black));
    fileFilterPanel.add(defaultFilter);
    final JOptionPane fileFilterDialog = new JOptionPane();
    fileFilterDialog.setMessage(fileFilterPanel);
    JButton[] options = new JButton[3];
    JButton okay = new JButton("Okay");

    JButton add = new JButton("Add another filter");
    JButton cancel = new JButton("Cancel");
    options[0] = okay;
    options[1] = add;
    options[2] = cancel;

    fileFilterDialog.setOptions(options);

    final JDialog dialog = fileFilterDialog.createDialog(null, "Set file filters...");
    okay.addActionListener(arg0 -> {
      // first check if we have a file range option and make sure it's valid
      final EnumMap<FilterType,String> filters = getFilters(fileFilterPanel);
      if (filters.containsKey(FilterType.isInRange)) {
        try {
          // if we can creat it, then it's not invalid!
          new NumberRangesFileFilter(filters.get(FilterType.isInRange), false);
        } catch(Exception e) {
          JOptionPane.showMessageDialog(dialog, new JLabel("<html>Please check the range you specified for the file number.  Ranges must be numerical, and disjoint <br>ranges should be separated by commas.  For example \"1-200,250-375\" is a valid range.</html>"), "Error in File Number Range", JOptionPane.ERROR_MESSAGE);
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

   public enum FilterType {
    none("None"),
    hasExtension("Has extension: "),
    hasPrefix("Has prefix: "),
//    hasNumGreaterThan("Has number greater than: "),
//    hasNumLessThan("Has number less than: ");
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
    if(status == JFileChooser.APPROVE_OPTION) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            BufferedReader reader = new BufferedReader(new FileReader(chooser.getSelectedFile().toString()));
            final String tregexPatternString = Tsurgeon.getTregexPatternFromReader(reader);
            final String tsurgeonOperationsString = Tsurgeon.getTsurgeonTextFromReader(reader);
            SwingUtilities.invokeLater(() -> InputPanel.getInstance().setScriptAndPattern(tregexPatternString, tsurgeonOperationsString));
          } catch (IOException e) {
            System.out.println("Error parsing Tsurgeon file");
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
  private boolean doTdiff = false;
  public static final int MAX_TDIFF_TREEBANKS = 2;

  private void doTdiff() {
    doTdiff = !doTdiff;
    ((JCheckBoxMenuItem) tDiff).setState(doTdiff);

    // Only allow 2 active treebanks
    if(doTdiff) {
      List<FileTreeNode> activeTreebanks = FilePanel.getInstance().getActiveTreebanks();
      for(int i = 2; i < activeTreebanks.size(); i++)
        activeTreebanks.get(i).setActive(false);
    }
  }

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

  public void doClearFileList() {
    FilePanel.getInstance().clearAll();
    clearFileList.setEnabled(false);
  }

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
   */
  public static void main(String[] args) {
    if (isMacOSX()) {
      setMacProperties();
    } else {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
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
    new TregexGUI(props, filenames);
  }


  public void about() {
    aboutBox.setSize(360, 240);
    aboutBox.setLocation((int)this.getLocation().getX() + 22, (int)this.getLocation().getY() + 22);
    aboutBox.setResizable(false);
    aboutBox.setVisible(true);
  }

}
