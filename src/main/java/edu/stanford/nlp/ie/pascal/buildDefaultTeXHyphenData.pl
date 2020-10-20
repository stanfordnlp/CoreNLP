use strict;

my $templateFilename = shift;
my $datFile = shift;
my $sourceFilename = shift;

open DAT, "<$datFile" or die;
my @hyphenLines = <DAT>;
chomp @hyphenLines;

# format the data as a Java string literal
@hyphenLines = map { qq'    "$_\\n"' } @hyphenLines;
my $insertString = join(" +\n", @hyphenLines) . "\n";

open INPUT, "<$templateFilename" or die;
open OUTPUT, ">$sourceFilename" or die;

my $line;
while( $line = <INPUT> ) {
  if( $line =~ /<INSERT HYPHEN STRING HERE>/ ) {
    print OUTPUT $insertString;
  } else {
    print OUTPUT $line;
  }
}

close INPUT;
close OUTPUT;
