### Bug fixes

### Improvements

Currently sbt output lacks column number information easily accessible for cli tools (for example emacs' sbt-mode have this [hack](https://github.com/ensime/emacs-sbt-mode/pull/74/files) to allow jump to proper place in a file when navigating from sbt output compilation error messages).

This change is adding column number just behind line number

```
[error] /Users/me/column/src/main/scala/Column.scala:5:31: type mismatch;
```

### Fixes with compatibility implications
