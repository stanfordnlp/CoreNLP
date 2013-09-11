package edu.stanford.nlp.ling;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.objectbank.ReaderIteratorFactory;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

/**
 * Basic implementation of DataCollection that should be suitable for most needs.
 * BasicDataCollection is a List of Datum's with convenience methods for a
 * variety of functions like reading in documents, getting doc stats, and splitting
 * up a collection.
 * <p/>
 * NOTE: This class doesn't try very hard to prevent adding non-Datum objects.
 *
 * @author Sepandar Kamvar
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author nmramesh@cs.stanford.edu. {@link #readSVMLightFormat(String)}
 */
public class BasicDataCollection<L, F> extends ArrayList<Datum<L, F>> implements DataCollection<L, F> {

  private static final long serialVersionUID = -7836249640130378128L;

  //protected Matrix datamatrix;
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  protected List<F> features;
  protected String name;
  /**
   * Constructs a new empty BasicDataCollection.
   */
  public BasicDataCollection() {
  }

  /**
   * Constructs a new BasicDataCollection with the given List of Datums.
   */
  public BasicDataCollection(List<Datum<L, F>> datums) {
    addAll(datums);
  }

  /**
   * Adds the given Datum to this collection and returns the resulting collection size.
   */
  public int addIndex(Datum<L, F> d) {
    super.add(d);
    return size();
  }

  /**
   * Returns the ith Datum in this DataCollection.
   */
  public Datum<L, F> getDatum(int i) {
    return get(i);
  }

  /** Returns the data matrix. */
  //public Matrix dataMatrix() { return datamatrix;}

  /**
   * Returns a List of the primary labels in each Datum.
   * Some or all of the elements may be null (if that Datum
   * had no label assigned).
   */
  public List<L> labels() {
    List<L> labels = new ArrayList<L>();
    for (int i = 0; i < size(); i++) {
      labels.add(get(i).label());
    }
    return labels;
  }

  /**
   * Returns an Index (indexed vocabulary) for the primary labels of all the
   * Datums in this DataCollection.
   */
  public Index<L> labelIndex() {
    return (new HashIndex<L>(labels()));
  }

  /**
   * Returns a List of Features and their corresponding indices.
   */
  public List<F> features() {
    return new ArrayList<F>(computeFeatureCounts().keySet());
  }

  /**
   * Returns an Index (indexed vocabulary) for the features of all the
   * Datums in this DataCollection.
   */
  public Index<F> featureIndex() {
    return (new HashIndex<F>(features()));
  }

  /**
   * Returns the name of this DataCollection.
   */
  public String name() {
    return name;
  }

  /**
   * Sets the name of this DataCollection.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns a detailed String representation of this DataCollection.
   */
  @Override
  public String toString() {
    //        return("Name: " + name + "\nData:\n"+ super.toString() + "\nFeatures:\n"+features+"\nDataMatrix:\n"+datamatrix.toMatlabSparseString());
    return ("Name: " + name + "\nData:\n" + super.toString() + "\nFeatures:\n" + features);

  }

  /**
   * Returns an XML String representation of DataCollection.
   */
  public String toXMLString() {
    //return("<name>" + name + "</name>\n<data>\n"+ super.toString() + "\n</data>\n<features>\n"+features+"\n</features>\n<data matrix>\n"+datamatrix.toMatlabSparseString()+"\n</data matrix>");
    return ("<name>" + name + "</name>\n<data>\n" + super.toString() + "\n</data>\n<features>\n" + features + "\n</features>\n");

  }

  /**
   * Returns a new DataCollection of the same subclass this method was called
   * on, or <tt>null</tt> if the subclass cannot be instantiated. Your subclass
   * must have a blank constructor for this to work. Subclasses that maintain
   * additional state should override this method, get the blank data collection
   * (from a super call) then cast and add their addition data, provided the
   * super call doesn't return null. This is primarily used for splitting
   * data collections. Default implementation copies the name of the collection.
   */
  public DataCollection<L, F> blankDataCollection() {
    BasicDataCollection<L, F> bdc;

    try {
      bdc = ErasureUtils.uncheckedCast(getClass().newInstance());
    } catch (Exception e) {
      return (null);
    }

    bdc.setName(name());
    return (bdc);
  }

