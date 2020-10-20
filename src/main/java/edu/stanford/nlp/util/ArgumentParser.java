package edu.stanford.nlp.util;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A class to set command line options. To use, create a static class into which you'd like
 * to put your properties. Then, for each field, set the annotation:
 *
 * <pre><code>
 *     import edu.stanford.nlp.util.ArgumentParser.Option
 *
 *     class Props {
 *       &#64;Option(name="anIntOption", required=false, gloss="This is an int")
 *       public static int anIntOption = 7; // default value is 7
 *       &#64;Option(name="anotherOption", required=false)
 *       public static File aCastableOption = new File("/foo");
 *     }
 * </code></pre>
 *
 * You can then set options with {@link ArgumentParser#fillOptions(String...)},
 * or with {@link ArgumentParser#fillOptions(java.util.Properties)}.
 *
 * If your default classpath has many classes in it, you can select a subset of them
 * by using {@link ArgumentParser#fillOptions(Class[], java.util.Properties)}, or some variant.
 *
 * A complete toy example looks like this:
 *
 * <pre><code>
 *     import java.util.Properties;
 *
 *     import edu.stanford.nlp.util.ArgumentParser;
 *     import edu.stanford.nlp.util.StringUtils;
 *
 *     public class Foo {
 *
 *       &#64;ArgumentParser.Option(name="bar", gloss="This is a string option.", required=true)
 *       private static String BAR = null;
 *
 *       public static void main(String[] args) {
 *         // Parse the arguments
 *         Properties props = StringUtils.argsToProperties(args);
 *         ArgumentParser.fillOptions(new Class[]{ Foo.class, ArgumentParser.class }, props);
 *
 *         log.info(INPUT);
 *       }
 *     }
 * </code></pre>
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("HtmlTagCanBeJavadocTag")
public class ArgumentParser  {

  private ArgumentParser() {} // static class

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
      ArgumentParser.class,
  };

  @Option(name = "option_classes", gloss = "Fill options from these classes")
  public static Class<?>[] optionClasses; // = null;
  @Option(name = "threads", gloss = "Number of threads on machine")
  public static int threads = Runtime.getRuntime().availableProcessors();
  @Option(name = "host", gloss = "Name of computer we are running on")
  public static String host = "(unknown)";
  @SuppressWarnings({"FieldCanBeLocal", "RedundantFieldInitialization"})
  @Option(name = "strict", gloss = "If true, make sure that all options passed in are used somewhere")
  private static boolean strict = false;
  @SuppressWarnings({"FieldCanBeLocal", "RedundantFieldInitialization"})
  @Option(name = "exec.verbose", gloss = "If true, print options as they are set.")
  private static boolean verbose = false;

  static {
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
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
      StringBuilder b = new StringBuilder("setting ").append(f.getDeclaringClass().getName()).append('#').append(f.getName()).append(' ');
      if (opt != null) {
        b.append('[').append(opt.name()).append("] ");
      }
      b.append("to: ").append(value);
      log(b.toString());
    }

    try {
      //--Permissions
      boolean accessState = true;
      if (Modifier.isFinal(f.getModifiers())) {
        runtimeException("Option cannot be final: " + f);
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
            runtimeException("Setting an array to a non-array field. field: " + f + " value: " + Arrays.toString(array) + " src: " + value);
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
        runtimeException("Cannot assign option field: " + f + " value: " + value + "; invalid type");
      }
      //--Permissions
      if (!accessState) {
        f.setAccessible(false);
      }
    } catch (IllegalArgumentException e) {
      err(e);
      runtimeException("Cannot assign option field: " + f.getDeclaringClass().getCanonicalName() + '.' + f.getName() + " value: " + value + " cause: " + e.getMessage());
    } catch (IllegalAccessException e) {
      err(e);
      runtimeException("Cannot access option field: " + f.getDeclaringClass().getCanonicalName() + '.' + f.getName());
    } catch (Exception e) {
      err(e);
      runtimeException("Cannot assign option field: " + f.getDeclaringClass().getCanonicalName() + '.' + f.getName() + " value: " + value + " cause: " + e.getMessage());
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
    return Arrays.stream(IGNORED_JARS).anyMatch(path::endsWith);
  }

  private static Class<?>[] getVisibleClasses() {
    //--Variables
    List<Class<?>> classes = new ArrayList<>();
    // (get classpath)
    String pathSep = System.getProperty("path.separator");
    String[] cp = System.getProperties().getProperty("java.class.path",
        null).split(pathSep);
    // --Fill Options
    // (get classes)
    for (String entry : cp) {
      log("Checking cp " + entry);
      //(should skip?)
      if (entry.equals(".") || entry.trim().isEmpty()) {
        continue;
      }
      //(no, don't skip)
      File f = new File(entry);
      if (f.isDirectory()) {
        // --Case: Files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(f.toPath(), "*.class")) {
          for (Path p : stream) {
              //(get the associated class)
              Class<?> clazz = filePathToClass(entry, p.toString());
              if (clazz != null) {
                //(add the class if it's valid)
                classes.add(clazz);
              }
            }
        } catch (IOException ioe) {
          error(ioe);
        }
      } else //noinspection StatementWithEmptyBody
        if (!isIgnored(entry)) {
        // --Case: Jar
        try (JarFile jar = new JarFile(f)) {
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
                debug("Could not scan class: " + clazz + " (in jar: " + f + ')');
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

  private static String threadRootClass() {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    int i = trace.length - 1;
    while(i > 0 &&
        (trace[i].getClassName().startsWith("com.intellij") ||
         trace[i].getClassName().startsWith("java.") ||
         trace[i].getClassName().startsWith("sun.")
        ) ) {
      i -= 1;
    }
    StackTraceElement elem = trace[i];
    return elem.getClassName();
  }

  private static String bufferString(String raw, int minLength) {
    StringBuilder b = new StringBuilder(raw);
    for (int i = raw.length(); i < minLength; ++i) {
      b.append(' ');
    }
    return b.toString();
  }


  @SuppressWarnings({"rawtypes", "ZeroLengthArrayAllocation", "ToArrayCallWithZeroLengthArrayArgument"})
  private static Map<String, Field> fillOptionsImpl(
          Object[] instances,
          Class<?>[] classes,
          Properties options,
          boolean ensureAllOptions,
          boolean isBootstrap) {

    // Print usage, if applicable
    if (!isBootstrap) {
      if ("true".equalsIgnoreCase(options.getProperty("usage", "false")) ||
          "true".equalsIgnoreCase(options.getProperty("help", "false"))
          ) {
        Set<Class<?>> allClasses = new HashSet<>();
        Collections.addAll(allClasses, classes);
        if (instances != null) {
          for (Object o : instances) {
            allClasses.add(o.getClass());
          }
        }
        System.err.println(usage(allClasses.toArray(new Class[0])));
        System.exit(0);
      }
    }

    //--Create Class->Object Mapping
    Map<Class, Object> class2object = new HashMap<>();
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
    Map<String, Field> canFill = new HashMap<>();
    Map<String, Pair<Boolean, Boolean>> required = new HashMap<>(); /* <exists, is_set> */
    Map<String, String> interner = new HashMap<>();
    for (Class c : classes) {
      Field[] fields;
      try {
        fields = scrapeFields(c);
      } catch (Throwable e) {
        debug("Could not check fields for class: " + c.getName() + "  (caused by " + e.getClass() + ": " + e.getMessage() + ')');
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
          if (name.isEmpty()) {
            name = f.getName().toLowerCase();
          }
          if (canFill.containsKey(name)) {
            String name1 = canFill.get(name).getDeclaringClass().getCanonicalName() + '.' + canFill.get(name).getName();
            String name2 = f.getDeclaringClass().getCanonicalName() + '.' + f.getName();
            if (!name1.equals(name2)) {
              runtimeException("Multiple declarations of option " + name + ": " + name1 + " and " + name2);
            } else {
              err("Class is in classpath multiple times: " + canFill.get(name).getDeclaringClass().getCanonicalName());
            }
          }
          canFill.put(name, f);
          required.put(name, mark);
          interner.put(name, name);
          //(add alternate names)
          if ( ! o.alt().isEmpty()) {
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
    for (Map.Entry<Object, Object> entry : options.entrySet()) {
      String rawKeyStr = entry.getKey().toString();
      String key = rawKeyStr.toLowerCase();
      // (get values)
      String value = entry.getValue().toString();
      assert value != null;
      Field target = canFill.get(key);
      // (mark required option as fulfilled)
      Pair<Boolean, Boolean> mark = required.get(key);
      if (mark != null && mark.first) {
        required.put(key, Pair.makePair(true, true));
      }
      // (fill the field)
      if (target != null) {
        // (case: declared option)
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
          // get the class
          Class clazz = null;
          try {
            clazz = ClassLoader.getSystemClassLoader().loadClass(className);
          } catch (Exception e) {
            err("Could not set option: " + entry.getKey() + "; either the option is mistyped, not defined, or the class " + className + " does not exist.");
          }
          // get the field
          if (clazz != null) {
            String fieldName = rawKeyStr.substring(lastDotIndex + 1);
            try {
              target = clazz.getField(fieldName);
            } catch (Exception e) {
              err("Could not set option: " + entry.getKey() + "; no such field: " + fieldName + " in class: " + className);
            }
            if (target != null) {
              log("option overrides " + target + " to '" + value + '\'');
              fillField(class2object.get(target.getDeclaringClass()), target, value);
            } else {
              err("Could not set option: " + entry.getKey() + "; no such field: " + fieldName + " in class: " + className);
            }
          }
        }
      }
    }

    //--Ensure Required
    boolean good = true;
    for (Map.Entry<String, Pair<Boolean, Boolean>> entry : required.entrySet()) {
      String key = entry.getKey();
      Pair<Boolean, Boolean> mark = entry.getValue();
      if (mark.first && !mark.second) {
        err("Missing required option: " + interner.get(key) + "   <in class: " + canFill.get(key).getDeclaringClass() + '>');
        required.put(key, Pair.makePair(true, true));  //don't duplicate error messages
        good = false;
      }
    }
    if ( ! good) {
      throw new RuntimeException("Specified properties are not parsable or not valid!");
      //System.exit(1);
    }

    return canFill;
  }

  @SuppressWarnings("UnusedReturnValue")
  private static Map<String, Field> fillOptionsImpl(
          Object[] instances,
          Class<?>[] classes,
          Properties options) {
    return fillOptionsImpl(instances, classes, options, strict, false);
  }


  /*
   * ----------
   * EXECUTION
   * ----------
   */

  /**
   * Populate all static options in the given set of classes, as defined by the given
   * properties.
   *
   * @param classes The classes to populate static {@link Option}-tagged fields in.
   * @param options The properties to use to fill these fields.
   */
  public static void fillOptions(Class<?>[] classes, Properties options) {
    fillOptionsImpl(null, classes, options);
  }


  /**
   * Populate all static {@link Option}-tagged fields in the given classes with the given Properties.
   * Then, fill in additional (or overwrite existing) properties with the given (String) command-line arguments.
   *
   * @param optionClasses The classes to populate static {@link Option}-tagged fields in.
   * @param props The properties to use to fill these fields.
   * @param args The command-line arguments to use to fill in additional properties.
   */
  @SuppressWarnings("UnusedDeclaration")
  public static void fillOptions(Class<?>[] optionClasses, Properties props, String... args) {
    ArgumentParser.optionClasses = optionClasses;
    fillOptions(props, args);

  }

  /**
   * Populate with the given command-line arguments all static {@link Option}-tagged fields in
   * the given classes.
   *
   * @param classes The classes to populate static {@link Option}-tagged fields in.
   * @param args The command-line arguments to use to fill in additional properties.
   */
  public static void fillOptions(Class<?>[] classes,
                                 String... args) {
    Properties options = StringUtils.argsToProperties(args); //get options
    fillOptionsImpl(null, BOOTSTRAP_CLASSES, options, false, true); //bootstrap
    fillOptionsImpl(null, classes, options);
  }

  /**
   * Populate all static options in the given class, as defined by the given
   * properties.
   *
   * @param clazz The class to populate static {@link Option}-tagged fields in.
   * @param options The properties to use to fill these fields.
   */
  public static void fillOptions(Class<?> clazz, Properties options) {
    fillOptionsImpl(null, new Class[]{ clazz }, options);
  }

  /**
   * Populate all static options in the given class, as defined by the given properties.
   * Then, fill in additional (or overwrite existing) properties with the given (String) command-line arguments.
   *
   * @param clazz The class to populate static {@link Option}-tagged fields in.
   * @param props The properties to use to fill these fields.
   * @param args Additional command-line options to fill these fields.
   */
  public static void fillOptions(Class<?> clazz, Properties props, String... args) {
    Properties allProperties = updatePropertiesWithOptions(props, args);
    fillOptionsImpl(null, new Class[]{ clazz }, allProperties);
  }

  /**
   * Populate all static options in the given class, as defined by the given
   * command-line arguments.
   *
   * @param clazz The class to populate static {@link Option}-tagged fields in.
   * @param args The command-line arguments to use to fill these fields.
   */
  public static void fillOptions(Class<?> clazz,
                                 String... args) {
    Class<?>[] classes = new Class<?>[1];
    classes[0] = clazz;
    fillOptions(classes, args);
  }

  /**
   * Populate with the given properties all static options in all classes in the current classpath.
   * Note that this may take a while if the classpath is large.
   *
   * @param props The properties to use to fill fields in the various classes.
   */
  public static void fillOptions(Properties props) {
    fillOptions(props, StringUtils.EMPTY_STRING_ARRAY);
  }

  /**
   * Populate with the given command-line arguments all static options in all
   * classes in the current classpath.
   * Note that this may take a while if the classpath is large.
   *
   * @param args The command-line arguments to use to fill options.
   */
  public static void fillOptions(String... args) {
    fillOptions(StringUtils.argsToProperties(args), StringUtils.EMPTY_STRING_ARRAY);
  }

  /**
   * Populate all static {@link Option}-tagged fields in the given classes with the given Properties.
   * Then, fill in additional (or overwrite existing) properties with the given (String) command-line arguments.
   * Note that this may take a while if the classpath is large.
   *
   * @param props The properties to use to fill fields in the various classes.
   * @param args The command-line arguments to use to fill in additional properties.
   */
  public static void fillOptions(Properties props, String... args) {
    //(convert to map)
    Properties allProperties = updatePropertiesWithOptions(props, args);
    //(bootstrap)
    Map<String, Field> bootstrapMap = fillOptionsImpl(null, BOOTSTRAP_CLASSES, allProperties, false, true);
    bootstrapMap.keySet().forEach(allProperties::remove);
    //(fill options)
    Class<?>[] visibleClasses = optionClasses;
    if (visibleClasses == null) { visibleClasses = getVisibleClasses(); } //get classes
    fillOptionsImpl(null, visibleClasses, allProperties); //fill
  }


  /**
   * Fill all non-static {@link Option}-tagged fields in the given set of objects with the given
   * properties.
   *
   * @param instances The object instances containing {@link Option}-tagged fields which we should fill.
   * @param options The properties to use to fill these fields.
   */
  public static void fillOptions(Object[] instances, Properties options) {
    Class[] classes = Arrays.stream(instances).map(Object::getClass).toArray(Class[]::new);
    fillOptionsImpl(instances, classes, options);
  }


  /**
   * Fill all non-static {@link Option}-tagged fields in the given set of objects with the given
   * command-line arguments.
   *
   * @param instances The object instances containing {@link Option}-tagged fields which we should fill.
   * @param args The command-line arguments to use to fill these fields.
   */
  public static void fillOptions(Object[] instances,
                                 String[] args) {
    Properties options = StringUtils.argsToProperties(args); //get options
    fillOptionsImpl(null, BOOTSTRAP_CLASSES, options, false, true); //bootstrap
    Class[] classes = Arrays.stream(instances).map(Object::getClass).toArray(Class[]::new);
    fillOptionsImpl(instances, classes, options);
  }

  /**
   * Fill all non-static {@link Option}-tagged fields in the given object with the given
   * properties.
   *
   * @param instance The object instance containing {@link Option}-tagged fields which we should fill.
   * @param options The properties to use to fill these fields.
   */
  public static void fillOptions(Object instance, Properties options) {
    fillOptions(new Object[]{ instance }, options);
  }

  /**
   * Populate all static options in the given class, as defined by the given properties.
   * Then, fill in additional (or overwrite existing) properties with the given (String) command-line arguments.
   *
   * @param instance The object instance containing {@link Option}-tagged fields which we should fill.
   * @param props The properties to use to fill these fields.
   * @param args Additional command-line options to fill these fields.
   */
  public static void fillOptions(Object instance, Properties props, String... args) {
    Properties allProperties = updatePropertiesWithOptions(props, args);
    fillOptions(new Object[]{ instance }, allProperties);
  }


  /**
   * Fill all non-static {@link Option}-tagged fields in the given object with the given
   * command-line arguments.
   *
   * @param instance The object instance containing {@link Option}-tagged fields which we should fill.
   * @param args The command-line arguments to use to fill these fields.
   */
  public static void fillOptions(Object instance, String... args) {
    fillOptions(new Object[]{ instance }, args);
  }


  /**
   * Fill all the options for a given subcomponent.
   * This assumes that the subcomponent takes properties with a prefix, so that, for example,
   * if the subcomponent is {@code parse} then it takes a property {@code parse.maxlen} for instance.
   *
   * @param subcomponent The subcomponent to fill options for.
   * @param subcomponentName The name of the subcomponent, for parsing properties.
   * @param props The properties to fill the options in the subcomponent with.
   */
  public static void fillOptions(Object subcomponent, String subcomponentName, Properties props) {
    ArgumentParser.fillOptions(subcomponent, props);
    Properties withoutPrefix = new Properties();
    String prefixString = subcomponentName + '.';
    for (Map.Entry entry : props.entrySet()) {
      String key = entry.getKey().toString();
      withoutPrefix.setProperty(key.replace(prefixString, ""), entry.getValue().toString());
    }
    ArgumentParser.fillOptions(subcomponent, withoutPrefix);
  }


  private static Properties updatePropertiesWithOptions(Properties props, String[] args) {
    Properties allProperties = new Properties();
    // copy it so props isn't changed but can be overridden by args
    for (String key : props.stringPropertyNames()) {
      allProperties.setProperty(key, props.getProperty(key));
    }
    Properties options = StringUtils.argsToProperties(args);
    for (String key : options.stringPropertyNames()) {
      allProperties.setProperty(key, options.getProperty(key));
    }
    return allProperties;
  }

  /**
   * Return the list of {@link ArgumentParser.Option}'s for provided Class
   * @param c The Class to analyze
   * @return A List containing the {@link ArgumentParser.Option} names
   */

  public static List<String> listOptions(Class c) {
    try {
      return Arrays.stream(scrapeFields(c)).map(field -> {
        ArgumentParser.Option[] anns = field.getAnnotationsByType(ArgumentParser.Option.class);
        return (anns.length > 0) ? anns[0].name() : null;
      }
      ).filter(argOpt -> (argOpt != null)).collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException("Exception thrown while scraping fields from "+c.getName());
    }
  }


  /**
   * Return a string describing the usage of the program this method is called from, given the
   * options declared in the given set of classes.
   * This will print both the static options, and the non-static options.
   *
   * @param optionsClasses The classes defining the options being used by this program.
   * @return A String describing the usage of the class.
   */
  public static String usage(Class[] optionsClasses) {
    String mainClass = threadRootClass();
    StringBuilder b = new StringBuilder();
    b.append("Usage: ").append(mainClass).append(' ');

    List<Pair<Option, Field>> options = new ArrayList<>();
    for (Class clazz : optionsClasses) {
      try {
        options.addAll(Arrays.stream(scrapeFields(clazz))
        .map(field -> {
          Annotation[] annotations = field.getAnnotationsByType(Option.class);
          if (annotations.length > 0) {
            return Pair.makePair((Option) annotations[0], field);
          } else {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));
      } catch (Exception e) {
        return b.append("<unknown>").toString();
      }
    }

    int longestOptionName = options.stream().map(x -> x.first.name().length()).max(Comparator.comparingInt(x -> x)).orElse(10);
    int longestOptionType = options.stream().map(x -> x.second.getType().getSimpleName().length()).max(Comparator.comparingInt(x -> x)).orElse(10) + 1;

    options.stream().filter(x -> x.first.required()).forEach(optionPair -> {
      Option option = optionPair.first;
      Field  field  = optionPair.second;
      b.append("\n\t-").append(bufferString(option.name(), longestOptionName))
          .append("   <").append(bufferString(field.getType().getSimpleName() + '>', longestOptionType))
          .append("   [required] ")
          .append(option.gloss());
    });
    options.stream().filter(x -> !x.first.required()).forEach(optionPair -> {
      Option option = optionPair.first;
      Field field = optionPair.second;
      b.append("\n\t-").append(bufferString(option.name(), longestOptionName))
          .append("   <").append(bufferString(field.getType().getSimpleName() + '>', longestOptionType))
          .append("   ")
          .append(option.gloss());
    });

    return b.toString();
  }


  /**
   * Return a string describing the usage of the program this method is called from, given the
   * options declared in the given set of objects.
   * This will print both the static options, and the non-static options.
   *
   * @param optionsClasses The objects defining the options being used by this program.
   * @return A String describing the usage of the class.
   */
  public static String usage(Object[] optionsClasses) {
    return usage(Arrays.stream(optionsClasses).map(Object::getClass).toArray(Class[]::new));
  }

  /**
   * Return a string describing the usage of the program this method is called from, given the
   * options declared in the given class.
   * This will print both the static options, and the non-static options.
   *
   * @param optionsClass The class defining the options being used by this program.
   * @return A String describing the usage of the class.
   */
  public static String usage(Class<?> optionsClass) {
    return usage(new Class[]{ optionsClass });
  }

  /**
   * Return a string describing the usage of the program this method is called from, given the
   * options declared in the given object.
   * This will print both the static options, and the non-static options.
   *
   * @param optionsClass The object defining the options being used by this program.
   * @return A String describing the usage of the class.
   */
  public static String usage(Object optionsClass) {
    return usage(new Class[]{ optionsClass.getClass() });
  }

}
