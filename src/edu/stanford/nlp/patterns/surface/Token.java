package edu.stanford.nlp.patterns.surface;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/** Currently can handle only ORs
 * Created by sonalg on 10/16/14.
 */
public class Token {
  //All the restrictions of a token: for example, word:xyz
  Map<Class, Object> classORrestrictions;
  //TODO: change this to map to true values
  String envBindBooleanRestriction;

  int numMinOcc = 1;
  int numMaxOcc = 1;
  //TODO: set this
  private String simple;



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
      for (Map.Entry<Class, Object> en : this.classORrestrictions.entrySet()) {
        if (str.isEmpty())
          str = "{" + en.getKey().getName() + ":" + en.getValue().toString() + "}";
        else
          str += " | " + "{" + en.getKey().getName() + ":" + en.getValue().toString() + "}";
      }
      str = "[" + str + "]";
    }else if(envBindBooleanRestriction != null && !envBindBooleanRestriction.isEmpty())
      str = envBindBooleanRestriction;
    if(numMinOcc != 1 || numMaxOcc != 1)
     str+="("+numMinOcc+","+numMaxOcc+")";
    return str.trim();
  }


  public String getSimple() {
    String str = "";
    if(classORrestrictions!= null && !this.classORrestrictions.isEmpty()) {
      for (Map.Entry<Class, Object> en : this.classORrestrictions.entrySet()) {
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

  public void addORRestriction(Class classR, Object value) {

    if(this.envBindBooleanRestriction != null && !this.envBindBooleanRestriction.isEmpty())
      throw new RuntimeException("cannot add restriction to something that is binding to an env variable");
    if(classORrestrictions == null)
      classORrestrictions = new TreeMap<Class, Object>(new Comparator<Class>() {
        @Override
        public int compare(Class o1, Class o2) {
          return o1.toString().compareTo(o2.toString());
        }
      });
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
    if(this.envBindBooleanRestriction == null || this.envBindBooleanRestriction.isEmpty() || this.classORrestrictions == null || this.classORrestrictions.isEmpty())
      return true;
    else
      return false;
  }

}