  /**
   * Uses the ObjectBank framework to iterate over all the Documents in the given sources,
   * adding them to this DataCollection
   */
  public <T extends Datum<L, F>> void load(List<File> sources, IteratorFromReaderFactory<T> ifrf) {
    ReaderIteratorFactory rif = new ReaderIteratorFactory(sources);
    ObjectBank<T> ob = new ObjectBank<T>(rif, ifrf);
    for (Datum<L, F> dat : ob) {
      add(dat);
    }
  }
 /**
  * This method is analogous to the readSVMLightFormat method in RVFDataset.
  * Can be used for loading the test set because we don't need to create an index for it (which an RVFDataset builds).
  * @return a DataCollection
  */
  public static DataCollection<String,String> readSVMLightFormat(String file) {
    DataCollection<String,String> data = new BasicDataCollection<String,String>();
    try {
      for(String line : ObjectBank.getLineIterator(new File(file))) {
        String[] columns = line.split("\\s+");
        ClassicCounter<String> features = new ClassicCounter<String>();
        for (int i = 1; i < columns.length; i++) {
          String[] f = columns[i].split(":");
          double val = Double.parseDouble(f[1]);
          features.incrementCount(f[0], val);
        }
        data.add(new RVFDatum<String, String>(features, columns[0]));
      }
    }
    catch(Exception e){
      System.out.println("Exception: "+e);
    }
    return data;
  }

  /**
   * Loads Document(s) for each input source in the given list and adds it to
   * this collection. Each source must be something that
   * {@link DocumentReader#getReader(Object)} can read (File, String filename,
   * URL, InputStream, or Reader). The given reader is used to read the
   * document(s) from each source, or if it's <tt>null</tt>, a default
   * DocumentReader is used. Each source is read in isolation and any exceptions
   * are silently caught internally.
   *
   * @param sources input sources to read documents from (File, String filename, etc.)
   * @param reader custom DocumentReader to use on sources (if <tt>null</tt>,
   * default DocumentReader is used)
   */
  /*public void load(List sources,DocumentReader reader)
  {
      if(reader==null) reader=new DocumentReader();
      for(Iterator iter=sources.iterator();iter.hasNext();)
      {
          try
          {
              // each element must be a File, String filename, URL, InputStream, or Reader
              Reader in=DocumentReader.getReader(iter.next());
              if(in!=null)
              {
                  // reads in a document from the given file and adds it to this collection
                  reader.setReader(in);
                  addAll(reader);
              }
          }
          catch(Exception e) { e.printStackTrace(); }

      }
  }   */

  /** Reads and adds all the Documents available from the given DocumentReader. */
  /*public void addAll(DocumentReader documentReader) throws IOException
  {
      Document doc;
      while((doc=documentReader.readDocument())!=null)
          add(doc);
  } */

  /**
   * Loads Documents listed in the given filename. Each line lists a filename
   * for a document to load, and optionally a list of labels separated by
   * whitespace. The labelColumn is which column of label to load for the label
   * of the document. Documents can be labeled for multiple attributes and
   * this allows a single file to list them all and select one for loading.
   * For example, the line "doc.txt title apple" specifies a file and two
   * labels (1=title, 2=apple). Passing in 0 for labelColumn uses the filename
   * as the label, and passing in -1 uses no label. Files listed can be
   * absolute paths or relative to file in which they are listed.
   */
  public static void loadLabeled(BasicDataCollection<String, Word> wordDataCollection, String filename, int labelColumn) throws IOException {
    File file = new File(filename);
    for (String line: ObjectBank.getLineIterator(new File(filename))) {
      String[] cols = line.split("\\s+"); // split on whitespace

      File f = new File(cols[0]); // try absolute first, then relative
      if ( ! f.isAbsolute()) {
        f = new File(file.getParent(), cols[0]);
      }

      // read in doc and label it based on column (if not -1)
      DocumentReader<String> reader = new DocumentReader<String>(DocumentReader.getReader(f));
      BasicDocument<String> doc = reader.readDocument();
      if (labelColumn >= 0) {
        doc.setLabel(cols[labelColumn]);
      }
      wordDataCollection.add(doc);
    }
  }

  /**
   * Computes and returns the total number of feature elements contained in
   * all the data in this collection.
   */
  public int computeFeatureTokenCount() {
    int count = 0;
    for (int i = 0; i < size(); i++) {
      count += get(i).asFeatures().size();
    }
    return (count);
  }

  /**
   * Computes and returns the total number of unique feature elements contained
   * in all the data in this collection.
   *
   * @return The total number of unique feature elements contained
   * in all the data in this collection.
   */
  public int computeFeatureTypeCount() {
    return computeFeatureCounts().size();
  }

