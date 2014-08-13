#!/bin/bash

# WORKAROUND lack of caching in public repos:
# http://docs.travis-ci.com/user/caching/

curl -L https://www.dropbox.com/s/7v8qrukd6wmoydz/sbt-cache.tar.bz2 | tar xjf - -C $HOME

# always succeed: the cache is optional
exit 0
