package edu.stanford.nlp.ie.pascal;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.util.Generics;

 /**
  * Scores Pascal challenge workshop information templates.
  * This score reflects which fields are present/absent, how well acronyms
  * agree with the names and URLs they correspond to.
  *
  * @author Jamie Nicolson
  */
public class AcronymModel implements RelationalModel {

  
  private static final double HIGH_PROB = 1.0;
  private static final double LOW_PROB = 0.0;
  private static boolean DEBUG= false;

  private static final String acronymStatistics =
    "workshopname workshopacronym workshophomepage conferencename conferenceacronym conferencehomepage\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00549450549450549\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.0521978021978022\n" +
    "0.00274725274725275\n" +
    "0.0357142857142857\n" +
    "0.00549450549450549\n" +
    "0.021978021978022\n" +
    "0.010989010989011\n" +
    "0.0357142857142857\n" +
    "0.0302197802197802\n" +
    "0.0824175824175824\n" +
    "0.00549450549450549\n" +
    "0.043956043956044\n" +
    "0.010989010989011\n" +
    "0.021978021978022\n" +
    "0.00549450549450549\n" +
    "0.0521978021978022\n" +
    "0.0412087912087912\n" +
    "0.0467032967032967\n" +
    "0.00274725274725275\n" +
    "0.010989010989011\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.00274725274725275\n" +
    "0.0137362637362637\n" +
    "0.00824175824175824\n" +
    "0.167582417582418\n" +
    "0.00549450549450549\n" +
    "0.0494505494505494\n" +
    "0.00824175824175824\n" +
    "0.0164835164835165\n" +
    "0.00549450549450549\n" +
    "0.0604395604395604\n" +
    "0.0467032967032967\n";
                                                                                
  private Prior priors;

  /**
   * Scores the partial template containing only the fields relevant to the score.
   * @param temp the {@link InfoTemplate} to be scored.
   * @return the model's score
   */
  public double computeProb(InfoTemplate temp){
    return computeProb(temp.wname,temp.wacronym,temp.cname,temp.cacronym,
      temp.whomepage, temp.chomepage);
  }
/**
 * Scores the {@link PascalTemplate} using the fields it contains which are relevant to the score.
 * (Ignores location and date fields.)
 * @param temp the full {@link PascalTemplate} to be scored
 * @return the model's score
 */

  public double computeProb(PascalTemplate temp) {
    double prob = 1.0;

    String wsname = temp.getValue("workshopname");
    String confname = temp.getValue("conferencename");
    String wsacronym = temp.getValue("workshopacronym");
    String confacronym = temp.getValue("conferenceacronym");
    String wsurl = temp.getValue("workshophomepage");
    String confurl = temp.getValue("conferencehomepage");
    return computeProb(wsname, wsacronym,confname,confacronym, wsurl, confurl);
  }

   /**
    * @throws IOException if the acronym statistics/weights can't be read from file.
    */
   public AcronymModel() throws IOException {
     priors = new Prior(new BufferedReader(new StringReader(acronymStatistics)));
     features = new Feature[]{new AcronymModel.LettersAligned(), new AcronymModel.BegWord(), new AcronymModel.EndWord(), new AcronymModel.AfterAligned(), new AcronymModel.AlignedPerWord(), new AcronymModel.WordsSkipped(), new AcronymModel.SyllableBoundary()};
     weights = new double[]{// here's weights from a bunch of training examples
       //-4.1004, 18.4127, 0.1789, 16.3189, 0.8818, -0.0725, -0.6550
       //-12.4082, 18.3893, 2.1826, 18.8487, 0.5042, -0.1231, 1.8876
       -11.8880, 14.4534, -2.6316, 24.1838, -2.2320, -0.2508, 4.3501

     };
     //intercept = -14.1449;
     //intercept = -7.4882;
     intercept = -2.2062;
   }

