package edu.stanford.nlp.util;

import java.io.*;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * <p>
 * A Map backed by the filesystem.
 * The primary use-case for this class is in reading a large cache which is convenient to store on disk.
 * The class will load subsets of data on demand; if the JVM is in danger of running out of memory, these will
 * be dropped from memory, and re-queried from disk if requested again.
 * For best results, make sure to set a maximum number of files (by default, any number of files can be created);
 * and, make sure this number is the same when reading and writing to the database.
 * </p>
 *
 * <p>
 * The keys should have a consistent hash code.
 * That is, the value of the hash code of an object should be consistent between runs of the JVM.
 * Note that this is <b>not</b> enforced in the specification of a hash code; in fact, in Java 7
 * the hash code of a String may change between JVM invocations. The user is advised to be wary.
 * </p>
 *
 * <p>
 * Furthermore, note that many of the operations on this class are expensive, as they require traversing
 *   a potentially large portion of disk, reading it into memory.
 * Some operations, such as those requiring all the values to be enumerated, may cause a spike in memory
 *   usage.
 * </p>
 *
 * <p>
 * This class is thread-safe, but not necessarily process-safe.
 * If two processes write to the same block, there is no guarantee that both values will actually be written.
 * This is very important -- <b>this class is a cache and not a database</b>.
 * If you care about data integrity, you should use a real database.
 * </p>
 *
 * <p>
 *   The values in this map should not be modified once read -- the cache has no reliable way to pick up this change
 *   and synchronize it with the disk.
 *   To enforce this, the cache will cast collections to their unmodifiable counterparts -- to avoid class cast exceptions,
 *   you should not parameterize the class with a particular type of collection
 *   (e.g., use {@link java.util.Map} rather than {@link java.util.HashMap}).
 * </p>
 *
 * <p>
 *   The serialization behavior can be safely changed by overwriting:
 * </p>
 *   <ul>
 *     <li>@See FileBackedCache#newInputStream</li>
 *     <li>@See FileBackedCache#newOutputStream</li>
 *     <li>@See FileBackedCache#writeNextObject</li>
 *     <li>@See FileBackedCache#readNextObject</li>
 *   </ul>
 *
 * @param <KEY> The key to cache by
 * @param <T> The object to cache
 *
 * @author Gabor Angeli (angeli at cs)
 */

public class FileBackedCache<KEY extends Serializable, T> implements Map<KEY, T>, Iterable <Map.Entry<KEY,T>> {
  //
  // Variables
  //
  /** The directory the cached elements are being written to */
  public final File cacheDir;

  /** The maximum number of files to create in that directory ('buckets' in the hash map) */
  public final int maxFiles;

  /** The implementation of the mapping */
  private final Map<KEY, SoftReference<T>> mapping = new ConcurrentHashMap<>();

  /** A reaper for soft references, to save memory on storing the keys */
  private final ReferenceQueue<T> reaper = new ReferenceQueue<>();

  /**
   * A file canonicalizer, so that we can synchronize on blocks -- static, as it should work between instances.
   * In particular, an exception is thrown if the JVM attempts to take out two locks on a file.
   */
  private static final Interner<File> canonicalFile = new Interner<>();
  /** A map indicating whether the JVM holds a file lock on the given file */
  private static final IdentityHashMap<File, FileSemaphore> fileLocks = Generics.newIdentityHashMap();

