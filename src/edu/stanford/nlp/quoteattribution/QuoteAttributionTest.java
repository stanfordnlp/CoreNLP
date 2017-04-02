package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;


import java.io.*;
import java.util.*;

/**
 * @author Michael Fang, Grace Muzny
 */
public class QuoteAttributionTest {

  // TODO: make this into a unit test

  public static final String test = " THE next day opened a new scene at Longbourn. Mr. Collins made his declaration in form. Having resolved to do it without loss of time, as his leave of absence extended only to the following Saturday, and having no feelings of diffidence to make it distressing to himself even at the moment, he set about it in a very orderly manner, with all the observances which he supposed a regular part of the business.\n" +
          " On finding Mrs. Bennet, Elizabeth, and one of the younger girls together soon after breakfast, he addressed the mother in these words, \"May I hope, Madam, for your interest with your fair daughter Elizabeth, when I solicit for the honour of a private audience with her in the course of this morning?\"\n" +
          " Before Elizabeth had time for any thing but a blush of surprise, Mrs. Bennet instantly answered, \"Oh dear! -- Yes -- certainly. -- I am sure Lizzy will be very happy -- I am sure she can have no objection. -- Come, Kitty, I want you up stairs.\"\n" +
          " And gathering her work together, she was hastening away, when Elizabeth called out, \"Dear Ma'am, do not go. -- I beg you will not go. -- Mr. Collins must excuse me. -- He can have nothing to say to me that any body need not hear.  I am going away myself.\"\n" +
          " \"No, no, nonsense, Lizzy. -- I desire you will stay where you are.\" -- And upon Elizabeth's seeming really, with vexed and embarrassed looks, about to escape, she added, \"Lizzy, I insist upon your staying and hearing Mr. Collins.\"\n" +
          " Elizabeth would not oppose such an injunction -- and a moment's consideration making her also sensible that it would be wisest to get it over as soon and as quietly as possible, she sat down again, and tried to conceal by incessant employment the feelings which were divided between distress and diversion. Mrs. Bennet and Kitty walked off, and as soon as they were gone Mr. Collins began.\n" +
          " \"Believe me, my dear Miss Elizabeth, that your modesty, so far from doing you any disservice, rather adds to your other perfections.  You would have been less amiable in my eyes had there not been this little unwillingness; but allow me to assure you that I have your respected mother's permission for this address.  You can hardly doubt the purport of my discourse, however your natural delicacy may lead you to dissemble; my attentions have been too marked to be mistaken. Almost as soon as I entered the house I singled you out as the companion of my future life.  But before I am run away with by my feelings on this subject, perhaps it will be advisable for me to state my reasons for marrying -- and moreover for coming into Hertfordshire with the design of selecting a wife, as I certainly did.\"\n" +
          " The idea of Mr. Collins, with all his solemn composure, being run away with by his feelings, made Elizabeth so near laughing that she could not use the short pause he allowed in any attempt to stop him farther, and he continued: \"My reasons for marrying are, first, that I think it a right thing for every clergyman in easy circumstances (like myself) to set the example of matrimony in his parish.  Secondly, that I am convinced it will add very greatly to my happiness; and thirdly -- which perhaps I ought to have mentioned earlier -- that it is the particular advice and recommendation of the very noble lady whom I have the honour of calling patroness.  Twice has she condescended to give me her opinion (unasked too!) on this subject; and it was but the very Saturday night before I left Hunsford -- between our pools at quadrille, while Mrs. Jenkinson was arranging Miss de Bourgh's foot-stool, that she said, 'Mr. Collins, you must marry.  A clergyman like you must marry. -- Chuse properly, chuse a gentlewoman for my sake; and for your own, let her be an active, useful sort of person, not brought up high, but able to make a small income go a good way.  This is my advice.  Find such a woman as soon as you can, bring her to Hunsford, and I will visit her.'  Allow me, by the way, to observe, my fair cousin, that I do not reckon the notice and kindness of Lady Catherine de Bourgh as among the least of the advantages in my power to offer.  You will find her manners beyond any thing I can describe; and your wit and vivacity I think must be acceptable to her, especially when tempered with the silence and respect which her rank will inevitably excite.  Thus much for my general intention in favour of matrimony; it remains to be told why my views were directed to Longbourn instead of my own neighbourhood, where I assure you there are many amiable young women.  But the fact is, that being, as I am, to inherit this estate after the death of your honoured father (who, however, may live many years longer), I could not satisfy myself without resolving to chuse a wife from among his daughters, that the loss to them might be as little as possible, when the melancholy event takes place -- which, however, as I have already said, may not be for several years.  This has been my motive, my fair cousin, and I flatter myself it will not sink me in your esteem.  And now nothing remains for me but to assure you in the most animated language of the violence of my affection.  To fortune I am perfectly indifferent, and shall make no demand of that nature on your father, since I am well aware that it could not be complied with; and that one thousand pounds in the 4 per cents, which will not be yours till after your mother's decease, is all that you may ever be entitled to.  On that head, therefore, I shall be uniformly silent; and you may assure yourself that no ungenerous reproach shall ever pass my lips when we are married.\"\n" +
          " It was absolutely necessary to interrupt him now.\n" +
          " \"You are too hasty, Sir,\" she cried.  \"You forget that I have made no answer.  Let me do it without farther loss of time.  Accept my thanks for the compliment you are paying me, I am very sensible of the honour of your proposals, but it is impossible for me to do otherwise than decline them.\"\n" +
          " \"I am not now to learn,\" replied Mr. Collins, with a formal wave of the hand, \"that it is usual with young ladies to reject the addresses of the man whom they secretly mean to accept, when he first applies for their favour; and that sometimes the refusal is repeated a second or even a third time.  I am therefore by no means discouraged by what you have just said, and shall hope to lead you to the altar ere long.\"\n" +
          " \"Upon my word, Sir,\" cried Elizabeth, \"your hope is rather an extraordinary one after my declaration.  I do assure you that I am not one of those young ladies (if such young ladies there are) who are so daring as to risk their happiness on the chance of being asked a second time.  I am perfectly serious in my refusal. -- You could not make me happy, and I am convinced that I am the last woman in the world who would make you so, -- Nay, were your friend Lady Catherine to know me, I am persuaded she would find me in every respect ill qualified for the situation.\"\n" +
          " \"Were it certain that Lady Catherine would think so,\" said Mr. Collins very gravely -- \"but I cannot imagine that her ladyship would at all disapprove of you.  And you may be certain that when I have the honour of seeing her again I shall speak in the highest terms of your modesty, economy, and other amiable qualifications.\"\n" +
          " \"Indeed, Mr. Collins, all praise of me will be unnecessary. You must give me leave to judge for myself, and pay me the compliment of believing what I say. I wish you very happy and very rich, and by refusing your hand, do all in my power to prevent your being otherwise. In making me the offer, you must have satisfied the delicacy of your feelings with regard to my family, and may take possession of Longbourn estate whenever it falls, without any self-reproach. This matter may be considered, therefore, as finally settled.\"\n" +
          " And rising as she thus spoke, she would have quitted the room, had not Mr. Collins thus addressed her, \"When I do myself the honour of speaking to you next on this subject I shall hope to receive a more favourable answer than you have now given me; though I am far from accusing you of cruelty at present, because I know it to be the established custom of your sex to reject a man on the first application, and perhaps you have even now said as much to encourage my suit as would be consistent with the true delicacy of the female character.\"\n" +
          " \"Really, Mr. Collins,\" cried Elizabeth with some warmth, \"you puzzle me exceedingly.  If what I have hitherto said can appear to you in the form of encouragement, I know not how to express my refusal in such a way as may convince you of its being one.\"\n" +
          " \"You must give me leave to flatter myself, my dear cousin, that your refusal of my addresses is merely words of course. My reasons for believing it are briefly these: -- It does not appear to me that my hand is unworthy your acceptance, or that the establishment I can offer would be any other than highly desirable.  My situation in life, my connections with the family of De Bourgh, and my relationship to your own, are circumstances highly in its favor; and you should take it into farther consideration that in spite of your manifold attractions, it is by no means certain that another offer of marriage may ever be made you.  Your portion is unhappily so small that it will in all likelihood undo the effects of your loveliness and amiable qualifications.  As I must therefore conclude that you are not serious in your rejection of me, I shall chuse to attribute it to your wish of increasing my love by suspense, according to the usual practice of elegant females.\"\n" +
          " \"I do assure you, Sir, that I have no pretension whatever to that kind of elegance which consists in tormenting a respectable man.  I would rather be paid the compliment of being believed sincere.  I thank you again and again for the honour you have done me in your proposals, but to accept them is absolutely impossible.  My feelings in every respect forbid it.  Can I speak plainer?  Do not consider me now as an elegant female intending to plague you, but as a rational creature speaking the truth from her heart.\"\n" +
          " \"You are uniformly charming!\" cried he, with an air of awkward gallantry; \"and I am persuaded that when sanctioned by the express authority of both your excellent parents, my proposals will not fail of being acceptable.\"\n" +
          " To such perseverance in wilful self-deception, Elizabeth would make no reply, and immediately and in silence withdrew; determined, that if he persisted in considering her repeated refusals as flattering encouragement, to apply to her father, whose negative might be uttered in such a manner as must be decisive, and whose behaviour at least could not be mistaken for the affectation and coquetry of an elegant female.";

