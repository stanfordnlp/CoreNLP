# Variables and subroutines for tagging temporal expressions
# Copyright 2001 - The MITRE Corporation

use strict;

package      TempEx;
use vars     qw(@ISA @EXPORT @EXPORT_OK $VERSION);
require      Exporter;
@ISA       = qw(Exporter);
@EXPORT    = qw($TE_Loaded $TE_DEBUG $TE_HeurLevel
		TE_TagTIMEX TE_AddAttributes Date2ISO
		SetLexTagName printNonConsumingTIMEXes);
@EXPORT_OK = qw(Date2DOW Week2Date Word2Num
		%TE_Ord2Num %Month2Num $TEmonthabbr);
$VERSION   = 1.05;


########################################
## Global variable declarations for time expressions

use vars qw($TE_Loaded $TE_DEBUG $TE_HeurLevel);
use vars qw(%TE_Ord2Num %Month2Num $TEmonthabbr);

my($TEday, $TEmonth, $TERelDayExpr);
my($TEFixedHol, $TENthDOWHol, $TELunarHol, $TEDayHol);
my(%FixedHol2Date, %NthDOWHol2Date);
my(%Day2Num);
my(%TE_TimeZones, %TE_Season, %TE_Season2Month);
my(@TE_ML, @TE_CumML);
my(%TE_DecadeNums);
my($TEOrdinalWords, $TENumOrds, $OT, $CT);
my(%NumWords, @UnitWords, @EndUnitWords);
my($LexTagName, $OTCD, $OTNNP);

$TE_Loaded    = 1;
$TE_HeurLevel = 3;
$TE_DEBUG     = 0;

$TEday        = "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)";
$TEmonth      = "(january|february|march|april|may|june|july|august|september|october|november|december)";
$TEmonthabbr  = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)";
$TERelDayExpr = "(today|yesterday|tomorrow|tonight|tonite)";

$TEFixedHol   = "(new\\s+year|inauguration|valentine|ground|candlemas|patrick|fool|(saint|st\.)\\s+george|walpurgisnacht|may\\s+day|beltane|cinco|flag|baptiste|canada|dominion|independence|bastille|halloween|allhallow|all\\s+(saint|soul)s|day\\s+of\\s+the\\s+dead|fawkes|veteran|christmas|xmas|boxing)";
$TENthDOWHol  = "(mlk|king|president|canberra|mother|father|labor|columbus|thanksgiving)";
$TELunarHol = "(easter|palm\\s+sunday|good\\s+friday|ash\\s+wednesday|shrove\\s+tuesday|mardis\\s+gras)";
$TEDayHol   = "(election|memorial|C?Hanukk?ah|Rosh|Kippur|tet|diwali|halloween)";

%FixedHol2Date = ("newyear",   "0101",    "inauguration", "0120",
		  "valentine", "0214",
                  "ground",    "0202",    "candlemas",    "0202",
		  "patrick",   "0317",    "fool",         "0401",
		  "st\.george","0423",    "saintgeorge",  "0423",
		  "walpurgisnacht", "0430",
		  "mayday",    "0501",    "beltane",      "0501",
		  "cinco",     "0505",    "flag",         "0614",
		  "baptiste",  "0624",    "dominion",     "0701",
		  "canada",    "0701",
		  "independence", "0704", "bastille",     "0714",
		  "halloween", "1031",    "allhallow",    "1101",
		  "allsaints",  "1101",   "allsouls",     "1102",
		  "dayofthedead", "1102",
		  "fawkes",    "1105",    "veteran",      "1111",
		  "christmas", "1225",    "xmas",         "1225"   );

# Format is month-DOW-nth
%NthDOWHol2Date   = ("mlk",        "1-1-3",  "king",         "1-1-3",
		     "president",  "2-1-3",  "canberra",     "3-1-3",
		     "mother",       "5-7-2",
		     "father",     "6-7-3",  "labor",        "9-1-1",
		     "columbus",   "10-1-2", "thanksgiving", "11-4-4");


%Month2Num    = ("jan" => 1, "feb" =>  2, "mar" =>  3, "apr" =>  4,
		 "may" => 5, "jun" =>  6, "jul" =>  7, "aug" =>  8,
		 "sep" => 9, "oct" => 10, "nov" => 11, "dec" => 12);
%Day2Num      = ("sunday"    => 0, "monday"   => 1, "tuesday" => 2,
		 "wednesday" => 3, "thursday" => 4, "friday"  => 5,
		 "saturday"  => 6);


%TE_TimeZones    = ("E" => -5, "C" => -6, "M" => -7, "P" => -8);

%TE_Season       = ("spring" => "SP", "summer" => "SU",
		 "autumn" => "FA", "fall" => "FA", "winter" => "WI");

%TE_Season2Month = ("SP" => 4, "SU" => 6, "FA" => 9, "WI" => 12);

@TE_ML     = (0, 31, 28, 31,  30,  31,  30,  31,  31,  30,  31,  30, 31);
@TE_CumML  = (0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365);

%TE_Ord2Num = ("first", 1, "second", 2, "third", 3, "fourth", 4,
	       "fifth", 5, "sixth", 6, "seventh", 7, "eighth", 8,
	       "ninth", 9, "tenth", 10, "eleventh", 11, "twelfth", 12,
	       "thirteenth", 13, "fourteenth", 14, "fifteenth", 15,
	       "sixteenth", 16, "seventeenth", 17, "eighteenth", 18,
	       "nineteenth", 19, "twentieth", 20, "twenty-first", 21,
	       "twenty-second", 22, "twenty-third", 23, "twenty-fourth", 24,
	       "twenty-fifth", 25, "twenty-sixth", 26, "twenty-seventh", 27,
	       "twenty-eighth", 28, "twenty-ninth", 29, "thirtieth", 30,
	       "thirty-first", 31);

%TE_DecadeNums = ("twen", 2, "thir",  3, "for",  4, "fif",  5, 
		  "six",  6, "seven", 7, "eigh", 8, "nine", 9);

# Need these in order - first must come after 21st
$TEOrdinalWords = "(tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|twenty-first|twenty-second|twenty-third|twenty-fourth|twenty-fifth|twenty-sixth|twenty-seventh|twenty-eighth|twenty-ninth|thirtieth|thirty-first|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)";
$TENumOrds = "([23]?1-?st|11-?th|[23]?2-?nd|12-?th|[12]?3-?rd|13-?th|[12]?[4-90]-?th|30-?th)";

$OT = "(<[^\/][^>]*>)";
$CT = "(<\\\/[^>]*>)";

%NumWords
    = ("dozen" => 12,     "score" => 20,     "gross" => 144,
       "zero" => 0,       "oh" => 0,         "a"   =>  1, 
       "one" => 1,        "two" =>  2,       "three" =>  3, 
       "four" => 4,       "five" =>  5,      "six" =>  6,
       "seven" => 7,      "eight" =>  8,     "nine" =>  9,
       "ten" => 10,       "eleven" => 11,    "twelve" =>  12,
       "thirteen" => 13,  "fourteen" => 14,  "fifteen" =>  15, 
       "sixteen" => 16,   "seventeen" => 17, "eighteen" =>  18,
       "nineteen" => 19,  "twenty" => 20,    "thirty" =>  30,
       "forty" => 40,     "fifty" => 50,     "sixty" =>  60,
       "seventy" => 70,   "eighty" => 80,    "ninety" =>  90,
       "hundred" => 100,  "thousand" => 1000,"million" =>  1000000,
       "billion" => 1000000000, "trillion" => 1000000000000);

@UnitWords = qw(trillion billion million thousand hundred);
@EndUnitWords = qw(gross dozen score);


#################################
##  Variables for jfrank code  ##
#################################

my $useDurationChanges = 1;	#basically, if set to 0, none of the duration code does anything
my $printNCTs = 1;		# if set to 1, it will print out the non-consuming TIMEXES at the end of the printout


#wordtonum.pl variables

my %word2Num = ("zero" => 0,
		"one" => 1,"two" => 2,"three" => 3,"four" => 4,"five" => 5,
		"six" => 6,"seven" => 7,"eight" => 8,"nine" => 9,"ten" => 10,
		"eleven" => 11,"twelve" => 12,"thirteen" => 13,
		"fourteen" => 14,"fifteen" => 15,"sixteen" => 16,
		"seventeen" => 17,"eighteen" => 18,"nineteen" => 19,
		"twenty" => 20,"thirty" => 30,"forty" => 40,"fifty" => 50,
		"sixty" => 60,"seventy" => 70,"eighty" => 80,"ninety" => 90,
		"hundred" => 100,"thousand" => 1000,
		"million" => 1000000,"billion" => 1000000000,"trillion" => 1000000000000);

my $unitNums = "(one|two|three|four|five|six|seven|eight|nine)";
my $uniqueNums = "(ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen)";
my $tensNums = "(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)";
my $higherNums = "(hundred|thousand|million|billion|trillion)";

####

my $numberTerm = "(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|trillion|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth|fortieth|fiftieth|sixtieth|seventieth|eightieth|ninetieth|hundreth|thousandth|millionth|billionth|trillionth)";

my $TE_Units = "(second|minute|hour|day|month|year|week|decade|centur(y|ie)|milleni(um|a))";

my $ordUnitNums = "(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)";
my $ordOtherNums = "(tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth|fortieth|fiftieth|sixtieth|seventieth|eightieth|ninetieth|hundreth|thousandth|millionth|billionth|trillionth)";

my %ordWord2Num = ("zeroeth" => 0,
		   "first" => 1,"second" => 2,"third" => 3,"fourth" => 4,"fifth" => 5,
		   "sixth" => 6,"seventh" => 7,"eighth" => 8,"ninth" => 9,"tenth" => 10,
		   "eleventh" => 11,"twelfth" => 12,"thirteenth" => 13,
		   "fourteenth" => 14,"fifteenth" => 15,"sixteenth" => 16,
		   "seventeenth" => 17,"eighteenth" => 18,"nineteenth" => 19,
		   "twentieth" => 20,"thirtieth" => 30,"fortieth" => 40,"fiftieth" => 50,
		   "sixtieth" => 60,"seventieth" => 70,"eightieth" => 80,"ninetieth" => 90,
		   "hundredth" => 100,"thousandth" => 1000,
		   "millionth" => 1000000,"billionth" => 1000000000,"trillionth" => 1000000000000);

#for getUniqueTID()
my $highestTID = 1;

# special variables relating to the beginPoint and endPoint values 
my $tiddef = 't99999';	#current default name for unspecified tids
my $tidDCT = 't0';	#current id for Document Creation Time

my $useUnspecTID = 1;	#if 1, we assign unspecified tids the value $tiddef
my $useDCT = 1;		#if 1 and $useUnspecTID = 1, then use 't0' instead of $tiddef 

my $unspecTIDVal = &getUnspecifiedTID();

# -if both tids exist, use them, i.e. "from 2002 until 2005"
# -if only one tid exists, use it, infer second time from duration value, then create non-consuming tid for it
# -if neither exists, use $tiddef as either beginPoint or endPoint, depending on the particular pattern...ignore
#  the other tid for now, since it can be easily inferred later anyway
#	-note that if $useUnspecTID = 0, we don't even create these non-consuming tids		


# some of these vars need to also be changed in: TimeTag.pl
my $valTagName = "VAL";
my $tidTagName = "tid";
my $tever = 3;	#TIMEX version


my @nonConsumingTIMEXes;
my $numNonConsumingTIMEXes = 0;

#################################
##   End of jfrank variables   ##
#################################



## Initialization
SetLexTagName("lex");

########################################
# Allows user to change name of POS tag
sub SetLexTagName {
    my($TagName);
    ($TagName) = @_;

    $LexTagName = $TagName;
    $OTCD = "<lex[^>]*pos=\\\"?CD[^>]*>";
    $OTNNP = "<lex[^>]*pos=\\\"?NNP?[^>]*>";
    
} # SetLexTagName


