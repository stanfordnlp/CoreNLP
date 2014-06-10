package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.tokensregex.PhraseTable;
import edu.stanford.nlp.util.CollectionUtils;
import junit.framework.TestCase;

import java.util.*;

/**
 * Test phrase table
 *
 * @author Angel Chang
 */
public class PhraseTableTest extends TestCase {
  String testText = "Peres, Arafat Feel the Heat   Melman, a journalist for the daily  Ha'aretz, specializes in   intelligence and terror affairs.           A few days ago, a crew from Israel's state-run television was secretly  invited to a detention center in the West Bank town of Jericho, now  under the control of Yasser Arafat's Palestinian Authority. They were  guests of Col. Jibril Rajoub, head of the Palestinian security service  in Jericho. To their surprise, they were allowed to interview and film  Muhammad Abu Varda, a member of the Izzidin al-Qassam military wing of  Hamas. In a quiet voice, Abu Varda told his interviewer how he was  instructed by his commander to select suitable candidates for suicide  bombings and send them on their missions.        At the end of the interview, he was asked what Hamas hopes to achieve  by killing innocent people (at least 60 have died in the four latest  Hamas bombings). ``We wanted to create chaos,'' he said, ``which would  generate a political change in Israel. It was our intention to bring  down the Labor government in the elections and crown the right-wing  Likud to power.''        It is difficult to accept Abu Varda's statement at face value. The  interview was conducted after he had been interrogated by Rajoub's  investigators and sentenced to life in prison by a Palestinian  security tribunal. But it is not inconceivable that Hamas, which does  not recognize the right of Israel to exist and conspires to sabotage  the Israeli-Palestinian peace accords, had indeed sought to bring down  the Israeli government. Likud leader Benjamin Netanyahu does not  conceal his intention to stop the negotiations with the Palestinian  Authority if he becomes prime minister, a goal shared by Hamas.        Thus, despite its dubious and tragic circumstances, the interview  shows how closely linked terrorism and Israeli domestic politics are.  Issues of security, terrorism, political stability and personal safety  have always dominated Israel's national agenda, especially during  election campaigns. As such, the fight against terrorism, in many  ways, is damage-control politics.        With his ratings sharply dropping 80 days before important elections,  Prime Minister Shimon Peres eagerly seized the opportunity given to  him by the terrorist's claim. Professing his lack of surprise upon  learning that Hamas wants Likud in power, Peres called upon Israelis  to show responsibility and ``not to allow bloody terrorists to  determine our future.''        The Likud angrily reacted to Peres' remarks. Zeev Benjamin Begin, a  prominent member of parliament and the son of the late Prime Minister  Menachem Begin, charged that the interview with Abu Varda was a joint  venture by ``the two partners, Arafat and Peres'' to influence the  Israeli electorate and ``divert attention'' from the government's  failure to stop terrorism.        What Begin and other opposition leaders refer to as a ``failure'' is a  package of 14 steps, undertaken last week by the  of the strategy is a new structure to fight terrorism, with Gen. Ami  Ayalon, the chief of Shabak, Israel's General Security Service, as its  head. The anti-terrorist center will coordinate the activities of  Israel's military and security establishment, with police, military  intelligence, Mossad, the foreign-espionage agency and Shabak all  represented. But the measures adopted to eradicate terrorism are  hardly new nor have they distinguished themselves by being effective.  Some of them violate basic democratic values.        Since the four suicidal attacks, several villages on the West Bank  still controlled by Israel were put under curfew. These villages are  hotbeds of fundamentalist agitation and recruitment. Ayalon disclosed  that his agents had identified, in one village of 6,000 inhabitants,  more than 40 teen-agers eager to die for the Hamas cause. Several  religious seminars that harbored Hamas activists were shut down.  Dozens of people suspected of belonging to Izzidin al-Qassam were  detained and are now being interrogated by Shabak agents.        Although it has not been confirmed officially, it is understood that  the Peres government authorized Ayalon and his center to use all means  available, including assassination. In the past 20 years, Israel  occasionally has resorted to assassination in its struggle against  Palestinian and Arab terrorism. Following the murder, at the Munich  Olympics in September 1972, of 11 Israeli athletes by Palestine  Liberation Organization terrorists, Israeli Prime Minister Golda Meir  sanctioned the intelligence community to assassinate all involved in  the murders. During the five-year Palestinian ``intifada,'' Israeli  special forces followed a policy of ``shoot on sight'' for suspects  identified by Israeli intelligence as terrorists. Last November,  Mossad agents killed Dr. Fathi Shikaki, leader of the Islamic Jihad,  on Malta. A month later, Shabak agents assassinated Yehiya Ayash,  better-known as the ``engineer,'' by booby-trapping his cellular  phone. Ayash was a senior operative of the Izzidin al-Qassam.        The recent wave of Hamas attacks on Israeli cities is thought to be  the revenge of the ``pupils of the engineer.'' His case, agrees Maj.  Gen. Uri Saguy, reflects the dilemma facing Israeli planners. In 1992,  when Saguy was head of military intelligence, he recommended, at a  Cabinet meeting, the elimination of Sheik Abbas Moussawi, leader of  Hezbollah, a pro-Iranian, Lebanon-based Muslim fundamentalist  organization. In retaliation for Moussawi's death, Hezbollah agents  blew up the Israeli Embassy in Buenos Aires, killing dozens of Israeli  diplomats and passersby. ``I thought then,'' Saguy admits, ``and still  think today that it is right to kill him, despite the heavy price we  paid. How can you fight terrorism without killing its leaders? True,  the liquidation of master terrorists can be very effective but  occasionally counterproductive. The dilemma always exists: Is the  damage you cause to the enemy bigger than his revenge?''        Leation between Israeli security services  and their Palestinian counterparts. Most of the wanted terrorists, the  leaders of Izzidin al-Qassam, have found shelter in the areas under  control of the Palestinian Authority. Since the signing, in September  1993, of the Israeli-PLO agreement, Israeli prime ministers have urged  Arafat to act against Hamas. The Palestinian president, however, was  reluctant. He was and still is afraid that a serious confrontation  with the fundamentalists will turn into a civil war.        But Israeli experts on terrorism now believe that Arafat has no choice  but to act firmly. Indeed, there are signs he has instructed his  security apparatus to crack down on Hamas militants and to strengthen  cooperation with Israel's security services. Rajoub's agents arrested  nearly 300 Hamas members and stormed several religious colleges in  Gaza and on the West Bank. Izzidin al-Qassam and Islamic Jihad were  declared unlawful organizations. But, according to Moshe Shahal, the  Israeli minister for internal security, ``the steps taken by the  Palestinian Authority are encouraging but insufficient.''        Shahal and his Cabinet colleagues know there is no magic recipe to  eliminate terrorism. Coordinated and surgical operations, made  possible by good intelligence-gathering, can only reduce the threat.  ``The fight against terrorism,'' says a senior Shabak official, ``is  basically an exercise in damage control.'' The problem is, the public  refuses to accept the limits of the war against terrorism. People want  100 percent security; they eagerly hang on to promises ``to get rid  of, once and forever, terrorists.''        Recent polls indicate that if the Palestinian president indeed  produces evidence and tangible results showing his determination to  fight terrorism, the Israeli public may give Labor's Peres another  chance. But if the public questions Arafat's sincerity and is  unsatisfied with his actions, they would blame their own government --  not Arafat. This means, as some polls already predict, that Netanyahu  will be the next prime minister of Israel.";
  List<String> phrases = Arrays.asList(
          "Peres",
          "Arafat",
          "Melman",
          "Ha'aretz",
          "Col.",
          "Col. Jibril Rajoub",
          "Col. A",
          "Col. B",
          "Col. C",
          "Col. D",
          "Col. E",
          "Col. F",
          "Col. G",
          "Col. H",
          "Col. I",
          "Col. J",
          "Col. K",
          "Col. L",
          "Col. M",
          "Col. N",
          "Col. O",
          "Col. P",
          "Col. Q",
          "Col. R",
          "Col. S",
          "Col. T",
          "Col. U",
          "Col. V",
          "Col. W",
          "Jibril"
  );