  //Test QuoteAttributionAnnotator on a chapter of PP.
  public static void testPP(String familyFile, String animateFile, String genderFile,
                            String charactersFile, String modelFile) throws IOException, ClassNotFoundException {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, depparse, quote, quoteattribution");
    props.setProperty("quoteattribution.familyWordsFile", familyFile);
    props.setProperty("quoteattribution.animacyWordsFile", animateFile);
    props.setProperty("quoteattribution.genderNamesFile", genderFile);

    props.setProperty("quoteattribution.charactersPath", charactersFile);
    props.setProperty("quoteattribution.modelPath", modelFile);
    
    StanfordCoreNLP coreNLP = new StanfordCoreNLP(props);
    Annotation processedAnnotation = coreNLP.process(test);

    List<CoreMap> quotes = processedAnnotation.get(CoreAnnotations.QuotationsAnnotation.class);
    for(CoreMap quote : quotes) {
      System.out.println("Quote: " + quote.get(CoreAnnotations.TextAnnotation.class));
      if(quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        System.out.println("Predicted Mention: " + quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) + " Predictor: " + quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class));
      } else {
        System.out.println("Predicted Mention: none");
      }
      if(quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) != null) {
        System.out.println("Predicted Speaker: " + quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) + " Predictor: " + quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class));
      } else {
        System.out.println("Predicted Speaker: none");
      }
      System.out.println("====");
    }
    System.out.println("Finished");
  }

  /**
   * Usage: java QuoteAttributionTest familywordsfile animatefile gendernamesfile charactersfile modelfile
   * @param args
   * @throws IOException
   * @throws ClassNotFoundException
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    if (args.length != 5) {
      System.out.println("Usage: java QuoteAttributionTest familywordsfile animatefile gendernamesfile charactersfile modelfile");
      System.exit(1);
    }
    String familyFile = args[0];
    String animateFile = args[1];
    String genderFile = args[2];
    String charactersFile = args[3];
    String modelFile = args[4];
    testPP(familyFile, animateFile, genderFile, charactersFile, modelFile);
  }
}
