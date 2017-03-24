#!/usr/bin/env bash

sbt -Dsbtio.path=../io -Dsbtutil.path=../util -Dsbtlm.path=../librarymanagement -Dsbtzinc.path=../zinc "$@"
