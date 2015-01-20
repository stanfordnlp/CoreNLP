#!/usr/bin/perl

#This code is used to introduce Temporal Functions into TIMEX3-tagged text as specified by TimeML1.1. 
#The following Temporal Functions introduced by this code are laid out in the August 18, 2004 spec created
#by Jessica Littman 
#  
#         1) Indefinite Future 
#         2) Indefinite Past
#         5) Identity
#         7) BeginPoint/EndPoint
#		other functions to follow
#         3) Coerce-to function
#
#Jon Phillips
#Georgetown University
#
# cat <TIMEX3DOCNAME> ./postTempEx.pl

use strict;

my $debug = 0;


#maps triggers for the TIMEX3 "value=" to their respective functions.  e.g. A TIMEX3 tag with value="FUTURE_REF" will be
#directed to code that creates an IndefiniteFuture function 
my %function_trigger=  ("FUTURE_REF","IndefiniteFuture",
			"PAST_REF","IndefinitePast",
			"PRESENT_REF","Identity");

#triggers for consumed text that indicates a begin/endpoint function is necessary 
my %beginend_trigger = ("the end of","END",
			"the beginning of","BEGIN",
			"the start of", "BEGIN",
			); 

#an array of timefunctions that are appended to the end of document 
my @timefunctions;
my $num_tf =1;


my $line;
my $nontag;
my $tag;
my $argument_id=0;
my @parts;
my $endtag;

my $isbegin;

#the text consumed by the tag
my $tagconsume;


while($line = <>)
{
    $/ = "\</TIMEX3>";

    my @entries;
    my %attribval;

	if($line =~ /<TIMEX3/){
		@parts = split(/<TIMEX3/,$line);
		#on the TIMEX3 tokens, split the actual TIMEX3 tag extent from the non-tag text 
		$nontag = $parts[0];
		$tag = $parts[1];
	
		$tag =~ /((.|\n)*?)(>(.|\n)*)/;
			
		$tag = $1;
		$endtag = $3;		
	
		#capture the text consumed by the tag  
		$endtag =~ />((.|\n)*)</;  	
		$tagconsume = $1;	
		print "$nontag<TIMEX3";	
		
		#process TIMEX3 tag attribute-values	
		#print "$tag";
		@entries = split(/\s+/,$tag);
		%attribval = ();
		foreach(@entries){
			if($_ =~ /=/){	
				$_ =~ /(\S*)="(\S*)"/;
                
				#parse a tag into attrib/val hash for easy manipulation 
				if($debug){	
					print "\ndebug:attrib,val:$1,$2\n";	
				}	
				$attribval{$1}= $2;	
			}	
		}
 			
		if($function_trigger{$attribval{"VAL"}} eq "IndefiniteFuture"){
			if($debug){
				#print "\nDebug:found IndefiniteFuture function\n";
			}	
			my $newattribval = &NewAttribVal($num_tf,$argument_id,%attribval); 
			#print out the new attribute/vals....now timex tag has a temporal function
			$tag .= "$newattribval";	
			push @timefunctions, &IndefiniteFuture_create($num_tf,$argument_id);	
			$num_tf++;
		}elsif($function_trigger{$attribval{"VAL"}} eq "IndefinitePast"){
			if($debug){	
				#print "\nDebug:found IndefinitePast function\n";
			}
			my $newattribval = &NewAttribVal($num_tf,$argument_id,%attribval); 
			$tag .= "$newattribval";	
			push @timefunctions, &IndefinitePast_create($num_tf,$argument_id);
			$num_tf++;
		
		}elsif($function_trigger{$attribval{"VAL"}} eq "Identity"){
			if($debug){	
				#print "\nDebug:found Identity function\n";
			}
			#print "\n\n GOT HERE\n";	
			my $newattribval = &NewAttribVal($num_tf,$argument_id,%attribval); 
			$tag .= "$newattribval";	
			push @timefunctions, &Identity_create($num_tf,$argument_id);
			$num_tf++;

		}elsif($attribval{"VAL"} =~ /^[\d\-]*(M|D|T|W)[\d\-]*$/){
			my $scale = $1;	
			if($debug){	
				print "\nDebug:found CoerceTo function\n";
			}
		        if($tagconsume =~ /^(last|past|next)\s*(.*)\s*/i){
				my $newattribval = &NewAttribVal($num_tf,$argument_id,%attribval); 
				$tag .= "$newattribval";	
				push @timefunctions, &CoerceTo_create($num_tf,$argument_id,$scale);	
				$num_tf++;	
               		} 
		#checks for begin end tag by looking at text consumed	
		}else{
			my $trigger;
			my $value;	
			foreach $trigger (keys %beginend_trigger){

				if($tagconsume =~ /^$trigger\s*(.*)\s*/i){
					$value = $1;
					if($beginend_trigger{$trigger} eq "BEGIN"){
						$isbegin = 1;
					}else{	
						$isbegin = 0;	
					}	
					if($debug){	
						#print "\nDebug:found Identity function\n";
					}
					my $newattribval = &NewAttribVal($num_tf+1,$argument_id,%attribval); 
					$tag .= "$newattribval";	
					push @timefunctions, &BeginEndPoint_create($num_tf,$argument_id,$isbegin,$value);
					$num_tf+=2;
				}	
			}	
		}			
		
		print "$tag$endtag";	
	}
	else{
		print "$line\n";
	}
		
	
}

