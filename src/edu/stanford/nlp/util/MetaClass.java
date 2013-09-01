package edu.stanford.nlp.util;

import java.io.File;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

/**
 * A meta class using Java's reflection library. Can be used to create a single
 * instance, or a factory, where each Class from the factory share their
 * constructor parameters.
 *
 * @author Gabor Angeli
 */
public class MetaClass {

  public static class ClassCreationException extends RuntimeException {

    private static final long serialVersionUID = -5980065992461870357L;

    private ClassCreationException() {
      super();
    }

    private ClassCreationException(String msg) {
      super(msg);
    }

    private ClassCreationException(Throwable cause) {
      super(cause);
    }

    private ClassCreationException(String msg, Throwable cause) {
      super(msg, cause);
    }

  }

  public static final class ConstructorNotFoundException extends ClassCreationException {
    private static final long serialVersionUID = -5980065992461870357L;

    private ConstructorNotFoundException() {
      super();
    }

    private ConstructorNotFoundException(String msg) {
      super(msg);
    }

    private ConstructorNotFoundException(Throwable cause) {
      super(cause);
    }

    private ConstructorNotFoundException(String msg, Throwable cause) {
      super(msg, cause);
    }


  }

  public static final class ClassFactory<T> {
    private Class<?>[] classParams;
    private Class<T> cl;
    private Constructor<T> constructor;

    private static boolean samePrimitive(Class<?> a, Class<?> b){
      if(!a.isPrimitive() && !b.isPrimitive()) return false;
      if(a.isPrimitive()){
        try {
          Class<?> type = (Class<?>) b.getField("TYPE").get(null);
          return type.equals(a);
        } catch (Exception e) {
          return false;
        }
      }
      if(b.isPrimitive()){
        try {
          Class<?> type = (Class<?>) a.getField("TYPE").get(null);
          return type.equals(b);
        } catch (Exception e) {
          return false;
        }
      }
      throw new IllegalStateException("Impossible case");
    }

    private static int superDistance(Class<?> candidate, Class<?> target) {
      if (candidate == null) {
        // --base case: does not implement
        return Integer.MIN_VALUE;
      } else if (candidate.equals(target)) {
        // --base case: exact match
        return 0;
      } else if(samePrimitive(candidate, target)){
        // --base case: primitive and wrapper
        return 0;
      } else {
        // --recursive case: try superclasses
        // case: direct superclass
        Class<?> directSuper = candidate.getSuperclass();
        int superDist = superDistance(directSuper, target);
        if (superDist >= 0)
          return superDist + 1; // case: superclass distance
        // case: implementing interfaces
        Class<?>[] interfaces = candidate.getInterfaces();
        int minDist = Integer.MAX_VALUE;
        for (Class<?> i : interfaces) {
          superDist = superDistance(i, target);
          if (superDist >= 0) {
            minDist = Math.min(minDist, superDist);
          }
        }
        if (minDist != Integer.MAX_VALUE)
          return minDist + 1; // case: interface distance
        else
          return -1; // case: failure
      }
    }