  private double computeProb(String wsname, String wsacronym, String confname,
                            String confacronym, String wsurl, String confurl){

    Set<String> presentFields = Generics.newHashSet();
    if( wsname != null && !wsname.equals("null") && !wsname.equals("") )
      presentFields.add("workshopname");
    if( wsacronym != null && !wsacronym.equals("null") && !wsacronym.equals(""))
      presentFields.add("workshopacronym");
    if( confname != null && !confname.equals("null")
        && !confname.equals(""))
      presentFields.add("conferencename");
    if( confacronym != null && !confacronym.equals("null")
        && !confacronym.equals(""))
      presentFields.add("conferenceacronym");
    if( wsurl != null && !wsurl.equals("null") && !wsurl.equals(""))
      presentFields.add("workshophomepage");
    if( confurl != null && !confurl.equals("null") && !confurl.equals(""))
      presentFields.add("conferencehomepage");

    //if the workshop and conference have the same acronym we return 0.
    if(presentFields.contains("conferenceacronym") &&
       presentFields.contains("workshopacronym") &&
       confacronym.equals(wsacronym)){

      return 0.0;
    }

    double prob = priors.get(presentFields);
    //System.out.println("Setting prior to " + prob + " based on the following "+
    // "fields being present: " + presentFields.toString());
    if( wsname != null && wsacronym != null ) {
      if(DEBUG)System.err.println("computing similarity for workshop");
      prob *= similarity(wsname, wsacronym);
    } else {
      if(DEBUG)System.err.println("NOT computing similarity for workshop");
    }

    if( confname != null && confacronym != null ) {
      if(DEBUG)System.err.println("computing similarity for conference");
      prob *= similarity(confname, confacronym);
    } else {
      if(DEBUG)System.err.println("NOT computing similarity for conference");
    }

    if( confacronym != null && confurl != null ) {
      if( acronymMatchesURL(confacronym, confurl) ) {
        prob *= probMatchFromAcronymAndURLMatch;
      } else {
        prob *= probMatchFromAcronymAndURLNoMatch;
      }
    }

    if( wsacronym != null && wsurl != null ) {
      if( acronymMatchesURL(wsacronym, wsurl) ) {
        prob *= probMatchFromAcronymAndURLMatch;
      } else {
        prob *= probMatchFromAcronymAndURLNoMatch;
      }
    }
    return prob;
  }

  private static boolean acronymMatchesURL(String ac, String url) {
    String lowerURL = url.toLowerCase();
    String strippedAc = (new String(AcronymModel.stripAcronym(ac))).toLowerCase();

    return lowerURL.indexOf(strippedAc) != -1;
  }

   private static final double probMatchFromAcronymAndURLMatch = .23934426;
   private static final double probMatchFromAcronymAndURLNoMatch = .052516411378;

   /**
    * Finds longest subsequent string of digits. Returns empty string
    * if there aren't any digits.
    */
   private static String acronymNumber(String acronym) {
     return "";
   }

   public static double URLSimilarity(String URL, String acronym) {
     String strippedAc = new String(stripAcronym(acronym));
     String acNumber = acronymNumber(acronym);
     return 0.0;
   }

   /**
    * @return the "rich similarity" score
    */
   public double similarity(String name, String acronym) {
     return RichSimilarity(name, acronym);
   }
     /**
      *
      * @return the "naive similarity" score
      */
   public double naiveSimilarity(String name, String acronym) {
     double similarity = LOW_PROB;
     String[] nameWords = splitOnWhitespace(name);
     String[] acronymWords = splitOnWhitespace(acronym);

     // first put together the letters in the acronym
     char[] acLetters = allLetters(acronymWords);

     // first let's try pulling the first letters from the name, and combining them to get the acronym
     char[] nameFirstLetters = firstLetters(nameWords);

     if (firstLetterInOrderMatch(nameFirstLetters, acLetters)) {
       // the letters in acronym can be constructed from the first letters in the name, in order
       similarity = HIGH_PROB;
     }

     if (DEBUG) {
       System.err.println("Similarity between (" + name + ") and (" + acronym + ") is " + similarity);
     }
     return similarity;
   }

