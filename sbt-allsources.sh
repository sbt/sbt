#!/usr/bin/env bash

sbt -Dsbtio.path=../io -Dsbtzinc.path=../zinc "$@"
