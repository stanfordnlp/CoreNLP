package edu.stanford.nlp.ling.tokensregex.types;

import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.EnvLookup;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.MetaClass;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Various implementations of the Expression interface
 *
 * @author Angel Chang
 */
public class Expressions {
  /** VAR - Variable */
  public static final String TYPE_VAR = "VAR";
  /** FUNCTION - (input) => (output) where input is a list of Values, and output is a single Value */
  public static final String TYPE_FUNCTION = "FUNCTION";
  /** REGEX - Regular expression pattern (for tokens or string) */
  public static final String TYPE_REGEX = "REGEX";
  public static final String TYPE_STRING_REGEX = "STRING_REGEX";
  public static final String TYPE_TOKEN_REGEX = "TOKEN_REGEX";
  /** REGEXMATCHVAR - Variable that refers to variable resulting from a regex match or used in a regex match (starts with $) */
  public static final String TYPE_REGEXMATCHVAR = "REGEXMATCHVAR";
  /** STRING - String */
  public static final String TYPE_STRING = "STRING";
  /** NUMBER - Numeric value (can be integer or real) */
  public static final String TYPE_NUMBER = "NUMBER";
  /** COMPOSITE - Composite value with field names and field values */
  public static final String TYPE_COMPOSITE = "COMPOSITE";
  /** LIST - List */
  public static final String TYPE_LIST = "LIST";
  public static final String TYPE_SET = "SET";
  public static final String TYPE_ANNOTATION_KEY = "ANNOKEY";
  /** CLASS - Maps to a Java class */
  public static final String TYPE_CLASS = "CLASS";
  public static final String TYPE_TOKENS = "TOKENS";
  public static final String TYPE_BOOLEAN = "BOOLEAN";

  public final static Value<Boolean> TRUE = new PrimitiveValue<Boolean>(Expressions.TYPE_BOOLEAN, true);
  public final static Value<Boolean> FALSE = new PrimitiveValue<Boolean>(Expressions.TYPE_BOOLEAN, false);
  public final static Value NIL = new PrimitiveValue("NIL", null);

  public static Boolean convertValueToBoolean(Value v, boolean keepNull) {
    Boolean res = null;
    if (v != null) {
      Object obj = v.get();
      if (obj != null) {
        if (obj instanceof Boolean) {
          res = ((Boolean) obj).booleanValue();
        } else if (obj instanceof Integer) {
          res = (((Integer) obj).intValue() != 0);
        } else {
          res = true;
        }
        return res;
      }
    }
    return (keepNull)? res:false;
  }

  public static Value<Boolean> convertValueToBooleanValue(Value v, boolean keepNull) {
    if (v != null) {
      Object obj = v.get();
      if (obj instanceof Boolean) {
        return (Value<Boolean>) v;
      } else {
        return new PrimitiveValue<Boolean>(Expressions.TYPE_BOOLEAN, convertValueToBoolean(v, keepNull));
      }
    } else {
      return keepNull? null:FALSE;
    }
  }

  public static <C> C asObject(Env env, Object v) {
    if (v instanceof Expression) {
      return (C) ((Expression) v).evaluate(env).get();
    } else {
      return (C) v;
    }
  }

  public static Expression asExpression(Env env, Object v) {
    if (v instanceof Expression) {
      return (Expression) v;
    } else {
      return createValue(null, v);
    }
  }

  public static Value asValue(Env env, Object v) {
    if (v instanceof Value) {
      return (Value) v;
    } else {
      return createValue(null, v);
    }
  }

  public static <T> Value createValue(String typename, T value, String... tags) {
    if (value instanceof Value) {
      return (Value) value;
    } else {
      return new PrimitiveValue<T>(typename, value, tags);
    }
  }

  /**
   * An expression that is a wrapper around another expression
   */
  public abstract static class WrappedExpression implements Expression {
    protected Expression expr;

    public Tags getTags() {
      return expr.getTags();
    }

    public void setTags(Tags tags) {
      expr.setTags(tags);
    }

    public String getType() {
      return expr.getType();
    }

    public Expression simplify(Env env) {
      return expr.simplify(env);
    }

    public boolean hasValue() {
      return expr.hasValue();
    }

    public Value evaluate(Env env, Object... args) {
      return expr.evaluate(env, args);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof WrappedExpression)) return false;

      WrappedExpression that = (WrappedExpression) o;

