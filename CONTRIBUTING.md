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

