```
$ sbt release
```

### Historical note

```
cd sbt-modules/util-take2
git filter-branch --index-filter 'git rm --cached -qr -- . && git reset -q $GIT_COMMIT -- build.sbt LICENSE NOTICE interface util/appmacro util/collection util/complete util/control util/log util/logic util/process util/relation cache' --prune-empty
git reset --hard
git gc --aggressive
git prune
```
