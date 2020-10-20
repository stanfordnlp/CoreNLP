package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

/**
 * Generic Sequence Pattern for regular expressions.
 *
 * <p>
 * Similar to Java's {@link java.util.regex.Pattern} except it is for sequences over arbitrary types T instead
 *  of just characters.
 *
 * <p> A regular expression must first be compiled into
 * an instance of this class.  The resulting pattern can then be used to create
 * a {@link SequenceMatcher} object that can match arbitrary sequences of type T
 * against the regular expression.  All of the state involved in performing a match
 * resides in the matcher, so many matchers can share the same pattern.
 *
 * <p>
 * To support sequence matching on a new type T, the following is needed:
 * <ul>
 *   <li>Implement a {@link NodePattern for matching type T}</li>
 *   <li>Optionally define a language for node matches and implement {@link SequencePattern.Parser} to compile a
 *       regular expression into a SequencePattern.
 *   </li>
 *   <li>Optionally implement a {@link MultiPatternMatcher.NodePatternTrigger}
 *        for optimizing matches across multiple patterns</li>
 *   <li>Optionally implement a {@link NodesMatchChecker} to support backreferences</li>
 * </ul>
 * See {@link TokenSequencePattern} for an example of how this class can be extended
 * to support a specific type {@code T}.
 * <p>
 * To use
 * <pre>{@code
 *   SequencePattern p = SequencePattern.compile("....");
 *   SequenceMatcher m = p.getMatcher(tokens);
 *   while (m.find()) ....
 * }</pre>
 *
 *
 * <p>
 * To support a new type {@code T}:
 * <ol>
 * <li> For a type {@code T} to be matchable, it has to have a corresponding {@code NodePattern<T>} that indicates
 *    whether a node is matched or not  (see {@code CoreMapNodePattern} for example)</li>
 * <li> To compile a string into corresponding pattern, will need to create a parser
 *    (see inner class {@code Parser}, {@code TokenSequencePattern} and {@code TokenSequenceParser.jj})</li>
 * </ol>
 *
 * <p>
 * SequencePattern supports the following standard regex features:
 * <ul>
 *  <li>Concatenation </li>
 *  <li>Or </li>
 *  <li>Groups  (capturing  / noncapturing )  </li>
 *  <li>Quantifiers (greedy / nongreedy) </li>
 * </ul>
 *
 * <p>
 * SequencePattern also supports the following less standard features:
 * <ol>
 * <li> Environment (see {@link Env}) with respect to which the patterns are compiled</li>
 * <li> Binding of variables
 * <br>Use {@link Env} to bind variables for use when compiling patterns
 * <br>Can also bind names to groups (see {@link SequenceMatchResult} for accessor methods to retrieve matched groups)
 * </li>
 * <li> Backreference matches - need to specify how back references are to be matched using {@link NodesMatchChecker} </li>
 * <li> Multinode matches - for matching of multiple nodes using non-regex (at least not regex over nodes) patterns
 *                        (need to have corresponding {@link MultiNodePattern},
 *                         see {@link MultiCoreMapNodePattern} for example) </li>
 * <li> Conjunctions - conjunctions of sequence patterns (works for some cases)</li>
 * </ol>
 *
 * <p>Note that this and the inherited classes do not implement any custom equals and hashCode functions.
 *
 * @author Angel Chang
 * @see SequenceMatcher
 */
public class SequencePattern<T> implements Serializable {

  // TODO:
  //  1. Validate backref capture groupid
  //  2. Actions
  //  3. Inconsistent templating with T
  //  4. Update TokensSequenceParser to handle backref of other attributes (\9{attr1,attr2,...})
  //  5. Improve nested capture groups (in matchresult) for other node types such as conjunctions/disjunctions

  private static final long serialVersionUID = 3484918485303693833L;

  private String patternStr;
  private PatternExpr patternExpr;
  private SequenceMatchAction<T> action;

  State root;
  int totalGroups = 0;

  // binding of group number to variable name
  VarGroupBindings varGroupBindings;

  // Priority associated with the pattern (higher priority patterns should take precedence over lower priority ones)
  double priority = 0.0;
  // Weight associated with the pattern
  double weight = 0.0;

  protected SequencePattern(SequencePattern.PatternExpr nodeSequencePattern) {
    this(null, nodeSequencePattern);
  }

  protected SequencePattern(String patternStr, SequencePattern.PatternExpr nodeSequencePattern) {
    this(patternStr, nodeSequencePattern, null);
  }

  protected SequencePattern(String patternStr, SequencePattern.PatternExpr nodeSequencePattern,
                            SequenceMatchAction<T> action) {
    this.patternStr = patternStr;
    this.patternExpr = nodeSequencePattern;
    this.action = action;

    nodeSequencePattern = new GroupPatternExpr(nodeSequencePattern, true);
    nodeSequencePattern = nodeSequencePattern.optimize();
    this.totalGroups = nodeSequencePattern.assignGroupIds(0);
    Frag f = nodeSequencePattern.build();
    f.connect(MATCH_STATE);
    this.root = f.start;
    varGroupBindings = new VarGroupBindings(totalGroups+1);
    nodeSequencePattern.updateBindings(varGroupBindings);
  }

  @Override
  public String toString() {
    return this.pattern();
  }

  public <T2> SequencePattern<T2> transform(NodePatternTransformer<T,T2> transformer) {
    if (action != null) {
      throw new UnsupportedOperationException("transform on actions not yet implemented");
    }
    SequencePattern.PatternExpr transformedPattern = this.patternExpr.transform(transformer);
    // TODO: Make string unique by indicating this pattern was transformed
    return new SequencePattern<>(this.patternStr, transformedPattern, null);
  }

  public String pattern() {
    return patternStr;
  }

  protected PatternExpr getPatternExpr() {
    return patternExpr;
  }

  public double getPriority() {
    return priority;
  }

  public void setPriority(double priority) {
    this.priority = priority;
  }

