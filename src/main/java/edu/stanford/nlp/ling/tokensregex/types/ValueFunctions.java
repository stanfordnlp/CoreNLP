package edu.stanford.nlp.ling.tokensregex.types;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ValueFunctions supported by tokensregex.
 *
 * @author Angel Chang
 */
public class ValueFunctions {

  private ValueFunctions() {} // static methods

  protected static Object lookupFunctionObject(Env env, String name) {
    if (env != null) {
      Object obj = env.get(name);
      if (obj != null) {
        return obj;
      }
    }
    return registeredFunctions.get(name);
  }

  public abstract static class NamedValueFunction implements ValueFunction {
    protected String name;
    protected String signature;

    public NamedValueFunction(String name) {
      this.name = name;
    }

    @Override
    public String getDescription() { return ""; }

    public String getParamDesc() { return "..."; }

    protected static String getParamDesc(String type, int nargs) {
      if (nargs < 0) {
        return type + "...";
      } else if (nargs <= 3) {
        String[] tmp = new String[nargs];
        Arrays.fill(tmp, type);
        return StringUtils.join(tmp, ",");
      } else return type + '[' + nargs + ']';
    }

    protected static String getTypeName(Class c) {
      return c.getCanonicalName();
    }

    public String toString() {
      if (signature == null) {
        signature = name + '(' + getParamDesc() + ')';
      }
      return signature;
    }

  } // end static class NamedValueFunction


  public static class ParamInfo {
    public final String name;
    public final String typeName;
    public final Class className;
    public final boolean nullable;

    public ParamInfo(String name, String typeName, Class className, boolean nullable) {
      this.name = name;
      this.typeName = typeName;
      this.className = className;
      this.nullable = nullable;
    }
  }

  public abstract static class TypeCheckedFunction extends NamedValueFunction {
    List<ParamInfo> paramInfos;
    int nargs;

    public TypeCheckedFunction(String name, List<ParamInfo> paramInfos) {
      super(name);
      this.paramInfos = paramInfos;
      nargs = (paramInfos != null)? paramInfos.size():0;
    }

    public TypeCheckedFunction(String name, ParamInfo... paramInfos) {
      super(name);
      this.paramInfos = Arrays.asList(paramInfos);
      nargs = paramInfos.length;
    }

