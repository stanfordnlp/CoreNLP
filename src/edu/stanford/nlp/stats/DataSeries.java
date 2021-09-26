package edu.stanford.nlp.stats;


import edu.stanford.nlp.io.RecordIterator;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.function.IntToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.*;
import java.io.*;

/**
 * A {@code DataSeries} represents a named sequence of {@code double}
 * values, and optionally refers to another {@code DataSeries} as its
 * domain.  Originally designed for making graphs and charts, but probably other
 * uses could be found.
 *
 * This file also contains several {@code DataSeries} implementations as
 * nested static classes:
 *
 * <ul>
 * <li> {@code FunctionDataSeries}, which computes data series values
 * dynamically, according to a function supplied at construction; </li>
 *
 * <li> {@code ArrayDataSeries}, which is backed by an array; </li>
 *
 * <li> {@code ListDataSeries}, which is backed by a list, and includes
 * static methods for reading a data series from a file or input stream; and
 * </li>
 *
 * <li> {@code AverageDataSeries}, which computes data series values
 * dynamically as a linear combination of the value of other data series
 * supplied at construction. </li>
 * </ul>
 *
 * @author Bill MacCartney
 */
public interface DataSeries  {

  /** A logger for this class */
  Redwood.RedwoodChannels log = Redwood.channels(DataSeries.class);

  public String     name();
  public double     get(int i);         // SAFE! if index out of bounds, return (double) i
  public int        size();
  public DataSeries domain();           // can be null; then domain = 0, 1, 2, ...


  // .......................................................................

  public static abstract class AbstractDataSeries implements DataSeries {

    private String   name;
    private DataSeries domain;

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public DataSeries domain() { return domain; }
    public void setDomain(DataSeries domain) { this.domain = domain; }

    public List<Pair<Double, Double>> toListPairDouble() {
      List<Pair<Double, Double>> list = new ArrayList<>();
      for (int i = 0; i < size(); i++) {
        double x = (domain() != null ? domain().get(i) : (double) i);
        double y = get(i);
        list.add(new Pair<>(x, y));
      }
      return list;
    }

  }


  // .......................................................................

  public static class FunctionDataSeries extends AbstractDataSeries {

    private ToIntFunction<Object> sizeFn;
    private IntToDoubleFunction function;

    public FunctionDataSeries(String name,
                              IntToDoubleFunction function,
                              ToIntFunction<Object> sizeFn,
                              DataSeries domain) {
      setName(name);
      this.function = function;
      this.sizeFn = sizeFn;
      setDomain(domain);
    }

    public FunctionDataSeries(String name,
                              IntToDoubleFunction function,
                              ToIntFunction<Object> sizeFn) {
      this(name, function, sizeFn, null);
    }

    public FunctionDataSeries(String name,
                              IntToDoubleFunction function,
                              int size,
                              DataSeries domain) {
      this(name, function, constantSizeFn(size), domain);
    }

    public FunctionDataSeries(String name,
                              IntToDoubleFunction function,
                              int size) {
      this(name, function, size, null);
    }

    public double get(int i) {
      if (i < 0 || i >= size()) return i;
      return function.applyAsDouble(i);
    }

    @Override
    public int size() { return sizeFn.applyAsInt(null); }

    private static ToIntFunction<Object> constantSizeFn(final int size) {
      return o -> size;
    }

  }


  // .......................................................................

  public static class ArrayDataSeries extends AbstractDataSeries {

    private double[] data;

    public ArrayDataSeries(String name) {
      setName(name);
      setData(new double[0]);
    }

    public ArrayDataSeries(String name, double[] data) {
      this(name);
      setData(data);
    }

    public ArrayDataSeries(String name, double[] data, DataSeries domain) {
      this(name, data);
      setDomain(domain);
    }

    public double[] data() { return data; }
    public void setData(double[] data) {
      if (data == null) throw new NullPointerException();
      this.data = data;
    }

    public double get(int i) {
      if (i < 0 || i >= data.length) return i;
      return data[i];
    }
    public void set(int i, double x) {
      if (i < 0 || i >= data.length) return; // no-op
      data[i] = x;
    }

    public int size() { return data.length; }

  }


  // .......................................................................

  public static class ListDataSeries extends AbstractDataSeries {

    private List<Double> data;

    public ListDataSeries(String name) {
      setName(name);
      setData(new ArrayList<>());
    }

    public ListDataSeries(String name, List<Double> data) {
      this(name);
      setData(data);
    }

    public ListDataSeries(String name, List<Double> data, DataSeries domain) {
      this(name, data);
      setDomain(domain);
    }

    public ListDataSeries(String name, DataSeries domain) {
      this(name);
      setDomain(domain);
    }

