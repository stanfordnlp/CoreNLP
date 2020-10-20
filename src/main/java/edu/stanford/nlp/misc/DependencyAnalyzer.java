package edu.stanford.nlp.misc; 
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Parses the output of DependencyExtractor into a tree, and constructs
 * transitive dependency closures of any set of classes.
 *
 * @author Jamie Nicolson (nicolson@cs.stanford.edu)
 */
public class DependencyAnalyzer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DependencyAnalyzer.class);

  /** Make true to record the dependencies as they are calculated. */
  private static final boolean VERBOSE = false;

  /**
   * Represents a package, class, method, or field in the dependency tree.
   */
  static class Identifier implements Comparable<Identifier> {
    public String name;

    /**
     * The set of Identifiers that are directly dependent on this one.
     */
    public Set<Identifier> ingoingDependencies = Generics.newHashSet();

    /**
     * The set of Identifiers upon which this Identifier is directly
     * dependent.
     */
    public Set<Identifier> outgoingDependencies = Generics.newHashSet();

    /**
     * True if this Identifier represents a class. It might be nicer
     * to use an enumerated type for all the types of Identifiers, but
     * for now all we care about is whether it is a class.
     */
    boolean isClass = false;

    public Identifier(String name) {
      this.name = name;
    }

    /**
     * Two identifiers are equal() if and only if their fully-qualified
     * names are the same.
     */
    @Override
    public boolean equals(Object obj) {
      return (obj != null) && (obj instanceof Identifier) && ((Identifier) obj).name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    public int compareTo(Identifier o) {
      return name.compareTo(o.name);
    }

    @Override
    public String toString() {
      return name;
    }

  } // end static class Identifier

  private Map<String,Identifier> identifiers = Generics.newHashMap();

  /**
   * Adds the starting classes to depQueue and closure.
   * Allows * as a wildcard for class names.
   */
  void addStartingClasses(LinkedList<Identifier> depQueue,
                          Set<Identifier> closure,
                          List<String> startingClasses) {
    // build patterns out of the given class names
    // escape . and $, turn * into .* for a regular expression
    Pattern[] startingPatterns = new Pattern[startingClasses.size()];
    boolean[] matched = new boolean[startingClasses.size()];
    for (int i = 0; i < startingClasses.size(); ++i) {
      String startingClass = startingClasses.get(i);
      startingClass = startingClass.replaceAll("\\.", "\\\\\\.");
      startingClass = startingClass.replaceAll("\\$", "\\\\\\$");
      startingClass = startingClass.replaceAll("\\*", ".*");
      startingPatterns[i] = Pattern.compile(startingClass);

      matched[i] = false;
    }

    // must iterate over every identifier, since we don't know which
    // ones will match any given expression
    for (Identifier id : identifiers.values()) {
      if (!id.isClass)
        continue;
      for (int i = 0; i < startingClasses.size(); ++i) {
        if (startingPatterns[i].matcher(id.name).matches()) {
          depQueue.addLast(id);
          closure.add(id);
          matched[i] = true;
          if (VERBOSE) {
            log.info("Starting class: " + id.name);
          }
          break;
        }
      }
    }

    for (int i = 0; i < startingClasses.size(); ++i) {
      if (!matched[i]) {
        log.info("Warning: pattern " + startingClasses.get(i) +
                           " matched nothing");
      }
    }
  }

  /**
   * Constructs the transitive closure of outgoing dependencies starting
   * from the given classes. That is, the returned collection is all the
   * classes that might be needed in order to use the given classes.
   * If none of the given classes are found, an empty collection is returned.
   *
   * @param startingClassNames A Collection of Strings, each the
   *                           fully-qualified name of a class. These are the starting elements of
   *                           the transitive closure.
   * @return A collection of Identifiers, each representing a class,
   *         that are the transitive closure of the starting classes.
   */
  public Collection<Identifier> transitiveClosure(List<String> startingClassNames) {
    Set<Identifier> closure = Generics.newHashSet();

    // The depQueue is the queue of items in the closure whose dependencies
    // have yet to be scanned.
    LinkedList<Identifier> depQueue = new LinkedList<>();

    // add all the starting classes to the closure and the depQueue
    addStartingClasses(depQueue, closure, startingClassNames);

    // Now work through the dependency queue, adding dependencies until
    // there are none left.
    while (!depQueue.isEmpty()) {
      Identifier id = depQueue.removeFirst();

      for (Identifier outgoingDependency : id.outgoingDependencies) {
        if (outgoingDependency.isClass && !closure.contains(outgoingDependency)) {
          if (VERBOSE) log.info("Added " + outgoingDependency + " due to " + id);
          depQueue.addLast(outgoingDependency);
          closure.add(outgoingDependency);
        }
      }
    }

    return closure;
  }

  //
  // These regular expressions are used to parse the raw output
  // of DependencyExtractor.
  //
  public static final Pattern pkgLine = Pattern.compile("(\\S*)(?:\\s+\\*)?\\s*");
  public static final Pattern classLine = Pattern.compile("    ([^<]\\S*)(?:\\s+\\*)?\\s*");
  public static final Pattern memberLine = Pattern.compile("        ([a-zA-Z_\\$]{1}.*)");
  public static final Pattern inDepLine = Pattern.compile("\\s*<-- (.*)");
  public static final Pattern outDepLine = Pattern.compile("\\s*--> (.*)");
  public static final Pattern bothDepLine = Pattern.compile("\\s*<-> (.*)");

  /**
   * Takes a dependency closure generated by DependencyExtractor, and prints out the class names of exactly
   * those classes in the closure that are in an <code>edu.stanford.nlp</code>-prepended package.
   *
   * @param args takes one argument: the name of a file that contains the output of a run of
   * DependencyExtractor
   */
  public static void main(String[] args) throws Exception {

    DependencyAnalyzer da = new DependencyAnalyzer(args[0]);

    ArrayList<String> startingClasses = new ArrayList<>(args.length - 1);
    for (int i = 1; i < args.length; ++i) {
      startingClasses.add(args[i]);
    }

    Collection<Identifier> closure = da.transitiveClosure(startingClasses);

    ArrayList<Identifier> sortedClosure = new ArrayList<>(closure);
    Collections.sort(sortedClosure);
    Set<String> alreadyOutput = Generics.newHashSet();
    for (Identifier identifier : sortedClosure) {
      String name = identifier.name;
      if (name.startsWith("edu.stanford.nlp")) {
        name = name.replace('.', '/') + ".class";
        // no need to output [] in the class names
        name = name.replaceAll("\\[\\]", "");
        // filter by uniqueness in case there were array classes found
        if (alreadyOutput.contains(name))
          continue;

        alreadyOutput.add(name);
        System.out.println(name);
      }
    }
  }

  public static String prependPackage(String pkgname, String classname) {
    if( pkgname.equals("") ) {
      return classname;
    } else {
      return pkgname + "." + classname;
    }
  }

  /**
   * Constructs a DependencyAnalyzer from the output of DependencyExtractor.
   * The data will be converted into a dependency tree.
   *
   * @param filename The path of a file containing the output of a run
   *                 of DependencyExtractor.
   */
  public DependencyAnalyzer(String filename) throws IOException {
    BufferedReader input = new BufferedReader(new FileReader(filename));

    String line;
    Identifier curPackage = null;
    Identifier curClass = null;

    while ((line = input.readLine()) != null) {

      Matcher matcher = pkgLine.matcher(line);
      String name;
      if (matcher.matches()) {
        name = matcher.group(1);
        curPackage = canonicalIdentifier(name);
        curClass = null;
        //log.info("Found package " + curPackage.name);
      } else {
        matcher = classLine.matcher(line);
        if (matcher.matches()) {
          name = prependPackage(curPackage.name, matcher.group(1));
          curClass = canonicalIdentifier(name);
          curClass.isClass = true;
          //curPackage.classes.add(curClass);
          //log.info("Found class " + curClass.name);
        } else {
          matcher = memberLine.matcher(line);
          if (matcher.matches()) {
            name = curClass.name + "." + matcher.group(1);
            //log.info("Found member: " + name );
          } else {
            matcher = inDepLine.matcher(line);
            if (matcher.matches()) {
              name = matcher.group(1);
              Identifier inDep = canonicalIdentifier(name);
              if (curClass != null) {
                curClass.ingoingDependencies.add(inDep);
              }
              //log.info("Found ingoing depedency: " +
              //    name);
            } else {
              matcher = outDepLine.matcher(line);
              if (matcher.matches()) {
                name = matcher.group(1);
                Identifier outDep = canonicalIdentifier(name);
                if (curClass != null) {
                  curClass.outgoingDependencies.add(outDep);
                }
                //log.info("Found outgoing dependency: " +
                //    name);
              } else {
                matcher = bothDepLine.matcher(line);
                if (matcher.matches()) {
                  name = matcher.group(1);
                  Identifier dep = canonicalIdentifier(name);
                  if (curClass != null) {
                    curClass.ingoingDependencies.add(dep);
                    curClass.outgoingDependencies.add(dep);
                  }
                } else {
                  log.info("Found unmatching line: " + line);
                }
              }
            }
          }
        }
      }
    }

    // After reading the dependencies, as a post-processing step we
    // connect all inner classes and outer classes with each other.
    for (Map.Entry<String, Identifier> entry : identifiers.entrySet()) {
      Identifier classId = entry.getValue();
      if (!classId.isClass) {
        continue;
      }
      String className = entry.getKey();
      int baseIndex = className.indexOf("$");
      if (baseIndex < 0) {
        continue;
      }
      String baseName = className.substring(0, baseIndex);
      Identifier baseId = identifiers.get(baseName);
      if (baseId == null) {
        continue;
      }
      baseId.ingoingDependencies.add(classId);
      baseId.outgoingDependencies.add(classId);
      classId.ingoingDependencies.add(baseId);
      classId.outgoingDependencies.add(baseId);
    }

  }

  /**
   * Returns the canonical Identifier with the given name.
   *
   * @param name The name of an Identifier.
   * @return The Identifier, which will have been newly created if it
   *         did not already exist.
   */
  private Identifier canonicalIdentifier(String name) {
    Identifier ident = identifiers.get(name);
    if (ident == null) {
      ident = new Identifier(name);
      identifiers.put(name, ident);
    }
    return ident;
  }

}
