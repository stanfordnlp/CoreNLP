package edu.stanford.nlp.patterns;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.dep.DataInstanceDep;
import edu.stanford.nlp.patterns.surface.DataInstanceSurface;
import edu.stanford.nlp.util.CoreMap;

import java.io.Serializable;
import java.util.List;

/** It's a list of Corelabels for SurfacePattern, Dependency parse for DepPattern etc
 * Created by sonalg on 11/1/14.
 */
public abstract class DataInstance implements Serializable {

  abstract public List<CoreLabel> getTokens();

  public static DataInstance getNewSurfaceInstance(List<CoreLabel> tokens){
    return new DataInstanceSurface(tokens);
  }

  public static DataInstance getNewInstance(PatternFactory.PatternType type, CoreMap s){
    if(type.equals(PatternFactory.PatternType.SURFACE))
        return new DataInstanceSurface(s.get(CoreAnnotations.TokensAnnotation.class));
    else if(type.equals(PatternFactory.PatternType.DEP))
        return new DataInstanceDep(s);
    else
        throw new UnsupportedOperationException();
  }


}
