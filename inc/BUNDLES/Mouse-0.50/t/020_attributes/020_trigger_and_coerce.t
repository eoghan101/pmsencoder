#!/usr/bin/perl

use strict;
use warnings;

use Test::More tests => 11;
use Test::Exception;



{

    package Fake::DateTime;
    use Mouse;

    has 'string_repr' => ( is => 'ro' );

    package Mortgage;
    use Mouse;
    use Mouse::Util::TypeConstraints;

    coerce 'Fake::DateTime' => from 'Str' =>
        via { Fake::DateTime->new( string_repr => $_ ) };

    has 'closing_date' => (
        is      => 'rw',
        isa     => 'Fake::DateTime',
        coerce  => 1,
        trigger => sub {
            my ( $self, $val ) = @_;
            ::pass('... trigger is being called');
            ::isa_ok( $self->closing_date, 'Fake::DateTime' );
            ::isa_ok( $val,                'Fake::DateTime' );
        }
    );
}

{
    my $mtg = Mortgage->new( closing_date => 'yesterday' );
    isa_ok( $mtg, 'Mortgage' );

    # check that coercion worked
    isa_ok( $mtg->closing_date, 'Fake::DateTime' );
}

Mortgage->meta->make_immutable;
ok( Mortgage->meta->is_immutable, '... Mortgage is now immutable' );

{
    my $mtg = Mortgage->new( closing_date => 'yesterday' );
    isa_ok( $mtg, 'Mortgage' );

    # check that coercion worked
    isa_ok( $mtg->closing_date, 'Fake::DateTime' );
}

