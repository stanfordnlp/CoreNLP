package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.trees.Tree;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An object to tell whether a given tree matches a tgrep expression.
 * Only tgrep syntax used in Roland (2001) is supported.  It also
 * keeps track of the arguments and adjuncts from a matched verb
 * phrase.
 *
 * TODO [cdm 2009]: Clearly usages of this should be removed and replaced with tregex.
 *
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */

public class TgrepMatcher {

  public static boolean debug = false;

  // ugh... a terrible hack...
  private static boolean stopSisterSearchAtComma = true;

  // chars that signal the beginning of a tgrep expression
  private static Set<Character> tGrepDelimiterChars;

  private static Map<Tree, Tree> parentMap;

  public static void addParents(Tree root) {
    parentMap = new IdentityHashMap<Tree, Tree>();
    addParentsHelper(root);
  }

  private static void addParentsHelper(Tree t) {
    Tree[] children = t.children();
    for (Tree child : children) {
      parentMap.put(child, t);
      addParentsHelper(child);
    }
  }

  // so no one instantiates it
  private TgrepMatcher() {
  }

  // initializer
  static {
    tGrepDelimiterChars = new HashSet<Character>(20);
    tGrepDelimiterChars.add(new Character(')'));
    tGrepDelimiterChars.add(new Character('('));
    tGrepDelimiterChars.add(new Character('>'));
    tGrepDelimiterChars.add(new Character('<'));
    tGrepDelimiterChars.add(new Character('!'));
    tGrepDelimiterChars.add(new Character('%'));
  }

  public static boolean subtreeMatches(Tree tree, String pattern) {
    if (matches(tree, pattern, 0)) {
      return true;
    } else {
      return descendantMatches(tree, pattern, 0, false);
    }
  }

  public static boolean matches(Tree tree, String pattern) {
    if (matches(tree, pattern, 0)) {
      return true;
    }
    return false;
  }

  private static boolean matches(Tree tree, String pattern, int position) {
    String subExpr = null;
    if (pattern.charAt(position) != '(') {
      subExpr = readSubExpr(pattern, position);
      return tree.label().toString().matches(subExpr);
    }

    boolean negate = false;
    boolean returnVal = false;

    Tree parent;
    Tree[] kids, sibs;

    // -1=undefined, 0=self, 1=parent, 2=child, 3=sister, 4=later sister, 5=next sister,
    // 6=descendant 7=descendant (leftmost) 8=descendent via unbroken chain
    int relnship = 0;  // first string must match label of _this_ tree
    int nextIndex = -1;

    position++;

    while (position < pattern.length()) {
      switch (pattern.charAt(position)) {
        case '!':
          if (negate) {
            tgrepExprError(position, pattern, "double negation");
          }
          negate = true;
          position++;
          break;

        case '>':
          if (relnship != -1) {
            tgrepExprError(position, pattern, "Twice defined relationship");
          }
          relnship = 1;
          position++;
          break;

        case '<':
          if (relnship != -1) {
            tgrepExprError(position, pattern, "Twice defined relationship");
          }
          if (pattern.charAt(++position) == '<') {
            if (pattern.charAt(++position) == ',') {
              relnship = 7;
              position++;
            } else if (pattern.charAt(position) == '+') {
              relnship = 8;
              position++;
              subExpr = readSubExpr(pattern, position);
              position += subExpr.length();
            } else {
              relnship = 6;
            }
          } else {
            relnship = 2;
          }

          if (Character.isDigit(pattern.charAt(position))) {
            nextIndex = Character.getNumericValue(pattern.charAt(position++)) - 1;
          }

          break;

        case '%':
          if (relnship != -1) {
            tgrepExprError(position, pattern, "Twice defined relationship");
          }

          if (pattern.charAt(++position) == '.') {
            if (pattern.charAt(++position) == '.') {
              relnship = 4;
              position++;
            } else {
              relnship = 5;
            }
          } else {
            relnship = 3;
          }

          break;

        case ')':
          return true;

        case '(':
        default:
          relnswitch:
          switch (relnship) {
            case 0: // self
              returnVal = matches(tree, pattern, position);
              break;

            case 1: // parent
              parent = parentMap.get(tree);
              if (parent == null) {
                returnVal = false;
              } else {
                returnVal = matches(parent, pattern, position);
              }
              break;

            case 2: // child
              if (tree.isLeaf()) {
                returnVal = false;
                break relnswitch;
              }

              kids = tree.children();
              for (int i = 0; i < kids.length; i++) {
                if (nextIndex >= 0 && i != nextIndex) {
                  continue;
                }
                if (matches(kids[i], pattern, position)) {
                  returnVal = true;
                  break relnswitch;
                }
              }
              returnVal = false;
              break;

            case 3: // sister (any)
            case 4: // sister (later)
            case 5: // sister (next)
              if (!parentMap.containsKey(tree)) {
                // could happen on root -- which has no sisters!
                returnVal = false;
                break relnswitch;
              }
              parent = parentMap.get(tree);
              sibs = parent.children();
              boolean afterthis = false;
              for (int i = 0; i < sibs.length; i++) {
                if (sibs[i] == tree) {
                  afterthis = true;
                  continue;
                }
                if (relnship == 4 && !afterthis) {
                  continue;
                }

                if (relnship == 5) {
                  if (afterthis) {
                    if (matches(sibs[i], pattern, position)) {
                      returnVal = true;
                    } else {
                      returnVal = false;
                    }
                    break relnswitch;
                  } else {
                    continue;
                  }
                }

                if (matches(sibs[i], pattern, position)) {
                  returnVal = true;
                  break relnswitch;
                }

                if (stopSisterSearchAtComma && sibs[i].label().value().matches(",|:")) {
                  break;
                }
              }

              returnVal = false;
              break;

            case 6: // descendant (any)
              returnVal = descendantMatches(tree, pattern, position);
              break;

            case 7: // descendant (leftmost)
              returnVal = descendantMatches(tree, pattern, position, true);
              break;

            case 8: // descendant via unbroken chain
              returnVal = descendantMatches(tree, pattern, position, subExpr);
              break;

            default:
              tgrepExprError(position, pattern, "Undefined relationship");
          }

          if (negate) {
            if (returnVal) {
              return false;
            }
          } else {
            if (!returnVal) {
              return false;
            }
          }

          position += readSubExpr(pattern, position).length();
          negate = false;
          relnship = -1;
          nextIndex = -1;
          break;

      }
    }

    return (true);
  }