    @Override
    public String getParamDesc() {
      StringBuilder sb = new StringBuilder();
      for (ParamInfo p:paramInfos) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        if (p.typeName != null) {
          sb.append(p.typeName);
        } else {
          sb.append(getTypeName(p.className));
        }
      }
      return sb.toString();
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != nargs) {
        return false;
      }
      for (int i = 0; i < in.size(); i++) {
        Value v = in.get(i);
        ParamInfo p = paramInfos.get(i);
        if (v == null) {
          if (!p.nullable) {
            return false;
          }
        } else {
          if (p.typeName != null && !p.typeName.equals(v.getType())) {
            return false;
          }
          if (v.get() != null) {
            if (p.className != null && !(p.className.isAssignableFrom(v.get().getClass()))) {
              return false;
            }
          }
        }
      }
      return true;
    }
  }

  public abstract static class NumericFunction extends NamedValueFunction {
    protected String resultTypeName = Expressions.TYPE_NUMBER;
    protected int nargs = 2;

    protected NumericFunction(String name, int nargs) {
      super(name);
      this.nargs = nargs;
    }

    protected NumericFunction(String name, int nargs, String resultTypeName) {
      super(name);
      this.resultTypeName = resultTypeName;
      this.nargs = nargs;
    }

    @Override
    public String getParamDesc() {
      return getParamDesc(Expressions.TYPE_NUMBER, nargs);
    }

    public abstract Number compute(Number...ns);

    @Override
    public boolean checkArgs(List<Value> in) {
      if (nargs > 0 && in.size() != nargs) {
        return false;
      }
      for (Value v : in) {
        if (v == null || !(v.get() instanceof Number)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (nargs > 0 && in.size() != nargs) {
        throw new IllegalArgumentException(nargs + " arguments expected, got " + in.size());
      }
      Number[] numbers = new Number[in.size()];
      for (int i = 0; i < in.size(); i++) {
        numbers[i] = (Number) in.get(i).get();
      }
      Number res = compute(numbers);
      return new Expressions.PrimitiveValue(resultTypeName, res);
    }
  }

  public static final ValueFunction ADD_FUNCTION = new NumericFunction("ADD", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        return in[0].longValue() + in[1].longValue();
      } else {
        return in[0].doubleValue() + in[1].doubleValue();
      }
    }
  };

  public static final ValueFunction SUBTRACT_FUNCTION = new NumericFunction("SUBTRACT", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        return in[0].longValue() - in[1].longValue();
      } else {
        return in[0].doubleValue() - in[1].doubleValue();
      }
    }
  };

  public static final ValueFunction MULTIPLY_FUNCTION = new NumericFunction("MULTIPLY", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        return in[0].longValue() * in[1].longValue();
      } else {
        return in[0].doubleValue() * in[1].doubleValue();
      }
    }
  };

  public static final ValueFunction DIVIDE_FUNCTION = new NumericFunction("DIVIDE", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        if ( in[0].longValue() % in[1].longValue() == 0)
          return in[0].longValue() / in[1].longValue();
        else return in[0].doubleValue() / in[1].doubleValue();
      } else {
        return in[0].doubleValue() / in[1].doubleValue();
      }
    }
  };

  public static final ValueFunction MOD_FUNCTION = new NumericFunction("MOD", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        return in[0].longValue() % in[1].longValue();
      } else {
        return in[0].doubleValue() % in[1].doubleValue();
      }
    }
  };

  public static final ValueFunction MAX_FUNCTION = new NumericFunction("MAX", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        return Math.max(in[0].longValue(), in[1].longValue());
      } else {
        return Math.max(in[0].doubleValue(), in[1].doubleValue());
      }
    }
  };

  public static final ValueFunction MIN_FUNCTION = new NumericFunction("MIN", 2) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0]) && isInteger(in[1])) {
        return Math.min(in[0].longValue(), in[1].longValue());
      } else {
        return Math.min(in[0].doubleValue(), in[1].doubleValue());
      }
    }
  };

  public static final ValueFunction POW_FUNCTION = new NumericFunction("POW", 2) {
    @Override
    public Number compute(Number... in) {
      return Math.pow(in[0].doubleValue(), in[1].doubleValue());
    }
  };

  public static final ValueFunction NEGATE_FUNCTION = new NumericFunction("NEGATE", 1) {
    @Override
    public Number compute(Number... in) {
      if (isInteger(in[0])) {
        return - in[0].longValue();
      } else {
        return - in[0].doubleValue();
      }
    }
  };

  public abstract static class BooleanFunction extends NamedValueFunction {
    protected String resultTypeName = Expressions.TYPE_BOOLEAN;
    protected int nargs = 2;

    protected BooleanFunction(String name, int nargs) {
      super(name);
      this.nargs = nargs;
    }

    protected BooleanFunction(String name, int nargs, String resultTypeName) {
      super(name);
      this.resultTypeName = resultTypeName;
      this.nargs = nargs;
    }

    public abstract Boolean compute(Boolean...ns);

    @Override
    public String getParamDesc() {
      return getParamDesc(Expressions.TYPE_BOOLEAN, nargs);
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (nargs > 0 && in.size() != nargs) {
        return false;
      }
      for (Value v : in) {
        if (v == null || !(v.get() instanceof Boolean)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (nargs > 0 && in.size() != nargs) {
        throw new IllegalArgumentException(nargs + " arguments expected, got " + in.size());
      }
      Boolean[] bools = new Boolean[in.size()];
      for (int i = 0; i < in.size(); i++) {
        bools[i] = (Boolean) in.get(i).get();
      }
      Boolean res = compute(bools);
      return new Expressions.PrimitiveValue(resultTypeName, res);
    }
  }

  public static final ValueFunction AND_FUNCTION = new BooleanFunction("AND", -1) {
    @Override
    public Boolean compute(Boolean... in) {
      for (Boolean b:in) {
        if (!b) return false;
      }
      return true;
    }
  };

  public static final ValueFunction OR_FUNCTION = new BooleanFunction("OR", -1) {
    @Override
    public Boolean compute(Boolean... in) {
      for (Boolean b:in) {
        if (b) return true;
      }
      return false;
    }
  };

  public static final ValueFunction NOT_FUNCTION = new BooleanFunction("NOT", 1) {
    @Override
    public Boolean compute(Boolean... in) {
      Boolean res = !in[0];
      return res;
    }
  };

  private static String join(Object[] args, String glue) {
    String res = null;
    if (args.length == 1) {
      // Only one element - check if it is a list or array and do join on that
      if (args[0] instanceof Iterable) {
        res = StringUtils.join((Iterable) args[0], glue);
      } else {
        res = StringUtils.join(args, glue);
      }
    } else {
      res = StringUtils.join(args, glue);
    }
    return res;
  }

  public abstract static class StringFunction extends NamedValueFunction {
    protected String resultTypeName = Expressions.TYPE_STRING;
    protected int nargs = 2;

    protected StringFunction(String name, int nargs) {
      super(name);
      this.nargs = nargs;
    }

    protected StringFunction(String name, int nargs, String resultTypeName) {
      super(name);
      this.resultTypeName = resultTypeName;
      this.nargs = nargs;
    }

    public abstract String compute(String... strs);

    @Override
    public String getParamDesc() {
      return getParamDesc(Expressions.TYPE_STRING, nargs);
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (nargs > 0 && in.size() != nargs) {
        return false;
      }
      for (Value v : in) {
        if (v == null /*|| !(v.get() instanceof String) */) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (nargs > 0 && in.size() != nargs) {
        throw new IllegalArgumentException(nargs + " arguments expected, got " + in.size());
      }
      String[] strs = new String[in.size()];
      for (int i = 0; i < in.size(); i++) {
        if (in.get(i).get() instanceof String) {
          strs[i] = (String) in.get(i).get();
        } else if (in.get(i).get() != null) {
          strs[i] = in.get(i).get().toString();
        } else {
          strs[i] = null;
        }
      }
      String res = compute(strs);
      return new Expressions.PrimitiveValue(resultTypeName, res);
    }
  }

  public static final ValueFunction CONCAT_FUNCTION = new StringFunction("CONCAT", -1) {
    @Override
    public String compute(String... in) {
      return join(in, "");
    }
  };

  public static final ValueFunction UPPERCASE_FUNCTION = new StringFunction("UPPERCASE", 1) {
    @Override
    public String compute(String... in) {
      return in[0].toUpperCase();
    }
  };

  public static final ValueFunction LOWERCASE_FUNCTION = new StringFunction("LOWERCASE", 1) {
    @Override
    public String compute(String... in) {
      return in[0].toLowerCase();
    }
  };

  public static final ValueFunction PRINT_FUNCTION = new NamedValueFunction("PRINT") {
    @Override
    public String getParamDesc() {
      return "...";
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() < 1) {
        return false;
      }
      if (in.size() > 1 && (in.get(0) == null || !(in.get(0).get() instanceof String))) {
        return false;
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.size() > 1) {
        String format = (String) in.get(0).get();
        Object[] args = new Object[in.size()-1];
        for (int i = 1; i < in.size(); i++) {
          args[i-1] = in.get(i).get();
        }
        String res = String.format(format,  args);
        System.out.print(res);
      } else {
        System.out.print(in.get(0));
      }
      return null;
    }
  };

  public static final ValueFunction FORMAT_FUNCTION = new NamedValueFunction("FORMAT") {
    @Override
    public String getParamDesc() {
      return Expressions.TYPE_STRING + ",...";
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() < 1) {
        return false;
      }
      if (in.get(0) == null || !(in.get(0).get() instanceof String)) {
        return false;
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      String format = (String) in.get(0).get();
      Object[] args = new Object[in.size()-1];
      for (int i = 1; i < in.size(); i++) {
        args[i-1] = in.get(i).get();
      }
      String res = String.format(format,  args);
      return new Expressions.PrimitiveValue(Expressions.TYPE_STRING, res);
    }
  };

  public static final ValueFunction JOIN_FUNCTION = new NamedValueFunction("JOIN") {
    @Override
    public String getParamDesc() {
      return "String glue,...";
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() < 1) {
        return false;
      }
      if (in.get(0) == null || !(in.get(0).get() instanceof String)) {
        return false;
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      String glue = (String) in.get(0).get();
      Object[] args = new Object[in.size()-1];
      for (int i = 1; i < in.size(); i++) {
        args[i-1] = in.get(i).get();
      }
      String res = join(args, glue);
      return new Expressions.PrimitiveValue(Expressions.TYPE_STRING, res);
    }
  };

  public static final ValueFunction CREATE_REGEX_FUNCTION = new NamedValueFunction("CREATE_REGEX") {
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() < 1) {
        return false;
      }
      if (in.get(0) == null || !(in.get(0).get() instanceof List)) {
        return false;
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      List list = (List) in.get(0).get();
      String[] args = new String[list.size()];
      for (int i = 0; i < list.size(); i++) {
        args[i] = list.get(i).toString();
      }
      MultiWordStringMatcher matcher = new MultiWordStringMatcher("EXCTWS");
      String regex = matcher.getRegex(args);
      return new Expressions.PrimitiveValue(Expressions.TYPE_REGEX, regex);
    }
  };

  private static final ParamInfo PARAM_INFO_VALUE_FUNCTION = new ParamInfo("FUNCTION", Expressions.TYPE_FUNCTION, ValueFunction.class, false);
  private static final ParamInfo PARAM_INFO_LIST = new ParamInfo("LIST", null, List.class, true);
  public static final ValueFunction MAP_VALUES_FUNCTION =
          new TypeCheckedFunction("MAP_VALUES", PARAM_INFO_LIST, PARAM_INFO_VALUE_FUNCTION) {
            // First argument is list of elements to apply function to
            // Second argument is function to apply
            @Override
            public Value apply(Env env, List<Value> in) {
              if (in.get(0) == null) return null;
              List list = (List) in.get(0).get();
              ValueFunction func = (ValueFunction) in.get(1).get();
              List<Value> res = new ArrayList<>(list.size());
              for (Object elem:list) {
                List<Value> args = new ArrayList<>(1);
                args.add(Expressions.createValue(Expressions.TYPE_LIST, elem));
                res.add(func.apply(env, args));
              }
              return new Expressions.PrimitiveValue<>(Expressions.TYPE_LIST, res);
            }
          };
  private static final ParamInfo PARAM_INFO_FUNCTION = new ParamInfo("FUNCTION", Expressions.TYPE_FUNCTION, Function.class, false);
  public static final ValueFunction MAP_FUNCTION =
          new TypeCheckedFunction("MAP", PARAM_INFO_LIST, PARAM_INFO_FUNCTION) {
            // First argument is list of elements to apply function to
            // Second argument is function to apply
            @Override
            public Value apply(Env env, List<Value> in) {
              if (in.get(0) == null) return null;
              List list = (List) in.get(0).get();
              Function func = (Function) in.get(1).get();
              List<Object> res = new ArrayList<>(list.size());
              for (Object elem:list) {
                res.add(func.apply(elem));
              }
              return new Expressions.PrimitiveValue<>(null, res);
            }
          };


  private static final ParamInfo PARAM_INFO_TOKEN_REGEX = new ParamInfo("TOKEN_REGEX", Expressions.TYPE_TOKEN_REGEX, TokenSequencePattern.class, false);
  private static final ParamInfo PARAM_INFO_TOKEN_LIST = new ParamInfo("TOKEN_LIST", null, List.class, true);
  private static final ParamInfo PARAM_INFO_TOKEN_LIST_REPLACE = new ParamInfo("TOKEN_LIST_REPLACEMENT", null, List.class, true);

  public static final ValueFunction TOKENS_MATCH_FUNCTION =
          new TypeCheckedFunction("TOKENS_MATCH", PARAM_INFO_TOKEN_LIST, PARAM_INFO_TOKEN_REGEX) {
            // First argument is list of tokens to match
            // Second argument is pattern to match
            @Override
            public Value apply(Env env, List<Value> in) {
              if (in.get(0) == null || in.get(0).get() == null) return Expressions.FALSE;
              List<CoreMap> cms = (List<CoreMap>) in.get(0).get();
              TokenSequencePattern pattern = (TokenSequencePattern) in.get(1).get();
              TokenSequenceMatcher matcher = pattern.getMatcher(cms);
              boolean matches = matcher.matches();
              return (matches)? Expressions.TRUE: Expressions.FALSE;
            }
          };

  public static final ValueFunction TOKENS_REPLACE_FUNCTION =
          new TypeCheckedFunction("TOKENS_REPLACE",
                  PARAM_INFO_TOKEN_LIST, PARAM_INFO_TOKEN_REGEX, PARAM_INFO_TOKEN_LIST_REPLACE) {
            // First argument is list of tokens to match
            // Second argument is pattern to match
            // Third argument is replacement tokens
            @Override
            public Value apply(Env env, List<Value> in) {
              if (in.get(0) == null || in.get(0).get() == null) return Expressions.FALSE;
              List<CoreMap> cms = (List<CoreMap>) in.get(0).get();
              List<CoreMap> replacement = (List<CoreMap>) in.get(2).get();
              TokenSequencePattern pattern = (TokenSequencePattern) in.get(1).get();
              TokenSequenceMatcher matcher = pattern.getMatcher(cms);
              List<CoreMap> replaced = matcher.replaceAll(replacement);
              return new Expressions.PrimitiveValue(Expressions.TYPE_TOKENS, replaced);
            }
          };

  private static final ParamInfo PARAM_INFO_STRING_REGEX = new ParamInfo("REGEX", Expressions.TYPE_REGEX, null, false);
  private static final ParamInfo PARAM_INFO_STRING = new ParamInfo("STRING", null, String.class, true);
  private static final ParamInfo PARAM_INFO_STRING_REPLACE = new ParamInfo("STRING_REPLACEMENT", null, String.class, true);

  public static final ValueFunction STRING_MATCH_FUNCTION =
          new TypeCheckedFunction("STRING_MATCH", PARAM_INFO_STRING, PARAM_INFO_STRING_REGEX) {
            // First argument is string to match
            // Second argument is pattern to match
            @Override
            public Value apply(Env env, List<Value> in) {
              if (in.get(0) == null || in.get(0).get() == null) return Expressions.FALSE;
              String str = (String) in.get(0).get();
              String regex = (String) in.get(1).get();
              Pattern pattern = env.getStringPattern(regex);
              Matcher matcher = pattern.matcher(str);
              boolean matches = matcher.matches();
              return (matches)? Expressions.TRUE: Expressions.FALSE;
            }
          };

  public static final ValueFunction STRING_REPLACE_FUNCTION =
          new TypeCheckedFunction("STRING_REPLACE",
                  PARAM_INFO_STRING, PARAM_INFO_STRING_REGEX, PARAM_INFO_STRING_REPLACE) {
            // First argument is string to match
            // Second argument is pattern to match
            // Third argument is replacement string
            @Override
            public Value apply(Env env, List<Value> in) {
              if (in.get(0) == null || in.get(0).get() == null) return Expressions.FALSE;
              String str = (String) in.get(0).get();
              String replacement = (String) in.get(2).get();
              String regex = (String) in.get(1).get();
              Pattern pattern = env.getStringPattern(regex);
              Matcher matcher = pattern.matcher(str);
              String replaced = matcher.replaceAll(replacement);
              return new Expressions.PrimitiveValue(Expressions.TYPE_STRING, replaced);
            }
          };

  private static final CoreLabelTokenFactory CORELABEL_FACTORY = new CoreLabelTokenFactory();
  private static final ParamInfo PARAM_INFO_TOKEN = new ParamInfo("TOKEN", null, CoreMap.class, false);
  public static final ValueFunction TOKEN_STRING_SPLIT_FUNCTION =
          new TypeCheckedFunction("TOKEN_STRING_SPLIT",
                  PARAM_INFO_TOKEN, PARAM_INFO_STRING_REGEX,
                  new ParamInfo("INCLUDE_MATCHED", null, Boolean.class, false)) {
            // First argument is token to split
            // Second argument is pattern to split on
            @Override
            public Value apply(Env env, List<Value> in) {
              CoreMap cm = (CoreMap) in.get(0).get();
              String regex = (String) in.get(1).get();
              Boolean includeMatchedAsTokens = (Boolean) in.get(2).get();
              Pattern pattern = env.getStringPattern(regex);
              List<CoreLabel> res =
                      ChunkAnnotationUtils.splitCoreMap(pattern, includeMatchedAsTokens, cm, CORELABEL_FACTORY);
              return new Expressions.PrimitiveValue(Expressions.TYPE_TOKENS, res);
            }
          };

  public static boolean isInteger(Number n) {
    return (n instanceof Long || n instanceof Integer || n instanceof Short);
  }

  public static final NumericComparator NUMBER_COMPARATOR = new NumericComparator();

  public static class NumericComparator implements Comparator<Number> {
    @Override
    public int compare(Number o1, Number o2) {
      if (isInteger(o1) && isInteger(o2)) {
        return Long.compare(o1.longValue(), o2.longValue());
      } else {
        return Double.compare(o1.doubleValue(),o2.doubleValue());
      }
    }
  }

  public static class ComparableComparator<T extends Comparable<T>> implements Comparator<T> {
    @Override
    public int compare(T o1, T o2) {
      return o1.compareTo(o2);
    }
  }

  public enum CompareType { GT, LT, GE, LE, EQ, NE }

  public static class CompareFunction<T> extends NamedValueFunction {
    Comparator<T> comparator;
    CompareType compType;
    Class clazz;

    public CompareFunction(String name, Comparator<T> comparator, CompareType compType, Class clazz) {
      super(name);
      this.comparator = comparator;
      this.compType = compType;
      this.clazz = clazz;
    }

    @Override
    public String getParamDesc() {
      return "(" + getTypeName(clazz) + "," + getTypeName(clazz) + ")";
    }

    public Boolean compare(T o1, T o2) {
      int res = comparator.compare(o1,o2);
      switch (compType) {
        case GT: return res > 0;
        case LT: return res < 0;
        case GE: return res >= 0;
        case LE: return res <= 0;
        case EQ: return res == 0;
        case NE: return res != 0;
        default: throw new UnsupportedOperationException("Unknown compType: " + compType);
      }
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2) {
        return false;
      }
      if (clazz != null) {
        if (in.get(0) == null || in.get(0).get() == null || !(clazz.isAssignableFrom(in.get(0).get().getClass()))) {
          return false;
        }
        if (in.get(1) == null || in.get(1).get() == null || !(clazz.isAssignableFrom(in.get(1).get().getClass()))) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.size() != 2) {
        throw new IllegalArgumentException("2 arguments expected, got " + in.size());
      }
      if (in.get(0) == null || in.get(1) == null || in.get(0).get() == null || in.get(1).get() == null) {
        return null; // Can't compare...
      }
      Boolean res = compare((T) in.get(0).get(), (T) in.get(1).get());
      return (res)? Expressions.TRUE: Expressions.FALSE;
    }
  }

  public static final ValueFunction NOT_EQUALS_FUNCTION = new NamedValueFunction("EQUALS") {
    @Override
    public String getParamDesc() {
      return "Object,Object";
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      return in.size() == 2;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.size() != 2) {
        throw new IllegalArgumentException("2 arguments expected, got " + in.size());
      }
      boolean res = false;
      if (in.get(0) == null || in.get(1) == null) {
        res = (in.get(0) == in.get(1));
      } else if (in.get(0).get() == null || in.get(1).get() == null) {
        res = (in.get(0).get() == in.get(1).get());
      } else {
        res = in.get(0).get().equals(in.get(1).get());
      }
      return (res)? Expressions.FALSE: Expressions.TRUE;
    }
  };


  public static final ValueFunction EQUALS_FUNCTION = new NamedValueFunction("EQUALS") {
    @Override
    public String getParamDesc() {
      return "Object,Object";
    }

    @Override
    public boolean checkArgs(List<Value> in) {
      return in.size() == 2;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.size() != 2) {
        throw new IllegalArgumentException("2 arguments expected, got " + in.size());
      }
      boolean res = false;
      if (in.get(0) == null || in.get(1) == null) {
        res = (in.get(0) == in.get(1));
      } else if (in.get(0).get() == null || in.get(1).get() == null) {
        res = (in.get(0).get() == in.get(1).get());
      } else {
        res = in.get(0).get().equals(in.get(1).get());
      }
      return (res)? Expressions.TRUE: Expressions.FALSE;
    }
  };

  public static final ValueFunction ANNOTATION_FUNCTION = new NamedValueFunction("ANNOTATION_VALUE") {
    @Override
    public String getParamDesc() {
      return "CoreMap coremap,String fieldName|Class field,[Object value]";
    }

    // First argument is what (CoreMap) to get annotation for
    // Second argument is field (Class or String) to get annotation for
    // Third argument (optional) is annotation value to set
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null ||
              (!(in.get(0).get() instanceof CoreMap) && !(in.get(0).get() instanceof List))) {
        return false;
      }
      if (in.get(1) == null ||
              (!(in.get(1).get() instanceof Class) && !(in.get(1).get() instanceof String))) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      Value cmv = in.get(0);
      Object field = in.get(1).get();
      Class annotationFieldClass = null;
      if (field instanceof String)  {
        annotationFieldClass = EnvLookup.lookupAnnotationKey(env, (String) field);
        if (annotationFieldClass == null) {
          throw new IllegalArgumentException("Cannot get annotation field " + field);
        }
      } else if (field instanceof Class) {
        annotationFieldClass = (Class)  field;
      } else {
        throw new IllegalArgumentException("Type mismatch on arg1: Cannot apply " + this + " to " + in);
      }
      if (cmv.get() instanceof CoreMap) {
        CoreMap cm = (CoreMap) cmv.get();
        if (in.size() >= 3) {
          Value v = in.get(2);
          Object annotationObject = (v != null)? v.get():null;
          cm.set(annotationFieldClass, annotationObject);
        }
        Object obj = cm.get(annotationFieldClass);
        return Expressions.createValue(annotationFieldClass.getName(), obj);
      } else if (cmv.get() instanceof List) {
        List<CoreMap> cmList = (List<CoreMap>) cmv.get();
        if (in.size() >= 3) {
          Value v = in.get(2);
          Object annotationObject = (v != null)? v.get():null;
          for (CoreMap cm:cmList) {
            cm.set(annotationFieldClass, annotationObject);
          }
        }
        List<Object> list = new ArrayList<>();
        Value res = new Expressions.PrimitiveValue(Expressions.TYPE_LIST, list);
        for (CoreMap cm:cmList) {
          list.add(cm.get(annotationFieldClass));
        }
        return res;
      } else {
        throw new IllegalArgumentException("Type mismatch on arg0: Cannot apply " + this + " to " + in);
      }
    }
  };

  public static final ValueFunction GET_ANNOTATION_TAG_FUNCTION = new NamedValueFunction("GET_ANNOTATION_TAG") {
    @Override
    public String getParamDesc() {
      return "CoreMap or List<CoreMap>,String tag";
    }

    // First argument is what (CoreMap or List<CoreMap>) to tag
    // Second argument is tag
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true; // Allow for NULL
      if (in.get(0) == null ||
              (!(in.get(0).get() instanceof CoreMap) && !(in.get(0).get() instanceof List))) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }

    public Value getTag(CoreMap cm, String tag)
    {
      Tags tags = cm.get(Tags.TagsAnnotation.class);
      return (tags != null)? tags.getTag(tag): null;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null) return null;
      Value v = in.get(0);
      Value res = null;
      String tag = (String) in.get(1).get();
      if (v.get() instanceof CoreMap) {
        res = getTag((CoreMap) v.get(), tag);
      } else if (v.get() instanceof List) {
        List<CoreMap> cmList = (List<CoreMap>) v.get();
        List<Value> list = new ArrayList<>();
        res = new Expressions.PrimitiveValue(Expressions.TYPE_LIST, list);
        for (CoreMap cm:cmList) {
          list.add(getTag(cm, tag));
        }
      } else {
        throw new IllegalArgumentException("Type mismatch on arg0: Cannot apply " + this + " to " + in);
      }
      return res;
    }
  };

  public static final ValueFunction SET_ANNOTATION_TAG_FUNCTION = new NamedValueFunction("SET_ANNOTATION_TAG") {
    @Override
    public String getParamDesc() {
      return "CoreMap or List<CoreMap>,String tag,[Object value]";
    }

    // First argument is what (CoreMap or List<CoreMap>) to tag
    // Second argument is tag
    // Third argument is tag value
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null ||
              (!(in.get(0).get() instanceof CoreMap) && !(in.get(0).get() instanceof List))) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }

    public void setTag(CoreMap cm, String tag, Value tagValue)
    {
      Tags tags = cm.get(Tags.TagsAnnotation.class);
      if (tags == null) {
        cm.set(Tags.TagsAnnotation.class, tags = new Tags());
      }
      tags.setTag(tag, tagValue);
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      Value v = in.get(0);
      String tag = (String) in.get(1).get();
      Value tagValue = (in.size() >= 3)? in.get(2):null;
      if (v.get() instanceof CoreMap) {
        setTag((CoreMap) v.get(), tag, tagValue);
      } else if (v.get() instanceof List) {
        List<CoreMap> cmList = (List<CoreMap>) v.get();
        for (CoreMap cm:cmList) {
          setTag(cm, tag, tagValue);
        }
      } else {
        throw new IllegalArgumentException("Type mismatch on arg0: Cannot apply " + this + " to " + in);
      }
      return v;
    }
  };

  public static final ValueFunction REMOVE_ANNOTATION_TAG_FUNCTION = new NamedValueFunction("REMOVE_ANNOTATION_TAG") {
    @Override
    public String getParamDesc() {
      return "CoreMap or List<CoreMap>,String tag";
    }

    // First argument is what (CoreMap) to tag
    // Second argument is tag
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2) {
        return false;
      }
      if (in.get(0) == null ||
              (!(in.get(0).get() instanceof CoreMap) && !(in.get(0).get() instanceof List))) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }
    public void removeTag(CoreMap cm, String tag)
    {
      Tags tags = cm.get(Tags.TagsAnnotation.class);
      if (tags != null) {
        tags.removeTag(tag);
      }
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      Value v = in.get(0);
      String tag = (String) in.get(1).get();
      if (v.get() instanceof CoreMap) {
        removeTag((CoreMap) v.get(), tag);
      } else if (v.get() instanceof List) {
        List<CoreMap> cmList = (List<CoreMap>) v.get();
        for (CoreMap cm:cmList) {
          removeTag(cm, tag);
        }
      } else {
        throw new IllegalArgumentException("Type mismatch on arg0: Cannot apply " + this + " to " + in);
      }
      return v;
    }
  };

  public static final ValueFunction TAGS_VALUE_FUNCTION = new NamedValueFunction("TAGS_VALUE") {
    @Override
    public String getParamDesc() {
      return getTypeName(Tags.class) + " tags,String field,[Object value]";
    }

    // First argument is tags object
    // Second argument is tag
    // Third argument (optional) is tag value
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null || !(in.get(0).get() instanceof Tags)) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      Value v = in.get(0);
      Tags tags = (Tags) v.get();
      String tag = (String) in.get(1).get();
      if (in.size() >= 3) {
        Value tagValue = in.get(2);
        tags.setTag(tag, tagValue);
      }
      return tags.getTag(tag);
    }
  };

  public static final ValueFunction SET_VALUE_TAG_FUNCTION = new NamedValueFunction("VALUE_TAG") {
    @Override
    public String getParamDesc() {
      return "Value,String tag,[Object value]";
    }

    // First argument is what to tag
    // Second argument is tag
    // Third argument is tag value
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      Value v = in.get(0);
      Tags tags = v.getTags();
      if (tags == null) {
        v.setTags(tags = new Tags());
      }
      String tag = (String) in.get(1).get();
      Value tagValue = (in.size() >= 3)? in.get(2):null;
      tags.setTag(tag, tagValue);
      return v;
    }
  };

  public static final ValueFunction GET_VALUE_TAG_FUNCTION = new NamedValueFunction("GET_VALUE_TAG") {
    @Override
    public String getParamDesc() {
      return "Value,String tag";
    }

    // First argument is what to tag
    // Second argument is tag
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2) {
        return false;
      }
      if (in.get(0) == null) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      Value v = in.get(0);
      Tags tags = v.getTags();
      String tag = (String) in.get(1).get();
      return (tags != null)? tags.getTag(tag):null;
    }
  };

  public static final ValueFunction REMOVE_VALUE_TAG_FUNCTION = new NamedValueFunction("REMOVE_VALUE_TAG") {
    @Override
    public String getParamDesc() {
      return "Value,String tag";
    }

    // First argument is what to tag
    // Second argument is tag
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2) {
        return false;
      }
      if (in.get(0) == null) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      Value v = in.get(0);
      Tags tags = v.getTags();
      if (tags == null) {
        v.setTags(tags = new Tags());
      }
      String tag = (String) in.get(1).get();
      tags.removeTag(tag);
      return v;
    }
  };

  public static final ValueFunction COMPOSITE_VALUE_FUNCTION = new NamedValueFunction("COMPOSITE_VALUE") {
    @Override
    public String getParamDesc() {
      return Expressions.TYPE_COMPOSITE + " obj,String field,[Object value]";
    }

    // First argument is composite value
    // Second argument is field to select
    // Third argument (optional) is value to set composite value field to
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true;   // Allow for null
      if (in.get(0) == null || !(in.get(0) instanceof Expressions.CompositeValue)) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null ) return null;   // Allow for null
      Expressions.CompositeValue v = (Expressions.CompositeValue) in.get(0);
      String fieldName = (String) in.get(1).get();
      if (in.size() >= 3) {
        v.set(fieldName, in.get(2));
      }
      return v.getValue(fieldName);
    }
  };

  public static final ValueFunction COMPOSITE_KEYS_FUNCTION = new NamedValueFunction("COMPOSITE_KEYS") {
    @Override
    public String getParamDesc() {
      return Expressions.TYPE_COMPOSITE;
    }

    // First argument is composite value
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 1) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true;   // Allow for null
      if (in.get(0) == null || !(in.get(0) instanceof Expressions.CompositeValue)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null ) return null;   // Allow for null
      Expressions.CompositeValue v = (Expressions.CompositeValue) in.get(0);
      List<String> res = new ArrayList<>(v.getAttributes());
      return Expressions.createValue(Expressions.TYPE_LIST, res);
    }
  };

  public static final ValueFunction OBJECT_FIELD_FUNCTION = new NamedValueFunction("OBJECT_FIELD") {
    @Override
    public String getParamDesc() {
      return "Object obj,String fieldName,[Object value]";
    }

    // First argument is object
    // Second argument is field to select
    // Third argument (optional) is value to assign to object field
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true;   // Allow for null
      if (in.get(0) == null || !(in.get(0) instanceof Object)) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof String)) {
        return false;
      }
      return true;
    }

    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null ) return null;   // Allow for null
      Value v = in.get(0);
      String fieldName = (String) in.get(1).get();
      try {
        Object obj = v.get();
        Field f = obj.getClass().getField(fieldName);
        if (in.size() >= 3) {
          Value fieldValue = in.get(2);
          if (fieldValue == null) {
            f.set(obj, null);
          } else if (f.getType().isAssignableFrom(Value.class)) {
            f.set(obj, fieldValue);
          } else {
            if (fieldValue.get() == null) {
              f.set(obj, null);
            } else if (f.getType().isAssignableFrom(List.class)) {
              if (fieldValue.get() instanceof List) {
                List list = (List) fieldValue.get();
                Type[] fieldParamTypes = ((ParameterizedType) f.getGenericType()).getActualTypeArguments();
                if (fieldParamTypes[0] instanceof Value) {
                  List<Value> list2 = new ArrayList<>(list.size());
                  for (Object elem:list) {
                    list2.add(Expressions.asValue(env, elem));
                  }
                  f.set(obj, list2);
                } else {
                  List list2 = new ArrayList(list.size());
                  for (Object elem:list) {
                    if (elem instanceof Value) {
                      list2.add(((Value) elem).get());
                    } else {
                      list2.add(elem);
                    }
                  }
                  f.set(obj, list2);
                }
              } else {
                f.set(obj, Arrays.asList(fieldValue.get()));
              }
            } else {
              f.set(obj, fieldValue.get());
            }
          }
        }
        return Expressions.createValue(null, f.get(obj));
      } catch (NoSuchFieldException | IllegalAccessException ex) {
        throw new RuntimeException("Cannot get field " + fieldName + " from " + v, ex);
      }
    }
  };

  public static final ValueFunction LIST_VALUE_FUNCTION = new NamedValueFunction("LIST_VALUE") {
    @Override
    public String getParamDesc() {
      return "List list,int index,[Object value]";
    }

    // First argument is List
    // Second argument is index of element to select
    // Third argument (optional) is value to assign list element
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true;   // Allow for null
      if (in.get(0) == null || !(in.get(0).get() instanceof List)) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof Integer)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null ) return null;   // Allow for null
      List list = (List) in.get(0).get();
      Integer index = (Integer) in.get(1).get();
      if (index < 0) {
        index = list.size() + index;
      }
      if (index >= list.size() || index < 0) {
        // index out of bounds
        return null;
      }
      if (in.size() >= 3) {
        Value fieldValue = in.get(2);
        if (fieldValue != null) {
          list.set(index, fieldValue.get());
        } else {
          list.set(index, null);
        }
      }
      Object obj = list.get(index);
      return Expressions.asValue(env, obj);