   /**
    *
    * @return the Hearst similarity score
    */
   public double HearstSimilarity(String name, String acronym) {
     char[] namechars = name.toLowerCase().toCharArray();
     char[] acrochars = acronym.toLowerCase().toCharArray();

     int nindex = namechars.length - 1;
     for (int aindex = acrochars.length - 1; aindex >= 0; --aindex) {
       if (!Character.isLetter(acrochars[aindex])) {
         continue;
       }
       while ((nindex >= 0 && namechars[nindex] != acrochars[aindex]) || (aindex == 0 && nindex > 0 && Character.isLetterOrDigit(namechars[nindex - 1]))) {
         nindex--;
       }
       if (nindex < 0) {
         //  System.err.println("\"" + name + "\" does NOT match \"" +
         //     acronym + "\"\n");
         return 0;
       }

       nindex--;
     }

     //System.err.println("\"" + name + "\" matches \"" + acronym + "\"\n");
     return 1.0;
   }

   public static interface Feature {
     public double value(Alignment alignment);

     public String toString();
   }

   public static class LettersAligned implements Feature {
     public String toString() {
       return "LettersAligned";
     };
     public double value(Alignment alignment) {
       int numAligned = 0;
       for (int i = 0; i < alignment.pointers.length; ++i) {
         if (alignment.pointers[i] != -1) {
           numAligned++;
         }
       }
       double pct = (double) numAligned / (double) alignment.pointers.length;
       if (DEBUG)
         System.out.println("LettersAligned=" + pct);
       return pct;
     }
   }

    public static class BegWord implements Feature {
        public String toString() { return "BegWord"; };
        public double value(Alignment alignment) {
            int begAligned = 0;
            for( int s = 0; s < alignment.pointers.length; ++s) {
                int idx = alignment.pointers[s];
                if( idx == 0 ) {
                    begAligned++;
                } else if( idx > 0) {
                    char cur = alignment.longForm[idx];
                    char prev = alignment.longForm[idx-1];
                    if( !Character.isLetterOrDigit(prev) &&
                        Character.isLetterOrDigit(cur) )
                    {
                        begAligned++;
                    }
                }
            }
            return (double)begAligned / (double)alignment.shortForm.length;
        }
    }

    public static class EndWord implements Feature {
        public String toString() { return "EndWord"; };
        public double value(Alignment alignment) {
            int endAligned = 0;
            for( int s = 0; s < alignment.pointers.length; ++s) {
                int idx = alignment.pointers[s];
                if( idx == alignment.longForm.length-1 ) {
                    endAligned++;
                } else if( idx >= 0) {
                    char cur = alignment.longForm[idx];
                    char next = alignment.longForm[idx+1];
                    if( !Character.isLetterOrDigit(next) &&
                        Character.isLetterOrDigit(cur) )
                    {
                        endAligned++;
                    }
                }
            }
            return (double)endAligned / (double)alignment.shortForm.length;
        }
    }

    /**
     * Percent of letters aligned immediately after another aligned letter.
     */
    public static class AfterAligned implements Feature {
      public String toString() { return "AfterAligned"; }

      public double value(Alignment alignment) {
        int numAfter = 0;
        for( int i = 1; i < alignment.pointers.length; ++i) {
          if( alignment.pointers[i] == alignment.pointers[i-1] + 1 ) {
            numAfter++;
          }
        }
        return (double)numAfter / (double)alignment.shortForm.length;
      }
    }

    private static class RunningAverage {
      double average;
      int numSamples;

      public RunningAverage() {
        average = 0.0;
        numSamples = 0;
      }

      public void addSample(double sample) {
        average = (numSamples * average) + sample;
        numSamples++;
        average /= numSamples;
      }

      public double getAverage() {
        return average;
      }

      public double getNumSammples() {
        return numSamples;
      }
    }

    /**
     * Average number of aligned letters per word.
     */
    public static class AlignedPerWord implements Feature {
      public String toString() { return "AlignedPerWord"; }

