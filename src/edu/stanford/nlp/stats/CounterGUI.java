package edu.stanford.nlp.stats;

import java.awt.BorderLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import edu.stanford.cs.ra.RA;
import edu.stanford.cs.ra.arguments.Argument;
import edu.stanford.cs.ra.arguments.ArgumentPolicy;
import edu.stanford.cs.ra.stringify.Stringify;

/**
 * Helper class for interactively playing with maps and counters.
 *
 * @author dramage
 */
public class CounterGUI {

  private CounterGUI() {
  }


  /** Table model backed by a list of counters */
  private static class MapTableModel<E> extends AbstractTableModel {
    /** List of counters to show counters from? */
    private final List<? extends Map<E,? extends Number>> maps;

    /** Sorted list of all keys in the counter */
    private final List<E> keys;

    public MapTableModel(List<? extends Map<E,? extends Number>> maps) {
      this.maps = maps;

      Set<E> keys = new TreeSet<E>();
      for (Map<E,? extends Number> map : maps) {
        for (E key : map.keySet()) {
          keys.add(key);
        }
      }

      this.keys = new ArrayList<E>(keys);
    }

    public int getColumnCount() {
      return maps.size()+1;
    }

    public int getRowCount() {
      return this.keys.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        return keys.get(rowIndex);
      } else {
        Map<E,? extends Number> map = maps.get(columnIndex-1);
        E key = keys.get(rowIndex);
        if (map.containsKey(key)) {
          return map.get(key);
        } else {
          return null;
        }
      }
    }

    @Override
    public Class<?> getColumnClass(int col) {
      if (col > 0 && maps.get(col - 1).size() > 0) {
        return maps.get(col - 1).values().iterator().next().getClass();
      } else {
        return Object.class;
      }
    }