//      return Expressions.PrimitiveValue.create(null, obj);
    }
  };

  public static final ValueFunction MAP_VALUE_FUNCTION = new NamedValueFunction("MAP_VALUE") {
    @Override
    public String getParamDesc() {
      return "Map map,Object key,[Object value]";
    }

    // First argument is Map
    // Second argument is key of element to select
    // Third argument (optional) is value to assign to element
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 2 && in.size() != 3) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true;   // Allow for null
      if (in.get(0) == null || !(in.get(0).get() instanceof Map)) {
        return false;
      }
      if (in.get(1) == null || !(in.get(1).get() instanceof Object)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null ) return null;   // Allow for null
      Map map = (Map) in.get(0).get();
      Object key = in.get(1).get();
      if (in.size() >= 3) {
        Value fieldValue = in.get(2);
        if (fieldValue != null) {
          map.put(key, fieldValue.get());
        } else {
          map.remove(key);
        }
      }
      Object obj = map.get(key);
      if (in.size() == 2 && obj == null && key instanceof String) {
        Class annotationFieldClass = null;
        annotationFieldClass = EnvLookup.lookupAnnotationKey(env, (String) key);
        if (annotationFieldClass != null) {
          obj = map.get(annotationFieldClass);
        }
      }
      return Expressions.asValue(env, obj);
