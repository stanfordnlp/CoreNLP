while($line = <STDIN>) {
    chomp($line);
    @fields = split(/\s+/, $line);
    next unless(@fields == 7);
    print join(" ", @fields[0..5]) . "\n";
}
