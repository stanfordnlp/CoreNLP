package edu.stanford.nlp.quoteattribution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by michaelf on 12/20/15.
 */
public class Person {

  public enum Gender {MALE, FEMALE, UNK}

  public final String name;
  public final Set<String> aliases;
  public final Gender gender;

  @Override
  public String toString() {
    return "Person{name='" + name + '\'' +
            ", aliases=" + aliases +
            ", gender=" + gender +
            '}';
  }

  public Person(String name, String gender, List<String> aliases) {
    this.name = name;
    if (gender.toLowerCase().startsWith("m")) {
      this.gender = Gender.MALE;
    } else if (gender.toLowerCase().startsWith("f")) {
      this.gender = Gender.FEMALE;
    } else {
      this.gender = Gender.UNK;
    }
    if (aliases != null) {
      this.aliases = new HashSet<>(aliases);
    } else {
      this.aliases = new HashSet<>();
    }
    this.aliases.add(name);
  }

  public boolean contains(String name) {
    return aliases.contains(name);
  }

}
