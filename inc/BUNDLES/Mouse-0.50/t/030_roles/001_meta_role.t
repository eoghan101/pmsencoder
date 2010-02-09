#!/usr/bin/perl

use strict;
use warnings;

use Test::More tests => 26;
use Test::Exception;

use Test::Mouse;
use Mouse::Meta::Role;
use lib 't/lib';
use MooseCompat;

{
    package FooRole;

    our $VERSION = '0.01';

    sub foo { 'FooRole::foo' }
}

my $foo_role = Mouse::Meta::Role->initialize('FooRole');
isa_ok($foo_role, 'Mouse::Meta::Role');
#isa_ok($foo_role, 'Class::MOP::Module');

is($foo_role->name, 'FooRole', '... got the right name of FooRole');
is($foo_role->version, '0.01', '... got the right version of FooRole');

# methods ...

ok($foo_role->has_method('foo'), '... FooRole has the foo method');
is($foo_role->get_method('foo')->body, \&FooRole::foo, '... FooRole got the foo method');

isa_ok($foo_role->get_method('foo'), 'Mouse::Meta::Role::Method');

is_deeply(
    [ $foo_role->get_method_list() ],
    [ 'foo' ],
    '... got the right method list');

# attributes ...

is_deeply(
    [ $foo_role->get_attribute_list() ],
    [],
    '... got the right attribute list');

ok(!$foo_role->has_attribute('bar'), '... FooRole does not have the bar attribute');

lives_ok {
    $foo_role->add_attribute('bar' => (is => 'rw', isa => 'Foo'));
} '... added the bar attribute okay';

is_deeply(
    [ $foo_role->get_attribute_list() ],
    [ 'bar' ],
    '... got the right attribute list');

ok($foo_role->has_attribute('bar'), '... FooRole does have the bar attribute');

{
    local $TODO = 'Mouse does not support role attributes';
    is_deeply(
        join('|', %{$foo_role->get_attribute('bar')}),
        join('|', %{+{ is => 'rw', isa => 'Foo' }}),
        '... got the correct description of the bar attribute');
}
lives_ok {
    $foo_role->add_attribute('baz' => (is => 'ro'));
} '... added the baz attribute okay';

is_deeply(
    [ sort $foo_role->get_attribute_list() ],
    [ 'bar', 'baz' ],
    '... got the right attribute list');

ok($foo_role->has_attribute('baz'), '... FooRole does have the baz attribute');

is_deeply(
    $foo_role->get_attribute('baz')->{is},
    'ro',
    '... got the correct description of the baz attribute');

lives_ok {
    $foo_role->remove_attribute('bar');
} '... removed the bar attribute okay';

is_deeply(
    [ $foo_role->get_attribute_list() ],
    [ 'baz' ],
    '... got the right attribute list');

ok(!$foo_role->has_attribute('bar'), '... FooRole does not have the bar attribute');
ok($foo_role->has_attribute('baz'), '... FooRole does still have the baz attribute');

# method modifiers

ok(!$foo_role->has_before_method_modifiers('boo'), '... no boo:before modifier');

my $method = sub { "FooRole::boo:before" };
lives_ok {
    $foo_role->add_before_method_modifier('boo' => $method);
} '... added a method modifier okay';

ok($foo_role->has_before_method_modifiers('boo'), '... now we have a boo:before modifier');
is(($foo_role->get_before_method_modifiers('boo'))[0], $method, '... got the right method back');

is_deeply(
    [ $foo_role->get_method_modifier_list('before') ],
    [ 'boo' ],
    '... got the right list of before method modifiers');
