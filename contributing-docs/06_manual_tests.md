Manual tests
============

Use the `publishLocalBin` command to publish the sbt project to your local machine. This is helpful for testing your changes.

```bash
$ sbt
> publishLocalBin
```

### Using the locally built sbt

The `publishLocalBin` command above will build and publish version `2.$MINOR.$PATCH-SNAPSHOT` (e.g. 1.1.2-SNAPSHOT) to your local Ivy repository.

To use the locally built sbt, set the version in `build.properties` file in your project to `2.$MINOR.$PATCH-SNAPSHOT` then launch `sbt` (this can be the `sbt` launcher installed in your machine).

```bash
$ cd $YOUR_OWN_PROJECT
$ sbt --server
> compile
```

### Clearing out boot and local cache

sbt consists of lots of JAR files. When running sbt locally, these JAR artifacts are cached in the `boot` directory under `$HOME/.sbt/boot/scala-3.8.2/org.scala-sbt/sbt/2.0.0-RC9-bin-SNAPSHOT` directory (The Scala version and sbt version part changes).

In order to see a change you've made to sbt's source code, this cache MUST be cleared. To clear this out, from the sbt shell in your application run:

```bash
> reboot dev
```

Alternatively from Bash:

```bash
$ rm -rf $HOME/.sbt/boot/scala-3.*
```

By default sbt uses a snapshot version (this is a scala convention for quick local changes- it tells users that this version could change).
One drawback of `-SNAPSHOT` version is that it's slow to resolve as it tries to hit all the resolvers.
This is important when testing performance, so that the slowness of the resolution does not impact sbt.

### Running sbt "from source" - `sbtOn`

In addition to locally publishing a build of sbt, there is an alternative, experimental launcher within sbt/sbt
to be able to run sbt "from source", that is to compile sbt and run it from its resulting classfiles rather than
from published jar files.

Such a launcher is available within sbt/sbt's build through a custom `sbtOn` command that takes as its first
argument the directory on which you want to run sbt, and the remaining arguments are passed _to_ that sbt
instance. For example:

I have setup a minimal sbt build in the directory `/s/t`, to run sbt on that directory I call:

```bash
> sbtOn /s/t
[info] Packaging /d/sbt/scripted/sbt/target/scala-2.12/scripted-sbt_2.12-1.2.0-SNAPSHOT.jar ...
[info] Done packaging.
[info] Running (fork) sbt.RunFromSourceMain /s/t
Listening for transport dt_socket at address: 5005
[info] Loading settings from idea.sbt,global-plugins.sbt ...
[info] Loading global plugins from /Users/dnw/.dotfiles/.sbt/1.0/plugins
[info] Loading project definition from /s/t/project
[info] Set current project to t (in build file:/s/t/)
[info] sbt server started at local:///Users/dnw/.sbt/1.0/server/ce9baa494c7598e4d59b/sock
> show baseDirectory
[info] /s/t
> exit
[info] shutting down sbt server
[success] Total time: 19 s, completed 25-Apr-2018 15:04:58
```

Please note that this alternative launcher does _not_ have feature parity with sbt/launcher. (Meta)
contributions welcome! :-D