#print each timefunction
foreach(@timefunctions){
	print "$_\n";
}
#################################################################################################
#################################################################################################
#sub to create new attribute-val pairs for the originating tag (not the function)
sub NewAttribVal{
	use strict;
	my($tfid,$anchorid,%atval) = @_;

	my $newtags;
	if($atval{"temporalFunction"} eq "false"){
		print "\n\nSYSTEM ERROR: Trying to create a temporal function for a TIMEX3 tag marked explicitly as temporalFunction=false\n\n";
		exit(1);
	}
		
	if(!$atval{"temporalFunction"}){	
		$newtags .= " temporalFunction=\"true\""; 
	}	
	$newtags .= " valueFromFunction=\"tf$tfid\""; 
	$newtags .= " anchorTimeID=\"t$anchorid\""; 

	return $newtags;
}
#################################################################################################
#IndefiniteFuture function sub
sub IndefiniteFuture_create{
	use strict;
	my($tfid,$argid) = @_;
	return  "<IndefiniteFuture tfid=\"tf$tfid\" argumentID=\"t$argid\"/>";
}

#IndefinitePast function sub
sub IndefinitePast_create{
	use strict;
	my($tfid,$argid) = @_;
	return  "<IndefinitePast tfid=\"tf$tfid\" argumentID=\"t$argid\"/>";
}

#Identity function sub
sub Identity_create{
	use strict;
	my($tfid,$argid) = @_;
	return  "<Identity tfid=\"tf$tfid\" argumentID=\"t$argid\"/>";
}

#BeginPoint/EndPoint Function
sub BeginEndPoint_create{
	use strict;
	my($tfid,$argid,$isbegin,$value) = @_;
	
	my $functionname;
	if($isbegin == 1){
		$functionname = "BeginPoint";
	}else{
		$functionname = "EndPoint";
	}
	my $part1 = "<GetNamedElementOf tfid=\"tf$tfid\" argumentID=\"t$argid\" value=\"$value\"/>"; 
	my $resultid = $tfid+1;	
	my $part2 = "<$functionname tfid=\"tf$resultid\" argumentID=\"tf$tfid\"/>";
	my $results = "$part1\n$part2\n";
	return $results;
}

sub CoerceTo_create{
	use strict;
	my($tfid,$argid,$scaletype) = @_;
	my $scale;
	
	if($scaletype eq "W"){
		$scale = "WEEK";	
	}
	elsif($scaletype eq "M"){
		$scale = "MONTH";	
	}	
	elsif($scaletype eq "D"){
		$scale = "DAY";	
	}	
	elsif($scaletype eq "YEAR"){
		$scale = "YEAR";	
	}	
	
	return "<CoerceTo tfid=\"tf$tfid\" argumentID=\"t$argid\" scale =\"$scale\"/>";	
}
