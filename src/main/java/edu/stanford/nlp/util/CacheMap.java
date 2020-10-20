package edu.stanford.nlp.util;

import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fixed size hash map with LRU replacement.  Can optionally automatically
 * dump itself out to a file as the cache grows.
 *
 * @author Ari Steinberg (ari.steinberg@stanford.edu)
 */

public class CacheMap<K,V> extends LinkedHashMap<K,V> {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CacheMap.class);

  private static final long serialVersionUID = 1L;
  private String backingFile;
  private int CACHE_ENTRIES;
  private int entriesSinceLastWritten;
  private int frequencyToWrite;
  private int hits;
  private int misses;
  private int puts;

  /**
   * Constructor.
   *
   * @param numEntries is the number of entries you want to store in the
   *                   CacheMap.  This is not the same as the number of
   *                   buckets - that is effected by this and the target
   *                   loadFactor.
   * @param accessOrder is the same as in LinkedHashMap.
   * @param backingFile is the name of the file to dump this to, if desired.
   * @see java.util.LinkedHashMap
   */
  public CacheMap(int numEntries, float loadFactor, boolean accessOrder,
                  String backingFile) {
    // Make sure its capacity is big enough so that we don't have to resize it
    // even if it gets one more element than we are expecting.  Round up the
    // division
    super((int)Math.ceil((numEntries+1)/loadFactor), loadFactor, accessOrder);
    CACHE_ENTRIES = numEntries;
    this.backingFile = backingFile;
    entriesSinceLastWritten = 0;
    this.frequencyToWrite = numEntries/128 + 1;
    hits = misses = puts = 0;
  }

  public CacheMap(int numEntries, float loadFactor, boolean accessOrder) {
    this(numEntries, loadFactor, accessOrder, null);
  }

  public CacheMap(int numEntries, float loadFactor) {
    this(numEntries, loadFactor, false, null);
  }

  public CacheMap(int numEntries) {
    this(numEntries, 0.75f, false, null);
  }

  /**
   * Creates a new file-backed CacheMap or loads it in from the specified file
   * if it already exists.  The parameters passed in are the same as the
   * constructor.  If useFileParams is true and the file exists, all of your
   * parameters will be ignored (replaced with those stored in the file
   * itself).  If useFileParams is false then we override the settings in the
   * file with the ones you specify (except loadFactor and accessOrder) and
   * reset the stats.
   */
  public static <K,V> CacheMap<K,V> create(int numEntries, float loadFactor,
                                boolean accessOrder, String file,
                                boolean useFileParams) {
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
      CacheMap<K, V> c = ErasureUtils.uncheckedCast(ois.readObject());
      log.info("Read cache from " + file + ", contains " + c.size() + " entries.  Backing file is " + c.backingFile);
      if (!useFileParams) {
        c.backingFile = file;
        c.hits = c.misses = c.puts = 0;
        c.CACHE_ENTRIES = numEntries;
      }

      return c;
    } catch (FileNotFoundException ex) {
      log.info("Cache file " + file + " has not been created yet.  Making new one.");
      return new CacheMap<>(numEntries, loadFactor, accessOrder, file);
    } catch (Exception ex) {
      log.info("Error reading cache file " + file + ".  Making a new cache and NOT backing to file.");
      return new CacheMap<>(numEntries, loadFactor, accessOrder);
    }
  }

  public static <K,V> CacheMap<K,V> create(int numEntries, float loadFactor, String file,
                                boolean useFileParams) {
    return create(numEntries, loadFactor, false, file, useFileParams);
  }

  public static <K,V> CacheMap<K,V> create(int numEntries, String file, boolean useFileParams) {
    return create(numEntries, .75f, false, file, useFileParams);
  }

  public static <K,V> CacheMap<K,V> create(String file, boolean useFileParams) {
    return create(1000, .75f, false, file, useFileParams);
  }

  /**
   * Dump out the contents of the cache to the backing file.
   */
  public void write() {
    // Do this even if not writing so we printStats() at good times
    entriesSinceLastWritten = 0;
    if (frequencyToWrite < CACHE_ENTRIES/4) frequencyToWrite *= 2;

    if (backingFile == null) return; 

    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(backingFile))) {
      log.info("Writing cache (size: " + size() + ") to " +
                         backingFile);
      oos.writeObject(this);
    } catch (Exception ex) {
      log.info("Error writing cache to file: " + backingFile + '!');
      log.info(ex);
    }
  }

  /**
   * @see java.util.LinkedHashMap#removeEldestEntry
   */
  @Override
  protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return size() > CACHE_ENTRIES;
  }

  /**
   * @see java.util.HashMap#get
   */
  @Override
  public V get(Object key) {
    V result = super.get(key);
    if (result == null) misses++;
    else hits++;
    return result;
  }

  /**
   * Add the entry to the map, and dump the map to a file if it's been a while
   * since we last did.
   * @see java.util.HashMap#put
   */
  @Override
  public V put(K key, V value) {
    V result = super.put(key, value);
    puts++;
    if (++entriesSinceLastWritten >= frequencyToWrite) {
      write(); // okay if backingFile is null
//      printStats(System.err);
    }
    return result;
  }

  /**
   * Print out cache stats to the specified stream.  Note that in many cases
   * treating puts as misses gives a better version of hit percentage than
   * actually using misses, since it's possible that some of your misses are
   * because you wind up choosing not to cache the particular value (we output
   * both versions).  Stats are reset when the cache is loaded in from disk
   * but are otherwise cumulative.
   */
  public void printStats(PrintStream out) {
    out.println("cache stats: size: " + size() + ", hits: " + hits +
                ", misses: " + misses + ", puts: " + puts +
                ", hit % (using misses): " + ((float)hits)/(hits + misses) +
                ", hit % (using puts): " + ((float)hits)/(hits + puts));
  }

}
