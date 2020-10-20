# Copyright (c) 2004 Leland Stanford Junior University
#
# This program has been made available for research purposes only.
# Please do not further distribute it.
# Commercial development of the software is not to be undertaken without
# prior agreement from Stanford University.
# This program is not open source nor is it in the public domain.
#
# For information contact:
#    Christopher Manning
#    Dept of Computer Science, Gates 4A
#    Stanford CA 94305-9040
#    USA
#    manning@cs.stanford.edu

use strict;

#
# These properties don't depend on file locations and are always used
#
my $INVARIANT_PROPERTIES = <<END;
<INSERT PROPERTIES HERE>
END

my $TASK1_HOME = $ENV{TASK1_HOME} || ".";

my $classpath = "$TASK1_HOME/pascal.jar";

my $javacmd = $ENV{JAVA_HOME} ? "$ENV{JAVA_HOME}/bin/java" : "java";
my $JAVA = "$javacmd -server -mx1000m -classpath $classpath";

my $propfile = "$TASK1_HOME/properties";

my $properties = $INVARIANT_PROPERTIES;

my $outputFilename = "$TASK1_HOME/classifier_output";
my $errorFilename = "$TASK1_HOME/classifier_error";


#
# add the location of the serialized classifier to the properties
#
$properties .= "loadClassifier=$TASK1_HOME/serialized_classifier.gz\n";


#
# Read the test document filename from the command line
#
my $testfile = $ARGV[0];
unless( $testfile ){
    die "Must supply a test document on the command line\n";
}

unless( -r $testfile ) {
    die "Test document '$testfile' is not readable\n";
}

#
# extract features from test document
#
my $columnFile = "$testfile.columns";
doCommand("$JAVA edu.stanford.nlp.ie.pascal.PhaseOneFeatureGrabber " .
    "$testfile >$columnFile");
$properties .= "testFile=$columnFile\n";


#
# write out the properties file
#
open PROPFILE, ">$propfile" or die;
print PROPFILE $properties;
close PROPFILE;

system("perl ./stripanswers.pl < $columnFile > $columnFile.new");
system("mv $columnFile.new $columnFile");

#
# Run classifier
#
doCommand("$JAVA edu.stanford.nlp.ie.pascal.ClassifierWrapper " .
    "-prop $propfile > $outputFilename 2> $errorFilename");

#
# END
# 

sub doCommand {
    my $cmd = shift;
    print "$cmd\n";
    system($cmd);
    my $retval = $?>>8;
    if( $retval ) {
        die "Command failed with return value $retval\n";
    }
}
