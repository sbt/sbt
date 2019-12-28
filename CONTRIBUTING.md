Steps to publish
================

```
$ sbt -Dsbt.build.version=1.0.3 -Dsbt.build.offline=true
> universal:publish
> debian:publish
> rpm:publish
> universal:bintrayReleaseAllStaged
> debian:releaseAllStaged
> rpm:releaseAllStaged
```

## Notes on batch

### Testing if a variable is blank

```
if not defined _JAVACMD set _JAVACMD=java
```

### Testing if an argument %0 is blank

```
if "%~0" == "" goto echolist_end
```

The above would work in case `%0` contains either double quote (`"`) or whitespace.
