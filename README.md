librarymanagement module for sbt
================================

```
git clone --no-hardlinks --branch 0.13 sbt sbt-modules/librarymanagement
cd sbt-modules/librarymanagement
git filter-branch --index-filter 'git rm --cached -qr -- . && git reset -q $GIT_COMMIT -- ivy util/cross' --prune-empty
git reset --hard
git gc --aggressive
git prune
git cb 1.0
```
