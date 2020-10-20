package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.QuoteAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * Created by mjfang on 12/18/16.
 */
public class XMLToAnnotation {

  public static String getJustText(Node text)
  {
    StringBuilder sb = new StringBuilder();
    NodeList textElems = text.getChildNodes();
    for(int i = 0; i < textElems.getLength(); i++)
    {
      Node child = textElems.item(i);
      String str = child.getTextContent();

      //replace single occurrence of \n with " ", double occurrences with a single one.
      str = str.replaceAll("\n(?!\n)", " ");
      str = str.replaceAll("_", ""); //bug fix for sentence splitting
      sb.append(str + " ");
    }

    return sb.toString();
  }

  //for standard annotations + quotes
  public static Properties getProcessedCoreNLPProperties()
  {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, depparse, quote");
    props.setProperty("ner.useSUTime","false");
    props.setProperty("ner.applyNumericClassifiers","false");
    props.setProperty("ssplit.newlineIsSentenceBreak","always");
    props.setProperty("outputFormat","serialized");
    props.setProperty("serializer","edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer");
    props.setProperty("threads", "1");
    return props;
  }

  public static void processCoreNLPIfDoesNotExist(File processedFile, Properties coreNLPProps, String text) {
    if (!processedFile.exists()) {
      try {
        StanfordCoreNLP coreNLP = new StanfordCoreNLP(coreNLPProps);
        Annotation processedAnnotation = coreNLP.process(text); //this document holds the split for paragraphs.
        ProtobufAnnotationSerializer pas = new ProtobufAnnotationSerializer(true);
        OutputStream fos = new BufferedOutputStream(new FileOutputStream(processedFile.getAbsolutePath()));
        pas.write(processedAnnotation, fos);

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static Annotation getAnnotatedFile(String text, String baseFileName, Properties props) throws IOException{

    File processedFile = new File(baseFileName + ".ser.gz");
    processCoreNLPIfDoesNotExist(processedFile, props, text);
    Annotation doc = ExtractQuotesUtil.readSerializedProtobufFile(processedFile);
    new QuoteAnnotator(new Properties()).annotate(doc); //important! Re-annotate to take into account that certain tokens are removed in the serialization process.
    return doc;
  }

  public static List<Integer> readConnection(String connection) {
    List<Integer> connectionList = new ArrayList<>();
    if(connection.equals("")) {
      return connectionList;
    }
    String[] connections = connection.split(",");
    for(String c : connections) {
      connectionList.add(Integer.parseInt(c.substring(1)));
    }
    return connectionList;
  }
  //return index of the token that ends this block of text.
  //key assumption: blocks are delimited by tokens (i.e. no token spans two blocks.)
  public static int getEndIndex(int startIndex, List<CoreLabel> tokens, String text)
  {
    text = text.trim(); //remove newlines that may throw off text length
    int currIndex = startIndex;
    CoreLabel token = tokens.get(startIndex);
    int tokenBeginChar = token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int offset = text.indexOf(token.get(CoreAnnotations.OriginalTextAnnotation.class));
    while(true) {
      int tokenEndChar = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      if(tokenEndChar - tokenBeginChar == text.length()) {
        return currIndex;
      }
      else if(tokenEndChar - tokenBeginChar > text.length()) {
        return currIndex - 1;
      }
      currIndex++;
      if(currIndex == tokens.size()) {
        return currIndex - 1;
      }
      token = tokens.get(currIndex);
    }
  }

  public static class GoldQuoteInfo {

    public int mentionStartTokenIndex, mentionEndTokenIndex;
    public String speaker, mention;

    public GoldQuoteInfo(int mentionStartTokenIndex, int mentionEndTokenIndex, String speaker, String mention) {
      this.mentionStartTokenIndex = mentionStartTokenIndex;
      this.mentionEndTokenIndex = mentionEndTokenIndex;
      this.speaker = speaker;
      this.mention = mention;
    }
  }

  public static class Data {
    public List<GoldQuoteInfo> goldList; //the gold values (mention location and speaker name) of the quotes
    public List<Person> personList;
    public Annotation doc;

    public Data(List<GoldQuoteInfo> goldList, List<Person> personList, Annotation doc) {
      this.goldList = goldList;
      this.personList = personList;
      this.doc = doc;
    }
  }

  public static List<Person> readXMLCharacterList(Document doc) {
    List<Person> personList = new ArrayList<>();
    NodeList characters = doc.getDocumentElement().getElementsByTagName("characters").item(0).getChildNodes();
    for(int i = 0; i < characters.getLength(); i++)
    {
      Node child = characters.item(i);
      if(child.getNodeName().equals("character")) {
        String name = child.getAttributes().getNamedItem("name").getNodeValue();
        char[] cName = name.toCharArray();
        cName[0] = Character.toUpperCase(cName[0]);
        name = new String(cName);
        List<String> aliases = Arrays.asList(child.getAttributes().getNamedItem("aliases").getNodeValue().split(";"));
        String gender = (child.getAttributes().getNamedItem("gender") == null) ? "" : child.getAttributes().getNamedItem("gender").getNodeValue();
        personList.add(new Person(child.getAttributes().getNamedItem("name").getNodeValue(), gender, aliases));
      }
    }
    return personList;
  }
  //write the character list to a file to work with the annotator
  public static void writeCharacterList(String fileName, List<Person> personList) throws IOException {
    StringBuilder text = new StringBuilder();
    for(Person p : personList) {

      String gender = "";
      switch (p.gender) {
        case MALE: gender = "M";
          break;
        case FEMALE: gender = "F";
          break;
        case UNK: gender = "";
          break;
      }
      text.append(p.name + ";" + gender);
      for (String alias : p.aliases) {
        text.append(";" + alias);
      }
      text.append("\n");
    }
    PrintWriter pw = IOUtils.getPrintWriter(fileName);
    pw.print(text);
    pw.close();
  }

  protected static class Mention {
    String text;
    int begin, end;

    public Mention(String text, int begin, int end) {
      this.text = text;
      this.begin = begin;
      this.end = end;
    }
  }

  public static Data readXMLFormat(String fileName) throws Exception {
    //Extract character list, gold quote speaker and mention information from the XML document.
    Document doc = XMLUtils.readDocumentFromFile(fileName);
    Node text = doc.getDocumentElement().getElementsByTagName("text").item(0);
    String docText = getJustText(text);
    Annotation document = getAnnotatedFile(docText, fileName, getProcessedCoreNLPProperties());
    List<CoreMap> quotes = document.get(CoreAnnotations.QuotationsAnnotation.class);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    List<GoldQuoteInfo> goldList = new ArrayList<>();
    Map<Integer, Mention> idToMention = new HashMap<>();
    List<Person> personList = readXMLCharacterList(doc);
    Map<String, List<Person>> personMap = QuoteAttributionUtils.readPersonMap(personList);
    List<Pair<Integer, String>> mentionIdToSpeakerList = new ArrayList<>();


    //there is at least 1 case in which the XML quote does not match up with the automatically-extracted quote. (Ex: quote by Mr. Collins that begins, "Hunsford, near Westerham, Kent, ...")
    //as the dirty solution, we treat all quotes encapsulated within an XML quote as the same speaker (although this is not 100% accurate!)
    int quoteIndex = 0;
    NodeList textElems = text.getChildNodes();
    int tokenIndex = 0;
    for(int i = 0; i < textElems.getLength(); i++) {
      Node chapterNode = textElems.item(i);
      if(chapterNode.getNodeName().equals("chapter")) {
        NodeList chapElems = chapterNode.getChildNodes();
        for (int j = 0; j < chapElems.getLength(); j++) {
          Node child = chapElems.item(j);
          if (child.getNodeName().equals("quote")) {

            //search for nested mentions
            NodeList quoteChildren = child.getChildNodes();
            for(int k = 0; k < quoteChildren.getLength(); k++)
            {
              Node quoteChild = quoteChildren.item(k);
              if(quoteChild.getNodeName().equals("mention"))
              {
                String mentionText = quoteChild.getTextContent();
                int id = Integer.parseInt(quoteChild.getAttributes().getNamedItem("id").getTextContent().substring(1));
                List<Integer> connections = readConnection(quoteChild.getAttributes().getNamedItem("connection").getNodeValue());
                int endIndex = getEndIndex(tokenIndex, tokens, mentionText);
//                mentions.put(id, new XMLMention(quoteChild.getTextContent(), tokenIndex, endIndex, id, connections));
                idToMention.put(id, new Mention(mentionText, tokenIndex, endIndex));
                tokenIndex = endIndex + 1;
              }
              else{
                String quoteText = quoteChild.getTextContent();
                quoteText = quoteText.replaceAll("\n(?!\n)", " "); //trim unnecessarily newlines
                quoteText = quoteText.replaceAll("_", "");
                tokenIndex = getEndIndex(tokenIndex, tokens, quoteText) + 1;
              }
            }

            String quoteText = child.getTextContent();
//              tokenIndex = getEndIndex(tokenIndex, tokens, quoteText) + 1;
            quoteText = quoteText.replaceAll("\n(?!\n)", " "); //trim unnecessarily newlines
            quoteText = quoteText.replaceAll("_", "");
            int quotationOffset = 1;
            if (quoteText.startsWith("``"))
              quotationOffset = 2;

            List<Integer> connections = readConnection(child.getAttributes().getNamedItem("connection").getTextContent());
            int id = Integer.parseInt(child.getAttributes().getNamedItem("id").getTextContent().substring(1));
            Integer mention_id = null;
            if (connections.size() > 0)
              mention_id = connections.get(0);
            else {
              System.out.println("quote w/ no mention. ID: " + id);
            }
//            Pair<Integer, Integer> mentionPair = idToMentionPair.get(mention_id);
            mentionIdToSpeakerList.add(new Pair<>(mention_id, child.getAttributes().getNamedItem("speaker").getTextContent()));
            String annotatedQuoteText = quotes.get(quoteIndex).get(CoreAnnotations.TextAnnotation.class);
            while(!quoteText.endsWith(annotatedQuoteText)) {
              quoteIndex++;
              annotatedQuoteText = quotes.get(quoteIndex).get(CoreAnnotations.TextAnnotation.class);
              mentionIdToSpeakerList.add(new Pair<>(mention_id, child.getAttributes().getNamedItem("speaker").getTextContent()));
            }
//            idToMentionPair.put(id, new Pair<>(-1, -1));
//            imention_id = connections.get(0);
//              quotes.add(new XMLQuote(quoteText.substring(quotationOffset, quoteText.length() - quotationOffset), child.getAttributes().getNamedItem("speaker").getTextContent(), id, chapterIndex, mention_id));
            quoteIndex++;
          } else if (child.getNodeName().equals("mention")) {
            String mentionText = child.getTextContent();
            int id = Integer.parseInt(child.getAttributes().getNamedItem("id").getTextContent().substring(1));
            List<Integer> connections = readConnection(child.getAttributes().getNamedItem("connection").getNodeValue());
            int endIndex = getEndIndex(tokenIndex, tokens, mentionText);
            idToMention.put(id, new Mention(mentionText, tokenIndex, endIndex));
//              mentions.put(id, new XMLMention(child.getTextContent(), tokenIndex, endIndex, id, connections));
            tokenIndex = endIndex + 1;
          } else {//#text
            String nodeText = child.getTextContent();
            nodeText = nodeText.replaceAll("\n(?!\n)", " ");
            nodeText = nodeText.replaceAll("_", "");
            if(tokenIndex >= tokens.size()) {
              continue;
            }
            tokenIndex = getEndIndex(tokenIndex, tokens, nodeText) + 1;
          }
        }
      }
    }
    for(Pair<Integer, String> item : mentionIdToSpeakerList) {
      Mention mention = idToMention.get(item.first);
      if(mention == null) {
        goldList.add(new GoldQuoteInfo(-1, -1, item.second, null));
      } else {
        goldList.add(new GoldQuoteInfo(mention.begin, mention.end, item.second, mention.text));
      }

    }

    //verify
    if(document.get(CoreAnnotations.QuotationsAnnotation.class).size() != goldList.size()) {
      throw new RuntimeException("Quotes size and gold size don't match!");
    }

    return new Data(goldList, personList, document);
  }
}