    @SuppressWarnings("unchecked")
    private void construct(String classname, Class<?>... params)
        throws ClassNotFoundException, NoSuchMethodException {
      // (save class parameters)
      this.classParams = params;
      // (create class)
      try {
        this.cl = (Class<T>) Class.forName(classname);
      } catch (ClassCastException e) {
        throw new ClassCreationException("Class " + classname
            + " could not be cast to the correct type");
      }
      // --Find Constructor
      // (get constructors)
      Constructor<?>[] constructors = cl.getDeclaredConstructors();
      Constructor<?>[] potentials = new Constructor<?>[constructors.length];
      Class<?>[][] constructorParams = new Class<?>[constructors.length][];
      int[] distances = new int[constructors.length]; //distance from base class
      // (filter: length)
      for (int i = 0; i < constructors.length; i++) {
        constructorParams[i] = constructors[i].getParameterTypes();
        if (params.length == constructorParams[i].length) { // length is good
          potentials[i] = constructors[i];
          distances[i] = 0;
        } else { // length is bad
          potentials[i] = null;
          distances[i] = -1;
        }
      }
      // (filter:type)
      for (int paramIndex = 0; paramIndex < params.length; paramIndex++) { // for
                                          // each
                                          // parameter...
        Class<?> target = params[paramIndex];
        for (int conIndex = 0; conIndex < potentials.length; conIndex++) { // for
                                          // each
                                          // constructor...
          if (potentials[conIndex] != null) { // if the constructor is
                            // in the pool...
            Class<?> cand = constructorParams[conIndex][paramIndex];
            int dist = superDistance(target, cand);
            if (dist >= 0) { // and if the constructor matches...
              distances[conIndex] += dist; // keep it
            } else {
              potentials[conIndex] = null; // else, remove it from
                              // the pool
              distances[conIndex] = -1;
            }
          }
        }
      }
      // (filter:min)
      this.constructor = (Constructor<T>) argmin(potentials, distances, 0);
      if (this.constructor == null) {
        StringBuilder b = new StringBuilder();
        b.append(classname).append("(");
        for (Class<?> c : params) {
          b.append(c.getName()).append(", ");
        }
        String target = b.substring(0, params.length==0 ? b.length() : b.length() - 2) + ")";
        throw new ConstructorNotFoundException(
            "No constructor found to match: " + target);
      }
    }

    private ClassFactory(String classname, Class<?>... params)
        throws ClassNotFoundException, NoSuchMethodException {
      // (generic construct)
      construct(classname, params);
    }

    private ClassFactory(String classname, Object... params)
        throws ClassNotFoundException, NoSuchMethodException {
      // (convert class parameters)
      Class<?>[] classParams = new Class[params.length];
      for (int i = 0; i < params.length; i++) {
        if(params[i] == null) throw new ClassCreationException("Argument " + i + " to class constructor is null");
        classParams[i] = params[i].getClass();
      }
      // (generic construct)
      construct(classname, classParams);
    }

    private ClassFactory(String classname, String... params)
        throws ClassNotFoundException, NoSuchMethodException {
      // (convert class parameters)
      Class<?>[] classParams = new Class[params.length];
      for (int i = 0; i < params.length; i++) {
        classParams[i] = Class.forName(params[i]);
      }
      // (generic construct)
      construct(classname, classParams);
    }

    /**
     * Creates an instance of the class produced in this factory
     *
     * @param params
     *            The arguments to the constructor of the class NOTE: the
     *            resulting instance will [unlike java] invoke the most
     *            narrow constructor rather than the one which matches the
     *            signature passed to this function
     * @return An instance of the class
     */
    public T createInstance(Object... params) {
      try {
        boolean accessible = true;
        if(!constructor.isAccessible()){
          accessible = false;
          constructor.setAccessible(true);
        }
        T rtn = constructor.newInstance(params);
        if(!accessible){ constructor.setAccessible(false); }
        return rtn;
      } catch (Exception e) {
        throw new ClassCreationException("MetaClass couldn't create " + constructor + " with args " + Arrays.toString(params), e);
      }
    }

    /**
     * Returns the full class name for the objects being produced
     *
     * @return The class name for the objects produced
     */
    public String getName() {
      return cl.getName();
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(cl.getName()).append("(");
      for (Class<?> cl : classParams) {
        b.append(" ").append(cl.getName()).append(",");
      }
      b.replace(b.length() - 1, b.length(), " ");
      b.append(")");
      return b.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
      if (o instanceof ClassFactory) {
        ClassFactory other = (ClassFactory) o;
        if (!this.cl.equals(other.cl))
          return false;
        for (int i = 0; i < classParams.length; i++) {
          if (!this.classParams[i].equals(other.classParams[i]))
            return false;
        }
        return true;
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return cl.hashCode();
    }

  } // end static class ClassFactory

  private String classname;

  /**
   * Creates a new MetaClass producing objects of the given type
   *
   * @param classname
   *            The full classname of the objects to create
   */
  public MetaClass(String classname) {
    this.classname = classname;
  }

  /**
   * Creates a new MetaClass producing objects of the given type
   *
   * @param classname
   *            The class to create
   */
  public MetaClass(Class<?> classname) {
    this.classname = classname.getName();
  }

