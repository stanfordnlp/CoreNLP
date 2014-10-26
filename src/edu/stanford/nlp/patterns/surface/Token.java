package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Currently can handle only ORs
 * Created by sonalg on 10/16/14.
 */
public class Token implements Serializable {
  static public Env env = TokenSequencePattern.getNewEnv();
  static Map<Class, String> class2KeyMapping = new ConcurrentHashMap<Class, String>();
  //All the restrictions of a token: for example, word:xyz
  Map<Class, String> classORrestrictions;
  //TODO: may be change this to map to true values?
  String envBindBooleanRestriction;
  final Pattern alphaNumeric = Pattern.compile("^[\\p{Alnum}\\s\\.]+$");

  int numMinOcc = 1;
  int numMaxOcc = 1;


  public Map<String, String> classORRestrictionsAsString(){
    if(classORrestrictions== null || classORrestrictions.isEmpty())
      return null;
    Map<String, String> str = new HashMap<String, String>();
    for(Map.Entry<Class, String> en: classORrestrictions.entrySet()){
       str.put(class2KeyMapping.get(en.getKey()), en.getValue().toString());
    }
    return str;
  }



  String[] trim(String[] p) {

    if (p == null)
      return null;

    for (int i = 0; i < p.length; i++) {
      p[i] = p[i].trim();
    }
    return p;
  }

  @Override
  public String toString(){

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
      classORrestrictions = new TreeMap<Class, String>(new ClassComparator());
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
    if((this.envBindBooleanRestriction == null || this.envBindBooleanRestriction.isEmpty()) &&  (this.classORrestrictions == null || this.classORrestrictions.isEmpty()))
      return true;
    else
      return false;
  }

  public static String getKeyForClass(Class classR) {

    String key =class2KeyMapping.get(classR);
    if(key == null){
      for(Map.Entry<String, Object> vars: env.getVariables().entrySet()){
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
      env.bind(key, classR);
    }
    return key;
  }

  public class ClassComparator implements Serializable, Comparator<Class>{
    @Override
    public int compare(Class o1, Class o2) {
      return o1.toString().compareTo(o2.toString());
    }
  }

}