      public double value(Alignment alignment) {
/*
        RunningAverage alignedPerWord = new RunningAverage();
        boolean inWord = false;
        int alignCount = 0;
        int sidx = 0;
        for(int lidx = 0; lidx < alignment.longForm.length; ++lidx ) {
          char cur = alignment.longForm[lidx];
          if( Character.isLetterOrDigit(cur) && !inWord ) {
            // beginning of word
            inWord = true;
          } else if( inWord && !Character.isLetterOrDigit(cur) ) {
            // end of word
            alignedPerWord.addSample(alignCount);
            alignCount = 0;
            inWord = false;
          }

          while( sidx < alignment.pointers.length &&
                 alignment.pointers[sidx] < lidx )
            sidx++;

          if( sidx < alignment.pointers.length &&
              alignment.pointers[sidx] == lidx && inWord)
          {
            alignCount++;
          }
        }
        if( inWord ) {
          // end of last word
          alignedPerWord.addSample(alignCount);
        }

        return alignedPerWord.getAverage();
*/
        boolean inWord = false;
        int wordCount = 0;
        for(int lidx = 0; lidx < alignment.longForm.length; ++lidx ) {
          char cur = alignment.longForm[lidx];
          if( Character.isLetterOrDigit(cur) && !inWord ) {
            // beginning of word
            ++wordCount;
            inWord = true;
          } else if( inWord && !Character.isLetterOrDigit(cur) ) {
            // end of word
            inWord = false;
          }
        }
        int alignCount = 0;
        for( int sidx = 0; sidx < alignment.pointers.length; ++sidx) {
          if( alignment.pointers[sidx] != -1 ) {
            ++alignCount;
          }
        }
        if( wordCount == 0 ) {
          return 0;
        } else {
          return (double)alignCount / (double)wordCount;
        }
      }
    }

    public static class WordsSkipped implements Feature {
        public String toString() { return "WordsSkipped"; };
        public double value(Alignment alignment) {
            int wordsSkipped = 0;
            int wordsAligned = 0;
            boolean inWord = false;
            boolean gotAlignedChar = false;
            boolean []isAligned = new boolean[alignment.longForm.length];
            for( int s = 0; s < alignment.pointers.length; ++s ) {
                if( alignment.pointers[s] != -1 ) {
                    isAligned[alignment.pointers[s]] = true;
                }
            }
            for( int l = 0; l < alignment.longForm.length; ++l ) {
                char cur = alignment.longForm[l];
                if( inWord ) {
                    if( !Character.isLetterOrDigit(cur)) {
                        // just finished a word
                        if( gotAlignedChar ) {
                            wordsAligned++;
                        } else {
                            wordsSkipped++;
                        }
                        inWord = false;
                    }
                } else {
                    if( Character.isLetterOrDigit(cur)) {
                        inWord = true;
                        gotAlignedChar = false;
                    }
                }
                if( isAligned[l] ) gotAlignedChar = true;
            }
            if( inWord ) {
                if( gotAlignedChar ) {
                    wordsAligned++;
                } else {
                    wordsSkipped++;
                }
            }
            if(DEBUG)System.out.println("Words skipped: " + wordsSkipped + "/" +
                (wordsSkipped + wordsAligned) );
            return wordsSkipped;
        }
    }

    public static class SyllableBoundary implements Feature {
        public String toString() { return "SyllableBoundary"; };
        TeXHyphenator teXHyphenator = new TeXHyphenator();
        public SyllableBoundary() throws IOException {
          teXHyphenator.loadDefault();
        }
        public double value(Alignment alignment) {
          char [] lcLongForm =
            (new String(alignment.longForm)).toLowerCase().toCharArray();
          boolean [] breakPoints = teXHyphenator.findBreakPoints(lcLongForm);
          int numSylAligned = 0;
          for( int i = 0; i < alignment.pointers.length; ++i ) {
            if( alignment.pointers[i] != -1 &&
                breakPoints[alignment.pointers[i]] )
            {
              numSylAligned++;
            }
          }
          return (double)numSylAligned / (double)alignment.pointers.length;
        }
    }

    private final Feature[] features;

    private final double[] weights;
    private final double intercept;