  /**
   * Creates a factory for producing instances of this class from a
   * constructor taking the given types as arguments
   *
   * @param <E>
   *            The type of the objects to be produced
   * @param classes
   *            The types used in the constructor
   * @return A ClassFactory of the given type
   */
  public <E> ClassFactory<E> createFactory(Class<?>... classes) {
    try {
      return new ClassFactory<E>(classname, classes);
    } catch (ClassCreationException e){
      throw e;
    } catch (Exception e) {
      throw new ClassCreationException(e);
    }
  }

  /**
   * Creates a factory for producing instances of this class from a
   * constructor taking the given types as arguments
   *
   * @param <E>
   *            The type of the objects to be produced
   * @param classes
   *            The types used in the constructor
   * @return A ClassFactory of the given type
   */
  public <E> ClassFactory<E> createFactory(String... classes) {
    try {
      return new ClassFactory<E>(classname, classes);
    } catch (ClassCreationException e){
      throw e;
    } catch (Exception e) {
      throw new ClassCreationException(e);
    }
  }

  /**
   * Creates a factory for producing instances of this class from a
   * constructor taking objects of the types given
   *
   * @param <E>
   *            The type of the objects to be produced
   * @param objects
   *            Instances of the types used in the constructor
   * @return A ClassFactory of the given type
   */
  public <E> ClassFactory<E> createFactory(Object... objects) {
    try {
      return new ClassFactory<E>(classname, objects);
    } catch (ClassCreationException e){
      throw e;
    } catch (Exception e) {
      throw new ClassCreationException(e);
    }
  }

  /**
   * Create an instance of the class, inferring the type automatically, and
   * given an array of objects as constructor parameters NOTE: the resulting
   * instance will [unlike java] invoke the most narrow constructor rather
   * than the one which matches the signature passed to this function
   *
   * @param <E>
   *            The type of the object returned
   * @param objects
   *            The arguments to the constructor of the class
   * @return An instance of the class
   */
  public <E> E createInstance(Object... objects) {
    ClassFactory<E> fact = createFactory(objects);
    return fact.createInstance(objects);
  }

  /**
   * Creates an instance of the class, forcing a cast to a certain type and
   * given an array of objects as constructor parameters NOTE: the resulting
   * instance will [unlike java] invoke the most narrow constructor rather
   * than the one which matches the signature passed to this function
   *
   * @param <E>
   *            The type of the object returned
   * @param type
   *            The class of the object returned
   * @param params
   *            The arguments to the constructor of the class
   * @return An instance of the class
   */
  @SuppressWarnings("unchecked")
  public <E,F extends E> F createInstance(Class<E> type, Object... params) {
    Object obj = createInstance(params);
    if (type.isInstance(obj)) {
      return (F) obj;
    } else {
      throw new ClassCreationException("Cannot cast " + classname
          + " into " + type.getName());
    }
  }

  public boolean checkConstructor(Object... params){
    try{
      createInstance(params);
      return true;
    } catch(ConstructorNotFoundException e){
      return false;
    }
  }

  @Override
  public String toString() {
    return classname;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof MetaClass) {
      return ((MetaClass) o).classname.equals(this.classname);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return classname.hashCode();
  }

  /**
   * Creates a new MetaClass (helper method)
   *
   * @param classname
   *            The name of the class to create
   * @return A new MetaClass object of the given class
   */
  public static MetaClass create(String classname) {
    return new MetaClass(classname);
  }

  /**
   * Creates a new MetaClass (helper method)
   *
   * @param clazz
   *            The class to create
   * @return A new MetaClass object of the given class
   */
  public static MetaClass create(Class <?> clazz) {
    return new MetaClass(clazz);
  }

