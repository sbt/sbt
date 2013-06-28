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

-  Move on to :doc:`running <Running>` to learn how to run sbt.
-  Then move on to :doc:`.sbt build definition <Basic-Def>`
   to learn more about build definitions.

Installing sbt
==============

sbt provides several packages for different operating systems or you can do `Manual Installation`_.

Officially supported packages:
  - MSI_ for Windows
  - ZIP_ or TGZ_ packages
  - RPM_ package
  - DEB_ package

.. note::

    Please report any issues with these to the `sbt-launcher-package`_ project.

**Third-party packages**:
  - :ref:`Homebrew <homebrew_setup>` or :ref:`Macports <macports_setup>` for `Mac`_
  - `Gentoo`_ emerge overlays

.. note::

   Third-party packages may not provide the latest version.
   Please make sure to report any issues with these packages to the relevant maintainers.

Mac
---

.. _macports_setup:

`Macports <http://macports.org/>`_
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: console

    $ port install sbt

.. _homebrew_setup:

`Homebrew <http://mxcl.github.com/homebrew/>`_
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: console

    $ brew install sbt

Gentoo
------

In the official tree there is no ebuild for sbt. But there are ebuilds to
merge sbt from binaries:
https://github.com/whiter4bbit/overlays/tree/master/dev-java/sbt-bin. To
merge sbt from this ebuilds you can do:

.. code-block:: console

    $ mkdir -p /usr/local/portage && cd /usr/local/portage
    $ git clone git://github.com/whiter4bbit/overlays.git
    $ echo "PORTDIR_OVERLAY=$PORTDIR_OVERLAY /usr/local/portage/overlays" >> /etc/make.conf
    $ emerge sbt-bin

.. note::

   Please report any issues with the ebuild `here <https://github.com/whiter4bbit/overlays/issues>`_.

.. _manual installation:

Manual Installation
-------------------


Unix
~~~~

Put sbt-launch.jar in ``~/bin``.

Create a script to run the jar, by creating ``~/bin/sbt`` with these contents:

.. code-block:: console

    $ SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
    $ java $SBT_OPTS -jar `dirname $0`/sbt-launch.jar "$@"

Make the script executable:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

Windows
~~~~~~~

Manual installation for Windows varies by terminal type and whether Cygwin is used.
In all cases, put the batch file or script on the path so that you can launch ``sbt``
in any directory by typing ``sbt`` at the command prompt.  Also, adjust JVM settings
according to your machine if necessary.

For **non-Cygwin users using the standard Windows terminal**, create a batch file ``sbt.bat``:

.. code-block:: console

    $ set SCRIPT_DIR=%~dp0
    $ java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -jar "%SCRIPT_DIR%sbt-launch.jar" %*

and put sbt-launch.jar in the same directory as the batch file.

If using **Cygwin with the standard Windows terminal**, create a bash script ``~/bin/sbt``: 

.. code-block:: console

    $ SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
    $ java $SBT_OPTS -jar sbt-launch.jar "$@"

Replace ``sbt-launch.jar`` with your path the the launcher jar and remember to use ``cygpath`` if necessary.
Make the script executable:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

If using **Cygwin with an Ansi terminal** (supports Ansi escape sequences and is configurable via ``stty``), create a bash script ``~/bin/sbt``:

.. code-block:: console

    $ SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
    $ stty -icanon min 1 -echo > /dev/null 2>&1
    $ java -Djline.terminal=jline.UnixTerminal -Dsbt.cygwin=true $SBT_OPTS -jar sbt-launch.jar "$@"
    $ stty icanon echo > /dev/null 2>&1

Replace ``sbt-launch.jar`` with your path the the launcher jar and remember to use ``cygpath`` if necessary.
Then, make the script executable:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

.. note::

    Other configurations are currently unsupported.
    Please feel free to `submit a pull request <https://github.com/sbt/sbt/blob/0.13/CONTRIBUTING.md>`_ implementing or describing that support.

Tips and Notes
==============

If you have any trouble running sbt, see :doc:`/Detailed-Topics/Setup-Notes` on terminal
encodings, HTTP proxies, and JVM options.

Next
====

Move on to :doc:`create a simple project <Hello>`.