########################################
sub TE_TagTIMEX (\$) {

    my($string);
    ($string) = @_;
    
    my($temp1, $temp2, $temp3, $temp4);
    my($rest, $Count, $MCount, $year, $Nyear, $testyear);
    my($TE1, $TE2, $Tag2, $MixedCase);
    my($mid1, $mid2, $b4, $copy);
   
    
    if(($string =~ /[A-Z]/o) && ($string =~ /[a-z]/o)) { $MixedCase = 1; }
    else { $MixedCase = 0; }

    $rest = $string;
    $string = "";
    $Count  = 0;
    $MCount = 0;
    while($rest =~ /($OT+((mid-$CT*$OT*)?(\d\d\d(\d))s?)$CT+)/) {
	$Count++;
	$string  .= $`;
	$year     = $1;
	$Nyear    = $7;
	$testyear = $8;
	$rest     = $';

	if(($Nyear > 1649) && ($Nyear < 2100) &&
	   !($rest =~ /\A\s+$OT+\w+$CT+\s+($OT+(daylight|standard)$CT+\s+)?$OT+time$CT+/ois) &&
	   !($rest =~ /\A\s+$OT*$OTNNP/os)) {
	    if(($testyear == 0) &&
	       ($rest =~ /(\A$OT+\'s$CT)/oi)) {
		$year .= $1;
		$rest = $';
	    }
	    $year = "<TIMEX$tever TYPE=\"DATE\">$year</TIMEX$tever>"; 
	}
	$string  .= $year;
    }
    $string  .= $rest;
    ###########################################
    ###  Code added by jfrank - July-August 2004  ###
    ###########################################

    # ADD DURATIONS TAGS AND ADD ATTRIBUTES TO DURATION TAGS #	

    $string = &deliminateNumbers($string);

#    my $numString = '(a? (few|couple))?$OT\+ (\d+|NUM_START.*?NUM_END)';  #will match either a numeric or word/based number
    my $numString = '(\d+|NUM_START.*?NUM_END)';  #will match either a numeric or word/based number
    my $numOrdString = '(((\d*)(1st|2nd|3rd|[4567890]th))|NUM_ORD_START.+?NUM_ORD_END)';


    my $curDurValue;
    my $curPhrase;

    #ADD NEW DURATION PATTERNS HERE
    #Note: each added pattern here requires an added pattern in the expressionToDuration and expressionToPoints functions

    my @matchPatterns;
    #i.e. "3-year","four-month old"
       $matchPatterns[0] = "($OT\+$numString(-|$CT\+\\s$OT\+)$TE_Units((-|$CT\+\\s$OT\+)old)?$CT\+)";


    #i.e. "the past twenty four years"
       $matchPatterns[1] = "($OT\+the$CT\+\\s$OT\+(\[pl\]ast|next)$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)";


    #i.e. "another 3 years"
       $matchPatterns[2] = "($OT+another$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)";


    #i.e. "the 2 months following the crash", "for ten days before leaving" 
    #### NEED TO FIX THIS, right now it doesn't include "the crash" or "leaving"...need to be able to recognize NPs and VPs using POS tags
#      $matchPatterns[3] = "($OT\+(the|for|in)$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+\\s$OT\+(since|after|following|before|prior$CT\+\\s$OT\+to|previous$CT\+\\s$OT\+to)$CT\+)"; 
       $matchPatterns[3] = "($OT\+the$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)"; 
    ####

    #i.e. "the first 9 months of 1997"
#       $matchPatterns[4] = "($OT\+the$CT\+\\s$OT\+(NUM_ORD_STARTfirstNUM_ORD_END|initial|last|final)$CT\+\\s($OT\+$numString$CT\+)\\s$OT\+$TE_Units(s)?$CT\+\\s$OT\+of$CT\+)"; 
       $matchPatterns[4] = "($OT\+the$CT\+\\s$OT\+(NUM_ORD_STARTfirstNUM_ORD_END|initial|last|final)$CT\+\\s($OT\+$numString$CT\+)\\s$OT\+$TE_Units(s)?$CT\+)"; 
   #### NEED TO FIX THIS, RIGHT NOW NEEDS TO INCLUDE THE FOLLOWING, like "1997" or "December" or "her life"


   #i.e. "the fifth straight year", "the third straight month in a row", "the ninth day consecutively"
       $matchPatterns[5] = "($OT\+the$CT\+\\s$OT\+$numOrdString$CT\+\\s$OT\+(straight|consecutive)$CT\+\\s$OT\+$TE_Units$CT\+(\\s($OT\+in$CT\+\\s$OT\+a$CT\+\\s$OT\+row$CT\+|$OT\+consecutively$CT\+))?)"; 
       $matchPatterns[6] = "($OT\+the$CT\+\\s$OT\+$numOrdString$CT\+\\s$OT\+$TE_Units$CT\+\\s$OT\+(straight|consecutively|in$CT\+\\s$OT\+a$CT\+\\s$OT\+row)$CT\+)";

   #jbp i.e. "no more than 60 days" "no more than 20 years"
       $matchPatterns[7] =  "($OT\+(n|N)o$CT\+\\s$OT\+more$CT\+\\s$OT\+than$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)";

   #jbp i.e. "no more than 60 days" "no more than 20 years"
       $matchPatterns[8] =  "($OT\+(m|M)ore$CT\+\\s$OT\+than$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)";

   #jbp i.e. "at least sixty days"
       $matchPatterns[9] =  "($OT\+(a|A)t$CT\+\\s$OT\+least$CT\+\\s$OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)";

   #i.e. "four years"
       $matchPatterns[10] = "($OT\+$numString$CT\+\\s$OT\+$TE_Units(s)?$CT\+)";

   #i.e. "a decade", "a few decades", NOT "a few hundred decades"
       $matchPatterns[11] = "($OT\+a$CT\+\\s($OT\+(few|couple|couple$CT\+\\s$OT\+of)$CT\+\\s)?$OT\+$TE_Units(s)?$CT\+)";
   

    #Do substitutions
    my $curPattern;

    foreach $curPattern (@matchPatterns){
EACHPAT: while ($string =~ /$curPattern/g){
		$curPhrase = $1;

		# This is a somewhat quick fix to the problem where in the pattern finding, $numString will include
		#   something like "NUM_START...NUM_END......NUM_START...NUM_END", with the first NUM_START and the last
		#   NUM_END supposedly enclosing just one number, when obviously that's not the case...this ends up screwing
		#   up the expressionToDuration function.
		# PROBLEM - This does create a problem with the case of "the first five minutes", because "first" ends up
		#	getting tags around it, which gets stopped here...this doesn't create a terrible problem, but
		#	it should still be fixed
		if ($curPhrase =~ /(NUM_START|NUM_ORD_START).+(NUM_START|NUM_ORD_START)/){
			next EACHPAT;
		}

		my $bef = $`;

		my $helper = "";
		if ($bef =~ /<TIMEX$tever[^>]*>(.*?)$/){
			$helper = $1;	#helper var is everything between current pattern and immediately-preceding TIMEX tag
		}

		if (($helper =~ /<\/TIMEX$tever>/) || ($helper eq "")){	# so we don't embed tags ### CHECK ON THIS ########################
			unless ($curPhrase =~ /^<TIMEX$tever[^>]*TYPE=\"DURATION\"[^>]*>/){   #already has duration tag
				$curDurValue = &expressionToDuration($curPhrase);
				my $pointsString = &expressionToPoints($curPhrase);

				my $bp = "";
				my $ep = "";
				if ($pointsString =~ /([^:]*):([^:]*)/){
					$bp = $1;
					$ep = $2;
				}

				my $beginString = "";
				my $endString = "";

				if ($bp ne ""){$beginString = " beginPoint=\"$bp\"";}   	
				if ($ep ne ""){$endString = " endPoint=\"$ep\"";}   					

				#deal with nonconsuming TIMEXes here
				my $curNCTIMEX = "";
				my $curBPTID = "";
				my $curEPTID = "";
				if (($beginString eq "") && ($endString ne "")){  #endPoint defined, not beginPoint
					$curBPTID = &getUniqueTID();
					$beginString = " beginPoint=\"$curBPTID\"";
					$curNCTIMEX = "<TIMEX$tever $tidTagName=\"$curBPTID\">";	#still need to add VAL
					$nonConsumingTIMEXes[$numNonConsumingTIMEXes] = $curNCTIMEX;
					$numNonConsumingTIMEXes++;
				} elsif (($endString eq "") && ($beginString ne "")){  #beginPoint defined, not endPoint
					$curEPTID = &getUniqueTID();
					$endString = " endPoint=\"$curEPTID\"";
					$curNCTIMEX = "<TIMEX$tever $tidTagName=\"$curEPTID\">";	#still need to add VAL
					$nonConsumingTIMEXes[$numNonConsumingTIMEXes] = $curNCTIMEX;
					$numNonConsumingTIMEXes++;
				}


				#make actual duration changes
				if ($useDurationChanges == 1){
					$string =~ s/$curPhrase/<TIMEX$tever TYPE=\"DURATION\"$beginString$endString $valTagName=\"$curDurValue\">$curPhrase<\/TIMEX$tever>/gi;
				}
			} 
 		}
   	}
    }


    #postprocessing to get rid of incorrect durations marked by the above patterns

    #this loop takes care of how we want to count "the four months after" as a duration, but don't want to count simply "four months later" as a duration
    while ($string =~ /(<TIMEX$tever[^>]*TYPE=\"DURATION\"[^>]*>($OT*$numString$CT+\s$OT+$TE_Units(s)?$CT*)<\/TIMEX$tever>)/gi){
	my $bef = $`;	
	my $aft = $';	
	my $foundString = $1;


	my $withinString = $2;
	#get rid of duration tags around something like "four months" if it ends with, i.e. "after" but is not preceded
	#  by "the", "for" or "in"

	if ($aft =~ /^$CT*\s$OT+(since|after|following|later|earlier|before|prior$CT+ $OT+to|previous$CT+ $OT+to)$CT+/i){
		unless ($bef =~ /$OT+(the|for|in)$CT+\s$OT*$/i){
			if ($useDurationChanges == 1){
				$string =~ s/$foundString/$withinString/;
			}
		} 
	}
    }
    



    #get rid of number delimiters  ###  NEED TO MAKE SURE THIS WORKS AT BEGINNING AND END
    $string =~ s/NUM_START//g;
    $string =~ s/NUM_END//g;
    $string =~ s/NUM_ORD_START//g;
    $string =~ s/NUM_ORD_END//g;


    ###########################################
    ########  End of code by jfrank  ##########
    ###########################################

    # two digit year
    $string =~ s/($OT+\'$CT+$OT+\d\d$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gsio;

    # ISO date/times
    $string =~ s/($OT+\d\d\d\d-?\d\d-?\d\d-?T\d\d(($CT*$OT*:$CT*$OT*)?\d\d)?([\+\-]\d{1,4})?$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/go;
    $string =~ s/($OT+\d\d\d\d-\d\d-\d\d$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/go;
    $string =~ s/($OT+T\d\d(:?\d\d)?(:?\d\d)?([\+\-]\d{1,4})?$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/go;

    # Slash notation
    $string =~ s/(($OT+\d\d:?\d\d$CT+\s+($OT+on$CT+\s+)?)?$OT+\d\d?\/\d\d?\/\d\d(\d\d)?$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/go;
    # Euro
    $string =~ s/($OT+\d\d?\.\d\d?\.\d\d(\d\d)?$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/go;

    # day of week
    $string =~ s/(($OT+(alternate|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?|each|next|last|this$CT+\s+$OT+coming)$CT+\s*)?$OT+$TEday(s)?$CT+(\s+$OT+(the$CT+\s+$OT+($TEOrdinalWords|$TENumOrds)|morning|afternoon|evening|night|next)s?$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gsio;

    # yesterday/today/tomorrow
    $string =~ s/((($OT+the$CT+\s+)?$OT+day$CT+\s+$OT+(before|after)$CT+\s+)?$OT+$TERelDayExpr$CT+(\s+$OT+(morning|afternoon|evening|night)$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gio;
    $string =~ s/($OT+\w+$CT+\s+)<TIMEX$tever TYPE=\"DATE\"[^>]*>($OT+(Today|Tonight)$CT+)<\/TIMEX$tever>/$1$4/gso;

    # this (morning/afternoon/evening) 
    $string =~ s/(($OT+(early|late)$CT+\s+)?$OT+this$CT+\s*$OT+(morning|afternoon|evening)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;
    $string =~ s/(($OT+(early|late)$CT+\s+)?$OT+last$CT+\s*$OT+night$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gsio;

    #added jbp: late (morning/afternoon/evening)
    $string =~ s/(($OT+(early|late)$CT+\s+)\s*$OT+(morning|afternoon|evening)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;

    # (next/last) (OrdinalWord of|DayofMonth) MonthName
    #      (DayOfMonth|OrdinalWord|OrdinalNum) ((of) year)
    if($string =~ /($TEmonth|$TEmonthabbr)/io) {
	$string =~ s/(($OT+(early|late)$CT+\s+($OT+[io]n$CT+\s+)?)?($OT+the$CT+\s+$OT+((($TEOrdinalWords|$TENumOrds)$CT+\s+$OT+)?week(end)?|beginning|start|middle|end|morning|day|afternoon|evening|night|ides|nones)$CT+\s+$OT+(of|in)$CT+\s+)?($OT+(\d\d?|next|last|($TEOrdinalWords|$TENumOrds)$CT+\s+$OT+of)$CT*\s*)*$OT+(mid-$CT*$OT*)?($TEmonth|$TEmonthabbr\.?($CT+$OT+.)?)$CT+((\s+$OT+of$CT+|$OT+,$CT+)?\s+$OT+\d{4}$CT+|\s*$OT+($TENumOrds|$TEOrdinalWords|\d\d?)$CT+(($OT+,$CT+)?\s*$OT+(\d{4}|\'$CT+$OT+\d\d)$CT+)?)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi; }
    if($MixedCase) {
	$string =~ s/<TIMEX$tever TYPE=\"DATE\">($OT+(mar|march|may|august)$CT+)<\/TIMEX$tever>/$1/go;
    }
    # !the (this(past)/next/last/)
    # (week|month|quarter|year|decade|century|spring|summer|winter|fall|autumn)
    if($string =~ /(next|last|coming|this)/io) {
	$string =~ s/(($OT+(late|early|the$CT+\s+$OT+(beginning|start|dawn|middle|end)$CT+\s+$OT+of)$CT+\s+($OT+in$CT+\s+)?)?($OT+the$CT+\s+)?$OT+(next|last|coming|this($CT+\s+$OT+(coming|past))?)$CT+\s*$OT+(week|month|quarter|year|decade|century|spring|summer|winter|fall|autumn)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi; }

    # seasons
    if($string =~ /(spring|summer|winter|fall|autumn)/io) {
	$string =~ s/(($OT+(each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?|late|early|the$CT+\s+$OT+(beginning|start|dawn|middle|end)$CT+\s+$OT+of)$CT+\s+)?($OT+in$CT+\s+$OT+the$CT+\s+)?$OT+(spring|summer|winter|fall|autumn)$CT+(\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi; }
   
    #(beginning|start|dawn|end) of year 
    if($string =~ /(beginning|start|dawn|end)/io) {
	#$string =~ s/(($OT+(each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?|late|early|the$CT+\s+$OT+(beginning|start|dawn|middle|end)$CT+\s+$OT+of)$CT+\s+)?($OT+in$CT+\s+$OT+the$CT+\s+)?(\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+))/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi; 
	}
    # holidays
    # Day/Eve
    $string =~ s/(($OT+(this($CT+\s+$OT+(coming|past))?|next|last|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+)?$OT+(Xmas|Christmas|Thanksgiving)$CT+(\s*$OT+(Day|Eve)$CT+)?(($OT+,$CT+)?\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi;
    # Possessive's Day
    if($string =~ /\bday\b/io) {
	$string =~ s/(($OT+(this($CT+\s+$OT+(coming|past))?|next|last|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+)?$OT+((Saint|St$CT*$OT*\.)$CT+\s+$OT+(\w+|Jean$CT+\s+$OT+Baptiste)|Ground$CT+\s+$OT+Hog|April$CT+\s+$OT+Fool|Valentine|Mother|Father|Veteran|President)($CT*$OT*\'?$CT*$OT*s)?$CT+\s+$OT+Day$CT+(($OT+,$CT+)?\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;
	# Day required
	$string =~ s/(($OT+(this($CT+\s+$OT+(coming|past))?|next|last|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+)?$OT+(Flag|Memorial|Independence|Labor|Columbus|Bastille|Canberra|Dominion|Canada|Boxing|Election|Inauguration|Guy$CT+\s+$OT+Fawkes|MLK|(Martin$CT+\s+$OT+Luther$CT+\s+$OT+)?King|May|All$CT+\s+$OT+(Saint|Soul)s($CT*$OT*\')?)$CT+\s*$OT+Day$CT+(($OT+,$CT+)?\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi;
    }
    # Birthdays
    $string =~ s/(($OT*$OTNNP$OT*\w+$CT+\s+)*$OT*$OTNNP$OT*\w+$CT*$OT*\'?$CT*$OT*s$CT+\s+$OT+Birthday$CT+(($OT+,$CT+)?\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;
    #new year
    $string =~ s/(($OT+(this($CT+\s+$OT+(coming|past))?|next|last|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+)?$OT+New$CT+\s*$OT+Year$CT+$OT+\'$CT*$OT*s$CT+\s*$OT+(Day|Eve)$CT+(($OT+,$CT+)?\s+$OT+\d{4}$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;
    # holidays that are not X Day
    $string =~ s/(($OT+(this($CT+\s+$OT+(coming|past))?|next|last|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+)?$OT+(Halloween|Allhallow(ma)?s|C?Hanukk?ah|Rosh$CT+\s*$OT+Hashanah|Yom$CT+\s*$OT+Kippur|Passover|Ramadan|Cinco$CT+\s+$OT+de$CT+\s+$OT+Mayo|tet|diwali|kwanzaa|Easter($CT+\s+$OT+Sunday)?|palm$CT+\s+$OT+sunday|mardis$CT+\s+$OT+gras|shrove$CT+\s+$OT+tuesday|ash$CT+\s+$OT+wednesday|good$CT+\s+$OT+friday|walpurgisnacht|beltane|candlemas|day$CT+\s+$OT+of$CT+\s+$OT+the$CT+\s+$OT+dead)$CT+(($OT+,$CT+)?\s+($OT+of$CT+\s+)?$OT+\d\d\d\d$CT+)?)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi;
    # Generic decade
    $string =~ s/($OT+the$CT+\s+($OT+(early|late|(beginning|start|middle|end)$CT+\s+$OT+of$CT+\s+$OT+the)$CT+\s+)?$OT+(\'$CT*$OT*\d0s|(\w+teen$CT+\s+$OT+)?(twen|thir|for|fif|six|seven|eigh|nine)ties)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi;

    # some century expressions
    $string =~ s/((($OT+(late|early)$CT+\s+$OT+in$CT+\s+)?$OT+the$CT+\s+($OT+(late|early|mid-?)$CT*\s*)?)?$OT*($TENumOrds-?|$TEOrdinalWords)$CT*\s*$OT*century$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi; 

    # some quarter expressions - need to add year refs
    if($string =~ /\bquarter\b/io) {
	$string =~ s/($OT+($TENumOrds|$TEOrdinalWords)$CT+\s+$OT+quarter$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi; 
	$string =~ s/($OT+($TENumOrds|$TEOrdinalWords)-quarter$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi;
    }
    # past|present|future refs   "The past" is special case
    $string =~ s/($OT+(current|once|medieval|(the$CT+\s+$OT+)?future)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi;

## jfrank changes here - originally there was a ? after "(\s*$OT+(week|fortnight...autumn)$CT+)" below
## - this gets rid of "the past" as a special case, but this was conflicting with duration constructions such as "the past three weeks"
## - therefore, the few lines of code following the next line deal with the special case of "the past"
    $string =~ s/($OT+the$CT+\s+$OT+past$CT+(\s*$OT+(week|fortnight|month|quarter|year|decade|century|spring|summer|winter|fall|autumn)$CT+))/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;

    #special case of "the past" - this needs to come after the durations processing so it doesn't conflict with cases like "the past 12 days"
    while ($string =~ /(($OT+)the$CT+\s$OT+past$CT+)/gi){
	my $currentThePastPattern = $1;
	my $currentOpeningTags = $2;
	unless ($currentOpeningTags =~ /<TIMEX/){
		$string =~ s/$currentThePastPattern/<TIMEX$tever TYPE=\"DATE\">$currentThePastPattern<\/TIMEX$tever>/g;
	}	
    }

##
## End of jfrank additions ##


    # each|every unit
    $string =~ s/($OT+(alternate|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+$OT+(minute|hour|day|week|month|year)s?$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;

    # (unit)ly
    $string =~ s/($OT+(bi-?)?((annual|year|month|week|dai|hour|night)ly|annual)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/goi;
    
    # (unit) before last/after next
    $string =~ s/(($OT+the$CT+\s+$OT+)?$OT+(year|month|week|day|night)$CT+\s+$OT+(before$CT+\s+$OT+last|after$CT+\s+$OT+next)$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/goi;
    
    # some interval expressions
    $string =~ s/($OT+\d{4}-to$CT*$OT*-\d{4}$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/go;
    $string =~ s/($OT+(the|this|last|next|coming)$CT+\s+$OT+weekend$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/oig;

    # year|month|week|day ago|from_now
    if($string =~ /\b(ago|hence|from)\b/io) {
	$string =~ s/(($OT+(about|around|some)$CT+\s+)?($OT*$OTCD$OT*\S+$CT+\s+($OT+and$CT+\s+)?)*($OT*$OTCD$OT*\S+$CT+\s+)$OT+(year|month|week|day|decade|cenutur(y|ie))s?$CT+\s+$OT+(ago($CT+\s+$OT+(today|tomorrow|yesterday|$TEday))?|hence|from$CT+\s$OT+(now|today|tomorrow|yesterday|$TEday))$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/goi;
	$string =~ s/(($OT+(about|around|some)$CT+\s+)?$OT+(a($CT+\s+$OT+few)?|several|some|many)$CT+\s+$OT+(year|month|fortnight|moon|week|day|((little|long)$CT+\s+$OT+)?while|decade|centur(y|ie)|(((really|very)$CT+\s+$OT+)?((long$CT+$OT+,$CT+\s+$OT+)*long|short)$CT+\s+$OT+)?(life)?time)s?$CT+\s+$OT+(ago($CT+\s+$OT+(today|tomorrow|yesterday|$TEday))?|hence|from$CT+\s$OT+(now|today|tomorrow|yesterday|$TEday))$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/goi;
	$string =~ s/<TIMEX$tever[^>]*>($OT+(today|tomorrow|yesterday|$TEday)$CT+<\/TIMEX$tever>)$CT*<\/TIMEX$tever>/$1/gio;
	$string =~ s/($OT+(ages|long)$CT+\s+$OT+ago$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/goi;
    }
    
    # Now a few time expressions

    # 24 hour Euro time
    $string =~ s/(($OT+(about|around|some)$CT+\s+)?$OT+\d\d?h\d\d$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/gois;

    # hh:mm AM/PM (time zone)
    if($string =~ /[ap]\.?m\b/io) {
	$string =~ s/(($OT+(about|around|some|exactly|precisely)$CT+\s+)?($OT*(quarter|half|$OTCD$OT*\w+$CT+\s+$OT+minutes?)$CT+\s+$OT+(past|after|of|before|to)$CT+\s+)?($OT+the$CT+\s+$OT+hour$CT+\s+$OT+of$CT+\s+)?($OT+\d\d?|$OT*$OTCD$OT*\w+)$CT*($OT*:$CT*$OT*\d\d$CT*|\s+$OT*$OTCD$OT*[a-z\-]+$CT*(\s+$OT*$OTCD$OT*[a-z\-]+$CT*)?)?\s*$OT*[ap]m$CT+(\s+$OT+(universal|zulu|[a-z]+$CT+\s+$OT+(standard|daylight))$CT+\s+$OT+time$CT+)?(\s+$OT+(sharp|exactly|precisely|on$CT+\s+$OT+the$CT+\s+$OT+dot)$CT+)?)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/gois;
	$string =~ s/(($OT+(about|around|some|exactly|precisely)$CT+\s+)?($OT*(quarter|half|$OTCD$OT*((\w+(-\w+)?|\d\d?)$CT+\s+$OT+minutes?))$CT+\s+$OT+(past|after|of|before|to)$CT+\s+)?($OT+the$CT+\s+$OT+hour$CT+\s+$OT+of$CT+\s+)?($OT+\d\d?|$OT*$OTCD$OT*\w+)$CT*($OT*:$CT*$OT*\d\d$CT*|\s+$OT*$OTCD$OT*[a-z\-]+$CT*(\s+$OT*$OTCD$OT*[a-z\-]+$CT*)?)?\s*$OT*[ap]\.m\.$CT+(\s+$OT+(universal|zulu|[a-z]+$CT+\s+$OT+(standard|daylight))$CT+\s+$OT+time$CT+)?(\s+$OT+(sharp|exactly|precisely|on$CT+\s+$OT+the$CT+\s+$OT+dot)$CT+)?)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/gois;
    }
    if($string =~ /\btime\b/io) {
	$string =~ s/($OT+\d{4}$CT+\s+($OT+hours$CT+\s+($OT+,$CT+)?)?$OT+(universal|zulu|[a-z]+$CT+\s+$OT+(standard|daylight))$CT+\s+$OT+time$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/gois;
	$string =~ s/($OT+\d\d?$CT+\s+$OT+hours?$CT+(\s+$OT+\d\d?$CT+\s+$OT+minutes?$CT+)?($OT+,$CT+)?\s+$OT+(universal|zulu|local|[a-z]+$CT+\s+$OT+(standard|daylight))$CT+\s+$OT+time$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/gois;
    }
    $string =~ s/($OT+\d\d\d\d$CT+\s+$OT+h(ou)?rs?$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/gois;

    # hh:mm in the (morning|afternoon|evening)
    if($string =~ /(morning|afternoon|evening|night)/io) {
	$string =~ s/(($OT+(about|around|some|exactly|precisely)$CT+\s+)?($OT*((a$CT+\s+$OT+)?quarter|half|$OTCD$OT*\w+(-\w+)?($CT+\s+$OT+minutes?)?)$CT+\s+$OT+(past|after|of|before|to|until)$CT+\s+)?($OT+the$CT+\s+$OT+hour$CT+\s+$OT+of$CT+\s+)?$OT*$OTCD$OT*([a-z]+|\d\d?)$CT+($OT+:$CT+$OT+\d\d$CT+)?\s+$OT+(in$CT+\s+$OT+the$CT+\s+$OT+(morning|afternoon|evening)|at$CT+\s+$OT+night)$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi; }
    # o'clock
    if($string =~ /clock/io) {
	$string =~ s/(($OT+(about|around|some|exactly|precisely)$CT+\s+)?($OT*((a$CT+\s+$OT+)?quarter|half|$OTCD$OT*\w+(-\w+)?($CT+\s+$OT+minutes?)?)$CT+\s+$OT+(past|after|of|before|to|until)$CT+\s+)?($OT+the$CT+\s+$OT+hour$CT+\s+$OT+of$CT+\s+)?$OT*$OTCD$OT*([a-z]+|\d\d?)$CT+\s+$OT+o\'clock$CT+(\s+$OT+(in$CT+\s*$OT+the$CT+\s*$OT+(morning|afternoon|evening)|at$CT+\s+$OT+night)$CT+)?(\s+$OT+(sharp|exactly|precisely|on$CT+\s+$OT+the$CT+\s+$OT+dot)$CT+)?)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
 
}
    # the hour of  (WARNING:  Overlaps previous - fixed below)
    $string =~ s/(($OT+(about|around|some|exactly|precisely)$CT+\s+)?($OT*((a$CT+\s+$OT+)?quarter|half|$OTCD$OT*(\w+(-\w+)?|\d\d?)($CT+\s+$OT+minutes?)?)$CT+\s+$OT+(past|after|of|before|to|until)$CT+\s+)?$OT+the$CT+\s+$OT+hour$CT+(\s+$OT+of$CT+\s+$OT*$OTCD$OT*([a-z]+|\d\d?)$CT+)?(\s+$OT+(sharp|exactly|precisely|on$CT+\s+$OT+the$CT+\s+$OT+dot)$CT+)?)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
    # X minutes before (WARNING:  Overlaps previous - fixed below)
    if($string =~ /\bminute/io) {
	$string =~ s/(($OT+(about|around|some|exactly|precisely)$CT+\s+)?$OT*((a$CT+\s+$OT+)?quarter|half|$OTCD$OT*([a-z]+(-[a-z]+)?|\d\d?)$CT+\s+$OT+minutes?)$CT+\s+$OT+(past|after|of|before|to|until)$CT+\s+$OT*$OTCD$OT*([a-z]+|\d\d?)$CT+(\s+$OT+(exactly|precisely|on$CT+\s+$OT+the$CT+\s+$OT+dot)$CT+)?)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi; }

    # noon/midnight/zero hour/midday
    $string =~ s/(($OT+(about|around|some)$CT+\s+)?$OT+(noon|midnight|mid-?day)$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
    $string =~ s/($OT+zero$CT+\s+$OT+hour$CT+\s+$OT+(universal|zulu|[a-z]+$CT+\s+$OT+(standard|daylight))$CT+\s+$OT+time$CT+)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;

    # X hours|minutes ago
    if($string =~ /\b(ago|hence|from)\b/io) {
	$string =~ s/(($OT+(about|around|some)$CT+\s+)?($OT*$OTCD$OT*\S+$CT+\s+($OT+and$CT+\s+)?)*$OT*$OTCD$OT*\S+$CT+\s+($OT+and$CT+\s+$OT+a$CT+\s+$OT+half$CT+\s+)?$OT+hours?$CT+\s+$OT+(ago|hence|from$CT+\s+$OT+now)$CT)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
	$string =~ s/($OT+(a$CT+\s+$OT+few|several|some)$CT+\s+$OT+hours$CT+\s+$OT+(ago|hence|from$CT+\s+$OT+now)$CT)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
	$string =~ s/(($OT+about$CT+\s+)?($OT+an?$CT+\s+)?($OT+(half($CT+\s+$OT+an)?|few)$CT+\s+)?$OT+hour$CT+\s+($OT+and$CT+\s+$OT+a$CT+\s+$OT+half$CT+\s+)?$OT+(ago|hence|from$CT+\s+$OT+now)$CT)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
	$string =~ s/(($OT*(($OT*$OTCD$OT*\S+$CT+\s+($OT+and$CT+\s+)?)*$OT*$OTCD$OT*\S+|$OT+(a|several|some)($CT+\s+$OT+few)?)$CT+\s+)+$OT+minutes?$CT+\s+$OT+(ago|hence|from$CT+\s+$OT+now)$CT)/<TIMEX$tever TYPE=\"TIME\">$1<\/TIMEX$tever>/goi;
    }

    # Now (by itself)
    $string =~ s/($OT+now$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/sogi; 
    
    # ---------------------
    # Clean up tags

    # Clean up sentence tags
#    while($string =~ s/(<TIMEX[^>]*>)(<s>)/$2$1/oi) {}
#    while($string =~ s/(<\/s>)(<\/TIMEX[^>]*>)/$2$1/oi) {}
    
    # Remove embedded tags
    while($string =~ /<TIMEX$tever[^>]*>/go) {
	$temp1 = $` . $&;
	$temp2 = $';
	while(($temp2 =~ /<\/TIMEX$tever>/o) &&
	   ($temp3 = $`) && (($temp4 = $') || 1) &&
	   ($temp3 =~ /<TIMEX$tever[^>]*>/o)) {
	       $temp2 = $` . $' . $temp4;
	       $string = $temp1 . $temp2;
	   }
    }
    
    $string =~ s/<TIMEX$tever[^>]*>($OT+(\'$CT+$OT+\d\d|\w+)$CT+<\/TIMEX$tever>\s*)<\/TIMEX$tever>/$1/goi;

    # proper names in quotes
    $copy = $string;
    while($copy =~ /\S\s*($OT+\"$CT+($OT+[^a-z0-9\"]\w*$CT+\s+)*<TIMEX$tever[^>]*>($OT+[^a-z0-9\"]\w*$CT+\s+)*$OT+[^a-z0-9\"]\w*$CT+<\/TIMEX$tever>(\s*$OT+[^a-z0-9\"]\w*$CT+)*$OT+\"$CT+)/gos) {
	$temp2 = $temp1 = $1;
	if($temp1 =~ />\s/o) {
	    $temp2  =~ s/<\/?TIMEX$tever[^>]*>//go;
	    $string =~ s/$temp1/$temp2/g;
	}
    }
    
    # ---------------------
    # merge timex tags here
    
    # This loop merges DOW followed by date
    $rest   = $string;
    $string = "";
    while($rest =~/(<\/TIMEX$tever>((\s*$OT)*,($CT\s*)*)?<TIMEX$tever[^>]*>)/gsio)  {
	$rest    = $';
	$b4      = $`;
	$mid1    = $1;
	$mid2    = $2;

	$b4      =~ /(.*<TIMEX$tever[^>]*>)/sio;
	$string .= $1;
	$TE1     = $';

	$rest    =~ /(<\/TIMEX$tever>)/gio;
	$rest    = $';
	$Tag2    = $1;
	$TE2     = $`;

	if(($TE1 =~ /$TEday/io) && ($TE2 =~ /\d/o)
	   && ($TE2 =~ /$TEmonthabbr/io)) {
	    $string .= "$TE1$mid2$TE2$Tag2";   #reprints with only 1 pair of tags
	} else {
	    $string .= "$TE1$mid1$TE2$Tag2";
	}
    }
    $string .= $rest;

    # This loop merges date followed by (this|next|last) year
    $rest   = $string;
    $string = "";
    while($rest =~/(<\/TIMEX$tever>(\s*($OT+of$CT+)?\s*)<TIMEX$tever[^>]*>)/giso)  {
	$rest    = $';
	$b4      = $`;
	$mid1    = $1;
	$mid2    = $2;

	$b4      =~ /(.*<TIMEX$tever[^>]*>)/sio;
	$string .= $1;
	$TE1     = $';

	$rest    =~ /(<\/TIMEX$tever>)/gios;
	$rest    = $';
	$Tag2    = $1;
	$TE2     = $`;

	if(($TE1 =~ /$TEmonthabbr/io) && ($TE2 =~ /year/o)) {
	    $string .= "$TE1$mid2$TE2$Tag2";
	} else {
	    $string .= "$TE1$mid1$TE2$Tag2";
	}
    }
    $string .= $rest;

    #########################
    ###  added by jfrank  ###
    #########################

    #insert tids into TIMEX3 tags
    $string = &addTIDs($string);    


    ##########################
    ### end of jfrank code ###
    ##########################

    return($string);

} # TE_TagTIMEX


########################################
sub TE_AddAttributes {
    
    my($string, $RefTime, $TE, $output);
    my($temp1, $temp2, $temp3, $temp4, $temp5, $temp6, $temp7);
    my($Attributes, $Dir, $b4, $EndTag, $lead, $STag);
    my($Comment, $Tag, $AorP, $Offset);
    my($Hour, $Min, $twelve, $DorS, $Zone, $TimeZone);
    my($format, $RTday, $RTmonth);
    my($POS, $POS2, $VB, $verb2, $RelDir, $textDir);
    my($test, $offset, $Reason);
    my($Y, $M, $D, $YM, $Day, $Year, $RefM);
    my($DOW, $DOM, $RefDOM, $RefDOW);
    my($Month, $NMonth);
    my($Season, $hol);
    my($W_b4, $W_aft, $trail, $trailtext);
    my($next_word, $preceding_word);
    my($Exact, $Unit, $Extra, $junk);
    my($Val, $Val1, $FoundVal);
    my($Type, $orig_string, $TEstring);
    
    ($string, $RefTime) = @_;

    $output = "";
    $orig_string = $string;

    while($string =~ /(<\/TIMEX$tever>)/io) {
	$Attributes = "";
	
	$b4     = $`;
	$EndTag = $1;
	$string = $';

	if($b4 =~ /(.*)(<TIMEX$tever[^>]*>)/sio) {
	    $lead     = $1;
	    $output  .= $1;
	    $STag     = $2;
	    $TE = $';
	} else {
	    print STDERR "** ERROR ** - No beginning TIMEX tag!\n";
	    #print STDERR "   --Original string--\n$orig_string\n-----\n";
	    #print STDERR "   --B4--\n$b4\n--string--\n$string\n-----\n";
	    
	}

	$STag =~ /TYPE=\"(\w+)\"/oi;
	$Type = lc($1);
	
	# find the string
	$TEstring = $TE;
	$TEstring =~ s/<[^>]+>//go;
	$TEstring =~ s/\n/ /go;
	if($TE_DEBUG) { $Attributes .= " TEXT=\"$TEstring\""; }

	# find preceding, next word 
	if($output =~ /.*$OT+(\w+)$CT+/os) {
	    $junk  = $';
	    $preceding_word = lc($2);
	    if($junk =~ /\"/o) { $preceding_word = ""; }
	} else { $preceding_word = ""; }

	if($string =~ /$OT+(\w+)$CT+/os) {
	    $junk  = $`;
	    $next_word = lc($2);
	    if($junk =~ /\"/o) { $next_word = ""; }
	} else { $next_word = ""; }

	$FoundVal = 0;

	# type=date
	if($Type eq "date") {
	   	
	    # Year marker present - No refTime required
	    if($TEstring =~ /(\d\d\d\d-?\d\d-?\d\d-?(T\d\d(:?\d\d)?(:?\d\d)?([\+\-]\d{1,4})?)?)/o) {
		# ISO date
		$Val = Date2ISO($1);
		$Attributes .= " $valTagName=\"$Val\"";
		$FoundVal = 1;
	    } elsif($TEstring =~ /\d\d?[\/\.]\d\d?[\/\.]\d\d(\d\d)?/o) {
		$Val = Date2ISO($TEstring);
		$Attributes .= " $valTagName=\"$Val\"";
		$FoundVal = 1;		
	    }elsif($TEstring =~ /(\d{4})-(to-)(\d{4})/io) {
		$Val  = $1;
		$Val1 = $3;
		$Attributes .= " $valTagName=\"$Val\/$Val1\"";
		$FoundVal = 1;
	    } elsif($TEstring =~ /((\w+teen)\s+)?(twen|thir|for|fif|six|seven|eigh|nine)ties/io) {
		$temp1 = $2;
		$temp2 = $3;
		if(defined($temp1) && ($temp3 = Word2Num($temp1))) {
		    $Val = (10*$temp3) + $TE_DecadeNums{lc($temp2)};
		} else { $Val = 190 + $TE_DecadeNums{lc($temp2)};
		     }
		$Attributes .= " $valTagName=\"$Val\"";
		$FoundVal = 1;
	    } elsif(($TEstring =~ /(\d{4}|\'\d\d)/io) &&
		    ($temp1 = $1) && (($temp2 = $') || 1) &&
		    ($temp2 !~ /years/io)) {
		$Val = $temp1;
		$b4  = $`;
		$trailtext = $string;
		$trailtext =~ s/<[^>]+>//go;
		if($Val =~ /\'(\d\d)/o) {
		    if($1 < 30) { $Val = 2000 + $1; }
		    else { $Val = 1900 + $1; }
		}

		# decades
		if($TEstring =~ /(\d{3})0\'?s/io) {
		    $Val  = $1;
		    $FoundVal = 1;
		}
		elsif($TEstring =~ /\'(\d)0s/io) {
		    if($1<3) { $Val = 200 +$1; }
		    else { $Val = 190 +$1; }
		    $FoundVal = 1;
		}

	        # Handle absolute dates 
		elsif(($b4 =~ /\b($TEmonth|$TEmonthabbr)\b/io) &&
		      ($b4 !~ /\b(fool|may\s+day)/io)) {
		    $Month  = $1;
		    $Month  =~ /(\w{3})/o;
		    $Month  = lc($1);
		    $NMonth = $Month2Num{$Month};
		    $Val   .= sprintf("%02d", $NMonth);

		    if($b4 =~ /($TEOrdinalWords|$TENumOrds)\s+week(end)?\s+(of|in)/io) {
			$temp1 = lc($1);
			if($temp1 =~ /\d+/o) { $temp2 = $&; }
			else { $temp2 = $TE_Ord2Num{$temp1}; }
			if($temp2 > 4) { $Attributes .= " ERROR=\"BadWeek\""; }
			if($TEstring =~ /weekend/io) { 
			    $Val   .= sprintf("%02d", ($temp2*7)-5); }
			else { $Val   .= sprintf("%02d", ($temp2*7)-3); }
			$Val = &Date2Week($Val);
			if($TEstring =~ /weekend/io) { $Val .= "WE"; }
		    }
		    elsif($b4 =~ /(\d\d?)/o) {
			$Val .= sprintf("%02d", $1); }
		    elsif($b4 =~ /$TEOrdinalWords/io) {
			$Val .= sprintf("%02d", $TE_Ord2Num{lc($1)});
		    }
		    elsif($b4 =~ /\bides\b/io) {
			if($Month =~ /(mar|may|jul|oct)/io) {
			    $Val .= sprintf("%02d", 15); }
			else { $Val .= sprintf("%02d", 13); }
		    }
		    elsif($b4 =~ /\bnones\b/io) {
			if($Month =~ /(mar|may|jul|oct)/io) {
			    $Val .= sprintf("%02d", 7); }
			else { $Val .= sprintf("%02d", 5); }
		    }
		    if(($TEstring =~ /the\s+week\s+(of|in)/io) &&
		       ($Val =~ /\A\d{8}\Z/)) {
			$Val = &Date2Week($Val);
		    }

		} elsif($b4 =~ /$TEFixedHol/io) {
		    $hol = lc($1);
		    $hol =~ s/\s+//go;
		    $FixedHol2Date{$hol} =~ /(\d\d)(\d\d)/o;
		    $Y = $Val;
		    $M = $1;
		    $D = $2;
		    $Val = sprintf("%4d%02d%02d", $Y, $M, $D);
		    if($TEstring =~ /(eve)/io) {
			$Val = &OffsetFromRef($Val, -1);
			if($Val =~ /\d\d\d\d(\d\d)(\d\d)/o) {
			    $M = $1;
			    $D = $2;
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			} else { $Val = ""; }
		    }
		    if($Val) { $Attributes .= " ALT_VAL=\"$Val\""; }
		    $Val = "";
		} elsif($b4 =~ /$TENthDOWHol/io) {
		    $temp1 = $NthDOWHol2Date{lc($1)};
		    if($temp1 =~ /(\d\d?)-(\d)-(\d)/o) {
			$M = $1;
			$temp2 = $2;
			$temp3 = $3;
			$D = NthDOW2Date($M, $temp2, $temp3, $Val);
			$Val = sprintf("%4d%02d%02d", $Val, $M, $D);
			$Attributes .= " ALT_VAL=\"$Val\"";
			$Val = "";
		    }
		} elsif($b4 =~ /\b$TELunarHol\b/io) {
		    $temp1 = $1;
		    $Val = EasterDate($Val);
		    if($b4 =~ /\bgood\s+friday\b/io) {
			$Val = &OffsetFromRef($Val, -3); }
		    elsif($b4 =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
			$Val = &OffsetFromRef($Val, -47); }
		    elsif($b4 =~ /\bash\s+wednesday\b/io) {
			$Val = &OffsetFromRef($Val, -46); }
		    elsif($b4 =~ /\bpalm\s+sunday\b/io) {
			$Val = &OffsetFromRef($Val, -7); }
		    $Attributes .= " ALT_VAL=\"$Val\"";
		    $Val = "";
		} elsif($b4 =~ /(spring|summer|autumn|fall|winter)/io) {
		    $Val   .= $TE_Season{lc($1)};
		}

		if(($Val =~ /((\d\d\d\d)(\d\d))(\d\d)/o) &&  
		   ($4 > MonthLength($3, $2))) {
		    $Val = $1;
		    $Attributes .= " ERROR=\"BadDay\"";
		}
		if($Val) { $Attributes .= " $valTagName=\"$Val\""; }
		
		$FoundVal = 1;
	    } # if year present

	    # ago
	    elsif($TEstring =~ /(while|year|month|moon|fortnight|week|day|decade|centur(y|ie)|time|long)s?\s+(ago|hence|before\s+last|after\s+next|from\s+(now|today|tomorrow|yesterday|$TEday))/oi) {

		$temp1 = $`;
		$temp2 = lc($1);
		$temp3 = lc($3);
		$temp4 = lc($4);
		$temp5 = lc($');

		$Extra = "";
		if($temp3 eq "ago") {
		    $Dir = -1;
		    if($temp5 =~ /(today|tomorrow|yesterday|$TEday)/o) {
			$temp4 = $temp5; }
		} else { $Dir = 1; }

		if($temp2 eq "year") { $Unit = "Y"; }
		elsif($temp2 eq "decade") { $Unit = "E"; }
		elsif($temp2 eq "month") { $Unit = "M"; }
		elsif($temp2 eq "week") { $Unit = "W"; }
		elsif($temp2 eq "fortnight") { $Unit = "W"; }
		elsif($temp2 eq "day") { $Unit = "D"; }
		else { $temp1 = "X";  $Unit = "X"; }

		unless($RefTime) { $Unit = "SKIP"; }

		if(($temp4 =~ /(today|tomorrow|yesterday|$TEday)/o) ||
		   ($preceding_word =~ /(exact|precise)/io)  ||
		   ($next_word =~ /(exact|precise)/io)) {
		    $Exact = 1; }
		else { $Exact = 0; }

		if($temp4 =~ /yesterday/io) {
		    $temp6 = &OffsetFromRef($RefTime, -1, "D");
		} elsif ($temp4 =~ /tomorrow/io) {
		    $temp6 = &OffsetFromRef($RefTime, 1, "D");
		} elsif ($temp4 =~ /$TEday/io) {
		    $temp7 = lc($&);
		    $temp7 =  $Day2Num{$temp7} - Date2DOW($RefTime);
		    if(($temp7 > 0) && ($Dir == -1)) { $temp7 -= 7; }
		    if(($temp7 < 0) && ($Dir ==  1)) { $temp7 += 7; }
		    $temp6 = &OffsetFromRef($RefTime, $temp7, "D");
		} else { $temp6 = $RefTime; }
		$temp6 =~ /(\d\d\d\d)(\d\d)(\d\d)/o;
		$Year  = $1;
		$Month = $2;
		$Day   = $3;

		if($temp1 =~ /about/io) { $temp1 = $'; $Exact = 0; }
		if($TEstring =~ /before\s+last/io) {
		    $Dir = -1;
		    $temp1 = 2;
		    $Exact = 0;
		} elsif($TEstring =~ /after\s+next/io) {
		    $Dir = 1;
		    $temp1 = 2;
		    $Exact = 0;
		} elsif($temp1 = Word2Num($temp1)) { }
		else { $temp1 = 0; $Unit = "X"; }
		
		if($Unit eq "Y") {
		    # Year
		    $Year += $Dir * $temp1;
		    if($Exact) {
			if($Day > MonthLength($Month, $Year)) {
			    $Extra = " ERROR=\"BadDay\"";
			} else {
			    $Val = sprintf("%04d%02d%02d", $Year, $Month, $Day);
			}
		    } else {
			if($Year > 0) { $Val = sprintf("%04d", $Year); }
			else { $Year *= -1; $Val = sprintf("%04dBC", $Year); }
		    }
		} elsif($Unit eq "E") {
		    $Year += $Dir * 10 * $temp1;
		    if($Exact) { $Val = sprintf("%04d", $Year); }
		    else { $Val = int($Year/10);
			   $Val = sprintf("%03d", $Val); }
		} elsif($Unit eq "M") {
		    # Month
		    $Month += $Dir * $temp1;
		    if($Month > 12) {
			$Year += int($Month/12);
			$Month = $Month % 12;
		    } elsif ($Month < 0) {
			$Year += int($Month/12) - 1;
			$Month = $Month % 12; }
		    if($Month == 0) { $Month = 12; $Year --; }
		    if($Exact) {
			if($TE_ML[$Month] < $Day) {
			    $Val = sprintf("%04d%02d", $Year, $Month);
			    $Extra= " ERROR=\"BadDay\"";
			} else {
			    $Val = sprintf("%04d%02d%02d", $Year, $Month, $Day);
			}
		    } else { $Val = sprintf("%04d%02d", $Year, $Month); }
		} elsif($Unit eq "W") {
		    if($temp2 eq "fortnight") { $temp1 *= 2;; }
		    $Val = &OffsetFromRef($temp6, $Dir * $temp1 * 7, "D");
		    unless($Exact) { $Val  = &Date2Week($Val); }
		} elsif($Unit eq "D") {
		    $Val = &OffsetFromRef($temp6, $Dir * $temp1, "D");
		} else {
		    if($Dir == 1) { $Val = "FUTURE_REF"; }
		    else { $Val = "PAST_REF"; }
		    $Extra = "";
		}

		if($Val) { $Attributes .= " $valTagName=\"$Val\"$Extra"; }
		$FoundVal = 1;
	    }  # ago

	    # Century
	    elsif(($TEstring =~ /century/oi) && ($b4 = $`) &&
		  ($b4 =~ /($TENumOrds|$TEOrdinalWords)/io)) {
		$temp1 = $&;
		if($temp1 =~ /\d+/o) { $temp2 = $& - 1; }
		elsif(defined($TE_Ord2Num{lc($temp1)})) {
		    $temp2 = $TE_Ord2Num{lc($temp1)} - 1; }
		$Attributes .= " $valTagName=\"$temp2\"";
		$FoundVal = 1;
	    }
	    
	    # Some REFs
	    elsif($TEstring =~ /\A(now|current)\Z/oi) {
		$Attributes .= " $valTagName=\"PRESENT_REF\"";
		$FoundVal = 1;
	    }
	    elsif($TEstring =~ /(future)/oi) {
		$Attributes .= " $valTagName=\"FUTURE_REF\"";
		$FoundVal = 1;
	    }
	    elsif($TEstring =~ /\A(once|medieval|the\s+past)\Z/oi) {
		$Attributes .= " $valTagName=\"PAST_REF\"";
		$FoundVal = 1;
	    }

	    # generic
	    elsif($TEstring =~ /(every|each|alternate)/io) {
		$Attributes .= " SET=\"YES\"";
		if($TEstring =~ /alternate/io) { $temp2 = 2; }
		elsif($TEstring =~ /every\s+other/io) { $temp2 = 2; }
		elsif($TEstring =~ /$TENumOrds/io) {
		    $& =~ /\d+/o;  $temp2 = $&; }
		elsif($TEstring =~ /$TEOrdinalWords/io) {
		    $temp2 = $TE_Ord2Num{lc($&)}; }
		else { $temp2 = 1; }
		
		if($TEstring =~ /(St\.|Saint)\s.*\bDay\b/oi) {
		    $Attributes .= " PERIODICITY=\"F${temp2}Y\" GRANULARITY=\"G1D\"";
		} elsif($TEstring =~ /\b$TEFixedHol\b/oi) {
		    $Attributes .= " PERIODICITY=\"F${temp2}Y\" GRANULARITY=\"G1D\"";
		} elsif($TEstring =~ /\b($TENthDOWHol|$TEDayHol|$TELunarHol)/oi) {
		    $Attributes .= " GRANULARITY=\"G1D\"";
		} elsif($TEstring =~ /\b(minute|hour|day|week|month|year)s?\b/oi) {
		    $temp1 = lc($1);
		    if($temp1 eq "minute") {
			$Attributes .= " PERIODICITY=\"FT${temp2}M\""; }
		    elsif($temp1 eq "hour") {
			$Attributes .= " PERIODICITY=\"FT${temp2}H\""; }
		    elsif($temp1 eq "day") {
			$Attributes .= " PERIODICITY=\"F${temp2}D\""; }
		    elsif($temp1 eq "week") {
			$Attributes .= " PERIODICITY=\"F${temp2}W\""; }
		    elsif($temp1 eq "month") {
			$Attributes .= " PERIODICITY=\"F${temp2}M\""; }
		    elsif($temp1 eq "year") {
			$Attributes .= " PERIODICITY=\"F${temp2}Y\""; }
		} elsif($TEstring =~ /\b(spring|summer|winter|fall|autumn)s?\b/oi) {
		    $temp1 = $TE_Season{lc($1)};
		    $Attributes .= " $valTagName=\"XXXX$temp1\" PERIODICITY=\"F${temp2}Y\"";
		} elsif(($TEstring =~ /\b$TEday(s)?\b/oi) &&
			($TEstring !~ /\b(shrove|ash|good|palm|easter)\b/oi)) {
		    $Val = $Day2Num{lc($1)};
		    if($TEstring =~ /morning/io) { $Val .= "TMO"; }
		    elsif($TEstring =~ /afternoon/io) { $Val .= "TAF"; }
		    elsif($TEstring =~ /evening/io) { $Val .= "TEV"; }
		    elsif($TEstring =~ /night/io) { $Val .= "TNI"; }
		    $Attributes .= " $valTagName=\"XXXXWXX-$Val\"";
		    if($Val =~ /T/o) { $Attributes .= " PERIODICITY=\"F${temp2}W\""; }
		    else { $Attributes .= " PERIODICITY=\"F${temp2}W\" GRANULARITY=\"G1D\""; }
		}
		$FoundVal = 1;
	    }  # each|every|alternate
	    elsif(($TEstring =~ /(morning|afternoon|evening|night)s/io) &&
		  ($TEstring =~ /\b$TEday\b/oi)) {
		$Val = $Day2Num{lc($1)};
		if($TEstring =~ /morning/io) { $Val .= "TMO"; }
		elsif($TEstring =~ /afternoon/io) { $Val .= "TAF"; }
		elsif($TEstring =~ /evening/io) { $Val .= "TEV"; }
		elsif($TEstring =~ /night/io) { $Val .= "TNI"; }
		$Attributes .= " $valTagName=\"XXXXWXX-$Val\"";
		$Attributes .= " PERIODICITY=\"F1W\"";
		$FoundVal = 1;
	    }
	    elsif($TEstring =~ /($TEday)s/io) {
		$Val = $Day2Num{lc($1)};
		$Attributes .= " $valTagName=\"XXXXWXX-$Val\"";
		$Attributes .= " PERIODICITY=\"F1W\" GRANULARITY=\"G1D\"";
	    }

	    # periods
	    elsif($TEstring =~ /((annual|year|month|week|dai|hour|night)ly|annual)/io) {
		$temp1 = lc($2);
		if($TEstring =~/(year|annual)/io) { $temp1 = "Y"; }
		elsif($temp1 eq "month") { $temp1 = "M"; }
		elsif($temp1 eq "week") { $temp1 = "W"; }
		elsif($temp1 =~ /(dai|night)/io) { $temp1 = "D"; }
		elsif($temp1 eq "hour") { $temp1 = "H"; }
		if($TEstring =~ /bi/io) { $temp2 = 2; }
		else { $temp2 = 1; }
		$Attributes .= " PERIODICITY=\"F$temp2$temp1\"";
		$FoundVal = 1;
	    }
	    
	    
	    # Now rules requiring RefTime
	    elsif($RefTime && ($RefTime =~ /\d\d\d\d/o)) {
		# yesterday, today, tomorrow, tonight
		# this (morning|afternoon|evening)
		# Friday the 13th

		if($TEstring =~ /yesterday/oi) {
		    $temp1 = $`;
		    $Val = &OffsetFromRef($RefTime, -1);
		    if($temp1 =~ /day\s+before/io) {
			$Val = &OffsetFromRef($Val, -1); }
		    $Attributes .= " $valTagName=\"$Val\"";
		    $FoundVal = 1;

		    # Friday the 13th type dates
		} elsif(($TEstring =~ /($TEOrdinalWords|$TENumOrds)/oi) &&
			($TEstring =~ /\b($TEday)\b/oi)) {
		    $DOW = lc($1);
		    $DOM = 0;
		    if($TEstring =~ /\d+/o) { $DOM = $&; }
		    elsif($TEstring =~ /$TEOrdinalWords/oi) {
			$DOM = $TE_Ord2Num{lc($&)}; }

		    if(($DOW eq "friday") && ($DOM == 13)) { $FoundVal = 1; }
		    else {
			# check if it means this month - otherwise no val
			# May be bad in february - should also check dierction
			$RefTime =~ /\d{6}/o;
			$YM = $&;
			$Val = sprintf("%s%02d", $YM, $DOM);
			if($Day2Num{$DOW} == Date2DOW($Val)) {
			    $Attributes .= " $valTagName=\"$Val\"";
			}
		    }

		    $FoundVal = 1;
		} elsif(($TEstring =~ /(today|tonight|tonite)/oi) ||
			($TEstring =~
			 /this\s+(morning|afternoon|evening)/oi)) {

		    # Only include value for specific use of day
		    # No val for generic use
		    if($RefTime =~ /\d{8}/o) { $Val = $&; }
		    else { print STDERR " ** Bad RefTime: $RefTime\n"; }

		    if($TEstring =~ /today/io) {
			
			$W_b4 = "";
			if($output =~ /.*>(\w+)</o) {
			    $W_b4  = lc($1);
			    $trail = $';
			    if($trail =~ /\"/o) { $W_b4 = ""; }
			}
			$W_aft = "";
			if($string =~ />\w+</o) {
			    $W_aft = lc($1);
			    $lead  = $`;
			    if($lead =~ /\"/o) { $W_aft = ""; }
			}

			if(($W_b4  =~ /(said|later|earlier)/io) ||
			   ($W_aft =~ /(for|at|on)/io) ||
			   ($output =~ /($TEday|up)/io)  ||
			   ($string =~ /($TEday|up)/io)) {
			    # Specific
			    $Attributes .= " $valTagName=\"$Val\"";
			} elsif(($W_b4   =~ /(am|is|are|were|was|that|of|in)/io) ||
				($output =~ /(many|here|most)/io)  ||
				($string =~ /(many|here|most)/io)) {
			    # Generic
			    $Attributes .= " $valTagName=\"PRESENT_REF\"";
			    $FoundVal = 1;
			} else {
			    # Default = Specific
			    $Attributes .= " $valTagName=\"$Val\"";
			}
		    } elsif($TEstring =~ /this\s+morning/io) {
			$Attributes .= " $valTagName=\"${Val}TMO\"";
		    } elsif($TEstring =~ /this\s+afternoon/io) {
			$Attributes .= " $valTagName=\"${Val}TAF\"";
		    } elsif($TEstring =~ /this\s+evening/io) {
			$Attributes .= " $valTagName=\"${Val}TEV\"";
		    } elsif($TEstring =~ /(tonight|tonite)/io) {
			$Attributes .= " $valTagName=\"${Val}TNI\"";
		    } else {
			$Attributes .= " $valTagName=\"$Val\"";
		    }
		    $FoundVal = 1;
		} elsif($TEstring =~ /tomorrow/oi) {
		    $temp1 = $`;
		    $Val = &OffsetFromRef($RefTime, 1);
		    if($temp1 =~ /day\s+after/io) {
			$Val = &OffsetFromRef($Val, 1); }
		    $Attributes .= " $valTagName=\"$Val\"";
		    $FoundVal = 1;
		}

		# next/last/this(past) present 
		elsif($TEstring =~ /(next|last|past|coming|this)/io) {
		    $textDir = lc($1);
		    $Reason = $textDir;
		    if($TEstring =~ /coming/io) { $textDir = "next"; }
		    if($TEstring =~ /past/io) { $textDir = "last"; }

		    # has month
		    if(($TEstring =~ /\b($TEmonth|$TEmonthabbr)\b/io) &&
		       ($TEstring !~ /\b(fool|may\s+day)/io)) {

			$1 =~ /(\w{3})/;
			$M = $Month2Num{lc($1)};
			
			# Is numeric day present?
			if($TEstring =~ /(\d\d?)/o) {
			    $D = sprintf("%02d", $1); }
			elsif($TEstring =~ /$TEOrdinalWords/oi) {
			    $D = sprintf("%02d", $TE_Ord2Num{lc($&)}) ;
			} else { $D = ""; }
			
			$RefTime =~ /(\d{4})(\d{2})/o;
			$Year = $1;
			$RefM = $2;
			if(($textDir =~ /next/io) &&
			   (($RefM >= $M) || ($TEstring =~ /year/o))) {
			    $Year++; }
			if(($textDir =~ /last/io) &&
			   (($RefM <= $M) || ($TEstring =~ /year/o))){
			    $Year--; }
			$YM = sprintf("%4d%02d", $Year, $M);
			$Val = $YM . $D;
			if($TEstring =~
			      /($TEOrdinalWords|$TENumOrds)\s+week(end)?\s+(of|in)/io) {
			    $temp1 = lc($1);
			    if($temp1 =~ /\d+/o) { $temp2 = $&; }
			    else { $temp2 = $TE_Ord2Num{$temp1}; }
			    if($temp2 > 4) {
				$Attributes .= " ERROR=\"BadWeek\""; }
			    if($TEstring =~ /weekend/io) { 
				$Val   .= sprintf("%02d", ($temp2*7)-5); }
			    else { $Val   .= sprintf("%02d", ($temp2*7)-3); }
			    $Val = &Date2Week($Val);
			    if($TEstring =~ /weekend/io) { $Val .= "WE"; }
			} 
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    } # has month

		    # No month - dayname
		    elsif(($TEstring =~ /($TEday)/io) &&
			  ($TEstring !~ /\b(shrove|ash|good|palm|easter)\b/io)) {
			$temp1 = lc($');
			$DOW = $Day2Num{lc($1)};
			unless(($preceding_word eq "the") ||
			       ($preceding_word eq "a")) {
			    $RefDOW = &Date2DOW($RefTime);
			    $offset = $DOW - $RefDOW;
			    if($textDir =~ /next/io) {
				if($offset <= 0) { $offset += 7; }
			    } elsif($textDir =~ /last/io) {
				if($offset >= 0) { $offset -= 7; }
			    }

			    $Val = &OffsetFromRef($RefTime, $offset);
			    if($temp1 =~ /morning/io) { $Val .= "TMO"; }
			    elsif($temp1 =~ /afternoon/io) { $Val .= "TAF"; }
			    elsif($temp1 =~ /evening/io) { $Val .= "TEV"; }
			    elsif($temp1 =~ /night/io) { $Val .= "TNI"; }

			    $Attributes .= " $valTagName=\"$Val\"";
			    
			}
			$FoundVal = 1;
		    }

		    # No monthname, no dayname - season
		    # this/next/last/past
		    elsif($TEstring =~
			  /(spring|summer|winter|fall|autumn)/io) {
			$Season = $TE_Season{$1};

			$RefTime =~ /(\d{4})(\d{2})/o;
			$Year = $1;
			$RefM = $2;

			# correct year
			if(($textDir =~ /last/io) &&
			   (($RefM-2) < $TE_Season2Month{$Season})) {
			    $Year--;
			} elsif(($textDir =~ /next/io) &&
				(($RefM+2) > $TE_Season2Month{$Season})) {
			    $Year++;
			}

			$Val = "$Year$Season";
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    }

		    # No monthname, no dayname - word "month"
		    elsif($TEstring =~ /month/io) {
			$RefTime =~ /(\d{4})(\d{2})/o;
			$Year = $1;
			$RefM = $2;
			$Val  = $1.$2;
			if($textDir =~ /next/io) {
			    $Val = &OffsetFromRef($RefTime, 1, "M");
			} elsif($textDir =~ /last/io) {
			    $Val = &OffsetFromRef($RefTime, -1, "M");
			} 
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    }
		    
		    # No month, no dayname - year
		    elsif($TEstring =~ /year/io) {
			$RefTime =~ /(\d{4})/o;
			$Val  = $1;
			if($textDir =~ /next/io) { $Val++; }
			elsif($textDir =~ /last/io) { $Val--; }
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    }
		    # Century
		    elsif($TEstring =~ /century/io) {
			$RefTime =~ /(\d{2})/o;
			$Val  = $1;
			if($textDir =~ /next/io) { $Val++; }
			elsif($textDir =~ /last/io) { $Val--; }
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    }
		    # Decade
		    elsif($TEstring =~ /decade/io) {
			$RefTime =~ /(\d{3})/o;
			$Val  = $1;
			if($textDir =~ /next/io) { $Val++; }
			elsif($textDir =~ /last/io) { $Val--; }
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    }
		    # last night
		    elsif($TEstring =~ /last\s+night/io) {
			$Val = &OffsetFromRef($RefTime, -1, "D");
			$Attributes .= " $valTagName=\"${Val}TNI\"";
			$FoundVal = 1;
		    }
		    # No month, no dayname - weekend 
		    elsif($TEstring =~ /(next|last|coming)\s+weekend/io) {
			$RefDOW = &Date2DOW($RefTime);
			# val1 = saturday
			if($TEstring =~ /(next|coming)/io) {
			    $Val1 = &OffsetFromRef($RefTime, -1*$RefDOW+6);
			} elsif($TEstring =~ /last/io) {
			    $Val1 = &OffsetFromRef($RefTime, -1*$RefDOW-1);
			}
			$Val  = &Date2Week($Val1);
			$Attributes .= " $valTagName=\"${Val}WE\"";
			$FoundVal = 1;
		    }

		    # No month, no dayname - week
		    elsif($TEstring =~ /week\b/io) {
			# Generic ISO Monday-to-Sunday week

			if($TEstring =~ /(next|coming)/io) {
			    $Val = &OffsetFromRef($RefTime, 7);
			} elsif($TEstring =~ /last/io) {
			    $Val = &OffsetFromRef($RefTime, -7);
			} else { $Val = $RefTime; }

			$Val = Date2Week($Val);
			$Attributes .= " $valTagName=\"$Val\"";
			$FoundVal = 1;
		    }
		    # Fixed Holiday
		    elsif($TEstring =~ /($TEFixedHol)/io) {
			$hol = lc($1);
			$hol =~ s/\s+//go;
			$FixedHol2Date{$hol} =~ /(\d\d)(\d\d)/o;
			$M = $1;
			$D = $2;
			$RefTime =~ /\d\d\d\d/o;
			$Y = $&;
			$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			if($TEstring =~ /(eve)/io) {
			    $Val = &OffsetFromRef($Val, -1);
			    $Val =~ /(\d\d\d\d)(\d\d)(\d\d)/o;
			    $Y = $1;
			    $M = $2;
			    $D = $3;
			}

			$RefTime =~ /\d{8}/o;
			$test = $&;

			if(($test < $Val) && ($textDir =~ /last/io)) {
			    $Y--;
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D); }
			if(($test > $Val) &&
			   ($textDir =~ /next/io)) {
			    $Y++;
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D); }

			$Attributes .= " ALT_VAL=\"$Val\"";
			$FoundVal = 1;
		    }
		    elsif($TEstring =~ /($TENthDOWHol)/io) {
			$temp1 = $NthDOWHol2Date{lc($1)};
			$temp1 =~ /(\d\d?)-(\d)-(\d)/o;
			$M = $1;
			$temp2 = $2;
			$temp3 = $3;
			$RefTime =~ /\d{4}/o;
			$Y = $&;
			$D = NthDOW2Date($M, $temp2, $temp3, $Y);
			$Val = sprintf("%4d%02d%02d", $Y, $M, $D);

			$RefTime =~ /\d{8}/o;
			$test = $&;

			if(($test < $Val) && ($textDir =~ /last/io)) {
			    $Y--;
			    $D = NthDOW2Date($M, $temp2, $temp3, $Y);
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D); }
			if(($test > $Val) &&
			   ($textDir =~ /next/io)) {
			    $Y++;
			    $D = NthDOW2Date($M, $temp2, $temp3, $Y);
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D); }

			$Attributes .= " ALT_VAL=\"$Val\"";
			$FoundVal = 1;
		    }
		    elsif($TEstring =~ /$TELunarHol/io) {
			$RefTime =~ /\d{4}/o;
			$Y = $&;
			$Val = EasterDate($Y);
			if($TEstring =~ /\bgood\s+friday\b/io) {
			    $Val = &OffsetFromRef($Val, -3); }
			elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
			    $Val = &OffsetFromRef($Val, -47); }
			elsif($TEstring =~ /\bash\s+wednesday\b/io) {
			    $Val = &OffsetFromRef($Val, -46); }
			elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
			    $Val = &OffsetFromRef($Val, -7); }

			$RefTime =~ /\d{8}/o;
			$test = $&;

			$temp1 = 0;
			if(($test < $Val) &&
			   ($textDir =~ /last/io)) {
			    $Y--;
			    $Val = EasterDate($Y);
			    if($TEstring =~ /\bgood\s+friday\b/io) {
				$Val = &OffsetFromRef($Val, -3); }
			    elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
				$Val = &OffsetFromRef($Val, -47); }
			    elsif($TEstring =~ /\bash\s+wednesday\b/io) {
				$Val = &OffsetFromRef($Val, -46); }
			    elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
				$Val = &OffsetFromRef($Val, -7); }
			}
			if(($test > $Val) &&
			   ($textDir =~ /next/io)) {
			    $Y++;
			    $Val = EasterDate($Y);
			    if($TEstring =~ /\bgood\s+friday\b/io) {
				$Val = &OffsetFromRef($Val, -3); }
			    elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
				$Val = &OffsetFromRef($Val, -47); }
			    elsif($TEstring =~ /\bash\s+wednesday\b/io) {
				$Val = &OffsetFromRef($Val, -46); }
			    elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
				$Val = &OffsetFromRef($Val, -7); }
			}
			
			$Attributes .= " ALT_VAL=\"$Val\"";
			$FoundVal = 1;
		    }
		    
		}  # last/this/next

		# Handle relative expressions requiring relative direction
		if(!$FoundVal && ($TE_HeurLevel > 1)) {
		    $Reason = "";
		    $verb2  = "";
		    $POS2   = "";
		    
		    # find the relevant verb and POS
		    if($lead   =~
		       /.*<LexTagName[^>]*pos=\"?(VBP|VBZ|VBD|MD)[^>]*>(\'?\w+)/sio) {
			$POS = $1; $VB  = lc($2);
			$' =~ /pos=\"(VB[A-Z]?)\"[^>]*>(\w+)/;
			$POS2 = $1; $verb2 = $2;
		    } elsif($string =~
			    /<$LexTagName[^>]*pos=\"?(VBP|VBZ|VBD|MD)[^>]*>(\'?\w+)/io) {
			$POS = $1; $VB  = lc($2);
			$' =~ /pos=\"?(VB[A-Z]?)\"[^>]*>(\w+)/;
			$POS2 = $1; $verb2 = $2;
		    } elsif($output =~
			    /.*<$LexTagName[^>]*pos=\"?(VBP|VBZ|VBD|MD)[^>]*>(\'?\w+)/sio) {
			$POS = $1; $VB = lc($2);
			$' =~ /pos=\"?(VB[A-Z]?)[^>]*>(\w+)/;
			$POS2 = $1; $verb2 = $2;
		    } else {
			$POS = "X"; $VB  = "NoVerb";
		    }

		    if(($POS =~/(VBP|VBZ|MD)/io) &&
		       (($output =~ /$OT+going$CT+\s+$OT+to$CT+/sio) ||
			($string =~ /$OT+going$CT+\s+$OT+to$CT+/sio))) {
			$POS = "MD"; $VB  = "going_to";
		    }
		    
		    if($TE_DEBUG > 1) {
			$Attributes .= " verb=\"$VB:$POS\"";
			if($POS eq "MD") {
			    $Attributes .= " verb2=\"$verb2:$POS2\""; }
		    }

		    $RelDir = 0;
		    if($POS eq "VBD") {
			$RelDir = -1;
			$Reason = "$POS";
		    }
		    elsif($POS eq "MD") {
			if($VB =~ /(will|\'ll|going_to)/io) {
			    $RelDir = 1;
			    $Reason = "$POS:$VB";
			}
			elsif($verb2 eq "have") {
			    $RelDir = -1;
			    $Reason = "$POS:$VB";
			}
			elsif(($VB =~ /((w|c|sh)ould|\'d)/o)
			      && ($POS2 eq "VB")) {
			    $RelDir = 1;
			    $Reason = "$POS:$VB";
			}
			
		    }

		    # Heuristic Level > 2
		    if(($TE_HeurLevel > 2) && ($RelDir == 0)) {
			# since / until
			if($preceding_word eq "since") {
			    $RelDir = -1;
			    $Reason = "since";
			} elsif($preceding_word eq "until") {
			    $RelDir = 1;
			    $Reason = "until";
			}
			    
		    } # if($TE_HeurLevel > 2)

		    
		    # We found a Relative direction
		    if($RelDir) {

			# Month name present
			if($TEstring =~ /\b($TEFixedHol|$TEmonth|$TEmonthabbr)\b/io) {
			    $1 =~ /(\w{3})/;
			    $M = $Month2Num{lc($1)};
			    $RefTime =~ /((\d{4})\d{4})/o;
			    $RefTime = $1;
			    $Y = $2;

			    # Is numeric day or week of present? 
			    if($TEstring =~ /($TEOrdinalWords|$TENumOrds)\s+week(end)?\s+(of|in)/io) {
				$temp1 = lc($1);
				if($temp1 =~ /\d+/o) { $temp2 = $&; }
				else { $temp2 = $TE_Ord2Num{$temp1}; }
				if($temp2 > 4) { $Attributes .= " ERROR=\"BadWeek\""; }
				if($TEstring =~ /weekend/io) { $D = ($temp2*7)-5; }
				else { $D = ($temp2*7)-3; }
				$format = "%4d%02d%02d";
				$Val = sprintf($format, $Y, $M, $D);
				$test = $RefTime;
			    }
			    elsif($TEstring =~ /(\d\d?)/o) {
				$D = $1;
				$format = "%4d%02d%02d";
				$Val = sprintf($format, $Y, $M, $D);
				$test = $RefTime;
			    }
			    # Ordinal day given
			    elsif($TEstring =~ /($TEOrdinalWords)/io) {
				$D = $TE_Ord2Num{lc($1)};
				$format = "%4d%02d%02d";
				$Val = sprintf($format, $Y, $M, $D);
				$test = $RefTime;
			    }
			    elsif($TEstring =~ /\bides\b/io) {
				if(($M == 3) || ($M == 5) ||
				   ($M == 7) || ($M == 10)) { $D = 15; }
				else { $D = 13; } 
				$format = "%4d%02d%02d";
				$Val = sprintf($format, $Y, $M, $D);
				$test = $RefTime;
			    }
			    elsif($TEstring =~ /\bnones\b/io) {
				if(($M == 3) || ($M == 5) ||
				   ($M == 7) || ($M == 10)) { $D = 7; }
				else { $D = 5; } 
				$format = "%4d%02d%02d";
				$Val = sprintf($format, $Y, $M, $D);
				$test = $RefTime;
			    }
			    # Fixed Holiday
			    elsif($TEstring =~ /($TEFixedHol)/io) {
				$hol = lc($1);
				$hol =~ s/\s+//go;
				$FixedHol2Date{$hol} =~ /(\d\d)(\d\d)/o;
				$M = $1;
				$D = $2;
				$format = "%4d%02d%02d";
				$Val = sprintf($format, $Y, $M, $D);
				if($TEstring =~ /(eve)/io) {
				    $Val = &OffsetFromRef($Val, -1);
				    $Val =~ /(\d\d\d\d)(\d\d)(\d\d)/o;
				    $Y = $1;
				    $M = $2;
				    $D = $3;
				}
				$test = $RefTime;
			    }
			    # just a month
			    else {
				$format = "%4d%02d";
				$Val = sprintf($format, $Y, $M);
				$RefTime =~ /(\d{6})/o;
				$test = $1;
			    }
			    
			    if(($RelDir > 0) && ($test > $Val)) {
				$Y++;
			    } elsif(($RelDir < 0) && ($test < $Val)) {
				$Y--;
			    }
			    $Val = sprintf($format, $Y, $M, $D);

			    if(($TEstring =~
				/the\s+(\w+\s+)?week(end)?\s+(of|in)/io) && $D) {
				$Val = &Date2Week($Val);
				if($TEstring =~ /weekend/io) { $Val .= "WE"; }
			    }

			    if($TEstring =~ /$TEFixedHol/io) {
				$Attributes .= " ALT_VAL=\"$Val\"";
			    } else { $Attributes .= " $valTagName=\"$Val\""; }
			    if(($TE_DEBUG > 1) && $Reason) {
				$Attributes .= " reason=\"$Reason\""; }
			    $FoundVal = 1;
			    
			} # if month name present

			# Nth DOW Hol
			elsif($TEstring =~ /($TENthDOWHol)/io) {
			    $temp1 = $NthDOWHol2Date{lc($1)};
			    $temp1 =~ /(\d\d?)-(\d)-(\d)/o;
			    $M = $1;
			    $temp2 = $2;
			    $temp3 = $3;
			    $RefTime =~ /\d{4}/o;
			    $Y = $&;
			    $D = NthDOW2Date($M, $temp2, $temp3, $Y);
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    $RefTime =~ /\d{8}/o;
			    $test = $&;
			    if(($RelDir > 0) && ($test > $Val)) {
				$Y++;
				$D = NthDOW2Date($M, $temp2, $temp3, $Y);
			    } elsif(($RelDir < 0) && ($test < $Val)) {
				$Y--;
				$D = NthDOW2Date($M, $temp2, $temp3, $Y);
			    }
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    $Attributes .= " ALT_VAL=\"$Val\"";
			    $FoundVal = 1;
			}
			
			elsif($TEstring =~ /\b$TELunarHol\b/io) {
			    $RefTime =~ /\d{4}/o;
			    $Y = $&;
			    $Val = EasterDate($Y);
			    if($TEstring =~ /\bgood\s+friday\b/io) {
				$Val = &OffsetFromRef($Val, -3); }
			    elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
				$Val = &OffsetFromRef($Val, -47); }
			    elsif($TEstring =~ /\bash\s+wednesday\b/io) {
				$Val = &OffsetFromRef($Val, -46); }
			    elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
				$Val = &OffsetFromRef($Val, -7); }

			    $RefTime =~ /\d{8}/o;
			    $test = $&;
			    if(($RelDir > 0) && ($test > $Val)) {
				$Y++;
				$Val = EasterDate($Y);
				if($TEstring =~ /\bgood\s+friday\b/io) {
				    $Val = &OffsetFromRef($Val, -3); }
				elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
				    $Val = &OffsetFromRef($Val, -47); }
				elsif($TEstring =~ /\bash\s+wednesday\b/io) {
				    $Val = &OffsetFromRef($Val, -46); }
				elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
				    $Val = &OffsetFromRef($Val, -7); }
			    } elsif(($RelDir < 0) && ($test < $Val)) {
				$Y--;
				$Val = EasterDate($Y);
				if($TEstring =~ /\bgood\s+friday\b/io) {
				    $Val = &OffsetFromRef($Val, -3); }
				elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
				    $Val = &OffsetFromRef($Val, -47); }
				elsif($TEstring =~ /\bash\s+wednesday\b/io) {
				    $Val = &OffsetFromRef($Val, -46); }
				elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
				    $Val = &OffsetFromRef($Val, -7); }
			    }
			    $Attributes .= " ALT_VAL=\"$Val\"";
			    $FoundVal = 1;
			}
			# Day of week, no month
			elsif($TEstring =~ /($TEday)/io) {
			    $temp1 = lc($');
			    $DOW = $Day2Num{lc($1)};

			    $RefDOW = &Date2DOW($RefTime);
			    $offset = $DOW - $RefDOW;
			    if(($RelDir > 0) && ($offset < 0)) {
				$offset += 7; }
			    if(($RelDir < 0) && ($offset > 0)) {
				$offset -= 7; }
			    $Val = &OffsetFromRef($RefTime, $offset);
			    if($temp1 =~ /morning/io) { $Val .= "MO"; }
			    elsif($temp1 =~ /afternoon/io) { $Val .= "AF"; }
			    elsif($temp1 =~ /evening/io) { $Val .= "EV"; }
			    elsif($temp1 =~ /night/io) { $Val .= "TNI"; }
			    
			    $Attributes .= " $valTagName=\"$Val\"";

			    $FoundVal = 1;
			}
			# (the|this) weekend
			elsif($TEstring =~ /weekend/io) {
			    $RefDOW = &Date2DOW($RefTime);
			    # val1 = saturday
			    if($RelDir > 0) {
				$Val1 = &OffsetFromRef($RefTime, -1*$RefDOW+6);
			    } elsif($RelDir < 0) {
				$Val1 = &OffsetFromRef($RefTime, -1*$RefDOW-1);
			    }
			    $Val = &Date2Week($Val1);
			    $Attributes .= " $valTagName=\"${Val}WE\"";
			    $FoundVal = 1;
			}

		    } # if($RelDir)

		    # assume dates near the reftime are this year
		    elsif(($TE_HeurLevel > 2) &&
			  ($TEstring =~ /\b($TEFixedHol|$TEmonth|$TEmonthabbr|$TENthDOWHol|$TELunarHol)\b/io)) {
			$RefTime =~ /((\d{4})(\d\d)(\d\d))/o;
			$RefTime = $1;
			$RTmonth = $3;
			$RTday   = $4;
			$Y = $2;
			$D = 0;
			$Val = "";

			if(($TEstring =~ /\b($TEmonth|$TEmonthabbr)\b/io) &&
			   ($TEstring !~ /\b(fool|may\s+day)/io)) {
			    $1 =~ /(\w{3})/;
			    $M = $Month2Num{lc($1)};
			} elsif($TEstring =~ /\b$TELunarHol\b/io) {
			    $Val = EasterDate($Y);
			    if($TEstring =~ /\bgood\s+friday\b/io) {
				$Val = &OffsetFromRef($Val, -3); }
			    elsif($TEstring =~ /\b(shrove\s+tuesday|mardis\s+gras)\b/io) {
				$Val = &OffsetFromRef($Val, -47); }
			    elsif($TEstring =~ /\bash\s+wednesday\b/io) {
				$Val = &OffsetFromRef($Val, -46); }
			    elsif($TEstring =~ /\bpalm\s+sunday\b/io) {
				$Val = &OffsetFromRef($Val, -7); }
			    $Val =~ /\d\d\d\d(\d\d)(\d\d)/o;
			    $M = $1;   $D = $2;
			} elsif($TEstring =~ /$TEFixedHol/io) {
			    $hol = lc($1);
			    $hol =~ s/\s+//go;
			    $FixedHol2Date{$hol} =~ /(\d\d)(\d\d)/o;
			    $M = $1;
			    $D = $2;
			    if($TEstring =~ /\beve\b/io) {
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
				$offset = -1;
				$Val = &OffsetFromRef($Val, $offset);
				$Val =~ /\d\d\d\d(\d\d)(\d\d)/o;
				$M = $1;
				$D = $2;
			    }
			} elsif($TEstring =~ /($TENthDOWHol)/io) {
			    $temp1 = $NthDOWHol2Date{lc($1)};
			    $temp1 =~ /(\d\d?)-(\d)-(\d)/o;
			    $M = $1;
			    $temp2 = $2;
			    $temp3 = $3;
			    $RefTime =~ /\d{4}/o;
			    $Y = $&;
			    $D = NthDOW2Date($M, $temp2, $temp3, $Y);
			    $Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			}

			if((abs($M - $RTmonth) < 2) ||
			   (abs($M - $RTmonth) == 11))  {
			    # guess nearby instance of date

			    # fix year if needed
			    if(($M - $RTmonth) == 11) { $Y--; }
			    elsif(($RTmonth - $M) == 11) { $Y++; }

			    # Is numeric day present?
			    if($Val) {1;}
			    elsif($TEstring =~ /($TEOrdinalWords|$TENumOrds)\s+week(end)?\s+(of|in)/io) {
				$temp1 = lc($1);
				if($temp1 =~ /\d+/o) { $D = $&; }
				else { $D = $TE_Ord2Num{$temp1}; }
				if($D > 4) { $Attributes .= " ERROR=\"BadWeek\""; }
				if($TEstring =~ /weekend/io) { $D = ($D*7)-5; }
				else { $D = ($D*7)-3; }
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    } elsif($TEstring =~ /(\d\d?)/o) {
				$D = $1;
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    }
			    # Ordinal day given
			    elsif($TEstring =~ /($TEOrdinalWords)/io) {
				$D = $TE_Ord2Num{lc($1)};
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    }
			    elsif($TEstring =~ /\bides\b/io) {
				if(($M == 3) || ($M == 5) ||
				   ($M == 7) || ($M == 10)) { $D = 15; }
				else { $D = 13; } 
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    }
			    elsif($TEstring =~ /\bnones\b/io) {
				if(($M == 3) || ($M == 5) ||
				   ($M == 7) || ($M == 10)) { $D = 7; }
				else { $D = 5; } 
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D);
			    }

			    elsif($D) {
				$Val = sprintf("%4d%02d%02d", $Y, $M, $D); }
			    else {
				$Val = sprintf("%4d%02d", $Y, $M); }

			    if(($TEstring =~ /the\s+(\w+\s+)?week(end)?\s+(of|in)/io) && $D) {
				$temp1 = $Val;
				$Val = &Date2Week($Val);
				if($TEstring =~ /weekend/io) { $Val .= "WE"; }
			    }
			    if($TEstring =~ /($TEFixedHol|$TENthDOWHol|$TELunarHol)/io) {
				$Attributes .= " ALT_VAL=\"$Val\""; }
			    else { $Attributes .= " $valTagName=\"$Val\""; }
			    if($TE_DEBUG > 1) {
				$Attributes .= " reason=\"near_reftime\""; }

			    
			} # abs($M - $RTmonth) < 2
		    } # dates near the reftime

		    else {
			# Handle generics
			if($TEstring =~ /($TEday)/io) {
			    $DOW = $Day2Num{lc($1)};
			    unless($DOW) { $DOW += 7; }
			    $Val = sprintf("-W-%d", $DOW)
			    }
		    }
		    
		}  # (!$FoundVal && ($TE_HeurLevel > 1))

		if(($Attributes =~ /$valTagName=\"\d{8}\"/o) &&
		   ($TEstring =~ /(morning|afternoon|evening|night)/oi)) {
		    if($TEstring =~ /morning/io) {
			$Attributes =~ s/($valTagName=\"\d{8})\"/$1TMO\"/io; }
		    elsif($TEstring =~ /afternoon/io) {
			$Attributes =~ s/($valTagName=\"\d{8})\"/$1TAF\"/io; }
		    elsif($TEstring =~ /evening/io) {
			$Attributes =~ s/($valTagName=\"\d{8})\"/$1TEV\"/io; }
		    elsif($TEstring =~ /night/io) {
			$Attributes =~ s/($valTagName=\"\d{8})\"/$1TNI\"/io; }
		}
		
	    } # if($RefTime)
	}  # if type=date

	# type=time
	elsif($Type eq "time") {

	    $TimeZone = "";
	    if($TEstring =~ /[\d\b]([A-Z][SD]T)\b/o) { $TimeZone = $1; }
	    elsif($TEstring =~ /universal/oi) { $TimeZone = "UT"; }
	    elsif($TEstring =~ /zulu/oi) { $TimeZone = "GMT"; }
	    elsif($TEstring =~ /([a-z])[a-z]+\s+([ds])[a-z]+\s+time/oi) {
		$Zone = uc($1);
		$DorS = uc($2);
		$TimeZone = "$Zone${DorS}T";
	    }

	    if($TEstring =~ /(exact|precise|sharp|on\s+the\s+dot)/io) {
		$Exact = 1; }
	    elsif($output =~ /(exactly|precisely)/io) { $Exact = 1; }
	    else { $Exact = 0; }

	    # ISO time
	    if($TEstring =~ /(T\d\d(:?\d\d)?(:?\d\d)?([\+\-]\d{1,4})?)/o) {
		$Val = Date2ISO($1);
		$Attributes .= " $valTagName=\"$Val\"";
	    }
	    # noon/midnight 
	    elsif($TEstring =~ /\b(noon|midnight|zero\s+hour)/oi) {
		$twelve = lc($1);
		if($twelve eq "noon") {
		    $Attributes .= " $valTagName=\"T1200$TimeZone\""; }
		else { $Attributes .= " $valTagName=\"T0000$TimeZone\""; }
	    }
	    # 24 hour Euro time
	    elsif($TEstring =~ /\b(\d\d?)h(\d\d)\b/io) {
		$Hour = $1;   $Min = $2;
		$Val = sprintf("%02d%02d", $Hour, $Min);
		$Attributes .= " $valTagName=\"T$Val\"";
		if(($Hour>24) || ($Min>59)) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
	    }

	    # X hour|minute ago
	    elsif($TEstring =~ /hours?\s+(and\s+a\s+half\s+)?(ago|hence|from\s+now)\Z/oi) {
		$temp1 = $`;
		$temp2 = $1;
		$temp3 = $2;

		if($temp3 eq "ago") { $Dir = -1; }
		else { $Dir = 1; }

		if(!$RefTime) {
		    # Do Nothing
		}
		elsif($temp1 =~ /\Aan\s+\Z/o) {
		    if($temp2) { $Val = &OffsetFromRef($RefTime, 90 * $Dir, "TM"); }
		    else { $Val = &OffsetFromRef($RefTime, $Dir, "TH"); }
		    $Attributes .= " $valTagName=\"$Val\"";
		} elsif($Offset = Word2Num($temp1)) {
		    $Offset *= $Dir;
		    $Val = &OffsetFromRef($RefTime, $Offset, "TH");
		    $Attributes .= " $valTagName=\"$Val\"";
		} elsif(($temp1 =~ /and\s+a\s+half\s*\Z/o) &&
			($Offset = Word2Num($`))) {
		    $Offset = ((60 * $Offset) + 30) * $Dir ;
		    $Val = &OffsetFromRef($RefTime, $Offset, "TM");
		    $Attributes .= " $valTagName=\"$Val\"";
		} elsif($temp1 =~ /\A(a\s+)?half(\s+an)?\s*\Z/o) {
		    $Val = &OffsetFromRef($RefTime, 30 * $Dir, "TM");
		    $Attributes .= " $valTagName=\"$Val\"";
		} else {
		    if($Dir == 1) { $Attributes .= " $valTagName=\"FUTURE_REF\""; }
		    else { $Attributes .= " $valTagName=\"PAST_REF\""; }
		}
	    }
	    elsif($TEstring =~ /minutes?\s+(ago|hence|from\s+now)\Z/oi) {
		$temp1 = $`;
		$temp3 = $1;

		if($temp3 eq "ago") { $Dir = -1; }
		else { $Dir = 1; }

		if(!$RefTime) {
		    if($Dir == 1) { $Attributes .= " $valTagName=\"FUTURE_REF\""; }
		    else { $Attributes .= " $valTagName=\"PAST_REF\""; }
		}
		elsif($Offset = Word2Num($temp1)) {
		    $Offset *= $Dir;
		    $Val = &OffsetFromRef($RefTime, $Offset, "TM");
		    $Attributes .= " $valTagName=\"$Val\"";
		} else {
		    if($Dir == 1) { $Attributes .= " $valTagName=\"FUTURE_REF\""; }
		    else { $Attributes .= " $valTagName=\"PAST_REF\""; }
		}
	    }
	    
	    # AM/PM
	    elsif($TEstring =~ /(\d\d?)(:(\d\d))?\s*([ap])\.?\s*m\.?/oi) {
		$Hour = $1;
		$Min  = $3;
		$AorP = lc($4);

		if($TEstring =~ /half\s+past/io) { $Min = 30; }
		elsif($TEstring =~ /quarter\s+after/io) { $Min = 15; }
		elsif($TEstring =~ /quarter\s+(of|before|to)/io) {
		    $Min = 45;    $Hour--;
		}
		elsif($TEstring =~ /(\d+|\w+(-\w+)?)\s+(minutes?\s+)?(before|after|past|of|to|until)/io) {
		    $Min = Word2Num(lc($1));
		    $temp1 = $4;
		    if($temp1 =~ /(of|before|to|until)/io) {
			$Min = 60 - $Min;  $Hour--; }
		}

		if(($AorP eq "p") && ($Hour < 12)) { $Hour += 12; }
		if(($AorP eq "a") && ($Hour == 12)) { $Hour = 0; }
		if(defined($Min)) { $Val = sprintf("%02d%02d", $Hour, $Min); }
		elsif($Exact) { $Val = sprintf("%02d00", $Hour); }
		else { $Val = sprintf("%02d", $Hour); }
		$Attributes .= " $valTagName=\"T$Val$TimeZone\"";
		if(($Hour>24) || (defined($Min) && ($Min>59))) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		if(($AorP eq "a") && ($Hour>12)) {
		    $Attributes .= " ERROR=\"Inconsistent\""; }
		$FoundVal = 1;
	    }
	    elsif($TEstring =~ /(\w+\s+)?(\w+\s+)?([\w\-]+)\s+([ap])\.?\s*m\.?/oi) {
		undef $Min;
		$AorP = lc($4);
		$temp1 = $1; $temp2 = $2; $temp3 = $3;
		if(defined($temp1) && Word2Num($temp1) &&
		   defined($temp2) && Word2Num($temp2) &&
		   Word2Num($temp3)) {
		    $Hour = Word2Num($temp1);
		    $Min  = Word2Num($temp2 . $temp3);
		} elsif(defined($temp1) && Word2Num($temp1) &&
			!defined($temp2)) {
		    $Hour = Word2Num($temp1);
		    $Min  = Word2Num($temp3);
		} else { $Hour = Word2Num($temp3); }

		if($TEstring =~ /half\s+past/io) { $Min = 30; }
		elsif($TEstring =~ /quarter\s+after/io) { $Min = 15; }
		elsif($TEstring =~ /quarter\s+(of|before|to|until)/io) {
		    $Min = 45;    $Hour--;
		}
		elsif($TEstring =~ /(\d+|\w+(-\w+)?)\s+(minutes?\s+)?(before|after|past|of|to|until)/io) {
		    $temp1 = $4;
		    $Min = Word2Num(lc($1));
		    if($temp1 =~ /(of|before|to|until)/io) {
			$Min = 60 - $Min;  $Hour--; }
		}
		
		if(($AorP eq "p") && ($Hour < 12)) { $Hour += 12; }
		if(($AorP eq "a") && ($Hour == 12)) { $Hour = 0; }
		
		if(defined($Min)) { $Val = sprintf("%02d%02d", $Hour, $Min); }
		elsif($Exact) { $Val = sprintf("%02d00", $Hour); }
		else { $Val = sprintf("%02d", $Hour); }
		$Attributes .= " $valTagName=\"T$Val$TimeZone\"";
		if(($Hour>24) ||
		   ((defined($Min)) && ($Min > 59)) ||
		   (($AorP eq "a")  && ($Hour > 12))) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		$FoundVal = 1;
	    }

	    # in the (morning|evening)
	    elsif($TEstring =~
		  /(\d\d?)(:(\d\d)|\s+o\'clock)?\s+(in\s+the\s+(morning|afternoon|evening)|at\s+night)/oi) {
		$Hour = $1;
		$Min  = $3;
		$AorP = lc($4);
		if(($AorP =~ /(afternoon|evening|night)/io) && ($Hour < 12)) {
		    $Hour += 12; }
		if(($AorP =~ /morning/io) && ($Hour == 12)) { $Hour = 0; }
		if($TEstring =~ /half\s+past/io) { $Min = 30; }
		elsif($TEstring =~ /quarter\s+after/io) { $Min = 15; }
		elsif($TEstring =~ /quarter\s+(of|before|to)/io) {
		    $Min = 45;    $Hour--;
		}
		elsif($TEstring =~ /(\d+|\w+(-\w+)?)\s+minutes?\s+(before|after|past|of|to|until)/io) {
		    $Min = Word2Num(lc($1));
		    $temp1 = $3;
		    if($temp1 =~ /(of|before|to|until)/io) {
			$Min = 60 - $Min;  $Hour--; }
		}

		if($Min) { $Val = sprintf("%02d%02d", $Hour, $Min); }
		elsif($Exact) { $Val = sprintf("%02d00", $Hour); }
		else { $Val = sprintf("%02d", $Hour); }
		$Attributes .= " $valTagName=\"T$Val\"";
		if(($Hour>24) || (defined($Min) && ($Min>59))) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		if(($AorP =~ /morning/io) && ($Hour>11)) {
		    $Attributes .= " ERROR=\"Inconsistent\""; }
		$FoundVal = 1;
	    }
	    
	    elsif(($TEstring =~
		   /([a-z]+)(\s+o\'clock)?\s+(in\s+the\s+(morning|afternoon|evening)|at\s+night)/oi) &&
		  ($temp1 = Word2Num($1)) && ($temp1 < 13)) {
		$Hour = $temp1;
		$AorP = lc($3);
		if(($AorP =~ /(afternoon|evening|night)/io) && ($Hour <12)) {
		    $Hour += 12; }
		if(($AorP =~ /morning/io) && ($Hour == 12)) { $Hour = 0; }
		$Val = sprintf("%02d", $Hour);
		if($TEstring =~ /half\s+past/io) { $Val .= "30"; }
		elsif($TEstring =~ /quarter\s+after/io) { $Val .= "15"; }
		elsif($TEstring =~ /quarter\s+(of|before|to)/io) {
		    $Hour--; $Val = sprintf("%02d45", $Hour);  }
		elsif($TEstring =~ /(\d+|\w+(-\w+)?)\s+minutes?\s+(before|after|past|of|to|until)/io) {
		    $Min = Word2Num(lc($1));
		    $temp1 = $3;
		    if($temp1 =~ /(of|before|to|until)/io) {
			$Min = 60 - $Min;  $Hour--;
		    }
		    $Val = sprintf("%02d%02d", $Hour, $Min);
		}

		$Attributes .= " $valTagName=\"T$Val\"";
		if($Hour>24) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		if(($AorP =~ /morning/io) && ($Hour>11)) {
		    $Attributes .= " ERROR=\"Inconsistent\""; }
		$FoundVal = 1;
	    }
	    
	    #     o\'clock      - No AM/PM clue
	    # OR  the hour of  - No AM/PM clue
	    elsif((($TEstring =~ /(\w+)\s+o\'clock/oi) &&
		   ($temp1 = Word2Num($1)) && ($temp1 < 13)) ||
		  (($TEstring =~ /the\s+hour\s+of\s+(\w+)/oi) &&
		   ($temp1 = Word2Num($1)) && ($temp1 < 13)) ||
		  (($TEstring =~ /(\w+(-\w+)?)\s+(minutes?\s+)?(before|after|past|of|to|until)\s+(\w+)/oi) &&
		   ($temp2 = Word2Num($1)) &&
		   ($temp1 = Word2Num($5)) && ($temp1 < 13))
		  ) {
		undef $Min;
		$Tag = "ALT_VAL";
		$Comment = " COMMENT=\"No AM/PM info\"";

		$Hour = $temp1;
		$Val = sprintf("%02d", $Hour);
		if($TEstring =~ /half\s+past/io) { $Val .= "30"; }
		elsif($TEstring =~ /quarter\s+after/io) { $Val .= "15"; }
		elsif($TEstring =~ /quarter\s+(of|before|to)/io) {
		    $Hour--; $Val = sprintf("%02d45", $Hour);  }
		elsif($TEstring =~ /(\w+(-\w+)?)\s+(minutes?\s+)?(before|after|past|of|to|until)/io) {
		    $Min = Word2Num(lc($1));
		    $temp1 = $4;
		    if($temp1 =~ /(of|before|to|until)/io) {
			$Min = 60 - $Min;  $Hour--;
		    }
		    $Val = sprintf("%02d%02d", $Hour, $Min);
		}

		# Search for broader context
		if(($orig_string =~/morning/io) &&
		   ($orig_string !~/(afternoon|evening|night)/io)) {
		    $Tag = "VAL"; $Comment = "";
		} elsif(($orig_string !~/morning/io) &&
			($orig_string =~/(afternoon|evening|night)/io)) {
		    $Tag = "VAL"; $Comment = "";
		    $Hour += 12;
		    if(defined($Min)) {
			$Val = sprintf("%02d%02d", $Hour, $Min); }
		    else { $Val = sprintf("%02d", $Hour); }
		}

		$Attributes .= " $Tag=\"T$Val\"";
		$Attributes .= $Comment;
		if($Hour>24) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		$FoundVal = 1;
	    }  # No AM/PM clue

	    # military type time
	    elsif($TEstring =~ /(\d\d):?(\d\d)/oi) {
		$Hour = $1;
		$Min  = $2;
		$Val = sprintf("%02d%02d", $Hour, $Min);
		$Attributes .= " $valTagName=\"T$Val$TimeZone\"";
		if(($Hour>24) || (defined($Min) && ($Min>59))) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		$FoundVal = 1;
	    }
	    elsif($TEstring =~ /(\d\d)(\d\d)\s+h(ou)rs?/oi) {
		$Hour = $1;
		$Min  = $2;
		$Val = sprintf("%02d%02d", $Hour, $Min);
		$Attributes .= " $valTagName=\"T$Val$TimeZone\"";
		if(($Hour>24) || (defined($Min) && ($Min>59))) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		$FoundVal = 1;
	    }
	    elsif($TEstring =~ /\b(\d\d?)\s+hours?(\s+(\d\d?)\s+minutes?)?/oi) {
		$Hour = $1;
		$Min  = $3;
		if($Min) { $Val = sprintf("%02d%02d", $Hour, $Min); }
		else { $Val = sprintf("%02d", $Hour); }
		$Attributes .= " $valTagName=\"T$Val$TimeZone\"";
		if(($Hour>24) || (defined($Min) && ($Min>59))) {
		    $Attributes .= " ERROR=\"Bad Time\""; }
		$FoundVal = 1;
	    }
	    
	} # if type=time


	##############################
	###  Code added by jfrank  ###
	##############################

	# type=duration
	elsif($Type eq "duration") {



	}

	##############################
	###   end of jfrank code   ###
	##############################


	else {
	    print STDERR "Bad type assigned!! $Type\n";
	}

	# Add  MOD\'s
	if($TEstring =~ /\b(late|end)\b/io) {
	    $Attributes .= " MOD=\"END\"";
	}elsif($TEstring =~ /\bno\s+more\s+than\b/io){
	    $Attributes .= " MOD=\"EQUAL_OR_LESS\"";
	}elsif($TEstring =~ /\bmore\s+than\b/io){ 
	    $Attributes .= " MOD=\"MORE_THAN\"";
	} elsif($TEstring =~ /\b(early|start|beginning|dawn of)\b/io) {
	    $Attributes .= " MOD=\"START\"";
	} elsif($TEstring =~ /\bmid(dle)?\b/io) {
	    $Attributes .= " MOD=\"MID\"";
	} elsif($TEstring =~ /\bat\s+least\b/io){
	    $Attributes .= " MOD=\"EQUAL_OR_MORE\"";
	}	
	if($TEstring =~ /(about|around|some)/io) {
	    $Attributes .= " MOD=\"APPROX\""; }

	$STag     =~ s/>/$Attributes>/;
	$output  .= "$STag$TE$EndTag";

    }  # While

    $output .= $string;

#####
#$output =~ s/<lex[^>]*>//g;
#$output =~ s/<\/lex>//g;
#####

    return($output);
    
} # TE_AddAttributes

########################################
sub Date2ISO {
    my($string, $ISO, $TOD);
    my($M, $D, $Y, $temp);
    my($H, $m, $S, $FS, $Z, $zone, $AMPM);
    ($string) = @_;

    # Already ISO
    if($string =~ /(\d\d\d\d-?\d\d-?\d\d)(-?(T\d\d(:?\d\d)?(:?\d\d)?([+-]\d{1,4})?))?/o) {
	$D = $1;  $H = $3;
	$D =~ s/-//go;
	if(defined($H)) {
	    $H =~ s/://go; 
	    return($D . $H); }
	else { return $D; }
    }
    ## ACE Format
    if($string =~ /(\d\d\d\d\d\d\d\d:\d\d\d\d)/o) {
	$D = $1; 
	$D =~ s/:/T/o;
	return $D; 
    }
    if($string =~ /T\d\d(:?\d\d)?(:?\d\d)?([+-]\d{1,4})?/o) {
	$string =~ s/://go;
	return($string);
    }

    if($string =~ /(\d\d?)\s+($TEmonth|$TEmonthabbr\.?),?\s+(\d\d(\s|\Z)|\d{4}\b)/oi) {
	$D = $1;    $M = $2;    $Y = $5;
	$M =~ /(\w{3})/o;
	$M = $Month2Num{lc($1)};
    } elsif($string =~ /($TEmonth|$TEmonthabbr\.?)\s+(\d\d?|$TEOrdinalWords)\b,?\s*(\d\d(\s|\Z)|\d{4}\b)/oi) {
	$D = $4;    $M = $1;    $Y = $6;
	if($D =~ /$TEOrdinalWords/o) { $D = $TE_Ord2Num{$D}; }
	$M =~ /(\w{3})/o;
	$M = $Month2Num{lc($1)};
    } elsif($string =~ /(\d\d\d\d)\/(\d\d?)\/(\d\d?)/o) {
	$M = $2; $D = $3; $Y = $1;
    } elsif($string =~ /(\d\d\d\d)\-(\d\d?)\-(\d\d?)/o) {
	$M = $2; $D = $3; $Y = $1;
    } elsif($string =~ /(\d\d?)\/(\d\d?)\/(\d\d(\d\d)?)/o) {
	$M = $1; $D = $2; $Y = $3;
    } elsif($string =~ /(\d\d?)\-(\d\d?)\-(\d\d(\d\d)?)/o) {
	$M = $1; $D = $2; $Y = $3;
    } elsif($string =~ /(\d\d?)\.(\d\d?)\.(\d\d(\d\d)?)/o) {
	$M = $2; $D = $1; $Y = $3;       # Euro date
    } elsif($string =~ /($TEmonth|$TEmonthabbr\.?)\s+(\d\d?).+(\d\d\d\d)\b/i) {
	$D = $4;    $M = $1;    $Y = $5;
	$M =~ /(\w{3})/o;
	$M = $Month2Num{lc($1)};
	unless(($Y<2100) && ($Y>1900)) { undef $Y; }
    }
    
    if(defined($Y)) {
	# Possible European Date
	if(($M > 12) && ($M < 31) && ($D < 12)) {
	    $temp = $M;   $M = $D;   $D = $temp;
	}
	# two digit year
	if($Y<39) { $Y += 2000; }
	elsif($Y<100) { $Y += 1900; }

	$ISO = sprintf("%4d%02d%02d", $Y, $M, $D);
    } else { $ISO = "XXXXXXXX"; }


    #Now add Time of Day
    if($string =~ /(\d?\d):(\d\d)(:(\d\d)(\.\d+)?)?(\s*([AP])\.?M\.?)?(\s+([+\-]\d+|[A-Z][SD]T|GMT([+\-]\d+)?))?/oi) {
	$H = $1;  $m = $2;  $S = $4; $FS = $5; $AMPM = $7; $zone = $9;
	if((defined($AMPM)) && ($AMPM =~ /P/oi)) { $H += 12; }
	if(defined($zone)) {
	    if($zone =~ /(GMT)([+\-]\d+)/o) { $zone = $2; }
	    elsif($zone =~ /GMT/o) { $zone = "Z"; }
	    elsif($zone =~ /([A-Z])([SD])T/o) {
		if(defined($TE_TimeZones{$1})) {
		    $Z = $TE_TimeZones{$1};
		    if($2 eq "D") { $Z++; }
		    if($Z<0) { $zone = sprintf("-%02d", -1*$Z); }
		    else { $zone = sprintf("+%02d", $Z); }
		}
	    }
	}
    } elsif($string =~ /(\d\d)(\d\d)\s+(h(ou)?r|(on\s+)?\d\d?\/\d)/oi) {
	$H = $1;  $m = $2;
    }
    
    if(defined($H)) {
	if(defined($FS)) { 
		$FS =~ s/\.//;	
		$TOD = sprintf("T%02d:%02d:%02d.%02d", $H, $m, $S, $FS); }
	elsif(defined($S)) { $TOD = sprintf("T%02d:%02d:%02d", $H, $m, $S); }
	elsif(defined($m)) { $TOD = sprintf("T%02d:%02d", $H, $m); }
	else { $TOD = sprintf("T%02d", $H); }
	$ISO .= $TOD;
	if(defined($zone)) {
	    if($zone =~ /\A\s+/o) { $zone = $'; }
	    $ISO .= $zone; }
    }

    return($ISO);
}  # Date2ISO


########################################
## Purely internal subroutines
########################################

sub OffsetFromRef {
    my($Ref, $Offset, $Gran);
    my($Month, $Day, $Year);
    my($Hour, $Minute, $ML);

    ($Ref, $Offset, $Gran) = (@_);
    unless(defined($Gran)) { $Gran = "D"; }

    if($Ref =~ /(\d\d\d\d)(\d\d)(\d\d)?(T(\d\d)(\d\d)?)?/o) {
	$Year  = $1;
	$Month = $2;
	$Day   = $3;
	$Hour  = $5;
	$Minute = $6;
    } else { return(""); }

    if($Gran eq "TH") {
	# Hours
	$Hour += $Offset;
	if($Hour > 23) {
	    $Offset = int($Hour/24);
	    $Hour   = $Hour % 24;
	    $Ref = sprintf("%4d%02d%02d", $Year, $Month, $Day);
	    $Ref = OffsetFromRef($Ref, $Offset, "D");
	    $Ref =~ /(\d\d\d\d)(\d\d)(\d\d)/o;
	    $Year  = $1;
	    $Month = $2;
	    $Day   = $3;
	} elsif($Hour < 0) {
	    $Offset = int($Hour/24) - 1;
	    $Hour   = $Hour % 24;
	    $Ref = sprintf("%4d%02d%02d", $Year, $Month, $Day);
	    $Ref = OffsetFromRef($Ref, $Offset, "D");
	    $Ref =~ /(\d\d\d\d)(\d\d)(\d\d)/o;
	    $Year  = $1;
	    $Month = $2;
	    $Day   = $3;	    
	}
	return(sprintf("%04d%02d%02dT%02d%02d", $Year, $Month, $Day, $Hour, $Minute));
    } elsif($Gran eq "TM") {
	# Minutes
	$Minute += $Offset;
	if($Minute > 60) {
	    $Offset = int($Minute/60);
	    $Minute = $Minute % 60;
	    $Ref = sprintf("%04d%02d%02dT%02d%02d", $Year, $Month, $Day, $Hour, $Minute);
	    $Ref = OffsetFromRef($Ref, $Offset, "TH");
	    return($Ref);
	} elsif($Minute <0) {
	    $Offset = int($Minute/60) - 1;
	    $Minute = $Minute % 60;
	    $Ref = sprintf("%04d%02d%02dT%02d%02d", $Year, $Month, $Day, $Hour, $Minute);
	    $Ref = OffsetFromRef($Ref, $Offset, "TH");
	    return($Ref);
	}
	return(sprintf("%04d%02d%02dT%02d%02d", $Year, $Month, $Day, $Hour, $Minute));
    } elsif($Gran eq "M") {
	# Month granularity
	$Month += $Offset;
	if($Month > 12) {
	    $Year += int($Month/12);
	    $Month = $Month % 12;
	} elsif ($Month < 0) {
	    $Year += int($Month/12) - 1;
	    $Month = $Month % 12;
	}
	if($Month == 0) {
	    $Month = 12;
	    $Year --;
	}
	return(sprintf("%4d%02d", $Year, $Month));
	
    } elsif($Gran eq "Y") {
	# Year granularity
	$Year += $Offset;
	return(sprintf("%4d", $Year));
    } else {
	# Day granularity
	$ML = &MonthLength($Month, $Year);

	$Day += $Offset;
	if($Offset > 0) {
	    # positive offsets
	    while($Day > $ML) {
		$Day -= $ML;
		$Month++;
		if($Month > 12) { $Month -= 12; $Year++; }
		$ML = &MonthLength($Month, $Year);
	    }
	} else {
	    # negative offsets
	    while($Day < 1) {
		$Month --;
		if($Month < 1) { $Month += 12; $Year--; }
		$ML = &MonthLength($Month, $Year);
		$Day += $ML;
	    }
	}
	return(sprintf("%4d%02d%02d", $Year, $Month, $Day));
    }

    return("");
    
} # End of subroutine OffsetFromRef


######################################## 
sub MonthLength {
    my($Month, $Year, $ML);
    ($Month, $Year) = @_;

    if(($Month == 2) && (IsLeapYear($Year))) { $ML = 29; }
    else { $ML = $TE_ML[$Month]; }
    return($ML);
} # End of subroutine MonthLength


######################################## 
sub IsLeapYear {
    # This is the Gregorian Calendar
    my($Year);
    ($Year) = @_;

    if((($Year % 400) == 0) ||
       ((($Year % 4) == 0) && (($Year % 100) != 0))) {
	return(1);
    } else { return(0); }

} # End of subroutine IsLeapYear

######################################## 
sub DayOfYear {
    my($Month, $Year, $Day, $ISO, $DOY);
    ($ISO) = @_;

    if($ISO =~ /\A(\d\d\d\d)(\d\d)(\d\d)/o) {
	$Year = $1; $Month = $2; $Day = $3;
	$DOY = $TE_CumML[$Month - 1] + $Day;
	if(IsLeapYear($Year) && ($Month > 2)) { $DOY++; }
	return($DOY);
    } else { return(0); }

} # End of subroutine DayOfYear


######################################## 
sub Week2Date {
    # Returns the date of the Thursday in the specified week.
    my($ISOin, $ISOout, $DOY, $Week, $Year);
    my($Month, $Day, $ML);
    ($ISOin) = @_;

    if($ISOin =~ /(\d\d\d\d)W(\d\d)/o) {
	$Year = $1;
	$Week = $2;
	$DOY  = $Week*7 - 3;

	$Month = 1;
	$ML = MonthLength($Month, $Year);
	while($DOY > $ML) {
	    $Month++;
	    if($Month > 12) { return "00000002";}
	    $DOY -= $ML;
	    $ML = MonthLength($Month, $Year);
	}
	$ISOout = sprintf("%04d%02d%02d", $Year, $Month, $DOY);
	return($ISOout);
    } else { return "00000001"; }

}  # End of subroutine Week2Date

######################################## 
sub Date2Week {
    my($Month, $Year, $Day, $ISO);
    my($D1, $DOY, $W);
    ($ISO) = @_;
    $W = 0;

    if($ISO =~ /\A(\d\d\d\d)(\d\d)(\d\d)/o) {
	$Year = $1; $Month = $2; $Day = $3;
	$D1   = "${Year}0101";
	$D1   = Date2DOW($D1);
	$DOY  = DayOfYear($ISO);

	if(($D1 > 4) && ($DOY < (7-$DOY))) {
	    $Year--;
	    $D1   = "${Year}0101";
	    $D1   = Date2DOW($D1);
	    $DOY += 365 + IsLeapYear($Year);
	}
	
	$W = int(($DOY + $D1 +5)/7);
	if($D1 > 4) { $W--; }
	$W = sprintf("%4.dW%02.d", $Year, $W);
	    
	return($W);
    } else { return(0); }

} # End of subroutine Date2Week


########################################
# Converts date to Day of Week
# Sunday = 0, Monday = 1, etc
sub Date2DOW  {
    my($YMD, $Month, $Day, $Year);
    my($A, $Y, $M, $D);

    ($YMD) = (@_);
    if($YMD =~ /(\d\d\d\d)(\d\d)(\d\d)/o) {
	$Year  = $1;
	$Month = $2;
	$Day   = $3; }
    else { return "7"; }

    $A = int((14 - $Month)/12);
    $Y = $Year - $A;
    $M = $Month + (12 * $A) - 2;
    $D = ($Day + $Y + int($Y/4) - int($Y/100)
	  + int($Y/400) + int(31*$M/12)) % 7;
    return($D);

} # Date2DOW

########################################
# Figures the nth DOW in month
# as a date for a given year
sub NthDOW2Date {
    my($month, $DOW, $nth, $year);
    ($month, $DOW, $nth, $year) = (@_);
    my($first, $shift);

    if($DOW == 7) { $DOW = 0; }
    # print "A: $month $DOW  $nth $year\n";
    
    $first = sprintf("%04d%02d01", $year, $month);
    # print "B: $first\n";
    $first = Date2DOW($first);
    $shift = $DOW - $first;

    # print "C: $first  $shift\n";
    
    if($shift < 0) { $shift += 7; }
    return($shift+(7*$nth)-6);
}

########################################
# Figures The date of easter for a given year
sub EasterDate {
    my($Y, $M, $D, $Date);
    ($Y) = (@_);
    my($G, $C, $H, $I, $J, $L);

    $G = $Y % 19;
    $C = int($Y / 100);
    $H = ($C - int($C/4) - int((8*$C+13)/25) + 19*$G +15) % 30;
    $I = $H - int($H/28)*(1 - int($H/28)*int(29/($H+1))*int((21-$G)/11));
    $J = ($Y + int($Y/4) + $I + 2 - $C +int($C/4)) % 7;
    $L = $I - $J;

    $M = 3 + int(($L+40)/44);
    $D = $L + 28 - 31*int($M/4);
    $Date = sprintf("%04d%02d%02d", $Y, $M, $D);
    return($Date);
}


########################################
# Converts numbers in words to numeric form
# works through trillions

sub Word2Num {
    my($string, $sum, $b4, $aft, $rest);
    ($string) = @_;

    my($temp1, $temp2);
    my($UW);
    
    $string = lc($string);
    if($string =~ /\s*(.*.\S)\s*/o) { $string = $1; }
    $sum = 0;
    $string =~ s/\s+and\s+/ /go;
    $string =~ s/-/ /go;
    $string =~ s/,//go;
    $string =~ s/\A\s*(.*\S)\s*/$1/o;

    if($string =~ /\A\d+(.\d*)?\Z/o) { return($&); }

    foreach $UW (@EndUnitWords) {
	if($string =~ /(.*)\s*$UW/) {
	    $b4  = $1;   $aft = $';
	    if($b4)  { $sum += $NumWords{$UW} * Word2Num($b4); }
	    else     { $sum = $NumWords{$UW}; }
	    if($aft) { $sum += Word2Num($aft); }
	    return($sum);
	}
    }

    foreach $UW (@UnitWords) {
	if($string =~ /(.*)\s*$UW\s*/) {
	    $b4  = $1;   $aft = $';
	    if($b4)  { $sum += $NumWords{$UW} * Word2Num($b4); }
	    else     { $sum = $NumWords{$UW}; }
	    if($aft) { $sum += Word2Num($aft); }
	    return($sum);
	}
    }

    if($NumWords{$string}) { return $NumWords{$string}; }
    
    if(($string =~ /\Aoh\s+(\w+)\Z/o) &&
       ($b4  = $1) && (defined($NumWords{$b4})) &&
       ($NumWords{$b4} < 10)) {
	return($NumWords{$b4});
    }
    if(($string =~ /\A(\w+)\s+(\w+)\Z/o) &&
       ($b4  = $1) && ($aft = $2) &&
       (defined($NumWords{$b4})) && (defined($NumWords{$aft})) &&
       ($NumWords{$aft} < 10) &&
       (($NumWords{$b4} > 19) || ($NumWords{$b4} = 0)) &&
       ($NumWords{$b4} == int($NumWords{$b4}/10)*10)) {
	$sum = $NumWords{$b4} + $NumWords{$aft};
	return($sum);
    }
    if(($string =~ /\A(\w+)/o) && ($b4 = $1) && ($aft = $') &&
       ($temp1 = Word2Num($b4)) && ($temp2 = Word2Num($aft)) &&
       ($temp1 > 9) && ($temp1 < 100) && ($temp2 < 100)) {
	$sum = 100*$temp1 + $temp2;
	return($sum);
    }
    # Still need  five oh four
    
    return("");

}  # Word2Num


########################
## jfrank subroutines ##
########################

sub printNonConsumingTIMEXes{
	foreach my $curNCT (@nonConsumingTIMEXes){
		print "$curNCT\n";
	}
}


#-recursive version of above function, converts words through trillions
#-takes words without POS tags

sub wordToNumber{
	my $word = shift;
	my $wordCopy = $word;

###
#  print STDERR "  word is $word\n";
###

	#lowercase
	$word = lc $word;

	#eliminate hypens, commas, and the word "and"
	$word =~ s/-/ /g;
	$word =~ s/,//g;
	$word =~ s/\sand//g;

	#if string starts with "a ", as in "a hundred", replace it with "one"
	if ($word =~ /^a\s/){ $word =~ s/a/one/;}

	#now count words
	my @dummyArray = split(/\s/,$word);
	my $numWords = @dummyArray;

	#replace each word with its number counterpart	
	my $curPart;
	my $curNum;
	my $curIndex = 1;
	while ($word =~ /([a-zA-Z]+)/g){
		$curPart = $1;

		if (exists $word2Num{$curPart}){
			$curNum = $word2Num{$curPart};
		} elsif (exists $ordWord2Num{$curPart}){
			if ($curIndex == $numWords){
				$curNum = $ordWord2Num{$curPart};
			} else {
				print STDERR "Error in wordToNumber function.\n";
				die "Ordinal number not in last position: Number is $wordCopy\n";
			}
		} else{
			die "Bad number put into wordToNumber.  Word is: $curPart\n";
		}

		$word =~ s/$curPart/$curNum/;
		$curIndex++;

	}
	return &wordToNumberRecurse($word);
}

sub wordToNumberRecurse{
	my $numString = shift;

	# return solitary number
	if ($numString =~ /^\d+$/){return $numString;}

	#first, find highest number in string
	my $highestNum = -1;
	my $curNumIndex = 0;
	my $highestNumIndex = 1;
	while ($numString =~ /(\d+)/g){
		$curNumIndex++;
		if ($1 > $highestNum){
			$highestNum = $1;
			$highestNumIndex = $curNumIndex;
		}
	}
	
	my @numberParts;
	my ($beforeString,$afterString);
	@numberParts = split(/\s?$highestNum\s?/,$numString);	
	my $numParts = @numberParts;
	if (($numParts > 2) or ($numParts < 1)){die "error with number string in wordToNumberRecurse\n";}

	#if highest number is between other numbers
	if (($highestNumIndex > 1) and ($highestNumIndex < $curNumIndex)){
		$beforeString = $numberParts[0];
		$afterString = $numberParts[1];
	} elsif ($highestNumIndex == 1){
		$beforeString = 1;
		$afterString = $numberParts[1];
	} elsif ($highestNumIndex == $curNumIndex) {
		$beforeString = $numberParts[0];
		$afterString = 0;		
	}

	my $beforeNum = &wordToNumberRecurse($beforeString);
	my $afterNum = &wordToNumberRecurse($afterString);

	my $evaluatedNumber = (($beforeNum * $highestNum) + $afterNum);
	return $evaluatedNumber;
}


##########
# replace thing like "three-hundred and four" (but also with POS tags) with 
#	"NUM_STARTthree-hundred and fourNUM_END" in a string
# NOTE: still need to deal with mixed cases like "12 million"
# Also, makes sure it deals with commas, like "one thousand, two hundred and seven"
# Also, make sure it deals with letter ", like "a hundred people"
# Also, need to deal with other words like "dozen","gross","score",etc..

sub deliminateNumbers{
	my $string = shift;

	my $numStart = "NUM_START";
	my $numEnd = "NUM_END";
	my $ordNumStart = "NUM_ORD_START";
	my $ordNumEnd = "NUM_ORD_END";

	my $middleOfNum = 0;
	my $curWord;
	my $rest = $string;
	$string = "";

	my $previousWord = "";
	my $nextWord = "";
	
	my $addTerm;
	while ($rest =~ /$OT+([a-zA-Z-]+)$CT+/g){
		$curWord = $2;
		my $oTags = $1;
		my $cTags = $3;

		$string .= $`;
		$rest = $';

		$rest =~ /$OT+([a-zA-Z-]+)/o;
		$nextWord = $2;		

		#the following deals reasonably well with hypenated numbers like "twenty-one"
		if ($curWord =~ /^$numberTerm(-$numberTerm)*$/i){	#current word is a number
			if ($middleOfNum == 0){	#first in (possible) series of numbers
				$addTerm = "$oTags$numStart$curWord$cTags";		
				$middleOfNum = 1;
			} else {    #either not first in series, or between ordinal and regular nums (i.e. "first two")
				if (($previousWord =~ /$ordUnitNums$/) or ($previousWord =~ /$ordOtherNums$/)){  #between ordinal and regular
					$string =~ s/($numStart(.*?))$/$ordNumStart$2/;	#replace with NUM_ORD_START
					$string =~ s/(($CT+)(\s*))$/$ordNumEnd$1/;	#insert in NUM_ORD_END
					$addTerm = "$oTags$numStart$curWord$cTags";		
				}else{	#number is continuing
					$addTerm = "$oTags$curWord$cTags";
				}
			}  #not first in series

		} else {				#current word is not a number
			if ($middleOfNum == 1){		#previous word was a number

				#following works fairly well...it avoids marking things like "six and two" as a single
				# number while still marking things like "two hundred and one" as a single number

				if (((lc $curWord) eq "and") and 	#number is continuing
                                    ($previousWord =~ /$higherNums/i) and 
				    (($nextWord =~ /$unitNums/i) or ($nextWord =~ /$uniqueNums/i) or
				     ($nextWord =~ /$tensNums(-$unitNums|-$ordUnitNums)?/i) or 
				     ($nextWord =~ /$ordUnitNums/i) or ($nextWord =~ /$ordOtherNums/i))){
					$addTerm = "$oTags$curWord$cTags";
				}else{						#number doesn't continue
					$middleOfNum = 0;

					# for ordinal numbers
					if (($previousWord =~ /$ordUnitNums$/) or ($previousWord =~ /$ordOtherNums$/)){
						$string =~ s/($numStart(.*?))$/$ordNumStart$2/;	#replace with NUM_ORD_START
						$string =~ s/(($CT+)(\s*))$/$ordNumEnd$1/;	#insert in NUM_ORD_END
					}else {   #for other numbers
						$string =~ s/(($CT+)(\s*))$/$numEnd$1/;	#insert in NUM_END
					}
					$addTerm = "$oTags$curWord$cTags";
				}
			} else { $addTerm = "$oTags$curWord$cTags";}
		}
		$string .= $addTerm;
		$previousWord = $curWord;
	}
	if (defined($curWord)){
		if ($curWord =~ /$numberTerm(-$numberTerm)*/){$string =~ s/(($CT+)(\s*))$/$numEnd$1/o;}	#if final word is a number
	}
	$string .= $rest;

	return $string;
}


# -For input string, adds TIDS to TIMEX2 tags, then returns updated string
# -Uses global variable $highestID

sub addTIDs{
	my $string = shift;
	my $currentTID;

	while ($string =~ /((<TIMEX$tever)([^>]*>))/g){
		my $curTag = $1;
		my $firstPart = $2;
		my $secondPart = $3;

		unless ($curTag =~ / $tidTagName=/i){	#unless tag already has a TID
			$currentTID = &getUniqueTID();
			my $newTag = $firstPart . " $tidTagName=\"$currentTID\"" . $secondPart;
			$string =~ s/$curTag/$newTag/;
		}
	}
	return $string;
}

sub getUniqueTID{
	my $curIDNum = $highestTID;
	$highestTID++;
	my $retVal = "t$curIDNum";
	return $retVal;
}

# Takes in an expression assumed to be a duration expression and returns a 
#   duration value, such as P3D for "3 days".  Other things assumed about 
#   the expressions:
#	-they may have POS tags, assuming opening tags are of the form <lex ...>
#	 and closing tags are of the form </lex>
#	-if they have numbers written out in words, i.e. "thirty seven", then
#	 the words have been tagged using deliminateNumbers(), thus, discluding
#	 POS tags, "thirty seven" would be tagged "NUM_STARTthirty sevenNUM_END" 

# Duration Format: PnYnMnDTnHnMnS


sub expressionToDuration{
	my $phrase = shift;
	my $multiplier = 1;  #only changes for special words like "decade", "week", etc..

	#note - some of the incorrect spelling in this hash is due to the generalized matching which will match things like "centurys"
	my %abbToTimeUnit = ("years" => "yr","year" => "yr","yrs" => "yr","yr" => "yr",
			     "months" => "mo","month" => "mo","mo" => "mo","mos" => "mo",
			     "days" => "da", "day" => "da",
			     "hours" => "hr", "hour" => "hr", "hrs" => "hr", "hr" => "hr",
			     "minutes" => "mi","minute" => "mi","mins" => "mi","min" => "mi",
			     "seconds" => "se","second" => "se","secs" => "se","sec" => "se",
			     "weeks" => "wk","week" => "wk","wks" => "wk","wk" => "wk",
			     "decades" => "de","decade" => "de","decs" => "de","dec" => "de",
			     "centurys" => "ct","century" => "ct","centuries" => "ct","centurie" => "ct",
			     "millenias" => "ml","millenia" => "ml","milleniums" => "ml","millenium" => "ml");

	#get rid of POS tags
	$phrase =~ s/<lex[^>]*>//g;
	$phrase =~ s/<\/lex>//g;

	#get rid of opening and closing tags
	$phrase =~ s/^$OT+//;
	$phrase =~ s/$CT+$//;


#####
#print STDERR "phrase is: $phrase\n";
#####


	#change number-words into actual numbers
	while ($phrase =~ /(NUM_START(.*?)NUM_END)/g){
		my $curPart = $1;
		my $curWordNum = $2;
		my $curNumNum = &wordToNumber($curWordNum);
		$phrase =~ s/$curPart/$curNumNum/;
	}
	while ($phrase =~ /(NUM_ORD_START(.*?)NUM_ORD_END)/g){
		my $curPart = $1;
		my $curWordNum = $2;
		my $curNumNum = &wordToNumber($curWordNum);
		$phrase =~ s/$curPart/$curNumNum/;
	}


	my %durVals = ("yr" => -1,"mo" => -1, "da" => -1, "hr" => -1, "mi" => -1, "se" => -1);
	my $curUnit;
	my $curVal;


	#i.e. "3 month", "6-day-old"
	if ($phrase =~ /^(\d*)[-\s]$TE_Units([-\s]old)?$/){
		$curVal = $1;
		$curUnit = $2;

	#i.e. "the past 3 months"
	} elsif ($phrase =~ /the\s([pl]ast|next)\s(\d*)\s($TE_Units(s)?)/){
		$curVal = $2;
		$curUnit = $4;
	#jbp i.e. "no more than"
	} elsif ($phrase =~ /^no\smore\sthan\s(\d*)\s$TE_Units(s)?$/i){
		$curVal = $1;
		$curUnit = $2;
	#jbp i.e. "more than" 	
	} elsif ($phrase =~ /^more\sthan\s(\d*)\s$TE_Units(s)?$/i){
		$curVal = $1;
		$curUnit = $2;
	#jbp i.e. "at least.. " 	
	} elsif ($phrase =~ /^at\sleast\s(\d*)\s$TE_Units(s)?$/i){
		$curVal = $1;
		$curUnit = $2;
	#i.e. "another thirteen months"
	} elsif ($phrase =~ /^another\s(\d*)\s$TE_Units(s)?$/){
		$curVal = $1;
		$curUnit = $2;

	############  NEED TO FIX THIS SO A NP OR VP COMES AT END
	#i.e. "for ten minutes following"
#        } elsif ($phrase =~ /^(the|for)\s(\d+)\s($TE_Units)(s)?\s(since|after|following|before|prior\sto|previous\sto)$/){ 
        } elsif ($phrase =~ /^the\s(\d+)\s($TE_Units)(s)?$/){ 
		$curVal = $1;
		$curUnit = $2;

	###########  NEED TO FIX THIS SO THAT SOMETHING COMES AFTER "OF"  ###############
	#i.e. "the first six months of", "the last 7 minutes of"
#	} elsif ($phrase =~ /^the\s(1|last|final)\s(\d+)\s$TE_Units(s)?\sof$/){
	} elsif ($phrase =~ /^the\s(1|last|final)\s(\d+)\s$TE_Units(s)?$/){
		$curVal = $2;
		$curUnit = $3;

	#i.e. "the eighth consecutive day in a row"
	} elsif ($phrase =~ /^the\s(\d+)\s(straight|consecutive)\s($TE_Units)(\s(in\sa\srow|consecutively))?$/){
		$curVal = $1;
		$curUnit = $3;

	#i.e. "the twenty ninth day straight"	
	} elsif ($phrase =~ /^the\s(\d*)\s($TE_Units)\s(straight|consecutively|in\sa\srow)$/){
		$curVal = $1;
		$curUnit = $2;

	#i.e. "four minutes"
	} elsif ($phrase =~ /^(\d+)\s($TE_Units)(s)?$/){
		$curVal = $1;
		$curUnit = $2;

	#i.e. "a decade", "a couple years"
        } elsif ($phrase =~ /^a\s((few|couple|couple\sof)\s)?($TE_Units(s)?)$/){
		$curVal = $1;
		$curUnit = $3;
		if (defined($curVal)){
			$curVal = "X"; 	#for underspecified values
		}else{
			$curVal = 1;
		}

	} else{
		print STDERR 'Unrecognized pattern in expressionToDuration';
		die "\n   Processed (POS tags removed and number words converted) pattern is: $phrase\n";
	}
	


	if ($curUnit =~ /\.$/) {chop($curUnit);}  #remove trailing period (in abbreviations)
	$curUnit = $abbToTimeUnit{$curUnit};

	#deal with special cases - week, decade, century, millenium
	if ($curUnit eq "wk"){
		$multiplier = 7;
		$curUnit = "da";
	} elsif ($curUnit =~ "de"){
		$multiplier = 10;
		$curUnit = "yr";
	} elsif ($curUnit =~ "ct"){
		$multiplier = 100;
		$curUnit = "yr";
	} elsif ($curUnit =~ "ml"){
		$multiplier = 1000;
		$curUnit = "yr";
	}
	if ($curVal =~ /^\d*$/){
		$curVal = $curVal * $multiplier;
	}

	$durVals{$curUnit} = $curVal;		
	


	
	my $durString = "P";
	unless ($durVals{"yr"} =~ /^-1$/){
		$durString = $durString . $durVals{"yr"} . "Y";
	}
	unless ($durVals{"mo"} =~ /^-1$/){
		$durString = $durString . $durVals{"mo"} . "M";
	}
	unless ($durVals{"da"} =~ /^-1$/){
		$durString = $durString . $durVals{"da"} . "D";
	}

	$durString .= "T";

	unless ($durVals{"hr"} =~ /^-1$/){
		$durString = $durString . $durVals{"hr"} . "H";
	}
	unless ($durVals{"mi"} =~ /^-1$/){
		$durString = $durString . $durVals{"mi"} . "M";
	}
	unless ($durVals{"se"} =~ /^-1$/){
		$durString = $durString . $durVals{"se"} . "S";
	}

	#if $durString ends in T
	if ($durString =~ /T$/){chop($durString);}

	return $durString;
}

#returns tids for the begin and endpoints of an expression in the format "btid:etid"
sub expressionToPoints{
	my $phrase = shift;
	my $beginPoint = "";
	my $endPoint = "";
	

	#get rid of POS tags
	$phrase =~ s/<lex[^>]*>//g;
	$phrase =~ s/<\/lex>//g;

	#get rid of opening and closing tags
	$phrase =~ s/^$OT+//;
	$phrase =~ s/$CT+$//;

	#change number-words into actual numbers
	while ($phrase =~ /(NUM_START(.*?)NUM_END)/g){
		my $curPart = $1;
		my $curWordNum = $2;
		my $curNumNum = &wordToNumber($curWordNum);
		$phrase =~ s/$curPart/$curNumNum/;
	}
	while ($phrase =~ /(NUM_ORD_START(.*?)NUM_ORD_END)/g){
		my $curPart = $1;
		my $curWordNum = $2;
		my $curNumNum = &wordToNumber($curWordNum);
		$phrase =~ s/$curPart/$curNumNum/;
	}


	#i.e. "3 month", "6-day-old"
	if ($phrase =~ /^(\d*)[-\s]$TE_Units([-\s]old)?$/){
		## not doing anything yet

	#i.e. "the past 3 months"
	} elsif ($phrase =~ /the\s([pl]ast|next)\s\d*\s($TE_Units(s)?)/){
		my $curOp1 = $1;
		if ($curOp1 =~ /[pl]ast/){
			$endPoint= $unspecTIDVal;
		} elsif ($curOp1 =~ /next/){
			$beginPoint= $unspecTIDVal;
		}

	#i.e. "another thirteen months"
	} elsif ($phrase =~ /^another\s\d*\s$TE_Units(s)?$/){
		$beginPoint= $unspecTIDVal;

	} elsif ($phrase =~ /^no\smore\sthan\s\d*\s$TE_Units(s)?$/i){
		$beginPoint= $unspecTIDVal;
	} elsif ($phrase =~ /^more\sthan\s\d*\s$TE_Units(s)?$/i){
		$beginPoint= $unspecTIDVal;
	} elsif ($phrase =~ /^at\sleast\s\d*\s$TE_Units(s)?$/i){
		$beginPoint= $unspecTIDVal;
	#i.e. "for ten minutes following"
#        } elsif ($phrase =~ /^(the|for)\s\d+\s($TE_Units)s?\s(since|after|following|before|prior\sto|previous\sto)$/){ 
        } elsif ($phrase =~ /^the\s(\d+)\s($TE_Units)(s)?$/){ 

		my $curOp1 = $3;
		if ($curOp1 =~ /(since|after|following)/){
			$beginPoint= $unspecTIDVal;
		} elsif ($curOp1 =~ /(before|prior to|previous to)/){
			$endPoint= $unspecTIDVal;
		}

	###########  NEED TO FIX THIS SO THAT SOMETHING COMES AFTER "OF"  ###############
	#i.e. "the first six months of", "the last 7 minutes of" - note that "first" gets translated to "1"
#	} elsif ($phrase =~ /^the\s(1|last|final)\s(\d+)\s$TE_Units(s)?\sof$/){
	} elsif ($phrase =~ /^the\s(1|last|final)\s(\d+)\s$TE_Units(s)?$/){

		## not doing anything yet - pattern is too vague

	#i.e. "the eighth consecutive day in a row"
	} elsif ($phrase =~ /^the\s(\d+)\s(straight|consecutive)\s($TE_Units)(\s(in\sa\srow|consecutively))?$/){
		$endPoint= $unspecTIDVal;

	#i.e. "the twenty ninth day straight"	
	} elsif ($phrase =~ /^the\s(\d*)\s($TE_Units)\s(straight|consecutively|in\sa\srow)$/){
		$endPoint= $unspecTIDVal;

	#i.e. "four minutes","a decade"
	} elsif ($phrase =~ /^(\d+)\s($TE_Units)(s)?$/){
		## not doing anything yet - pattern is too vague

        } elsif ($phrase =~ /^a\s((few|couple|couple\sof)\s)?($TE_Units(s)?)$/){
		## not doing anything yet - pattern is too vague

	} else{
		print STDERR 'Unrecognized pattern in expressionToPoints';
		die "\n   Processed (POS tags removed and number words converted) pattern is: $phrase\n";
	}

	my $retVal = $beginPoint . ":" . $endPoint;
	return $retVal;

}

sub getUnspecifiedTID{
	my $retval = "";
	if ($useUnspecTID == 1){			
		$retval= $tiddef;
		if ($useDCT == 1){$retval = $tidDCT;}
	}
	return $retval;
}

###############################
## end of jfrank subroutines ##
###############################






# End of internal subroutines
###############################################

1;
# Copyright 2001 - The MITRE Corporation

__END__
The rest of this is to provide documentation through perldoc.

= pod Begin TempEx Documentation

=head1 Module TempEx

 Title:          Temporal Expressions
 Version:        1.05
 Purpose:        Tagging temporal expressions
 Author:         Dr. George Wilson
 Organization:   The MITRE Corporation
 email:          gwilson@mitre.org
 Date:		 Dec-2001

=head1 B<Copyright Notice>

=item Copyright 2001 The MITRE Corporation

=item Use of this code is subject to a licensing agreement.

=item

Better documentation is planned, but
right now you are stuck with this.

 Usage

 There are two main functions and two variables to control behaviour.
 
 Function: TE_TagTIMEX
    The input is assumed to be a sentence that has been tagged
    with part-of-speech tags. The output is the same sentence
    with additional tags to mark time expressions. The part of
    speech tagging is assumed to be compatible with the current
    version of the prelembic tagger that comes with the alembic
    distribution.

 Function: TE_AddAttributes
    The function takes two input arguments: a sentence and a
    reference time. The sentence is assumed to be a string that
    has been tagged with part-of-speech tags and for time expressions.
    The reference time is a string in the format YYYYMMDDHHMM.
    The output is the same sentence with attributes added to the time
    tags. Which rules are used and the extent of the attributes are
    controlled by the variables TE_DEBUG and TE_HeurLevel.

 Function: Date2ISO
    The function takes a single string as an input arguement
    and attempts to reformat it into an ISO date. This may
    be useful for making driver programs.
    
 Variable: Debug flag
   TE_DEBUG = 0          means only required output (default)
                         This will be the TYPE and VAL if applicable.
   TE_DEBUG = 1          include TEXT=XXX attribute
   TE_DEBUG = 2          all output - right now, that includes
			 information about rules used and interpretation
			 of verbs.

 Variable: Heuristic Level
   TE_HeurLevel = 0      only absolute dates are used (RefTime not used)
   TE_HeurLevel = 1      Allow rules based on strong linguistics markers
   TE_HeurLevel = 2      also use solid verb rules
   TE_HeurLevel = 3      additional heuristics (default)


=cut


v
