=====
Setup
=====

Overview
========

To create an sbt project, you'll need to take these steps:

-  Install sbt and create a script to launch it.
-  Setup a simple :doc:`hello world <Hello>` project

   -  Create a project directory with source files in it.
   -  Create your build definition.

-  Move on to :doc:`running <Running>` to learn how to run
   sbt.
-  Then move on to :doc:`.sbt build definition <Basic-Def>`
   to learn more about build definitions.

Installing sbt
==============

You need two files: `sbt-launch.jar`_ and a script to run it.

*Note: Relevant information is moving to the `download
page <http://www.scala-sbt.org/download.html>`_*

Yum
---

The sbt package is available from the |typesafe-yum-repo|_. Please install `this rpm`_ to add
the typesafe yum repository to your list of approved sources. Then run:
``text yum install sbt`` to grab the latest release of sbt.

\*Note: please make sure to report any issues you may find to the |sbt-launcher-issues|_.

Apt
---

The sbt package is available from the |typesafe-debian-repo|_. Please install `this deb`_ to add the
typesafe debian repository to your list of approved sources. Then run:
``text apt-get install sbt`` to grab the latest release of sbt.

If sbt cannot be found, dont forget to update your list of repositories.
To do so, run: ``text apt-get update``

\*Note: please make sure to report any issues you may find to the |sbt-launcher-issues|_.

Gentoo
------

In official tree there is no ebuild for sbt. But there are ebuilds to
merge sbt from binaries:
https://github.com/whiter4bbit/overlays/tree/master/dev-java/sbt-bin. To
merge sbt from this ebuilds you can do next:

::

    mkdir -p /usr/local/portage && cd /usr/local/portage
    git clone git://github.com/whiter4bbit/overlays.git
    echo "PORTDIR_OVERLAY=$PORTDIR_OVERLAY /usr/local/portage/overlays" >> /etc/make.conf
    emerge sbt-bin

Mac
---

Use either `MacPorts <http://macports.org/>`_:
``text $ sudo port install sbt``

Or `HomeBrew <http://mxcl.github.com/homebrew/>`_:
``text $ brew install sbt``

There is no need to download the sbt-launch.jar separately with either
approach.

Windows
-------

You can download the `msi`_

*or*

Create a batch file ``sbt.bat``:

::

    set SCRIPT_DIR=%~dp0
    java -Xmx512M -jar "%SCRIPT_DIR%sbt-launch.jar" %*

and put `sbt-launch.jar`_ in the same directory as the batch file. Put ``sbt.bat`` on your path so
that you can launch ``sbt`` in any directory by typing ``sbt`` at the command prompt.

Unix
----

Download `sbt-launch.jar`_ and place it in ``~/bin``.

Create a script to run the jar, by placing this in a file called ``sbt``
in your ``~/bin`` directory:

::

    java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar `dirname $0`/sbt-launch.jar "$@"

Make the script executable:

::

    $ chmod u+x ~/bin/sbt

Tips and Notes
--------------

If you have any trouble running ``sbt``, see :doc:`/Detailed-Topics/Setup-Notes` on terminal
encodings, HTTP proxies, and JVM options.

To install sbt, you could also use this fairly elaborated shell script:
https://github.com/paulp/sbt-extras (see sbt file in the root dir). It
has the same purpose as the simple shell script above but it will
install sbt if necessary. It knows all recent versions of sbt and it
also comes with a lot of useful command line options.

Next
----

Move on to :doc:`create a simple project <Hello>`.


.. |sbt-launcher-issues| replace:: launcher package project
.. _sbt-launcher-issues: https://github.com/sbt/sbt-launcher-package/issues
.. |typesafe-yum-repo| replace:: Typesafe Yum Repository
.. _typesafe-yum-repo: http://rpm.typesafe.com
.. |typesafe-debian-repo| replace:: Typesafe Debian Repository
.. _typesafe-debian-repo: http://apt.typesafe.com
.. _this rpm: http://rpm.typesafe.com/typesafe-repo-2.0.0-1.noarch.rpm
.. _this deb: http://apt.typesafe.com/repo-deb-build-0002.deb

