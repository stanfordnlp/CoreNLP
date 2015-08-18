package edu.stanford.nlp.patterns.demo;

import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass;
import edu.stanford.nlp.patterns.surface.SurfacePattern;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;

import java.util.Map;
import java.util.Properties;

/** This is for calling GetPatternsFromDataMultiClass from code.
 * Created by sonalg on 4/20/15.
 */
public class SPIEDDemo {

  public static void main(String[] args){
    try{

      Properties props = StringUtils.argsToPropertiesWithResolve(new String[]{"-props","file"});
      GetPatternsFromDataMultiClass<SurfacePattern> model = GetPatternsFromDataMultiClass.<SurfacePattern>run(props);

      for(Map.Entry<String, Counter<SurfacePattern>> p : model.getLearnedPatterns().entrySet()){
        System.out.println("For label " + p.getKey() + ", the patterns learned are: ");
        for(Map.Entry<SurfacePattern, Double> pat: p.getValue().entrySet()){
          System.out.println("Pattern " + pat + " with score " + pat.getValue());
        }
        System.out.println("For label " + p.getKey() + ", the learned words are:  "  + model.constVars.getLearnedWords(p.getKey()));
      }



    }catch(Exception e){
      e.printStackTrace();
    }

  }

}
