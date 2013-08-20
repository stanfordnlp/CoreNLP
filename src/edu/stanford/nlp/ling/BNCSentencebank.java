package edu.stanford.nlp.ling;

import edu.stanford.nlp.io.FileFilters;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * BNC Sentencebank (/Document loader)
 * 
 * Loads a BNC document and makes it available as a Sentencebank. Currently, just the sentences (word + POS tags)
 * are extracted from BNC SGML files. Additional information such as paragraph/header
 * mark up, etc. are not loaded. 
 * 
 * @author danielcer
 *         mcdm
 *
 */
public class BNCSentencebank extends Sentencebank<ArrayList<TaggedWord>, TaggedWord> {
   private List<ArrayList<TaggedWord>> sentences = new ArrayList<ArrayList<TaggedWord>>();
   private boolean VERBOSE = false;
   
   public BNCSentencebank(String filename) throws IOException {
     loadDocument(filename);
   }
   
   public BNCSentencebank(File path, FileFilter filt) {
     loadPath(path, filt);
   }
   
   private void loadDocument(String filename) throws IOException {
     loadDocument(new File(filename));
   }
   private void loadDocument(File file) throws IOException {
     if (VERBOSE) {
       System.err.printf("reading %s\n", file);
     }
     
     BufferedReader reader = new BufferedReader(new FileReader(file));
     Pattern p = Pattern.compile("^<s n=\".*");     
     
     for (String inline = reader.readLine(); inline != null; inline = reader.readLine()) {
       //System.err.println("line: " + inline);
       if (!p.matcher(inline).matches()) continue; //skip everything that isn't a sentence

       Pair<List<String>,List<String>> word_tag = processText(inline); //updating the lists of words and tags

       //old code to get the words only:
       //String sentStr = XMLUtils.stripTags(new StringReader(inline), null, false);
       //List<String> words = Arrays.asList(sentStr.split("\\s"));
       //List<String> tags = words;
       //sentences.add(Sentence.toTaggedList(words,tags));

       sentences.add(Sentence.toTaggedList(word_tag.first(), word_tag.second()));
     }
     reader.close();
   }


  private Pair<List<String>,List<String>>  processText(String line) {
    List<String> words = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();

    List<String> items = StringUtils.split(line,"<");
    for(String item : items) {
      //System.err.println("item: " + item);
      if(item.startsWith("w ") || item.startsWith("c ")) { //it's a word or a punctuation
        String[] tw = item.split(">");
        if(tw.length < 2) continue; //it means that there is a problem in the file (no word after tag)
        String tag = tw[0].substring(2);
        String word = tw[1];
        if(word.endsWith(" ")) {
          word = word.substring(0,word.length()-1);
        }
        if(word.contains(" ")){
          String[] tmp = word.split(" ");
          word = "";
          for(String part : tmp) {
            word += part + "_";
          }
          word = word.substring(0,word.length()-1);
        }
        if(word.contains(" ")){
          word = word.replace(" ", "_");
        }
        if(word.contains("/")){
          word = word.replace("/", "_");
        }
        words.add(word);
        tags.add(tag);
        //System.err.println(word + "/" + tag);
      }
    }

    return new Pair<List<String>,List<String>>(words,tags);

  }

  @Override
  public Iterator<ArrayList<TaggedWord>> iterator() {
    return sentences.iterator();
  }

  @Override
  public int size() {
    return sentences.size();
  }
  
  @Override
  public void apply(SentenceVisitor<TaggedWord> tp) {
    for (ArrayList<TaggedWord> sent : this) {
       tp.visitSentence(sent);
    }
  }

  @Override
  public void clear() {
    sentences.clear();    
  }

  @Override
  public void loadPath(File path, FileFilter filter) {
    if (path.isDirectory()) {      // 
      File[] directoryListing = path.listFiles(filter);
      if (directoryListing == null) {
        throw new IllegalArgumentException("Directory access problem for: " + path);
      }
      for (File file : directoryListing) {
        System.err.printf("processing '%s'\n", file);
        loadPath(file, filter);
      }
    } else {
      try { 
        loadDocument(path);
      } catch (IOException e) {
        throw new RuntimeException("Error trying to read file: "+path);
      }
    }
  }

  /**
   * Reads the parsed BNC files (-file "doc1,doc2,...").
   * Prints one sentence per line.
   * By default it prints only the words.
   * If -wordAndTag is used, it prints both in the following format : word/tag

   *
   * @param args parsed BNC files or a directory containing these
   * @throws IOException
   */

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.printf("Usage:\n\tjava edu.stanford.nlp.ling.BNCSentencebank -file \"(bnc doc1),(bnc doc2), ...\"");
    }

    Properties props = StringUtils.argsToProperties(args);
    boolean wordAndTag = props.getProperty("wordAndTag") != null;

    String files = props.getProperty("file");
    for (String filename : files.split(",")) {
      File file = new File(filename);
      BNCSentencebank bncbank;
      if (file.isDirectory()) {
        bncbank = new BNCSentencebank(file, FileFilters.findRegexFileFilter("."));
      } else {        
        bncbank = new BNCSentencebank(filename);        
      }

      for (ArrayList<TaggedWord> sent : bncbank) {
        if(wordAndTag) {
          for(TaggedWord tw : sent) {
            System.out.print(tw + " "); 
          }
          System.out.print("\n");
        } else {
          System.out.println(Sentence.listToString(sent));
        }
      }
    }
  }
}
