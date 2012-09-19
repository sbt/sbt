=====================
 Triggered execution
=====================

.. howto::
   :id: basic
   :title: Run a command when sources change
   :type: command
   
   ~ test

You can make a command run when certain files change by prefixing the command with ``~``.  Monitoring is terminated when ``enter`` is pressed.  This triggered execution is configured by the ``watch`` setting, but typically the basic settings ``watch-sources`` and ``poll-interval`` are modified as described in later sections.

The original use-case for triggered execution was continuous compilation:

::

    > ~ test:compile

    > ~ compile

You can use the triggered execution feature to run any command or task, however.  The following will poll for changes to your source code (main or test) and run ``test-only`` for the specified test.

::

    > ~ test-only example.TestA

.. howto::
   :id: multi
   :title: Run multiple commands when sources change
   :type: command
   
   ~ ;a ;b

The command passed to ``~`` may be any command string, so multiple commands may be run by separating them with a semicolon.  For example,

::

    > ~ ;a ;b

This runs ``a`` and then ``b`` when sources change.

.. howto::
   :id: sources
   :title: Configure the sources that are checked for changes
   :type: setting
   
   watchSources <+= baseDirectory { _ / "examples.txt" }

* ``watchSources`` defines the files for a single project that are monitored for changes.  By default, a project watches resources and Scala and Java sources.
* ``watchTransitiveSources`` then combines the ``watchSources`` for the current project and all execution and classpath dependencies (see :doc:`/Getting-Started/Full-Def` for details on inter-project dependencies).

To add the file ``demo/example.txt`` to the files to watch,

::

    watchSources <+= baseDirectory { _ / "demo" / "examples.txt" }

.. howto::
   :id: interval
   :title: Set the time interval between checks for changes to sources
   :type: setting
   
   pollInterval := 1000 // in ms

``pollInterval`` selects the interval between polling for changes in milliseconds.  The default value is ``500 ms``.  To change it to ``1 s``,

::

    pollInterval := 1000 // in ms
