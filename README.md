
incrementalcompiler module for sbt
==================================

```
$ git filter-branch --index-filter 'git rm --cached -qr -- . && git reset -q $GIT_COMMIT -- compile interface util/classfile util/classpath util/datatype util/relation' --prune-empty
```
