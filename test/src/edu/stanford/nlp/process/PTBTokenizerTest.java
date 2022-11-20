package edu.stanford.nlp.process;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.international.negra.NegraPennLanguagePack;


/** @author Christopher Manning
 */
public class PTBTokenizerTest {

  private final String[] ptbInputs = {
      "This is a sentence.",
      "U.S. insurance: Conseco acquires Kemper Corp. \n</HEADLINE>\n<P>\nU.S insurance",
      "Based in Eugene,Ore., PakTech needs a new distributor after Sydney-based Creative Pack Pty. Ltd. went into voluntary administration.",
      "The Iron Age (ca. 1300 – ca. 300 BC).",
      "Indo\u00ADnesian ship\u00ADping \u00AD",
      "Gimme a phone, I'm gonna call.",
      "\"John & Mary's dog,\" Jane thought (to herself).\n\"What a #$%!\na- ``I like AT&T''.\"",
      "I said at 4:45pm.",
      "I can't believe they wanna keep 40% of that.\"\n``Whatcha think?''\n\"I don't --- think so...,\"",
      "You `paid' US$170,000?!\nYou should've paid only$16.75.",
      "1. Buy a new Chevrolet (37%-owned in the U.S..) . 15%",
      "I like you ;-) but do you care :(. I'm happy ^_^ but shy (x.x)!",
      "Diamond (``Not even the chair'') lives near Udaipur (84km). {1. A potential Palmer trade:}",
      "No. I like No. 24 and no.47.",
      "You can get a B.S. or a B. A. or a Ph.D (sometimes a Ph. D) from Stanford.",
      "@Harry_Styles didn`t like Mu`ammar al-Qaddafi",
      "Kenneth liked Windows 3.1, Windows 3.x, and Mesa A.B as I remember things.",
      "I like programming in F# more than C#.",
      "NBC Live will be available free through the Yahoo! Chat Web site. E! Entertainment said ``Jeopardy!'' is a game show.",
      "I lived in O\u2019Malley and read OK! Magazine.",
      "I lived in O\u0092Malley and read OK! Magazine.", /* invalid unicode codepoint, but inherit from cp1252 */
      "I like: \u2022wine, \u0095cheese, \u2023salami, & \u2043speck.",
      "I don't give a f**k about your sh*tty life.",
      "First sentence.... Second sentence.",
      "First sentence . . . . Second sentence.",
      "I wasn’t really ... well, what I mean...see . . . what I'm saying, the thing is . . . I didn’t mean it.",
      "This is a url test. Here is one: http://google.com.",
      "This is a url test. Here is one: htvp://google.com.",
      "Download from ftp://myname@host.dom/%2Fetc/motd",
      "Download from svn://user@location.edu/path/to/magic/unicorns",
      "Download from svn+ssh://user@location.edu/path/to/magic/unicorns",
      "Independent Living can be reached at http://www.inlv.demon.nl/.",
      "We traveled from No. Korea to So. Calif. yesterday.",
      "I dunno.",
      "The o-kay was received by the anti-acquisition front on its foolishness-filled fish market.",
      "We ran the pre-tests through the post-scripted centrifuge.",
      "School-aged parents should be aware of the unique problems that they face.",
      "I dispute Art. 53 of the convention.",
      "I like Art. And I like History.",
      "Contact: sue@google.com, fred@stanford.edu; michael.inman@lab.rpi.cs.cmu.edu.",
      "Email: recruiters@marvelconsultants.com <mailto:recruiters@marvelconsultants.com>",
      " Jeremy Meier <jermeier@earthlink.net>",
      "Ram Tackett,  (mailto:rtackett@abacustech.net)",
      "[Jgerma5@aol.com]. Danny_Jones%ENRON@eott.com",
      "https://fancy.startup.ai",
      "mid-2015",
      "UK-based",
      "2010-2015 and 1986-87",
      "20-30%",
      "80,000-man march",
      "39-yard",
      "60-90's",
      "Soft AC-styled",
      "3 p.m., eastern time",
      "Total Private\nOrders 779.5 -9.5%",
      "2-9.5%",
      "2- 9.5%",
      "From July 23-24. Radisson Miyako Hotel.",
      "23 percent-2 percent higher than today",
      "23 percent--2 percent higher than today",
      "438798-438804",
      "He earned eligibility by virtue of a top-35 finish.",
      "Witt was 2-for-34 as a hitter",
      "An Atlanta-bound DC-9 crashed",
      "weigh 1,000-1,200 pounds, ",
      "Imus arrived to be host for the 5:30-to-10 a.m. show.",
      "The .38-Magnum bullet, a TRS-80, and IBM OS/2 sophisticated sound/graphics",
      "a 1908 Model K Stanley with 1:01-minute time",
      "the 9-to-11:45 a.m. weekday shift",
      "Brighton Rd. Pacifica",
      "Walls keeping water out of the bowl-shaped city have been breached, and emergency teams are using helicopters to drop 1,350kg (3,000lb) sandbags and concrete barriers into the gaps.",
      "i got (89.2%) in my exams",
      "Dial 908-333-4444 to unban mox opal",
      "The jerk who banned mox opal has social security number 555-55-5555.",
      "What do you suppose is in the file thicc_antennae.jpg?",
      "What do you suppose is in the file thicc_antennae.asdf?",
      // the second half of this is half of a two character codepoint
      // it used to crash the tokenizer for being unhandled text
      "two character codepoint: 😸" + ((char) 55296) + " ",
      // the space is because some weirdness happens having an
      // unmatched surrogate at the end of a text
      "half codepoint:" + ((char) 55296) + " ",
      "There are ,2 days left",
      "I sent 'em an 'email'",
  };

