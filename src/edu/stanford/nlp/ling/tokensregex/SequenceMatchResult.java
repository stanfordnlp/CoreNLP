package edu.stanford.nlp.ling.tokensregex;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.MatchResult;

import edu.stanford.nlp.util.Comparators;
import edu.stanford.nlp.util.HasInterval;
import edu.stanford.nlp.util.Interval;

/**
 * The result of a match against a sequence.
 *
 * Similar to Java's {@link MatchResult} except it is for sequences
 * over arbitrary types T instead of just characters.
 *
 * This interface contains query methods used to determine the
 * results of a match against a regular expression against an sequence.
 * The match boundaries, groups and group boundaries can be seen
 * but not modified through a {@link SequenceMatchResult}.
 *
 * @author Angel Chang
 * @see SequenceMatcher
 */
public interface SequenceMatchResult<T> extends MatchResult, HasInterval<Integer> {

  // TODO: Need to be careful with GROUP_BEFORE_MATCH/GROUP_AFTER_MATCH
  int GROUP_BEFORE_MATCH = Integer.MIN_VALUE;    // Special match groups (before match)
  int GROUP_AFTER_MATCH = Integer.MIN_VALUE + 1; // Special match groups (after match)

  double score();
  double priority();

  /**
   * Returns the original sequence the match was performed on.
   *
   * @return The list that the match was performed on
   */
  List<? extends T> elements();

  /**
   * Returns pattern used to create this sequence match result
   *
   * @return the SequencePattern against which this sequence match result was matched
   */
  SequencePattern<T> pattern();

  /**
   * Returns the entire matched subsequence as a list.
   *
   * @return the matched subsequence as a list
   * @throws  IllegalStateException
   *          If no match has yet been attempted,
   *          or if the previous match operation failed
   */
  List<? extends T> groupNodes();

  /**
   * Returns the matched group as a list.
   *
   * @param  group The index of a capturing group in this matcher's pattern
   * @return the matched group as a list
   * @throws  IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   * @throws  IndexOutOfBoundsException If there is no capturing group in the pattern
   *          with the given index
   */
  List<? extends T> groupNodes(int group);

  BasicSequenceMatchResult<T> toBasicSequenceMatchResult();

  // String lookup versions using variables

  /**
   * Returns the matched group as a list.
   *
   * @param  groupVar The name of the capturing group in this matcher's pattern
   * @return the matched group as a list
   *         or {@code null} if there is no capturing group in the pattern
   *         with the given name
   * @throws  IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   */
  List<? extends T> groupNodes(String groupVar);

  /**
   * Returns the {@code String} representing the matched group.
   *
   * @param  groupVar The name of the capturing group in this matcher's pattern
   * @return the matched group as a {@code String}
   *         or {@code null} if there is no capturing group in the pattern
   *         with the given name
   * @throws IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   */
  String group(String groupVar);

  /**
   * Returns the start index of the subsequence captured by the given group
   * during this match.
   *
   * @param  groupVar The name of the capturing group in this matcher's pattern
   * @return the index of the first element captured by the group,
   *         or {@code -1} if the match was successful but the group
   *         itself did not match anything
   *         or if there is no capturing group in the pattern
   *         with the given name
   * @throws IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   */
  int start(String groupVar);

  /**
   * Returns the index of the next element after the subsequence captured by the given group
   * during this match.
   *
   * @param  groupVar The name of the capturing group in this matcher's pattern
   * @return the index of the next element after the subsequence captured by the group,
   *         or {@code -1} if the match was successful but the group
   *         itself did not match anything
   *         or if there is no capturing group in the pattern
   *         with the given name
   * @throws IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   */
  int end(String groupVar);

  int getOrder();

  /**
   * Returns an Object representing the result for the match for a particular node.
   * (actual Object returned depends on the type T of the nodes).  For instance,
   * for a CoreMap, the match result is returned as a {@code Map<Class, Object>}, while
   * for String, the match result is typically a MatchResult.
   *
   * @param  index The index of the element in the original sequence.
   * @return The match result associated with the node at the given index.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   * @throws IndexOutOfBoundsException If the index is out of range
   */
  Object nodeMatchResult(int index);

  /**
   * Returns an Object representing the result for the match for a particular node in a group.
   * (actual Object returned depends on the type T of the nodes.  For instance,
   * for a CoreMap, the match result is returned as a {@code Map<Class, Object>}, while
   * for String, the match result is typically a MatchResult.
   *
   * @param  groupid The index of a capturing group in this matcher's pattern
   * @param  index The index of the element in the captured subsequence.
   * @return the match result associated with the node
   *         at the given index for the captured group.
   * @throws IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   * @throws IndexOutOfBoundsException If there is no capturing group in the pattern
   *          with the given groupid or if the index is out of range
   */
  Object groupMatchResult(int groupid, int index);

  /**
   * Returns an Object representing the result for the match for a particular node in a group.
   * (actual Object returned depends on the type T of the nodes.  For instance,
   * for a CoreMap, the match result is returned as a {@code Map<Class, Object>}, while
   * for String, the match result is typically a MatchResult.
   *
   * @param  groupVar The name of the capturing group in this matcher's pattern
   * @param  index The index of the element in the captured subsequence.
   * @return the match result associated with the node
   *         at the given index for the captured group.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  Object groupMatchResult(String groupVar, int index);

  /**
   * Returns a list of Objects representing the match results for the entire sequence.
   *
   * @return the list of match results associated with the entire sequence
   * @throws  IllegalStateException If no match has yet been attempted,
   *          or if the previous match operation failed
   */
  List<Object> groupMatchResults();

