package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.StringTokenizer;

public class BioCreativeFileMerge {

  public static void main(String[] args) throws Exception {

    int format = Integer.parseInt(args[0]);

    BufferedReader in1 = new BufferedReader(new FileReader(args[1]));
    BufferedReader in2 = new BufferedReader(new FileReader(args[2]));

    FileWriter out = new FileWriter(args[3]);

    String line1 = "", line2 = "", outLine = "";
    StringTokenizer st1, st2;
    String word1, word2, taggedWord1, taggedWord2, pos1, pos2;
    int i1, i2;
    String id1, id2;
    int i = 0;

    while ((line1 = in1.readLine()) != null) {
      i++;
      //System.out.println(i);
      line2 = in2.readLine();

      st1 = new StringTokenizer(line1);
      st2 = new StringTokenizer(line2);

      id1 = st1.nextToken();
      id2 = st2.nextToken();

      if (!id1.equals(id2)) {
        System.out.println("1Files don't match.  Line " + i + ":");
        System.out.println(line1 + "\n" + line2);
        System.exit(0);
      }

      //outLine = id1 + " ";

      //out.write(id1 + "\n");

      if (format == 0) {
        out.write(id1 + "\n");
      } else if (format == 1) {
        out.write(id1 + " ");
      }

      while (st1.hasMoreTokens()) {
        try {
          taggedWord1 = st1.nextToken();
          i1 = taggedWord1.lastIndexOf("/");
          word1 = taggedWord1.substring(0, i1);
          pos1 = taggedWord1.substring(i1 + 1);

          taggedWord2 = st2.nextToken();
          i2 = taggedWord2.lastIndexOf("/");
          word2 = taggedWord2.substring(0, i2);
          pos2 = taggedWord2.substring(i2 + 1);

          if (!word1.equalsIgnoreCase(word2)) {
            System.out.println("2Files don't match.  Line " + i + ":");
            System.out.println(word1 + "\n" + word2);
            System.out.println(line1 + "\n" + line2);
            System.exit(0);
          }

          if (format == 0) {
            if (pos2.startsWith("NEWGENE")) {
              out.write(word1 + " " + pos1 + " " + pos2 + "\n");
            } else {
              out.write(word1 + " " + pos1 + " O \n");
            }
          } else if (format == 1) {
            if (pos2.startsWith("NEWGENE")) {
              out.write(word2 + "/" + pos1 + "_" + pos2 + " ");
            } else {
              out.write(word2 + "/" + pos1 + " ");
            }
          }

        } catch (Exception e) {
          System.out.println(line1);
          System.exit(0);
        }
      }

      out.write("\n");

    }

    out.close();

  }

}