  /**
   * Utility method for cast
   * @param type The type to cast into a class
   * @return The class corresponding to the passed in type
   */
  private static Class <?> type2class(Type type){
		if(type instanceof Class <?>){
			return (Class <?>) type;	//base case
		}else if(type instanceof ParameterizedType){
			return type2class( ((ParameterizedType) type).getRawType() );
		}else if(type instanceof TypeVariable<?>){
			return type2class( ((TypeVariable<?>) type).getBounds()[0] );
		}else if(type instanceof WildcardType){
			return type2class( ((WildcardType) type).getUpperBounds()[0] );
		}else{
			throw new IllegalArgumentException("Cannot convert type to class: " + type);
		}
	}
  /**
   * Decode an array encoded as a String. This entails a comma separated value enclosed in brackets
   * or parentheses
   * @param encoded The String encoded array
   * @return A String array corresponding to the encoded array
   */
	private static String[] decodeArray(String encoded){
		char[] chars = encoded.trim().toCharArray();

		//--Parse the String
		//(state)
		char quoteCloseChar = (char) 0;
		List<StringBuilder> terms = new LinkedList<StringBuilder>();
		StringBuilder current = new StringBuilder();
		//(start/stop overhead)
		int start = 0; int end = chars.length;
		if(chars[0] == '('){ start += 1; end -= 1; if(chars[end] != ')') throw new IllegalArgumentException("Unclosed paren in encoded array: " + encoded); }
		if(chars[0] == '['){ start += 1; end -= 1; if(chars[end] != ']') throw new IllegalArgumentException("Unclosed bracket in encoded array: " + encoded); }
		//(finite state automata)
		for(int i=start; i<end; i++){
			if(chars[i] == '\\'){
				//(case: escaped character)
				if(i == chars.length - 1) throw new IllegalArgumentException("Last character of encoded pair is escape character: " + encoded);
				current.append(chars[i+1]);
				i += 1;
			} else if(quoteCloseChar != 0){
				//(case: in quotes)
				if(chars[i] == quoteCloseChar){
					quoteCloseChar = (char) 0;
				}else{
					current.append(chars[i]);
				}
			}else{
				//(case: normal)
				if(chars[i] == '"'){
          quoteCloseChar = '"';
				} else if(chars[i] == '\''){
          quoteCloseChar = '\'';
				} else if(chars[i] == ',' || chars[i] == ' ' || chars[i] == '\t' || chars[i] == '\n'){
					//break
          if (current.length() > 0) {
					  terms.add(current);
          }
					current = new StringBuilder();
				}else{
					current.append(chars[i]);
				}
			}
		}

		//--Return
		if(current.length() > 0) terms.add(current);
		String[] rtn = new String[terms.size()];
		int i=0;
		for(StringBuilder b : terms){
			rtn[i] = b.toString().trim();
			i += 1;
		}
    return rtn;
  }

