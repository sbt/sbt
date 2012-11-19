=================
 Interactive mode
=================

By default, sbt's interactive mode is started when no commands are provided on the command line or when the ``shell`` command is invoked.

.. howto::
   :id: basic_completion
   :title: Use tab completion
   :type: command
   
   testOnly <TAB>

As the name suggests, tab completion is invoked by hitting the tab key.
Suggestions are provided that can complete the text entered to the left of the current cursor position.
Any part of the suggestion that is unambiguous is automatically appended to the current text.
Commands typically support tab completion for most of their syntax.

As an example, entering ``tes`` and hitting tab:

.. code-block:: console

    > tes<TAB>
 
results in sbt appending a ``t``:

.. code-block:: console

    > test

To get further completions, hit tab again:

.. code-block:: console

    > test<TAB>
    testFrameworks   testListeners    testLoader       testOnly         testOptions      test:

Now, there is more than one possibility for the next character, so sbt prints the available options.
We will select ``testOnly`` and get more suggestions by entering the rest of the command and hitting tab twice:

.. code-block:: console

    > testOnly<TAB><TAB>
    --                      sbt.DagSpecification    sbt.EmptyRelationTest   sbt.KeyTest             sbt.RelationTest        sbt.SettingsTest

The first tab inserts an unambiguous space and the second suggests names of tests to run.
The suggestion of `--` is for the separator between test names and options provided to the test framework.
The other suggestions are names of test classes for one of sbt's modules.
Test name suggestions require tests to be compiled first.
If tests have been added, renamed, or removed since the last test compilation, the completions will be out of date until another successful compile.


.. howto::
   :id: verbose_completion
   :title: Show more tab completion suggestions
   :type: text
   
   Press tab multiple times.

Some commands have different levels of completion.  Hitting tab multiple times increases the verbosity of completions.  (Presently, this feature is only used by the ``set`` command.)

.. howto::
   :id: show_keybindings
   :title: Show JLine keybindings
   :type: commands
   
   > consoleQuick
   scala> :keybindings

Both the Scala and sbt command prompts use JLine for interaction.  The Scala REPL contains a ``:keybindings`` command to show many of the keybindings used for JLine.  For sbt, this can be used by running one of the ``console`` commands (``console``, ``consoleQuick``, or ``consoleProject``) and then running ``:keybindings``.  For example:

.. code-block:: console

    > consoleProject
    [info] Starting scala interpreter...
    ...
    scala> :keybindings
    Reading jline properties for default key bindings.
    Accuracy not guaranteed: treat this as a guideline only.

    1 CTRL-A: move to the beginning of the line
    2 CTRL-B: move to the previous character
    ...


.. howto::
   :id: change_keybindings
   :title: Modify the default JLine keybindings

JLine, used by both Scala and sbt, uses a configuration file for many of its keybindings.
The location of this file can be changed with the system property ``jline.keybindings``.
The default keybindings file is included in the sbt launcher and may be used as a starting point for customization.


.. howto::
   :id: prompt
   :title: Configure the prompt string
   :type: setting
   
   shellPrompt := { (s: State) => System.getProperty("user.name") + "> " }

By default, sbt only displays `> ` to prompt for a command.
This can be changed through the ``shellPrompt`` setting, which has type ``State => String``.
:doc:`State </Extending/Build-State>` contains all state for sbt and thus provides access to all build information for use in the prompt string.

Examples:

::

    // set the prompt (for this build) to include the project id.
    shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }
    
    // set the prompt (for the current project) to include the username
    shellPrompt := { state => System.getProperty("user.name") + "> " }


.. howto::
   :id: history
   :title: Use history
   :type: command
   
   !

Interactive mode remembers history even if you exit sbt and restart it.
The simplest way to access history is to press the up arrow key to cycle
through previously entered commands.  Use ``Ctrl+r`` to incrementally
search history backwards.  The following commands are supported:

* ``!`` Show history command help.
* ``!!`` Execute the previous command again.
* ``!:`` Show all previous commands.
* ``!:n`` Show the last n commands.
* ``!n`` Execute the command with index ``n``, as shown by the ``!:`` command.
* ``!-n`` Execute the nth command before this one.
* ``!string`` Execute the most recent command starting with 'string'
* ``!?string`` Execute the most recent command containing 'string'

.. howto::
   :id: history_file
   :title: Change the location of the interactive history file
   :type: setting
   
   historyPath := Some( baseDirectory.value / ".history" )

By default, interactive history is stored in the ``target/`` directory for the current project (but is not removed by a ``clean``).
History is thus separate for each subproject.
The location can be changed with the ``historyPath`` setting, which has type ``Option[File]``.
For example, history can be stored in the root directory for the project instead of the output directory:

::

    historyPath := Some(baseDirectory.value / ".history")

The history path needs to be set for each project, since sbt will use the value of ``historyPath`` for the current project (as selected by the ``project`` command).


.. howto::
   :id: share_history
   :title: Use the same history for all projects
   :type: setting
   
   historyPath := Some( (target in LocalRootProject).value / ".history" )

The previous section describes how to configure the location of the history file.
This setting can be used to share the interactive history among all projects in a build instead of using a different history for each project.
The way this is done is to set ``historyPath`` to be the same file, such as a file in the root project's ``target/`` directory:

::

    historyPath :=
        Some( (target in LocalRootProject).value / ".history")

The ``in LocalRootProject`` part means to get the output directory for the root project for the build.

.. howto::
   :id: disable_history
   :title: Disable interactive history
   :type: setting
   
   historyPath := None

If, for whatever reason, you want to disable history, set ``historyPath`` to ``None`` in each project it should be disabled in:

    historyPath := None

.. howto::
   :id: pre_commands
   :title: Run commands before entering interactive mode
   :type: batch
   
   clean compile shell

Interactive mode is implemented by the ``shell`` command.
By default, the ``shell`` command is run if no commands are provided to sbt on the command line.
To run commands before entering interactive mode, specify them on the command line followed by ``shell``.
For example,

.. code-block:: console

    $ sbt clean compile shell

This runs ``clean`` and then ``compile`` before entering the interactive prompt.
If either ``clean`` or ``compile`` fails, sbt will exit without going to the prompt.
To enter the prompt whether or not these initial commands succeed, prepend `-shell`, which means to run ``shell`` if any command fails.
For example, 

.. code-block:: console

    $ sbt -shell clean compile shell