  /**
   * Computes and returns a Counter from each unique feature element (Object) in
   * the data in this collection to a count (double) of its total number of
   * occurrences in this collection. The keyTransformer is applied to each
   * feature before it's counted, which is useful when you want to lump or
   * otherwise change how features are bucketed. For example, if the features
   * are Words with pos tags or semantic types, you might provide a transformer
   * that just pulls out the String. If featureTransformer is null, an
   * identity transform is used (i.e. the features are counted as-is).
   */
  public <T> ClassicCounter<T> computeFeatureCounts(Function<F, T> featureTransformer) {
    if (featureTransformer == null) {
      throw new UnsupportedOperationException("Cannot use null function - use the no-arg version.");
    }
    ClassicCounter<T> featureCounts = new ClassicCounter<T>();
    for (Datum<L, F> d  : this) {
      for (F f : d.asFeatures()) {
        featureCounts.incrementCount(featureTransformer.apply(f));
      }
    }
    return (featureCounts);
  }

  /**
   * Computes and returns a Counter from each unique feature element (Object) in
   * the data in this collection to a count (double) of its total number of
   * occurrences in this collection.
   */
  public ClassicCounter<F> computeFeatureCounts() {
    return computeFeatureCounts(new IdentityTransformer<F>());
  }

  /**
   * Computes and returns a Counter from each unique label of a Datum in
   * this data collection to a count (double) of the total number of
   * Datums with that label.
   */
  public ClassicCounter<L> computeLabelCounts() {
    ClassicCounter<L> labelCounts = new ClassicCounter<L>();
    for (Datum<L, F> d : this) {
      labelCounts.incrementCount(d.label());
    }
    return labelCounts;
  }

  /**
   * Returns the input Object without doing anything.
   */
  private static class IdentityTransformer<T> implements Function<T, T> {
    public T apply(T in) {
      return in;
    }
  }

  /**
   * Return a DataCollection with the subset of data that range from the
   * fraction into the DataCollection to the end fraction of the DataCollection.
   * When rounding, the left-hand side should be inclusive, and the
   * right-hand side exclusive (except when it's 1.0).  So, if there are 200
   * data, numbered 0 to 199, 0.2 to 0.3 gives you 40 through 59.
   */
  public DataCollection<L, F> splitRange(double start, double end) {
    BasicDataCollection<L, F> dc = (BasicDataCollection<L, F>)blankDataCollection();
    dc.addRangeFrom(this, start, end);
    return dc;
  }

  public DataCollection<L, F> splitRange(double s1, double e1, double s2, double e2) {
    BasicDataCollection<L, F> dc = (BasicDataCollection<L, F>) blankDataCollection();
    dc.addRangeFrom(this, s1, e1);
    dc.addRangeFrom(this, s2, e2);
    return dc;
  }

  /**
   * Returns a DataCollection with a fraction of the data starting from
   * a specified point, expressed as a fraction into the DataCollection.
   * When rounding, the left-hand side should be inclusive, and the
   * right-hand side exclusive
   */
  public DataCollection<L, F> splitFrom(double start, double fraction) {
    BasicDataCollection<L, F> dc = (BasicDataCollection<L, F>) blankDataCollection();
    double end = start + fraction;
    if (end > 1.0) {
      dc.addRangeFrom(this, start, 1.0);
      dc.addRangeFrom(this, 0.0, end - 1.0);
      return dc;
    }
    dc.addRangeFrom(this, start, end);
    return dc;
  }

  /**
   * Divides the DataCollection into dcs of sizes specified by the fractions
   * argument.  fractions is normalized to 1.  The data are separated
   * sequentially by chunks, starting from the specified start point
   * (expressed as a fraction into the DataCollection).  So if you want 75%
   * training, 10% validation, and 15% test, you need to pass in a fractions
   * array containing [.75, .25, .10 ] in that order. This method uses reflection
   * to ensure that the array's type is of the subclass on which you call the
   * method. Thus it's safe to cast it down to an array of that subtype, and
   * all the elements will also be of the subtype.
   *
   * @return An array containing the new dcs
   */
  public DataCollection<L, F>[] split(double start, double[] fractions) {
    DataCollection<L, F>[] dcs = ErasureUtils.uncheckedCast(Array.newInstance(getClass(), fractions.length));
    for (int i = 0; i < fractions.length; i++) {
      dcs[i] = blankDataCollection();
    }

    double[] normalized = normalize(fractions);

    for (int i = 0; i < normalized.length; i++) {
      dcs[i] = splitFrom(start, normalized[i]);
      start += normalized[i];
      while (start >= 1.0) {
        start -= 1.0;
      }
    }

    return dcs;
  }