  //
  // Constructors
  //

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * or starting with an empty cache.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   */
  public FileBackedCache(File directoryToCacheIn) {
    this(directoryToCacheIn, -1);
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * or starting with an empty cache.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   * @param maxFiles The maximum number of files to store on disk
   */
  public FileBackedCache(File directoryToCacheIn, int maxFiles) {
    // Ensure directory exists
    if (!directoryToCacheIn.exists()) {
      if (!directoryToCacheIn.mkdirs()) {
        throw new IllegalArgumentException("Could not create cache directory: " + directoryToCacheIn);
      }
    }
    // Ensure directory is directory
    if (!directoryToCacheIn.isDirectory()) {
      throw new IllegalArgumentException("Cache directory must be a directory: " + directoryToCacheIn);
    }
    // Ensure directory is writable
    if (!directoryToCacheIn.canRead()) {
      throw new IllegalArgumentException("Cannot read cache directory: " + directoryToCacheIn);
    }
    // Save cache directory
    this.cacheDir = directoryToCacheIn;
    this.maxFiles = maxFiles;
    // Start cache cleaner
    /*
    Occasionally clean up the cache, removing keys which have been garbage collected.
   */
    Thread mappingCleaner = new Thread() {
      @SuppressWarnings({"unchecked", "StatementWithEmptyBody", "EmptyCatchBlock", "InfiniteLoopStatement"})
      @Override
      public void run() {
        while (true) {
          try {
            if (reaper.poll() != null) {
              // Clear reference queue
              while (reaper.poll() != null) {
              }
              // GC stale cache entries
              List<KEY> toRemove = Generics.newLinkedList();
              try {
                for (Entry<KEY, SoftReference<T>> entry : mapping.entrySet()) {
                  if (entry.getValue().get() == null) {
                    // Remove stale SoftReference
                    toRemove.add(entry.getKey());
                  }
                }
              } catch (ConcurrentModificationException e) {
                // Do nothing --
              }
              // Actually remove entries
              for (KEY key : toRemove) {
                mapping.remove(key);
              }
            }
            // Sleep a bit
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
          }
        }
      }
    };
    mappingCleaner.setDaemon(true);
    mappingCleaner.start();
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * with the initial mapping added, or starting with only the initial mapping.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   * @param initialMapping The initial elements to place into the cache.
   */
  public FileBackedCache(File directoryToCacheIn, Map<KEY, T> initialMapping) {
    this(directoryToCacheIn, -1);
    putAll(initialMapping);
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * with the initial mapping added, or starting with only the initial mapping.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   * @param maxFiles The maximum number of files to store on disk
   * @param initialMapping The initial elements to place into the cache.
   */
  public FileBackedCache(File directoryToCacheIn, Map<KEY, T> initialMapping, int maxFiles) {
    this(directoryToCacheIn, maxFiles);
    putAll(initialMapping);
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * or starting with an empty cache.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   */
  public FileBackedCache(String directoryToCacheIn) {
    this(new File(directoryToCacheIn), -1);
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * or starting with an empty cache.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   * @param maxFiles The maximum number of files to store on disk
   */
  public FileBackedCache(String directoryToCacheIn, int maxFiles) {
    this(new File(directoryToCacheIn), maxFiles);
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * with the initial mapping added, or starting with only the initial mapping.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   * @param initialMapping The initial elements to place into the cache.
   */
  public FileBackedCache(String directoryToCacheIn, Map<KEY, T> initialMapping) {
    this(new File(directoryToCacheIn), initialMapping);
  }

  /**
   * Create a file backed cache in a particular directory; either inheriting the elements in the directory
   * with the initial mapping added, or starting with only the initial mapping.
   * This constructor may exception, and will create the directory in question if it does not exist.
   * @param directoryToCacheIn The directory to create the cache in
   * @param initialMapping The initial elements to place into the cache.
   * @param maxFiles The maximum number of files to store on disk
   */
  public FileBackedCache(String directoryToCacheIn, Map<KEY, T> initialMapping, int maxFiles) {
    this(new File(directoryToCacheIn), initialMapping, maxFiles);
  }

  //
  // Interface
  //

  /**
   * Gets the size of the cache, in terms of elements on disk.
   * Note that this is an expensive operation, as it reads the entire cache in from disk.
   * @return The size of the cache on disk.
   */
  @Override
  public int size() {
    return readCache();
  }

  /**
   * Gets the size of the cache, in terms of elements in memory.
   * In a multithreaded environment this is on a best-effort basis.
   * This method makes no disk accesses.
   * @return The size of the cache in memory.
   */
  public int sizeInMemory() {
    return mapping.size();
  }

  /**
   * Gets whether the cache is empty, including elements on disk.
   * Note that this returns true if the cache is empty.
   */
  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Returns true if the specified key exists in the mapping (on a best-effort basis in a multithreaded
   * environment).
   * This method may require some disk access, up to a maximum of one file read (of unknown size a priori).
   * @param key The key to query.
   * @return True if this key is in the cache.
   */
  @Override
  public boolean containsKey(Object key) {
    // Early exits
    if (mapping.containsKey(key)) return true;
    if (!tryFile(key)) return false;
    // Read the block for this key
    Collection<Pair<KEY, T>> elementsRead = readBlock(key);
    for (Pair<KEY, T> pair : elementsRead) {
      if (pair.first.equals(key)) return true;
    }
    return false;
  }

  /**
   * Returns true if the specified value is contained.
   * It is nearly (if not always) a bad idea to call this method.
   * @param value The value being queried for
   * @return True if the specified value exists in the cache.
   */
  @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
  @Override
  public boolean containsValue(Object value) {
    // Try to short circuit and save the use from their stupidity
    if (mapping.containsValue(new SoftReference(value))) { return true; }
    // Do an exhaustive check over the values
    return values().contains(value);
  }

  /**
   * Get a cached value based on a key.
   * If the key is in memory, this is a constant time operation.
   * Else, this requires a single disk access, of undeterminable size but roughly correlated with the
   * quality of the key's hash code.
   */
  @SuppressWarnings({"SuspiciousMethodCalls", "unchecked"})
  @Override
  public T get(Object key) {
    SoftReference<T> likelyReferenceOrNull = mapping.get(key);
    T referenceOrNull = likelyReferenceOrNull == null ? null : likelyReferenceOrNull.get();
    if (likelyReferenceOrNull == null) {
      // Case: We don't know about this element being in the cache
      if (!tryFile(key)) { return null; }  // Case: there's no hope of finding this element
      Collection<Pair<KEY, T>> elemsRead = readBlock(key);  // Read the block for this key
      for (Pair<KEY, T> pair : elemsRead) {
        if (pair.first.equals(key)) { return pair.second; }
      }
      return null;
    } else if (referenceOrNull == null) {
      // Case: This element once was in the cache
      mapping.remove(key);
      return get(key);  // try again
    } else {
      if (referenceOrNull instanceof Collection) {
        return (T) Collections.unmodifiableCollection((Collection) referenceOrNull);
      } else if (referenceOrNull instanceof Map) {
        return (T) Collections.unmodifiableMap((Map) referenceOrNull);
      } else {
        return referenceOrNull;
      }
    }
  }

  @Override
  public T put(KEY key, T value) {
    T existing = get(key);
    if (existing == value || (existing != null && existing.equals(value))) {
      // Make sure we flush objects which have changed
      if (existing != null && !existing.equals(value)) {
        updateBlockOrDelete(key, value);
      }
      // Return the same object back
      return existing;
    } else {
      // In-memory
      SoftReference<T> ref = new SoftReference<>(value, this.reaper);
      mapping.put(key, ref);
      // On Disk
      if (existing == null) {
        appendBlock(key, value);
      } else {
        updateBlockOrDelete(key, value);
      }
      // Return
      return existing;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T remove(Object key) {
    if (!tryFile(key)) return null;
    try {
      return updateBlockOrDelete((KEY) key, null);
    } catch (ClassCastException e) {
      return null;
    }
  }

  @Override
  public void putAll(Map<? extends KEY, ? extends T> m) {
    for (Entry<? extends KEY, ? extends T> entry : m.entrySet()) {
      try {
        put( entry.getKey(), entry.getValue() );
      } catch (RuntimeException e) {
        err(e);
      }
    }
  }

  /**
   * Clear the IN-MEMORY portion of the cache. This does not delete any files.
   */
  @Override
  public void clear() {
    mapping.clear();
  }

  /**
   * Returns all the keys for this cache that are found ON DISK.
   * This is an expensive operation.
   * @return The set of keys for this cache as found on disk.
   */
  @Override
  public Set<KEY> keySet() {
    readCache();
    return mapping.keySet();
  }

  /**
   * Returns all the values for this cache that are found ON DISK.
   * This is an expensive operation, both in terms of disk access time,
   * and in terms of memory used.
   * Furthermore, the memory used in this function cannot be GC collected -- you are loading the
   * entire cache into memory.
   * @return The set of values for this cache as found on disk.
   */
  @Override
  public Collection<T> values() {
    Set<Entry<KEY, T>> entries = entrySet();
    ArrayList<T> values = Generics.newArrayList(entries.size());
    for (Entry<KEY, T> entry : entries) {
      values.add(entry.getValue());
    }
    return values;
  }

  /**
   * Returns all the (key,value) pairs for this cache that are found ON DISK.
   * This is an expensive operation, both in terms of disk access time,
   * and in terms of memory used.
   * Furthermore, the memory used in this function cannot be GC collected -- you are loading the
   * entire cache into memory.
   * @return The set of keys and associated values for this cache as found on disk.
   */
  @Override
  public Set<Entry<KEY, T>> entrySet() {
    readCache();
    Set<Entry<KEY, SoftReference<T>>> entries = mapping.entrySet();
    Set<Entry<KEY, T>> rtn = Generics.newHashSet();
    for (final Entry<KEY, SoftReference<T>> entry : entries) {
      T value = entry.getValue().get();
      if (value == null) value = get(entry.getKey());
      final T valueFinal = value;
      rtn.add(new Entry<KEY, T>(){
        private T valueImpl = valueFinal;
        @Override
        public KEY getKey() {
          return entry.getKey();
        }
        @Override
        public T getValue() {
          return valueImpl;
        }
        @Override
        public T setValue(T value) {
          T oldValue = valueImpl;
          valueImpl = value;
          return oldValue;
        }
      });
    }
    return rtn;
  }

  /**
   * Iterates over the entries of the cache.
   * In the end, this loads the entire cache, but it can do it incrementally.
   * @return An iterator over the entries in the cache.
   */
  @Override
  public Iterator<Entry<KEY,T>> iterator() {
    final File[] files = cacheDir.listFiles();
    if (files == null || files.length == 0) return Generics.<Entry<KEY,T>>newLinkedList().iterator();
    for (int i = 0; i < files.length; ++i) {
      try {
        files[i] = canonicalFile.intern(files[i].getCanonicalFile());
      } catch (IOException e) {
        throw throwSafe(e);
      }
    }

    return new Iterator<Entry<KEY,T>>() {
      Iterator<Pair<KEY, T>> elements = readBlock(files[0]).iterator();
      int index = 1;

      @Override
      public boolean hasNext() {
        // Still have elements in this block
        if (elements.hasNext()) return true;
        // Still have files to traverse
        elements = null;
        while (index < files.length && elements == null) {
          try {
            elements = readBlock(files[index]).iterator();
          } catch (OutOfMemoryError e) {
            warn("FileBackedCache", "Caught out of memory error (clearing cache): " + e.getMessage());
            FileBackedCache.this.clear();
            //noinspection EmptyCatchBlock
            try { Thread.sleep(1000); } catch (InterruptedException e2) {
              throw new RuntimeInterruptedException(e2);
            }
            elements = readBlock(files[index]).iterator();
          } catch (RuntimeException e) {
            err(e);
          }
          index += 1;
        }
        // No more elements
        return elements != null && hasNext();
      }
      @Override
      public Entry<KEY, T> next() {
        if (!hasNext()) throw new NoSuchElementException();
        // Convert a pair to an entry
        final Pair<KEY, T> pair =  elements.next();
        return new Entry<KEY, T>() {
          @Override
          public KEY getKey() { return pair.first; }
          @Override
          public T getValue() { return pair.second; }
          @Override
          public T setValue(T value) { throw new RuntimeException("Cannot set entry"); }
        };
      }
      @Override
      public void remove() {
        throw new RuntimeException("Remove not implemented");
      }
    };
  }

  /**
   * Remove a given key from memory, not removing it from the disk.
   * @param key The key to remove from memory.
   */
  public boolean removeFromMemory(KEY key) {
    return mapping.remove(key) != null;
  }

  /**
   * Get the list of files on which this JVM holds a lock.
   * @return A collection of files on which the JVM holds a file lock.
   */
  public static Collection<File> locksHeld() {
    ArrayList<File> files = Generics.newArrayList();
    for (Entry<File, FileSemaphore> entry : fileLocks.entrySet()) {
      if (entry.getValue().isActive()) {
        files.add(entry.getKey());
      }
    }
    return files;
  }

  //
  // Daemons
  //

  //
  // Implementation
  // These are directly called by the interface methods
  //
  /** Reads the cache in its entirely -- this is potentially very slow */
  private int readCache() {
    File[] files = cacheDir.listFiles();
    if (files == null) { return 0; }
    for (int i = 0; i < files.length; ++i) {
      try {
        files[i] = canonicalFile.intern(files[i].getCanonicalFile());
      } catch (IOException e) {
        throw throwSafe(e);
      }
    }
    int count = 0;
    for (File f : files) {
      try {
        Collection<Pair<KEY, T>> block = readBlock(f);
        count += block.size();
      } catch (Exception e) {
        throw throwSafe(e);
      }
    }
    return count;
  }

  /** Checks for the existence of the block associated with the key */
  private boolean tryFile(Object key) {
    try {
      return hash2file(key.hashCode(), false).exists();
    } catch (IOException e) {
      throw throwSafe(e);
    }
  }

  /** Reads the block specified by the key in its entirety */
  private Collection<Pair<KEY, T>> readBlock(Object key) {
    try {
      return readBlock(hash2file(key.hashCode(), true));
    } catch (IOException e) {
      err("Could not read file: " + cacheDir.getPath() + File.separator + fileRoot(key.hashCode()));
      throw throwSafe(e);
    }
  }

  /** Appends a value to the block specified by the key */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private void appendBlock(KEY key, T value) {
    boolean haveTakenLock = false;
    Pair<? extends OutputStream, CloseAction> writer = null;
    try {
      // Get File
      File toWrite = hash2file(key.hashCode(), false);
      boolean exists = toWrite.exists();
      robustCreateFile(toWrite);
      synchronized (toWrite) {
        assert canonicalFile.intern(toWrite.getCanonicalFile()) == toWrite;
        // Write Object
        writer = newOutputStream(toWrite, exists);
        haveTakenLock = true;
        writeNextObject(writer.first, Pair.makePair(key, value));
        writer.second.apply();
        haveTakenLock = false;
      }
    } catch (IOException e) {
      try { if (haveTakenLock) { writer.second.apply(); } }
      catch (IOException e2) { throw throwSafe(e2); }
      throw throwSafe(e);
    }
  }

  /** Updates a block with the specified value; or deletes the block if the value is null */
  @SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
  private T updateBlockOrDelete(KEY key, T valueOrNull) {
    Pair<? extends InputStream, CloseAction> reader = null;
    Pair<? extends OutputStream, CloseAction> writer = null;
    boolean haveClosedReader = false;
    boolean haveClosedWriter = false;
    try {
      // Variables
      File blockFile = hash2file(key.hashCode(), true);
      synchronized (blockFile) {
        assert canonicalFile.intern(blockFile.getCanonicalFile()) == blockFile;
        reader = newInputStream(blockFile);
        writer = newOutputStream(blockFile, false); // Get write lock before reading
        List<Pair<KEY, T>> block = Generics.newLinkedList();
        T existingValue = null;
        // Read
        Pair<KEY, T> element;
        while ((element = readNextObjectOrNull(reader.first)) != null) {
          if (element.first.equals(key)) {
            if (valueOrNull != null) {
              // Update
              existingValue = element.second;
              element.second = valueOrNull;
              block.add(element);
            }
          } else {
            // Spurious read
            block.add(element);
          }
        }
        reader.second.apply();
        haveClosedReader = true;
        // Write
        for( Pair<KEY, T> elem : block ) {
          writeNextObject(writer.first, elem);
        }
        writer.second.apply();
        haveClosedWriter = true;
        // Return
        return existingValue;
      }
    } catch (IOException | ClassNotFoundException e) {
      err(e);
      throw throwSafe(e);
    } finally {
      try {
        if (reader != null && !haveClosedReader) { reader.second.apply(); }
          if (writer != null && !haveClosedWriter) { writer.second.apply(); }
      } catch (IOException e) {
        warn(e);
      }
    }
  }

  //
  // Implementation Helpers
  // These are factored bits of the implementation
  //

  /** Completely reads a block into local memory */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private Collection<Pair<KEY, T>> readBlock(File block) {
    boolean haveClosed = false;
    Pair<? extends InputStream, CloseAction> input = null;

    try {
      synchronized (block) {
        assert canonicalFile.intern(block.getCanonicalFile()) == block;
        List<Pair<KEY, T>> read = Generics.newLinkedList();
        // Get the reader
        input = newInputStream(block);
        // Get each object in the block
        Pair<KEY,T> element;
        while ((element = readNextObjectOrNull(input.first)) != null) {
          read.add(element);
        }
        input.second.apply();
        haveClosed = true;
        // Add elements
        for (Pair<KEY, T> elem : read) {
          SoftReference<T> ref = new SoftReference<>(elem.second, this.reaper);
          mapping.put(elem.first, ref);
        }
        return read;
      }
    } catch (StreamCorruptedException e) {
      warn("Stream corrupted reading " + block);
      // Case: corrupted write
      if (!block.delete()) {
        throw new IllegalStateException("File corrupted, and cannot delete it: " + block.getPath());
      }
      return Generics.newLinkedList();
    } catch (EOFException e) {
      warn("Empty file (someone else is preparing to write to it?) " + block);
      return Generics.newLinkedList();
    } catch (IOException e) {
      // Case: General IO Error
      err("Could not read file: " + block + ": " + e.getMessage());
      return Generics.newLinkedList();
    } catch (ClassNotFoundException e) {
      // Case: Couldn't read class
      err("Could not read a class in file: " + block + ": " + e.getMessage());
      return Generics.newLinkedList();
    } catch (RuntimeException e) {
      // Case: Unknown error -- see if it's caused by StreamCorrupted
      if (e.getCause() != null && StreamCorruptedException.class.isAssignableFrom(e.getCause().getClass())) {
        // Yes -- caused by StreamCorrupted
        if (!block.delete()) {
          throw new IllegalStateException("File corrupted, and cannot delete it: " + block.getPath());
        }
        return Generics.newLinkedList();
      } else {
        // No -- random error (pass up)
        throw e;
      }
    } finally {
      if (input != null && !haveClosed) {
        try {
          input.second.apply();
        } catch (IOException e) { warn(e); }
      }
    }
  }

  /** Returns a file corresponding to a hash code, ensuring it exists first */
  private File hash2file(int hashCode, boolean create) throws IOException {
    File candidate =  canonicalFile.intern(new File(cacheDir.getCanonicalPath() + File.separator + fileRoot(hashCode) + ".block.ser.gz").getCanonicalFile());
    if (create) { robustCreateFile(candidate); }
    return candidate;
  }

  private int fileRoot(int hashCode) {
    if (this.maxFiles < 0) { return hashCode; }
    else { return Math.abs(hashCode) % this.maxFiles; }
  }

  //
  // Java Hacks
  //
  /** Turns out, an ObjectOutputStream cannot append to a file. This is dumb. */
  public static class AppendingObjectOutputStream extends ObjectOutputStream {
    public AppendingObjectOutputStream(OutputStream out) throws IOException {
      super(out);
    }
    @Override
    protected void writeStreamHeader() throws IOException {
      // do not write a header, but reset
      reset();
    }
  }

  private static RuntimeException throwSafe(Throwable e) {
    if (e instanceof RuntimeException) return (RuntimeException) e;
    else if (e.getCause() == null) return new RuntimeException(e);
    else return throwSafe(e.getCause());
  }

  private static void robustCreateFile(File candidate) throws IOException {
    int tries = 0;
    while ( ! candidate.exists()) {
      if (tries > 30) { throw new IOException("Could not create file: " + candidate); }
      if (candidate.createNewFile()) { break; }
      tries++;
      try { Thread.sleep(1000); } catch (InterruptedException e) {
        log(e);
        throw new RuntimeInterruptedException(e);
      }
    }
  }

  public interface CloseAction {
    void apply() throws IOException;
  }

  public static class FileSemaphore {
    private int licenses = 1;
    private final FileLock lock;
    private final FileChannel channel;

    public FileSemaphore(FileLock lock, FileChannel channel) { this.lock = lock; this.channel = channel; }

    public synchronized boolean isActive() {
      if (licenses == 0) { assert lock == null || !lock.isValid(); }
      if (licenses != 0 && lock != null) { assert lock.isValid(); }
      return licenses != 0;
    }

    public synchronized void take() {
      if (!isActive()) { throw new IllegalStateException("Taking a file license when the licenses have all been released"); }
      licenses += 1;
    }

    public synchronized void release() throws IOException {
      if (licenses <= 0) { throw new IllegalStateException("Already released all semaphore licenses"); }
      licenses -= 1;
      if (licenses <= 0) {
        if (lock != null) { lock.release(); }
        channel.close();
      }
    }
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  protected FileSemaphore acquireFileLock(File f) throws IOException {
    assert canonicalFile.intern(f.getCanonicalFile()) == f;
    synchronized (f) {
      // Check semaphore
      synchronized (fileLocks) {
        if (fileLocks.containsKey(f)) {
          FileSemaphore sem = fileLocks.get(f);
          if (sem.isActive()) {
            sem.take();
            return sem;
          } else {
            fileLocks.remove(f);
          }
        }
      }
      // Get the channel
      FileChannel channel = new RandomAccessFile(f, "rw").getChannel();
      FileLock lockOrNull = null;
      // Try the lock
      for (int i = 0; i < 1000; ++i) {
        lockOrNull = channel.tryLock();
        if (lockOrNull == null || !lockOrNull.isValid()) {
          try { Thread.sleep(1000); } catch (InterruptedException e) {
            log(e);
            throw new RuntimeInterruptedException(e);
          }
          if (i % 60 == 59) { warn("FileBackedCache", "Lock still busy after " + ((i+1)/60) + " minutes"); }
          //noinspection UnnecessaryContinue
          continue;
        } else {
          break;
        }
      }
      if (lockOrNull == null) { warn("FileBackedCache", "Could not acquire file lock! Continuing without lock"); }
      // Return
      FileSemaphore sem = new FileSemaphore(lockOrNull, channel);
      synchronized (fileLocks) {
        fileLocks.put(f, sem);
      }
      return sem;
    }
  }

  //
  //  POSSIBLE OVERRIDES
  //

  /**
   * Create a new input stream, along with the code to close it and clean up.
   * This code may be overridden, but should match nextObjectOrNull().
   * IMPORTANT NOTE: acquiring a lock (well, semaphore) with FileBackedCache#acquireFileLock(File)
   * is generally a good idea. Make sure to release() it in the close action as well.
   *
   * @param f The file to read from
   * @return A pair, corresponding to the stream and the code to close it.
   * @throws IOException
   */
  protected Pair<? extends InputStream, CloseAction> newInputStream(File f) throws IOException {
    final FileSemaphore lock = acquireFileLock(f);
    final ObjectInputStream rtn = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(f))));
    return new Pair<>(rtn,
            () -> {
              lock.release();
              rtn.close();
            });
  }

  /**
   * Create a new output stream, along with the code to close it and clean up.
   * This code may be overridden, but should match nextObjectOrNull()
   * IMPORTANT NOTE: acquiring a lock (well, semaphore) with FileBackedCache#acquireFileLock(File)
   * is generally a good idea. Make sure to release() it in the close action as well.
   *
   * @param f The file to write to
   * @param isAppend Signals whether the file we are writing to exists, and we are appending to it.
   * @return A pair, corresponding to the stream and the code to close it.
   * @throws IOException
   */
  protected Pair<? extends OutputStream, CloseAction> newOutputStream(File f, boolean isAppend) throws IOException {
    final FileOutputStream stream = new FileOutputStream(f, isAppend);
    final FileSemaphore lock = acquireFileLock(f);
    final ObjectOutputStream rtn = isAppend
        ? new AppendingObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(stream)))
        : new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(stream)));
    return new Pair<>(rtn,
            () -> {
              rtn.flush();
              lock.release();
              rtn.close();
            });
  }

  /**
   * Return the next object in the given stream, or null if there is no such object.
   * This method may be overwritten, but should match the implementation of newInputStream
   * @param input The input stream to read the object from
   * @return A (key, value) pair corresponding to the read object
   * @throws IOException
   * @throws ClassNotFoundException
   */
  @SuppressWarnings("unchecked")
  protected Pair<KEY, T> readNextObjectOrNull(InputStream input) throws IOException, ClassNotFoundException {
    try {
      return (Pair<KEY, T>) ((ObjectInputStream) input).readObject();
    } catch (EOFException e) {
      return null; // I hate java
    }
  }

  /**
   * Write an object to a stream
   * This method may be overwritten, but should match the implementation of newOutputStream()
   * @param output The output stream to write the object to.
   * @param value The value to write to the stream, as a (key, value) pair.
   * @throws IOException
   */
  protected void writeNextObject(OutputStream output, Pair<KEY, T> value) throws IOException {
    ((ObjectOutputStream) output).writeObject(value);
  }

  /**
   * <p>Merge a number of caches together. This could be useful for creating large caches,
   * as (1) it can bypass NFS for local caching, and (2) it can allow for many small caches
   * that are then merged together, which is more efficient as the number of entries in a bucket
   * increases (e.g., if the cache becomes very large).</p>
   *
   * <p>If there are collision, they are broken by accepting the entry in destination (if applicable),
   *    and then by accepting the entry in the last constituent.</p>
   *
   * <p><b>IMPORTANT NOTE:</b>: This method requires quite a bit of memory, and there is a brief time
   * when it deletes all the files in destination, storing the data entirely in memory. If the program
   * crashes in this state, THE DATA IN |destination| MAY BE LOST</p>
   *
   * @param destination The cache to append to. This might not be empty, in which case all entries
   *                   in the destination are preserved.
   * @param constituents The constituent caches. All entries in each of these caches are added to
   *                     the destination.
   */
  public static <KEY extends Serializable, T extends Serializable> void merge(
      FileBackedCache<KEY, T> destination, FileBackedCache<? extends KEY, ? extends T>[] constituents) {
    startTrack("Merging Caches");

    // (1) Read everything into memory
    forceTrack("Reading Constituents");
    Map<String, Map<KEY, T>> combinedMapping = Generics.newHashMap();
    try {
      // Accumulate constituents
      for (int i = 0; i < constituents.length; ++i) {
        FileBackedCache<? extends KEY, ? extends T> constituent = constituents[i];
        for (Entry<? extends KEY, ? extends T> entry : constituent) {
          String fileToWriteTo = destination.hash2file(entry.getKey().hashCode(), false).getName();
          if (!combinedMapping.containsKey(fileToWriteTo)) { combinedMapping.put(fileToWriteTo, Generics.<KEY,T>newHashMap()); }
          combinedMapping.get(fileToWriteTo).put(entry.getKey(), entry.getValue());
        }
        log("[" + new DecimalFormat("0000").format(i) + "/" + constituents.length + "] read " + constituent.cacheDir + " [" + (Runtime.getRuntime().freeMemory() / 1000000) + "MB free memory]");
        constituent.clear();
      }
      // Accumulate destination
      for (Entry<? extends KEY, ? extends T> entry : destination) {
        String fileToWriteTo = destination.hash2file(entry.getKey().hashCode(), false).getName();
        if (!combinedMapping.containsKey(fileToWriteTo)) { combinedMapping.put(fileToWriteTo, Generics.<KEY,T>newHashMap()); }
        combinedMapping.get(fileToWriteTo).put(entry.getKey(), entry.getValue());
      }
    } catch (IOException e) {
      err("Found exception in merge() -- all data is intact (but passing exception up)");
      throw new RuntimeException(e);
    }
    endTrack("Reading Constituents");

    // (2) Clear out Destination
    forceTrack("Clearing Destination");
    if (!destination.cacheDir.exists() && !destination.cacheDir.mkdirs()) {
      throw new RuntimeException("Could not create cache dir for destination (data is intact): " + destination.cacheDir);
    }
    File[] filesInDestination = destination.cacheDir.listFiles();
    if (filesInDestination == null) {
      throw new RuntimeException("Cannot list files in destination's cache dir (data is intact): " + destination.cacheDir);
    }
    for (File block : filesInDestination) {
      if (!block.delete()) {
        warn("FileBackedCache", "could not delete block: " + block);
      }
    }
    endTrack("Clearing Destination");

    // (3) Write new files
    forceTrack("Writing New Files");
    try {
      for (Entry<String, Map<KEY, T>> blockEntry : combinedMapping.entrySet()) {
        // Get File
        File toWrite = canonicalFile.intern(new File(destination.cacheDir + File.separator + blockEntry.getKey()).getCanonicalFile());
        boolean exists = toWrite.exists(); // should really be false;
        // Write Objects
        Pair<? extends OutputStream, CloseAction> writer = destination.newOutputStream(toWrite, exists);
        for (Entry<KEY, T> entry : blockEntry.getValue().entrySet()) {
          destination.writeNextObject(writer.first, Pair.makePair(entry.getKey(), entry.getValue()));
        }
        writer.second.apply();
      }
    } catch (IOException e) {
      err("Could not write constituent files to combined cache (DATA IS LOST)!");
      throw new RuntimeException(e);
    }
    endTrack("Writing New Files");
    endTrack("Merging Caches");
  }

  @SuppressWarnings("unchecked")
  public static <KEY extends Serializable, T extends Serializable> void merge(
      FileBackedCache<KEY, T> destination, Collection<FileBackedCache<KEY, T>> constituents) {
    merge(destination, constituents.toArray((FileBackedCache<KEY,T>[])new FileBackedCache[constituents.size()]));
  }

}
