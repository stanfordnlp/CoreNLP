package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Created by sonalg on 10/24/14.
 */
public abstract class PatternIndex {

  public abstract int addToIndex(SurfacePattern p);
  public abstract SurfacePattern get(int i);
  public abstract  void close();

  public abstract Integer indexOf(SurfacePattern pat);

  public abstract void save(String dir) throws IOException;

  public static PatternIndex newInstance(ConstantsAndVariables.PatternIndexWay way, String dir) {
    if (way.equals(ConstantsAndVariables.PatternIndexWay.MEMORY))
      return new PatternIndexInMemory();
    else if (way.equals(ConstantsAndVariables.PatternIndexWay.LUCENE)) {
      try {
        Class<? extends PatternIndex> c = (Class<? extends PatternIndex>) Class.forName("edu.stanford.nlp.patterns.surface.PatternIndexLucene");
        Constructor<? extends PatternIndex> ctor = c.getConstructor(String.class);
        PatternIndex index = ctor.newInstance(dir);
        return index;
      }catch (ClassNotFoundException e) {
        throw new RuntimeException("Lucene option is not distributed (license clash). Email us if you really want it.");
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      }
    } else
      throw new UnsupportedOperationException();
  }

  public static PatternIndex load(String dir, ConstantsAndVariables.PatternIndexWay way){
    Redwood.log(ConstantsAndVariables.minimaldebug, "Reading all patterns from " + dir);

    if(way.equals(ConstantsAndVariables.PatternIndexWay.MEMORY))
    return PatternIndexInMemory.load(dir);
    else if(way.equals(ConstantsAndVariables.PatternIndexWay.LUCENE)){
      try{
        Class<? extends PatternIndex> c = (Class<? extends PatternIndex>) Class.forName("edu.stanford.nlp.patterns.surface.PatternIndexWayLucene");
        Method m = c.getMethod("load", String.class);
        PatternIndex index = (PatternIndex) m.invoke(null, new Object[]{dir});
        return index;
      }catch (ClassNotFoundException e) {
        throw new RuntimeException("Lucene option is not distributed (license clash). Email us if you really want it.");
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }

    } else
      throw new UnsupportedOperationException();
  }

  public abstract void finishCommit();
}