  /**
   * Cast a String representation of an object into that object.
   * E.g. "5.4" will be cast to a Double; "[1,2,3]" will be cast
   * to an Integer[].
   *
   * NOTE: Date parses from a Long
   *
   * @param <E> The type of the object returned (same as type)
   * @param value The string representation of the object
   * @param type The type (usually class) to be returned (same as E)
   * @return An object corresponding to the String value passed
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static final <E> E cast(String value, Type type){
    //--Get Type
    Class <?> clazz = null;
    Type[] params = null;
    if(type instanceof Class){
      clazz = (Class <?>) type;
    }else if(type instanceof ParameterizedType){
      ParameterizedType pt = (ParameterizedType) type;
      params = pt.getActualTypeArguments();
      clazz = (Class <?>) pt.getRawType();
    }else{
      clazz = type2class(type);
      throw new IllegalArgumentException("Cannot cast to type (unhandled type): " + type);
    }
    //--Cast
    if(String.class.isAssignableFrom(clazz)){
      // (case: String)
      return (E) value;
    }else if(Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)){
      //(case: boolean)
      if("1".equals(value)){ return (E) new Boolean(true); }
      return (E) new Boolean( Boolean.parseBoolean(value) );
    }else if(Integer.class.isAssignableFrom(clazz) || int.class.isAssignableFrom(clazz)){
      //(case: integer)
      try {
        return (E) new Integer(Integer.parseInt(value));
      } catch (NumberFormatException e) {
        return (E) new Integer((int) Double.parseDouble(value));
      }
    }else if(Long.class.isAssignableFrom(clazz) || long.class.isAssignableFrom(clazz)){
      //(case: long)
      try {
        return (E) new Long(Long.parseLong(value));
      } catch (NumberFormatException e) {
        return (E) new Long((long) Double.parseDouble(value));
      }
    }else if(Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)){
      //(case: float)
      if(value == null){ return (E) new Float(Float.NaN); }
      return (E) new Float(Float.parseFloat(value));
    }else if(Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz)){
      //(case: double)
      if(value == null){ return (E) new Double(Double.NaN); }
      return (E) new Double(Double.parseDouble(value));
    }else if(Short.class.isAssignableFrom(clazz) || short.class.isAssignableFrom(clazz)){
      //(case: short)
      try {
        return (E) new Short(Short.parseShort(value));
      } catch (NumberFormatException e) {
        return (E) new Short((short) Double.parseDouble(value));
      }
    }else if(Byte.class.isAssignableFrom(clazz) || byte.class.isAssignableFrom(clazz)){
      //(case: byte)
      try {
        return (E) new Byte(Byte.parseByte(value));
      } catch (NumberFormatException e) {
        return (E) new Byte((byte) Double.parseDouble(value));
      }
    }else if(Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)){
      //(case: char)
      return (E) new Character((char) Integer.parseInt(value));
    }else if(java.util.Date.class.isAssignableFrom(clazz)){
      //(case: date)
      try {
        return (E) new Date(Long.parseLong(value));
      } catch (NumberFormatException e) {
        return null;
      }
    } else if(java.util.Calendar.class.isAssignableFrom(clazz)){
      //(case: date)
      try {
        Date d = new Date(Long.parseLong(value));
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(d);
        return (E) cal;
      } catch (NumberFormatException e) {
        return null;
      }
    } else if(File.class.isAssignableFrom(clazz)){
      return (E) new File(value);
    } else if(Class.class.isAssignableFrom(clazz)){
      try {
        return (E) Class.forName(value);
      } catch (ClassNotFoundException e) {
        return null;
      }
    } else if(clazz.isArray()){
      if(value == null){ return null; }
      Class <?> subType = clazz.getComponentType();
      // (case: array)
      String[] strings = decodeArray(value);
      Object[] array = (Object[]) Array.newInstance(clazz.getComponentType(), strings.length);
      for(int i=0; i<strings.length; i++){
        array[i] = cast(strings[i], subType);
      }
      return (E) array;
    } else if(clazz.isEnum()){
      // (case: enumeration)
      Class c = (Class) clazz;
      if(value == null){ return null; }
      if (value.charAt(0) == '"') value = value.substring(1);
      if (value.charAt(value.length()-1) == '"') value = value.substring(0, value.length() - 1);
      try {
        return (E) Enum.valueOf(c, value);
      } catch (Exception e){
        try {
          return (E) Enum.valueOf(c, value.toLowerCase());
        } catch (Exception e2){
          try {
            return (E) Enum.valueOf(c, value.toUpperCase());
          } catch (Exception e3){
            return (E) Enum.valueOf(c,
                (Character.isUpperCase(value.charAt(0)) ? Character.toLowerCase(value.charAt(0)) : Character.toUpperCase(value.charAt(0))) +
                    value.substring(1));
          }
        }
      }
    } else {
      try {
        // (case: can parse from string)
        Method decode = clazz.getMethod("fromString", String.class);
        return (E) decode.invoke(MetaClass.create(clazz), value);
      } catch (NoSuchMethodException e) {  // Silent errors for misc failures
      } catch (InvocationTargetException e) {
      } catch (IllegalAccessException e) {
      } catch (ClassCastException e) {
      }
      // We could not cast this object
      return null;
    }
  }

  private static <E> E argmin(E[] elems, int[] scores, int atLeast) {
    int argmin = argmin(scores, atLeast);
    return argmin >= 0 ? elems[argmin] : null;
  }
  private static int argmin(int[] scores, int atLeast) {
    int min = Integer.MAX_VALUE;
    int argmin = -1;
    for(int i=0; i<scores.length; i++){
      if(scores[i] < min && scores[i] >= atLeast){
        min = scores[i];
        argmin = i;
      }
    }
    return argmin;
  }
}