  /**
   * Returns a list of Objects representing the match results for the nodes in the group.
   *
   * @param  group The index of a capturing group in this matcher's pattern
   * @return the list of match results associated with the nodes
   *         for the captured group.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   * @throws IndexOutOfBoundsException If there is no capturing group in the pattern
   *         with the given index
   */
  List<Object> groupMatchResults(int group);

  /**
   * Returns a list of Objects representing the match results for the nodes in the group.
   *
   * @param  groupVar
   *         The name of the capturing group in this matcher's pattern
   * @return the list of match results associated with the nodes
   *         for the captured group.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   */
  List<Object> groupMatchResults(String groupVar);

  /**
   * Returns the value (some Object) associated with the entire matched sequence.
   *
   * @return value associated with the matched sequence.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   */
  Object groupValue();

  /**
   * Returns the value (some Object) associated with the captured group.
   *
   * @param  group The index of a capturing group in this matcher's pattern
   * @return value associated with the captured group.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   */
  Object groupValue(int group);

  /**
   * Returns the value (some Object) associated with the captured group.
   *
   * @param  var The name of the capturing group in this matcher's pattern
   * @return value associated with the captured group.
   * @throws IllegalStateException If no match has yet been attempted,
   *         or if the previous match operation failed
   */
  Object groupValue(String var);

  MatchedGroupInfo<T> groupInfo();
  MatchedGroupInfo<T> groupInfo(int group);
  MatchedGroupInfo<T> groupInfo(String var);

  GroupToIntervalFunc TO_INTERVAL = new GroupToIntervalFunc(0);

  class GroupToIntervalFunc<MR extends MatchResult> implements Function<MR, Interval<Integer>> {
    int group;
    public GroupToIntervalFunc(int group) { this.group = group; }
    @Override
    public Interval<Integer> apply(MR in) {
      return Interval.toInterval(in.start(group), in.end(group), Interval.INTERVAL_OPEN_END);
    }
  }

  Comparator<MatchResult> PRIORITY_COMPARATOR = (e1, e2) -> {
    double s1 = 0;
    if (e1 instanceof SequenceMatchResult) { s1 =  ((SequenceMatchResult) e1).priority(); }
    double s2 = 0;
    if (e2 instanceof SequenceMatchResult) { s2 =  ((SequenceMatchResult) e2).priority(); }
    if (s1 == s2) {
      return 0;
    } else {
      return (s1 > s2)? -1: 1;
    }
  };

  Comparator<MatchResult> SCORE_COMPARATOR = (e1, e2) -> {
    double s1 = 0;
    if (e1 instanceof SequenceMatchResult) { s1 =  ((SequenceMatchResult) e1).score(); }
    double s2 = 0;
    if (e2 instanceof SequenceMatchResult) { s2 =  ((SequenceMatchResult) e2).score(); }
    if (s1 == s2) {
      return 0;
    } else {
      return (s1 > s2)? -1: 1;
    }
  };

  Comparator<MatchResult> ORDER_COMPARATOR = (e1, e2) -> {
    int o1 = 0;
    if (e1 instanceof SequenceMatchResult) { o1 =  ((SequenceMatchResult) e1).getOrder(); }
    int o2 = 0;
    if (e2 instanceof SequenceMatchResult) { o2 =  ((SequenceMatchResult) e2).getOrder(); }
    if (o1 == o2) {
      return 0;
    } else {
      return (o1 < o2)? -1:1;
    }
  };

  /** Compares two match results.
   *  Use to order match results by: length (longest first)
   */
  Comparator<MatchResult> LENGTH_COMPARATOR = (e1, e2) -> {
    int len1 = e1.end() - e1.start();
    int len2 = e2.end() - e2.start();
    if (len1 == len2) {
      return 0;
    } else {
      return (len1 > len2)? -1:1;
    }
  };

  Comparator<MatchResult> OFFSET_COMPARATOR = (e1, e2) -> {
    if (e1.start() == e2.start()) {
      if (e1.end() == e2.end()) {
        return 0;
      } else {
        return (e1.end() < e2.end())? -1:1;
      }
    } else {
      return (e1.start() < e2.start())? -1:1;
    }
  };

  /** Compares two match results. Use to order match results by:
   *  priority (highest first), score (highest first), length (longest first),
   *  and then beginning token offset (smaller offset first), original order (smaller first)
   */
  Comparator<MatchResult> PRIORITY_SCORE_LENGTH_ORDER_OFFSET_COMPARATOR =
          Comparators.chain(PRIORITY_COMPARATOR, SCORE_COMPARATOR, LENGTH_COMPARATOR, ORDER_COMPARATOR, OFFSET_COMPARATOR);

  Comparator<? super MatchResult> DEFAULT_COMPARATOR = PRIORITY_SCORE_LENGTH_ORDER_OFFSET_COMPARATOR;

  ToDoubleFunction<MatchResult> SCORER = in -> {
    if (in instanceof SequenceMatchResult) { return  ((SequenceMatchResult) in).score(); }
    else return 0.0;
  };

  /**
   * Information about a matched group.
   *
   * @param <T> The type of object in the sequence
   */
  final class MatchedGroupInfo<T> {
    public final String text;
    public final List<? extends T> nodes;
    public final List<Object> matchResults;
    public final Object value;
    public final String varName;

    public MatchedGroupInfo(String text, List<? extends T> nodes, List<Object> matchResults, Object value, String varName) {
      this.text = text;
      this.nodes = nodes;
      this.matchResults = matchResults;
      this.value = value;
      this.varName = varName;
    }
  } // end class MatchedGroupInfo

}
