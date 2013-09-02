package edu.stanford.nlp.sequences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;

public class GazFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

  /**
   *
   */
  private static final long serialVersionUID = -8510805196161524401L;

  @Override
  public void init(SeqClassifierFlags flags) {
    super.init(flags);

    try {
      gazNames = new ArrayList<String>();
      gazMap = new CollectionValuedMap<Pair<String,String>,String[]>();

      System.err.print("initializing gazetteers...");
      for (String file : ObjectBank.getLineIterator(new File(flags.gazFilesFile))) {
        if (file.trim().length() == 0) { continue; }
        if (file.trim().startsWith("#")) { continue; }
        String[] bits = file.split("/");
        String gazName = bits[bits.length-1];
        gazNames.add(gazName);
        for (String entry : ObjectBank.getLineIterator(new File(file))) {
          String[] words = entry.split("\\s+");
          for (String word : words) {
            Pair<String,String> wordGaz = new Pair<String,String>(word,gazName);
            gazMap.add(wordGaz,words);
          }
        }
      }
      System.err.println("done");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  CollectionValuedMap<Pair<String,String>,String[]> gazMap = null;
  Collection<String> gazNames = null;

  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> info, int position, Clique clique) {

    Collection<String> features = new ArrayList<String>();

    if (clique == cliqueC) {
      String cWord = info.get(position).word();
      for (String gazName : gazNames) {
        Pair<String,String> wordGaz = new Pair<String,String>(cWord,gazName);
        Collection<String[]> possibleEntries = gazMap.get(wordGaz);
        boolean matches = false;
        LOOP: for (String[] entry : possibleEntries) {
          for (int i = position-entry.length+1; i <= position; i++) {
            // see if it matches starting there
            matches = true;
            for (int j = 0; j < entry.length; j++) {
              if (!info.get(i+j).word().equalsIgnoreCase(entry[j])) {
                matches = false;
                break;
              }
            }
            if (matches) {
              break LOOP;
            }
          }
        }
        if (matches) {
          features.add(gazName);
        }
      }
    } else if (clique == cliqueCpC) {
      String cWord = info.get(position).word();
      String pWord = info.get(position-1).word();
      Collection<String> cGaz = new ArrayList<String>();
      Collection<String> pGaz = new ArrayList<String>();
      for (String gazName : gazNames) {
        Collection<String> gaz = null;
        String word = null;
        for (int p = 0; p <= 1; p++) {
          if (p == 1) { gaz = cGaz; word = cWord; }
          else if (p == 0) { gaz = pGaz; word = pWord; }
          Pair<String,String> wordGaz = new Pair<String,String>(word,gazName);
          Collection<String[]> possibleEntries = gazMap.get(wordGaz);
          boolean matches = false;
        LOOP: for (String[] entry : possibleEntries) {
          for (int i = position-entry.length+p; i <= position; i++) {
            // see if it matches  starting there
            matches = true;
            for (int j = 0; j < entry.length; j++) {
              if (!info.get(i+j).word().equalsIgnoreCase(entry[j])) {
                matches = false;
                break;
              }
            }
            if (matches) {
              break LOOP;
            }
          }
        }
          if (matches) {
            gaz.add(gazName);
          }
        }
      }
      for (String g1 : pGaz) {
        for (String g2 : cGaz) {
          if (g1.equals("stopwords") == g2.equals("stopwords")) {
            features.add(g1+" :: "+g2);
          }
        }
      }
    }
    return features;
  }

  private void writeObject(java.io.ObjectOutputStream out)
    throws IOException {
    out.writeObject(gazNames);
    out.writeObject(gazMap);
  }

  @SuppressWarnings({"unchecked"})
  private void readObject(java.io.ObjectInputStream in)
    throws IOException, ClassNotFoundException {
    gazNames = (Collection<String>) in.readObject();
    gazMap = (CollectionValuedMap<Pair<String,String>,String[]>) in.readObject();
  }

}