  private static String readSubExpr(String pattern, int position) {
    StringBuilder buf = new StringBuilder();

    if (position < pattern.length() && pattern.charAt(position) == '(') {
      int parenDepth = 0;
      do {
        buf.append(pattern.charAt(position));
        if (pattern.charAt(position) == '(') {
          parenDepth++;
        } else if (pattern.charAt(position) == ')') {
          parenDepth--;
        }
        position++;
      } while (parenDepth > 0);
    } else {
      while (position < pattern.length() && !tGrepDelimiterChars.contains(new Character(pattern.charAt(position)))) {
        if (pattern.charAt(position) == '\\' && tGrepDelimiterChars.contains(new Character(pattern.charAt(position + 1)))) {
          position++;
        }
        buf.append(pattern.charAt(position++));
      }

      if (buf.length() == 0 || !tGrepDelimiterChars.contains(new Character(pattern.charAt(position)))) {
        tgrepExprError(position, pattern, "Illegal character");
      }
    }

    return buf.toString();
  }

  private static boolean descendantMatches(Tree tree, String pattern, int position) {
    return descendantMatches(tree, pattern, position, false);
  }

  private static boolean descendantMatches(Tree tree, String pattern, int position, boolean leftmost) {
    return descendantMatches(tree, pattern, position, leftmost, "(.*)");
  }

  private static boolean descendantMatches(Tree tree, String pattern, int position, String viaPattern) {
    return descendantMatches(tree, pattern, position, false, viaPattern);
  }

  private static boolean descendantMatches(Tree tree, String pattern, int position, boolean leftmost, String viaPattern) {
    Tree[] kids = tree.children();

    for (Tree kid : kids) {
      if (matches(kid, pattern, position)) {
        return true;
      }

      if (!kid.isLeaf() && matches(kid, viaPattern) && descendantMatches(kid, pattern, position, leftmost, viaPattern)) {
        return true;
      }

      if (leftmost) {
        return false;
      }
    }

    return false;
  }

  private static void tgrepExprError(int position, String pattern, String error) {
    System.err.println("Parse error of tgrep expression " + pattern + " at position " + position + ": " + error);
    System.exit(1);
  }
}
