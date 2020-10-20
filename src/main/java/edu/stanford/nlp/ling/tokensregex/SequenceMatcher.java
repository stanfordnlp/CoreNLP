package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.stanford.nlp.ling.tokensregex.SequenceMatcher.FindType.FIND_NONOVERLAPPING;

/**
 * A generic sequence matcher.
 *
 * Similar to Java's {@code Matcher} except it matches sequences over an arbitrary type {@code T}
 *   instead of characters.
 * For a type {@code T} to be matchable, it has to have a corresponding {@code NodePattern<T>} that indicates
 *    whether a node is matched or not.
 * <p>
 * A matcher is created as follows:
 *
 * {@code
 *   SequencePattern<T> p = SequencePattern<T>.compile("...");
 *   SequencePattern<T> m = p.getMatcher(List<T> sequence);
 * }
 *
 * <p>
 * Functions for searching
 * <pre>{@code
 *    boolean matches()
 *    boolean find()
 *    boolean find(int start)
 * }</pre>
 * Functions for retrieving matched patterns
 * <pre>{@code
 *    int groupCount()
 *    List<T> groupNodes(), List<T> groupNodes(int g)
 *    String group(), String group(int g)
 *    int start(), int start(int g), int end(), int end(int g)
 * }</pre>
 * Functions for replacing
 * <pre>{@code
 *    List<T> replaceFirst(List<T> seq), List replaceAll(List<T> seq)
 *    List<T> replaceFirstExtended(List<MatchReplacement<T>> seq), List<T> replaceAllExtended(List<MatchReplacement<T>> seq)
 * }</pre>
 * Functions for defining the region of the sequence to search over (default region is entire sequence)
 * <pre>{@code
 *     void region(int start, int end)
 *     int regionStart()
 *     int regionEnd()
 * }</pre>
 *
 * NOTE: When find is used, matches are attempted starting from the specified start index of the sequence
 *   The match with the earliest starting index is returned.
 *
 * @author Angel Chang
 */
public class SequenceMatcher<T> extends BasicSequenceMatchResult<T> {

  private static final Logger logger = Logger.getLogger(SequenceMatcher.class.getName());

  private boolean includeEmptyMatches = false;
  private boolean matchingCompleted = false;
  private boolean matched = false;
  boolean matchWithResult = false; // If result of matches should be kept
  private int nextMatchStart = 0;

  private int regionStart = 0;
  private int regionEnd = -1;

  // TODO: Check and fix implementation for FIND_ALL
  /**
   * Type of search to perform
   * <ul>
   * <li>FIND_NONOVERLAPPING - Find nonoverlapping matches (default)</li>
   * <li>FIND_ALL - Find all potential matches
   *            Greedy/reluctant quantifiers are not enforced
   *            (perhaps should add syntax where some of them are enforced...)</li>
   * </ul>
   */
  public enum FindType { FIND_NONOVERLAPPING, FIND_ALL }
  private FindType findType = FIND_NONOVERLAPPING;

  // For FIND_ALL
  private Iterator<Integer> curMatchIter = null;
  private MatchedStates<T> curMatchStates = null;
  private final Set<String> prevMatchedSignatures = new HashSet<>();

  // Branching limit for searching with back tracking. Higher value makes the search faster but uses more memory.
  private int branchLimit = 32;

  protected SequenceMatcher(SequencePattern<T> pattern, List<? extends T> elements) {
    this.pattern = pattern;
    // NOTE: It is important elements DO NOT change as we do matches
    // TODO: Should we just make a copy of the elements?
    this.elements = elements;
    if (elements == null) {
      throw new IllegalArgumentException("Cannot match against null elements");
    }
    this.regionEnd = elements.size();
    this.priority = pattern.priority;
    this.score = pattern.weight;
    this.varGroupBindings = pattern.varGroupBindings;
    matchedGroups = new MatchedGroup[pattern.totalGroups];
  }

  public void setBranchLimit(int blimit){
    this.branchLimit = blimit;
  }


  /**
   * Interface that specifies what to replace a matched pattern with.
   *
   * @param <T>
   */
  public interface MatchReplacement<T> {

    /**
     * Append to replacement list.
     * @param match Current matched sequence
     * @param list replacement list
     */
    void append(SequenceMatchResult<T> match, List<T> list);
  }


  /**
   * Replacement item is a sequence of items.
   * @param <T>
   */
  public static class BasicMatchReplacement<T> implements MatchReplacement<T>  {
    List<T> replacement;

    @SafeVarargs
    public BasicMatchReplacement(T... replacement) {
      this.replacement = Arrays.asList(replacement);
    }

    public BasicMatchReplacement(List<T> replacement) {
      this.replacement = replacement;
    }

    /**
     * Append to replacement list our list of replacement items.
     * @param match Current matched sequence
     * @param list replacement list
     */
    @Override
    public void append(SequenceMatchResult<T> match, List<T> list) {
      list.addAll(replacement);
    }
  }


  /**
   * Replacement item is a matched group specified with a group name.
   * @param <T>
   */
  public static class NamedGroupMatchReplacement<T> implements MatchReplacement<T> {
    String groupName;

    public NamedGroupMatchReplacement(String groupName) {
      this.groupName = groupName;
    }

    /**
     * Append to replacement list the matched group with the specified group name
     * @param match Current matched sequence
     * @param list replacement list
     */
    @Override
    public void append(SequenceMatchResult<T> match, List<T> list) {
      list.addAll(match.groupNodes(groupName));
    }

  }


  /**
   * Replacement item is a matched group specified with a group id.
   * @param <T>
   */
  public static class GroupMatchReplacement<T> implements MatchReplacement<T> {

    int group;

    public GroupMatchReplacement(int group) {
      this.group = group;
    }

    /**
     * Append to replacement list the matched group with the specified group id
     * @param match Current matched sequence
     * @param list replacement list
     */
    @Override
    public void append(SequenceMatchResult<T> match, List<T> list) {
      list.addAll(match.groupNodes(group));
    }
  }