    public static char[] stripAcronym(String acronym) {
        char [] raw = acronym.toCharArray();
        char [] firstTry = new char[raw.length];
        int outIdx = 0;
        for( int inIdx = 0; inIdx < raw.length; ++inIdx) {
            if( Character.isLetter(raw[inIdx]) ) {
                firstTry[outIdx++] = raw[inIdx];
            }
        }
        if( outIdx == firstTry.length ) {
          if(DEBUG)  System.out.println("Converted \"" + acronym + "\" to \"" +
                (new String(firstTry)) + "\"\n");
            return firstTry;
        } else {
            char [] polished = new char[outIdx];
            System.arraycopy(firstTry, 0, polished, 0, outIdx);
          if(DEBUG)  System.out.println("Converted \"" + acronym + "\" to \"" +
                (new String(polished)) + "\"\n");
            return polished;
        }
    }
        

    public double RichSimilarity(String name, String acronym) {
        AlignmentFactory fact = new AlignmentFactory(
            name.toCharArray(), stripAcronym(acronym) );

        double maxprob = 0.0;
        Iterator iter = fact.getAlignments();
        while(iter.hasNext()) {
            Alignment align = (Alignment) iter.next();

            double [] featureVals = new double[features.length];
            for( int f = 0; f < features.length; ++f) {
                featureVals[f] = features[f].value(align);
            }

            // compute dotproduct and sigmoid
            double dotprod = dotproduct(weights, featureVals) + intercept;
            double exp = Math.exp(dotprod);
            double prob = exp / (1 + exp);

            // align.print();
            //System.out.println("Prob: " + prob + "\n-----------\n");

            if( prob > maxprob ){
                maxprob = prob;
            }
        }

        return maxprob;
    }

    private static double dotproduct(double[] one, double[]two) {
        double sum = 0.0;
        for( int i = 0; i < one.length; ++i) {
            double product = one[i] * two[i];
            if(DEBUG)System.out.println("product: " + product);
            sum += product;
        }
        if(DEBUG)System.out.println("sum: " + sum);
        return sum;
    }

    private static final String[] stringArrayType = new String[0];

    private static String[] splitOnWhitespace(String words) {
        String[] firstCut = words.split("\\s+");

        ArrayList<String> wordList = new ArrayList<String>(firstCut.length);
        for( int i = 0; i < firstCut.length; ++i ) {
            if( firstCut[i].length() > 0 ) {
                wordList.add(firstCut[i]);
            }
        }
        return wordList.toArray(stringArrayType);
    }

    private static boolean firstLetterInOrderMatch(char[] nameFirstLetters, char[] acLetters) {
        int nameIdx = 0;
        int acIdx = 0;

        for( ; acIdx < acLetters.length; ++acIdx) {
            while( nameIdx < nameFirstLetters.length && nameFirstLetters[nameIdx] != acLetters[acIdx] ) {
                ++nameIdx;
            }
            if( nameIdx == nameFirstLetters.length ) {
                return false;
            }
        }
        return true;
    }

    private static char[] allLetters(String[] acronym) {
        StringBuffer sb = new StringBuffer();
        for( int s = 0; s < acronym.length; ++s ) {
            String acr = acronym[s];
            for(int c = 0; c < acr.length(); ++c ) {
                char ch = acr.charAt(c);
                if( Character.isLetter( ch ) ) {
                    sb.append(ch);
                }
            }
        }
        return sbToChars(sb);
    }

    private static char[] firstLetters(String[] name) {
        StringBuffer sb = new StringBuffer(name.length);
        for( int s = 0; s < name.length; ++s) {
            char c = name[s].charAt(0);
            if( Character.isLetter(c) ) {
                sb.append(c);
            }
        }
        return sbToChars(sb);
    }

    private static char[] sbToChars(StringBuffer sb) {
        char[] letters = new char[sb.length()];
        sb.getChars(0, sb.length(), letters, 0);
        return letters;
    }

  public static void main(String[] args) throws Exception {

    AcronymModel am = new AcronymModel();
    String s1 = args[0];
    String s2 = args[1];
    System.out.println("Hearst:  "+am.HearstSimilarity(s1, s2));
    System.out.println("naive:   "+am.naiveSimilarity(s1, s2));
    System.out.println("Rich:    "+am.RichSimilarity(s1, s2));
    System.out.println("default: "+am.similarity(s1, s2));

  }

}
