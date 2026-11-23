#!/usr/bin/env perl
use strict;
use warnings;
use IPC::Open3;
use Symbol qw(gensym);

# Secrets stay in process memory and apply_patch stdin; never print their values.
# Run from the repository root. Rotation invalidates existing JWTs and pending OTPs.
sub random_secret {
    open my $random, '-|', 'openssl', 'rand', '-hex', '48' or die "Cannot generate secret\n";
    my $value = <$random>;
    close $random or die "Secret generation failed\n";
    chomp $value;
    $value =~ /^[a-f0-9]{96}$/ or die "Invalid generated secret\n";
    return $value;
}

my %settings = (
    JWT_SECRET => random_secret(),
    OTP_HASH_SECRET => random_secret(),
    SPRING_PROFILES_ACTIVE => 'real-ai,real-knowledge',
);
my $patch = "*** Begin Patch\n";
for my $file ('.env', 'backend/.env') {
    -f $file or die "Expected local environment file: $file\n";
    open my $input, '<', $file or die "Cannot read $file\n";
    local $/;
    my $old = <$input>;
    close $input;
    my $new = $old;
    for my $key (sort keys %settings) {
        my $line = "$key=$settings{$key}";
        if ($new !~ s/^\Q$key\E=.*$/$line/mg) {
            $new .= "\n" unless $new =~ /\n\z/;
            $new .= "$line\n";
        }
    }
    $patch .= "*** Update File: $file\n@@\n";
    $patch .= join('', map { "-$_\n" } split /\n/, $old);
    $patch .= join('', map { "+$_\n" } split /\n/, $new);
}
$patch .= "*** End Patch\n";
my $error = gensym;
my $pid = open3(my $writer, my $reader, $error, 'apply_patch');
print {$writer} $patch;
close $writer;
my $output = do { local $/; <$reader> };
my $errors = do { local $/; <$error> };
waitpid($pid, 0);
$? == 0 or die "Environment update failed; no secret values printed\n";
(chmod 0600, '.env', 'backend/.env') == 2 or die "Could not restrict environment file permissions\n";
print "Updated both ignored environment files with matching independent secrets and real-service profiles.\n";
