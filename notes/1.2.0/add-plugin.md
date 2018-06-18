Adds `--addPluginSbtFile=<file>` command, which adds the given .sbt file to the plugin build.
Using this mechanism editors or IDEs can start a build with required plugin.

```
$ cat /tmp/extra.sbt
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.7")

$ sbt --addPluginSbtFile=/tmp/extra.sbt
...
sbt:helloworld> plugins
In file:/xxxx/hellotest/
  ...
  sbtassembly.AssemblyPlugin: enabled in root
```
