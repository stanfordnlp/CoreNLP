package edu.stanford.nlp.misc;

import java.lang.reflect.Constructor;


/**
 * Determine the size of an Object (in bytes taken up in memory).
 * Based on a program in the Java Platform Performance book.
 * This version has been extended somewhat, so that it can at least
 * handle some cases of classes whose constructors require an argument
 * (e.g., Double, Integer), but more could clearly be done here, in a more
 * general way, using reflection.
 *
 * @author Steve Wilson
 * @author Jeff Kesselman
 * @author Christopher Manning
 */
public class ObjectSize {

  private static final int NUMOBJ = 10000;

  private ObjectSize() {
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static long sizeOf(Class<?> clazz) {
    long size = 0;
    try {
      @SuppressWarnings("unused")
      Object primer = clazz.newInstance();
      long startingMemoryUse = getUsedMemory();
      Object[] objects = new Object[NUMOBJ];
      for (int i = 0; i < objects.length; i++) {
        objects[i] = clazz.newInstance();
      }
      long endingMemoryUse = getUsedMemory();
      float approxSize = (endingMemoryUse - startingMemoryUse) / (float) NUMOBJ;
      size = Math.round(approxSize);
      //++
      System.err.println("Created object is a " + objects[0].getClass().getName() + " value " + objects[0]);
    } catch (Exception e) {
      System.out.println("WARNING:couldn't instantiate " + clazz);
      e.printStackTrace();
    }
    return size;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static long sizeOf(Constructor<?> constr, Object[] args) {
    long size = 0;
    try {
      @SuppressWarnings("unused")
      Object primer = constr.newInstance(args);
      long startingMemoryUse = getUsedMemory();
      Object[] objects = new Object[NUMOBJ];
      for (int i = 0; i < objects.length; i++) {
        objects[i] = constr.newInstance(args);
      }
      long endingMemoryUse = getUsedMemory();
      float approxSize = (endingMemoryUse - startingMemoryUse) / (float) NUMOBJ;
      size = Math.round(approxSize);
      //++
      System.err.println("Created object is a " + objects[0].getClass().getName() + " value " + objects[0]);
    } catch (Exception e) {
      System.out.println("WARNING:couldn't instantiate " + constr);
      e.printStackTrace();
    }
    return size;
  }

  public static long getUsedMemory() {
    gc();
    long totalMemory = Runtime.getRuntime().totalMemory();
    gc();
    long freeMemory = Runtime.getRuntime().freeMemory();
    long usedMemory = totalMemory - freeMemory;
    return usedMemory;
  }

  private static void gc() {
    try {
      System.gc();
      Thread.sleep(100);
      System.runFinalization();
      Thread.sleep(100);
      System.gc();
      Thread.sleep(100);
      System.runFinalization();
      Thread.sleep(100);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public static void main(String[] args) {
    try {
      if (args.length == 0) {
        System.err.println("Usage: java ObjectSize classOfInterest [argumentClass argumentValue]");
      } else if (args.length == 1) {
        Class<?> clazz = Class.forName(args[0]);
        System.out.println("Size of object is " + sizeOf(clazz) + " bytes.");
      } else {
        Class<?> clazz;
        if (args[1].equals("double")) {
          clazz = Double.TYPE;
        } else if (args[1].equals("float")) {
          clazz = Float.TYPE;
        } else if (args[1].equals("int")) {
          clazz = Integer.TYPE;
        } else {
          clazz = Class.forName(args[1]);
        }
        Class<?>[] argsClass = new Class<?>[]{clazz};
        Object[] arguments;
        if (clazz.isPrimitive()) {
          Integer i = Integer.valueOf(args[2]);
          arguments = new Object[]{i};
        } else {
          argsClass[0].newInstance();
          // could apply valueOf method to it via reflection
          Object arg = args[2];
          arguments = new Object[]{arg};
        }
        Constructor<?> constr = Class.forName(args[0]).getConstructor(argsClass);
        System.out.println("Size of object is " + sizeOf(constr, arguments) + " bytes.");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}