    public List<Double> data() { return data; }
    public void setData(List<Double> data) {
      if (data == null) throw new NullPointerException();
      this.data = data;
    }

    public double get(int i) {
      if (i < 0 || i >= data.size()) return i;
      return data.get(i);
    }
    public void set(int i, double x) {
      if (i < 0 || i >= data.size()) return; // no-op
      data.set(i, x);
    }
    public void add(double x) { data.add(Double.valueOf(x)); }

    public int size() { return data.size(); }

    /**
     * If a record contains a field that can't be parsed as a double, the whole
     * record is skipped.
     */
    public static DataSeries[] readDataSeries(RecordIterator it, boolean useHeaders) {

      if (!it.hasNext()) return null;
      List<String> record = it.next();  // read first record

      int columns = record.size();
      if (columns < 1) throw new IllegalArgumentException();

      ListDataSeries[] serieses = new ListDataSeries[columns];
      for (int col = 0; col < columns; col++) {
        ListDataSeries series = new ListDataSeries("y" + col);
        if (col == 0) {
          series.setName("x");
        } else {
          series.setDomain(serieses[0]);
        }
        serieses[col] = series;
      }

      if (useHeaders) {                 // first record contains header strings
        for (int i = 0; i < record.size() && i < serieses.length; i++) {
          serieses[i].setName(record.get(i));
        }
        record = it.next();
      }

      while (true) {
        try {
          double[] values = new double[columns];
          for (int col = 0; col < columns; col++) {
            values[col] = Double.valueOf(record.get(col));
          }
          for (int col = 0; col < columns; col++) {
            serieses[col].add(values[col]);
          }
        } catch (NumberFormatException e) {
          // skip whole record
        }
        if (!it.hasNext()) break;
        record = it.next();
      }
      return serieses;
    }

    public static DataSeries[] readDataSeries(InputStream in, boolean useHeaders) {
      return readDataSeries(new RecordIterator(in), useHeaders);
    }

    public static DataSeries[] readDataSeries(InputStream in) {
      return readDataSeries(new RecordIterator(in), false);
    }

    public static DataSeries[] readDataSeries(String filename, boolean useHeaders) throws FileNotFoundException {
      return readDataSeries(new RecordIterator(filename), useHeaders);
    }

    public static DataSeries[] readDataSeries(String filename) throws FileNotFoundException {
      return readDataSeries(new RecordIterator(filename), false);
    }

    public static void main(String[] args) throws FileNotFoundException {

      DataSeries[] serieses = null;

      if (args.length > 0) {
        serieses = readDataSeries(args[0], true);
      } else {
        log.info("[Reading from stdin...]");
        serieses = readDataSeries(System.in, true);
      }

      for (DataSeries series : serieses) {
        System.out.print(series.name() + ": ");
        System.out.println(((ListDataSeries) series).toListPairDouble());
      }

    }

    @SuppressWarnings("unused")
    private static void demo1() {

      ListDataSeries xData = new ListDataSeries("x");
      ListDataSeries yData = new ListDataSeries("y", xData);
      for (double x = 0.0; x < 5.0; x++) {
        xData.add(x);
        yData.add(x * x);
      }

      System.out.println(yData.toListPairDouble());

    }

  }


  // .......................................................................

  public static class AverageDataSeries implements DataSeries {

    private DataSeries[] components;

    public AverageDataSeries(DataSeries[] components) {
      if (components == null || components.length < 1)
        throw new IllegalArgumentException("Need at least one component!");
      this.components = new DataSeries[components.length];
      for (int i = 0; i < components.length; i++) {
        if (components[i] == null)
          throw new IllegalArgumentException("Can't have null components!");
        this.components[i] = components[i];
      }
      domain();                         // to ensure domains are same
    }

    public String name() {
      StringBuilder name = new StringBuilder();
      name.append("avg(");
      boolean flag = false;
      for (DataSeries series : components) {
        if (flag) name.append(", "); else flag = true;
        name.append(series.name());
      }
      name.append(")");
      return name.toString();
    }

    public double get(int i) {
      double y = 0.0;
      for (DataSeries series : components)
        y += series.get(i);
      return y / components.length;
    }

    public int size() {
      int size = Integer.MAX_VALUE;
      for (DataSeries series : components)
        size = Math.min(size, series.size());
      return size;
    }

    public DataSeries domain() {
      DataSeries domain = components[0].domain(); // could be null
      for (DataSeries series : components)
        if (series.domain() != domain)
          throw new IllegalStateException("The components of this AverageDataSeries do not have the same domains!");
      return domain;
    }

    @Override
    public String toString() { return name(); }

  }

}
