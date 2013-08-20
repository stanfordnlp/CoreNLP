package edu.stanford.nlp.ie.ner.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import edu.stanford.nlp.objectbank.ObjectBank;

/**
 * Table model for displaying NERResult objects in a JTable.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class NERResultTableModel extends AbstractTableModel {
  /**
   * 
   */
  private static final long serialVersionUID = -1231978578320348190L;
  private static final int ID_COLUMN = 0;
  private static final int TEXT_COLUMN = 1;
  private static final int TP_COLUMN = 2;
  private static final int FP_COLUMN = 3;
  private static final int FN_COLUMN = 4;

  private String[] columnNames = new String[]{"ID", // document ID
                                              "Text", // a fragment of the document text
                                              "TP", // true positives
                                              "FP", // false positives
                                              "FN", // false negatives
  };

  private List results;
  private List filtered; // the filtered results
  private HashMap resultMap; // map from id to results

  public NERResultTableModel() {
    // initalize to empty arry so we don't have to special case
    results = new ArrayList();
    filtered = results;
  }

  /**
   * Uses the given messages for the table model.
   */
  public void setResults(List results) {
    this.results = results;
    resultMap = new HashMap();
    for (int i = 0; i < results.size(); i++) {
      resultMap.put((((NERResult) results.get(i)).getID()), results.get(i));
    }
    setFilter(null);
    fireTableDataChanged();
  }

  /**
   * Returns the appropriate column value for the row'th parsed message.
   * <H4>Columns:</H4>
   * <OL START="0">
   * <LI>ID: document ID
   * <LI>Text: text fragment from the message
   * <LI>TP: # true positives
   * <LI>FP: # false positives
   * <LI>FN: # false negatives
   * </OL>
   */
  public Object getValueAt(int row, int col) {
    NERResult result = (NERResult) filtered.get(row);

    Object value;
    switch (col) {
      case ID_COLUMN:
        value = result.getID();
        break;
      case TEXT_COLUMN:
        value = result.getText();
        break;
      case TP_COLUMN:
        value = Integer.valueOf(result.getTP());
        break;
      case FP_COLUMN:
        value = Integer.valueOf(result.getFP());
        break;
      case FN_COLUMN:
        value = Integer.valueOf(result.getFN());
        break;
      default:
        value = null;
    }
    return (value);
  }

  /**
   * Returns false for all cells.
   */
  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return (false);
  }

  /**
   * Returns the class of the first non-null cell in the given column, or Object.class if none.
   */
  @Override
  public Class getColumnClass(int columnIndex) {
    for (int r = 0; r < getRowCount(); r++) {
      if (getValueAt(r, columnIndex) != null) {
        return (getValueAt(r, columnIndex).getClass());
      }
    }
    return (Object.class); // default
  }

  /**
   * Returns the number of parsed messages being displayed.
   */
  public int getRowCount() {
    return (filtered.size());
  }

  /**
   * Returns the number of columns being displayed.
   */
  public int getColumnCount() {
    return (columnNames.length);
  }

  /**
   * Returns the name of the column at the given index.
   *
   * @see #getValueAt
   */
  @Override
  public String getColumnName(int columnIndex) {
    return (columnNames[columnIndex]);
  }

  /**
   * Returns the number of (filtered) documents displayed.
   */
  public int getNumDocuments() {
    return getRowCount();
  }

  /**
   * Gets the result for the document at the given index.
   */
  public NERResult getResult(int index) {
    return ((NERResult) filtered.get(index));
  }

  /**
   * Returns the ID of the document at the given index.
   */
  public String getDocumentID(int index) {
    return (((NERResult) filtered.get(index)).getID());
  }

  /**
   * Returns whether the result at the given index has been viewed or not.
   */
  public boolean hasBeenViewed(int index) {
    return ((NERResult) filtered.get(index)).hasBeenViewed();
  }

  /**
   * Declares that the result at the given index has been viewed.
   */
  public void markAsViewed(int index) {
    ((NERResult) filtered.get(index)).markAsViewed();
  }

  /**
   * Loads the given evaluation file.  Assumes the file is in the format
   * produced by the BioCreative evaluation script:
   * <pre>
   * (FP,FN)|ID|start end|correct answer      or
   * (TP)|ID|alternative answer|start end|best answer|start end
   * </pre>
   */
  public int loadEvalFile(File file) {
    int numMatches = 0;
    try {
      for (String line : ObjectBank.getLineIterator(file)) {
        String[] columns = line.split("\\|", 6);
        if (columns.length < 4) {
          break;
        }

        String id = columns[1];
        NERResult result = (NERResult) resultMap.get(id);
        if (result != null) {
          int type = NERMatch.getType(columns[0]);
          int indexColumn; // the column containing the indices of the match
          if (type == NERMatch.TP) {
            indexColumn = 3;
          } else {
            indexColumn = 2;
          }
          int index = columns[indexColumn].indexOf(' ');
          if (index != -1) {
            int start = Integer.parseInt(columns[indexColumn].substring(0, index));
            int end = Integer.parseInt(columns[indexColumn].substring(index + 1));
            NERMatch match = new NERMatch(type, start, end);
            result.addMatch(match);
            numMatches++;
          } else {
            System.err.println("Could not recognize start/end index");
          }
        } else {
          System.err.println("Could not find id " + id);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    fireTableDataChanged();
    return (numMatches);
  }

  /**
   * Loads given test file.  Assumes file is a tokenized Biocreative file of the format:
   * <pre>
   * &#64;&#64;id Document 1
   * &#64;&#64;id Document 2
   * ...
   * <pre>
   */
  public int loadTestFile(File file) {
    ArrayList results = new ArrayList();
    try {
      for(String line : ObjectBank.getLineIterator(file)) {
        if (line.startsWith("@@")) {
          // find the first space
          int index = line.indexOf(' ');
          if (index != -1) {
            String id = line.substring(2, index);
            String text = line.substring(index + 1);
            NERResult result = new NERResult(id);
            result.setText(text);
            results.add(result);
          } else {
            break;
          }
        } else {
          break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    setResults(results);
    return (results.size());
  }

  /**
   * Replaces the current filter with the given filter, and updates the table.
   */
  public void setFilter(NERResultFilter filter) {
    // if there is no filter, just set the filtered results to the complete list of results
    if (filter == null) {
      filtered = results;
    } else {
      filtered = new ArrayList();
      for (Iterator iter = results.iterator(); iter.hasNext();) {
        NERResult result = (NERResult) iter.next();
        if (!filter.filter(result)) {
          filtered.add(result);
        }
      }
    }
    fireTableDataChanged();
  }
}
