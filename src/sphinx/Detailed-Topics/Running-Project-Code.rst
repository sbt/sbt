====================
Running Project Code
====================

The ``run`` and ``console`` actions provide a means for running user
code in the same virtual machine as sbt. This page describes the
problems with doing so, how sbt handles these problems, what types of
code can use this feature, and what types of code must use a :doc:`forked jvm <Forking>`.
Skip to User Code if you just want to see when you should use a :doc:`forked jvm <Forking>`.

Problems
========

System.exit
-----------

User code can call ``System.exit``, which normally shuts down the JVM.
Because the ``run`` and ``console`` actions run inside the same JVM as
sbt, this also ends the build and requires restarting sbt.

Threads
-------

User code can also start other threads. Threads can be left running
after the main method returns. In particular, creating a GUI creates
several threads, some of which may not terminate until the JVM
terminates. The program is not completed until either ``System.exit`` is
called or all non-daemon threads terminate.

Deserialization and class loading
---------------------------------

During deserialization, the wrong class loader might be used for various
complex reasons. This can happen in many scenarios, and running under
SBT is just one of them. This is discussed for instance in issues :issue:`163` and
:issue:`136`. The reason is
explained
`here <http://jira.codehaus.org/browse/GROOVY-1627?focusedCommentId=85900#comment-85900>`_.

sbt's Solutions
===============

System.exit
-----------

User code is run with a custom ``SecurityManager`` that throws a custom
``SecurityException`` when ``System.exit`` is called. This exception is
caught by sbt. sbt then disposes of all top-level windows, interrupts
(not stops) all user-created threads, and handles the exit code. If the
exit code is nonzero, ``run`` and ``console`` complete unsuccessfully.
If the exit code is zero, they complete normally.

Threads
-------

sbt makes a list of all threads running before executing user code.
After the user code returns, sbt can then determine the threads created
by the user code. For each user-created thread, sbt replaces the
uncaught exception handler with a custom one that handles the custom
``SecurityException`` thrown by calls to ``System.exit`` and delegates
to the original handler for everything else. sbt then waits for each
created thread to exit or for ``System.exit`` to be called. sbt handles
a call to ``System.exit`` as described above.

A user-created thread is one that is not in the ``system`` thread group
and is not an ``AWT`` implementation thread (e.g. ``AWT-XAWT``,
``AWT-Windows``). User-created threads include the ``AWT-EventQueue-*``
thread(s).

User Code
=========

Given the above, when can user code be run with the ``run`` and
``console`` actions?

The user code cannot rely on shutdown hooks and at least one of the
following situations must apply for user code to run in the same JVM:

1. User code creates no threads.
2. User code creates a GUI and no other threads.
3. The program ends when user-created threads terminate on their own.
4. ``System.exit`` is used to end the program and user-created threads
   terminate when interrupted.
5. No deserialization is done, or the deserialization code avoids
   ensures that the right class loader is used, as in
   https://github.com/NetLogo/NetLogo/blob/master/src/main/org/nlogo/util/ClassLoaderObjectInputStream.scala
   or
   https://github.com/scala/scala/blob/master/src/actors/scala/actors/remote/JavaSerializer.scala#L20.

The requirements on threading and shutdown hooks are required because
the JVM does not actually shut down. So, shutdown hooks cannot be run
and threads are not terminated unless they stop when interrupted. If
these requirements are not met, code must run in a :doc:`forked jvm <Forking>`.

The feature of allowing ``System.exit`` and multiple threads to be used
cannot completely emulate the situation of running in a separate JVM and
is intended for development. Program execution should be checked in a
:doc:`forked jvm <Forking>` when using multiple threads or ``System.exit``.