  private final String[][] ptbGold = {
      { "This", "is", "a", "sentence", "." },
      { "U.S.", "insurance", ":", "Conseco", "acquires", "Kemper", "Corp.", ".",
          "</HEADLINE>", "<P>", "U.S", "insurance" },
      { "Based", "in", "Eugene", ",", "Ore.", ",", "PakTech", "needs", "a", "new",
          "distributor", "after", "Sydney-based", "Creative", "Pack", "Pty.", "Ltd.",
          "went", "into", "voluntary", "administration", "." },
      { "The", "Iron", "Age", "-LRB-", "ca.", "1300", "--", "ca.", "300", "BC", "-RRB-", "." },
      { "Indonesian", "shipping", "-" },
      { "Gim", "me", "a", "phone", ",", "I", "'m", "gon", "na", "call", "."},
      { "``", "John", "&", "Mary", "'s", "dog", ",", "''", "Jane", "thought", "-LRB-", "to", "herself", "-RRB-",
          ".", "``", "What", "a", "#", "$", "%", "!", "a", "-", "``", "I", "like", "AT&T", "''", ".", "''" },
      { "I", "said", "at", "4:45", "pm", "."},
      { "I", "ca", "n't", "believe", "they", "wan", "na", "keep", "40", "%", "of", "that", ".", "''",
          "``", "Whatcha", "think", "?", "''", "``", "I", "do", "n't", "--", "think", "so", "...", ",", "''" },
      // We don't yet split "Whatcha" but probably should following model of "Whaddya" --> What d ya. Maybe What cha
      { "You", "`", "paid", "'", "US$", "170,000", "?!", "You", "should", "'ve", "paid", "only", "$", "16.75", "." },
      { "1", ".", "Buy", "a", "new", "Chevrolet",
          "-LRB-", "37", "%", "-", "owned", "in", "the", "U.S.", ".", "-RRB-", ".", "15", "%" },
      // Unclear if 37%-owned is right or wrong under old PTB....  Maybe should be 37 %-owned even though sort of crazy
      { "I", "like", "you", ";--RRB-", "but", "do", "you", "care",  ":-LRB-", ".",
          "I", "'m", "happy", "^_^", "but", "shy", "-LRB-x.x-RRB-", "!" },
      { "Diamond", "-LRB-", "``", "Not", "even",  "the", "chair", "''", "-RRB-", "lives", "near", "Udaipur", "-LRB-", "84", "km", "-RRB-", ".",
          "-LCB-", "1", ".", "A", "potential", "Palmer", "trade", ":", "-RCB-"},
      { "No", ".", "I", "like", "No.", "24", "and", "no.", "47", "." },
      { "You", "can", "get", "a", "B.S.", "or", "a", "B.", "A.", "or", "a", "Ph.D", "-LRB-", "sometimes", "a", "Ph.", "D", "-RRB-", "from", "Stanford", "." },
      { "@Harry_Styles", "did", "n`t", "like", "Mu`ammar", "al-Qaddafi" },
      { "Kenneth", "liked", "Windows", "3.1", ",", "Windows", "3.x", ",", "and", "Mesa", "A.B", "as", "I", "remember", "things", ".", },
      { "I", "like", "programming", "in", "F#", "more", "than", "C#", "." },
      { "NBC", "Live", "will", "be", "available", "free", "through", "the", "Yahoo!", "Chat", "Web", "site", ".",
          "E!", "Entertainment", "said", "``", "Jeopardy!", "''", "is", "a", "game", "show", "." },
      { "I", "lived", "in", "O'Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "lived", "in", "O'Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "like", ":", "\u2022", "wine", ",", "\u2022", "cheese", ",", "\u2023", "salami",
          ",", "&", "\u2043", "speck", "." },
      { "I", "do", "n't", "give", "a", "f**k", "about", "your", "sh*tty", "life", "." },
      { "First", "sentence", "...", ".", "Second", "sentence", "." },
      { "First", "sentence", "...", ".", "Second", "sentence", "." },
      { "I", "was", "n't", "really", "...", "well", ",", "what", "I", "mean", "...", "see", "...", "what", "I", "'m", "saying",
          ",", "the", "thing", "is", "...", "I", "did", "n't", "mean", "it", "." },
      { "This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "http://google.com", "." },
      { "This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "htvp", ":", "/", "/", "google.com", "." },
      { "Download", "from", "ftp://myname@host.dom/%2Fetc/motd" },
      { "Download", "from", "svn://user@location.edu/path/to/magic/unicorns" },
      { "Download", "from", "svn+ssh://user@location.edu/path/to/magic/unicorns" },
      { "Independent", "Living", "can", "be", "reached", "at", "http://www.inlv.demon.nl/", "." },
      { "We", "traveled", "from", "No.", "Korea", "to", "So.", "Calif.", "yesterday", "." },
      { "I", "du", "n", "no", "." },
      {"The", "o-kay", "was", "received", "by", "the", "anti-acquisition", "front", "on", "its", "foolishness-filled", "fish", "market", "."},
      {"We", "ran", "the", "pre-tests", "through", "the", "post-scripted", "centrifuge", "."},
      {"School-aged", "parents", "should", "be", "aware", "of", "the", "unique", "problems", "that", "they", "face","."},
      { "I", "dispute", "Art.", "53", "of", "the", "convention", "." },
      { "I", "like", "Art", ".", "And", "I", "like", "History", "." },
      { "Contact", ":", "sue@google.com", ",", "fred@stanford.edu", ";", "michael.inman@lab.rpi.cs.cmu.edu", "." },
      { "Email", ":", "recruiters@marvelconsultants.com", "<mailto:recruiters@marvelconsultants.com>" },
      { "Jeremy", "Meier", "<jermeier@earthlink.net>" },
      { "Ram", "Tackett", ",", "-LRB-", "mailto:rtackett@abacustech.net", "-RRB-" },
      { "-LSB-", "Jgerma5@aol.com", "-RSB-", ".", "Danny_Jones%ENRON@eott.com" },
      { "https://fancy.startup.ai" },
      { "mid-2015" },
      { "UK-based" },
      { "2010-2015", "and", "1986-87" },
      { "20-30", "%" },
      { "80,000-man", "march" },
      { "39-yard" },
      { "60-90", "'s" },
      { "Soft", "AC-styled" },
      { "3", "p.m.", ",", "eastern", "time" },
      { "Total", "Private", "Orders", "779.5", "-9.5", "%" },
      { "2-9.5", "%" },
      { "2", "-", "9.5", "%" },
      { "From", "July", "23-24", ".", "Radisson", "Miyako", "Hotel", "." },
      { "23", "percent-2", "percent", "higher", "than", "today" },
      { "23", "percent", "--", "2", "percent", "higher", "than", "today" },
      { "438798-438804" },
      { "He", "earned", "eligibility", "by", "virtue", "of", "a", "top-35", "finish", "." },
      { "Witt", "was", "2-for-34", "as", "a", "hitter" },
      { "An", "Atlanta-bound", "DC-9", "crashed" },
      { "weigh", "1,000-1,200", "pounds", "," },
      { "Imus", "arrived", "to", "be", "host", "for", "the", "5:30-to-10", "a.m.", "show", "." },
      { "The", ".38-Magnum", "bullet", ",", "a", "TRS-80", ",", "and", "IBM", "OS/2", "sophisticated", "sound/graphics" },
      { "a", "1908", "Model", "K", "Stanley", "with", "1:01-minute", "time" },
      { "the", "9-to-11:45", "a.m.", "weekday", "shift" },
      { "Brighton", "Rd.", "Pacifica"},
      { "Walls", "keeping", "water", "out", "of", "the", "bowl-shaped", "city", "have", "been", "breached", ",", "and",
              "emergency", "teams", "are", "using", "helicopters", "to", "drop", "1,350", "kg", "-LRB-", "3,000", "lb",
              "-RRB-", "sandbags", "and", "concrete", "barriers", "into", "the", "gaps", "." },
      { "i", "got", "-LRB-", "89.2", "%", "-RRB-", "in", "my", "exams" },
      { "Dial", "908-333-4444", "to", "unban", "mox", "opal" },
      { "The", "jerk", "who", "banned", "mox", "opal", "has", "social", "security", "number", "555-55-5555", "." },
      // test that filename extensions trigger something being a single word
      { "What", "do", "you", "suppose", "is", "in", "the", "file", "thicc_antennae.jpg", "?" },
      { "What", "do", "you", "suppose", "is", "in", "the", "file", "thicc_antennae", ".", "asdf", "?" },
      { "two", "character", "codepoint", ":", "😸" },
      { "half", "codepoint", ":", },
      { "There", "are", ",", "2", "days", "left", },
      { "I", "sent", "'em", "an", "'", "email", "'", },
  };

  private final String[][] ptbGoldSplitHyphenated = {
      { "This", "is", "a", "sentence", "." },
      { "U.S.", "insurance", ":", "Conseco", "acquires", "Kemper", "Corp.", ".",
          "</HEADLINE>", "<P>", "U.S", "insurance" },
      { "Based", "in", "Eugene", ",", "Ore.", ",", "PakTech", "needs", "a", "new",
          "distributor", "after", "Sydney", "-", "based", "Creative", "Pack", "Pty.", "Ltd.",
          "went", "into", "voluntary", "administration", "." },
      { "The", "Iron", "Age", "(", "ca.", "1300", "–", "ca.", "300", "BC", ")", "." },
      { "Indonesian", "shipping", "-" },
      { "Gim", "me", "a", "phone", ",", "I", "'m", "gon", "na", "call", "."},
      { "\"", "John", "&", "Mary", "'s", "dog", ",", "\"", "Jane", "thought", "(", "to", "herself", ")",
          ".", "\"", "What", "a", "#", "$", "%", "!", "a", "-", "``", "I", "like", "AT&T", "''", ".", "\"" },
      { "I", "said", "at", "4:45", "pm", "."},
      { "I", "ca", "n't", "believe", "they", "wan", "na", "keep", "40", "%", "of", "that", ".", "\"",
          "``", "Whatcha", "think", "?", "''", "\"", "I", "do", "n't", "---", "think", "so", "…", ",", "\"" },
      // We don't yet split "Whatcha" but probably should following model of "Whaddya" --> What d ya. Maybe What cha
      { "You", "`", "paid", "'", "US$", "170,000", "?!", "You", "should", "'ve", "paid", "only", "$", "16.75", "." },
      { "1", ".", "Buy", "a", "new", "Chevrolet",
          "(", "37", "%", "-", "owned", "in", "the", "U.S.", ".", ")", ".", "15", "%" },
      // Unclear if 37%-owned is right or wrong under old PTB....  Maybe should be 37 %-owned even though sort of crazy
      { "I", "like", "you", ";-)", "but", "do", "you", "care",  ":(", ".",
          "I", "'m", "happy", "^_^", "but", "shy", "(x.x)", "!" },
      { "Diamond", "(", "``", "Not", "even",  "the", "chair", "''", ")", "lives", "near", "Udaipur", "(", "84", "km", ")", ".",
          "{", "1", ".", "A", "potential", "Palmer", "trade", ":", "}"},
      { "No", ".", "I", "like", "No.", "24", "and", "no.", "47", "." },
      { "You", "can", "get", "a", "B.S.", "or", "a", "B.", "A.", "or", "a", "Ph.D", "(", "sometimes", "a", "Ph.", "D", ")", "from", "Stanford", "." },
      { "@Harry_Styles", "did", "n`t", "like", "Mu`ammar", "al", "-", "Qaddafi" },
      { "Kenneth", "liked", "Windows", "3.1", ",", "Windows", "3.x", ",", "and", "Mesa", "A.B", "as", "I", "remember", "things", ".", },
      { "I", "like", "programming", "in", "F#", "more", "than", "C#", "." },
      { "NBC", "Live", "will", "be", "available", "free", "through", "the", "Yahoo!", "Chat", "Web", "site", ".",
          "E!", "Entertainment", "said", "``", "Jeopardy!", "''", "is", "a", "game", "show", "." },
      { "I", "lived", "in", "O’Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "lived", "in", "O’Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "like", ":", "\u2022", "wine", ",", "\u2022", "cheese", ",", "\u2023", "salami",
          ",", "&", "\u2043", "speck", "." },
      { "I", "do", "n't", "give", "a", "f**k", "about", "your", "sh*tty", "life", "." },
      { "First", "sentence", "…", ".", "Second", "sentence", "." },
      { "First", "sentence", "…", ".", "Second", "sentence", "." },
      { "I", "was", "n’t", "really", "…", "well", ",", "what", "I", "mean", "…", "see", "…", "what", "I", "'m", "saying",
          ",", "the", "thing", "is", "…", "I", "did", "n’t", "mean", "it", "." },
      { "This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "http://google.com", "." },
      { "This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "htvp", ":", "/", "/", "google.com", "." },
      { "Download", "from", "ftp://myname@host.dom/%2Fetc/motd" },
      { "Download", "from", "svn://user@location.edu/path/to/magic/unicorns" },
      { "Download", "from", "svn+ssh://user@location.edu/path/to/magic/unicorns" },
      { "Independent", "Living", "can", "be", "reached", "at", "http://www.inlv.demon.nl/", "." },
      { "We", "traveled", "from", "No.", "Korea", "to", "So.", "Calif.", "yesterday", "." },
      { "I", "du", "n", "no", "." },
      {"The", "o-kay", "was", "received", "by", "the", "anti-acquisition", "front", "on", "its", "foolishness", "-", "filled", "fish", "market", "."},
      {"We", "ran", "the", "pre-tests", "through", "the", "post-scripted", "centrifuge", "."},
      {"School", "-", "aged", "parents", "should", "be", "aware", "of", "the", "unique", "problems", "that", "they", "face","."},
      { "I", "dispute", "Art.", "53", "of", "the", "convention", "." },
      { "I", "like", "Art", ".", "And", "I", "like", "History", "." },
      { "Contact", ":", "sue@google.com", ",", "fred@stanford.edu", ";", "michael.inman@lab.rpi.cs.cmu.edu", "." },
      { "Email", ":", "recruiters@marvelconsultants.com", "<mailto:recruiters@marvelconsultants.com>" },
      { "Jeremy", "Meier", "<jermeier@earthlink.net>" },
      { "Ram", "Tackett", ",", "(", "mailto:rtackett@abacustech.net", ")" },
      { "[", "Jgerma5@aol.com", "]", ".", "Danny_Jones%ENRON@eott.com" },
      { "https://fancy.startup.ai" },
      { "mid-2015" },
      { "UK", "-", "based" },
      { "2010", "-", "2015", "and", "1986", "-", "87" },
      { "20", "-", "30", "%" },
      { "80,000", "-", "man", "march" },
      { "39", "-", "yard"},
      { "60", "-", "90's" },
      { "Soft", "AC", "-", "styled" },
      { "3", "p.m.", ",", "eastern", "time" },
      { "Total", "Private", "Orders", "779.5", "-9.5", "%" },
      { "2", "-", "9.5", "%" },
      { "2", "-", "9.5", "%" },
      { "From", "July", "23", "-", "24", ".", "Radisson", "Miyako", "Hotel", "." },
      { "23", "percent", "-", "2", "percent", "higher", "than", "today" },
      { "23", "percent", "--", "2", "percent", "higher", "than", "today" },
      { "438798", "-", "438804" },
      { "He", "earned", "eligibility", "by", "virtue", "of", "a", "top", "-", "35", "finish", "." },
      { "Witt", "was", "2", "-", "for", "-", "34", "as", "a", "hitter" },
      { "An", "Atlanta", "-", "bound", "DC", "-", "9", "crashed" },
      { "weigh", "1,000", "-", "1,200", "pounds", "," },
      { "Imus", "arrived", "to", "be", "host", "for", "the", "5:30", "-", "to", "-", "10", "a.m.", "show", "." },
      { "The", ".38", "-", "Magnum", "bullet", ",", "a", "TRS", "-", "80", ",", "and", "IBM", "OS", "/", "2",
              "sophisticated", "sound", "/", "graphics" },
      { "a", "1908", "Model", "K", "Stanley", "with", "1:01", "-", "minute", "time" },
      { "the", "9", "-", "to", "-", "11:45", "a.m.", "weekday", "shift" },
// In 2017, these ones didn't work and we used these answers. May have to move back to them if we have a problem with too much pushback.
// these are the correct answers:
//      { "weigh", "1,000-1,200", "pounds", "," },
//      { "Imus", "arrived", "to", "be", "host", "for", "the", "5:30-to-10", "a.m.", "show", "." },
//      { "The", ".38-Magnum", "bullet", ",", "a", "TRS", "-", "80", ",", "and", "IBM", "OS", "/", "2",
//                "sophisticated", "sound", "/", "graphics"},
//      { "a", "1908", "Model", "K", "Stanley", "with", "1:01-minute", "time" },
//      { "the", "9-to-11:45", "a.m.", "weekday", "shift" },
      { "Brighton", "Rd.", "Pacifica"},
      { "Walls", "keeping", "water", "out", "of", "the", "bowl", "-", "shaped", "city", "have", "been", "breached", ",", "and",
              "emergency", "teams", "are", "using", "helicopters", "to", "drop", "1,350", "kg", "(", "3,000", "lb",
              ")", "sandbags", "and", "concrete", "barriers", "into", "the", "gaps", "." },
      { "i", "got", "(", "89.2", "%", ")", "in", "my", "exams" },
      { "Dial", "908-333-4444", "to", "unban", "mox", "opal" },
      { "The", "jerk", "who", "banned", "mox", "opal", "has", "social", "security", "number", "555-55-5555", "." },
      { "What", "do", "you", "suppose", "is", "in", "the", "file", "thicc_antennae.jpg", "?" },
      { "What", "do", "you", "suppose", "is", "in", "the", "file", "thicc_antennae", ".", "asdf", "?" },
      { "two", "character", "codepoint", ":", "😸" },
      { "half", "codepoint", ":", },
      { "There", "are", ",", "2", "days", "left", },
      { "I", "sent", "'em", "an", "'", "email", "'", },
  };

  @Test
  public void testPTBTokenizerWord() {
    TokenizerFactory<Word> tokFactory = PTBTokenizer.PTBTokenizerFactory.newWordTokenizerFactory("ptb3Escaping");
    runOnTwoArrays(tokFactory, ptbInputs, ptbGold);
  }

  private final String[] moreInputs = {
          "Joseph Someone (fl. 2050–75) liked the noble gases, viz. helium, neon, argon, xenon, krypton and radon.",
          "Sambucus nigra subsp. canadensis and Canis spp. missing",
          "Jim Jackon & Co. LLC replied.",
          "Xanadu Pvt. Ltd. replied.",
          " \u2010 - ___ ",
          "whenever one goes 'tisk tisk' at something",
          "¯\\_(ツ)_/¯",
          "She hates Alex.",
          "An offering of 10 million common shares, via Alex. Brown &amp; Sons.",
  };

  private final String[][] moreGold = {
          { "Joseph", "Someone", "-LRB-", "fl.", "2050", "--", "75", "-RRB-", "liked", "the", "noble", "gases", ",",
                  "viz.", "helium", ",", "neon", ",", "argon", ",", "xenon", ",", "krypton", "and", "radon", "." },
          { "Sambucus", "nigra", "subsp.", "canadensis", "and", "Canis", "spp.", "missing" },
          { "Jim", "Jackon", "&", "Co.", "LLC", "replied", "." },
          { "Xanadu", "Pvt.", "Ltd.", "replied", "." },
          { "-", "-", "___" },
          { "whenever", "one", "goes", "`", "tisk", "tisk", "'", "at", "something" },
          { "¯\\_-LRB-ツ-RRB-_/¯" },
          { "She", "hates", "Alex", "."},
          { "An", "offering", "of", "10", "million", "common", "shares", ",", "via", "Alex.", "Brown", "&", "Sons", "."},
  };

  private final String[][] moreGoldUD = {
          { "Joseph", "Someone", "(", "fl.", "2050", "–", "75", ")", "liked", "the", "noble", "gases", ",",
                  "viz.", "helium", ",", "neon", ",", "argon", ",", "xenon", ",", "krypton", "and", "radon", "." },
          { "Sambucus", "nigra", "subsp.", "canadensis", "and", "Canis", "spp.", "missing" },
          { "Jim", "Jackon", "&", "Co.", "LLC", "replied", "." },
          { "Xanadu", "Pvt.", "Ltd.", "replied", "." },
          { "\u2010", "-", "___" },
          { "whenever", "one", "goes", "'", "tisk", "tisk", "'", "at", "something" },
          { "¯\\_(ツ)_/¯" },
          { "She", "hates", "Alex", "."},
          { "An", "offering", "of", "10", "million", "common", "shares", ",", "via", "Alex.", "Brown", "&", "Sons", "."},
  };

  @Test
  public void testPTBTokenizerCoreLabel() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("ptb3Escaping");
    runOnTwoArrays(tokFactory, moreInputs, moreGold);
    TokenizerFactory<CoreLabel> tokFactoryUD = PTBTokenizer.coreLabelFactory();
    runOnTwoArrays(tokFactoryUD, moreInputs, moreGoldUD);
  }


  private final String[] corpInputs = {
    "So, too, many analysts predict, will Exxon Corp., Chevron Corp. and Amoco Corp.",
    "So, too, many analysts predict, will Exxon Corp., Chevron Corp. and Amoco Corp.   ",
  };

  private final String[][] corpGold = {
          { "So", ",", "too", ",", "many", "analysts", "predict", ",", "will", "Exxon",
            "Corp.", ",", "Chevron", "Corp.", "and", "Amoco", "Corp", "." }, // strictTreebank3
          { "So", ",", "too", ",", "many", "analysts", "predict", ",", "will", "Exxon",
                  "Corp.", ",", "Chevron", "Corp.", "and", "Amoco", "Corp.", "." }, // regular
  };

  @Test
  public void testCorp() {
    assertEquals(2, corpInputs.length);
    assertEquals(2, corpGold.length);
    // We test a 2x2 design: {strict, regular} x {no following context, following context}
    for (int sent = 0; sent < 4; sent++) {
      PTBTokenizer<CoreLabel> ptbTokenizer = new PTBTokenizer<>(new StringReader(corpInputs[sent / 2]),
              new CoreLabelTokenFactory(),
              (sent % 2 == 0) ? "strictTreebank3": "");
      int i = 0;
      while (ptbTokenizer.hasNext()) {
        CoreLabel w = ptbTokenizer.next();
        try {
          assertEquals("PTBTokenizer problem", corpGold[sent % 2][i], w.word());
        } catch (ArrayIndexOutOfBoundsException aioobe) {
          // the assertion below outside the loop will fail
        }
        i++;
      }
      if (i != corpGold[sent % 2].length) {
        System.out.print("Gold: ");
        System.out.println(Arrays.toString(corpGold[sent % 2]));
        List<CoreLabel> tokens = new PTBTokenizer<>(new StringReader(corpInputs[sent / 2]),
              new CoreLabelTokenFactory(),
              (sent % 2 == 0) ? "strictTreebank3": "").tokenize();
        System.out.print("Guess: ");
        System.out.println(SentenceUtils.listToString(tokens));
        System.out.flush();
      }
      assertEquals("PTBTokenizer num tokens problem", i, corpGold[sent % 2].length);
    }
  }


  private static final String[] jeInputs = {
          "it's",
          " it's ",
          // "open images/cat.png", // Dunno how to get this case without bad consequence. Can't detect eof in pattern....
  };

  private static final List[] jeOutputs = {
          Arrays.asList(new Word("it"), new Word("'s")),
          Arrays.asList(new Word("it"), new Word("'s")),
          // Arrays.asList(new Word("open"), new Word("images/cat.png")),
  };


  /** These case check things still work at end of file that would normally have following contexts. */
  @Test
  public void testJacobEisensteinApostropheCase() {
    assertEquals(jeInputs.length, jeOutputs.length);
    for (int i = 0; i < jeInputs.length; i++) {
      StringReader reader = new StringReader(jeInputs[i]);
      PTBTokenizer<Word> tokenizer = PTBTokenizer.newPTBTokenizer(reader);
      List<Word> tokens = tokenizer.tokenize();
      assertEquals(jeOutputs[i], tokens);
    }
  }


  private static final String[] untokInputs = {
    "London - AFP reported junk .",
    "Paris - Reuters reported news .",
    "Sydney - News said - something .",
    "HEADLINE - New Android phone !",
    "I did it 'cause I wanted to , and you 'n' me know that .",
    "He said that `` Luxembourg needs surface - to - air missiles . ''",
  };

  private static final String[] untokOutputs = {
    "London - AFP reported junk.",
    "Paris - Reuters reported news.",
    "Sydney - News said - something.",
    "HEADLINE - New Android phone!",
    "I did it 'cause I wanted to, and you 'n' me know that.",
    "He said that \"Luxembourg needs surface-to-air missiles.\"",
  };

  @Test
  public void testUntok() {
    assertEquals(untokInputs.length, untokOutputs.length);
    for (int i = 0; i < untokInputs.length; i++) {
      assertEquals("untok gave the wrong result", untokOutputs[i], PTBTokenizer.ptb2Text(untokInputs[i]));
    }
  }


  @Test
  public void testInvertible() {
    String text = "  This     is     a      colourful sentence.    ";
    PTBTokenizer<CoreLabel> tokenizer =
      PTBTokenizer.newPTBTokenizer(new StringReader(text), false, true);
    List<CoreLabel> tokens = tokenizer.tokenize();
    assertEquals(6, tokens.size());
    assertEquals("  ", tokens.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    assertEquals("     ", tokens.get(0).get(CoreAnnotations.AfterAnnotation.class));
    assertEquals("Wrong begin char offset", 2, (int) tokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    assertEquals("Wrong end char offset", 6, (int) tokens.get(0).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    assertEquals("This", tokens.get(0).get(CoreAnnotations.OriginalTextAnnotation.class));
    // note: after(x) and before(x+1) are the same
    assertEquals("     ", tokens.get(0).get(CoreAnnotations.AfterAnnotation.class));
    assertEquals("     ", tokens.get(1).get(CoreAnnotations.BeforeAnnotation.class));
    // americanize is now off by default
    assertEquals("colourful", tokens.get(3).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("colourful", tokens.get(3).get(CoreAnnotations.OriginalTextAnnotation.class));
    assertEquals("", tokens.get(4).after());
    assertEquals("", tokens.get(5).before());
    assertEquals("    ", tokens.get(5).get(CoreAnnotations.AfterAnnotation.class));

    StringBuilder result = new StringBuilder();
    result.append(tokens.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    for (CoreLabel token : tokens) {
      result.append(token.get(CoreAnnotations.OriginalTextAnnotation.class));
      String after = token.get(CoreAnnotations.AfterAnnotation.class);
      if (after != null)
        result.append(after);
    }
    assertEquals(text, result.toString());

    for (int i = 0; i < tokens.size() - 1; ++i) {
      assertEquals(tokens.get(i).get(CoreAnnotations.AfterAnnotation.class),
                   tokens.get(i + 1).get(CoreAnnotations.BeforeAnnotation.class));
    }
  }


  private final String[] sgmlInputs = {
    "Significant improvements in peak FEV1 were demonstrated with tiotropium/olodaterol 5/2 μg (p = 0.008), 5/5 μg (p = 0.012), and 5/10 μg (p < 0.0001) versus tiotropium monotherapy [51].",
    "Panasonic brand products are produced by Samsung Electronics Co. Ltd. Sanyo products aren't.",
    "Oesophageal acid exposure (% time <pH 4) was similar in patients with or without complications (19.2% v 19.3% p>0.05).",
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Strict//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">",
    "Hi! <foo bar=\"baz xy = foo !$*) 422\" > <?PITarget PIContent?> <?PITarget PIContent> Hi!",
    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>\n<book xml:id=\"simple_book\" xmlns=\"http://docbook.org/ns/docbook\" version=\"5.0\">\n",
    "<chapter xml:id=\"chapter_1\"><?php echo $a; ?>\n<!-- This is an SGML/XML comment \"Hi!\" -->\n<p> </p> <p-fix / >",
    "<a href=\"http:\\\\it's\\here\"> <quote orig_author='some \"dude'/> <not sgmltag",
    "<quote previouspost=\"\n" +
            "&gt; &gt; I really don't want to process this junk.\n" +
            "&gt; No one said you did, runny.  What's got you so scared, anyway?-\n" +
            "\">",
    "&lt;b...@canada.com&gt; funky@thedismalscience.net <myemail@where.com>",
    "<DOC> <DOCID> nyt960102.0516 </DOCID><STORYID cat=w pri=u> A0264 </STORYID> <SLUG fv=ttj-z> ", // this is a MUC7 document
    // In WMT 2015 from GigaWord (mis)processing. Do not always want to allow newline within SGML as messes too badly with CoNLL and one-sentence-per-line processing
    "<!-- copy from here --> <a href=\"http://strategis.gc.ca/epic/internet/inabc-eac.nsf/en/home\"><img src=\"id-images/ad-220x80_01e.jpg\" alt=\"Aboriginal Business Canada:\n" +
                  "Opening New Doors for Your Business\" width=\"220\" height=\"80\" border=\"0\"></a> <!-- copy to here --> Small ABC Graphic Instructions 1.",
    "We traveled from No.\nKorea to the U.S.A.\nWhy?"
  };

  private final String[][] sgmlGold = {
    { "Significant", "improvements", "in", "peak", "FEV1", "were", "demonstrated", "with", "tiotropium", "/", "olodaterol",
            "5/2", "μg", "(", "p", "=", "0.008", ")", ",", "5/5", "μg", "(", "p", "=", "0.012", ")",
            ",", "and", "5/10", "μg", "(", "p", "<", "0.0001", ")", "versus", "tiotropium", "monotherapy",
            "[", "51", "]", "." },
    { "Panasonic", "brand", "products", "are", "produced", "by", "Samsung", "Electronics", "Co.", "Ltd.", ".",
            "Sanyo", "products", "are", "n't", ".", },
    { "Oesophageal", "acid", "exposure", "(", "%", "time", "<", "pH", "4", ")", "was", "similar", "in",
            "patients", "with", "or", "without", "complications", "(", "19.2", "%", "v", "19.3", "%",
            "p", ">", "0.05", ")", ".", },
    { "<!DOCTYPE\u00A0html\u00A0PUBLIC\u00A0\"-//W3C//DTD\u00A0HTML\u00A04.01\u00A0Strict//EN\"\u00A0\"http://www.w3.org/TR/html4/strict.dtd\">" }, // spaces go to &nbsp; \u00A0
    { "Hi", "!", "<foo\u00A0bar=\"baz\u00A0xy\u00A0=\u00A0foo\u00A0!$*)\u00A0422\"\u00A0>", "<?PITarget\u00A0PIContent?>", "<?PITarget\u00A0PIContent>", "Hi", "!" },
    { "<?xml\u00A0version=\"1.0\"\u00A0encoding=\"UTF-8\"\u00A0?>", "<?xml-stylesheet\u00A0type=\"text/xsl\"\u00A0href=\"style.xsl\"?>",
            "<book\u00A0xml:id=\"simple_book\"\u00A0xmlns=\"http://docbook.org/ns/docbook\"\u00A0version=\"5.0\">", },
    { "<chapter\u00A0xml:id=\"chapter_1\">", "<?php\u00A0echo\u00A0$a;\u00A0?>", "<!--\u00A0This\u00A0is\u00A0an\u00A0SGML/XML\u00A0comment\u00A0\"Hi!\"\u00A0-->",
            "<p>", "</p>", "<p-fix\u00A0/\u00A0>"},
    { "<a href=\"http:\\\\it's\\here\">", "<quote orig_author='some \"dude'/>", "<", "not", "sgmltag" },
    { "<quote previouspost=\"\u00A0" +
            "&gt; &gt; I really don't want to process this junk.\u00A0" +
            "&gt; No one said you did, runny.  What's got you so scared, anyway?-\u00A0" +
            "\">" },
    { "&lt;b...@canada.com&gt;", "funky@thedismalscience.net", "<myemail@where.com>" },
    { "<DOC>", "<DOCID>", "nyt", "960102.0516", "</DOCID>", "<STORYID\u00A0cat=w\u00A0pri=u>", "A0264", "</STORYID>", "<SLUG\u00A0fv=ttj-z>" },
    { "<!--\u00A0copy\u00A0from\u00A0here\u00A0-->", "<a\u00A0href=\"http://strategis.gc.ca/epic/internet/inabc-eac.nsf/en/home\">",
            "<img\u00A0src=\"id-images/ad-220x80_01e.jpg\"\u00A0alt=\"Aboriginal\u00A0Business\u00A0Canada:\u00A0" +
            "Opening\u00A0New\u00A0Doors\u00A0for\u00A0Your\u00A0Business\"\u00A0width=\"220\"\u00A0height=\"80\"\u00A0border=\"0\">",
            "</a>",  "<!--\u00A0copy\u00A0to\u00A0here\u00A0-->", "Small", "ABC", "Graphic", "Instructions", "1", "." },
    { "We", "traveled", "from", "No.", "Korea", "to", "the", "U.S.A.", ".", "Why", "?" },
  };

  @Test
  public void testPTBTokenizerSGML() {
    // System.err.println("Starting SGML test");
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("invertible");
    runOnTwoArrays(tokFactory, sgmlInputs, sgmlGold);
    runAgainstOrig(tokFactory, sgmlInputs);
  }

  private final String[][] sgmlPerLineGold = {
    { "Significant", "improvements", "in", "peak", "FEV1", "were", "demonstrated", "with", "tiotropium", "/", "olodaterol",
            "5/2", "μg", "(", "p", "=", "0.008", ")", ",", "5/5", "μg", "(", "p", "=", "0.012", ")",
            ",", "and", "5/10", "μg", "(", "p", "<", "0.0001", ")", "versus", "tiotropium", "monotherapy",
            "[", "51", "]", "." },
    { "Panasonic", "brand", "products", "are", "produced", "by", "Samsung", "Electronics", "Co.", "Ltd.", ".",
            "Sanyo", "products", "are", "n't", ".", },
    { "Oesophageal", "acid", "exposure", "(", "%", "time", "<", "pH", "4", ")", "was", "similar", "in",
            "patients", "with", "or", "without", "complications", "(", "19.2", "%", "v", "19.3", "%",
            "p", ">", "0.05", ")", ".", },
    { "<!DOCTYPE\u00A0html\u00A0PUBLIC\u00A0\"-//W3C//DTD\u00A0HTML\u00A04.01\u00A0Strict//EN\"\u00A0\"http://www.w3.org/TR/html4/strict.dtd\">" }, // spaces go to &nbsp; \u00A0
    { "Hi", "!", "<foo\u00A0bar=\"baz\u00A0xy\u00A0=\u00A0foo\u00A0!$*)\u00A0422\"\u00A0>", "<?PITarget\u00A0PIContent?>", "<?PITarget\u00A0PIContent>", "Hi", "!" },
    { "<?xml\u00A0version=\"1.0\"\u00A0encoding=\"UTF-8\"\u00A0?>", "<?xml-stylesheet\u00A0type=\"text/xsl\"\u00A0href=\"style.xsl\"?>",
            "<book\u00A0xml:id=\"simple_book\"\u00A0xmlns=\"http://docbook.org/ns/docbook\"\u00A0version=\"5.0\">", },
    { "<chapter\u00A0xml:id=\"chapter_1\">", "<?php\u00A0echo\u00A0$a;\u00A0?>", "<!--\u00A0This\u00A0is\u00A0an\u00A0SGML/XML\u00A0comment\u00A0\"Hi!\"\u00A0-->",
            "<p>", "</p>", "<p-fix\u00A0/\u00A0>"},
    { "<a href=\"http:\\\\it's\\here\">", "<quote orig_author='some \"dude'/>", "<", "not", "sgmltag" },
    { "<", "quote", "previouspost", "=", "\"",
            ">", ">", "I", "really", "do", "n't", "want", "to", "process", "this", "junk", ".",
            ">", "No", "one", "said", "you", "did", ",", "runny", ".", "What", "'s", "got", "you", "so", "scared", ",", "anyway", "?", "-",
            "\"", ">" },
    { "&lt;b...@canada.com&gt;", "funky@thedismalscience.net", "<myemail@where.com>" },
    { "<DOC>", "<DOCID>", "nyt", "960102.0516", "</DOCID>", "<STORYID\u00A0cat=w\u00A0pri=u>", "A0264", "</STORYID>", "<SLUG\u00A0fv=ttj-z>" },
    { "<!--\u00A0copy\u00A0from\u00A0here\u00A0-->", "<a\u00A0href=\"http://strategis.gc.ca/epic/internet/inabc-eac.nsf/en/home\">",
            "<", "img", "src", "=", "\"", "id-images/ad-220x80_01e.jpg", "\"", "alt", "=", "\"", "Aboriginal", "Business", "Canada", ":",
            "Opening", "New", "Doors", "for", "Your", "Business", "\"",
            "width", "=", "\"", "220", "\"", "height", "=", "\"", "80", "\"", "border", "=", "\"", "0", "\"", ">",
            "</a>",  "<!--\u00A0copy\u00A0to\u00A0here\u00A0-->", "Small", "ABC", "Graphic", "Instructions", "1", "." },
    { "We", "traveled", "from", "No", ".", "Korea", "to", "the", "U.S.A.", "Why", "?" },
  };

  @Test
  public void testPTBTokenizerTokenizePerLineSGML() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("tokenizePerLine=true,invertible");
    runOnTwoArrays(tokFactory, sgmlInputs, sgmlPerLineGold);
    runAgainstOrig(tokFactory, sgmlInputs);
  }

  @Test
  public void testPTBTokenizerTokenizeSplitHyphens() {
    // Note that by turning off the ptb3 escaping option, this winds
    // up testing the () tokenization as well
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory(
            "splitHyphenated=true,splitForwardSlash,ellipses=unicode,invertible");
    runOnTwoArrays(tokFactory, ptbInputs, ptbGoldSplitHyphenated);
    runAgainstOrig(tokFactory, ptbInputs);
  }

  @Test
  public void testPTBTokenizerTokenizeParens() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("invertible");
    runOnTwoArrays(tokFactory, moreInputs, moreGoldUD);
    runAgainstOrig(tokFactory, moreInputs);
  }


  @Test
  public void testFractions() {
    String[] sample = { "5-1/4 plus 2 3/16 = 7\u00A07/16 in the U.S.S.R. Why not?" };
    String[][] tokenizedNormal = { { "5-1/4", "plus", "2\u00A03/16", "=", "7\u00A07/16", "in", "the", "U.S.S.R.", ".", "Why", "not", "?" } };
    String[][] tokenizedStrict = { { "5-1/4", "plus", "2", "3/16", "=", "7", "7/16", "in", "the", "U.S.S.R", ".", "Why", "not", "?" } };
    TokenizerFactory<CoreLabel> tokFactoryNormal = PTBTokenizer.coreLabelFactory("invertible=true");
    TokenizerFactory<CoreLabel> tokFactoryStrict = PTBTokenizer.coreLabelFactory("strictTreebank3=true,invertible=true");
    runOnTwoArrays(tokFactoryNormal, sample, tokenizedNormal);
    runOnTwoArrays(tokFactoryStrict, sample, tokenizedStrict);
    runAgainstOrig(tokFactoryNormal, sample);
    runAgainstOrig(tokFactoryStrict, sample);
  }


  public static <T extends Label> void runOnTwoArrays(TokenizerFactory<T> tokFactory, String[] inputs, String[][] desired) {
    assertEquals("Test data arrays don't match in length", inputs.length, desired.length);
    for (int sent = 0; sent < inputs.length; sent++) {
      // System.err.println("Testing " + inputs[sent]);
      Tokenizer<T> tok = tokFactory.getTokenizer(new StringReader(inputs[sent]));
      for (int i = 0; tok.hasNext() || i < desired[sent].length; i++) {
        if ( ! tok.hasNext()) {
          fail("PTBTokenizer generated too few tokens for sentence " + sent + "! Missing " + desired[sent][i]);
        }
        T w = tok.next();
        if (i >= desired[sent].length) {
          fail("PTBTokenizer generated too many tokens for sentence " + sent + "! Added " + w.value());
        } else {
          assertEquals("PTBTokenizer got wrong token for " + inputs[sent], desired[sent][i], w.value());
        }
      }
    }
  }

  private static <T extends CoreLabel> void runOnTwoArraysWithOffsets(TokenizerFactory<T> tokFactory, String[] inputs, String[][] desired) {
    assertEquals("Test data arrays don't match in length", inputs.length, desired.length);
    for (int sent = 0; sent < inputs.length; sent++) {
      // System.err.println("Testing " + inputs[sent]);
      Tokenizer<T> tok = tokFactory.getTokenizer(new StringReader(inputs[sent]));
      for (int i = 0; tok.hasNext() || i < desired[sent].length; i++) {
        if ( ! tok.hasNext()) {
          fail("PTBTokenizer generated too few tokens for sentence " + sent + "! Missing " + desired[sent][i]);
        }
        T w = tok.next();
        if (i >= desired[sent].length) {
          fail("PTBTokenizer generated too many tokens for sentence " + sent + "! Added " + w.value());
        } else {
          assertEquals("PTBTokenizer got wrong token", desired[sent][i], w.value());
          assertEquals("PTBTokenizer charOffsets wrong for " + desired[sent][i], desired[sent][i].length(),
                    w.endPosition() - w.beginPosition());
        }
      }
    }
  }


  /** The appending has to run one behind so as to make sure that the after annotation has been filled in!
   *  Just placing the appendTextFrom() after reading tok.next() in the loop does not work.
   */
  private static <T extends CoreLabel> void runAgainstOrig(TokenizerFactory<T> tokFactory, String[] inputs) {
    for (String input : inputs) {
      // System.err.println("Running on line: |" + input + "|");
      StringBuilder origText = new StringBuilder();
      T last = null;
      for (Tokenizer<T> tok = tokFactory.getTokenizer(new StringReader(input)); tok.hasNext(); ) {
        appendTextFrom(origText, last);
        last = tok.next();
      }
      appendTextFrom(origText, last);
      assertEquals("PTBTokenizer has wrong originalText", input, origText.toString());
    }
  }

  private static <T extends CoreLabel> void appendTextFrom(StringBuilder origText, T token) {
    if (token != null) {
      // System.err.println("|Before|OrigText|After| = |" + token.get(CoreAnnotations.BeforeAnnotation.class) +
      //         "|" + token.get(CoreAnnotations.OriginalTextAnnotation.class) + "|" + token.get(CoreAnnotations.AfterAnnotation.class) + "|");
      if (origText.length() == 0) {
        origText.append(token.get(CoreAnnotations.BeforeAnnotation.class));
      }
      origText.append(token.get(CoreAnnotations.OriginalTextAnnotation.class));
      origText.append(token.get(CoreAnnotations.AfterAnnotation.class));
    }
  }


  @SuppressWarnings("unchecked")
  @Test
  public void testPTBTokenizerGerman() {
    String[] sample = { "Das TV-Duell von Kanzlerin Merkel und SPD-Herausforderer Steinbrück war eher lahm - können es die Spitzenleute der kleinen Parteien besser? ",
            "Die erquickende Sicherheit und Festigkeit in der Bewegung, den Vorrat von Kraft, kann ja die Versammlung nicht fühlen, hören will sie sie nicht, also muß sie sie sehen; und die sehe man einmal in einem Paar spitzen Schultern, zylindrischen Schenkeln, oder leeren Ärmeln, oder lattenförmigen Beinen." };
    String[][] tokenized = {
            { "Das", "TV", "-", "Duell", "von", "Kanzlerin", "Merkel", "und", "SPD", "-", "Herausforderer", "Steinbrück", "war", "eher",
              "lahm", "-", "können", "es", "die", "Spitzenleute", "der", "kleinen", "Parteien", "besser", "?", },
            {"Die", "erquickende", "Sicherheit", "und", "Festigkeit", "in", "der", "Bewegung", ",", "den", "Vorrat", "von",
                    "Kraft", ",", "kann", "ja", "die", "Versammlung", "nicht", "fühlen", ",", "hören", "will", "sie", "sie",
                    "nicht", ",", "also", "muß", "sie", "sie", "sehen", ";", "und", "die", "sehe", "man", "einmal", "in", "einem",
                    "Paar", "spitzen", "Schultern", ",", "zylindrischen", "Schenkeln", ",", "oder", "leeren", "Ärmeln", ",",
                    "oder", "lattenförmigen", "Beinen", "."
            }
    };
    TreebankLanguagePack tlp = new NegraPennLanguagePack();
    TokenizerFactory tokFactory = tlp.getTokenizerFactory();
    runOnTwoArrays(tokFactory, sample, tokenized);
  }

  private final String[] mtInputs = {
    "Enter an option [?/Current]:{1}",
    "for example, {1}http://www.autodesk.com{2}, or a path",
    "enter {3}@{4} at the Of prompt.",
    "{1}block name={2}",
    "1202-03-04 5:32:56 2004-03-04T18:32:56",
    "20°C is 68°F because 0℃ is 32℉",
    "a.jpg a-b.jpg a.b.jpg a-b.jpg a_b.jpg a-b-c.jpg 0-1-2.jpg a-b/c-d_e.jpg a-b/c-9a9_9a.jpg\n",
    "¯\\_(ツ)_/¯",
    "#hashtag #Azərbaycanca #mûǁae #Čeština #日本語ハッシュタグ #1 #23 #Trump2016 @3 @acl_2016",
          "Sect. 793 of the Penal Code",
    "Pls. copy the text within this quote to the subject part of your email and explain wrt. the principles.",
  };

  private final String[][] mtGold = {
    { "Enter", "an", "option", "-LSB-", "?", "/", "Current", "-RSB-", ":", "-LCB-", "1", "-RCB-" },
    { "for", "example", ",", "-LCB-", "1", "-RCB-", "http://www.autodesk.com", "-LCB-", "2", "-RCB-", ",", "or", "a", "path" },
    { "enter", "-LCB-", "3", "-RCB-", "@", "-LCB-", "4", "-RCB-", "at", "the", "Of", "prompt", "." },
    { "-LCB-", "1", "-RCB-", "block", "name", "=", "-LCB-", "2", "-RCB-" },
    { "1202-03-04", "5:32:56", "2004-03-04T18:32:56" },
    { "20", "°C", "is", "68", "°F", "because", "0", "℃", "is", "32", "℉" },
    { "a.jpg", "a-b.jpg", "a.b.jpg", "a-b.jpg", "a_b.jpg", "a-b-c.jpg", "0-1-2.jpg", "a-b/c-d_e.jpg", "a-b/c-9a9_9a.jpg"},
    { "¯\\_-LRB-ツ-RRB-_/¯" },
    { "#hashtag", "#Azərbaycanca", "#mûǁae", "#Čeština", "#日本語ハッシュタグ", "#", "1", "#", "23", "#Trump2016", "@", "3", "@acl_2016" },
          { "Sect.", "793", "of", "the", "Penal", "Code" },
    { "Pls.", "copy", "the", "text", "within", "this", "quote", "to", "the", "subject", "part", "of", "your", "email",
            "and", "explain", "wrt.", "the", "principles", "." },
  };

  private final String[][] mtGoldUD = {
          { "Enter", "an", "option", "[", "?", "/", "Current", "]", ":", "{", "1", "}" },
          { "for", "example", ",", "{", "1", "}", "http://www.autodesk.com", "{", "2", "}", ",", "or", "a", "path" },
          { "enter", "{", "3", "}", "@", "{", "4", "}", "at", "the", "Of", "prompt", "." },
          { "{", "1", "}", "block", "name", "=", "{", "2", "}" },
          { "1202-03-04", "5:32:56", "2004-03-04T18:32:56" },
          { "20", "°C", "is", "68", "°F", "because", "0", "℃", "is", "32", "℉" },
          { "a.jpg", "a-b.jpg", "a.b.jpg", "a-b.jpg", "a_b.jpg", "a-b-c.jpg", "0-1-2.jpg", "a-b/c-d_e.jpg", "a-b/c-9a9_9a.jpg"},
          { "¯\\_(ツ)_/¯" },
          { "#hashtag", "#Azərbaycanca", "#mûǁae", "#Čeština", "#日本語ハッシュタグ", "#", "1", "#", "23", "#Trump2016", "@", "3", "@acl_2016" },
          { "Sect.", "793", "of", "the", "Penal", "Code" },
          { "Pls.", "copy", "the", "text", "within", "this", "quote", "to", "the", "subject", "part", "of", "your", "email",
                  "and", "explain", "wrt.", "the", "principles", "." },
  };

  @Test
  public void testPTBTokenizerMT() {
    TokenizerFactory<Word> tokFactory = PTBTokenizer.PTBTokenizerFactory.newWordTokenizerFactory("ptb3Escaping");
    runOnTwoArrays(tokFactory, mtInputs, mtGold);
    TokenizerFactory<Word> tokFactoryUD = PTBTokenizer.factory();
    runOnTwoArrays(tokFactoryUD, mtInputs, mtGoldUD);
  }

  private final String[] emojiInputs = {
          // The non-BMP Emoji end up being surrogate pair encoded in Java! This list includes a flag. "😉😀😂😍🤡🇦🇺🍺"
          "\uD83D\uDE09\uD83D\uDE00\uD83D\uDE02\uD83D\uDE0D\uD83E\uDD21\uD83C\uDDE6\uD83C\uDDFA\uD83C\uDF7A",
          // People with skin tones
          "\uD83D\uDC66\uD83C\uDFFB\uD83D\uDC67\uD83C\uDFFF",
          // A family with cheese; \u200D is the zero-width joiner for making complex emoji
          "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\uD83E\uDDC0",
          // Some BMP emoji
          "\u00AE\u203C\u2198\u231A\u2328\u23F0\u2620\u26BD\u2705\u2757",
          // Choosing emoji vs. text presentation.
          "⚠⚠️⚠︎❤️❤",
          // 👩 ‍ ⚖ , 👨🏿‍🎤  i.e. female judge person with skin color (scales is U+2696) then ??
          "\uD83D\uDC69\u200D⚖\uD83D\uDC68\uD83C\uDFFF\u200D\uD83C\uDFA4",
          "555-0199",
          "555\u20120199",
          "DBM submits proposed ₱5.024-trillion 2022 budget",
          // The tokenizer should handle Indian language etc. non-spacing and combining marks
          "Kanakadasa's \"Ramadhanya Charite\" (ರಾಮಧಾನ್ಯ ಚರಿತೆ ) is a rare work.",
          // Be okay with spaced acronyms
          "Today, U. A. E. is a rich country.",
          // California flag: 🏴󠁵󠁳󠁣󠁡󠁿
          "\uD83C\uDFF4\uDB40\uDC75\uDB40\uDC73\uDB40\uDC63\uDB40\uDC61\uDB40\uDC7F",
          "when one goes 'tisk tisk' at something",
  };

  private final String[][] emojiGold = {
          { "\uD83D\uDE09", "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD21", "\uD83C\uDDE6\uD83C\uDDFA", "\uD83C\uDF7A" },
          { "\uD83D\uDC66\uD83C\uDFFB", "\uD83D\uDC67\uD83C\uDFFF" },
          { "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", "\uD83E\uDDC0" },
          { "\u00AE", "\u203C", "\u2198", "\u231A", "\u2328", "\u23F0", "\u2620", "\u26BD", "\u2705", "\u2757" },
          { "⚠", "⚠️", "⚠︎", "❤️", "❤"},
          { "\uD83D\uDC69\u200D⚖", "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83C\uDFA4" },
          { "555-0199" },
          { "555‒0199" },
          {"DBM", "submits", "proposed", "₱", "5.024", "-", "trillion", "2022", "budget" },
          { "Kanakadasa", "'s", "\"", "Ramadhanya", "Charite", "\"", "(", "ರಾಮಧಾನ್ಯ", "ಚರಿತೆ", ")", "is", "a", "rare", "work", "." },
          { "Today", ",", "U.", "A.", "E.", "is", "a", "rich", "country", "." },
          // California flag: 🏴󠁵󠁳󠁣󠁡󠁿
          { "\uD83C\uDFF4\uDB40\uDC75\uDB40\uDC73\uDB40\uDC63\uDB40\uDC61\uDB40\uDC7F" },
          { "when", "one", "goes", "'", "tisk", "tisk", "'", "at", "something" },
  };

  @Test
  public void testEmoji() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("invertible");
    runOnTwoArraysWithOffsets(tokFactory, emojiInputs, emojiGold);
    runAgainstOrig(tokFactory, emojiInputs);
    assertEquals(1, "\uD83D\uDCF7".codePointCount(0, 2));
    assertEquals(2, "❤️".codePointCount(0, 2));
  }


  private final String[] tweetInputs = {
          "Happy #StarWars week! Ever wonder what was going on with Uncle Owen's dad? Check out .@WHMPodcast's rant on Ep2 https://t.co/9iJMMkAokT",
          "RT @BiIlionaires: #TheForceAwakens inspired vehicles are a big hit in LA.",
          "“@people: A woman built the perfect #StarWars costume for her dog https://t.co/VJRQwNZB0t https://t.co/nmNROB7diR”@guacomole123",
          "I would like to get a 13\" MB Air with an i7@1,7GHz",
          "So you have audio track 1 @145bpm and global project tempo is now 145bpm",
          "I know that the inside of the mall opens @5am.",
          "I have ordered Bose Headfones worth 300USD. Not 156bpmt. FCPX MP4 playback choppy on 5k iMac",
          "RT @Suns: What happens when you combine @50cent, #StarWars and introductions at an @NBA game? This.",
          "RT @ShirleyHoman481: '#StarWars' Premiere Street Closures Are “Bigger Than the Oscars”: Four blocks of Hollywood Blvd. -- from Highland… ht…",
          "In 2009, Wiesel criticized the Vatican for lifting the excommunication of controversial bishop Richard Williamson, a member of the Society of Saint Pius X.",
          "RM460.35 million",
          "I like Amb. McFaul.",
          "including eight honorary LL.D.s (Doctorate of Laws)",
          "I is less than Br. The marked reduction is predicted.",
          "I met A. I. Markov to begin.",
          "states (loc. cit.) that",
          "U.S. vs. The World",
          "for max. 14 days",
          "stems reach a max. of 3.5 m",
          "for min. 14 days",
          "stems reach a min. of 3.5 m",
          "Apple released the iPhone 11 Pro Max. The iPhone family expanded.",
          "C'mon, c’mon.",
          "phone: 47-42-17-11; fax, 47-42-80-44 or 47-42-18-81;",
          "I installed SPSS28.0.",
  };

  private final String[][] tweetGold = {
          { "Happy", "#StarWars", "week", "!", "Ever", "wonder", "what", "was", "going", "on", "with", "Uncle",
                  "Owen", "'s", "dad", "?", "Check", "out", ".@WHMPodcast", "'s", "rant", "on", "Ep2",
                  "https://t.co/9iJMMkAokT" },
          { "RT", "@BiIlionaires", ":", "#TheForceAwakens", "inspired", "vehicles", "are", "a", "big", "hit", "in", "LA", "." },
          { "“", "@people", ":", "A", "woman", "built", "the", "perfect", "#StarWars", "costume", "for", "her", "dog",
                  "https://t.co/VJRQwNZB0t", "https://t.co/nmNROB7diR", "”", "@guacomole123" },
          { "I", "would", "like", "to", "get", "a", "13", "\"", "MB", "Air", "with", "an", "i7", "@", "1,7", "GHz" },
          { "So", "you", "have", "audio", "track", "1", "@", "145", "bpm", "and", "global", "project", "tempo", "is",
                  "now", "145", "bpm" },
          { "I", "know", "that", "the", "inside", "of", "the", "mall", "opens", "@", "5", "am", "." },
          { "I", "have", "ordered", "Bose", "Headfones", "worth", "300", "USD", ".", "Not", "156bpmt", ".",
            "FCPX", "MP4", "playback", "choppy", "on", "5k", "iMac" },
          { "RT", "@Suns", ":", "What", "happens", "when", "you", "combine", "@50cent", ",", "#StarWars", "and",
                  "introductions", "at", "an", "@NBA", "game", "?", "This", "." },
          { "RT", "@ShirleyHoman481", ":", "'", "#StarWars", "'", "Premiere", "Street", "Closures", "Are", "“",
                  "Bigger", "Than", "the", "Oscars", "”", ":", "Four", "blocks", "of", "Hollywood", "Blvd.", "--",
                  "from", "Highland", "…", "ht", "…" },
          // Should really be "Saint Pius X ." but unclear how to achieve.
          { "In", "2009", ",", "Wiesel", "criticized", "the", "Vatican", "for", "lifting", "the", "excommunication",
                  "of", "controversial", "bishop", "Richard", "Williamson", ",", "a", "member", "of", "the",
                  "Society", "of", "Saint", "Pius", "X." },
          { "RM", "460.35", "million" },
          { "I", "like", "Amb.", "McFaul", "." },
          { "including", "eight", "honorary", "LL.D.s", "(", "Doctorate", "of", "Laws", ")" },
          { "I", "is", "less", "than", "Br.", ".", "The", "marked", "reduction", "is", "predicted", "." },
          { "I", "met", "A.", "I.", "Markov", "to", "begin", "." },
          { "states", "(", "loc.", "cit.", ")", "that" },
          { "U.S.", "vs.", "The", "World" },
          { "for", "max.", "14", "days" },
          { "stems", "reach", "a", "max.", "of", "3.5", "m" },
          { "for", "min.", "14", "days" },
          { "stems", "reach", "a", "min.", "of", "3.5", "m" },
          { "Apple", "released", "the", "iPhone", "11", "Pro", "Max", ".", "The", "iPhone", "family", "expanded", "." },
          { "C'm", "on", ",", "c’m", "on", "." },
          { "phone", ":", "47-42-17-11", ";", "fax", ",", "47-42-80-44", "or", "47-42-18-81", ";" },
          { "I", "installed", "SPSS", "28.0", "." },
  };

  @Test
  public void testTweets() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("invertible");
    runOnTwoArrays(tokFactory, tweetInputs, tweetGold);
    runAgainstOrig(tokFactory, tweetInputs);
  }

  private final String[] hyphenInputs = {
          // Text starting with BOM (should be deleted), words with soft hyphens and non-breaking space.
          "\uFEFFThis is hy\u00ADphen\u00ADated and non-breaking spaces: 3\u202F456\u202F473.89",
          // Test that some cp1252 that shouldn't be in file is normalized okay
          "\u0093I need \u008080.\u0094 \u0082And \u0085 dollars.\u0092",
          "Charles Howard ''Charlie’' Bridges and Helen Hoyle Bridges",
          "All energy markets close at 1 p.m. except Palo Verde electricity futures and options, closing at\n" +
                  "12:55.; Palladium and copper markets close at 1 p.m.; Silver markets close at 1:05 p.m.",
          "BHP is `` making the right noises.''",
          "``There's a saying nowadays,'' he said. ```The more you owe, the longer you live.' It means the mafia " +
                  "won't come until we have money.''\n",
          "\"Whereas strategic considerations have to be based on 'real- politick' and harsh facts,\" Saleem said.",
          "F*ck, cr-p, I met Uchenna Nnobuko yesterday.",  // remnant of "dunno" should not match prefix
          // "bad?what opinion?kisses", // Not yet sure whether to break on this one (don't on periods)
          "I´m wrong and she\u00B4s right, anna_goldie.",
          "Left Duxbury Ave. and read para. 13.8 and attached 3802.doc.",
          "Phone:86-0832-2115188. DIAL `M' FOR MANNERS.",
          "The athlete didnt see his son, Alex. In the Shu'la neighborhood.",
          "China is very high tech. Moose are wont to roam here.",
  };

  private final String[][] hyphenGold = {
          { "This", "is", "hyphenated", "and", "non-breaking", "spaces", ":", "3456473.89" },
          { "``", "I", "need", "€", "80", ".", "''", "`", "And", "...", "dollars", ".", "'" },
          { "Charles", "Howard", "``", "Charlie", "''", "Bridges", "and", "Helen", "Hoyle", "Bridges" },
          { "All", "energy", "markets", "close", "at", "1", "p.m.", "except", "Palo", "Verde", "electricity", "futures",
                  "and", "options", ",", "closing", "at", "12:55", ".", ";", "Palladium", "and", "copper", "markets",
                  "close", "at", "1", "p.m.", ";", "Silver", "markets", "close", "at", "1:05", "p.m." },
          { "BHP", "is", "``", "making", "the", "right", "noises", ".", "''" },
          { "``", "There", "'s", "a", "saying", "nowadays", ",", "''", "he", "said", ".", "``", "`", "The", "more", "you",
                  "owe", ",", "the", "longer", "you", "live", ".", "'", "It", "means", "the", "mafia",
                  "wo", "n't", "come", "until", "we", "have", "money", ".", "''" },
          { "``", "Whereas", "strategic", "considerations", "have", "to", "be", "based", "on",
                  "`", "real", "-", "politick", "'", "and", "harsh", "facts", ",", "''", "Saleem", "said", "." },
          { "F*ck", ",", "cr-p", ",", "I", "met", "Uchenna", "Nnobuko", "yesterday", "." },
          // { "bad", "?", "what", "opinion", "?", "kisses" },
          { "I", "'m", "wrong", "and", "she", "'s", "right", ",", "anna_goldie", "." },
          { "Left", "Duxbury", "Ave.", "and", "read", "para.", "13.8", "and", "attached", "3802.doc", "." },
          { "Phone", ":", "86-0832-2115188", ".", "DIAL", "`", "M", "'", "FOR", "MANNERS", "." },
          { "The", "athlete", "did", "nt", "see", "his", "son", ",", "Alex", ".", "In", "the", "Shu'la", "neighborhood", "." },
          { "China", "is", "very", "high", "tech", ".", "Moose", "are", "wont", "to", "roam", "here", "." },
   };

  @Test
  public void testHyphensQuoteAndBOM() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("normalizeCurrency=false,invertible,ptb3Escaping");
    runOnTwoArrays(tokFactory, hyphenInputs, hyphenGold);
    runAgainstOrig(tokFactory, hyphenInputs);
  }


  private final String[] apostropheInputs = {
          "Th'enchanting tale",
          "Mu'min and Yāghmūrasen know Mu'min‘s tribe, the Koumïa",
          "The Qur’an's Allah",
          "I'D'VE THOUGHT THAT THEY'D'VE LIKED IT.",
          "I'd've thought that they'd've liked it.",
          "J'aime les enfants",
          "Y'know y'all want it",
          "Wet'n'wild Las Vegas", // unclear this should really be one token, but seems only consistent treatment
          "in \"Retour de L'U.R.S.S.\" in 1936",
          "wedding do's and don'ts,",
          "``'Tain't mine and 'tain't enough.''",
          "``The only thing tainted about money is t'ain't mine, and t'ain't enough.''",
          "I caught BA.2.12.1 and BA.5 while reading about X.500",
          "IT IS ON P.72",
          "ʻAbdu'l-Bahá, born ʻAbbás, was the eldest son of Baháʼu'lláh and served as head of the Baháʼí Faith from 1892 until 1921.",
          "Let's shoot'em up",
          "In Louis L'Amour's 1985 historical novel",
          "Grace O'Malley's Castle.",
          "The Z-R relationship was Z = 408R9.20",
          "I use SPSS28.0 to measure Lee’s指数 as 其IC50约为4.814μmol / L",
          "Some people write BA2.12.1. Tests were DM899.00.",
          "@Insanomania They do... Their mentality doesn't :(\n",
          "Hey, c'mon, I need some $$$$.",

  };

  private final String[][] apostropheGold = {
          { "Th'", "enchanting", "tale" },
          { "Mu'min", "and", "Yāghmūrasen", "know", "Mu'min", "`s", "tribe", ",", "the", "Koumïa" },
          { "The", "Qur'an", "'s", "Allah" },
          { "I", "'D", "'VE", "THOUGHT", "THAT", "THEY", "'D", "'VE", "LIKED", "IT", "." },
          { "I", "'d", "'ve", "thought", "that", "they", "'d", "'ve", "liked", "it", "." },
          { "J'aime", "les", "enfants" },
          { "Y'", "know", "y'", "all", "want", "it" },
          { "Wet'n'wild", "Las", "Vegas" },
          { "in", "``", "Retour", "de", "L'U.R.S.S.", "''", "in", "1936" },
          { "wedding", "do", "'s", "and", "do", "n'ts", "," },
          { "``", "'T", "ai", "n't", "mine", "and", "'t", "ai", "n't", "enough", ".", "''" },
          { "``", "The", "only", "thing", "tainted", "about", "money", "is", "t'", "ai", "n't", "mine", ",", "and", "t'", "ai", "n't", "enough", ".", "''" },
          { "I", "caught", "BA.2.12.1", "and", "BA.5", "while", "reading", "about", "X.500" },
          { "IT", "IS", "ON", "P.72" }, // really this should be split, but it currently isn't 🤷
          { "ʻAbdu'l", "-", "Bahá", ",", "born", "ʻAbbás", ",", "was", "the", "eldest", "son", "of", "Baháʼu'lláh", "and",
                  "served", "as", "head", "of", "the", "Baháʼí", "Faith", "from", "1892", "until", "1921", "."
          },
          { "Let", "'s", "shoot", "'em", "up" },
          { "In", "Louis", "L'Amour", "'s", "1985", "historical", "novel" },
          { "Grace", "O'Malley", "'s", "Castle", "." },
          { "The", "Z-R", "relationship", "was", "Z", "=", "408R", "9.20" },
          { "I", "use", "SPSS", "28.0", "to", "measure", "Lee's指数", "as", "其IC50约为", "4.814", "μmol", "/", "L" }, // could use \p{Latin} more in patterns?
          { "Some", "people", "write", "BA", "2.12.1", ".","Tests", "were", "DM", "899.00", "." },
          { "@Insanomania", "They", "do", "...", "Their", "mentality", "does", "n't", ":(" },
          { "Hey", ",", "c'm", "on", ",", "I", "need", "some", "$$$$", "." },

  };

  @Test
  public void testApostrophes() {
    // Note that this is running with "latex" normalization of quotes!
    TokenizerFactory<CoreLabel> tokFactory =
            PTBTokenizer.coreLabelFactory("invertible,ptb3Escaping,normalizeCurrency=false,normalizeParentheses=false");
    runOnTwoArrays(tokFactory, apostropheInputs, apostropheGold);
    runAgainstOrig(tokFactory, apostropheInputs);
  }

}
