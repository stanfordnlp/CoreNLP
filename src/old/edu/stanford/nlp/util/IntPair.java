package old.edu.stanford.nlp.util;

public class IntPair extends IntTuple {
  private static final long serialVersionUID = 1L;


  public IntPair() {
    super(2);
  }


  public IntPair(int src, int trgt) {
    super(2);
    elements[0] = src;
    elements[1] = trgt;
  }


  public int getSource() {
    return get(0);
  }

  public int getTarget() {
    return get(1);
  }


  @Override
  public IntTuple getCopy() {
    IntPair nT = new IntPair(elements[0], elements[1]);
    return nT;
  }


}


