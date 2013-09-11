package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.regexp.RegexpExtractor;
import edu.stanford.nlp.io.FileCopier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Manages a collection of serialized FieldExtractors and maintains a mapping
 * between ontology classes/slots and extractors/fields to fill them in. Each
 * mediator is associated with a mediator file that stores the contents of this
 * mediator in XML. You can add FieldExtractors to this mediator, which keeps
 * a record of the names, descriptions, etc. of each extractor as well as a
 * serialized version of the extractor for later use. Serialized
 * extractors are stored in the same directory as the mediator file.
 * You can also register associations between a class
 * and slot name in an ontology and a corresponding extractor and field to
 * fill that slot. Then you can call {@link #extractSlotFillers} to use the
 * associated field extractors to fill the class's slots from text.
 * Mediators can be built and used programmatically through the API as well
 * as through a command-line interface when this class is run as a standalone
 * app. Note that this mediator doesn't know anything about any particular
 * ontology, rather it stores a generic mapping between class names and slot
 * names represented by strings and FieldExtractors to fill those slots.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class ExtractorMediator {
  /**
   * Set of unique extractor names managed by this mediator.
   */
  private final Set<String> extractorNames = new HashSet<String>();
  /**
   * Loaded extractors indexed by their unique name.
   */
  private final Map<String, FieldExtractor> extractorsByName = new HashMap<String, FieldExtractor>();
  /**
   * Cached String array of extractable field names for each extractor
   * indexed by the extractor's unique name.
   */
  private final Map<String, String[]> extractableFieldsByName = new HashMap<String, String[]>();
  /**
   * Cached descriptions of each extractor by their unique name.
   */
  private final Properties descriptionsByName = new Properties();
  /**
   * Cached class names of each extractor by their unique name.
   */
  private final Properties classNamesByName = new Properties();

  /**
   * Have the contents of this mediator changed since construction.
   */
  private boolean updated;

  /**
   * Main association map between classses and slots in an ontology and
   * extractors and fields to fill them. Keys to this map are names of
   * ontology classes and the value is a nested Map from the set of slot
   * names (Strings) for that class that have registered associations to
   * a list of ExtractorField objects that specify the FieldExtractors and
   * field names used to fill that slot. Right now only the first
   * ExtactorField for each slot is used, but the framework will allow in the
   * future for multiple extractors competing or combining their information
   * to fill a single slot.
   */
  private final Map<String, Map> extractableSlotsByClassName = new HashMap<String, Map>();

  /**
   * File where this mediator is stored (in XML).
   */
  private File mediatorFile;

  /**
   * Names of newly added extractors that haven't yet been stored.
   */
  private final Set<String> newExtractorNames = new HashSet<String>();
  /**
   * Serialized files for removed extractors (to remove when mediator is stored).
   */
  private final List<File> oldExtractorFiles = new ArrayList<File>();

  /**
   * Ontology mediator for restricting extractor assignments and filling slots up the class hierarchy.
   */
  private OntologyMediator ontology;

  /**
   * Whether to print out extra debug info
   */
  private final boolean verbose;

  /**
   * Constructs a new empty ExtractorMediator. To load an existing mediator,
   * call {@link #load} to read in the stored mediator file. If verbose is
   * true, debugging information is printed to stderr for various methods.
   */
  public ExtractorMediator(boolean verbose) {
    this.verbose = verbose;
    updated = false; // haven't made any additions to info yet
    mediatorFile = null; // no location to store yet
    ontology = null;
  }

  /**
   * Constructs a new empty ExtractorMediator without verbose output.
   */
  public ExtractorMediator() {
    this(true);
  } // JS: TODO: CHANGE BACK TO FALSE

  /**
   * Extracts fillers for the slots of the given class from the given text.
   * For each slot of the given class that's had an extractor assigned,
   * that extractor is used to extract the slot from the given text. A Map
   * is returned from slot names (String) to extracted text (String).
   * Extracted values may be empty ("") but will never be null.
   * The implementation aggregates all extractors that need to extract more
   * than one slot so each extractor is run the minimum number of times.
   * If no extractors have been registered for any slots of this class, the
   * Map returned will be empty, but it will never be null.
   * <p/>
   * If an ontology mediator has been provided, this method will walk up the
   * class hierarchy looking to fill blank slots with extractors for superclasses
   * of the given class. An extracted value from a subclass will never be
   * overwritten with a value from a superclass.
   */
  public Map<String, String> extractSlotFillers(String className, String text) {
    // map from slots (String) to extracted value (String)
    Map<String, String> extractedFieldsBySlotName = new HashMap<String, String>();

    List<String> ancestors = getAncestors(ontology, className);
    for (int i = 0; i < ancestors.size(); i++) {
      className = ancestors.get(i);

      Map extractorFieldsBySlotName = extractableSlotsByClassName.get(className);
      if (extractorFieldsBySlotName == null || extractorFieldsBySlotName.size() == 0) {
        if (verbose) {
          System.err.println("No extractors have been assigned to any slots for " + className);
        }
        continue;
      }

      // pulls the names of the set of extractors needed for each slot of this class
      Set<String> extractorNames = new HashSet<String>();
      Iterator slotNameIter = extractorFieldsBySlotName.keySet().iterator();
      while (slotNameIter.hasNext()) {
        String slotName = (String) slotNameIter.next();
        List extractorFields = (List) extractorFieldsBySlotName.get(slotName);
        if (extractorFields == null || extractorFields.size() == 0) {
          if (verbose) {
            System.err.println("ERROR: null or empty list of extractor fields for slot: " + slotName);
          }
          continue;
        }

        // adds the first extractor's name for this slot to the set of extractors
        String extractorName = ((ExtractorField) extractorFields.get(0)).getExtractorName();
        extractorNames.add(extractorName);
      }

      if (verbose) {
        System.err.println("Attempting to extract slots for " + className);
      }

      // runs the extractors and stores their output by extractor name
      // the extractor will be loaded first if it's never been used before
      Map<String, Map> extractedFieldsByExtractorName = new HashMap<String, Map>();
      Iterator<String> extractorNameIter = extractorNames.iterator();
      while (extractorNameIter.hasNext()) {
        String extractorName = extractorNameIter.next();
        FieldExtractor extractor = getExtractor(extractorName);
        if (extractor == null) {
          if (verbose) {
            System.err.println("ERROR: unable to load extractor: " + extractorName);
          }
        } else {
          if (verbose) {
            System.err.println("Extracting from " + extractorName);
          }
          Map extractedFields = extractor.extractFields(text);
          extractedFieldsByExtractorName.put(extractorName, extractedFields);
        }
      }

      // sticks the extracted fields into the appropriate slots
      if (verbose) {
        System.err.println("Filled slots:");
      }
      slotNameIter = extractorFieldsBySlotName.keySet().iterator();
      while (slotNameIter.hasNext()) {
        String slotName = (String) slotNameIter.next();

        // don't override an extracted slot from a subclass with one from a (more general) superclass
        String curValue = extractedFieldsBySlotName.get(slotName);
        if (curValue != null && curValue.length() > 0) {
          continue;
        }

        List extractorFields = (List) extractorFieldsBySlotName.get(slotName);
        ExtractorField ef = (ExtractorField) extractorFields.get(0); // assumes one extractor per slot
        Map extractedFields = extractedFieldsByExtractorName.get(ef.getExtractorName());
        if (extractedFields == null) {
          // must not have been able to load extractor
          extractedFieldsBySlotName.put(slotName, "");
        } else {
          String extractedValue = (String) extractedFields.get(ef.getFieldName());
          if (verbose) {
            System.err.println(slotName + ": " + extractedValue);
          }
          extractedFieldsBySlotName.put(slotName, extractedValue);
        }
      }
    }
    return (extractedFieldsBySlotName);
  }

  /**
   * Extracts a filler for the given slot of the given class from the given text.
   * This is a more specific alternative to {@link #extractSlotFillers}, which
   * attempts to fill every slot for the given class. Returns the extracted
   * value for the slot, which may be empty ("") but will never be null.
   * <p/>
   * If an ontology mediator has been provided, this method will walk up the
   * class hierarchy looking to fill blank slots with extractors for superclasses
   * of the given class. An extracted value from a subclass will never be
   * overwritten with a value from a superclass.
   */
  public String extractSlotFiller(String className, String slotName, String text) {
    List<String> ancestors = getAncestors(ontology, className);
    for (int i = 0; i < ancestors.size(); i++) {
      className = ancestors.get(i);

      Map extractorFieldsBySlotName = extractableSlotsByClassName.get(className);
      if (extractorFieldsBySlotName == null || extractorFieldsBySlotName.size() == 0) {
        if (verbose) {
          System.err.println("No extractors have been assigned to any slots for " + className);
        }
        continue;
      }

      List extractorFields = (List) extractorFieldsBySlotName.get(slotName);
      if (extractorFields == null || extractorFields.size() == 0) {
        if (verbose) {
          System.err.println("No extractors have been assigned to " + slotName + " for class " + className);
        }
        continue;
      }

      if (verbose) {
        System.err.println("Attempting to extract slot for " + className);
      }

      // uses the first extractor for this slot to pull out the value
      ExtractorField ef = (ExtractorField) extractorFields.get(0);
      String extractorName = ef.getExtractorName();
      FieldExtractor extractor = getExtractor(extractorName);
      if (extractor == null) {
        if (verbose) {
          System.err.println("ERROR: unable to load extractor: " + extractorName);
        }
        continue;
      }
      if (verbose) {
        System.err.println("Extracting from " + extractorName);
      }
      Map extractedFields = extractor.extractFields(text);
      String extractedValue = (String) extractedFields.get(ef.getFieldName());
      if (extractedValue != null && extractedValue.length() > 0) {
        if (verbose) {
          System.err.println(slotName + ": " + extractedValue);
        }
        return (extractedValue); // return as soon as a value was found
      }
    }

    return (""); // returns this if no slot value was ever extracted
  }

  /**
   * Registers a field of a FieldExtractor to fill in a slot for an ontology class.
   * You can register more than one extractor+field for each slot, though currently
   * only the first extractor will be used. In the future we may extract from
   * them all and intelligenly combine the outputs.
   * You can't register an association with an extractor that's not managed
   * by this mediator, or to a non-existent field name.
   *
   * @param className     the name of the class in the ontology to instantiate
   * @param slotName      the name of the class's slot to fill using a FieldExtractor
   * @param extractorName the name of the FieldExtractor to fill the slot
   * @param fieldName     the field from the FieldExtractor to use for filling the slot
   * @return whether the assignment was successful (i.e. the extractor and field exist)
   */
  public boolean assignExtractorField(String className, String slotName, String extractorName, String fieldName) {
    if (!extractorNames.contains(extractorName)) {
      if (verbose) {
        System.err.println("Can't assign slot to unknown extractor: " + extractorName);
      }
      return (false);
    }
    if (!isFieldExtractable(extractorName, fieldName)) {
      if (verbose) {
        System.err.println("Extractor \"" + extractorName + "\" can't extract field: " + fieldName);
      }
      return (false);
    }

    // JS: TODO: if ontology isn't null, check that it has class and slot matching

    // pulls the list of extractable slots
    Map<String, List> extractorFieldsBySlotName = extractableSlotsByClassName.get(className);
    if (extractorFieldsBySlotName == null) {
      // adds this class to the association map if it's never been mentioned before
      extractorFieldsBySlotName = new HashMap<String, List>();
      extractableSlotsByClassName.put(className, extractorFieldsBySlotName);
    }

    List<ExtractorField> extractorFields = extractorFieldsBySlotName.get(slotName);
    if (extractorFields == null) {
      // adds a new list of ExtractorFields for this slot if it's never been mentioned before
      extractorFields = new ArrayList<ExtractorField>();
      extractorFieldsBySlotName.put(slotName, extractorFields);
    }

    // add the extractor and field to this slot's list of ExtractorFields
    ExtractorField ef = new ExtractorField(extractorName, fieldName);
    extractorFields.add(ef);

    return (true);
  }

  /**
   * Removes the responsibility of the FieldExtractor field to fill the given
   * slot of the given ontology class. This does not remove the extractor from
   * the mediator (to do that, call {@link #removeExtractor}). Returns whether
   * the association was found and removed.
   *
   * @return <tt>true</tt> if the given association was found and removed,
   *         <tt>false</tt> otherwise.
   */
  public boolean unassignExtractorField(String className, String slotName, String extractorName, String fieldName) {
    Map extractorFieldsBySlotName = extractableSlotsByClassName.get(className);
    if (extractorFieldsBySlotName == null || extractorFieldsBySlotName.size() == 0) {
      if (verbose) {
        System.err.println("No extractors have been assigned to any slots for " + className);
      }
      return (false);
    }

    List extractorFields = (List) extractorFieldsBySlotName.get(slotName);
    if (extractorFields == null || extractorFields.size() == 0) {
      if (verbose) {
        System.err.println("No extractors have been assigned to " + slotName + " for class " + className);
      }
      return (false);
    }

    // removes the relevant assigned extractor field for this slot
    // cleans out the slot entry and class entry if they become empty
    Iterator efIter = extractorFields.iterator();
    boolean removed = false; // has an extractor field been removed
    while (efIter.hasNext()) {
      ExtractorField ef = (ExtractorField) efIter.next();
      if (ef.getExtractorName().equals(extractorName) && ef.getFieldName().equals(fieldName)) {
        efIter.remove();
        removed = true;
      }
    }
    if (removed) {
      if (extractorFields.isEmpty()) {
        extractorFieldsBySlotName.remove(slotName);
      }
      if (extractorFieldsBySlotName.isEmpty()) {
        extractableSlotsByClassName.remove(className);
      }
    } else if (verbose) {
      System.err.println(extractorName + "." + fieldName + " was never assigned to " + slotName + " for class " + className);
    }

    return (removed);
  }

  /**
   * Removes all extractor fields that have been assigned to this class slot.
   * Returns whether the extractors for this class and slot were found and removed.
   *
   * @return <tt>true</tt> if the assignnments for the given class and slot
   *         were found and removed,  <tt>false</tt> otherwise.
   */
  public boolean removeAllAssignments(String className, String slotName) {
    ExtractorField[] extractorFields = getAssignedExtractorFields(className, slotName);
    if (extractorFields.length == 0) {
      if (verbose) {
        System.err.println("No extractors have been assigned to any slots for " + className);
      }
      return (false);
    }

    // removes all extractor fields for this slot
    for (int i = 0; i < extractorFields.length; i++) {
      if (!unassignExtractorField(className, slotName, extractorFields[i].getExtractorName(), extractorFields[i].getFieldName())) {
        return (false);
      }
    }

    return (true);
  }

  /**
   * Removes all extractor fields that have been assigned to any slot for this class.
   * Returns whether the extractors for this class's slots were found and removed.
   *
   * @return <tt>true</tt> if the assignments for this class were found and removed,
   *         <tt>false</tt> otherwise.
   */
  public boolean removeAllAssignments(String className) {
    String[] slotNames = getExtractableSlots(className);
    if (slotNames.length == 0) {
      if (verbose) {
        System.err.println("No extractors have been assigned to any slots for " + className);
      }
      return (false);
    }

    // removes all extractor fields for this class
    for (int i = 0; i < slotNames.length; i++) {
      if (!removeAllAssignments(className, slotNames[i])) {
        return (false);
      }
    }

    return (true);
  }

  /**
   * Returns the set of unique extractor names (String) managed by this mediator.
   */
  public Set<String> getExtractorNames() {
    return (extractorNames);
  }

  /**
   * Returns the FieldExtractor with the given name, or null if this mediator
   * doesn't manage any extractor with that name. Mediators cache previously
   * loaded extractors, so if this extractor has been fetched before, it is
   * returned immediately. Otherwise it is read from its serialized form,
   * cached, and then returned.
   */
  public FieldExtractor getExtractor(String extractorName) {
    if (!extractorNames.contains(extractorName)) {
      if (verbose) {
        System.err.println("Can't get unknown extractor: " + extractorName);
      }
      return (null);
    }

    // gets the cached extractor or loads it if this is the first time
    FieldExtractor extractor = extractorsByName.get(extractorName);
    if (extractor == null) {
      // attempt to load and cache extractor (comes back null if there was a problem)
      if (verbose) {
        System.err.println("Loading serialized extractor: " + extractorName);
      }
      extractor = ExtractorUtilities.loadExtractor(getSerializedExtractorFile(extractorName));
      if (extractor == null) {
        if (verbose) {
          System.err.println("Unable to load extractor: " + extractorName);
        }
      } else {
        cacheExtractor(extractorName, extractor);
      }
    }

    return (extractor);
  }

  /**
   * Returns the list of extractable fields for the given extractor, or
   * null if no such extractor is maintained by this mediator.
   */
  public String[] getExtractableFields(String extractorName) {
    return extractableFieldsByName.get(extractorName);
  }

  /**
   * Returns a brief textual description of the given extractor, or null
   * if no such extractor is maintained by this mediator.
   */
  public String getDescription(String extractorName) {
    return (descriptionsByName.getProperty(extractorName));
  }

  /**
   * Returns the name of the Java class of which the given extractor is an
   * instance, or null if no such extractor is maintained by this mediator.
   */
  public String getClassName(String extractorName) {
    return (classNamesByName.getProperty(extractorName));
  }

  /**
   * Returns whether the given extractor can extract the given field.
   */
  public boolean isFieldExtractable(String extractorName, String fieldName) {
    String[] extractableFields = getExtractableFields(extractorName);
    if (extractableFields == null) {
      return (false); // no such extractor
    }
    for (int i = 0; i < extractableFields.length; i++) {
      if (fieldName.equals(extractableFields[i])) {
        return (true);
      }
    }

    return (false); // no match found
  }


  /**
   * Adds the given FieldExtractor unless an extractor with the same name
   * already exists. The name, description and class name of the extractor
   * are maintained by the mediator for inventory. In addition, a copy of
   * the given extractor is serialized into this mediator's extractor dir
   * so that it can be loaded in the future. The name of the serialized file
   * is derived from the unique name of the extractor, so it's important that
   * the extractor's name doesn't contain illegal file characters (like '/').
   * Note that serialization is actually performed the next time <tt>store</tt>
   * is called, not when this method is called.
   *
   * @return whether this extractor was successfully added
   * @see FieldExtractor#getName
   * @see #store
   */
  public boolean addExtractor(FieldExtractor extractor) {
    String name = extractor.getName();
    if (extractorNames.contains(name)) {
      if (verbose) {
        System.err.println("We already have an extractor named " + name);
      }
      return (false);
    }

    // stores information on this extractor so it can be described without
    // having to be loaded
    registerExtractor(extractor);

    return (true);
  }

  /**
   * Registers the given extractor and stores its name and information.
   * This is an internal method that should only be called once it's been
   * established that this extractor is not currently managed by the mediator.
   */
  void registerExtractor(FieldExtractor extractor) {
    // caches information for this extractor
    String name = extractor.getName();
    addExtractorName(name);
    cacheExtractor(name, extractor);
    setExtractableFields(name, extractor.getExtractableFields());
    setDescription(name, extractor.getDescription());
    setClassName(name, extractor.getClass().getName());

    // marks the extractor to be serialized next time mediator is stored
    newExtractorNames.add(name);
  }

  /**
   * Adds the given name to the list of extractors (internal use only).
   */
  void addExtractorName(String name) {
    extractorNames.add(name);
    updated = true; // mediator has changed -> can't change extractor dir etc.
  }

  /**
   * Stores the given extractor (internal use only).
   */
  void cacheExtractor(String name, FieldExtractor extractor) {
    extractorsByName.put(name, extractor);
  }

  /**
   * Stores the fields the given extractor can extract (internal use only).
   */
  void setExtractableFields(String name, String[] extractableFields) {
    extractableFieldsByName.put(name, extractableFields);
  }

  /**
   * Stores the description for the given extractor (internal use only).
   */
  void setDescription(String name, String description) {
    descriptionsByName.setProperty(name, description);
  }

  /**
   * Stores the class name for the given extractor (internal use only).
   */
  void setClassName(String name, String className) {
    classNamesByName.setProperty(name, className);
  }

  /**
   * Removes the given extractor from this mediator. All stored information
   * about this extractor is deleted. Any associations between class slots and
   * this extractor are removed. The serialized file for this extractor is
   * marked deletion the next time this mediator is stored.
   * Returns false if there is no extractor with this name, true otherwise.
   *
   * @return whether the extractor was successfully removed.
   */
  public boolean removeExtractor(String extractorName) {
    if (!extractorNames.contains(extractorName)) {
      if (verbose) {
        System.err.println("Can't remove nonexistent extractor: " + extractorName);
      }
      return (false);
    }

    // removes stored information for this extractor
    extractorNames.remove(extractorName);
    extractorsByName.remove(extractorName);
    extractableFieldsByName.remove(extractorName);
    descriptionsByName.remove(extractorName);
    classNamesByName.remove(extractorName);

    // marks the serialized extractor file for deletion when this mediator is next stored
    // if it hasn't been serialized yet, just unmarks it for serialization
    if (newExtractorNames.contains(extractorName)) {
      newExtractorNames.remove(extractorName);
    } else {
      oldExtractorFiles.add(getSerializedExtractorFile(extractorName));
    }

    // removes assignments for this extractor
    Iterator<String> classNameIter = extractableSlotsByClassName.keySet().iterator();
    while (classNameIter.hasNext()) {
      Map extractorFieldsBySlotName = extractableSlotsByClassName.get(classNameIter.next());
      Iterator slotNameIter = extractorFieldsBySlotName.keySet().iterator();
      while (slotNameIter.hasNext()) {
        List extractorFields = (List) extractorFieldsBySlotName.get(slotNameIter.next());
        Iterator efIter = extractorFields.iterator();
        while (efIter.hasNext()) {
          ExtractorField ef = (ExtractorField) efIter.next();
          if (ef.getExtractorName().equals(extractorName)) {
            efIter.remove();
          }
        }
        if (extractorFields.isEmpty()) {
          slotNameIter.remove();
        }
      }
      if (extractorFieldsBySlotName.isEmpty()) {
        classNameIter.remove();
      }
    }

    return (true);
  }

  /**
   * Returns the file where this mediator is stored, or <tt>null</tt> if no
   * file has yet been specified.
   */
  public File getMediatorFile() {
    return (mediatorFile);
  }

  /**
   * Moves the location where the mediator file and serialized extractors will
   * be stored. Copies all serialized extractors for this mediator to the dir
   * that contains newMediatorFile. Does not store the extractor in this new
   * location (call {@link #store} afterwards to do that). Note this means that
   * newly added extractors won't be serialized until store is called.
   */
  public boolean setMediatorFile(File newMediatorFile) {
    try {
      if (mediatorFile != null) {
        File extractorDir = mediatorFile.getParentFile(); // current dir
        File newExtractorDir = newMediatorFile.getParentFile(); // new dir
        newExtractorDir.mkdirs(); // ensure new dir exists
        for (Iterator<String> iter = getExtractorNames().iterator(); iter.hasNext();) {
          // copies over serialized extractors to the new extractor dir
          String name = iter.next();
          File serializedExtractor = getSerializedExtractorFile(name);
          if (serializedExtractor.canRead()) {
            // only copies extractors that can be read (some may not yet have been serialized)
            File newSerializedExtractor = new File(newExtractorDir, serializedExtractor.getName());
            FileCopier.copyFile(serializedExtractor, newSerializedExtractor);
          }
        }
      }
      mediatorFile = newMediatorFile;
      if (verbose) {
        System.err.println("Sucessfully set mediator file to " + mediatorFile);
      }
      return (true);
    } catch (Exception e) {
      if (verbose) {
        e.printStackTrace();
      }
      return (false);
    }
  }

  /**
   * Returns the current ontology mediator being used, or <tt>null</tt> if none has been assigned.
   */
  public OntologyMediator getOntologyMediator() {
    return (ontology);
  }

  /**
   * Sets the ontology mediator to use for restricting possible class/slot assignments
   * for extractors and for filling class slots up the class hierarchy. Passing in
   * <tt>null</tt> effectively disables use of an ontology mediator.
   */
  public void setOntologyMediator(OntologyMediator ontology) {
    this.ontology = ontology;
  }

  /**
   * Returns the list of all class names for which at least one slot has been
   * assigned an extractor.
   */
  public String[] getExtractableClassNames() {
    return new ArrayList<String>(extractableSlotsByClassName.keySet()).toArray(new String[0]);
  }

  /**
   * Returns a list of slot names for the given class that have been assigned
   * extractors. The list may be empty but will never be null.
   */
  public String[] getExtractableSlots(String className) {
    Map extractorFieldsBySlotName = extractableSlotsByClassName.get(className);
    if (extractorFieldsBySlotName == null) {
      return (new String[0]);
    }
    return ((String[]) new ArrayList(extractorFieldsBySlotName.keySet()).toArray(new String[0]));
  }

  /**
   * Returns the list of extractor fields currently assigned to the given class slot.
   * The list may be empty but will never be null.
   */
  public ExtractorField[] getAssignedExtractorFields(String className, String slotName) {
    Map extractorFieldsBySlotName = extractableSlotsByClassName.get(className);
    if (extractorFieldsBySlotName == null) {
      return (new ExtractorField[0]);
    }
    List extractorFields = (List) extractorFieldsBySlotName.get(slotName);
    if (extractorFields == null) {
      return (new ExtractorField[0]);
    }
    return ((ExtractorField[]) extractorFields.toArray(new ExtractorField[0]));
  }

  /**
   * Returns the first extractor field assigned to the given class slot, or
   * null if no extractor field has yet been assigned.
   */
  public ExtractorField getAssignedExtractorField(String className, String slotName) {
    ExtractorField[] extractorFields = getAssignedExtractorFields(className, slotName);
    if (extractorFields.length == 0) {
      return (null);
    } else {
      return (extractorFields[0]);
    }
  }

  /**
   * Returns the File where the serialized extractor for the given name is stored.
   * Extractors are all stored in the same dir as the mediator XML file and are
   * named by their unique name followed by the ".obj" suffix. Note that this
   * method does not verify the existence of the file or extractor name, so
   * the caller is respsonsible for ensuring its validity.
   */
  public File getSerializedExtractorFile(String extractorName) {
    return (new File(mediatorFile.getParent(), extractorName + ".obj"));
  }

  /**
   * Clears all state from this mediator (as if it has just been constructed).
   */
  private void clear() {
    extractorNames.clear();
    extractorsByName.clear();
    extractableFieldsByName.clear();
    descriptionsByName.clear();
    classNamesByName.clear();
    extractableSlotsByClassName.clear();
    newExtractorNames.clear();
    oldExtractorFiles.clear();
    updated = false;
  }

  /**
   * Attempts to load the state of this mediator from the given XML file.
   * If the file does not exist, or if there is a problem reading
   * and/or parsing it, this method will return false, otherwise it will
   * return true upon successful completion. Any existing state in the mediator
   * is wiped out before loading a mediator file.
   *
   * @param mediatorFile XML extractor mediator file to load state from
   * @return whether loading the mediator from its file was successful
   */
  public boolean load(File mediatorFile) {
    this.mediatorFile = mediatorFile;
    clear(); // remove any existing state
    if (verbose) {
      System.err.println("Loading mediator file from " + mediatorFile);
    }
    if (!mediatorFile.exists()) {
      if (verbose) {
        System.err.println("Could not find mediator file: " + mediatorFile);
      }
      return (false);
    }
    return (new EMXMLHandler(this).loadMediator());
  }

  /**
   * Attempts to store the current state of this mediator to an XML file.
   * The file will be stored in the location with which this class was constructed.
   * This will either create a new file or override the file from which this mediator
   * was loaded. If there is any problem writing out the file, this method will
   * return false, otherwise it will return true upon successful completion.
   * Before storing, any extractors that have been removed since the previous
   * storage will have their serialized files removed. Similarly, any newly added
   * extractors will be serialized.
   *
   * @return whether storing the file and the newly serialized extractors was successful
   */
  public boolean store() {

    // removes serialized files for deleted extractors
    for (int i = 0; i < oldExtractorFiles.size(); i++) {
      // deletes the serialized extractor file
      File extractorFile = oldExtractorFiles.get(i);
      if (!extractorFile.delete()) {
        if (verbose) {
          System.err.println("Unable to delete serialized extractor file: " + extractorFile);
        }
      }
    }
    if (verbose && oldExtractorFiles.size() > 0) {
      System.err.println("Removed " + oldExtractorFiles.size() + " old extractor files");
    }
    oldExtractorFiles.clear(); // old extractors have been taken care of

    // creates serialized files for added extractors
    for (Iterator<String> iter = newExtractorNames.iterator(); iter.hasNext();) {
      String name = iter.next();
      FieldExtractor extractor = getExtractor(name);
      File out = getSerializedExtractorFile(name);
      try {
        extractor.storeExtractor(new FileOutputStream(out));
      } catch (Exception e) {
        if (verbose) {
          System.err.println("Unable to serialize extractor to " + out);
          e.printStackTrace();
        }
        return (false);
      }
    }
    if (verbose && newExtractorNames.size() > 0) {
      System.err.println("Serialized " + newExtractorNames.size() + " new extractors");
    }
    newExtractorNames.clear(); // new extractors have been taken care of

    // stores the XML representation of this mediator
    return (new EMXMLHandler(this).storeMediator());
  }

  /**
   * Returns all superclasses of the given class (including itself) from
   * most specific to most general (according to the given ontology).
   * Iterating through the returned list is akin to walking up the superclass
   * ("is-a") hierarchy. In the case of multiple inheritence, classes are
   * ordered by generation but intra-generation ordering is arbitrary.
   * For example, "a isa b isa c" -> [a, b, c], "a isa b isa c ; a isa d isa e"
   * -> "[a, b, d, c, e]. If ontology is null, just className is returned.
   */
  public static List<String> getAncestors(OntologyMediator ontology, String className) {
    List<String> ancestors = new ArrayList<String>(); // superclasses of className (inclusive)
    LinkedList<String> classQueue = new LinkedList<String>(); // classes waiting to have their superclasses pulled

    classQueue.add(className); // start with given class
    while (!classQueue.isEmpty()) {
      // de-queue first element
      className = classQueue.removeFirst();

      // en-queue superclasses
      if (ontology != null) {
        Set<String> superclasses = ontology.getSuperclasses(className);
        if (superclasses != null) {
          classQueue.addAll(superclasses);
        }
      }

      // add de-queued class to ancestor list
      ancestors.add(className);
    }

    return (ancestors);
  }

  /**
   * Runs ExtractorMediator as a standalone command-line app.
   * <pre>Usage: java edu.stanford.nlp.ie.ExtractorMediator mediatorFilename</pre>
   * The command-line interface lets you interact with an extractor mediator
   * from a command prompt. If <tt>mediatorFilename</tt> is an existing file,
   * it is loaded, otherwise a new mediator is created that will write itself
   * to that file. You can add new extractors and assign extractors to class slots,
   * remove old extractors or assignments, list current extractors and assignments,
   * and store the resulting mediator out to XML. You can also extract an instance
   * of a class from a text file using the current set of extractors. Type
   * <tt>?</tt> at the command prompt to see a list of available commands.
   * <p/>
   * This interface is useful for testing the functionality of ExtractorMediator,
   * but is also intended to be a serious command-line interface for building
   * and maintaining mediators.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: java edu.stanford.nlp.ie.ExtractorMediator mediatorFilename");
      System.exit(1);
    }

    File mediatorFile = new File(args[0]);
    ExtractorMediator mediator = new ExtractorMediator();
    if (mediatorFile.exists()) {
      System.out.print("Loading mediator file (" + mediatorFile + ")...");
      if (mediator.load(mediatorFile)) {
        System.out.println("Successful.");
      } else {
        System.out.println("ERROR");
      }
    } else {
      System.out.println("Mediator file not found -> using new mediator");
    }

    // some test examples
    mediator.addExtractor(new RegexpExtractor("time-test", "time", "((1[0-2])|0?[0-9]):[0-5][0-9]( ?[AaPp]\\.?[Mm]\\.?)?"));
    mediator.addExtractor(new RegexpExtractor("price-test", "price", "\\$[1-9][0-9]*\\.[0-9][0-9]"));
    mediator.assignExtractorField("Product", "cost", "price-test", "price");
    mediator.assignExtractorField("Seminar", "start-time", "time-test", "time");


    try {
      // command-prompt loop
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      while (true) {
        System.out.print("command or '?' > ");
        String line = in.readLine().trim();
        if ("?".equals(line)) {
          printCommands();
        } else if (line.startsWith("add ")) {
          String[] addArgs = line.split(" +", 2);
          if (addArgs.length == 2) {
            File extractorFile = new File(addArgs[1]);
            if (extractorFile.canRead()) {
              FieldExtractor extractor = ExtractorUtilities.loadExtractor(extractorFile);
              if (extractor == null) {
                System.out.println("ERROR: unable to load extractor from " + extractorFile);
              } else if (mediator.addExtractor(extractor)) {
                String extractorName = extractor.getName();
                String className = mediator.getClassName(extractorName);
                String description = mediator.getDescription(extractorName);
                String[] extractableFields = mediator.getExtractableFields(extractorName);
                System.out.println("Added new extractor: " + extractorName);
                System.out.println("* class name: " + className);
                System.out.println("* extractable fields: " + StringUtils.join(extractableFields, ", "));
                System.out.println("* description: " + description);
              } else {
                System.out.println("ERROR: mediator already has an extractor named " + extractor.getName());
              }
            } else {
              System.out.println("ERROR: can't read " + extractorFile);
            }
          } else {
            System.out.println("ERROR: must specify a serialized extractor to add");
          }
        } else if (line.startsWith("assign ")) {
          String[] assignArgs = line.split(" +", 5);
          if (assignArgs.length == 5) {
            String className = assignArgs[1];
            String slotName = assignArgs[2];
            String extractorName = assignArgs[3];
            String fieldName = assignArgs[4];
            if (mediator.assignExtractorField(className, slotName, extractorName, fieldName)) {
              System.out.println("Assigned " + className + "." + slotName + " to " + extractorName + "." + fieldName);
            } else {
              System.out.println("ERROR: assignment failed (bad extractor of field)");
            }
          } else {
            System.out.println("ERORR: must specify a class, slot, extractor, and field for assignment");
          }
        } else if (line.startsWith("extract ")) {
          String[] extractArgs = line.split(" +", 3);
          if (extractArgs.length == 3) {
            String className = extractArgs[1];
            File file = new File(extractArgs[2]);
            if (file.isFile() && file.canRead()) {
              System.out.println("Loading " + file + "...");
              String text = IOUtils.slurpFile(file);
              System.out.println("Extracting fields to instantiate a new instance of " + className);
              Map<String, String> extractedFieldsBySlotName = mediator.extractSlotFillers(className, text);
              System.out.println("Extracted slots:");
              Iterator<String> slotNameIter = extractedFieldsBySlotName.keySet().iterator();
              while (slotNameIter.hasNext()) {
                String slotName = slotNameIter.next();
                String extractedField = extractedFieldsBySlotName.get(slotName);
                System.out.println("* " + slotName + ": " + extractedField);
              }
            } else {
              System.out.println("ERROR: can't load " + file);
            }
          } else {
            System.out.println("ERROR: must provide a class name and file name to extract");
          }
        } else if (line.startsWith("fill ")) {
          String[] extractArgs = line.split(" +", 4);
          if (extractArgs.length == 4) {
            String className = extractArgs[1];
            String slotName = extractArgs[2];
            File file = new File(extractArgs[3]);
            if (file.isFile() && file.canRead()) {
              System.out.println("Loading " + file + "...");
              String text = IOUtils.slurpFile(file);
              System.out.println("Extracting filler for " + slotName + " in class " + className);
              String extractedValue = mediator.extractSlotFiller(className, slotName, text);
              System.out.println("Extracted value for " + slotName + ": " + extractedValue);
            } else {
              System.out.println("ERROR: can't load " + file);
            }
          } else {
            System.out.println("ERROR: must provide a class name, slot name, and file name to fill slot");
          }
        } else if ("list assignments".equals(line)) {
          System.out.println("Currently assigned extractor fields (Class.slot -> Extractor.field):");
          String[] classNames = mediator.getExtractableClassNames();
          for (int i = 0; i < classNames.length; i++) {
            String[] slotNames = mediator.getExtractableSlots(classNames[i]);
            for (int j = 0; j < slotNames.length; j++) {
              ExtractorMediator.ExtractorField ef = mediator.getAssignedExtractorField(classNames[i], slotNames[j]);
              System.out.println("* " + classNames[i] + "." + slotNames[j] + " -> " + ef.getExtractorName() + "." + ef.getFieldName());
            }
          }
        } else if ("list extractors".equals(line)) {
          System.out.println("Current extractors managed by this mediator:");
          Iterator<String> extractorNameIter = mediator.getExtractorNames().iterator();
          while (extractorNameIter.hasNext()) {
            String extractorName = extractorNameIter.next();
            String className = mediator.getClassName(extractorName);
            String description = mediator.getDescription(extractorName);
            String[] extractableFields = mediator.getExtractableFields(extractorName);

            System.out.println("* " + extractorName);
            System.out.println("  - class name: " + className);
            System.out.println("  - extractable fields: " + StringUtils.join(extractableFields, ", "));
            System.out.println("  - description: " + description);
          }
        } else if (line.startsWith("remove ")) {
          String[] removeArgs = line.split(" +", 2);
          if (removeArgs.length == 2) {
            String extractorName = removeArgs[1];
            if (mediator.removeExtractor(extractorName)) {
              System.out.println("Removed extractor: " + extractorName);
            } else {
              System.out.println("ERROR: unable to remove extractor: " + extractorName);
            }
          } else {
            System.out.println("ERROR: must specify an extractor to remove");
          }
        } else if ("store".equals(line)) {
          System.out.print("Storing mediator file (" + mediatorFile + ")...");
          if (mediator.store()) {
            System.out.println("Successful.");
          } else {
            System.out.println("ERROR");
          }
        } else if (line.startsWith("unassign ")) {
          String[] assignArgs = line.split(" +", 5);
          if (assignArgs.length == 5) {
            String className = assignArgs[1];
            String slotName = assignArgs[2];
            String extractorName = assignArgs[3];
            String fieldName = assignArgs[4];
            if (mediator.unassignExtractorField(className, slotName, extractorName, fieldName)) {
              System.out.println("Unassigned " + extractorName + "." + fieldName + " for " + className + "." + slotName);
            } else {
              System.out.println("ERROR: unassignment failed (bad extractor of field)");
            }
          } else {
            System.out.println("ERORR: must specify a class, slot, extractor, and field for unassignment");
          }
        } else if ("exit".equals(line)) {
          System.out.println("Goodbye!");
          System.exit(0);
        } else {
          System.out.println("UNRECOGNIZED COMMAND - type ? for help");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Prints a list of commands for this application.
   */
  private static void printCommands() {
    System.out.println();
    System.out.println("Possible commands:");
    System.out.println("add <extractor> - add a serialized FieldExtractor");
    System.out.println("assign <class> <slot> <extractor> <field> - associate an extractor field with a class slot");
    System.out.println("extract <class> <file> - extract an instance of <class> from the text in <file>");
    System.out.println("fill <class> <slot> <file> - fill a single class slot from the text in <file>");
    System.out.println("list assignments - print current extractor field assignments");
    System.out.println("list extractors - print current list of extractors");
    System.out.println("remove <extractor> - remove field extractor by name");
    System.out.println("store - write out XML for this mediator");
    System.out.println("unassign <class> <slot> <extractor> <field> - disassociate an extractor field with a class slot");
    System.out.println("exit - quit this application");
  }

  /**
   * Represents a particular extractable field of a particular FieldExtractor.
   */
  public static class ExtractorField {
    /**
     * Unqiue name of the FieldExtractor.
     */
    private String extractorName;
    /**
     * Extractable field of the FieldExtractor.
     */
    private String fieldName;

    /**
     * Constructs a new Extractor field for the given extractor and field.
     */
    public ExtractorField(String extractorName, String fieldName) {
      this.extractorName = extractorName;
      this.fieldName = fieldName;
    }

    /**
     * Returns the unique name of the FieldExtractor.
     */
    public String getExtractorName() {
      return (extractorName);
    }

    /**
     * Returns the extractable field of the FieldExtractor.
     */
    public String getFieldName() {
      return (fieldName);
    }

    /**
     * Returns a String representation of the extractor name and field.
     */
    public String toString() {
      return ("ExtractorField[" + extractorName + "." + fieldName + "]");
    }
  }
}
