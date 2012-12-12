#!/bin/bash -ex
VERSION=9.2.2

cd `dirname $0`

rm -rf target/pg

mkdir -p dist

DIST=postgresql-$VERSION.tar.bz2

[ ! -e "dist/$DIST" ] && wget -O dist/$DIST http://ftp.postgresql.org/pub/source/v$VERSION/$DIST
mkdir -p target/pg
tar xvj -C target/pg -f dist/$DIST
pushd target/pg/postgresql-$VERSION

for PATCH in ../../../patches/*.patch
do
    patch -p1 < $PATCH
done

LDFLAGS='-Wl,-rpath,\$${ORIGIN}/../lib/' \
./configure --disable-rpath --with-openssl --with-ossp-uuid --prefix=`pwd`/../dest/ && make world check install-world  || exit $?

popd

DEST=src/main/resources/postgresql-`uname -s`-`uname -m`.tbz

rm -rf $DEST
tar -cvj -C target/pg/dest -f $DEST .
