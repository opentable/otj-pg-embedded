#!/bin/bash -ex
VERSION=9.3.5-1

cd `dirname $0`

PACKDIR=$(mktemp -d -t wat.XXXXXX)
LINUX_DIST=dist/postgresql-$VERSION-linux-x64-binaries.tar.gz
OSX_DIST=dist/postgresql-$VERSION-osx-binaries.zip

mkdir -p dist/ target/generated-resources/
[ -e $LINUX_DIST ] || wget -O $LINUX_DIST "http://get.enterprisedb.com/postgresql/postgresql-$VERSION-linux-x64-binaries.tar.gz"
[ -e $OSX_DIST ] || wget -O $OSX_DIST "http://get.enterprisedb.com/postgresql/postgresql-$VERSION-osx-binaries.zip"

tar xzf $LINUX_DIST -C $PACKDIR
pushd $PACKDIR/pgsql
tar cjf $OLDPWD/target/generated-resources/postgresql-Linux-x86_64.tbz \
  share/postgresql \
  lib \
  bin/initdb \
  bin/pg_ctl \
  bin/postgres
popd

rm -fr $PACKDIR && mkdir -p $PACKDIR

unzip -q -d $PACKDIR $OSX_DIST
pushd $PACKDIR/pgsql
tar cjf $OLDPWD/target/generated-resources/postgresql-Darwin-x86_64.tbz \
  share/postgresql \
  lib/*.dylib \
  lib/postgresql/*.so \
  bin/initdb \
  bin/pg_ctl \
  bin/postgres
popd

rm -rf $PACKDIR
