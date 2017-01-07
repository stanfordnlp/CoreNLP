#!/usr/bin/perl -w
# driver for tagging time expressions
# this driver is aimed at TDT data
# Author:         George Wilson  - gwilson@mitre.org
# Last modified:  November, 2001
# Copyright 2001  The MITRE Corporation


## Expecting POS tags of the form
## <lex pos="NN">dog</lex>
## Change this if you use a different name
SetLexTagName("lex");


###   Nothing to change below this line

### variables added by jfrank ####
#these vars also need to be changed in: TempEx.pm
my $docTag = "DOC";
my $tever = 3;		#Timex Version
my $valTagName = "VAL";
##
my $printNCTs = 1;	#if set to 1, prints out non-consuming TIMEXes at the end of the printout

###  End of jfrank variables  ###

# Read one document at a time
$/ = "<\/$docTag>";

use  TempEx;
use  TempEx qw($TEmonthabbr %Month2Num);

$HL        = 3;
$FirstDate = 0;
$TagHeader = 1;
$WARN      = 1;

#Process command line flags
while((defined $ARGV[0]) && ($ARGV[0] =~ /^-/)) {
    $flag = shift(@ARGV);

    if($flag eq "-h") { &Usage(); exit(1); }
    elsif($flag =~ /\A-D(\d)\Z/o)  { $TE_DEBUG  = $1; }
    elsif($flag =~ /\A-HL(\d)\Z/o) { $HL        = $1; }
    elsif($flag =~ /\A-FD\Z/o)     { $FirstDate = 1; }
    elsif($flag =~ /\A-FDNW\Z/o)   { $FirstDate = 1; $WARN = 0;}
    elsif($flag =~ /\A-TH\Z/o)     { $TagHeader = 1; }
    else {
	print "Unknown flag $flag\n";
	&Usage(); exit(1);
    }
}

# Main loop to process documents
while(<>) {
    unless(/<\/$docTag>/o) { print; next; }
    $TE_HeurLevel = $HL;
    #extract date
    if(/<(DATE_TIME|DATE|DL|DD)>\s*(.*)\s*<\/(DATE_TIME|DATE|DL|DD)>/oi) {
	$temp = $2;
#### Added by jfrank #########
# - I also added "DOCNO" to the above "if" statement
	my $curTagType = $3;
	
	if ($curTagType eq "DOCNO"){
		if ($temp =~ /[a-zA-Z]+(\d\d\d\d\d\d)\D/){
			$temp = $1 + 19000000;				#assume it's the 1900s, perhaps should change
		} elsif ($temp =~ /[a-zA-Z]+(\d\d\d\d\d\d\d\d)\.\d/){
			$temp = $1;
		}
	}elsif ($curTagType eq "DD"){
		if ($temp =~ /= (\d\d\d\d\d\d) /){$temp = $1 + 19000000;}   #same as above comment
	}
#### End of jfrank code #######
	$RefDate = Date2ISO($temp);
	if($RefDate =~ /XXXXXXXX/o) {
	    print STDERR "Failed to understand input date: $temp\n";
	    print STDERR " Please report this format to the author.\n";
	    print;   next;
	}
	if($TagHeader) {
	    $QMT = quotemeta($temp);
	    #s/>$QMT</><TIMEX$tever $valTagName=\"$RefDate\">$temp<\/TIMEX$tever></i;
	    s/$QMT/<TIMEX$tever $valTagName=\"$RefDate\">$temp<\/TIMEX$tever>/i;
	}
    } elsif(/<[^>]*air_date=\"([^\"]+)\">/oi) {
	$temp = $1;
	$RefDate = Date2ISO($temp);
	if($RefDate =~ /XXXXXXXX/o) {
	    print STDERR "Failed to understand input date: $temp\n";
	    print STDERR " Please report this format to the author.\n";
	    print;   next;
	}
	if($TagHeader) {
	    $QMT = quotemeta($temp);
	    s/>$QMT</><TIMEX$tever $valTagName=\"$RefDate\">$temp<\/TIMEX$tever></i;
	}
    } elsif (/(\A|\n)Date:(.*)/io) {
	$temp = $2;
	chomp $temp;
	if (/(\A|\n)Time:(.*)/io) {
	    $temp2 = $2;
	    chomp $temp2;
	    if(($temp2 =~ /\d\d\d\d/o) && ($temp2 !~ /[^\s0-9]/o)) {
		$temp2 .= " hours";
	    }
	    $temp .= " $temp2";
	}
	$RefDate = Date2ISO($temp);
   	if($TagHeader){
	    $QMT = quotemeta($temp);
	    s/$QMT/<TIMEX$tever $valTagName=\"$RefDate\">$temp<\/TIMEX$tever>/i;
	} 
	} elsif (/\S/) {
	$temp = length($_);
	unless($FirstDate)  {
	    print STDERR "Warning: No date tag - $temp\n";
	    print STDERR "You might want to use the FD flag\n";
	    print STDERR "Skipping this document.\n";
            #print LOG "ERROR (TimeTag.pl): No date tag, skipping document\n";
	    if($temp < 100) { print STDERR "$_\n\n"; }
	    print;  next;
	}
	if(/$TEmonthabbr\.?\w*\s+(\d+),?\s+(\d{4})/io) {
	    $RefDate = sprintf("%4d%02d%02d", $3, $Month2Num{lc($1)}, $2);
	} else {
	    if($WARN) {
		print STDERR "Warning: No reference date found in document\n"; }
	    $TE_HeurLevel = 0;
	    $RefDate = 0;
	}
    }
	
    $Rest = $_;
	# loop through sentences
    while($Rest =~ /(<\/s>)/io) {
	$b4     = $`;
	$EndTag = $1;
	$Rest   = $';
	
	$b4 =~ /(.*)(<s>)/ios;
	print $1;	
	$StartTag = $2;
	$Sent = $';
	
	# Process sentences here
	$Sent = &TE_TagTIMEX($Sent);
	$Sent = &TE_AddAttributes($Sent, $RefDate);

        print "$StartTag$Sent$EndTag";

### CHANGED BY jfrank ########
	$StartTag = "";
	$EndTag = "";
##############################

#	print "$StartTag$Sent$EndTag";

    }
    #tag the end-time timestamp if it exists  
    if($Rest =~ /<END_TIME>\s*(.*)\s*<\/END_TIME>/moi) {
	$temp = $1;
	
	$EndDate = Date2ISO($temp);
	if($EndDate =~ /XXXXXXXX/o) {
	    print STDERR "Failed to understand input date: $temp\n";
	    print STDERR " Please report this format to the author.\n";
	    print;   next;
	}
	$QMT = quotemeta($temp);
	$Rest =~ s/$QMT/<TIMEX$tever $valTagName=\"$EndDate\">$temp<\/TIMEX$tever>/i;
    }
    print "$Rest";

    ## Added by jfrank
    if ($printNCTs == 1){
	    print "\n";
	    &printNonConsumingTIMEXes();
    }
    ##
}




sub Usage() {
    # Display help message

    print "Usage:   TimeTag.pl [-h -FD -TH -Dn -HLn] files\n";
    print "                                  n is a number\n";
    print "         h    = help message\n";
    print "         FD   = First Date found will be used as reference date\n";
    print "         FDNW = First Date found will be used as reference date\n";
    print "                No Warnings given\n";
    print "         TH   = Tag Header, not tagged by default\n";
    print "         D    = Debug Level      -  0,1,2     default=0\n";
    print "         HL   = Heuristic Level  -  0,1,2,3   default=3\n\n";

}

# Copyright 2001  The MITRE Corporation