  /**
   * Replaces all occurrences of the pattern with the specified list
   *   of replacement items (can include matched groups).
   * @param replacement What to replace the matched sequence with
   * @return New list with all occurrences of the pattern replaced
   * @see #replaceFirst(java.util.List)
   * @see #replaceFirstExtended(java.util.List)
   * @see #replaceAllExtended(java.util.List)
   */
  public List<T> replaceAllExtended(List<MatchReplacement<T>> replacement) {
    List<T> res = new ArrayList<>();
    FindType oldFindType = findType;
    findType = FindType.FIND_NONOVERLAPPING;
    int index = 0;
    while (find()) {
      // Copy from current index to found index
      res.addAll(elements().subList(index, start()));
      for (MatchReplacement<T> r:replacement) {
        r.append(this, res);
      }
      index = end();
    }
    res.addAll(elements().subList(index, elements().size()));
    findType = oldFindType;
    return res;
  }

  /**
   * Replaces the first occurrence of the pattern with the specified list
   *   of replacement items (can include matched groups).
   * @param replacement What to replace the matched sequence with
   * @return New list with the first occurrence of the pattern replaced
   * @see #replaceFirst(java.util.List)
   * @see #replaceAll(java.util.List)
   * @see #replaceAllExtended(java.util.List)
   */
  public List<T> replaceFirstExtended(List<MatchReplacement<T>> replacement) {
    List<T> res = new ArrayList<>();
    FindType oldFindType = findType;
    findType = FindType.FIND_NONOVERLAPPING;
    int index = 0;
    if (find()) {
      // Copy from current index to found index
      res.addAll(elements().subList(index, start()));
      for (MatchReplacement<T> r:replacement) {
        r.append(this, res);
      }
      index = end();
    }
    res.addAll(elements().subList(index, elements().size()));
    findType = oldFindType;
    return res;
  }

  /**
   * Replaces all occurrences of the pattern with the specified list.
   * Use {@link #replaceAllExtended(java.util.List)} to replace with matched groups.
   *
   * @param replacement What to replace the matched sequence with
   * @return New list with all occurrences of the pattern replaced
   * @see #replaceAllExtended(java.util.List)
   * @see #replaceFirst(java.util.List)
   * @see #replaceFirstExtended(java.util.List)
   */
  public List<T> replaceAll(List<T> replacement) {
    List<T> res = new ArrayList<>();
    FindType oldFindType = findType;
    findType = FindType.FIND_NONOVERLAPPING;
    int index = 0;
    while (find()) {
      // Copy from current index to found index
      res.addAll(elements().subList(index, start()));
      res.addAll(replacement);
      index = end();
    }
    res.addAll(elements().subList(index, elements().size()));
    findType = oldFindType;
    return res;
  }

  /**
   * Replaces the first occurrence of the pattern with the specified list.
   * Use {@link #replaceFirstExtended(java.util.List)} to replace with matched groups.
   *
   * @param replacement What to replace the matched sequence with
   * @return New list with the first occurrence of the pattern replaced
   * @see #replaceAll(java.util.List)
   * @see #replaceAllExtended(java.util.List)
   * @see #replaceFirstExtended(java.util.List)
   */
  public List<T> replaceFirst(List<T> replacement) {
    List<T> res = new ArrayList<>();
    FindType oldFindType = findType;
    findType = FindType.FIND_NONOVERLAPPING;
    int index = 0;
    if (find()) {
      // Copy from current index to found index
      res.addAll(elements().subList(index, start()));
      res.addAll(replacement);
      index = end();
    }
    res.addAll(elements().subList(index, elements().size()));
    findType = oldFindType;
    return res;
  }

  public FindType getFindType() {
    return findType;
  }

  public void setFindType(FindType findType) {
    this.findType = findType;
  }

  public boolean isMatchWithResult() {
    return matchWithResult;
  }

  public void setMatchWithResult(boolean matchWithResult) {
    this.matchWithResult = matchWithResult;
  }

  /**
   * Reset the matcher and then searches for pattern at the specified start index.
   *
   * @param start - Index at which to start the search
   * @return true if a match is found (false otherwise)
   * @throws IndexOutOfBoundsException if start is {@literal <} 0 or larger then the size of the sequence
   * @see #find()
   */
  public boolean find(int start) {
    if (start < 0 || start > elements.size()) {
      throw new IndexOutOfBoundsException("Invalid region start=" + start + ", need to be between 0 and " + elements.size());
    }
    reset();
    return find(start, false);
  }

  protected boolean find(int start, boolean matchStart) {
    boolean done = false;
    while (!done) {
      boolean res = find0(start, matchStart);
      if (res) {
        boolean empty = this.group().isEmpty();
        if (!empty || includeEmptyMatches) return res;
        else {
          start = start + 1;
        }
      }
      done = !res;
    }
    return false;
  }

  private boolean find0(int start, boolean matchStart) {
    boolean match = false;
    matched = false;
    matchingCompleted = false;
    if (matchStart)  {
      match = findMatchStart(start, false);
    } else {
      for (int i = start; i < regionEnd; i++) {
        match = findMatchStart(i, false);
        if (match) {
          break;
        }
      }
    }
    matched = match;
    matchingCompleted = true;
    if (matched) {
      nextMatchStart = (findType == FindType.FIND_NONOVERLAPPING)? end(): start()+1;
    } else {
      nextMatchStart = -1;
    }
    return match;
  }

  /**
   * Searches for pattern in the region starting
   *  at the next index
   * @return true if a match is found (false otherwise)
   */
  private boolean findNextNonOverlapping()
  {
    if (nextMatchStart < 0) { return false; }
    return find(nextMatchStart, false);
  }

