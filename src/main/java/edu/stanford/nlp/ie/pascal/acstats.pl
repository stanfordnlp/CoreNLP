use strict;

my @counts;

my @fields = ("workshopname", "workshopacronym", "conferencename",
    "conferenceacronym" );

foreach my $file ( @ARGV ) {
    my ($wsname, $wsac, $confname, $confac);

    open IN, "<$file" or die;

    my @idx;
    for( my $f = 0; $f < @fields; ++$f ) {
      $idx[$f] = 0;
    }
    my $line;
    while( $line = <IN> ) {
        for( my $f = 0; $f < @fields; ++$f) {
            if( $line =~ /$fields[$f]/ ) {
              $idx[$f] = 1;
            }
        }
    }

    $counts[makeIndex(\@idx)]++;
}

sub makeIndex {
  my $indexes = shift;

  my $index = 0;
  for( my $i = 0; $i < @$indexes; ++$i ) {
    $index *= 2;
    $index += $indexes->[$i];
  }
  return $index;
}

# smoothing and normalizing
my $total = 0;
for(my $i = 0; $i < @counts; ++$i) {
  $counts[$i]++;
  $total += $counts[$i];
}
for( my $i = 0; $i < @counts; ++$i ) {
  $counts[$i] /= $total;
}

print join(" ", @fields) . "\n";

print join("\n", @counts) . "\n";