  public void testPhraseTable() throws Exception {
    PhraseTable phraseTable = new PhraseTable();
    phraseTable.normalize = true;
    phraseTable.caseInsensitive = true;
    phraseTable.addPhrases(phrases);
    List<PhraseTable.PhraseMatch> matched = phraseTable.findAllMatches(testText);
    assertTrue(matched != null);
    assertEquals(12, matched.size());
    // Test lookup
    PhraseTable.Phrase p = phraseTable.lookupNormalized("COL.");
    assertEquals("Col.", p.getText());
  }

  public void testIterator() throws Exception {
    PhraseTable phraseTable = new PhraseTable();
    phraseTable.caseInsensitive = true;
    phraseTable.addPhrases(phrases);

    Set<String> origPhrases = new HashSet<String>();
    origPhrases.addAll(phrases);
    Set<String> iteratedPhrases = new HashSet<String>();
    Iterator<PhraseTable.Phrase> iterator = phraseTable.iterator();
    while (iterator.hasNext()) {
      iteratedPhrases.add(iterator.next().getText());
    }
    Set<String> intersection = CollectionUtils.intersection(origPhrases, iteratedPhrases);
    Collection<String> inOrigNotInIterated = CollectionUtils.diff(origPhrases, intersection);
    assertTrue("In original but not in iterated: " + inOrigNotInIterated, inOrigNotInIterated.isEmpty());
    Collection<String> inIteratedNotInOrig = CollectionUtils.diff(iteratedPhrases, intersection);
    assertTrue("In iterated but not in original: " + inIteratedNotInOrig, inIteratedNotInOrig.isEmpty());
  }

  public void testFindMatches() throws Exception {
    String text = "Who is Col. Jibril Rajoub";
    PhraseTable phraseTable = new PhraseTable();
    phraseTable.caseInsensitive = true;
    phraseTable.addPhrases(phrases);
    List<PhraseTable.PhraseMatch> matched = phraseTable.findMatches(text, 2, 5, true);
    assertTrue(matched != null);
    assertEquals(2, matched.size());
  }

}