  private boolean findNextAll()
  {
    if (curMatchIter != null && curMatchIter.hasNext()) {
      while (curMatchIter.hasNext()) {
        int next = curMatchIter.next();
        curMatchStates.setMatchedGroups(next);
        String sig = getMatchedSignature();
        if (!prevMatchedSignatures.contains(sig)) {
          prevMatchedSignatures.add(sig);
          return true;
        }
      }
    }
    if (nextMatchStart < 0) { return false; }
    prevMatchedSignatures.clear();
    boolean matched = find(nextMatchStart, false);
    if (matched) {
      Collection<Integer> matchedBranches = curMatchStates.getMatchIndices();
      curMatchIter = matchedBranches.iterator();
      int next = curMatchIter.next();
      curMatchStates.setMatchedGroups(next);
      prevMatchedSignatures.add(getMatchedSignature());
    }
    return matched;
  }

  /**
   * Applies the matcher and returns all non overlapping matches
   * @return a Iterable of match results
   */
  public Iterable<SequenceMatchResult<T>> findAllNonOverlapping() {
    Iterator<SequenceMatchResult<T>> iter = new Iterator<SequenceMatchResult<T>>() {
      SequenceMatchResult<T> next;

      private SequenceMatchResult<T> getNext() {
        boolean found = find();
        if (found) {
          return toBasicSequenceMatchResult();
        } else {
          return null;
        }
      }

      @Override
      public boolean hasNext() {
        if (next == null) {
          next = getNext();
          return (next != null);
        } else {
          return true;
        }
      }

      @Override
      public SequenceMatchResult<T> next() {
        if (!hasNext()) { throw new NoSuchElementException(); }
        SequenceMatchResult<T> res = next;
        next = null;
        return res;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
    return new IterableIterator<>(iter);
  }

  /**
   * Searches for the next occurrence of the pattern
   * @return true if a match is found (false otherwise)
   * @see #find(int)
   */
  public boolean find() {
    switch (findType) {
      case FIND_NONOVERLAPPING:
        return findNextNonOverlapping();
      case FIND_ALL:
        return findNextAll();
      default:
        throw new UnsupportedOperationException("Unsupported findType " + findType);
    }
  }

  private boolean findMatchStart(int start, boolean matchAllTokens) {
    switch (findType) {
      case FIND_NONOVERLAPPING:
        return findMatchStartBacktracking(start, matchAllTokens);
      case FIND_ALL:
        // TODO: Should use backtracking here too, need to keep track of todo stack
        // so we can recover after finding a match
        return findMatchStartNoBacktracking(start, matchAllTokens);
      default:
        throw new UnsupportedOperationException("Unsupported findType " + findType);
    }
  }

  // Does not do backtracking - alternative matches are stored as we go
  private boolean findMatchStartNoBacktracking(int start, boolean matchAllTokens) {
    boolean matchAll = true;
    MatchedStates<T> cStates = getStartStates();
    cStates.matchLongest = matchAllTokens;
    // Save cStates for FIND_ALL ....
    curMatchStates = cStates;
    for(int i = start; i < regionEnd; i++){
      boolean match = cStates.match(i);
      if (cStates.size() == 0) {
        break;
      }
      if (!matchAllTokens) {
        if ((matchAll && cStates.isAllMatch())
            || (!matchAll && cStates.isMatch())) {
          cStates.completeMatch();
          return true;
        }
      }
    }
    cStates.completeMatch();
    return cStates.isMatch();
  }

  // Does some backtracking...
  private boolean findMatchStartBacktracking(int start, boolean matchAllTokens) {
    boolean matchAll = true;
    Stack<MatchedStates> todo = new Stack<>();
    MatchedStates cStates = getStartStates();
    cStates.matchLongest = matchAllTokens;
    cStates.curPosition = start-1;
    todo.push(cStates);
    while (!todo.empty()) {
      cStates = todo.pop();
      int s = cStates.curPosition+1;
      for(int i = s; i < regionEnd; i++){
        if (Thread.interrupted()) {
          throw new RuntimeInterruptedException();
        }
        cStates.match(i);
        if (cStates.size() == 0) {
          break;
        }
        if (!matchAllTokens) {
          if ((matchAll && cStates.isAllMatch())
              || (!matchAll && cStates.isMatch())) {
            cStates.completeMatch();
            return true;
          }
        }
        if (branchLimit >= 0 && cStates.branchSize() > branchLimit) {
          MatchedStates s2 = cStates.split(branchLimit);
          todo.push(s2);
        }
      }
      if (cStates.isMatch()) {
        cStates.completeMatch();
        return true;
      }
      cStates.clean();
    }
    return false;
  }

  /**
   * Checks if the pattern matches the entire sequence
   * @return true if the entire sequence is matched (false otherwise)
   * @see #find()
   */
  public boolean matches() {
    matched = false;
    matchingCompleted = false;
    boolean status = findMatchStart(0, true);
    if (status) {
      // Check if entire region is matched
      status = ((matchedGroups[0].matchBegin == regionStart) && (matchedGroups[0].matchEnd == regionEnd));
    }
    matchingCompleted = true;
    matched = status;
    return status;
  }

  private void clearMatched() {
    for (int i = 0; i < matchedGroups.length; i++) {
      matchedGroups[i] = null;
    }
    if (matchedResults != null) {
      for (int i = 0; i < matchedResults.length; i++) {
        matchedResults[i] = null;
      }
    }
  }

  private String getStateMessage() {
    if (!matchingCompleted) {
      return "Matching not completed";
    } else if (!matched) {
      return "No match found";
    } else {
      return "Match successful";
    }
  }

  /**
   * Set region to search in.
   * @param start - start index
   * @param end - end index (exclusive)
   */
  public void region(int start, int end) {
    if (start < 0 || start > elements.size()) {
      throw new IndexOutOfBoundsException("Invalid region start=" + start + ", need to be between 0 and " + elements.size());
    }
    if (end < 0 || end > elements.size()) {
      throw new IndexOutOfBoundsException("Invalid region end=" + end + ", need to be between 0 and " + elements.size());
    }
    if (start > end) {
      throw new IndexOutOfBoundsException("Invalid region end=" + end + ", need to be larger then start=" + start);
    }
    this.regionStart = start;
    this.nextMatchStart = start;
    this.regionEnd = end;
  }

  public int regionEnd()
  {
    return regionEnd;
  }

  public int regionStart()
  {
    return regionStart;
  }

  /**
   * Returns a copy of the current match results.  Use this method
   * to save away match results for later use, since future operations
   * using the SequenceMatcher changes the match results.
   *
   * @return Copy of the the current match results
   */
  @Override
  public BasicSequenceMatchResult<T> toBasicSequenceMatchResult() {
    if (matchingCompleted && matched) {
      return super.toBasicSequenceMatchResult();
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public int start(int group) {
    if (matchingCompleted && matched) {
      return super.start(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public int end(int group) {
    if (matchingCompleted && matched) {
      return super.end(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public List<T> groupNodes(int group) {
    if (matchingCompleted && matched) {
      return super.groupNodes(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public Object groupValue(int group) {
    if (matchingCompleted && matched) {
      return super.groupValue(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public MatchedGroupInfo<T> groupInfo(int group) {
    if (matchingCompleted && matched) {
      return super.groupInfo(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public List<Object> groupMatchResults(int group) {
    if (matchingCompleted && matched) {
      return super.groupMatchResults(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public Object groupMatchResult(int group, int index) {
    if (matchingCompleted && matched) {
      return super.groupMatchResult(group, index);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  @Override
  public Object nodeMatchResult(int index) {
    if (matchingCompleted && matched) {
      return super.nodeMatchResult(index);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  /**
   * Clears matcher.
   * Clears matched groups, reset region to be entire sequence.
   */
  public void reset() {
    regionStart = 0;
    regionEnd = elements.size();
    nextMatchStart = 0;
    matchingCompleted = false;
    matched = false;
    clearMatched();

    // Clearing for FIND_ALL
    prevMatchedSignatures.clear();
    curMatchIter = null;
    curMatchStates = null;
  }

  /**
   * Returns the ith element
   * @param i - index
   * @return ith element
   */
  public T get(int i)
  {
    return elements.get(i);
  }

  /** Returns a non-null MatchedStates, which has a non-empty states list inside. */
  private MatchedStates<T> getStartStates() {
    return new MatchedStates<>(this, pattern.root);
  }

  /**
   * Contains information about a branch of running the NFA matching
   */
  private static class BranchState {

    // Branch id
    final int bid;
    // Parent branch state
    final BranchState parent;
    // Map of group id to matched group
    Map<Integer,MatchedGroup> matchedGroups;
    // Map of sequence index id to matched node result
    Map<Integer,Object> matchedResults;
    // Map of state to object storing information about the state for this branch of execution
    // Used for states corresponding to
    //    repeating patterns: key is RepeatState, object is Pair<Integer,Boolean>
    //                        pair indicates sequence index and whether the match was complete
    //    multinode patterns: key is MultiNodePatternState, object is Interval<Integer>
    //                        the interval indicates the start and end node indices for the multinode pattern
    //    conjunction patterns: key is ConjStartState, object is ConjMatchStateInfo
    Map<SequencePattern.State, Object> matchStateInfo;
    //Map<SequencePattern.State, Pair<Integer,Boolean>> matchStateCount;
    Set<Integer> bidsToCollapse; // Branch ids to collapse together with this branch
                                 // Used for conjunction states, which requires multiple paths
                                 // through the NFA to hold
    Set<Integer> collapsedBids;  // Set of Branch ids that has already been collapsed ...
                                // assumes that after being collapsed no more collapsing required

    public BranchState(int bid) {
      this(bid, null);
    }

    public BranchState(int bid, BranchState parent) {
      this.bid = bid;
      this.parent = parent;
      if (parent != null) {
        if (parent.matchedGroups != null) {
          matchedGroups = new LinkedHashMap<>(parent.matchedGroups);
        }
        if (parent.matchedResults != null) {
          matchedResults = new LinkedHashMap<>(parent.matchedResults);
        }
        /*        if (parent.matchStateCount != null) {
    matchStateCount = new LinkedHashMap<SequencePattern.State, Pair<Integer,Boolean>>(parent.matchStateCount);
  }      */
        if (parent.matchStateInfo != null) {
          matchStateInfo = new LinkedHashMap<>(parent.matchStateInfo);
        }
        if (parent.bidsToCollapse != null) {
          bidsToCollapse = new ArraySet<>(parent.bidsToCollapse.size());
          bidsToCollapse.addAll(parent.bidsToCollapse);
        }
        if (parent.collapsedBids != null) {
          collapsedBids = new ArraySet<>(parent.collapsedBids.size());
          collapsedBids.addAll(parent.collapsedBids);
        }
      }
    }

    // Add to list of related branch ids that we would like to keep...
    private void updateKeepBids(BitSet bids) {
      if (matchStateInfo != null) {
        // TODO: Make values of matchStateInfo more organized (implement some interface) so we don't
        // need this kind of specialized code
        for (SequencePattern.State s : matchStateInfo.keySet()) {
          if (s instanceof SequencePattern.ConjStartState) {
            SequencePattern.ConjMatchStateInfo info = (SequencePattern.ConjMatchStateInfo) matchStateInfo.get(s);
            info.updateKeepBids(bids);
          }
        }
      }
    }

    private void addBidsToCollapse(int[] bids) {
      if (bidsToCollapse == null) {
        bidsToCollapse = new ArraySet<>(bids.length);
      }
      for (int b:bids) {
        if (b != bid) {
          bidsToCollapse.add(b);
        }
      }
    }

    private void addMatchedGroups(Map<Integer,MatchedGroup> g) {
      for (Integer k : g.keySet()) {
        if (!matchedGroups.containsKey(k)) {
          matchedGroups.put(k, g.get(k));
        }
      }
    }

    private void addMatchedResults(Map<Integer,Object> res) {
      if (res != null) {
        for (Integer k : res.keySet()) {
          if (!matchedResults.containsKey(k)) {
            matchedResults.put(k, res.get(k));
          }
        }
      }
    }
  }

  private static class State {

    final int bid;
    final SequencePattern.State tstate;

    public State(int bid, SequencePattern.State tstate) {
      this.bid = bid;
      this.tstate = tstate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      State state = (State) o;

      if (bid != state.bid) {
        return false;
      }
      if ( ! Objects.equals(tstate, state.tstate)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = bid;
      result = 31 * result + (tstate != null ? tstate.hashCode() : 0);
      return result;
    }
  }


  /**
   * Overall information about the branching of paths through the NFA
   *  (maintained for one attempt at matching, with multiple MatchedStates).
   */
  static class BranchStates {

    // Index of global branch id to pair of parent branch id and branch index
    // (the branch index is with respect to parent, from 1 to number of branches the parent has)
    // TODO: This index can grow rather large, use index that allows for shrinkage
    //       (has remove function and generate new id every time)
    HashIndex<Pair<Integer,Integer>> bidIndex = new HashIndex<>(512);
    // Map of branch id to branch state
    Map<Integer,BranchState> branchStates = new HashMap<>();//Generics.newHashMap();
    // The activeMatchedStates is only kept to determine what branch states are still needed
    // It's okay if it overly conservative and has more states than needed,
    // And while ideally a set, it's okay to have duplicates (esp if it is a bit faster for normal cases).
    Collection<MatchedStates> activeMatchedStates = new ArrayList<>();//= Generics.newHashSet();

    /**
     * Links specified MatchedStates to us (list of MatchedStates
     *   is used to determine what branch states still need to be kept)
     * @param s
     */
    private void link(MatchedStates s)
    {
      activeMatchedStates.add(s);
    }

    /**
     * Unlinks specified MatchedStates to us (list of MatchedStates
     *   is used to determine what branch states still need to be kept)
     * @param s
     */
    private void unlink(MatchedStates s) {
      // Make sure all instances of s are removed
      while (activeMatchedStates.remove(s)) {}
    }

    protected int getBid(int parent, int child)
    {
      return bidIndex.indexOf(new Pair<>(parent,child));
    }

    protected int newBid(int parent, int child)
    {
      return bidIndex.addToIndexUnsafe(new Pair<>(parent,child));
    }

    protected int size()
    {
      return branchStates.size();
    }

    /**
     * Removes branch states are are no longer needed
     */
    private void condense() {
      BitSet keepBidStates = new BitSet();
//      Set<Integer> curBidSet = new HashSet<Integer>();//Generics.newHashSet();
//      Set<Integer> keepBidStates = new HashSet<Integer>();//Generics.newHashSet();
      for (MatchedStates ms:activeMatchedStates) {
        // Trim out unneeded states info
        List<State> states = ms.states;
        if (logger.isLoggable(Level.FINEST)) {
          logger.finest("Condense matched state: curPosition=" + ms.curPosition
              + ", totalTokens=" + ms.matcher.elements.size()
              + ", nStates=" + states.size());
        }
        for (State state: states) {
          keepBidStates.set(state.bid);
        }
      }
      for (MatchedStates ms : activeMatchedStates) {
        for (State state : (List<State>) ms.states) {
          int bid = state.bid;
          BranchState bs = getBranchState(bid);
          if (bs != null) {
            keepBidStates.set(bs.bid);
            bs.updateKeepBids(keepBidStates);
            if (bs.bidsToCollapse != null) {
              mergeBranchStates(bs);
            }
          }
        }
      }
      Iterator<Integer> iter = branchStates.keySet().iterator();
      while (iter.hasNext()) {
        int bid = iter.next();
        if (!keepBidStates.get(bid)) {
          if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Remove state for bid=" + bid);
          }
          iter.remove();
        }
      }
      /* note[gabor]: replaced code below with the above
      Collection<Integer> curBidStates = new ArrayList<Integer>(branchStates.keySet());
      for (int bid:curBidStates) {
        if (!keepBidStates.get(bid)) {
          if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Remove state for bid=" + bid);
          }
          branchStates.remove(bid);
        }
      }  */


      // TODO: We should be able to trim some bids from our bidIndex as well....
      /*
      if (bidIndex.size() > 1000) {
        logger.warning("Large bid index of size " + bidIndex.size());
      }
      */
    }

    /** A safe version of {@link SequenceMatcher.BranchStates#getParents(int, Integer[])} */
    private List<Integer> getParents(int bid) {
      List<Integer> pids = new ArrayList<>();
      Pair<Integer,Integer> p = bidIndex.get(bid);
      while (p != null && p.first() >= 0) {
        pids.add(p.first());
        p = bidIndex.get(p.first());
      }
      Collections.reverse(pids);
      return pids;
    }

    /**
     * Given a branch id, return a list of parent branches
     * @param bid  branch id
     * @return list of parent branch ids
     */
    private List<Integer> getParents(int bid, Integer[] buffer) {
      int index = buffer.length - 1;
      buffer[index] = bid;
      index -= 1;
      Pair<Integer,Integer> p = bidIndex.get(bid);
      while (p != null && p.first() >= 0) {
        buffer[index] = p.first;
        index -= 1;
        if (index < 0) {
          return getParents(bid);  // optimization failed -- back off to the old version
        }
        p = bidIndex.get(p.first());
      }
      return Arrays.asList(buffer).subList(index + 1, buffer.length);
    }

    /**
     * Returns the branch state for a given branch id
     * (the appropriate ancestor branch state is returned if
     *  there is no branch state associated with the given branch id)
     * @param bid branch id
     * @return BranchState associated with the given branch id
     */
    protected BranchState getBranchState(int bid) {
      BranchState bs = branchStates.get(bid);
      if (bs == null) {
        BranchState pbs = null;
        int id = bid;
        while (pbs == null && id >= 0) {
          Pair<Integer, Integer> p = bidIndex.get(id);
          id = p.first;
          pbs = branchStates.get(id);
        }
        bs = pbs;
      }
      return bs;
    }

    /**
     * Returns the branch state for a given branch id
     * (the appropriate ancestor branch state is returned if
     *  there is no branch state associated with the given branch id)
     * If add is true, then adds a new branch state for this branch id
     * (ensuring that the returned branch state is for the specified branch id)
     * @param bid branch id
     * @param add whether a new branched state should be added
     * @return BranchState associated with the given branch id
     */
    protected BranchState getBranchState(int bid, boolean add) {
      BranchState bs = getBranchState(bid);
      if (add) {
        if (bs == null) {
          bs = new BranchState(bid);
        } else if (bs.bid != bid) {
          bs = new BranchState(bid, bs);
        }
        branchStates.put(bid, bs);
      }
      return bs;
    }

    protected Map<Integer,MatchedGroup> getMatchedGroups(int bid, boolean add) {
      BranchState bs = getBranchState(bid, add);
      if (bs == null) {
        return null;
      }
      if (add && bs.matchedGroups == null) {
        bs.matchedGroups = new LinkedHashMap<>();
      }
      return bs.matchedGroups;
    }

    protected MatchedGroup getMatchedGroup(int bid, int groupId) {
      Map<Integer,MatchedGroup> map = getMatchedGroups(bid, false);
      if (map != null) {
        return map.get(groupId);
      } else {
        return null;
      }
    }

    protected void setGroupStart(int bid, int captureGroupId, int curPosition) {
      if (captureGroupId >= 0) {
        Map<Integer,MatchedGroup> matchedGroups = getMatchedGroups(bid, true);
        MatchedGroup mg = matchedGroups.get(captureGroupId);
        if (mg != null) {
          // This is possible if we have patterns like "( ... )+" in which case multiple nodes can match as the subgroup
          // We will match the first occurrence and use that as the subgroup  (Java uses the last match as the subgroup)
          logger.fine("Setting matchBegin=" + curPosition + ": Capture group " + captureGroupId + " already exists: " + mg);
        }
        matchedGroups.put(captureGroupId, new MatchedGroup(curPosition, -1, null));
      }
    }

    protected void setGroupEnd(int bid, int captureGroupId, int curPosition, Object value) {
      if (captureGroupId >= 0) {
        Map<Integer,MatchedGroup> matchedGroups = getMatchedGroups(bid, true);
        MatchedGroup mg = matchedGroups.get(captureGroupId);
        int end = curPosition+1;
        if (mg != null) {
          if (mg.matchEnd == -1) {
            matchedGroups.put(captureGroupId, new MatchedGroup(mg.matchBegin, end, value));
          } else {
            if (mg.matchEnd != end) {
              logger.warning("Cannot set matchEnd=" + end + ": Capture group " + captureGroupId + " already ended: " + mg);
            }
          }
        } else {
          logger.warning("Cannot set matchEnd=" + end + ": Capture group " + captureGroupId + " is null");
        }
      }
    }

    protected void clearGroupStart(int bid, int captureGroupId) {
      if (captureGroupId >= 0) {
        Map<Integer,MatchedGroup> matchedGroups = getMatchedGroups(bid, false);
        if (matchedGroups != null) {
          matchedGroups.remove(captureGroupId);
        }
      }
    }

    protected Map<Integer,Object> getMatchedResults(int bid, boolean add) {
      BranchState bs = getBranchState(bid, add);
      if (bs == null) {
        return null;
      }
      if (add && bs.matchedResults == null) {
        bs.matchedResults = new LinkedHashMap<>();
      }
      return bs.matchedResults;
    }

    protected Object getMatchedResult(int bid, int index) {
      Map<Integer,Object> map = getMatchedResults(bid, false);
      if (map != null) {
        return map.get(index);
      } else {
        return null;
      }
    }

    protected void setMatchedResult(int bid, int index, Object obj) {
      if (index >= 0) {
        Map<Integer,Object> matchedResults = getMatchedResults(bid, true);
        Object oldObj = matchedResults.get(index);
        if (oldObj != null) {
          logger.warning("Setting matchedResult=" + obj + ": index " + index + " already exists: " + oldObj);
        }
        matchedResults.put(index, obj);
      }
    }

    protected int getBranchId(int bid, int nextBranchIndex, int nextTotal) {
      if (nextBranchIndex <= 0 || nextBranchIndex > nextTotal) {
        throw new IllegalArgumentException("Invalid nextBranchIndex=" + nextBranchIndex + ", nextTotal=" + nextTotal);
      }
      if (nextTotal == 1) {
        return bid;
      } else {
        Pair<Integer,Integer> p = new Pair<>(bid, nextBranchIndex);
        int i = bidIndex.indexOf(p);
        if (i < 0) {
          for (int j = 0; j < nextTotal; j++) {
            bidIndex.add(new Pair<>(bid, j + 1));
          }
          i = bidIndex.indexOf(p);
        }
        return i;
      }
    }

    protected Map<SequencePattern.State,Object> getMatchStateInfo(int bid, boolean add) {
      BranchState bs = getBranchState(bid, add);
      if (bs == null) {
        return null;
      }
      if (add && bs.matchStateInfo == null) {
        bs.matchStateInfo = new LinkedHashMap<>();
      }
      return bs.matchStateInfo;
    }

    protected Object getMatchStateInfo(int bid, SequencePattern.State node) {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, false);
      return (matchStateInfo != null)? matchStateInfo.get(node):null;
    }

    protected void removeMatchStateInfo(int bid, SequencePattern.State node) {
      Object obj = getMatchStateInfo(bid, node);
      if (obj != null) {
        Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
        matchStateInfo.remove(node);
      }
    }

    protected void setMatchStateInfo(int bid, SequencePattern.State node, Object obj) {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
      matchStateInfo.put(node, obj);
    }

    protected void startMatchedCountInc(int bid, SequencePattern.State node) {
      startMatchedCountInc(bid, node, 1, 1);
    }

    protected void startMatchedCountDec(int bid, SequencePattern.State node) {
      startMatchedCountInc(bid, node, 0, -1);
    }

    protected void startMatchedCountInc(int bid, SequencePattern.State node, int initialValue, int delta) {
      Map<SequencePattern.State,Object> matchStateCount = getMatchStateInfo(bid, true);
      Pair<Integer,Boolean> p = (Pair<Integer,Boolean>) matchStateCount.get(node);
      if (p == null) {
        matchStateCount.put(node, new Pair<>(initialValue, false));
      } else {
        matchStateCount.put(node, new Pair<>(p.first() + delta, false));
      }
    }

    protected int endMatchedCountInc(int bid, SequencePattern.State node) {
      Map<SequencePattern.State,Object> matchStateCount = getMatchStateInfo(bid, false);
      if (matchStateCount == null) { return 0; }
      matchStateCount = getMatchStateInfo(bid, true);
      Pair<Integer,Boolean> p = (Pair<Integer,Boolean>) matchStateCount.get(node);
      if (p != null) {
        int v = p.first();
        matchStateCount.put(node, new Pair<>(v, true));
        return v;
      } else {
        return 0;
      }
    }

    protected void clearMatchedCount(int bid, SequencePattern.State node)
    {
      removeMatchStateInfo(bid, node);
    }

    protected void setMatchedInterval(int bid, SequencePattern.State node, HasInterval<Integer> interval) {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
      HasInterval<Integer> p = (HasInterval<Integer>) matchStateInfo.get(node);
      if (p == null) {
        matchStateInfo.put(node, interval);
      } else {
        logger.warning("Interval already exists for bid=" + bid);
      }
    }

    protected HasInterval<Integer> getMatchedInterval(int bid, SequencePattern.State node) {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
      HasInterval<Integer> p = (HasInterval<Integer>) matchStateInfo.get(node);
      return p;
    }

    protected void addBidsToCollapse(int bid, int[] bids) {
      BranchState bs = getBranchState(bid, true);
      bs.addBidsToCollapse(bids);
    }

    private void mergeBranchStates(BranchState bs) {
      if (bs.bidsToCollapse != null && ! bs.bidsToCollapse.isEmpty()) {
        for (int cbid : bs.bidsToCollapse) {
          // Copy over the matched group info
          if (cbid != bs.bid) {
            BranchState cbs = getBranchState(cbid);
            if (cbs != null) {
              bs.addMatchedGroups(cbs.matchedGroups);
              bs.addMatchedResults(cbs.matchedResults);
            } else {
              logger.finest("Unable to find state info for bid=" + cbid);
            }
          }
        }
        if (bs.collapsedBids == null) {
          bs.collapsedBids = bs.bidsToCollapse;
        } else {
          bs.collapsedBids.addAll(bs.bidsToCollapse);
        }
        bs.bidsToCollapse = null;
      }
    }

  } // end static class BranchStates


  private String getMatchedSignature() {
    if (matchedGroups == null) return null;
    StringBuilder sb = new StringBuilder();
    for (MatchedGroup g : matchedGroups) {
      sb.append('(').append(g.matchBegin).append(',').append(g.matchEnd).append(')');
    }
    return sb.toString();
  }

  /**
   * Utility class that helps us perform pattern matching against a sequence.
   * Keeps information about:
   * <ul>
   * <li>the states we need to visit</li>
   * <li>the current position in the sequence we are at</li>
   * <li>state for each branch we took</li>
   * </ul>
   *
   * @param <T>  Type of node that the matcher is operating on
   */
  static class MatchedStates<T> {

    // Sequence matcher with pattern that we are matching against and sequence
    final SequenceMatcher<T> matcher;
    // Branch states
    BranchStates branchStates;
    // set of old states along with their branch ids (used to avoid reallocating mem)
    List<State> oldStates;
    // new states to be explored (along with their branch ids)
    List<State> states;
    // Current position to match
    int curPosition = -1;
    // Favor matching longest
    boolean matchLongest;

    protected MatchedStates(SequenceMatcher<T> matcher, SequencePattern.State state)
    {
      this(matcher, new BranchStates());
      int bid = branchStates.newBid(-1, 0);
      states.add(new State(bid,state));
    }

    private MatchedStates(SequenceMatcher<T> matcher, BranchStates branchStates) {
      this.matcher = matcher;
      states = new ArrayList<>();
      oldStates = new ArrayList<>();
      this.branchStates = branchStates;
      branchStates.link(this);
    }

    protected BranchStates getBranchStates()
    {
      return branchStates;
    }

    /**
     * Split part of the set of states to explore into another MatchedStates
     * @param branchLimit - rough limit on the number of branches we want
     *                      to keep in each MatchedStates
     * @return new MatchedStates with part of the states still to be explored
     */
    protected MatchedStates split(int branchLimit)
    {
      Set<Integer> curBidSet = new HashSet<>();//Generics.newHashSet();
      for (State state:states) {
        curBidSet.add(state.bid);
      }
      List<Integer> bids = new ArrayList<>(curBidSet);
      bids.sort((o1, o2) -> {
        int res = compareMatches(o1, o2);
        return res;
      });

      MatchedStates<T> newStates = new MatchedStates<>(matcher, branchStates);
      int v = Math.min(branchLimit, (bids.size()+1)/2);
      //Generics.newHashSet();
      Set<Integer> keepBidSet = new HashSet<>(bids.subList(0, v));
      swapAndClear();
      for (State s:oldStates) {
        if (keepBidSet.contains(s.bid)) {
          states.add(s);
        } else {
          newStates.states.add(s);
        }
      }
      newStates.curPosition = curPosition;
      branchStates.condense();
      return newStates;
    }

    protected List<? extends T> elements()
    {
      return matcher.elements;
    }

    protected T get()
    {
      return matcher.get(curPosition);
    }

    protected int size()
    {
      return states.size();
    }

    protected int branchSize()
    {
      return branchStates.size();
    }

    private void swap() {
      List<State> tmpStates = oldStates;
      oldStates = states;
      states = tmpStates;
    }

    private void swapAndClear() {
      swap();
      states.clear();
    }

    // Attempts to match element at the specified position
    private boolean match(int position) {
      curPosition = position;
      boolean matched = false;

      swapAndClear();
      // Start with old state, and try to match next element
      // New states to search after successful match will be updated during the match process
      for (State state:oldStates) {
        if (state.tstate.match(state.bid, this)) {
          matched = true;
        }
      }

      // Run NFA to process non consuming states
      boolean done = false;
      while (!done) {
        swapAndClear();
        boolean matched0 = false;
        for (State state:oldStates) {
          if (state.tstate.match0(state.bid, this)) {
            matched0 = true;
          }
        }
        done = !matched0;
      }

      branchStates.condense();
      return matched;
    }


    private final Integer[] p1Buffer = new Integer[128];
    private final Integer[] p2Buffer = new Integer[128];

    protected int compareMatches(int bid1, int bid2) {
      if (bid1 == bid2) return 0;
      List<Integer> p1 = branchStates.getParents(bid1, p1Buffer);
//      p1.add(bid1);
      List<Integer> p2 = branchStates.getParents(bid2, p2Buffer);
//      p2.add(bid2);
      int n = Math.min(p1.size(), p2.size());
      for (int i = 0; i < n; i++) {
        if (p1.get(i) < p2.get(i)) return -1;
        if (p1.get(i) > p2.get(i)) return 1;
      }
      return Integer.compare(p1.size(), p2.size());
    }

    /**
     * Returns index of state that results in match (-1 if no matches).
     */
    private int getMatchIndex() {
      for (int i = 0; i < states.size(); i++) {
        State state = states.get(i);
        if (state.tstate.equals(SequencePattern.MATCH_STATE)) {
          return i;
        }
      }
      return -1;
    }

    /**
     * Returns a set of indices that results in a match.
     */
    private Collection<Integer> getMatchIndices() {
      HashSet<Integer> allMatchIndices = new LinkedHashSet<>();// Generics.newHashSet();
      for (int i = 0; i < states.size(); i++) {
        State state = states.get(i);
        if (state.tstate.equals(SequencePattern.MATCH_STATE)) {
          allMatchIndices.add(i);
        }
      }
      return allMatchIndices;
    }

    /**
     * Of the potential match indices, selects one and returns it
     *  (returns -1 if no matches)
     */
    private int selectMatchIndex() {
      int best = -1;
      int bestbid = -1;
      MatchedGroup bestMatched = null;
      int bestMatchedLength = -1;
      for (int i = 0; i < states.size(); i++) {
        State state = states.get(i);
        if (state.tstate.equals(SequencePattern.MATCH_STATE)) {
          if (best < 0) {
            best = i;
            bestbid = state.bid;
            bestMatched = branchStates.getMatchedGroup(bestbid, 0);
            bestMatchedLength = (bestMatched != null)? bestMatched.matchLength() : -1;
          } else {
            // Compare if this match is better?
            int bid = state.bid;
            MatchedGroup mg = branchStates.getMatchedGroup(bid, 0);
            int matchLength = (mg != null)? mg.matchLength() : -1;
            // Select the branch that matched the most
            // TODO: Do we need to roll the matchedLength to bestMatchedLength check into the compareMatches?
            boolean better;
            if (matchLongest) {
              better = (matchLength > bestMatchedLength || (matchLength == bestMatchedLength && compareMatches(bestbid, bid) > 0));
            } else {
              better = compareMatches(bestbid, bid) > 0;
            }
            if (better) {
              bestbid = bid;
              best = i;
              bestMatched = branchStates.getMatchedGroup(bestbid, 0);
              bestMatchedLength = (bestMatched != null)? bestMatched.matchLength() : -1;
            }
          }
        }
      }
      return best;
    }

    private void completeMatch() {
      int matchStateIndex = selectMatchIndex();
      setMatchedGroups(matchStateIndex);
    }

    /**
     * Set the indices of the matched groups
     * @param matchStateIndex
     */
    private void setMatchedGroups(int matchStateIndex) {
      matcher.clearMatched();
      if (matchStateIndex >= 0) {
        State state = states.get(matchStateIndex);
        int bid = state.bid;
        BranchState bs = branchStates.getBranchState(bid);
        if (bs != null) {
          branchStates.mergeBranchStates(bs);
          Map<Integer,MatchedGroup> matchedGroups = bs.matchedGroups;
          if (matchedGroups != null) {
            for (int group : matchedGroups.keySet()) {
              matcher.matchedGroups[group] = matchedGroups.get(group);
            }
          }
          Map<Integer,Object> matchedResults = bs.matchedResults;
          if (matchedResults != null) {
            if (matcher.matchedResults == null) {
              matcher.matchedResults = new Object[matcher.elements().size()];
            }
            for (int index : matchedResults.keySet()) {
              matcher.matchedResults[index] = matchedResults.get(index);
            }
          }
        }
      }
    }

    private boolean isAllMatch() {
      boolean allMatch = true;
      if ( ! states.isEmpty()) {
        for (State state:states) {
          if (!state.tstate.equals(SequencePattern.MATCH_STATE)) {
            allMatch = false;
            break;
          }
        }
      } else {
        allMatch = false;
      }
      return allMatch;
    }

    private boolean isMatch() {
      int matchStateIndex = getMatchIndex();
      return (matchStateIndex >= 0);
    }

    protected void addStates(int bid, Collection<SequencePattern.State> newStates) {
      int i = 0;
      for (SequencePattern.State s:newStates) {
        i++;
        int id = branchStates.getBranchId(bid, i, newStates.size());
        states.add(new State(id, s));
      }
    }

    protected void addState(int bid, SequencePattern.State state)
    {
      this.states.add(new State(bid, state));
    }

    private void clean() {
      branchStates.unlink(this);
      branchStates = null;
    }

    protected void setGroupStart(int bid, int captureGroupId) {
      branchStates.setGroupStart(bid, captureGroupId, curPosition);
    }

    protected void setGroupEnd(int bid, int captureGroupId, Object value) {
      branchStates.setGroupEnd(bid, captureGroupId, curPosition, value);
    }

    protected void setGroupEnd(int bid, int captureGroupId, int position, Object value) {
      branchStates.setGroupEnd(bid, captureGroupId, position, value);
    }

    protected void clearGroupStart(int bid, int captureGroupId)
    {
      branchStates.clearGroupStart(bid, captureGroupId);
    }

  } // end static class MatchedStates

}