//      return Expressions.PrimitiveValue.create(null, obj);
    }
  };

  public static final ValueFunction MAP_KEYS_FUNCTION = new NamedValueFunction("MAP_KEYS") {
    @Override
    public String getParamDesc() {
      return "Map";
    }

    // First argument is Map
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() != 1) {
        return false;
      }
      if (in.get(0) == null || in.get(0).get() == null) return true;   // Allow for null
      if (in.get(0) == null || !(in.get(0).get() instanceof Map)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      if (in.get(0) == null || in.get(0).get() == null ) return null;   // Allow for null
      Map map = (Map) in.get(0).get();
      List<Object> res = new ArrayList<>(map.keySet());
      return Expressions.createValue(Expressions.TYPE_LIST, res);
    }
  };

  public static final ValueFunction AGGREGATE_FUNCTION = new NamedValueFunction("AGGREGATE") {
    @Override
    public String getParamDesc() {
      return "ValueFunction func,Object initialValue,...";
    }

    // First argument is function to apply
    // Second argument is initial value
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() < 2) {
        return false;
      }
      if (in.get(0) == null || !(in.get(0).get() instanceof ValueFunction)) {
        return false;
      }
      if (in.get(1) == null) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      ValueFunction func = (ValueFunction) in.get(0).get();
      Value res = in.get(1);
      List<Value> args = new ArrayList<>(2);
      for (int i = 2; i < in.size(); i++) {
        args.set(0, res);
        args.set(1, in.get(i));
        res = func.apply(env, args);
      }
      return res;
    }
  };

  public static final ValueFunction CALL_FUNCTION = new NamedValueFunction("CALL") {
    @Override
    public String getParamDesc() {
      return "ValueFunction func or String funcname,...";
    }

    // First argument is function to apply
    @Override
    public boolean checkArgs(List<Value> in) {
      if (in.size() < 1) {
        return false;
      }
      if (in.get(0) == null ||
              !(in.get(0).get() instanceof ValueFunction || in.get(0).get() instanceof String)) {
        return false;
      }
      return true;
    }
    @Override
    public Value apply(Env env, List<Value> in) {
      Value res;
      List<Value> args = new ArrayList<>(in.size() - 1);
      for (int i = 1; i < in.size(); i++) {
        args.add(in.get(i));
      }
      if (in.get(0).get() instanceof ValueFunction) {
        ValueFunction func = (ValueFunction) in.get(0).get();
        res = func.apply(env, args);
      } else if (in.get(0).get() instanceof String) {
        Expressions.FunctionCallExpression func =
                new Expressions.FunctionCallExpression((String) in.get(0).get(), args);
        res = func.evaluate(env);
      } else {
        throw new IllegalArgumentException("Type mismatch on arg0: Cannot apply " + this + " to " + in);
      }
      return res;
    }
  };

  static final CollectionValuedMap<String, ValueFunction> registeredFunctions =
          new CollectionValuedMap<>(
                  MapFactory.<String, Collection<ValueFunction>>linkedHashMapFactory(),
                  CollectionFactory.<ValueFunction>arrayListFactory(), false);
  static {
    registeredFunctions.add("Add", ADD_FUNCTION);
    registeredFunctions.add("Subtract", SUBTRACT_FUNCTION);
    registeredFunctions.add("Multiply", MULTIPLY_FUNCTION);
    registeredFunctions.add("Divide", DIVIDE_FUNCTION);
    registeredFunctions.add("Mod", MOD_FUNCTION);
    registeredFunctions.add("Min", MIN_FUNCTION);
    registeredFunctions.add("Max", MAX_FUNCTION);
    registeredFunctions.add("Pow", POW_FUNCTION);
    registeredFunctions.add("Negate", NEGATE_FUNCTION);

    registeredFunctions.add("And", AND_FUNCTION);
    registeredFunctions.add("Or", OR_FUNCTION);
    registeredFunctions.add("Not", NOT_FUNCTION);

    registeredFunctions.add("Format", FORMAT_FUNCTION);
    registeredFunctions.add("Concat", CONCAT_FUNCTION);
    registeredFunctions.add("Join", JOIN_FUNCTION);
    registeredFunctions.add("Lowercase", LOWERCASE_FUNCTION);
    registeredFunctions.add("Uppercase", UPPERCASE_FUNCTION);

    registeredFunctions.add("Map", MAP_VALUES_FUNCTION);
    registeredFunctions.add("Map", MAP_FUNCTION);

    registeredFunctions.add("Match", TOKENS_MATCH_FUNCTION);
    registeredFunctions.add("Match", STRING_MATCH_FUNCTION);
    registeredFunctions.add("Replace", TOKENS_REPLACE_FUNCTION);
    registeredFunctions.add("Replace", STRING_REPLACE_FUNCTION);

    registeredFunctions.add("GE", new CompareFunction<>("GE", NUMBER_COMPARATOR, CompareType.GE, Number.class) );
    registeredFunctions.add("GT", new CompareFunction<>("GT", NUMBER_COMPARATOR, CompareType.GT, Number.class) );
    registeredFunctions.add("LE", new CompareFunction<>("LE", NUMBER_COMPARATOR, CompareType.LE, Number.class) );
    registeredFunctions.add("LT", new CompareFunction<>("LT", NUMBER_COMPARATOR, CompareType.LT, Number.class) );
    registeredFunctions.add("EQ", new CompareFunction<>("EQ", NUMBER_COMPARATOR, CompareType.EQ, Number.class) );
    registeredFunctions.add("NE", new CompareFunction<>("NE", NUMBER_COMPARATOR, CompareType.NE, Number.class) );
    registeredFunctions.add("EQ", EQUALS_FUNCTION );
    registeredFunctions.add("NE", NOT_EQUALS_FUNCTION );

    registeredFunctions.add("VTag", SET_VALUE_TAG_FUNCTION);
    registeredFunctions.add("GetVTag", GET_VALUE_TAG_FUNCTION);
    registeredFunctions.add("RemoveVTag", REMOVE_VALUE_TAG_FUNCTION);

    registeredFunctions.add("Tag", SET_ANNOTATION_TAG_FUNCTION);
    registeredFunctions.add("GetTag", GET_ANNOTATION_TAG_FUNCTION);
    registeredFunctions.add("RemoveTag", REMOVE_ANNOTATION_TAG_FUNCTION);

    registeredFunctions.add("Split", TOKEN_STRING_SPLIT_FUNCTION);
    registeredFunctions.add("Annotate", ANNOTATION_FUNCTION);
    registeredFunctions.add("Aggregate", AGGREGATE_FUNCTION);

    registeredFunctions.add("Call", CALL_FUNCTION);

    registeredFunctions.add("CreateRegex", CREATE_REGEX_FUNCTION);

    registeredFunctions.add("Select", COMPOSITE_VALUE_FUNCTION);
    registeredFunctions.add("Select", MAP_VALUE_FUNCTION);
    registeredFunctions.add("Select", TAGS_VALUE_FUNCTION);
    registeredFunctions.add("Select", ANNOTATION_FUNCTION);
    registeredFunctions.add("Select", OBJECT_FIELD_FUNCTION);
    registeredFunctions.add("ListSelect", LIST_VALUE_FUNCTION);

    registeredFunctions.add("Keys", MAP_KEYS_FUNCTION);
    registeredFunctions.add("Keys", COMPOSITE_KEYS_FUNCTION);

    registeredFunctions.add("Set", TAGS_VALUE_FUNCTION);
    registeredFunctions.add("Set", COMPOSITE_VALUE_FUNCTION);
    registeredFunctions.add("Set", MAP_VALUE_FUNCTION);
    registeredFunctions.add("Set", ANNOTATION_FUNCTION);
    registeredFunctions.add("Set", OBJECT_FIELD_FUNCTION);
    registeredFunctions.add("Set", LIST_VALUE_FUNCTION);
    registeredFunctions.add("Get", TAGS_VALUE_FUNCTION);
    registeredFunctions.add("Get", COMPOSITE_VALUE_FUNCTION);
    registeredFunctions.add("Get", MAP_VALUE_FUNCTION);
    registeredFunctions.add("Get", ANNOTATION_FUNCTION);
    registeredFunctions.add("Get", OBJECT_FIELD_FUNCTION);
    registeredFunctions.add("Get", LIST_VALUE_FUNCTION);

    // For debugging
    registeredFunctions.add("Print", PRINT_FUNCTION);
  }

  public static void main(String[] args) {
    // Dumps the registered functions
    for (Map.Entry<String, Collection<ValueFunction>> entry : registeredFunctions.entrySet()) {
      for (ValueFunction vf: entry.getValue()) {
        System.out.println(entry.getKey() + ": " + vf);
      }
    }
  }

}
