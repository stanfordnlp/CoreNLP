package edu.stanford.nlp.util;

import java.io.ObjectInputFilter;
import java.util.Set;
import java.util.TreeSet;

// SerializationAudit audit = new SerializationAudit()
// ois.setObjectInputFilter(audit);
// after deserializing:
// audit.printSeenClasses();
public final class SerializationAudit implements ObjectInputFilter {
  private final Set<String> seenClasses = new TreeSet<>();

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

  public void printSeenClasses() {
    seenClasses.forEach(System.out::println);
  }
}

