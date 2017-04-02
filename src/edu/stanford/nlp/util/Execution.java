package edu.stanford.nlp.util;

import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A class to set command line options. To use, create a static class into which you'd like
 * to put your properties. Then, for each field, set the annotation:
 *
 * <pre>
 *   <code>
 *     import edu.stanford.nlp.util.Execution.Option
 *
 *     class Props {
 *       &#64;Option(name="anIntOption", required=false)
 *       public static int anIntOption = 7 // default value is 7
 *       &#64;Option(name="anotherOption", required=false)
 *       public static File aCastableOption = new File("/foo")
 *     }
 *   </code>
 * </pre>
 *
 * <p>
 *   You can then set options with {@link Execution#exec(Runnable, String[])},
 *   or with {@link Execution#fillOptions(java.util.Properties)}.
 * </p>
 *
 * <p>
 *   If your default classpath has many classes in it, you can select a subset of them
 *   by using {@link Execution#fillOptions(Class[], java.util.Properties)}, or some variant.
 * </p>
 */
public class Execution {

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface Option {
    String name() default "";

    String gloss() default "";

    boolean required() default false;

    String alt() default "";
  }

  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  private static final String[] IGNORED_JARS = {
  };
  private static final Class[] BOOTSTRAP_CLASSES = {
      Execution.class,
  };

  @Option(name = "option_classes", gloss = "Fill options from these classes")
  public static Class<?>[] optionClasses = null;
  @Option(name = "threads", gloss = "Number of threads on machine")
  public static int threads = Runtime.getRuntime().availableProcessors();
  @Option(name = "host", gloss = "Name of computer we are running on")
  public static String host = "(unknown)";
  @SuppressWarnings("FieldCanBeLocal")
  @Option(name = "strict", gloss = "If true, make sure that all options passed in are used somewhere")
  private static boolean strict = false;
  @SuppressWarnings("FieldCanBeLocal")
  @Option(name = "exec.verbose", gloss = "If true, print options as they are set.")
  private static boolean verbose = false;

  static {
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
    }
  }

  /**
   * A lazy iterator over files, not loading all of them into memory at once.
   */
  public static class LazyFileIterator implements Iterator<File> {

    private FilenameFilter filter;
    private File[] dir;
    private Stack<File[]> parents = new Stack<File[]>();
    private Stack<Integer> indices = new Stack<Integer>();

    private int toReturn = -1;

    public LazyFileIterator(File path, final String filter) {
      this(path, (file, name) -> {
        String filePath = (file.getPath() + "/" + name);
        return new File(filePath).isDirectory() || filePath.matches(filter);
      });
    }

    public LazyFileIterator(File dir, FilenameFilter filter) {
      if (!dir.exists()) throw new IllegalArgumentException("Could not find directory: " + dir.getPath());
      if (!dir.isDirectory()) throw new IllegalArgumentException("Not a directory: " + dir.getPath());
      this.filter = filter;
      this.dir = dir.listFiles(filter);
      enqueue();
    }

    private void enqueue() {
      toReturn += 1;
      boolean good = (toReturn < dir.length && !dir[toReturn].isDirectory());
      while (!good) {
        if (toReturn >= dir.length) {
          //(case: pop)
          if (parents.isEmpty()) {
            toReturn = -1;
            return;  //this is where we exit
          } else {
            dir = parents.pop();
            toReturn = indices.pop();
          }
        } else if (dir[toReturn].isDirectory()) {
          //(case: push)
          parents.push(dir);
          indices.push(toReturn + 1);
          dir = dir[toReturn].listFiles(filter);
          toReturn = 0;
        } else {
          throw new IllegalStateException("File is invalid, but in range and not a directory: " + dir[toReturn]);
        }
        //(check if good)
        good = (toReturn < dir.length && !dir[toReturn].isDirectory());
      }
      // if we reach here we found something
    }

    @Override
    public boolean hasNext() {
      return toReturn >= 0;
    }

    @Override
    public File next() {
      if (toReturn >= dir.length || toReturn < 0) throw new IllegalStateException("No more elements!");
      File rtn = dir[toReturn];
      enqueue();
      return rtn;
    }

    @Override
    public void remove() {
      throw new IllegalArgumentException("NOT IMPLEMENTED");
    }

  }



	/*
   * ----------
	 * OPTIONS
	 * ----------
	 */

  private static void fillField(Object instance, Field f, String value) {
    //--Verbose
    if (verbose) {
      Option opt = f.getAnnotation(Option.class);
      StringBuilder b = new StringBuilder("setting ").append(f.getDeclaringClass().getName()).append("#").append(f.getName()).append(" ");
      if (opt != null) {
        b.append("[").append(opt.name()).append("] ");
      }
      b.append("to: ").append(value);
      log(b.toString());
    }

    try {
      //--Permissions
      boolean accessState = true;
      if (Modifier.isFinal(f.getModifiers())) {
        fatal("Option cannot be final: " + f);
      }
      if (!f.isAccessible()) {
        accessState = false;
        f.setAccessible(true);
      }
      //--Set Value
      Object objVal = MetaClass.cast(value, f.getGenericType());
      if (objVal != null) {
        if (objVal.getClass().isArray()) {
          //(case: array)
          Object[] array = (Object[]) objVal;
          // error check
          if (!f.getType().isArray()) {
            fatal("Setting an array to a non-array field. field: " + f + " value: " + Arrays.toString(array) + " src: " + value);
          }
          // create specific array
          Object toSet = Array.newInstance(f.getType().getComponentType(), array.length);
          for (int i = 0; i < array.length; i++) {
            Array.set(toSet, i, array[i]);
          }
          // set value
          f.set(instance, toSet);
        } else {
          //case: not array
          f.set(instance, objVal);
        }
      } else {
        fatal("Cannot assign option field: " + f + " value: " + value + "; invalid type");
      }
      //--Permissions
      if (!accessState) {
        f.setAccessible(false);
      }
    } catch (IllegalArgumentException e) {
      err(e);
      fatal("Cannot assign option field: " + f.getDeclaringClass().getCanonicalName() + "." + f.getName() + " value: " + value + " cause: " + e.getMessage());
    } catch (IllegalAccessException e) {
      err(e);
      fatal("Cannot access option field: " + f.getDeclaringClass().getCanonicalName() + "." + f.getName());
    } catch (Exception e) {
      err(e);
      fatal("Cannot assign option field: " + f.getDeclaringClass().getCanonicalName() + "." + f.getName() + " value: " + value + " cause: " + e.getMessage());
    }
  }

  @SuppressWarnings("rawtypes")
  private static Class filePathToClass(String cpEntry, String path) {
    if (path.length() <= cpEntry.length()) {
      throw new IllegalArgumentException("Illegal path: cp=" + cpEntry
          + " path=" + path);
    }
    if (path.charAt(cpEntry.length()) != '/') {
      throw new IllegalArgumentException("Illegal path: cp=" + cpEntry
          + " path=" + path);
    }
    path = path.substring(cpEntry.length() + 1);
    path = path.replaceAll("/", ".").substring(0, path.length() - 6);
    try {
      return Class.forName(path,
          false,
          ClassLoader.getSystemClassLoader());
    } catch (ClassNotFoundException e) {
      throw fail("Could not load class at path: " + path);
    } catch (NoClassDefFoundError ex) {
      warn("Class at path " + path + " is unloadable");
      return null;
    }
  }

  private static boolean isIgnored(String path) {
    for (String ignore : IGNORED_JARS) {
      if (path.endsWith(ignore)) {
        return true;
      }
    }
    return false;
  }

  public static Class<?>[] getVisibleClasses() {
    //--Variables
    List<Class<?>> classes = new ArrayList<Class<?>>();
    // (get classpath)
    String pathSep = System.getProperty("path.separator");
    String[] cp = System.getProperties().getProperty("java.class.path",
        null).split(pathSep);
    // --Fill Options
    // (get classes)
    for (String entry : cp) {
      log("Checking cp " + entry);
      //(should skip?)
      if (entry.equals(".") || entry.trim().length() == 0) {
        continue;
      }
      //(no, don't skip)
      File f = new File(entry);
      if (f.isDirectory()) {
        // --Case: Files
        LazyFileIterator iter = new LazyFileIterator(f, ".*class$");
        while (iter.hasNext()) {
          //(get the associated class)
          Class<?> clazz = filePathToClass(entry, iter.next().getPath());
          if (clazz != null) {
            //(add the class if it's valid)
            classes.add(clazz);
          }
        }
      } else //noinspection StatementWithEmptyBody
        if (!isIgnored(entry)) {
        // --Case: Jar
        try {
          JarFile jar = new JarFile(f);
          Enumeration<JarEntry> e = jar.entries();
          while (e.hasMoreElements()) {
            //(for each jar file element)
            JarEntry jarEntry = e.nextElement();
            String clazz = jarEntry.getName();
            if (clazz.matches(".*class$")) {
              //(if it's a class)
              clazz = clazz.substring(0, clazz.length() - 6)
                  .replaceAll("/", ".");
              //(add it)
              try {
                classes.add(
                    Class.forName(clazz,
                        false,
                        ClassLoader.getSystemClassLoader()));
              } catch (ClassNotFoundException ex) {
                warn("Could not load class in jar: " + f + " at path: " + clazz);
              } catch (NoClassDefFoundError ex) {
                debug("Could not scan class: " + clazz + " (in jar: " + f + ")");
              }
            }
          }
        } catch (IOException e) {
          warn("Could not open jar file: " + f + "(are you sure the file exists?)");
        }
      } else {
        //case: ignored jar
      }
    }

    return classes.toArray(new Class<?>[classes.size()]);
  }

  /**
   * Get all the declared fields of this class and all super classes.
   */
  private static Field[] scrapeFields(Class<?> clazz) throws Exception {
    List<Field> fields = new ArrayList<>();
    while (clazz != null && !clazz.equals(Object.class)) {
      fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
      clazz = clazz.getSuperclass();
    }
    return fields.toArray(new Field[fields.size()]);
  }


  @SuppressWarnings("rawtypes")
  protected static Map<String, Field> fillOptionsImpl(
      Object[] instances,
      Class<?>[] classes,
      Properties options,
      boolean ensureAllOptions) {

    //--Create Class->Object Mapping
    Map<Class, Object> class2object = new HashMap<Class, Object>();
    if (instances != null) {
      for (int i = 0; i < classes.length; ++i) {
        assert instances[i].getClass() == classes[i];
        class2object.put(classes[i], instances[i]);
        Class<?> mySuper = instances[i].getClass().getSuperclass();
        while (mySuper != null && !mySuper.equals(Object.class)) {
          if (!class2object.containsKey(mySuper)) {
            class2object.put(mySuper, instances[i]);
          }
          mySuper = mySuper.getSuperclass();
        }
      }
    }

    //--Get Fillable Options
    Map<String, Field> canFill = new HashMap<String, Field>();
    Map<String, Pair<Boolean, Boolean>> required = new HashMap<String, Pair<Boolean, Boolean>>(); /* <exists, is_set> */
    Map<String, String> interner = new HashMap<String, String>();
    for (Class c : classes) {
      Field[] fields;
      try {
        fields = scrapeFields(c);
      } catch (Throwable e) {
        debug("Could not check fields for class: " + c.getName() + "  (caused by " + e.getClass() + ": " + e.getMessage() + ")");
        continue;
      }

      boolean someOptionFilled = false;
      boolean someOptionFound = false;
      for (Field f : fields) {
        Option o = f.getAnnotation(Option.class);
        if (o != null) {
          someOptionFound = true;
          //(check if field is static)
          if ((f.getModifiers() & Modifier.STATIC) == 0 && instances == null) {
            continue;
          }
          someOptionFilled = true;
          //(required marker)
          Pair<Boolean, Boolean> mark = Pair.makePair(false, false);
          if (o.required()) {
            mark = Pair.makePair(true, false);
          }
          //(add main name)
          String name = o.name().toLowerCase();
          if (name.equals("")) {
            name = f.getName().toLowerCase();
          }
          if (canFill.containsKey(name)) {
            String name1 = canFill.get(name).getDeclaringClass().getCanonicalName() + "." + canFill.get(name).getName();
            String name2 = f.getDeclaringClass().getCanonicalName() + "." + f.getName();
            if (!name1.equals(name2)) {
              fatal("Multiple declarations of option " + name + ": " + name1 + " and " + name2);
            } else {
              err("Class is in classpath multiple times: " + canFill.get(name).getDeclaringClass().getCanonicalName());
            }
          }
          canFill.put(name, f);
          required.put(name, mark);
          interner.put(name, name);
          //(add alternate names)
          if (!o.alt().equals("")) {
            for (String alt : o.alt().split(" *, *")) {
              alt = alt.toLowerCase();
              if (canFill.containsKey(alt) && !alt.equals(name))
                throw new IllegalArgumentException("Multiple declarations of option " + alt + ": " + canFill.get(alt) + " and " + f);
              canFill.put(alt, f);
              if (mark.first) required.put(alt, mark);
              interner.put(alt, name);
            }
          }
        }
      }
      //(check to ensure that something got filled, if any @Option annotation was found)
      if (someOptionFound && !someOptionFilled) {
        warn("found @Option annotations in class " + c + ", but didn't set any of them (all options were instance variables and no instance given?)");
      }
    }

    //--Fill Options
    for (Object rawKey : options.keySet()) {
      String rawKeyStr = rawKey.toString();
      String key = rawKey.toString().toLowerCase();
      // (get values)
      String value = options.get(rawKey).toString();
      assert value != null;
      Field target = canFill.get(key);
      // (mark required option as fulfilled)
      Pair<Boolean, Boolean> mark = required.get(key);
      if (mark != null && mark.first) {
        required.put(key, Pair.makePair(true, true));
      }
      // (fill the field)
      if (target != null) {
        // (case: declared option)z
        fillField(class2object.get(target.getDeclaringClass()), target, value);
      } else if (ensureAllOptions) {
        // (case: undeclared option)
        // split the key
        int lastDotIndex = rawKeyStr.lastIndexOf('.');
        if (lastDotIndex < 0) {
          err("Unrecognized option: " + key);
          continue;
        }
        if (!rawKeyStr.startsWith("log.")) {  // ignore Redwood options
          String className = rawKeyStr.substring(0, lastDotIndex);
          String fieldName = rawKeyStr.substring(lastDotIndex + 1);
          // get the class
          Class clazz = null;
          try {
            clazz = ClassLoader.getSystemClassLoader().loadClass(className);
          } catch (Exception e) {
            err("Could not set option: " + rawKey + "; either the option is mistyped, not defined, or the class " + className + " does not exist.");
          }
          // get the field
          if (clazz != null) {
            try {
              target = clazz.getField(fieldName);
            } catch (Exception e) {
              err("Could not set option: " + rawKey + "; no such field: " + fieldName + " in class: " + className);
            }
            if (target != null) {
              log("option overrides " + target + " to '" + value + "'");
              fillField(class2object.get(target.getDeclaringClass()), target, value);
            } else {
              err("Could not set option: " + rawKey + "; no such field: " + fieldName + " in class: " + className);
            }
          }
        }
      }
    }

    //--Ensure Required
    boolean good = true;
    for (String key : required.keySet()) {
      Pair<Boolean, Boolean> mark = required.get(key);
      if (mark.first && !mark.second) {
        err("Missing required option: " + interner.get(key) + "   <in class: " + canFill.get(key).getDeclaringClass() + ">");
        required.put(key, Pair.makePair(true, true));  //don't duplicate error messages
        good = false;
      }
    }
    if (!good) {
      System.exit(1);
    }

    return canFill;
  }

  protected static Map<String, Field> fillOptionsImpl(
      Object[] instances,
      Class<?>[] classes,
      Properties options) {
    return fillOptionsImpl(instances, classes, options, strict);
  }


	/*
	 * ----------
	 * EXECUTION
	 * ----------
	 */

  public static void fillOptions(Class<?>[] classes, Properties options) {
    fillOptionsImpl(null, classes, options);
  }

  public static void fillOptions(Class<?> clazz, Properties options) {
    fillOptionsImpl(null, new Class[]{ clazz }, options);
  }

  public static void fillOptions(Properties props, String[] args) {
    //(convert to map)
    Properties options = StringUtils.argsToProperties(args);
    for (String key : props.stringPropertyNames()) {
      options.setProperty(key, props.getProperty(key));
    }
    //(bootstrap)
    Map<String, Field> bootstrapMap = fillOptionsImpl(null, BOOTSTRAP_CLASSES, options, false); //bootstrap
    for (String key : bootstrapMap.keySet()) {
      options.remove(key);
    }
    //(fill options)
    Class<?>[] visibleClasses = optionClasses;
    if (visibleClasses == null) visibleClasses = getVisibleClasses(); //get classes
    fillOptionsImpl(null, visibleClasses, options);//fill
  }

  @SuppressWarnings("UnusedDeclaration")
  public static void fillOptions(Class<?>[] optionClasses, Properties props, String[] args) {
    Execution.optionClasses = optionClasses;
    fillOptions(props, args);

  }

  public static void fillOptions(Properties props) {
    fillOptions(props, new String[0]);
  }

  public static void fillOptions(Class<?>[] classes,
                                  String[] args) {
    Properties options = StringUtils.argsToProperties(args); //get options
    fillOptionsImpl(null, BOOTSTRAP_CLASSES, options, false); //bootstrap
    fillOptionsImpl(null, classes, options);
  }

  public static void fillOptions(Class<?> clazz,
                                 String[] args) {
    Class<?>[] classes = new Class<?>[1];
    classes[0] = clazz;
    fillOptions(classes, args);
  }

  public static void fillOptions(Object[] instances, Properties options) {
    Class[] classes = new Class[instances.length];
    for (int i = 0; i < classes.length; ++i) { classes[i] = instances[i].getClass(); }
    fillOptionsImpl(instances, classes, options);
  }

  public static void fillOptions(Object instance, Properties options) {
    fillOptions(new Object[]{ instance }, options);
  }

  public static void fillOptions(Object[] instances,
                                 String[] args) {
    Properties options = StringUtils.argsToProperties(args); //get options
    fillOptionsImpl(null, BOOTSTRAP_CLASSES, options, false); //bootstrap
    Class[] classes = new Class[instances.length];
    for (int i = 0; i < classes.length; ++i) { classes[i] = instances[i].getClass(); }
    fillOptionsImpl(instances, classes, options);
  }

  public static void fillOptions(Object instance,
                                 String[] args) {
    fillOptions(new Object[]{ instance }, args);
  }

  public static void exec(Runnable toRun) {
    exec(toRun, new String[0]);
  }

  public static void exec(Runnable toRun, Class[] optionClasses) {
    Execution.optionClasses = optionClasses;
    exec(toRun, new String[0]);
  }

  public static void exec(Runnable toRun, String[] args) {
    exec(toRun, args, false);
  }

  public static void exec(Runnable toRun, String[] args, Class[] optionClasses) {
    Execution.optionClasses = optionClasses;
    exec(toRun, args, false);
  }
  public static void exec(Runnable toRun, String[] args, Class[] optionClasses, boolean exit) {
    Execution.optionClasses = optionClasses;
    exec(toRun, StringUtils.argsToProperties(args), exit);
  }

  public static void exec(Runnable toRun, String[] args, boolean exit) {
    exec(toRun, StringUtils.argsToProperties(args), exit);
  }

  public static void exec(Runnable toRun, Properties options) {
    exec(toRun, options, false);
  }

  public static void exec(Runnable toRun, Properties options, boolean exit) {
    //--Init
    //(bootstrap)
    Map<String, Field> bootstrapMap = fillOptionsImpl(null, BOOTSTRAP_CLASSES, options, false); //bootstrap
    for (String key : bootstrapMap.keySet()) {
      options.remove(key);
    }
    startTrack("init");
    //(fill options)
    Class<?>[] visibleClasses = optionClasses;
    if (visibleClasses == null) visibleClasses = getVisibleClasses(); //get classes
    fillOptionsImpl(null, visibleClasses, options);//fill
    endTrack("init");
    // -- Setup Logging
    StanfordRedwoodConfiguration.apply(options);
    //--Run Program
    int exitCode = 0;
    startTrack("main");
    try {
      toRun.run();
    } catch (Throwable t) {
      log(FORCE, t);
      exitCode = 1;
    }
    endTracksTo("main");  // end main
    if (exit) {
      System.exit(exitCode);
    }
  }

  private static String threadRootClass() {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    StackTraceElement elem = trace[trace.length - 1];
    return elem.getClassName();
  }

  @SuppressWarnings("UnusedDeclaration")
  public static void usageAndExit(String[] expectedArgs) {
    String clazz = threadRootClass();
    StringBuilder b = new StringBuilder();
    b.append("USAGE: ").append(clazz).append(" ");
    for (String arg : expectedArgs) {
      b.append(arg).append(" ");
    }
    System.out.println(b.toString());
    System.exit(0);
  }

  @SuppressWarnings("UnusedDeclaration")
  public static void usageAndExit(Map<String, String[]> argToFlagsMap) {
    String clazz = threadRootClass();
    StringBuilder b = new StringBuilder();
    b.append("USAGE: ").append(clazz).append("\n\t");
    for (String arg : argToFlagsMap.keySet()) {
      String[] flags = argToFlagsMap.get(arg);
      if (flags == null || flags.length == 0) {
        throw new IllegalArgumentException(
            "No flags registered for arg: " + arg);
      }
      b.append("{");
      for (int i = 0; i < flags.length - 1; i++) {
        b.append(flags[i]).append(",");
      }
      b.append(flags[flags.length - 1]).append("}");
    }
    System.out.println(b.toString());
    System.exit(0);
  }


}
