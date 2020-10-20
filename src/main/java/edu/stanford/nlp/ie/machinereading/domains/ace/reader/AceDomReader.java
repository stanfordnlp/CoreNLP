
package edu.stanford.nlp.ie.machinereading.domains.ace.reader; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.ie.machinereading.common.DomReader;

/**
 * DOM reader for an ACE specification.
 *
 * @author David McClosky
 */
public class AceDomReader extends DomReader  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AceDomReader.class);

  private static AceCharSeq parseCharSeq(Node node) {
    Node child = getChildByName(node, "charseq");
    String start = getAttributeValue(child, "START");
    String end = getAttributeValue(child, "END");
    String text = child.getFirstChild().getNodeValue();
    return new AceCharSeq(text,
			  Integer.parseInt(start),
			  Integer.parseInt(end));
  }

  /**
   * Extracts one entity mention
   */
  private static AceEntityMention parseEntityMention(Node node) {
    String id = getAttributeValue(node, "ID");
    String type = getAttributeValue(node, "TYPE");
    String ldctype = getAttributeValue(node, "LDCTYPE");
    AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
    AceCharSeq head = parseCharSeq(getChildByName(node, "head"));
    return (new AceEntityMention(id, type, ldctype, extent, head));
  }

  /**
   * Extracts info about one relation mention
   */
  private static AceRelationMention parseRelationMention(Node node,
							 AceDocument doc) {
    String id = getAttributeValue(node, "ID");
    AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
    String lc = getAttributeValue(node, "LEXICALCONDITION");

    // create the mention
    AceRelationMention mention = new AceRelationMention(id, extent, lc);

    // find the mention args
    List<Node> args = getChildrenByName(node, "relation_mention_argument");
    for(Node arg: args){
      String role = getAttributeValue(arg, "ROLE");
      String refid = getAttributeValue(arg, "REFID");
      AceEntityMention am = doc.getEntityMention(refid);

      if(am != null){
      	am.addRelationMention(mention);
      	if(role.equalsIgnoreCase("arg-1")){
      		mention.getArgs()[0] = new AceRelationMentionArgument(role, am);
      	} else if(role.equalsIgnoreCase("arg-2")){
      		mention.getArgs()[1] = new AceRelationMentionArgument(role, am);
      	} else {
      		throw new RuntimeException("Invalid relation mention argument role: " + role);
      	}
      }
    }

    return mention;
  }

  /**
   * Extracts info about one relation mention
   */
  private static AceEventMention parseEventMention(Node node,
               AceDocument doc) {
    String id = getAttributeValue(node, "ID");
    AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
    AceCharSeq anchor = parseCharSeq(getChildByName(node, "anchor"));

    // create the mention
    AceEventMention mention = new AceEventMention(id, extent, anchor);

    // find the mention args
    List<Node> args = getChildrenByName(node, "event_mention_argument");
    for (Node arg : args) {
      String role = getAttributeValue(arg, "ROLE");
      String refid = getAttributeValue(arg, "REFID");
      AceEntityMention am = doc.getEntityMention(refid);

      if(am != null){
        am.addEventMention(mention);
        mention.addArg(am, role);
      }
    }

    return mention;
  }

  /**
   * Parses one ACE specification
   * @return Simply displays the events to stdout
   */
  public static AceDocument parseDocument(File f)
    throws IOException, SAXException, ParserConfigurationException {

    // parse the Dom document
    Document document = readDocument(f);

    //
    // create the ACE document object
    //
    Node docElement = document.getElementsByTagName("document").item(0);
    AceDocument aceDoc =
      new AceDocument(getAttributeValue(docElement, "DOCID"));

    //
    // read all entities
    //
    NodeList entities = document.getElementsByTagName("entity");
    int entityCount = 0;
    for(int i = 0; i < entities.getLength(); i ++){
      Node node = entities.item(i);

      //
      // the entity type and subtype
      //
      String id = getAttributeValue(node, "ID");
      String type = getAttributeValue(node, "TYPE");
      String subtype = getAttributeValue(node, "SUBTYPE");
      String cls = getAttributeValue(node, "CLASS");

      // create the entity
      AceEntity entity = new AceEntity(id, type, subtype, cls);
      aceDoc.addEntity(entity);

      // fetch all mentions of this event
      List<Node> mentions = getChildrenByName(node, "entity_mention");

      // parse all its mentions
      for (Node mention1 : mentions) {
        AceEntityMention mention = parseEntityMention(mention1);
        entity.addMention(mention);
        aceDoc.addEntityMention(mention);
      }

      entityCount++;
    }
    //log.info("Parsed " + entityCount + " XML entities.");

    //
    // read all relations
    //
    NodeList relations = document.getElementsByTagName("relation");
    for(int i = 0; i < relations.getLength(); i ++){
      Node node = relations.item(i);

      //
      // the relation type, subtype, tense, and modality
      //
      String id = getAttributeValue(node, "ID");
      String type = getAttributeValue(node, "TYPE");
      String subtype = getAttributeValue(node, "SUBTYPE");
      String modality = getAttributeValue(node, "MODALITY");
      String tense = getAttributeValue(node, "TENSE");

      // create the relation
      AceRelation relation = new AceRelation(id, type, subtype,
					     modality, tense);
      aceDoc.addRelation(relation);

      // XXX: fetch relation_arguments here!

      // fetch all mentions of this relation
      List<Node> mentions = getChildrenByName(node, "relation_mention");

      // traverse all mentions
      for (Node mention1 : mentions) {
        AceRelationMention mention = parseRelationMention(mention1, aceDoc);
        relation.addMention(mention);
        aceDoc.addRelationMention(mention);
      }
    }

    //
    // read all events
    //
    NodeList events = document.getElementsByTagName("event");
    for(int i = 0; i < events.getLength(); i ++){
      Node node = events.item(i);

      //
      // the event type, subtype, tense, and modality
      //
      String id = getAttributeValue(node, "ID");
      String type = getAttributeValue(node, "TYPE");
      String subtype = getAttributeValue(node, "SUBTYPE");
      String modality = getAttributeValue(node, "MODALITY");
      String polarity = getAttributeValue(node, "POLARITY");
      String genericity = getAttributeValue(node, "GENERICITY");
      String tense = getAttributeValue(node, "TENSE");

      // create the event
      AceEvent event = new AceEvent(id, type, subtype,
               modality, polarity, genericity, tense);
      aceDoc.addEvent(event);

      // fetch all mentions of this relation
      List<Node> mentions = getChildrenByName(node, "event_mention");

      // traverse all mentions
      for (Node mention1 : mentions) {
        AceEventMention mention = parseEventMention(mention1, aceDoc);
        event.addMention(mention);
        aceDoc.addEventMention(mention);
      }
    }

    return aceDoc;
  }

  public static void main(String [] argv) throws Exception {
    if (argv.length != 1) {
      log.info("Usage: java AceDomReader <APF file>");
      System.exit(1);
    }

    File f = new File(argv[0]);
    AceDocument doc = parseDocument(f);
    System.out.println("Processed ACE document:\n" + doc);
    ArrayList<ArrayList<AceRelationMention>> r = doc.getAllRelationMentions();
    System.out.println("size: " + r.size());
  }
}
