Steps to publish
================

```
$ sbt -Dsbt.build.version=1.0.0-M1
> universal:publish
> debian:publish
> rpm:publish
> universal:bintrayReleaseAllStaged
> debian:releaseAllStaged
> rpm:releaseAllStaged
```

