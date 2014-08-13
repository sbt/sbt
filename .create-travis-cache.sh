#!/bin/bash

# very simple script to generate a tar of dependencies in ivy cache for extraction in TravisCI.

STAGING_DIR="$(pwd)/sbt-zipped-home-cache"
TAR_NAME=sbt-cache.tar.bz2


# Create a new staging directory to save things.
mkdir $STAGING_DIR
pushd $STAGING_DIR

# Clear previous files.
rm -rf $STAGING_DIR/*

# Copy all files over that we need to stage
mkdir .ivy2
mkdir .sbt
echo "Copying ivy2 cache ..."
cp -R ~/.ivy2/cache .ivy2/
echo "Copying sbt boot cache ..."
cp -R ~/.sbt/boot .sbt/


# Now generate the TAR
echo "Generating $TAR_NAME ..."
tar -cjvf $TAR_NAME .ivy2 .sbt >/dev/null 2>&1

#Now pop, we're done.
popd