    private static final long serialVersionUID = 1L;
  }


  /**
   * Returns a JTable that provides a view on the contents of the given
   * counters.  The returned view makes no guarantees as to what happens
   * if the values in the counters change.  Use showCounters to display
   * this table in a JFrame.
   */
  public static <E> JTable getTableForCounters(List<Counter<E>> counters) {
    LinkedList<Map<E,? extends Number>> maps = new LinkedList<Map<E,? extends Number>>();
    for (Counter<E> counter : counters) {
      maps.add(Counters.asMap(counter));
    }
    return getTableForMaps(maps);
  }

  /**
   * Returns a JTable that provides a view on the contents of the given
   * counters.  The returned view makes no guarantees as to what happens
   * if the values in the counters change.  Use showCounters to display
   * this table in a JFrame.
   */
  public static <E,M extends Map<E,? extends Number>> JTable getTableForMaps(List<M> maps) {
    JTable table = new JTable(new MapTableModel<E>(maps));
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

    // if Java >= 1.6, enable sorting;  TODO: fix this after leaving stone age
    try {
      String[] version = System.getProperty("java.version").split("\\.");
      if (Integer.parseInt(version[0])>=1 && Integer.parseInt(version[1])>=6) {
        table.getClass().getMethod("setAutoCreateRowSorter", Boolean.TYPE)
          .invoke(table, true);
        table.getClass().getMethod("setFillsViewportHeight", Boolean.TYPE)
          .invoke(table, true);
      }
    } catch (Exception e) {
      // couldn't enable sorting - sail a vee
    }

    return table;
  }

  /**
   * Shows a JFrame containing a view of the given counters.  The returned
   * view makes no guarantees as to what happens if the values in the
   * counters change.
   */
  public static <E> void showCounters(final String title,
      final List<Counter<E>> counters) {

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JScrollPane scrolled = new JScrollPane(getTableForCounters(counters));

        final JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(scrolled, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
      }
    });
  }

  /**
   * Shows a JFrame containing a view of the given counters.  The returned
   * view makes no guarantees as to what happens if the values in the
   * counters change.
   */
  public static <E,M extends Map<E,? extends Number>> void showMaps(final String title,
      final List<M> counters) {

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JScrollPane scrolled = new JScrollPane(getTableForMaps(counters));

        final JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(scrolled, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
      }
    });
  }

  /**
   * Shows a JFrame containing a view of the given counters.  The returned
   * view makes no guarantees as to what happens if the values in the
   * counters change.
   */
  public static <E,M extends Map<E,? extends Number>> void showMaps(final String title, final M ... counters) {
    showMaps(title, Arrays.asList(counters));
  }

  /**
   * Temporary constructor ignoring types for scala compatibility.
   */
  @SuppressWarnings("unchecked")
  public static void showMapsScala(String title, Object[] counters) {
    showMaps(title, (List)Arrays.asList(counters));
  }

  //
  // For command line invocation
  //

  private enum CounterGUIAction {
    show, topKcounts, topKtfidf, topKnormed, similarity
  }

  @Argument("List of counters to display")
  @Argument.Policy(ArgumentPolicy.REQUIRED)
  @Argument.Name("CounterGUI:CounterFiles")
  @Argument.Switch("--counters")
  private static File[] counterFiles;

  @Argument("Type of loaded counter")
  @Argument.Default("java.lang.String")
  @Argument.Name("CounterGUI:CounterType")
  @Argument.Switch("--type")
  private static Class<?> counterType;

  @Argument("Window title")
  @Argument.Default("Counters")
  @Argument.Name("CounterGUI:WindowTitle")
  @Argument.Switch("--title")
  private static String windowTitle;

  @Argument("What actions to take on the loaded counters")
  @Argument.Default("show")
  @Argument.Name("CounterGUI:Actions")
  @Argument.Switch("--actions")
  private static CounterGUIAction[] actions;

  @Argument("How many top counters should we show for topK actions")
  @Argument.Default("5")
  @Argument.Name("CounterGUI:TopK")
  @Argument.Switch("--topk")
  private static int topK;

  /**
   * Simple test main method constructs and displays a dinky counter.
   */
  @SuppressWarnings("unchecked")
  public static <E> void main(String[] argv) {
    RA.begin(argv, CounterGUI.class);

    Arrays.sort(counterFiles);

    List<Counter<E>> counters
      = new ArrayList<Counter<E>>(counterFiles.length);
    List<File> files = Arrays.asList(counterFiles);

    for (File file : counterFiles) {
      Counter<E> counter =
        Counters.loadCounter(file.getPath(), (Class)counterType);
      counters.add(counter);
    }

    for (CounterGUIAction action : actions) {
      switch (action) {
      case topKcounts:
        for (int i = 0; i < counters.size(); i++) {
          System.out.println(files.get(i).getName() + " : " +counters.get(i).totalCount() + " : "+
              Counters.toBiggestValuesFirstString(counters.get(i), topK));
        }
        break;

      case topKtfidf:
        List<Map<E,Double>> asMaps = new ArrayList<Map<E,Double>>(counters.size());
        for (Counter<E> counter : counters) {
          asMaps.add(Counters.asMap(new ClassicCounter<E>(counter)));
        }

        BagUtils.weightByTFIDF(asMaps);
        for (int i = 0; i < counters.size(); i++) {
          System.out.println(files.get(i).getName() + " : " +
              Counters.toBiggestValuesFirstString(Counters.fromMap(asMaps.get(i)), topK));
        }
        break;

      case topKnormed:
        // each gets normalized across word counters
        Counter<E> total = new ClassicCounter<E>();
        for (Counter<E> counter : counters) {
          Counters.addInPlace(total, counter);
        }
        List<Counter<E>> newCounters = new LinkedList<Counter<E>>();
        for (Counter<E> counter : counters) {
          ClassicCounter<E> newCounter = new ClassicCounter<E>();
          newCounters.add(newCounter);
          for (E e : counter.keySet()) {
            newCounter.setCount(e, 1.0 / ((1.0 / Math.log(counter.getCount(e))) + (1.0 / (counter.getCount(e) / total.getCount(e)))));
          }
        }
        for (int i = 0; i < counters.size(); i++) {
          System.out.println(files.get(i).getName() + " : " +
              Counters.toBiggestValuesFirstString(newCounters.get(i), topK));
        }
        break;

      case similarity:
        // generate a similarities table
        double[][] similarities = new double[counters.size()][counters.size()];
        for (int i = 0; i < similarities.length; i++) {
          final Counter<E> a = counters.get(i);
          similarities[i][i] = 1;
          for (int j = i+1; j < similarities.length; j++) {
            final Counter<E> b = counters.get(j);
            similarities[i][j] = similarities[j][i] = Counters.dotProduct(a, b)
              / (a.totalCount() * b.totalCount());
          }
        }
        System.out.println(Stringify.toString(similarities));
        break;

      case show:
        showCounters(windowTitle, counters);
        break;

      default:
        throw new UnsupportedOperationException("Unrecognized action: "+action);
      }
    }
  }
}
