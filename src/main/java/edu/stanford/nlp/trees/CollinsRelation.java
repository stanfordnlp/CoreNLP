package edu.stanford.nlp.trees;

import java.util.Arrays;

/**
 * A relation 4-tuple for the dependency representation of Collins (1999; 2003).
 * The tuple represents categories common to a head and its modifier:
 *
 *   Parent    - The common parent between the head daughter and the daughter in which the
 *                modifier appears.
 *   Head      - The category label of the head daughter.
 *   Modifier  - The category label of the daughter in which the modifier appears.
 *   Direction - Orientation of the modifier with respect to the head.
 *
 * @author Spence Green
 */
public class CollinsRelation {

  public enum Direction {Left,Right}

  private final String parent;
  private final String head;
  private final String modifier;
  private final Direction direction;

  private static final int defaultPadding = 8;

  public CollinsRelation(String par, String head, String mod, Direction dir) {
    parent = par;
    this.head = head;
    modifier = mod;
    direction = dir;
  }

  @Override
  public String toString() {
    final String dir = (direction == Direction.Left) ? "L" : "R";
    return String.format("%s%s%s%s", pad(parent), pad(head), pad(modifier), dir);
  }

  private static String pad(String s) {
    if (s == null) return null;
    int add = defaultPadding - s.length(); //Number of whitespace characters to add
    if(add <= 0) return s;

    StringBuilder str = new StringBuilder(s);

    char[] ch = new char[add];
    Arrays.fill(ch, ' ');
    str.append(ch);

    return str.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other)
      return true;
    if (!(other instanceof CollinsRelation))
      return false;

    CollinsRelation otherRel = (CollinsRelation) other;

    return (parent.equals(otherRel.parent) &&
            head.equals(otherRel.head) &&
            modifier.equals(otherRel.modifier) &&
            direction == otherRel.direction);
  }

  @Override
  public int hashCode() {
    int hash = 1;
    hash *= 68 * parent.hashCode();
    hash *= 983 * modifier.hashCode();
    hash *= 672 * head.hashCode();
    hash *= (direction == Direction.Left) ? -1 : 1;
    return hash;
  }

}
