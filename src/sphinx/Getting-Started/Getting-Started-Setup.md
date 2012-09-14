[sbt-launch.jar]: http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.12.0/sbt-launch.jar

# Setup

[[Previous|Getting Started Welcome]] _Getting Started Guide page 2 of 14._ [[Next|Getting Started Hello]]

# Overview

To create an sbt project, you'll need to take these steps:

 - Install sbt and create a script to launch it.
 - Setup a simple [[hello world|Getting Started Hello]] project
    - Create a project directory with source files in it.
    - Create your build definition.
 - Move on to [[running|Getting Started Running]] to learn how to run sbt.
 - Then move on to [[.sbt build definition|Getting Started Basic Def]] to learn more about build definitions.

# Installing sbt

You need two files; [sbt-launch.jar] and a script to run it.

*Note: Relevant information is moving to the [download page](http://www.scala-sbt.org/download.html)*

## Yum

The sbt package is available from the [Typesafe Yum Repository](http://rpm.typesafe.com). Please install [this rpm](http://rpm.typesafe.com/typesafe-repo-2.0.0-1.noarch.rpm) to add the typesafe yum repository to your list of approved sources. Then run:
```text
yum install sbt
```
to grab the latest release of sbt.

*Note: please make sure to report any issues you may find [here](https://github.com/sbt/sbt-launcher-package/issues).

## Apt

The sbt package is available from the [Typesafe Debian Repository](http://apt.typesafe.com). Please install [this deb](http://apt.typesafe.com/repo-deb-build-0002.deb) to add the typesafe debian repository to your list of approved sources. Then run:
```text
apt-get install sbt
```
to grab the latest release of sbt.

If sbt cannot be found, dont forget to update your list of repositories. To do so, run:
```text
apt-get update
```

*Note: please make sure to report any issues you may find [here](https://github.com/sbt/sbt-launcher-package/issues).

## Gentoo

In official tree there is no ebuild for sbt. But there are ebuilds to merge sbt from binaries: https://github.com/whiter4bbit/overlays/tree/master/dev-java/sbt-bin. To merge sbt from this ebuilds you can do next:

```text
mkdir -p /usr/local/portage && cd /usr/local/portage
git clone git://github.com/whiter4bbit/overlays.git
echo "PORTDIR_OVERLAY=$PORTDIR_OVERLAY /usr/local/portage/overlay" >> /etc/make.conf
emerge sbt-bin
```

## Mac

Use either [MacPorts](http://macports.org/):
```text
$ sudo port install sbt
```

Or [HomeBrew](http://mxcl.github.com/homebrew/):
```text
$ brew install sbt
```

There is no need to download the sbt-launch.jar separately with either approach.

## Windows

You can download the [msi](http://scalasbt.artifactoryonline.com/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.12.0/sbt.msi) 

*or*

Create a batch file `sbt.bat`:

```text
set SCRIPT_DIR=%~dp0
java -Xmx512M -jar "%SCRIPT_DIR%sbt-launch.jar" %*
```

and put [sbt-launch.jar] in the same directory as the batch file.  Put `sbt.bat` on your path so that you can launch `sbt` in any directory by typing `sbt` at the command prompt.

## Unix

Download [sbt-launch.jar] and place it in `~/bin`.

Create a script to run the jar, by placing this in a file called `sbt` in your `~/bin` directory:

```text
java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar `dirname $0`/sbt-launch.jar "$@"
```

Make the script executable:

```text
$ chmod u+x ~/bin/sbt
```

## Tips and Notes

If you have any trouble running `sbt`, see [[Setup Notes]] on terminal encodings, HTTP proxies, and JVM options.

To install sbt, you could also use this fairly elaborated shell script: https://github.com/paulp/sbt-extras (see sbt file in the root dir). It has the same purpose as the simple shell script above but it will install sbt if necessary. It knows all recent versions of sbt and it also comes with a lot of useful command line options.

## Next

Move on to [[create a simple project|Getting Started Hello]].