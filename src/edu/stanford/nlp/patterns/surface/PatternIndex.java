package edu.stanford.nlp.patterns.surface;

import java.io.IOException;

/**
 * Created by sonalg on 10/24/14.
 */
public abstract class PatternIndex {

  public abstract int addToIndex(SurfacePattern p);
  public abstract SurfacePattern get(int i);
  public abstract  void close();

  public abstract Integer indexOf(SurfacePattern pat);

  public abstract void save(String dir) throws IOException;
}
