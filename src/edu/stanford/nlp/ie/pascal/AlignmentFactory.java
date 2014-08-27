package edu.stanford.nlp.ie.pascal;

import java.util.*;
import edu.stanford.nlp.util.Generics;

/**
 * Generates {@link Alignment} objects for acronym alignment.
 *
 * @author Jamie Nicolson
 */
public class AlignmentFactory {

  public static final byte SHIFT_LONG = 1;
  public static final byte SHIFT_SHORT = 2;
  public static final byte SHIFT_BOTH = 4;

  private char[] longForm;
  private char[] lcLongForm;
  private  char[] shortForm;
  private  char[] lcShortForm;
  private  int [][]alignMatrix;
  private byte [][]backMatrix;
  private Set<Alignment> alignments;

  public AlignmentFactory(String longForm, String shortForm) {
    this(longForm.toCharArray(), shortForm.toCharArray());
  }

  public static char[] toLower(char []in) {
    char[] out = new char[in.length];
    for(int i = 0; i < in.length; ++i) {
      out[i] = Character.toLowerCase(in[i]);
    }
    return out;
  }

  public AlignmentFactory(char[] longForm, char[] shortForm) {
    this.longForm = longForm;
    this.lcLongForm = toLower(longForm);
    this.shortForm = shortForm;
    this.lcShortForm = toLower(shortForm);

    alignMatrix = new int[lcLongForm.length][lcShortForm.length];
    backMatrix = new byte[lcLongForm.length][lcShortForm.length];
    for( int l = 0; l < lcLongForm.length; ++l) {
      for( int s = 0; s < lcShortForm.length; ++s) {
        int match = (lcLongForm[l] == lcShortForm[s]) ? 1 : 0;
        int froml = (l == 0) ? 0 : alignMatrix[l-1][s];
        int froms = (s == 0) ? 0 : alignMatrix[l][s-1];
        int frommatch =
                ((l==0 || s==0) ? 0 : alignMatrix[l-1][s-1]) + match;
        int max = Math.max(froml, Math.max(froms, frommatch));
        byte backp = 0;
        if( froml == max  ) backp |= SHIFT_LONG;
        if( froms == max  ) backp |= SHIFT_SHORT;
        if( match == 1 && frommatch == max ) backp |= SHIFT_BOTH;
        backMatrix[l][s] = backp;
        alignMatrix[l][s] = max;
      }
    }

    alignments = Generics.newHashSet();
    int[] pointers = new int[lcShortForm.length];
    Arrays.fill(pointers, -1);

    if( lcLongForm.length > 0 && lcShortForm.length > 0 ) {
      addCount = 0;
      //initListMatrix();
      findAlignments(pointers, lcLongForm.length-1, lcShortForm.length-1);
      //listMatrix = null;

    }
  }

  public Iterator<Alignment> getAlignments() {
    return alignments.iterator();
  }

  public ArrayList<Alignment> getAlignmentsList() {
    return new ArrayList<Alignment>(alignments);
  }

  public static String dumpIntArray(int []a) {
    StringBuilder buf = new StringBuilder();
    buf.append('[');
    for (int anA : a) {
      buf.append(anA).append(' ');
    }
    buf.append(']');
    return buf.toString();
  }

  int addCount;

/*
    LinkedList [] [] listMatrix;

    private void initListMatrix() {
      listMatrix = new LinkedList[][lcLongForm.length];
      for( int i = 0; i < lcLongForm.length; i++) {
        listMatrix[i] = new LinkedList[lcShortForm.length];
      }
    }
*/

/*
    private void findAlignments(int l, int s) {
        if( listMatrix[l][s] != null )
          return;

        byte backp = backMatrix[l][s];

        listMatrix[l][s] = new LinkedList();

        if( alignMatrix[l][s] == 0 ) {
            listMatrix[l][s].add( new int[shortForm.length] );
            return;
        }

        if( (backp & SHIFT_BOTH) != 0 ) {
            assert( lcLongForm[l] == lcShortForm[s] );
            findAlignments(l-1,s-1);
            LinkedList from = listMatrix[l-1][s-1];
            Iterator iter = from.iterator();
            while(iter.hasNext()) {
              int[] ref = (int[]) iter.next();
              int[] cpy = ref.clone();
              cpy[s] = l;
              listMatrix[l][s].add(cpy);
            }
        }

        if( (backp & SHIFT_LONG) != 0 ) {
            if( l != 0 ) {
              findAlignments(l-1, s);
              Iterator iter = listMatrix[l-1][s];
              while(iter.hasNext()) {
                listMatrix[l][s].add( iter.next() );
              }
            } else {
              listMatrix[l][s].add( new int[shortForm.length] );
            }
        }

        if( (backp & SHIFT_SHORT) != 0 ) {
            backp &= ~SHIFT_SHORT;
            int[] ptrcpy = (int[]) ((backp == 0) ? pointers : pointers.clone());
            if( s == 0 ) {
                ++addCount;
                alignments.add( new Alignment(longForm, shortForm, ptrcpy) );
            } else {
                findAlignments(ptrcpy, l, s-1);
            }
        }

        if( lcLongForm[l] == lcShortForm[s] )
            assert( (backMatrix[l][s] & SHIFT_BOTH) != 0);
*/

  private void findAlignments(int[]pointers, int lg, int s)
  {
    byte backp = backMatrix[lg][s];

    if( alignMatrix[lg][s] == 0 ) {
      ++addCount;
      alignments.add( new Alignment(longForm, shortForm, pointers) );
      return;
    }

    if( (backp & SHIFT_LONG)!= 0 ) {
      backp &= ~SHIFT_LONG;
      int[] ptrcpy = ((backp == 0) ? pointers : pointers.clone());
      if( lg == 0 ) {
        ++addCount;
        alignments.add( new Alignment(longForm, shortForm, ptrcpy) );
      } else {
        findAlignments(ptrcpy, lg-1, s);
      }
    }

    if( (backp & SHIFT_SHORT) != 0 ) {
      backp &= ~SHIFT_SHORT;
      int[] ptrcpy = ((backp == 0) ? pointers : pointers.clone());
      if( s == 0 ) {
        ++addCount;
        alignments.add( new Alignment(longForm, shortForm, ptrcpy) );
      } else {
        findAlignments(ptrcpy, lg, s-1);
      }
    }

    if( lcLongForm[lg] == lcShortForm[s] )
      assert( (backMatrix[lg][s] & SHIFT_BOTH) != 0);

    if( (backp & SHIFT_BOTH) != 0 ) {
      assert( lcLongForm[lg] == lcShortForm[s] );
      pointers[s] = lg;
      if( lg == 0 || s == 0 ) {
        ++addCount;
        alignments.add( new Alignment(longForm, shortForm, pointers) );
      } else {
        findAlignments(pointers, lg-1, s-1);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    AlignmentFactory fact = new AlignmentFactory(args[0].toCharArray(),
            AcronymModel.stripAcronym(args[1]));

    Iterator<Alignment> iter = fact.getAlignments();
    while( iter.hasNext() ) {
      Alignment a = iter.next();
      a.print();
    }
  }

}
