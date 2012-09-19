===================
Triggered Execution
===================

You can make a command run when certain files change by prefixing the
command with ``~``. Monitoring is terminated when ``enter`` is pressed.
This triggered execution is configured by the ``watch`` setting, but
typically the basic settings ``watch-sources`` and ``poll-interval`` are
modified.

-  ``watch-sources`` defines the files for a single project that are
   monitored for changes. By default, a project watches resources and
   Scala and Java sources.
-  ``watch-transitive-sources`` then combines the ``watch-sources`` for
   the current project and all execution and classpath dependencies (see
   :doc:`Full Configuration </Getting-Started/Full-Def>` for details on inter-project dependencies).
-  ``poll-interval`` selects the interval between polling for changes in
   milliseconds. The default value is ``500 ms``.

Some example usages are described below.

Compile
=======

The original use-case was continuous compilation:

.. code-block:: console

    > ~ test:compile

    > ~ compile

Testing
=======

You can use the triggered execution feature to run any command or task.
One use is for test driven development, as suggested by Erick on the
mailing list.

The following will poll for changes to your source code (main or test)
and run ``test-only`` for the specified test.

.. code-block:: console

    > ~ test-only example.TestA

Running Multiple Commands
=========================

Occasionally, you may need to trigger the execution of multiple
commands. You can use semicolons to separate the commands to be
triggered.

The following will poll for source changes and run ``clean`` and
``test``.

.. code-block:: console

    > ~ ;clean ;test
