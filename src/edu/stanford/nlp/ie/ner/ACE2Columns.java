package edu.stanford.nlp.ie.ner;

import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.Generics;

/**
 * A utility for converting files from the ACE information extraction task
 * format into the multi-column format.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class ACE2Columns {

  private ACE2Columns() {
  }


  /**
   * A container class for information about an ACE entity mention
   */
  private static class ACEEntity implements Comparable<ACEEntity> {

    public String type;
    public int start;
    public int end;

    ACEEntity(String type, int start, int end) {
      this.type = normalizeType(type);
      this.start = start;
      this.end = end;
    }

    // normalizes the type names to match our entity types
    private static String normalizeType(String type) {
      if (type.startsWith("PER")) {
        return "PERSON";
      } else if (type.startsWith("FAC")) {
        return "FAC";
      } else if (type.startsWith("ORG")) {
        return "ORGANIZATION";
      } else if (type.startsWith("LOC")) {
        return "LOCATION";
      }
      return type;
    }

    public boolean overlaps(ACEEntity other) {
      return ((start >= other.start && start <= other.end) || (end >= other.end && end <= other.end));
    }

    public int length() {
      return end - start;
    }

    @Override
    public String toString() {
      return type + " (" + start + '-' + end + ')';
    }

    public int compareTo(ACEEntity other) {
      return other.start - this.start;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if ( ! (o instanceof ACEEntity)) {
        return false;
      }
      ACEEntity aceEntity = (ACEEntity) o;

      if (end != aceEntity.end) return false;
      if (start != aceEntity.start) return false;
      if (type != null) {
        return type.equals(aceEntity.type);
      } else {
        return aceEntity.type == null;
      }
    }

    @Override
    public int hashCode() {
      int result;
      result = (type != null ? type.hashCode() : 0);
      result = 31 * result + start;
      result = 31 * result + end;
      return result;
    }

    public String startTag() {
      return '<' + type + '>';
    }

    public String endTag() {
      return "</" + type + '>';
    }

  } // end static class ACEEntity


  private static class ACEHandler extends DefaultHandler {
    String text;
    boolean saveCharacters;

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (saveCharacters) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = start, end = start + length; i < end; i++) {
          sb.append(ch[i]);
        }
        text = sb.toString();
      }
    }
  }

  private static class ACEEntityHandler extends ACEHandler {
    private static final String ENTITY_TYPE = "entity_type";
    private static final String ENTITY_MENTION = "entity_mention";
    private static final String TYPE = "TYPE";
    private static final String NAME = "NAME";
    private static final String START = "start";
    private static final String END = "end";

    private List<ACEEntity> entities = Generics.newArrayList();
    private String entityType;
    boolean keepMention;

    int start;
    int end;

    public ACEEntityHandler() {
      super();
      reset();
    }

    public List<ACEEntity> entities() {
      return entities;
    }

    public void reset() {
      entities = Generics.newArrayList();
    }

    /**
     * Checks the list of entities found thus far for overlapping entities.  If two entities overlap, keeps
     * the shorter of the two.
     */
    private void addEntity(String type, int start, int end) {
      ACEEntity newEntity = new ACEEntity(type, start, end);
      if (end < start) {
        System.err.println("Bad entity indices: " + newEntity);
        return;
      }
      // iterate backwards because we might be removing some entities
      for (int i = entities.size() - 1; i >= 0; i--) {
        ACEEntity entity = entities.get(i);
        if (newEntity.overlaps(entity)) {
          if (newEntity.length() < entity.length()) {
            // found a shorter entity that covers these indices -> remove the old one
            entities.remove(i);
          } else {
            // existing entity is shorter -> return without adding
            return;
          }
        }
      }
      // made it here, so this is the shortest entity covering these indices -> add to list
      entities.add(newEntity);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (ENTITY_TYPE.equals(qName)) {
        saveCharacters = true;
      } else if (ENTITY_MENTION.equals(qName) && NAME.equals(attributes.getValue(TYPE))) {
        keepMention = true;
      } else if (keepMention && (START.equals(qName)) || END.equals(qName)) {
        saveCharacters = true;
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if (ENTITY_TYPE.equals(qName)) {
        entityType = text;
        saveCharacters = false;
      } else if (ENTITY_MENTION.equals(qName)) {
        keepMention = false;
      } else if (keepMention && START.equals(qName)) {
        start = Integer.parseInt(text);
      } else if (keepMention && END.equals(qName)) {
        end = Integer.parseInt(text);
        addEntity(entityType, start, end);
      }
    }
  }

  private static class ACETextHandler extends ACEHandler {
    private static final String TURN = "turn";
    private static final String TEXT = "TEXT";
    private StringBuffer sb;
    int offset; // offset before start of "real" text
    boolean started;

    ACETextHandler() {
      super();
      reset();
    }

    public void reset() {
      sb = new StringBuffer();
      offset = 0;
      started = false;
    }

    public String text() {
      return sb.toString();
    }

    public int offset() {
      return offset;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (TEXT.equals(qName) || TURN.equals(qName)) {
        saveCharacters = true;
      }
      if (saveCharacters && text != null) {
        sb.append(text); // pick up stuff broken up by <time> tags
        text = null;
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if (TEXT.equals(qName) || TURN.equals(qName)) {
        if (text != null) {
          sb.append(text);
        }
        sb.append('\n'); // the entity indices count an extra character for the closing </turn>
        saveCharacters = false;
        text = null;
      }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (saveCharacters) {
        if (text != null) {
          // long text blocks are broken up for some reason
          // (try ACE-phase2/train/bnews/9801.172.sgm without this)
          // so make sure they are picked up here
          sb.append(text);
        }
        super.characters(ch, start, length);
        started = true;
      } else if (!started) {
        offset += length;
      }
    }
  }

  /**
   * Usage: java ACE2Columns dir1 ...
   * <p/>
   * where <code>dir*</code> are directories containing pairs of .sgm and
   * .sgm.tmx.rdc.xml files
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: java ACE2Columns dir dir2 ...");
      System.exit(1);
    }

    ACEEntityHandler entityHandler = new ACEEntityHandler();
    ACETextHandler textHandler = new ACETextHandler();
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.getXMLReader().setFeature("http://xml.org/sax/features/validation", false);

      for (String arg : args) {
        File dir = new File(arg);
        File[] files = dir.listFiles();
        Arrays.sort(files, new Comparator<File>() {
          public int compare(final File o1, final File o2) {
            return (o1).getName().compareTo((o2).getName());
          }

        });
        for (int j = 0; j < files.length; j++) {
          File sgmFile = files[j];
          if (!sgmFile.getName().endsWith(".sgm")) {
            continue;
          }
          int index = sgmFile.getName().lastIndexOf(".sgm");
          String prefix = sgmFile.getName().substring(0, index);
          File xmlFile = null;
          // look for the entity file one file previous
          if (j > 0) {
            xmlFile = files[j - 1];
            if (!xmlFile.getName().startsWith(prefix)) {
              xmlFile = null;
            }
          }
          // else try one file following
          if (xmlFile == null) {
            j++;
            xmlFile = files[j];
            if (!xmlFile.getName().startsWith(prefix)) {
              xmlFile = null;
            }
          }
          if (xmlFile == null) {
            System.err.println("Could not find entity file for " + sgmFile.getName());
            continue;
          }

          System.err.println("Parsing " + sgmFile.getName());
          parser.parse(sgmFile, textHandler);
          String text = textHandler.text();
          // unescape XML characters
          text = text.replaceAll("&amp;", "&");

          parser.parse(xmlFile, entityHandler);
          List<ACEEntity> entities = entityHandler.entities();
          // sort the entries in reverse order
          Collections.sort(entities);

          // wrap text in tags
          StringBuilder sb = new StringBuilder(text);
          int offset = textHandler.offset();
          for (ACEEntity entity : entities) {
            if (entity.start < 0 || entity.end - offset >= sb.length()) {
              System.err.println("Warning (Out of Bounds): " + entity);
              continue;
            }
            sb.insert(entity.end - offset + 1, entity.endTag());
            sb.insert(entity.start - offset, entity.startTag());
          }

          PTBTokenizer<Word> tokenizer = PTBTokenizer.newPTBTokenizer(new StringReader(sb.toString()));
          String tag = "O";
          while (tokenizer.hasNext()) {
            String token = tokenizer.next().word();
            if (token.startsWith("</")) {
              tag = "O";
            } else if (token.startsWith("<") && token.endsWith(">")) {
              tag = token.substring(1, token.length() - 1);
            } else {
              // remove whitespace from words like telephone numbers, ellipses, etc.
              token = token.replaceAll("\\s", "");
              System.out.println(token + ' ' + tag);
            }
          }
          System.out.println();
          entityHandler.reset();
          textHandler.reset();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

