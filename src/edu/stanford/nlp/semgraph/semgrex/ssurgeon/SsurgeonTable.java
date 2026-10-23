package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A lookup table for {@link Remap}, from each key to one value.
 *<br>
 * In an edit file, a table is a block whose first line is
 * {@code table name}, followed by one {@code key<TAB>value} line per
 * entry.  A key or value may contain spaces, so the tab is required.
 *<br>
 * The entries may form a chain, such as {@code a} to {@code b} and
 * {@code b} to {@code c}, which iterate follows to the end.  They may
 * not form a cycle, such as {@code a} to {@code b} and {@code b} to
 * {@code a}, as iterate would go around it forever, and for the same
 * reason no key may map to itself.
 */
public class SsurgeonTable {
  /** The command word which starts a table block in an edit file */
  public static final String LABEL = "table";

  private final String name;
  private final Map<String, String> entries;

  /**
   * Creates a table.
   *
   * @param name the name edits use for the table
   * @param entries the entries, which are copied in order
   * @throws SsurgeonParseException if the name is empty, or the entries form a cycle
   */
  public SsurgeonTable(String name, Map<String, String> entries) {
    if (name == null || name.isEmpty()) {
      throw new SsurgeonParseException("A table needs a name");
    }
    this.name = name;
    this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    checkCycles();
  }

  /**
   * Reads a table from the lines of an edit file block after its {@code table name} line.
   *
   * @param name the name of the table
   * @param lines the entries, one {@code key<TAB>value} per line
   * @return the table
   * @throws SsurgeonParseException if a line has no tab, a key is given two different
   *   values, or the entries form a cycle
   */
  public static SsurgeonTable fromLines(String name, List<String> lines) {
    Map<String, String> entries = new LinkedHashMap<>();
    for (String line : lines) {
      int tab = line.indexOf('\t');
      if (tab < 0) {
        throw new SsurgeonParseException("Table " + name + " has a line with no tab between its key and value: |" + line + "|");
      }
      String key = line.substring(0, tab).trim();
      String value = line.substring(tab + 1).trim();
      if (key.isEmpty()) {
        throw new SsurgeonParseException("Table " + name + " has a line with no key: |" + line + "|");
      }
      String previous = entries.put(key, value);
      if (previous != null && !previous.equals(value)) {
        throw new SsurgeonParseException("Table " + name + " maps " + key + " to both " + previous + " and " + value);
      }
    }
    return new SsurgeonTable(name, entries);
  }

  /** Rejects entries which lead back to a key already followed */
  private void checkCycles() {
    for (String start : entries.keySet()) {
      Set<String> seen = new HashSet<>();
      String key = start;
      while (entries.containsKey(key)) {
        if (!seen.add(key)) {
          throw new SsurgeonParseException("Table " + name + " maps " + start + " around a cycle back to " + key +
                                           ", which iterate would follow forever");
        }
        key = entries.get(key);
      }
    }
  }

  /**
   * Returns the name edits use for this table.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Looks up a key.
   *
   * @param key the key
   * @return its value, or null if the table has no such key
   */
  public String get(String key) {
    return entries.get(key);
  }

  /**
   * Returns the entries of this table, in the order they were given.
   *
   * @return an unmodifiable view of the entries
   */
  public Map<String, String> entries() {
    return entries;
  }

  @Override
  public String toString() {
    return LABEL + " " + name + " (" + entries.size() + " entries)";
  }
}
