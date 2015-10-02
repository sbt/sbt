#!/bin/bash

for t in `git tag`
do
  git tag -d $t
done