  /**
   * Divides this DataCollection two two splits, the first of which is contains given
   * fraction of the original DataCollection. For example, split(0.75) returns
   * a 75/25 split (e.g. train/test) in the form of a length-2 array.
   */
  public DataCollection<L, F>[] split(double fraction) {
    return (split(0, new double[]{fraction, 1.0 - fraction}));
  }

  /**
   * Randomly splits into two pieces at the given fraction.
   */
  public DataCollection<L, F>[] splitRandom(double fraction) {
    return (splitRandom(new double[]{fraction, 1.0 - fraction}));
  }

  /**
   * Randomly splits into two pieces at the given fraction.
   */
  public DataCollection<L, F>[] splitRandom(double fraction, long seed) {
    return (splitRandom(new double[]{fraction, 1.0 - fraction}, seed));
  }

  /**
   * Calls splitRandom with <tt>System.currentTimeMillis</tt> as the seed.
   */
  public DataCollection<L, F>[] splitRandom(double[] fractions) {
    return (splitRandom(fractions, System.currentTimeMillis()));
  }

  /**
   * Divides the DataCollection into dcs of sizes specified by the fractions
   * argument.  fractions is normalized to 1.  Attempts to distribute the
   * data randomly among the new dcs.  Specifying the same seed on
   * the same machine should produce the same split.
   *
   * @return An array containing the new dcs
   */
  public DataCollection<L, F>[] splitRandom(double[] fractions, long seed) {
    int i;

    Class<? super BasicDataCollection<L,F>> klass = ErasureUtils.<Class<BasicDataCollection<L,F>>>uncheckedCast(getClass());
    DataCollection<L, F>[] dcs = ErasureUtils.<BasicDataCollection<L,F>>mkTArray(klass,fractions.length);
    for (i = 0; i < fractions.length; i++) {
      dcs[i] = blankDataCollection();
    }

    double[] normalized = normalize(fractions);
    int[] corpSizes = new int[normalized.length];

    int numDocs = size();
    int docsRemaining = numDocs;

    // compute the number of data in each DataCollection
    for (i = 0; i < normalized.length - 1; i++) {
      corpSizes[i] = (int) (normalized[i] * numDocs);
      docsRemaining -= corpSizes[i];
    }
    // the last DataCollection gets what's left over to avoid rounding errors
    corpSizes[i] = docsRemaining;

    Random randGen = new Random(seed);

    int lastDefault = 0; // the last bucket selected deterministically

    for (i = 0; i < numDocs; i++) {
      boolean docPlaced = false;
      int rand = randGen.nextInt() % numDocs;
      int j;
      for (j = 0; j < corpSizes.length; j++) {
        if (rand < corpSizes[j] && dcs[j].size() < corpSizes[j]) {
          dcs[j].add(get(i));
          docPlaced = true;
          break;
        }
        rand -= corpSizes[j];
      }
      if (!docPlaced) {
        for (j = lastDefault + 1; ;) {
          if (dcs[j].size() < corpSizes[j]) {
            dcs[j].add(get(i));
            lastDefault = j;
            break;
          }
          // cycle around to first bucket if necessary
          j = (j + 1) % normalized.length;
        }
      }
    }

    return dcs;
  }

  public static double[] normalize(double[] fractions) {
    double[] normalized = new double[fractions.length];

    // normalize fractions
    double sum = 0.0;
    for (int i = 0; i < fractions.length; i++) {
      sum += fractions[i];
    }
    for (int i = 0; i < fractions.length; i++) {
      normalized[i] = fractions[i] / sum;
    }
    return normalized;
  }

  /**
   * add data from DataCollection dc to this DataCollection over given range
   */
  private void addRangeFrom(DataCollection<L, F> dc, double start, double end) {
    int csize = dc.size();
    int sindex = (int) (start * csize);
    int eindex = (int) (end * csize);
    // System.err.println("addRangeFrom " + start + " to " + end + " of " + dc.size() + " data: Adding data " + sindex + " <= i < " + eindex);
    for (int i = sindex; i < eindex; i++) {
      if (i == csize) {
        System.err.println("*** err: csize=" + csize);
        System.err.println(" start=" + start + " sindex=" + sindex);
        System.err.println(" end=" + end + " eindex=" + eindex);
      }
      add(dc.get(i));
    }
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    try {
      if (args.length < 1) {
        System.err.println("Usage: BasicDataCollection fileToLoad");
      } else {
        BasicDataCollection<String,Word> bdc = new BasicDataCollection<String,Word>();
        BasicDataCollection.loadLabeled(bdc, args[0], 2);
        System.err.println(bdc);
        System.err.println(bdc.labels());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