      if (expr != null ? !expr.equals(that.expr) : that.expr != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return expr != null ? expr.hashCode() : 0;
    }
  }

  /**
  * An expression with a typename and tags
  */
  public abstract static class TypedExpression implements Expression, Serializable {
    String typename;
    Tags tags;

    public TypedExpression(String typename, String... tags) {
      this.typename = typename;
      if (tags != null) {
        this.tags = new Tags(tags);
      }
    }

    public Tags getTags() {
      return tags;
    }

    public void setTags(Tags tags) {
      this.tags = tags;
    }

    public String getType() {
      return typename;
    }

    public Expression simplify(Env env) {
      return this;
    }

    public boolean hasValue() {
      return false;
    }

    private static final long serialVersionUID = 2;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TypedExpression)) return false;

      TypedExpression that = (TypedExpression) o;

      if (tags != null ? !tags.equals(that.tags) : that.tags != null) return false;
      if (typename != null ? !typename.equals(that.typename) : that.typename != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = typename != null ? typename.hashCode() : 0;
      result = 31 * result + (tags != null ? tags.hashCode() : 0);
      return result;
    }
  }

  /**
   * A simple implementation of an expression that is represented by a java object of type T
   * @param <T> type of the expression object
   */
  public static abstract class SimpleExpression<T> extends Expressions.TypedExpression {
    T value;

    protected SimpleExpression(String typename, T value, String... tags) {
      super(typename, tags);
      this.value = value;
    }

    public T get() {
      return value;
    }

    public String toString() {
      return getType() + "(" + value + ")";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SimpleExpression)) return false;
      if (!super.equals(o)) return false;

      SimpleExpression that = (SimpleExpression) o;

      if (value != null ? !value.equals(that.value) : that.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }
  }

  /**
   * A simple implementation of an expression that is represented by a java object of type T
   *    and which also has a cached Value stored with it
   * @param <T> type of the expression object
   */
  public static class SimpleCachedExpression<T> extends SimpleExpression<T> {
    Value evaluated;
    boolean disableCaching = false;

    protected SimpleCachedExpression(String typename, T value, String... tags) {
      super(typename, value, tags);
    }

    protected Value doEvaluation(Env env, Object... args) {
      throw new UnsupportedOperationException("Cannot evaluate type: " + typename);
    }

    public Value evaluate(Env env, Object... args) {
      if (args != null) {
        return doEvaluation(env, args);
      }
      if (evaluated == null || disableCaching) {
        evaluated = doEvaluation(env, args);
      }
      return evaluated;
    }

    public boolean hasValue() {
      return (evaluated != null);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SimpleCachedExpression)) return false;

      SimpleCachedExpression that = (SimpleCachedExpression) o;

      if (disableCaching != that.disableCaching) return false;
      if (evaluated != null ? !evaluated.equals(that.evaluated) : that.evaluated != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = evaluated != null ? evaluated.hashCode() : 0;
      result = 31 * result + (disableCaching ? 1 : 0);
      return result;
    }
  }

  /**
   * Simple implementation of Value backed by a java object of type T
   * @param <T>
   */
  public static class SimpleValue<T> extends Expressions.TypedExpression implements Value<T> {
    T value;

    protected SimpleValue(String typename, T value, String... tags) {
      super(typename, tags);
      this.value = value;
    }

    public T get() {
      return value;
    }

    public Value evaluate(Env env, Object... args) {
      return this;
    }

    public String toString() {
      return getType() + "(" + value + ")";
    }

    public boolean hasValue() {
      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SimpleValue)) return false;
      if (!super.equals(o)) return false;

      SimpleValue that = (SimpleValue) o;

      if (value != null ? !value.equals(that.value) : that.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }
  }

  /**
   * A string that represents a regular expression
   */
  public static class RegexValue extends SimpleValue<String> {
    public RegexValue(String regex, String... tags) {
      super(TYPE_REGEX, regex, tags);
    }
  }

  /**
   * A variable assignment with the name of the variable, and the expression to assign to that variable
   */
  public static class VarAssignmentExpression extends Expressions.TypedExpression {
    String varName;
    Expression valueExpr;
    boolean bindAsValue = false;

    public VarAssignmentExpression(String varName, Expression valueExpr, boolean bindAsValue) {
      super("VAR_ASSIGNMENT");
      this.varName = varName;
      this.valueExpr = valueExpr;
      this.bindAsValue = bindAsValue;
    }
    public Value evaluate(Env env, Object... args) {
      Value value = valueExpr.evaluate(env, args);
      if (args != null) {
        if (args.length == 1 && args[0] instanceof CoreMap) {
          CoreMap cm = (CoreMap) args[0];
          Class annotationKey = EnvLookup.lookupAnnotationKey(env, varName);
          if (annotationKey != null) {
            cm.set(annotationKey, (value != null)? value.get():null);
            return value;
          }
        }
      }
      if (bindAsValue) {
        env.bind(varName, value);
      } else {
        env.bind(varName, (value != null)? value.get():null);
        if (TYPE_REGEX == value.getType()) {
          try {
            Object vobj = value.get();
            if (vobj instanceof String) {
              env.bindStringRegex(varName, (String) vobj);
            } else if (vobj instanceof Pattern) {
              env.bindStringRegex(varName, ((Pattern) vobj).pattern());
            }
          } catch (Exception ex) {}
        }
      }
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof VarAssignmentExpression)) return false;
      if (!super.equals(o)) return false;

      VarAssignmentExpression that = (VarAssignmentExpression) o;

      if (bindAsValue != that.bindAsValue) return false;
      if (valueExpr != null ? !valueExpr.equals(that.valueExpr) : that.valueExpr != null) return false;
      if (varName != null ? !varName.equals(that.varName) : that.varName != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (varName != null ? varName.hashCode() : 0);
      result = 31 * result + (valueExpr != null ? valueExpr.hashCode() : 0);
      result = 31 * result + (bindAsValue ? 1 : 0);
      return result;
    }
  }

  /**
   * A variable, which can be assigned any expression.
   * When evaluated, the value of the variable is retrieved from the
   *   environment, evaluated, and returned.
   */
  public static class VarExpression extends SimpleExpression<String> implements AssignableExpression  {
    public VarExpression(String varname, String... tags) {
      super(TYPE_VAR, varname, tags);
    }
    public Value evaluate(Env env, Object... args) {
      Expression exp = null;
      String varName = value;
      if (args != null) {
        if (args.length == 1 && args[0] instanceof CoreMap) {
          CoreMap cm = (CoreMap) args[0];
          Class annotationKey = EnvLookup.lookupAnnotationKey(env, varName);
          if (annotationKey != null) {
            return createValue(varName, cm.get(annotationKey));
          }
        }
      }
      Object obj = env.get(varName);
      if (obj != null) {
        exp = asExpression(env, obj);
      }
      Value v = exp != null? exp.evaluate(env, args): null;
      if (v == null) {
        System.err.println("Unknown variable: " + varName);
      }
      return v;
    }
    public Expression assign(Expression expr) {
      return new VarAssignmentExpression(value, expr, true);
    }
  }

  /**
   * A variable that represents a regular expression match result.
   * The match result is identified either by the group id (Integer) or
   *   the group name (String).
   * When evaluated, one argument (the MatchResult or SequenceMatchResult) must be supplied.
   * Depending on the match result supplied, the returned value
   *   is either a String (for MatchResult) or a list of tokens (for SequenceMatchResult).
   */
  private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
  public static class RegexMatchVarExpression extends SimpleExpression implements AssignableExpression {
    public RegexMatchVarExpression(String groupname, String... tags) {
      super(TYPE_REGEXMATCHVAR, groupname, tags);
    }
    public RegexMatchVarExpression(Integer groupid, String... tags) {
      super(TYPE_REGEXMATCHVAR, groupid, tags);
    }
    public static RegexMatchVarExpression valueOf(String group) {
      if (DIGITS_PATTERN.matcher(group).matches()) {
        Integer n = Integer.valueOf(group);
        return new RegexMatchVarExpression(n);
      } else {
        return new RegexMatchVarExpression(group);
      }
    }
    public Value evaluate(Env env, Object... args) {
      if (args != null && args.length > 0) {
        if (args[0] instanceof SequenceMatchResult) {
          SequenceMatchResult mr = (SequenceMatchResult) args[0];
          Object v = get();
          if (v instanceof String) {
            // TODO: depending if TYPE_STRING, use string version...
            return new PrimitiveValue<List>(TYPE_TOKENS, mr.groupNodes((String) v));
          } else if (v instanceof Integer) {
            return new PrimitiveValue<List>(TYPE_TOKENS, mr.groupNodes((Integer) v));
          } else {
            throw new UnsupportedOperationException("String match result must be referred to by group id");
          }
        } else if (args[0] instanceof MatchResult) {
          MatchResult mr = (MatchResult) args[0];
          Object v = get();
          if (v instanceof Integer) {
            String str = mr.group((Integer) get());
            return new PrimitiveValue<String>(TYPE_STRING, str);
          } else {
            throw new UnsupportedOperationException("String match result must be referred to by group id");
          }
        }
      }
      return null;
    }
    public Expression assign(Expression expr) {
      return new VarAssignmentExpression(value.toString(), expr, false);
    }
  }

  public static class RegexMatchResultVarExpression extends SimpleExpression {
    public RegexMatchResultVarExpression(String groupname, String... tags) {
      super(TYPE_REGEXMATCHVAR, groupname, tags);
    }
    public RegexMatchResultVarExpression(Integer groupid, String... tags) {
      super(TYPE_REGEXMATCHVAR, groupid, tags);
    }
    public static RegexMatchResultVarExpression valueOf(String group) {
      if (DIGITS_PATTERN.matcher(group).matches()) {
        Integer n = Integer.valueOf(group);
        return new RegexMatchResultVarExpression(n);
      } else {
        return new RegexMatchResultVarExpression(group);
      }
    }
    public Value evaluate(Env env, Object... args) {
      if (args != null && args.length > 0) {
        if (args[0] instanceof SequenceMatchResult) {
          SequenceMatchResult mr = (SequenceMatchResult) args[0];
          Object v = get();
          if (v instanceof String) {
            return new PrimitiveValue("MATCHED_GROUP_INFO", mr.groupInfo((String) v));
          } else if (v instanceof Integer) {
            return new PrimitiveValue("MATCHED_GROUP_INFO", mr.groupInfo((Integer) v));
          } else {
            throw new UnsupportedOperationException("String match result must be referred to by group id");
          }
        }
      }
      return null;
    }
  }

  /**
   * A function call that can be assigned a value.
   */
  public static class AssignableFunctionCallExpression extends FunctionCallExpression implements AssignableExpression {
    public AssignableFunctionCallExpression(String function, List<Expression> params, String... tags) {
      super(function, params, tags);
    }

    public Expression assign(Expression expr) {
      List<Expression> newParams = new ArrayList<Expression>(params);
      newParams.add(expr);
      Expression res = new FunctionCallExpression(function, newParams);
      res.setTags(tags);
      return res;
    }
  }

  public static class IndexedExpression extends AssignableFunctionCallExpression {
    public IndexedExpression(Expression expr, int index) {
      super("ListSelect", Arrays.asList(expr, new PrimitiveValue("Integer", index)));
    }
  }

  public static class FieldExpression extends AssignableFunctionCallExpression {
    public FieldExpression(Expression expr, String field) {
      super("Select", Arrays.asList(expr, new PrimitiveValue(TYPE_STRING, field)));
    }
    public FieldExpression(Expression expr, Expression field) {
      super("Select", Arrays.asList(expr, field));
    }
  }

  public static class OrExpression extends FunctionCallExpression {
    public OrExpression(List<Expression> children) {
      super("Or", children);
    }
  }

  public static class AndExpression extends FunctionCallExpression {
    public AndExpression(List<Expression> children) {
      super("And", children);
    }
  }

  public static class NotExpression extends FunctionCallExpression {
    public NotExpression(Expression expr) {
      super("Not", Arrays.asList(expr));
    }
  }

  public static class IfExpression extends Expressions.TypedExpression {
    Expression condExpr;
    Expression trueExpr;
    Expression falseExpr;

    public IfExpression(Expression cond, Expression vt, Expression vf) {
      super("If");
      this.condExpr = cond;
      this.trueExpr = vt;
      this.falseExpr = vf;
    }

    public Value evaluate(Env env, Object... args) {
      Value condValue = condExpr.evaluate(env, args);
      Boolean cond = (Boolean) condValue.get();
      if (cond) {
        return trueExpr.evaluate(env, args);
      } else {
        return falseExpr.evaluate(env, args);
      }
    }
  }

  public static class CaseExpression extends Expressions.WrappedExpression {
    public CaseExpression(List<Pair<Expression,Expression>> conds, Expression elseExpr) {
      if (conds.size() == 0) {
        throw new IllegalArgumentException("No conditions!");
      } else {
        expr = elseExpr;
        for (int i = conds.size()-1; i>=0; i--) {
          Pair<Expression,Expression> p = conds.get(i);
          expr = new IfExpression(p.first(), p.second(), expr);
        }
      }
    }
  }

  public static class ConditionalExpression extends Expressions.WrappedExpression {
    public ConditionalExpression(Expression expr) {
      this.expr = expr;
    }

    public ConditionalExpression(String op, Expression expr1, Expression expr2) {
      if (">=".equals(op)) {
        expr = new FunctionCallExpression("GE", Arrays.asList(expr1, expr2));
      } else if ("<=".equals(op))  {
        expr = new FunctionCallExpression("LE", Arrays.asList(expr1, expr2));
      } else if (">".equals(op)) {
        expr = new FunctionCallExpression("GT", Arrays.asList(expr1, expr2));
      } else if ("<".equals(op)) {
        expr = new FunctionCallExpression("LT", Arrays.asList(expr1, expr2));
      } else if ("==".equals(op)) {
        expr = new FunctionCallExpression("EQ", Arrays.asList(expr1, expr2));
      } else if ("!=".equals(op)) {
        expr = new FunctionCallExpression("NE", Arrays.asList(expr1, expr2));
      } else if ("=~".equals(op)) {
        expr = new FunctionCallExpression("Match", Arrays.asList(expr1, expr2));
      } else if ("!~".equals(op)) {
        expr = new NotExpression(new FunctionCallExpression("Match", Arrays.asList(expr1, expr2)));
      }
    }

    public String getType() {
      return Expressions.TYPE_BOOLEAN;
    }

    public Expression simplify(Env env) {
      return this;
    }

    public Value evaluate(Env env, Object... args) {
      Value v = expr.evaluate(env, args);
      return convertValueToBooleanValue(v, false);
    }

  }

  public static class ListExpression extends TypedExpression {

    List<Expression> exprs;

    public ListExpression(String typename, String... tags) {
      super(typename, tags);
      this.exprs = new ArrayList<Expression>();
    }

    public ListExpression(String typename, List<Expression> exprs, String... tags) {
      super(typename, tags);
      this.exprs = new ArrayList<Expression>(exprs);
    }

    public void addAll(List<Expression> exprs) {
      if (exprs != null) {
        this.exprs.addAll(exprs);
      }
    }

    public void add(Expression expr) {
      this.exprs.add(expr);
    }

    public Value evaluate(Env env, Object... args) {
      List<Value> values = new ArrayList<Value>(exprs.size());
      for (Expression s:exprs) {
        values.add(s.evaluate(env, args));
      };
      return new PrimitiveValue<List<Value>>(typename, values);
    }
  }

  private static final boolean isArgTypesCompatible(Class[] paramTypes, Class[] targetParamTypes)
  {
    boolean compatible = true;
    if (targetParamTypes.length == paramTypes.length) {
      for (int i = 0; i < targetParamTypes.length; i++) {
        if (targetParamTypes[i].isPrimitive()) {
          compatible = false;
          if (paramTypes[i] != null) {
            try {
              Class<?> type = (Class<?>) paramTypes[i].getField("TYPE").get(null);
              if (type.equals(targetParamTypes[i])) { compatible = true; }
            } catch (NoSuchFieldException ex2) {
            } catch (IllegalAccessException ex2) {
            }
          }
          if (!compatible) break;
        } else {
          if (paramTypes[i] != null && !targetParamTypes[i].isAssignableFrom(paramTypes[i])) {
            compatible = false;
            break;
          }
        }
      }
    } else {
      compatible = false;
    }
    return compatible;
  }

  protected static final String NEWLINE = System.getProperty("line.separator");
  public static class FunctionCallExpression extends Expressions.TypedExpression {
    String function;
    List<Expression> params;

    public FunctionCallExpression(String function, List<Expression> params, String... tags) {
      super(TYPE_FUNCTION, tags);
      this.function = function;
      this.params = params;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder("");
      sb.append(function);
      sb.append("(");
      sb.append(StringUtils.join(params, ", "));
      sb.append(")");
      return sb.toString();
    }

    public Expression simplify(Env env)
    {
      boolean paramsAllHasValue = true;
      List<Expression> simplifiedParams = new ArrayList<Expression>(params.size());
      for (Expression param:params) {
        Expression simplified = param.simplify(env);
        simplifiedParams.add(simplified);
        if (!(simplified.hasValue())) {
          paramsAllHasValue = false;
        }
      }
      Expression res = new FunctionCallExpression(function, simplifiedParams);
      if (paramsAllHasValue) {
        return res.evaluate(env);
      } else {
        return res;
      }
    }

    public Value evaluate(Env env, Object... args) {
      Object funcValue = ValueFunctions.lookupFunctionObject(env, function);
      if (funcValue == null) {
        throw new RuntimeException("Unknown function " + function);
      }
      if (funcValue instanceof Value) {
        funcValue = ((Value) funcValue).evaluate(env, args).get();
      }
      if (funcValue instanceof ValueFunction) {
        ValueFunction f = (ValueFunction) funcValue;
        List<Value> evaled = new ArrayList<Value>();
        for (Expression param:params) {
          evaled.add(param.evaluate(env, args));
        }
        return f.apply(env, evaled);
      } else if (funcValue instanceof Collection) {
        List<Value> evaled = new ArrayList<Value>();
        for (Expression param:params) {
          evaled.add(param.evaluate(env, args));
        }
        Collection<ValueFunction> fs = (Collection<ValueFunction>) funcValue;
        for (ValueFunction f:fs) {
          if (f.checkArgs(evaled)) {
            return f.apply(env, evaled);
          }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Cannot find function matching args: " + function + NEWLINE);
        sb.append("Args are: " + StringUtils.join(evaled, ",") + NEWLINE);
        if (fs.size() > 0) {
          sb.append("Options are:\n" + StringUtils.join(fs, NEWLINE));
        } else {
          sb.append("No options");
        }
        throw new RuntimeException(sb.toString());
      } else if (funcValue instanceof Class) {
        Class c = (Class) funcValue;
        List<Value> evaled = new ArrayList<Value>();
        for (Expression param:params) {
          evaled.add(param.evaluate(env, args));
        }
        Class[] paramTypes = new Class[params.size()];
        Object[] objs = new Object[params.size()];
        boolean paramsNotNull = true;
        for (int i = 0; i < params.size(); i++) {
          Value v = evaled.get(i);
          if (v != null) {
            objs[i] = v.get();
            if (objs[i] != null) {
              paramTypes[i] = objs[i].getClass();
            } else {
              paramTypes[i] = null;
              paramsNotNull = false;
            }
          } else {
            objs[i] = null;
            paramTypes[i] = null;
            paramsNotNull = false;
            //throw new RuntimeException("Missing evaluated value for " + params.get(i));
          }
        }
        if (paramsNotNull) {
          Object obj = MetaClass.create(c).createInstance(objs);
          if (obj != null) {
            return new PrimitiveValue<Object>(function, obj);
          }
        }
        try {
          Constructor constructor = null;
          try {
            constructor = c.getConstructor(paramTypes);
          } catch (NoSuchMethodException ex) {
            Constructor[] constructors = c.getConstructors();
            for (Constructor cons:constructors) {
              Class[] consParamTypes = cons.getParameterTypes();
              boolean compatible = isArgTypesCompatible(paramTypes, consParamTypes);
              if (compatible) {
                constructor = cons;
                break;
              }
            }
            if (constructor == null) {
              throw new RuntimeException("Cannot instantiate " + c, ex);
            }
          }
          Object obj = constructor.newInstance(objs);
          return new PrimitiveValue<Object>(function, obj);
        } catch (InvocationTargetException ex) {
          throw new RuntimeException("Cannot instantiate " + c, ex);
        } catch (InstantiationException ex) {
          throw new RuntimeException("Cannot instantiate " + c, ex);
        } catch (IllegalAccessException ex) {
          throw new RuntimeException("Cannot instantiate " + c, ex);
        }
      } else {
        throw new UnsupportedOperationException("Unsupported function value " + funcValue);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FunctionCallExpression)) return false;

      FunctionCallExpression that = (FunctionCallExpression) o;

      if (function != null ? !function.equals(that.function) : that.function != null) return false;
      if (params != null ? !params.equals(that.params) : that.params != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = function != null ? function.hashCode() : 0;
      result = 31 * result + (params != null ? params.hashCode() : 0);
      return result;
    }
  }

  public static class MethodCallExpression extends Expressions.TypedExpression {
    String function;
    Expression object;
    List<Expression> params;

    public MethodCallExpression(String function, Expression object, List<Expression> params, String... tags) {
      super(TYPE_FUNCTION, tags);
      this.function = function;
      this.object = object;
      this.params = params;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder("");
      sb.append(object);
      sb.append(".");
      sb.append(function);
      sb.append("(");
      sb.append(StringUtils.join(params, ", "));
      sb.append(")");
      return sb.toString();
    }

    public Expression simplify(Env env)
    {
      boolean paramsAllHasValue = true;
      List<Expression> simplifiedParams = new ArrayList<Expression>(params.size());
      for (Expression param:params) {
        Expression simplified = param.simplify(env);
        simplifiedParams.add(simplified);
        if (!(simplified.hasValue())) {
          paramsAllHasValue = false;
        }
      }
      Expression simplifiedObject = object.simplify(env);
      Expression res = new MethodCallExpression(function, simplifiedObject, simplifiedParams);
      if (paramsAllHasValue && object.hasValue()) {
        return res.evaluate(env);
      } else {
        return res;
      }
    }

    public Value evaluate(Env env, Object... args) {
      Value evaledObj = object.evaluate(env, args);
      if (evaledObj == null || evaledObj.get() == null) return null;
      Object mainObj = evaledObj.get();
      Class c = mainObj.getClass();
      List<Value> evaled = new ArrayList<Value>();
      for (Expression param:params) {
        evaled.add(param.evaluate(env, args));
      }
      Class[] paramTypes = new Class[params.size()];
      Object[] objs = new Object[params.size()];
      for (int i = 0; i < params.size(); i++) {
        Value v = evaled.get(i);
        if (v != null) {
          objs[i] = v.get();
          if (objs[i] != null) {
            paramTypes[i] = objs[i].getClass();
          } else {
            paramTypes[i] = null;
          }
        } else {
          objs[i] = null;
          paramTypes[i] = null;
          //throw new RuntimeException("Missing evaluated value for " + params.get(i));
        }
      }
      Method method = null;
      try {
        method = c.getMethod(function, paramTypes);
      } catch (NoSuchMethodException ex) {
        Method[] methods = c.getMethods();
        for (Method m:methods) {
          if (m.getName().equals(function)) {
            Class[] mParamTypes = m.getParameterTypes();
            if (mParamTypes.length == paramTypes.length) {
              boolean compatible = isArgTypesCompatible(paramTypes, mParamTypes);
              if (compatible) {
                method = m;
                break;
              }
            }
          }
        }
        if (method == null) {
          throw new RuntimeException("Cannot find method " + function + " on object of class " + c, ex);
        }
      }
      try {
        Object res = method.invoke(mainObj, objs);
        return new PrimitiveValue<Object>(function, res);
      } catch (InvocationTargetException ex) {
        throw new RuntimeException("Cannot evaluate method " + function + " on object " + mainObj, ex);
      } catch (IllegalAccessException ex) {
        throw new RuntimeException("Cannot evaluate method " + function + " on object " + mainObj, ex);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MethodCallExpression)) return false;
      if (!super.equals(o)) return false;

      MethodCallExpression that = (MethodCallExpression) o;

      if (function != null ? !function.equals(that.function) : that.function != null) return false;
      if (object != null ? !object.equals(that.object) : that.object != null) return false;
      if (params != null ? !params.equals(that.params) : that.params != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (function != null ? function.hashCode() : 0);
      result = 31 * result + (object != null ? object.hashCode() : 0);
      result = 31 * result + (params != null ? params.hashCode() : 0);
      return result;
    }
  }

  /**
  * Primitive value that is directly represented by a Java object of type T
  */
  public static class PrimitiveValue<T> extends SimpleValue<T> {
    public PrimitiveValue(String typename, T value, String... tags) {
      super(typename, value, tags);
    }
  }

  /**
  * A composite value with field names and values for each field
  */
  public static class CompositeValue extends SimpleCachedExpression<Map<String,Expression>> implements Value<Map<String,Expression>>{
    public CompositeValue(String... tags) {
      super(TYPE_COMPOSITE, Generics.<String,Expression>newHashMap(), tags);
    }

    public CompositeValue(Map<String, Expression> m, boolean isEvaluated, String... tags) {
      super(TYPE_COMPOSITE, m, tags);
      if (isEvaluated) {
        evaluated = this;
        disableCaching = !checkValue();
      }
    }

    private boolean checkValue() {
      boolean ok = true;
      for (String key:value.keySet()) {
        Expression expr = value.get(key);
        if (expr != null && !expr.hasValue()) {
          ok = false;
        }
      }
      return ok;
    }

    public Set<String> getAttributes() {
      return value.keySet();
    }

    public Expression getExpression(String attr) {
      return value.get(attr);
    }

    public Value getValue(String attr) {
      Expression expr = value.get(attr);
      if (expr == null) return null;
      if (expr instanceof Value) {
        return (Value) expr;
      }
      throw new UnsupportedOperationException("Expression was not evaluated....");
    }

    public <T> T get(String attr) {
      Expression expr = value.get(attr);
      if (expr == null) return null;
      if (expr instanceof Value) {
        return ((Value<T>) expr).get();
      }
      throw new UnsupportedOperationException("Expression was not evaluated....");
    }

    public void set(String attr, Object obj) {
      if (obj instanceof Expression) {
        value.put(attr, (Expression) obj);
      } else {
        value.put(attr, createValue(null, obj));
      }
      evaluated = null;
    }

    private static Value attemptTypeConversion(CompositeValue cv, Env env, Object... args) {
      Expression typeFieldExpr = cv.value.get("type");
      if (typeFieldExpr != null) {
        // Automatically convert types ....
        Value typeValue = typeFieldExpr.evaluate(env, args);
        if (typeFieldExpr instanceof VarExpression) {
          VarExpression varExpr = (VarExpression) typeFieldExpr;
          // The name of the variable is used to indicate the "type" of object
          String typeName = varExpr.get();
          if (typeValue != null) {
            // Check if variable points to a class
            // If so, then try to instantiate a new instance of the class
            if (TYPE_CLASS.equals(typeValue.getType())) {
              // Variable maps to a java class
              Class c = (Class) typeValue.get();
              try {
                Object obj = c.newInstance();
                // for any field other than the "type", set the value of the field
                //   of the created object to the specified value
                for (String s:cv.value.keySet()) {
                  if (!"type".equals(s)) {
                    Value v = cv.value.get(s).evaluate(env, args);
                    try {
                      Field f = c.getField(s);
                      f.set(obj, v.get());
                    } catch (NoSuchFieldException ex){
                      throw new RuntimeException("Unknown field " + s + " for type " + typeName, ex);
                    }
                  }
                }
                return new PrimitiveValue<Object>(typeName, obj);
              } catch (InstantiationException ex) {
                throw new RuntimeException("Cannot instantiate " + c, ex);
              } catch (IllegalAccessException ex) {
                throw new RuntimeException("Cannot instantiate " + c, ex);
              }
            } else if (typeValue.get() != null){
              // When evaluated, variable does not explicitly map to "CLASS"
              // See if we can convert this CompositeValue into appropriate object
              // by calling "create(CompositeValue cv)"
              Class c = typeValue.get().getClass();
              try {
                Method m = c.getMethod("create", CompositeValue.class);
                CompositeValue evaluatedCv = cv.evaluateNoTypeConversion(env, args);
                try {
                  return new PrimitiveValue<Object>(typeName, m.invoke(typeValue.get(), evaluatedCv));
                } catch (InvocationTargetException ex) {
                  throw new RuntimeException("Cannot instantiate " + c, ex);
                } catch (IllegalAccessException ex) {
                  throw new RuntimeException("Cannot instantiate " + c, ex);
                }
              } catch (NoSuchMethodException ex) {}
            }
          }
        } else if (typeValue != null && typeValue.get() instanceof String) {
          String typeName = (String) typeValue.get();
          // Predefined types:
          Expression valueField = cv.value.get("value");
          Value value = valueField.evaluate(env, args);
          if (TYPE_ANNOTATION_KEY.equals(typeName)) {
            String className = (String) value.get();
            try {
              return new PrimitiveValue<Class>(TYPE_ANNOTATION_KEY, Class.forName(className));
            } catch (ClassNotFoundException ex) {
              throw new RuntimeException("Unknown class " + className, ex);
            }
          } else if (TYPE_CLASS.equals(typeName)) {
            String className = (String) value.get();
            try {
              return new PrimitiveValue<Class>(TYPE_CLASS, Class.forName(className));
            } catch (ClassNotFoundException ex) {
              throw new RuntimeException("Unknown class " + className, ex);
            }
          } else if (TYPE_STRING.equals(typeName)) {
            return new PrimitiveValue<String>(TYPE_STRING, (String) value.get());
          } else if (TYPE_REGEX.equals(typeName)) {
            return new RegexValue((String) value.get());
            /* } else if (TYPE_TOKEN_REGEX.equals(type)) {
       return new PrimitiveValue<TokenSequencePattern>(TYPE_TOKEN_REGEX, (TokenSequencePattern) value.get()); */
          } else if (TYPE_NUMBER.equals(typeName)) {
            if (value.get() instanceof Number) {
              return new PrimitiveValue<Number>(TYPE_NUMBER, (Number) value.get());
            } else if (value.get() instanceof String){
              String str = (String) value.get();
              if (str.contains(".")) {
                return new PrimitiveValue<Number>(TYPE_NUMBER, Double.valueOf(str));
              } else {
                return new PrimitiveValue<Number>(TYPE_NUMBER, Long.valueOf(str));
              }
            } else {
              throw new IllegalArgumentException("Invalid value " + value + " for type " + typeName);
            }
          } else {
            // TODO: support other types
            return new PrimitiveValue(typeName, value.get());
            //throw new UnsupportedOperationException("Cannot convert type " + typeName);
          }
        }
      }
      return null;
    }

    public CompositeValue simplifyNoTypeConversion(Env env, Object... args) {
      Map<String, Expression> m = value;
      Map<String, Expression> res = Generics.newHashMap (m.size());
      for (String s:m.keySet()) {
        res.put(s, m.get(s).simplify(env));
      }
      return new CompositeValue(res, true);
    }

    private CompositeValue evaluateNoTypeConversion(Env env, Object... args) {
      Map<String, Expression> m = value;
      Map<String, Expression> res = Generics.newHashMap (m.size());
      for (String s:m.keySet()) {
        res.put(s, m.get(s).evaluate(env, args));
      }
      return new CompositeValue(res, true);
    }

    public Value doEvaluation(Env env, Object... args) {
      Value v = attemptTypeConversion(this, env, args);
      if (v != null) return v;
      Map<String, Expression> m = value;
      Map<String, Expression> res = Generics.newHashMap (m.size());
      for (String s:m.keySet()) {
        res.put(s, m.get(s).evaluate(env, args));
      }
      disableCaching = !checkValue();
      return new CompositeValue(res, true);
    }
  }
}
