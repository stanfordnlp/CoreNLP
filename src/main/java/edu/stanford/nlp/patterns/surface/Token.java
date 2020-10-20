package edu.stanford.nlp.patterns.surface;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.patterns.ConstantsAndVariables;
import edu.stanford.nlp.patterns.PatternFactory;

/** Currently can handle only ORs.
 *
 *  @author sonalg
 *  @version 10/16/14
 */
public class Token implements Serializable {

  //Can be semgrex.Env but does not matter
  //static public Env env = TokenSequencePattern.getNewEnv();

  static Map<Class, String> class2KeyMapping = new ConcurrentHashMap<>();

  //All the restrictions of a token: for example, word:xyz
  Map<Class, String> classORrestrictions;

  //TODO: may be change this to map to true values?
  String envBindBooleanRestriction;

  private final Pattern alphaNumeric = Pattern.compile("^[\\p{Alnum}\\s]+$");

  int numMinOcc = 1;
  int numMaxOcc = 1;

  PatternFactory.PatternType type;

  public Token(PatternFactory.PatternType type){
    this.type  = type;
  }

  public Token(Class c, String s, PatternFactory.PatternType type){
    this(type);
    addORRestriction(c, s);
  }

  public Map<String, String> classORRestrictionsAsString(){
    if(classORrestrictions== null || classORrestrictions.isEmpty())
      return null;
    Map<String, String> str = new HashMap<>();
    for(Map.Entry<Class, String> en: classORrestrictions.entrySet()){
       str.put(class2KeyMapping.get(en.getKey()), en.getValue());
    }
    return str;
  }

  @Override
  public String toString() {
    if (type.equals(PatternFactory.PatternType.SURFACE))
      return toStringSurface();
    else if (type.equals(PatternFactory.PatternType.DEP))
      return toStringDep();
    else
      throw new UnsupportedOperationException();
  }

  private String toStringDep() {
    String str = "";
    if(classORrestrictions!= null && !this.classORrestrictions.isEmpty()) {
      for (Map.Entry<Class, String> en : this.classORrestrictions.entrySet()) {
        String orgVal = en.getValue().toString();
        String val;


        if(!alphaNumeric.matcher(orgVal).matches())
          val = "/" + Pattern.quote(orgVal.replaceAll("/","\\\\/"))+ "/";
        else
          val = orgVal;

        if (str.isEmpty())
          str = "{" + class2KeyMapping.get(en.getKey()) + ":" + val + "}";
        else
          str += " | " + "{" + class2KeyMapping.get(en.getKey()) + ":" + val + "}";
      }
    }
    return str.trim();
  }

  private String toStringSurface(){
    String str = "";
    if(classORrestrictions!= null && !this.classORrestrictions.isEmpty()) {
      for (Map.Entry<Class, String> en : this.classORrestrictions.entrySet()) {
        String orgVal = en.getValue().toString();
        String val;


        if(!alphaNumeric.matcher(orgVal).matches())
          val = "/" + Pattern.quote(orgVal.replaceAll("/","\\\\/"))+ "/";
        else
          val = "\"" + orgVal +"\"";

        if (str.isEmpty())
          str = "{" + class2KeyMapping.get(en.getKey()) + ":" + val + "}";
        else
          str += " | " + "{" + class2KeyMapping.get(en.getKey()) + ":" + val + "}";
      }
      str = "[" + str + "]";
    }else if(envBindBooleanRestriction != null && !envBindBooleanRestriction.isEmpty())
      str = envBindBooleanRestriction;
    if(numMinOcc != 1 || numMaxOcc != 1)
     str+="{"+numMinOcc+","+numMaxOcc+"}";
    return str.trim();
  }


  public String getSimple() {
    String str = "";
    if(classORrestrictions!= null && !this.classORrestrictions.isEmpty()) {
      for (Map.Entry<Class, String> en : this.classORrestrictions.entrySet()) {
        if (str.isEmpty())
          str = en.getValue().toString();
        else
          str += "|" + en.getValue().toString();
      }

    }else if(envBindBooleanRestriction != null && !envBindBooleanRestriction.isEmpty()){
    if(envBindBooleanRestriction.startsWith("$FILLER"))
      str = "FW";
    else if (envBindBooleanRestriction.startsWith("$STOP"))
      str = "SW";

    }

    return str.trim();
  }

  @Override
  public int hashCode(){
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object o){
    if(! (o instanceof Token))
      return false;
    return o.toString().equals(this.toString());

  }

  public void addORRestriction(Class classR, String value) {

    getKeyForClass(classR);

    if(this.envBindBooleanRestriction != null && !this.envBindBooleanRestriction.isEmpty())
      throw new RuntimeException("cannot add restriction to something that is binding to an env variable");
    if(classORrestrictions == null)
      classORrestrictions = new TreeMap<>(new ClassComparator());
    assert value!=null;
    classORrestrictions.put(classR, value);
  }

  public void setEnvBindRestriction(String envBind) {
    if(this.classORrestrictions != null && !this.classORrestrictions.isEmpty())
      throw new RuntimeException("cannot add env bind restriction to something that has restricted");
    this.envBindBooleanRestriction = envBind;
  }

  public void setNumOcc(int min, int max) {
    numMinOcc = min;
    numMaxOcc = max;
  }

  public boolean isEmpty() {
    return (this.envBindBooleanRestriction == null || this.envBindBooleanRestriction.isEmpty()) && (this.classORrestrictions == null || this.classORrestrictions.isEmpty());
  }

  public static String getKeyForClass(Class classR) {

    String key =class2KeyMapping.get(classR);
    if(key == null){
      for(Map.Entry<String, Object> vars: ConstantsAndVariables.globalEnv.getVariables().entrySet()){
        if(vars.getValue().equals(classR)){
          key = vars.getKey().toLowerCase();
          class2KeyMapping.put(classR, key);
          break;
        }
      }
    }
    if(key == null){
      key = classR.getSimpleName().toLowerCase();
      class2KeyMapping.put(classR, key);
      ConstantsAndVariables.globalEnv.bind(key, classR);
    }
    return key;
  }

  public class ClassComparator implements Serializable, Comparator<Class>{
    @Override
    public int compare(Class o1, Class o2) {
      return o1.toString().compareTo(o2.toString());
    }
  }

  public static String toStringClass2KeyMapping(){
    StringBuilder str = new StringBuilder();
    for(Map.Entry<Class, String> en: class2KeyMapping.entrySet()){
      if(str.length() > 0)
        str.append("\n");
      str.append(en.getKey().getName()+"###"+en.getValue());
    }
    return str.toString();
  }

  public static void setClass2KeyMapping(File file) throws ClassNotFoundException {
    for(String line: IOUtils.readLines(file)){
      String[] toks = line.split("###");
      class2KeyMapping.put(Class.forName(toks[0]), toks[1]);
    }
  }

}
