#!/usr/bin/env bash

sbt -Dsbtio.path=../io -Dsbtlm.path=../librarymanagement -Dsbtzinc.path=../zinc "$@"
