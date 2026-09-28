package edu.stanford.nlp.util;

import java.io.ObjectInputFilter;
import java.util.Set;
import java.util.TreeSet;

/**
 * An {@link ObjectInputFilter} that records the name of every class
 * (for arrays, the base component class) seen during deserialization,
 * without rejecting anything.
 * <p>
 * Usage:
 * <pre>{@code
 * SerializationAudit audit = new SerializationAudit();
 * ois.setObjectInputFilter(audit);
 * // after deserializing:
 * audit.printSeenClasses();
 * }</pre>
 */
public final class SerializationAudit implements ObjectInputFilter {
  private final Set<String> seenClasses = new TreeSet<>();

  /** Creates an audit that has not yet seen any classes. */
  public SerializationAudit() { }

  public ObjectInputFilter.Status checkInput(FilterInfo info) {
    Class<?> clazz = info.serialClass();

    if (clazz != null) {
      Class<?> base = clazz;
      while (base.isArray()) {
        base = base.getComponentType();
      }

      seenClasses.add(base.getName());
    }

    return ObjectInputFilter.Status.UNDECIDED;
  }

  /** Prints the names of the classes seen so far to stdout, one per line, in sorted order. */
  public void printSeenClasses() {
    seenClasses.forEach(System.out::println);
  }
}

