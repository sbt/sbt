# Setup Notes

Some notes on how to set up your `sbt` script.

## Do not put `sbt-launch.jar` on your classpath.

Do _not_ put `sbt-launch.jar` in your `$SCALA_HOME/lib` directory, your project's `lib` directory, or anywhere it will be put on a classpath. It isn't a library.

## Terminal encoding

The character encoding used by your terminal may differ from Java's default encoding for your platform.  In this case, you will need to add the option `-Dfile.encoding=<encoding>` in your `sbt` script to set the encoding, which might look like:

```text
java -Dfile.encoding=UTF8
```

## JVM heap, permgen, and stack sizes

If you find yourself running out of permgen space or your workstation is low
on memory, adjust the JVM configuration as you would for any application. For example
a common set of memory-related options is:

```text
java -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256m
```
## Boot directory

`sbt-launch.jar` is just a bootstrap; the actual meat of sbt, and the Scala
compiler and standard library, are downloaded to the shared directory `$HOME/.sbt/boot/`.

To change the location of this directory, set the `sbt.boot.directory` system property in your `sbt` script.  A relative path will be resolved against the current working directory, which can be useful if you want to avoid sharing the boot directory between projects.  For example, the following uses the pre-0.11 style of putting the boot directory in `project/boot/`:

```text
java -Dsbt.boot.directory=project/boot/
```

## HTTP Proxy

On Unix, sbt will pick up any HTTP proxy settings from the `http.proxy` environment variable. If you are behind a proxy requiring authentication, your `sbt` script must also pass flags to set the `http.proxyUser` and `http.proxyPassword` properties:

```text
java -Dhttp.proxyUser=username -Dhttp.proxyPassword=mypassword
```

On Windows, your script should set properties for proxy host, port, and if applicable, username and password:

```text
java -Dhttp.proxyHost=myproxy -Dhttp.proxyPort=8080 -Dhttp.proxyUser=username -Dhttp.proxyPassword=mypassword
```