  public double getWeight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }

  public SequenceMatchAction<T> getAction() {
    return action;
  }

  public void setAction(SequenceMatchAction<T> action) {
    this.action = action;
  }

  public int getTotalGroups() {
    return totalGroups;
  }

  // Compiles string (regex) to NFA for doing pattern simulation
  public static <T> SequencePattern<T> compile(Env env, String string) {
    try {
      Pair<PatternExpr, SequenceMatchAction<T>> p = env.parser.parseSequenceWithAction(env, string);
      return new SequencePattern<>(string, p.first(), p.second());
    } catch (Exception ex) {
      throw new RuntimeException("Error compiling " + string + " using environment " + env);
    }
    //throw new UnsupportedOperationException("Compile from string not implemented");
  }

  protected static <T> SequencePattern<T> compile(SequencePattern.PatternExpr nodeSequencePattern) {
    return new SequencePattern<>(nodeSequencePattern);
  }

  public SequenceMatcher<T> getMatcher(List<? extends T> tokens) {
    return new SequenceMatcher<>(this, tokens);
  }

  public <OUT> OUT findNodePattern(Function<NodePattern<T>, OUT> filter) {
    Queue<State> todo = new LinkedList<>();
    Set<State> seen = new HashSet<>();
    todo.add(root);
    seen.add(root);
    while (!todo.isEmpty()) {
      State state = todo.poll();
      if (state instanceof NodePatternState) {
        NodePattern<T> pattern = ((NodePatternState) state).pattern;
        OUT res = filter.apply(pattern);
        if (res != null) return res;
      }
      if (state.next != null) {
        for (State s: state.next) {
          if (!seen.contains(s)) { seen.add(s); todo.add(s); }
        }
      }
    }
    return null;
  }

  public <OUT> Collection<OUT> findNodePatterns(Function<NodePattern<T>, OUT> filter, boolean allowOptional, boolean allowBranching) {
    List<OUT> outList = new ArrayList<>();
    Queue<State> todo = new LinkedList<>();
    Set<State> seen = new HashSet<>();
    todo.add(root);
    seen.add(root);
    while (!todo.isEmpty()) {
      State state = todo.poll();
      if ((allowOptional || !state.isOptional) && (state instanceof NodePatternState)) {
        NodePattern<T> pattern = ((NodePatternState) state).pattern;
        OUT res = filter.apply(pattern);
        if (res != null) {
          outList.add(res);
        }
      }
      if (state.next != null) {
        boolean addNext = allowBranching || state.next.size() == 1;
        if (addNext) {
          for (State s : state.next) {
            if (!seen.contains(s)) {
              seen.add(s);
              todo.add(s);
            }
          }
        }
      }
    }
    return outList;
  }
  // Parses string to PatternExpr
  public interface Parser<T> {
    SequencePattern.PatternExpr parseSequence(Env env, String s) throws Exception;
    Pair<SequencePattern.PatternExpr, SequenceMatchAction<T>> parseSequenceWithAction(Env env, String s) throws Exception;
    SequencePattern.PatternExpr parseNode(Env env, String s) throws Exception;
  }

  // Binding of variable names to groups
  // matches the group indices
  static class VarGroupBindings {

    final String[] varnames;  // Assumes number of groups low

    protected VarGroupBindings(int size) {
      varnames = new String[size];
    }

    protected void set(int index, String name) {
      varnames[index] = name;
    }
  }

  // Interface indicating when two nodes match
  protected static interface NodesMatchChecker<T> {
    public boolean matches(T o1, T o2);
  }

  public static final NodesMatchChecker<Object> NODES_EQUAL_CHECKER = new NodesMatchChecker<Object>() {
    @Override
    public boolean matches(Object o1, Object o2) {
      return o1.equals(o2);
    }
  };

  public static final PatternExpr ANY_NODE_PATTERN_EXPR = new NodePatternExpr(NodePattern.ANY_NODE);
  public static final PatternExpr SEQ_BEGIN_PATTERN_EXPR = new SequenceStartPatternExpr();
  public static final PatternExpr SEQ_END_PATTERN_EXPR = new SequenceEndPatternExpr();

  /**
   * Represents a sequence pattern expressions (before translating into NFA).
   */
  public abstract static class PatternExpr implements Serializable {

    private static final long serialVersionUID = 7610237291757954879L;

    protected abstract Frag build();

    /**
     * Assigns group ids to groups embedded in this patterns starting with at the specified number,
     * returns the next available group id.
     *
     * @param start Group id to start with
     * @return The next available group id
     */
    protected abstract int assignGroupIds(int start);

    /**
     * Make a deep copy of the sequence pattern expressions
     */
    protected abstract PatternExpr copy();

    /**
     * Updates the binding of group to variable name
     * @param bindings
     */
    protected abstract void updateBindings(VarGroupBindings bindings);

    protected Object value() { return null; }

    /** Returns an optimized version of this pattern - default is a noop */
    protected PatternExpr optimize() { return this; }

    protected abstract PatternExpr transform(NodePatternTransformer transformer);
  }

  /** Represents one element to be matched. */
  public static class NodePatternExpr extends PatternExpr {

    final NodePattern nodePattern;

    public NodePatternExpr(NodePattern nodePattern) {
      this.nodePattern = nodePattern;
    }

    @Override
    protected Frag build()
    {
      State s = new NodePatternState(nodePattern);
      return new Frag(s);
    }

    @Override
    protected PatternExpr copy()
    {
      return new NodePatternExpr(nodePattern);
    }

    @Override
    protected int assignGroupIds(int start) { return start; }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {}

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      return new NodePatternExpr(transformer.transform(nodePattern));
    }

    public String toString() {
      return nodePattern.toString();
    }
  }

  /** Represents a pattern that can match multiple nodes. */
  public static class MultiNodePatternExpr extends PatternExpr {

    private final MultiNodePattern multiNodePattern;

    public MultiNodePatternExpr(MultiNodePattern nodePattern) {
      this.multiNodePattern = nodePattern;
    }

    @Override
    protected Frag build() {
      State s = new MultiNodePatternState(multiNodePattern);
      return new Frag(s);
    }

    @Override
    protected PatternExpr copy()
    {
      return new MultiNodePatternExpr(multiNodePattern);
    }

    @Override
    protected int assignGroupIds(int start) { return start; }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {}
    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      return new MultiNodePatternExpr(transformer.transform(multiNodePattern));
    }

    public String toString() {
      return multiNodePattern.toString();
    }
  }

  /** Represents one element to be matched. */
  public static class SpecialNodePatternExpr extends PatternExpr {

    private static final long serialVersionUID = 3347587132602082616L;

    private final String name;
    Factory<State> stateFactory;

    public SpecialNodePatternExpr(String name) {
      this(name, null);
    }

    public SpecialNodePatternExpr(String name, Factory<State> stateFactory) {
      this.name = name;
      this.stateFactory = stateFactory;
    }

    @Override
    protected Frag build() {
      State s = stateFactory.create();
      return new Frag(s);
    }

    @Override
    protected PatternExpr copy()
    {
      return new SpecialNodePatternExpr(name, stateFactory);
    }

    @Override
    protected int assignGroupIds(int start) { return start; }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {}
    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      return new SpecialNodePatternExpr(name, stateFactory);
    }

    public String toString() {
      return name;
    }
  }

  public static class SequenceStartPatternExpr extends SpecialNodePatternExpr implements Factory<State> {
    public SequenceStartPatternExpr() {
      super("SEQ_START");
      this.stateFactory = this;
    }

    @Override
    public State create() {
      return new SeqStartState();
    }
  }

  public static class SequenceEndPatternExpr extends SpecialNodePatternExpr implements Factory<State> {
    public SequenceEndPatternExpr() {
      super("SEQ_END");
      this.stateFactory = this;
    }

    @Override
    public State create() {
      return new SeqEndState();
    }
  }

  /** Represents a sequence of patterns to be matched. */
  public static class SequencePatternExpr extends PatternExpr {

    private static final long serialVersionUID = 7446769896088599604L;

    final List<PatternExpr> patterns;

    public SequencePatternExpr(List<PatternExpr> patterns) {
      this.patterns = patterns;
    }

    public SequencePatternExpr(PatternExpr... patterns) {
      this.patterns = Arrays.asList(patterns);
    }

    @Override
    protected Frag build() {
      Frag frag = null;
      if (patterns.size() > 0) {
        PatternExpr first = patterns.get(0);
        frag = first.build();
        for (int i = 1; i < patterns.size(); i++) {
          PatternExpr pattern = patterns.get(i);
          Frag f = pattern.build();
          frag.connect(f);
        }
      }
      return frag;
    }

    @Override
    protected int assignGroupIds(int start) {
      int nextId = start;
      for (PatternExpr pattern : patterns) {
        nextId = pattern.assignGroupIds(nextId);
      }
      return nextId;
    }

    @Override
    protected void updateBindings(VarGroupBindings bindings) {
      for (PatternExpr pattern : patterns) {
        pattern.updateBindings(bindings);
      }
    }

    @Override
    protected PatternExpr copy() {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.copy());
      }
      return new SequencePatternExpr(newPatterns);
    }

    @Override
    public PatternExpr optimize() {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.optimize());
      }
      return new SequencePatternExpr(newPatterns);
    }

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.transform(transformer));
      }
      return new SequencePatternExpr(newPatterns);
    }

    public String toString() {
      return StringUtils.join(patterns, " ");
    }
  }

  // Expression that indicates a back reference
  // Need to match a previously matched group somehow
  public static class BackRefPatternExpr extends PatternExpr {

    private static final long serialVersionUID = -4649629486266561619L;

    private final NodesMatchChecker matcher; // How a match is determined
    private final int captureGroupId;  // Indicates the previously matched group this need to match

    public BackRefPatternExpr(NodesMatchChecker matcher, int captureGroupId) {
      if (captureGroupId <= 0) { throw new IllegalArgumentException("Invalid captureGroupId=" + captureGroupId); }
      this.captureGroupId = captureGroupId;
      this.matcher = matcher;
    }

    @Override
    protected Frag build() {
      State s = new BackRefState(matcher, captureGroupId);
      return new Frag(s);
    }

    @Override
    protected int assignGroupIds(int start) {
      return start;
    }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {}

    @Override
    protected PatternExpr copy()
    {
      return new BackRefPatternExpr(matcher, captureGroupId);
    }

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      // TODO: Implement me!!!
      throw new UnsupportedOperationException("BackRefPatternExpr.transform not implemented yet!!! Please implement me!!!");
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      if (captureGroupId >= 0) {
        sb.append('\\').append(captureGroupId);
      } else {
        sb.append('\\');
      }
      sb.append('{').append(matcher).append('}');
      return sb.toString();
    }
  }

  public static class ValuePatternExpr extends PatternExpr {

    private final PatternExpr expr;
    private final Object value;

    public ValuePatternExpr(PatternExpr expr, Object value) {
      this.expr = expr;
      this.value = value;
    }

    @Override
    protected Frag build() {
      Frag frag = expr.build();
      frag.connect(new ValueState(value));
      return frag;
    }

    @Override
    protected int assignGroupIds(int start) {
      return expr.assignGroupIds(start);
    }

    @Override
    protected PatternExpr copy() {
      return new ValuePatternExpr(expr.copy(), value);
    }

    @Override
    protected PatternExpr optimize() {
      return new ValuePatternExpr(expr.optimize(), value);
    }

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      return new ValuePatternExpr(expr.transform(transformer), value);
    }

    @Override
    protected void updateBindings(VarGroupBindings bindings) {
      expr.updateBindings(bindings);
    }
  }

  /** Expression that represents a group. */
  public static class GroupPatternExpr extends PatternExpr {

    private static final long serialVersionUID = -6477601300665620926L;

    private final PatternExpr pattern;
    private final boolean capture; // Do capture or not?  If do capture, an capture group id will be assigned
    private int captureGroupId; // -1 if this pattern is not part of a capture group or capture group not yet assigned,
                             // otherwise, capture group number
    private final String varname;  // Alternate variable with which to refer to this group

    public GroupPatternExpr(PatternExpr pattern) {
      this(pattern, true);
    }

    public GroupPatternExpr(PatternExpr pattern, boolean capture) {
      this(pattern, capture, -1, null);
    }

    public GroupPatternExpr(PatternExpr pattern, String varname) {
      this(pattern, true, -1, varname);
    }

    private GroupPatternExpr(PatternExpr pattern, boolean capture, int captureGroupId, String varname) {
      this.pattern = pattern;
      this.capture = capture;
      this.captureGroupId = captureGroupId;
      this.varname = varname;
    }

    @Override
    protected Frag build()
    {
      Frag f = pattern.build();
      Frag frag = new Frag(new GroupStartState(captureGroupId, f.start), f.out);
      frag.connect(new GroupEndState(captureGroupId));
      return frag;
    }

    @Override
    protected int assignGroupIds(int start) {
      int nextId = start;
      if (capture) {
        captureGroupId = nextId;
        nextId++;
      }
      return pattern.assignGroupIds(nextId);
    }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {
      if (varname != null) {
        bindings.set(captureGroupId, varname);
      }
      pattern.updateBindings(bindings);
    }

    @Override
    protected PatternExpr copy()
    {
      return new GroupPatternExpr(pattern.copy(), capture, captureGroupId, varname);
    }

    @Override
    protected PatternExpr optimize() {
      return new GroupPatternExpr(pattern.optimize(), capture, captureGroupId, varname);
    }

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer) {
      return new GroupPatternExpr(pattern.transform(transformer), capture, captureGroupId, varname);
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append('(');
      if (!capture) {
        sb.append("?: ");
      } else if (varname != null) {
        sb.append('?').append(varname).append(' ');
      }
      sb.append(pattern);
      sb.append(')');
      return sb.toString();
    }
  }

  /**  Expression that represents a pattern that repeats for a number of times. */
  public static class RepeatPatternExpr extends PatternExpr {

    private static final long serialVersionUID = 3935482630250147745L;

    private final PatternExpr pattern;
    private final int minMatch;
    private final int maxMatch;
    private final boolean greedyMatch;

    public RepeatPatternExpr(PatternExpr pattern, int minMatch, int maxMatch) {
      this(pattern, minMatch, maxMatch, true);
    }

    public RepeatPatternExpr(PatternExpr pattern, int minMatch, int maxMatch, boolean greedy) {
      if (minMatch < 0) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch);
      }
      if (maxMatch >= 0 && minMatch > maxMatch) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch + ", maxMatch=" + maxMatch);
      }
      this.pattern = pattern;
      this.minMatch = minMatch;
      this.maxMatch = maxMatch;
      this.greedyMatch = greedy;
    }

    @Override
    protected Frag build()
    {
      Frag f = pattern.build();
      if (minMatch == 1 && maxMatch == 1) {
        return f;
      } else if (minMatch <= 5 && maxMatch <= 5 && greedyMatch) {
        // Make copies if number of matches is low
        // Doesn't handle nongreedy matches yet
        // For non greedy match need to move curOut before the recursive connect

        // Create NFA fragment that
        // have child pattern repeating for minMatch times
        if (minMatch > 0) {
          //  frag.start -> pattern NFA -> pattern NFA ->
          for (int i = 0; i < minMatch-1; i++) {
            Frag f2 = pattern.build();
            f.connect(f2);
          }
        } else {
          // minMatch is 0
          // frag.start ->
          f = new Frag(new State());
        }
        if (maxMatch < 0) {
          // Unlimited (loop back to self)
          //        --------
          //       \|/     |
          // ---> pattern NFA --->
          Set<State> curOut = f.out;
          Frag f2 = pattern.build();
          f2.connect(f2);
          f.connect(f2);
          f.add(curOut);
        } else {
          // Limited number of times this pattern repeat,
          // just keep add pattern (with option of being done) until maxMatch reached
          // ----> pattern NFA ----> pattern NFA --->
          //   |                |
          //   -->              --->
          for (int i = minMatch; i < maxMatch; i++) {
            Set<State> curOut = f.out;
            Frag f2 = pattern.build();
            f.connect(f2);
            f.add(curOut);
          }
        }
        if (minMatch == 0) {
          f.start.markOptional(true);
        }
        return f;
      }  else {
        // More general but more expensive matching (when branching, need to keep state explicitly)
        State s = new RepeatState(f.start, minMatch, maxMatch, greedyMatch);
        f.connect(s);
        return new Frag(s);
      }
    }

    @Override
    protected int assignGroupIds(int start) {
      return pattern.assignGroupIds(start);
    }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {
      pattern.updateBindings(bindings);
    }

    @Override
    protected PatternExpr copy()
    {
      return new RepeatPatternExpr(pattern.copy(), minMatch, maxMatch, greedyMatch);
    }
    @Override
    protected PatternExpr optimize()
    {
      return new RepeatPatternExpr(pattern.optimize(), minMatch, maxMatch, greedyMatch);
    }
    @Override
    protected PatternExpr transform(NodePatternTransformer transformer)
    {
      return new RepeatPatternExpr(pattern.transform(transformer), minMatch, maxMatch, greedyMatch);
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(pattern);
      sb.append('{').append(minMatch).append(',').append(maxMatch).append('}');
      if (!greedyMatch) {
        sb.append('?');
      }
      return sb.toString();
    }
  }

  /**  Expression that represents a disjunction. */
  public static class OrPatternExpr extends PatternExpr {

    private static final long serialVersionUID = 2566259662702631896L;

    private final List<PatternExpr> patterns;

    public OrPatternExpr(List<PatternExpr> patterns) {
      this.patterns = patterns;
    }

    public OrPatternExpr(PatternExpr... patterns) {
      this.patterns = Arrays.asList(patterns);
    }

    @Override
    protected Frag build()
    {
      Frag frag = new Frag();
      frag.start = new State();
      // Create NFA fragment that
      // have one starting state that branches out to NFAs created by the children expressions
      //  ---> pattern 1 --->
      //   |
      //   ---> pattern 2 --->
      //   ...
      for (PatternExpr pattern : patterns) {
        // Build child NFA
        Frag f = pattern.build();
        if (pattern.value() != null) {
          // Add value state to child NFA
          f.connect(new ValueState(pattern.value()));
        }
        // Add child NFA to next states of fragment start
        frag.start.add(f.start);
        // Add child NFA out (unlinked) states to out (unlinked) states of this fragment
        frag.add(f.out);
      }
      frag.start.markOptional(true);
      return frag;
    }

    @Override
    protected int assignGroupIds(int start) {
      int nextId = start;
      // assign group ids of child expressions
      for (PatternExpr pattern : patterns) {
        nextId = pattern.assignGroupIds(nextId);
      }
      return nextId;
    }
    @Override
    protected void updateBindings(VarGroupBindings bindings) {
      // update bindings of child expressions
      for (PatternExpr pattern : patterns) {
        pattern.updateBindings(bindings);
      }
    }

    @Override
    protected PatternExpr copy()
    {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.copy());
      }
      return new OrPatternExpr(newPatterns);
    }

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer)
    {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.transform(transformer));
      }
      return new OrPatternExpr(newPatterns);
    }

    public String toString() {
      return StringUtils.join(patterns, " | ");
    }

    // minimize size of or clauses to trigger optimization
    private static final int OPTIMIZE_MIN_SIZE = 5;
    @Override
    protected PatternExpr optimize()
    {
      if (patterns.size() <= OPTIMIZE_MIN_SIZE) {
        // Not enough patterns for fancy optimization
        List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
        for (PatternExpr p:patterns) {
          newPatterns.add(p.optimize());
        }
        return new OrPatternExpr(newPatterns);
      } else {
        // More fancy optimization
        return optimizeOr();
      }
    }

    private PatternExpr optimizeOr() {
      PatternExpr optimizedStringSeqs = optimizeOrStringSeqs();
      // Go through patterns and get candidate sequences with the same start...
      return optimizedStringSeqs;
    }

    private PatternExpr optimizeOrStringSeqs() {
      // Try to collapse OR of NodePattern with just strings into a StringInSetAnnotationPattern
      List<PatternExpr> opts = new ArrayList<>(patterns.size());
      // Map from annotation key (Class), ignoreCase (Boolean) to set of patterns/strings
      Map<Pair<Class,Boolean>, Pair<Collection<PatternExpr>, Set<String>>> stringPatterns =
              new HashMap<>();
      Map<Pair<Class,Boolean>, Pair<Collection<PatternExpr>, Set<List<String>>>> stringSeqPatterns =
              new HashMap<>();
      // Go through patterns and get candidates for optimization
      for (PatternExpr p:patterns) {
        PatternExpr opt = p.optimize();
        opts.add(opt);

        // Check for special patterns that we can optimize
        if (opt instanceof NodePatternExpr) {
          Pair<Class, CoreMapNodePattern.StringAnnotationPattern> pair = _getStringAnnotation_(opt);
          if (pair != null) {
            Boolean ignoreCase = pair.second.ignoreCase();
            String target = pair.second.target;
            Pair<Class,Boolean> key = Pair.makePair(pair.first, ignoreCase);
            Pair<Collection<PatternExpr>, Set<String>> saved = stringPatterns.get(key);
            if (saved == null) {
              saved = new Pair<>(new ArrayList<>(), new HashSet<>());
              stringPatterns.put(key, saved);
            }
            saved.first.add(opt);
            saved.second.add(target);
          }
        } else if (opt instanceof SequencePatternExpr) {
          SequencePatternExpr seq = (SequencePatternExpr) opt;
          if (seq.patterns.size() > 0) {
            boolean isStringSeq = true;
            Pair<Class,Boolean> key = null;
            List<String> strings = null;
            for (PatternExpr sp: seq.patterns) {
              // check if string match over same key
              Pair<Class, CoreMapNodePattern.StringAnnotationPattern> pair = _getStringAnnotation_(sp);
              if (pair != null) {
                if (key != null) {
                  // check key
                  if (key.first.equals(pair.first) && key.second.equals(pair.second.ignoreCase())) {
                    // okay
                  } else {
                    isStringSeq = false;
                    break;
                  }
                } else {
                  key = Pair.makePair(pair.first, pair.second.ignoreCase());
                  strings = new ArrayList<>();
                }
                strings.add(pair.second.target);
              } else {
                isStringSeq = false;
                break;
              }
            }
            if (isStringSeq) {
              Pair<Collection<PatternExpr>, Set<List<String>>> saved = stringSeqPatterns.get(key);
              if (saved == null) {
                saved = new Pair<>(new ArrayList<>(), new HashSet<>());
                stringSeqPatterns.put(key, saved);
              }
              saved.first.add(opt);
              saved.second.add(strings);
            }
          }
        }
      }

      // Go over our maps and see if any of these strings should be optimized away
      // Keep track of things we have optimized away
      Map<PatternExpr, Boolean> alreadyOptimized = new IdentityHashMap<>();
      List<PatternExpr> finalOptimizedPatterns = new ArrayList<>(patterns.size());
      // optimize strings
      for (Map.Entry<Pair<Class, Boolean>, Pair<Collection<PatternExpr>, Set<String>>> entry : stringPatterns.entrySet()) {
        Pair<Collection<PatternExpr>, Set<String>> saved = entry.getValue();
        Set<String> set = saved.second;
        int flags = (entry.getKey().second)? (NodePattern.CASE_INSENSITIVE | NodePattern.UNICODE_CASE):0;
        if (set.size() > OPTIMIZE_MIN_SIZE) {
          PatternExpr optimized = new NodePatternExpr(
                  new CoreMapNodePattern(entry.getKey().first, new CoreMapNodePattern.StringInSetAnnotationPattern(set, flags)));
          finalOptimizedPatterns.add(optimized);
          for (PatternExpr p:saved.first) {
            alreadyOptimized.put(p, true);
          }
        }
      }
      // optimize string sequences
      for (Map.Entry<Pair<Class, Boolean>, Pair<Collection<PatternExpr>, Set<List<String>>>> entry : stringSeqPatterns.entrySet()) {
        Pair<Collection<PatternExpr>, Set<List<String>>> saved = entry.getValue();
        Set<List<String>> set = saved.second;
        if (set.size() > OPTIMIZE_MIN_SIZE) {
          Pair<Class, Boolean> key = entry.getKey();
          PatternExpr optimized = new MultiNodePatternExpr(
                  new MultiCoreMapNodePattern.StringSequenceAnnotationPattern(key.first(), set, key.second()));
          finalOptimizedPatterns.add(optimized);
          for (PatternExpr p:saved.first) {
            alreadyOptimized.put(p, true);
          }
        }
      }
      // Add back original stuff that we didn't optimize
      for (PatternExpr p: opts) {
        Boolean included = alreadyOptimized.get(p);
        if (included == null || !included) {
          finalOptimizedPatterns.add(p);
        }
      }
      return new OrPatternExpr(finalOptimizedPatterns);
    }

    private static Pair<Class,CoreMapNodePattern.StringAnnotationPattern> _getStringAnnotation_(PatternExpr p) {
      if (p instanceof NodePatternExpr) {
        NodePattern nodePattern = ((NodePatternExpr) p).nodePattern;
        if (nodePattern instanceof CoreMapNodePattern) {
          List<Pair<Class, NodePattern>> annotationPatterns = ((CoreMapNodePattern) nodePattern).getAnnotationPatterns();
          if (annotationPatterns.size() == 1) {
            // Check if it is a string annotation pattern
            Pair<Class, NodePattern> pair = annotationPatterns.get(0);
            if (pair.second instanceof CoreMapNodePattern.StringAnnotationPattern) {
              return Pair.makePair(pair.first, (CoreMapNodePattern.StringAnnotationPattern) pair.second);
            }
          }
        }
      }
      return null;
    }
  }

  // Expression that represents a conjunction
  public static class AndPatternExpr extends PatternExpr {

    private static final long serialVersionUID = -5470437627660213806L;

    private final List<PatternExpr> patterns;

    public AndPatternExpr(List<PatternExpr> patterns) {
      this.patterns = patterns;
    }

    public AndPatternExpr(PatternExpr... patterns) {
      this.patterns = Arrays.asList(patterns);
    }

    @Override
    protected Frag build()
    {
      ConjStartState conjStart = new ConjStartState(patterns.size());
      Frag frag = new Frag();
      frag.start = conjStart;
      // Create NFA fragment that
      // have one starting state that branches out to NFAs created by the children expressions
      // AND START ---> pattern 1 --->  AND END (0/n)
      //            |
      //             ---> pattern 2 ---> AND END (1/n)
      //             ...
      for (int i = 0; i < patterns.size(); i++) {
        PatternExpr pattern = patterns.get(i);
        // Build child NFA
        Frag f = pattern.build();
        // Add child NFA to next states of fragment start
        frag.start.add(f.start);

        f.connect(new ConjEndState(conjStart, i));
        // Add child NFA out (unlinked) states to out (unlinked) states of this fragment
        frag.add(f.out);
      }
      return frag;
    }

    @Override
    protected int assignGroupIds(int start) {
      int nextId = start;
      // assign group ids of child expressions
      for (PatternExpr pattern : patterns) {
        nextId = pattern.assignGroupIds(nextId);
      }
      return nextId;
    }

    @Override
    protected void updateBindings(VarGroupBindings bindings) {
      // update bindings of child expressions
      for (PatternExpr pattern : patterns) {
        pattern.updateBindings(bindings);
      }
    }

    @Override
    protected PatternExpr copy()
    {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.copy());
      }
      return new AndPatternExpr(newPatterns);
    }

    @Override
    protected PatternExpr optimize()
    {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.optimize());
      }
      return new AndPatternExpr(newPatterns);
    }

    @Override
    protected PatternExpr transform(NodePatternTransformer transformer)
    {
      List<PatternExpr> newPatterns = new ArrayList<>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.transform(transformer));
      }
      return new AndPatternExpr(newPatterns);
    }

    public String toString() {
      return StringUtils.join(patterns, " & ");
    }
  }

  /* ----- NFA states for matching sequences ----- */

  // Patterns are converted to the NFA states
  // Assumes the matcher will step through the NFA states one token at a time

  /**
   * An accepting matching state
   */
  protected static final State MATCH_STATE = new MatchState();

  /**
   * Represents a state in the NFA corresponding to a regular expression for matching a sequence
   */
  static class State {
    /**
     * Set of next states from this current state.
     * NOTE: Most of the time, next is just one state.
     */
    Set<State> next;
    boolean hasSavedValue;
    boolean isOptional;    // is this state optional

    protected State() {}

    /**
     * Update the set of out states by unlinked states from this state
     * @param out - Current set of out states (to be updated by this function)
     */
    protected void updateOutStates(Set<State> out) {
      if (next == null) {
        out.add(this);
      } else {
        for (State s:next) {
          s.updateOutStates(out);
        }
      }
    }

    /**
     * Non-consuming match.
     * @param bid - Branch id
     * @param matchedStates - State of the matching so far (to be updated by the matching process)
     * @return true if match
     */
    protected <T> boolean  match0(int bid, SequenceMatcher.MatchedStates<T> matchedStates) {
      return match(bid, matchedStates, false);
    }

    /**
     * Consuming match.
     * @param bid - Branch id
     * @param matchedStates - State of the matching so far (to be updated by the matching process)
     * @return true if match
     */
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates) {
      return match(bid, matchedStates, true);
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume) {
      return match(bid, matchedStates, consume, null);
    }

    /**
     * Given the current matched states, attempts to run NFA from this state.
     *  If consuming:  tries to match the next element - goes through states until an element is consumed or match is false
     *  If non-consuming: does not match the next element - goes through non element consuming states
     * In both cases, matchedStates should be updated as follows:
     * - matchedStates should be updated with the next state to be processed.
     *
     * @param bid - Branch id
     * @param matchedStates - State of the matching so far (to be updated by the matching process)
     * @param consume - Whether to consume the next element or not
     * @return true if match
     */
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState) {
      boolean match = false;
      if (next != null) {
        int i = 0;
        for (State s:next) {
          i++;
          boolean m = s.match(matchedStates.branchStates.getBranchId(bid,i,next.size()), matchedStates, consume, this);
          if (m) {
            // NOTE: We don't break because other branches may have useful state information
            match = true;
          }
        }
      }
      return match;
    }

    /**
     * Add state to the set of next states.
     * @param nextState - state to add
     */
    protected void add(State nextState) {
      if (next == null) {
        next = Collections.singleton(nextState);
      } else if (next.size() == 1) {
        next = new LinkedHashSet<>(next);
        next.add(nextState);
      } else {
        next.add(nextState);
      }
    }

    public <T> Object value(int bid, SequenceMatcher.MatchedStates<T> matchedStates) {
      if (hasSavedValue) {
        HasInterval<Integer> matchedInterval = matchedStates.getBranchStates().getMatchedInterval(bid, this);
        if (matchedInterval != null && matchedInterval instanceof ValuedInterval) {
          return ((ValuedInterval) matchedInterval).getValue();
        }
      }
      return null;
    }

    public void markOptional(boolean propagate) {
      this.isOptional = true;
      if (propagate && next != null) {
        Stack<State> todo = new Stack<>();
        Set<State> seen = new HashSet<>();
        todo.addAll(next);
        while (!todo.empty()) {
          State s = todo.pop();
          s.isOptional = true;
          seen.add(s);
          if (next != null) {
            for (State n : next) {
              if (!seen.contains(n)) {
                todo.push(n);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Final accepting state.
   */
  private static class MatchState extends State {
    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState) {
      // Always add this state back (effectively looping forever in this matching state)
      matchedStates.addState(bid, this);
      return false;
    }
  }

  /**
   * State with associated value.
   */
  private static class ValueState extends State {
    final Object value;

    private ValueState(Object value) {
      this.value = value;
    }

    @Override
    public <T> Object value(int bid, SequenceMatcher.MatchedStates<T> matchedStates) { return value; }
  }

  /**
   * State for matching one element/node.
   */
  private static class NodePatternState extends State {
    final NodePattern pattern;

    protected NodePatternState(NodePattern p) {
      this.pattern = p;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      if (consume) {
        // Get element and return if it matched or not
        T node = matchedStates.get();
        // TODO: Fix type checking
        if (matchedStates.matcher.matchWithResult) {
          Object obj = pattern.matchWithResult(node);
          if (obj != null) {
            if (obj != Boolean.TRUE) {
              matchedStates.branchStates.setMatchedResult(bid, matchedStates.curPosition, obj);
            }
            // If matched, need to add next states to the queue of states to be processed
            matchedStates.addStates(bid, next);
            return true;
          } else {
            return false;
          }
        } else {
          if (node != null && pattern.match(node)) {
            // If matched, need to add next states to the queue of states to be processed
            matchedStates.addStates(bid, next);
            return true;
          } else {
            return false;
          }
        }
      } else {
        // Not consuming element - add this state back to queue of states to be processed
        // This state was not successfully matched
        matchedStates.addState(bid, this);
        return false;
      }
    }

  }

  /**
   * State for matching multiple elements/nodes.
   */
  private static class MultiNodePatternState extends State {

    private final MultiNodePattern pattern;

    protected MultiNodePatternState(MultiNodePattern p) {
      this.pattern = p;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      if (consume) {
        HasInterval<Integer> matchedInterval = matchedStates.getBranchStates().getMatchedInterval(bid, this);
        int cur = matchedStates.curPosition;
        if (matchedInterval == null) {
          // Haven't tried to match this node before, try now
          // Get element and return if it matched or not
          List<? extends T> nodes = matchedStates.elements();
          // TODO: Fix type checking
          Collection<HasInterval<Integer>> matched = pattern.match(nodes, cur);
          // Order matches
          if (pattern.isGreedyMatch()) {
            // Sort from long to short
            matched = CollectionUtils.sorted(matched, Interval.LENGTH_GT_COMPARATOR);
          } else {
            // Sort from short to long
            matched = CollectionUtils.sorted(matched, Interval.LENGTH_LT_COMPARATOR);
          }

          // TODO: Check intervals are valid?   Start at cur and ends after?
          if (matched != null && matched.size() > 0) {
            int nBranches = matched.size();
            int i = 0;
            for (HasInterval<Integer> interval:matched) {
              i++;
              int bid2 = matchedStates.getBranchStates().getBranchId(bid, i, nBranches);
              matchedStates.getBranchStates().setMatchedInterval(bid2, this, interval);
              // If matched, need to add next states to the queue of states to be processed
              // keep in current state until end node reached
              if (interval.getInterval().getEnd()-1 <= cur) {
                matchedStates.addStates(bid2, next);
              } else {
                matchedStates.addState(bid2, this);
              }
            }
            return true;
          } else {
            return false;
          }
        } else {
          // Previously matched this state - just need to step through until we get to end of matched interval
          if (matchedInterval.getInterval().getEnd()-1 <= cur) {
            matchedStates.addStates(bid, next);
          } else {
            matchedStates.addState(bid, this);
          }
          return true;
        }
      } else {
        // Not consuming element - add this state back to queue of states to be processed
        // This state was not successfully matched
        matchedStates.addState(bid, this);
        return false;
      }
    }

  }

  /**
   * State that matches a pattern that can occur multiple times.
   */
  private static class RepeatState extends State {

    private final State repeatStart;
    private final int minMatch;
    private final int maxMatch;
    private final boolean greedyMatch;

    public RepeatState(State start, int minMatch, int maxMatch, boolean greedyMatch)
    {
      this.repeatStart = start;
      this.minMatch = minMatch;
      this.maxMatch = maxMatch;
      this.greedyMatch = greedyMatch;
      if (minMatch < 0) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch);
      }
      if (maxMatch >= 0 && minMatch > maxMatch) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch + ", maxMatch=" + maxMatch);
      }
      this.isOptional = this.minMatch <= 0;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      // Get how many times this states has already been matched
      int matchedCount = matchedStates.getBranchStates().endMatchedCountInc(bid, this);
      // Get the minimum number of times we still need to match this state
      int minMatchLeft = minMatch - matchedCount;
      if (minMatchLeft < 0) {
        minMatchLeft = 0;
      }
      // Get the maximum number of times we can match this state
      int maxMatchLeft;
      if (maxMatch < 0) {
        // Indicate unlimited matching
        maxMatchLeft = maxMatch;
      } else {
        maxMatchLeft = maxMatch - matchedCount;
        if (maxMatch < 0) {
          // Already exceeded the maximum number of times we can match this state
          // indicate state not matched
          return false;
        }
      }
      boolean match = false;
      // See how many branching options there are...
      int totalBranches = 0;
      if (minMatchLeft == 0 && next != null) {
         totalBranches += next.size();
      }
      if (maxMatchLeft != 0) {
        totalBranches++;
      }
      int i = 0; // branch index
      // Check if there we have met the minimum number of matches
      // If so, go ahead and try to match next state
      //  (if we need to consume an element or end a group)
      if (minMatchLeft == 0 && next != null) {
        for (State s:next) {
          i++;   // Increment branch index
          // Depending on greedy match or not, different priority to branches
          int pi = (greedyMatch && maxMatchLeft != 0)? i+1:i;
          int bid2 = matchedStates.getBranchStates().getBranchId(bid,pi,totalBranches);
          matchedStates.getBranchStates().clearMatchedCount(bid2, this);
          boolean m = s.match(bid2, matchedStates, consume);
          if (m) {
            match = true;
          }
        }
      }
      // Check if we have the option of matching more
      // (maxMatchLeft < 0 indicate unlimited, maxMatchLeft > 0 indicate we are still allowed more matches)
      if (maxMatchLeft != 0) {
        i++;    // Increment branch index
        // Depending on greedy match or not, different priority to branches
        int pi = greedyMatch? 1:i;
        int bid2 = matchedStates.getBranchStates().getBranchId(bid,pi,totalBranches);
        if (consume) {
          // Premark many times we have matched this pattern
          matchedStates.getBranchStates().startMatchedCountInc(bid2, this);
          // Consuming - try to see if repeating this pattern does anything
          boolean m = repeatStart.match(bid2, matchedStates, consume);
          if (m) {
            match = true;
          } else {
            // Didn't match - decrement how many times we have matched this pattern
            matchedStates.getBranchStates().startMatchedCountDec(bid2, this);
          }
        } else {
          // Not consuming - don't do anything, just add this back to list of states to be processed
          matchedStates.addState(bid2, this);
        }
      }
      return match;
    }
  }

  /**
   * State for matching previously matched group.
   */
  static class BackRefState extends State {

    private final NodesMatchChecker matcher;
    private final int captureGroupId;

    public BackRefState(NodesMatchChecker matcher, int captureGroupId)
    {
      this.matcher = matcher;
      this.captureGroupId = captureGroupId;
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates,
                                SequenceMatcher.MatchedGroup matchedGroup, int matchedNodes)
    {
      T node = matchedStates.get();
      if (matcher.matches(node, matchedStates.elements().get(matchedGroup.matchBegin+matchedNodes))) {
        matchedNodes++;
        matchedStates.getBranchStates().setMatchStateInfo(bid, this,
                new Pair<>(matchedGroup, matchedNodes));
        int len = matchedGroup.matchEnd - matchedGroup.matchBegin;
        if (len == matchedNodes) {
          matchedStates.addStates(bid, next);
        } else {
          matchedStates.addState(bid, this);
        }
        return true;
      }
      return false;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      // Try to match previous node/nodes exactly
      if (consume) {
        // First element is group that is matched, second is number of nodes matched so far
        Pair<SequenceMatcher.MatchedGroup, Integer> backRefState =
                (Pair<SequenceMatcher.MatchedGroup, Integer>) matchedStates.getBranchStates().getMatchStateInfo(bid, this);
        if (backRefState == null) {
          // Haven't tried to match this node before, try now
          // Get element and return if it matched or not
          SequenceMatcher.MatchedGroup matchedGroup = matchedStates.getBranchStates().getMatchedGroup(bid, captureGroupId);
          if (matchedGroup != null) {
            // See if the first node matches
            if (matchedGroup.matchEnd > matchedGroup.matchBegin) {
              boolean matched = match(bid, matchedStates, matchedGroup, 0);
              return matched;
            } else {
              // TODO: Check handling of previous nodes that are zero elements?
              return super.match(bid, matchedStates, consume, prevState);
            }
          }
          return false;
        } else {
          SequenceMatcher.MatchedGroup matchedGroup = backRefState.first();
          int matchedNodes = backRefState.second();
          boolean matched = match(bid, matchedStates, matchedGroup, matchedNodes);
          return matched;
        }
      } else {
        // Not consuming, just add this state back to list of states to be processed
        matchedStates.addState(bid, this);
        return false;
      }
    }
  }

  /**
   * State for matching the start of a group.
   */
  static class GroupStartState extends State {

    private final int captureGroupId;

    public GroupStartState(int captureGroupId, State startState)
    {
      this.captureGroupId = captureGroupId;
      add(startState);
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      // We only mark start when about to consume elements
      if (consume) {
        // Start of group, mark start
        matchedStates.setGroupStart(bid, captureGroupId);
        return super.match(bid, matchedStates, consume, prevState);
      } else {
        // Not consuming, just add this state back to list of states to be processed
        matchedStates.addState(bid, this);
        return false;
      }
    }
  }

  /**
   * State for matching the end of a group.
   */
  static class GroupEndState extends State {

    private final int captureGroupId;

    public GroupEndState(int captureGroupId)
    {
      this.captureGroupId = captureGroupId;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState) {

      // Opposite of GroupStartState
      // Mark the end of the group
      Object v = (prevState != null) ? prevState.value(bid, matchedStates) : null;
      if (consume) {
        // We are consuming so the curPosition isn't part of our group
        matchedStates.setGroupEnd(bid, captureGroupId, matchedStates.curPosition-1, v);
      } else {
        matchedStates.setGroupEnd(bid, captureGroupId, v);
      }
      return super.match(bid, matchedStates, consume, prevState);
    }
  }

  static class ConjMatchStateInfo {

    // A conjunction consists of several child expressions
    //  When the conjunction state is entered,
    //    we keep track of the branch id and the node index
    //     we are on at that time (startBid and startPos)

    /**
     * The branch id when the conjunction state is entered
     */
    private final int startBid;

    /**
     * The node index when the conjunction state is entered
     */
    private final int startPos;

    /**
     * The number of child expressions making up the conjunction
     */
    private final int childCount;

    /**
     * For each child expression, we keep track of the
     *   set of branch ids that causes the child expression to
     *    be satisfied (and their corresponding node index
     *     when the expression is satisfied)
     */
    private final Set<Pair<Integer,Integer>>[] reachableChildBids;

    private ConjMatchStateInfo(int startBid, int childCount, int startPos)
    {
      this.startBid = startBid;
      this.startPos = startPos;
      this.childCount = childCount;
      this.reachableChildBids = new Set[childCount];
    }

    private void addChildBid(int i, int bid, int pos)
    {
      if (reachableChildBids[i] == null) {
        reachableChildBids[i] = new ArraySet<>();
      }
      reachableChildBids[i].add(new Pair<>(bid, pos) );
    }

    private boolean isAllChildMatched()
    {
      for (Set<Pair<Integer,Integer>> v:reachableChildBids) {
        if (v == null || v.isEmpty()) return false;
      }
      return true;
    }

    /**
     * Returns true if there is a feasible combination of child branch ids that
     * causes all child expressions to be satisfied with
     * respect to the specified child expression
     *   (assuming satisfaction with the specified branch and node index)
     * For other child expressions to have a compatible satisfiable branch,
     *   that branch must also terminate with the same node index as this one.
     *
     * @param index - Index of the child expression
     * @param bid - Branch id that causes the indexed child to be satisfied
     * @param pos - Node index that causes the indexed child to be satisfied
     * @return whether there is a feasible combination that causes all
     *          children to be satisfied with respect to specified child.
     */
    private boolean isAllChildMatched(int index, int bid, int pos)
    {
      for (int i = 0; i < reachableChildBids.length; i++) {
        Set<Pair<Integer,Integer>> v = reachableChildBids[i];
        if (v == null || v.isEmpty()) return false;
        if (i != index) {
          boolean ok = false;
          for (Pair<Integer,Integer> p:v) {
            if (p.second() == pos) {
              ok = true;
              break;
            }
          }
          if (!ok) { return false; }
        }
      }
      return true;
    }

    /**
     * Returns array of child branch ids that
     * causes all child expressions to be satisfied with
     * respect to the specified child expression
     *   (assuming satisfaction with the specified branch and node index).
     * For other child expressions to have a compatible satisfiable branch,
     *   that branch must also terminate with the same node index as this one.
     *
     * @param index - Index of the child expression
     * @param bid - Branch id that causes the indexed child to be satisfied
     * @param pos - Node index that causes the indexed child to be satisfied
     * @return array of child branch ids if there is a valid combination
     *         null otherwise
     */
    private int[] getAllChildMatchedBids(int index, int bid, int pos)
    {
      int[] matchedBids = new int[reachableChildBids.length];
      for (int i = 0; i < reachableChildBids.length; i++) {
        Set<Pair<Integer,Integer>> v = reachableChildBids[i];
        if (v == null || v.isEmpty()) return null;
        if (i != index) {
          boolean ok = false;
          for (Pair<Integer,Integer> p:v) {
            if (p.second() == pos) {
              ok = true;
              matchedBids[i] = p.first();
              break;
            }
          }
          if (!ok) { return null; }
        } else {
          matchedBids[i] = bid;
        }
      }
      return matchedBids;
    }

    protected void updateKeepBids(BitSet bids) {
      // TODO: Is there a point when we don't need to keep these bids anymore?
      for (Set<Pair<Integer, Integer>> v : reachableChildBids) {
        if (v != null) {
          for (Pair<Integer, Integer> p : v) {
            bids.set(p.first());
          }
        }
      }
    }
  }



  private void readObject(ObjectInputStream ois)
    throws IOException, ClassNotFoundException {
    patternStr = (String)ois.readObject();

    patternExpr = (PatternExpr) ois.readObject();
    //this.patternStr = patternStr;
    //this.patternExpr = nodeSequencePattern;
    action = (SequenceMatchAction) ois.readObject();

    patternExpr = new GroupPatternExpr(patternExpr, true);
    patternExpr = patternExpr.optimize();
    this.totalGroups = patternExpr.assignGroupIds(0);
    Frag f = patternExpr.build();
    f.connect(MATCH_STATE);
    this.root = f.start;
    varGroupBindings = new VarGroupBindings(totalGroups+1);
    patternExpr.updateBindings(varGroupBindings);
  }


  private void writeObject(ObjectOutputStream oos)
    throws IOException {
    oos.writeObject(toString());
    oos.writeObject(this.getPatternExpr());
    oos.writeObject(this.getAction());

  }  //  public void writeObject()

  // States for matching conjunctions
  // - Basic, not well tested implementation that may not work for all cases ...
  // - Can be optimized to terminate earlier if one branch of the conjunction is known not to succeed
  // - May cause lots of states to be kept (not efficient)
  // - priority should be specified for conjunction branches (there can be conflicting greedy/nongreedy patterns)
  //   (should we prioritize by order?) - currently behavior is not well defined

  /**
   * State for matching a conjunction
   */
  static class ConjStartState extends State {

    private final int childCount;  // Number of children that this conjunction consists of

    public ConjStartState(int childCount)
    {
      this.childCount = childCount;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      matchedStates.getBranchStates().setMatchStateInfo(bid, this,
              new ConjMatchStateInfo(bid, childCount, matchedStates.curPosition));
      // Start of conjunction, mark start
      boolean allMatch = true;
      if (next != null) {
        int i = 0;
        for (State s:next) {
          i++;
          boolean m = s.match(matchedStates.getBranchStates().getBranchId(bid,i,next.size()), matchedStates, consume);
          if (!m) {
            allMatch = false;
            break;
          }
        }
      }
      return allMatch;
    }
  }

  /**
   * State for matching the end of a conjunction.
   */
  static class ConjEndState extends State {

    private final ConjStartState startState;
    private final int childIndex;

    public ConjEndState(ConjStartState startState, int childIndex)
    {
      this.startState = startState;
      this.childIndex = childIndex;
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      // Opposite of ConjStartState
      // Don't do anything when we are about to consume an element
      // Only we are done consuming, and preparing to go on to the next element
      // do we check if all branches matched
      if (consume) {
        return false;
      } else {
        // NOTE: There is a delayed matched here, in that we actually want to remember
        //  which of the incoming branches succeeded
        // Use the bid of the corresponding ConjAndState?
        ConjMatchStateInfo stateInfo = (ConjMatchStateInfo) matchedStates.getBranchStates().getMatchStateInfo(bid, startState);
        if (stateInfo != null) {
          stateInfo.addChildBid(childIndex, bid, matchedStates.curPosition);
          int[] matchedBids = stateInfo.getAllChildMatchedBids(childIndex, bid, matchedStates.curPosition);
          if (matchedBids != null) {
            matchedStates.getBranchStates().addBidsToCollapse(bid, matchedBids);
            return super.match(bid, matchedStates, consume, prevState);
          }
        }
        return false;
      }
    }
  }

  /**
   * State for matching start of sequence.
   */
  static class SeqStartState extends State {

    public SeqStartState()
    {
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      if (consume) {
        if (matchedStates.curPosition == 0) {
          // Okay - try next
          return super.match(bid, matchedStates, consume, this);
        }
      }
      return false;
    }
  }

  /**
   * State for matching end of sequence.
   */
  static class SeqEndState extends State {

    public SeqEndState()
    {
    }

    @Override
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume, State prevState)
    {
      if (!consume) {
        if (matchedStates.curPosition == matchedStates.elements().size()-1) {
          // Okay - try next
          return super.match(bid, matchedStates, consume, this);
        }
      }
      return false;
    }
  }

  /**
   * Represents a incomplete NFS with start State and a set of unlinked out states.
   */
  private static class Frag {
    State start;
    Set<State> out;

    protected Frag() {
 //     this(new State());
    }

    protected Frag(State start) {
      this.start = start;
      this.out = new LinkedHashSet<>();
      start.updateOutStates(out);
    }

    protected Frag(State start, Set<State> out) {
      this.start = start;
      this.out = out;
    }

    protected void add(State outState) {
      if (out == null) {
        out = new LinkedHashSet<>();
      }
      out.add(outState);
    }

    protected void add(Collection<State> outStates) {
      if (out == null) {
        out = new LinkedHashSet<>();
      }
      out.addAll(outStates);
    }

    // Connect frag f to the out states of this frag
    // the out states of this frag is updated to be the out states of f
    protected void connect(Frag f) {
      for (State s:out) {
        s.add(f.start);
      }
      out = f.out;
    }

    // Connect state to the out states of this frag
    // the out states of this frag is updated to be the out states of state
    protected void connect(State state) {
      for (State s:out) {
        s.add(state);
      }
      out = new LinkedHashSet<>();
      state.updateOutStates(out);
/*      if (state.next != null) {
        out.addAll(state.next);
      } else {
        out.add(state);
      } */
    }

  } // end static class Frag

} // end class SequencePattern
