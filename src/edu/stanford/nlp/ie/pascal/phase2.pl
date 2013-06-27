#!/usr/bin/perl

use strict;
use Errno qw(EEXIST);

my $java = $ENV{JAVA_CMD} || "java";

my %param;
$param{serializedClassifier} = shift @ARGV;
$param{dateModelParamFile} = shift @ARGV;
$param{inputFiles} = join(" ", @ARGV);
$param{numSamples} = 100;

unless( $param{serializedClassifier} && $param{dateModelParamFile} && @ARGV) {
    die "Insufficient args";
}

# make output directory
my $outdir = mkTimestampDir();
foreach my $idx(1,2) {
    $param{"phase${idx}OutFilename"} = "$outdir/phase$idx.out";
    $param{"phase${idx}MergedFilename"} = "$outdir/phase$idx.merged";
    $param{"phase${idx}ScoresFilename"} = "$outdir/phase$idx.scores";
}
$param{logFilename} = "$outdir/log";

# write settings to output directory
my $paramFilename = "$outdir/params";
open PARAM_FILE, ">$paramFilename" or die;
print PARAM_FILE map { "$_=$param{$_}\n" } sort keys %param;
close PARAM_FILE;

# run phase2
my $cmd = "$java edu.stanford.nlp.ie.pascal.Phase2 -prop $paramFilename " .
    join(" ", @ARGV) . " >& $param{logFilename}";
printAndDo($cmd);

# merge tags and compute scores
foreach my $idx (1,2) {
    $cmd = "perl /u/nlp/bin/mergetags.pl -nt 0 " .
        $param{"phase${idx}OutFilename"} . " > " .
        $param{"phase${idx}MergedFilename"};
    printAndDo($cmd);
    $cmd = "perl /u/nlp/bin/evalIOB.pl " . $param{"phase${idx}MergedFilename"}
        . " > " . $param{"phase${idx}ScoresFilename"};
    printAndDo($cmd);
}

sub mkTimestampDir {
    my $success;
    my $timestamp;

    do {
        my ($sec, $min, $hour, $mday, $month, $year) = localtime(time);
        $year += 1900;
        $month += 1;

        $timestamp = sprintf("%04d%02d%02d%02d%02d.%02d",
            $year, $month, $mday, $hour, $min, $sec);

        $success = mkdir $timestamp;
        if( !$success && $! != EEXIST ) {
            die "Unable to create directory $timestamp";
        }
    } until($success);

    return $timestamp;
}

sub printAndDo {
    my $cmd = shift;

    print "$cmd\n";
    system($cmd);
    my $rv = $?>>8;
    if( $rv ) {
        die "ERROR: '$cmd' failed with return value $rv\n";
    }
}
