package edu.stanford.nlp.ie.pascal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Container class for aligning acronyms.
 *
 * @author Jamie Nicolson
 */

public class Alignment {
  public char[] longForm;
  public char[] shortForm;
  public int[] pointers;

  public Alignment(char[] longForm, char[] shortForm, int[] pointers) {
    this.longForm = longForm;
    this.shortForm = shortForm;
    this.pointers = pointers;
  }

  public void serialize(PrintWriter writer) {
    writer.println(new String(longForm));
    writer.println(new String(shortForm));
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < pointers.length; ++i) {
      sb.append(pointers[i] + " ");
    }
    writer.println(sb.toString());
  }

  public Alignment(BufferedReader reader) throws IOException {
    String line;
    line = reader.readLine();
    if (line == null) {
      throw new IOException();
    }
    longForm = line.toCharArray();
    line = reader.readLine();
    if (line == null) {
      throw new IOException();
    }
    shortForm = line.toCharArray();
    line = reader.readLine();
    if (line == null) {
      throw new IOException();
    }
    String[] pstrings = line.split("\\s+");
    if (pstrings.length != shortForm.length) {
      throw new IOException("Number of pointers != size of short form");
    }
    pointers = new int[pstrings.length];
    for (int i = 0; i < pointers.length; ++i) {
      pointers[i] = Integer.parseInt(pstrings[i]);
    }
  }

  public void print() {
    System.out.println(toString());
  }

  @Override
  public String toString() {
    return toString("");
  }

  private static final char[] spaces = "                      ".toCharArray();

  public String toString(String prefix) {
    StringBuffer buf = new StringBuffer();
    buf.append(prefix);
    buf.append(longForm);
    buf.append("\n");
    buf.append(spaces, 0, prefix.length());
    int l = 0;
    for (int s = 0; s < shortForm.length; ++s) {
      if (pointers[s] == -1) {
        continue;
      }
      for (; l < longForm.length && pointers[s] != l; ++l) {
        buf.append(" ");
      }
      if (l < longForm.length) {
        buf.append(shortForm[s]);
        ++l;
      }
    }
    return buf.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof Alignment)) {
      return false;
    }
    Alignment cmp = (Alignment) o;

    return Arrays.equals(longForm, cmp.longForm) && Arrays.equals(shortForm, cmp.shortForm) && Arrays.equals(pointers, cmp.pointers);
  }

  @Override
  public int hashCode() {
    int code = 0;
    for (int i = 0; i < pointers.length; ++i) {
      code += pointers[i];
      code *= 31;
    }
    return code;
  }
}
