==================
 Running commands
==================

.. howto::
   :id: batch
   :title: Pass arguments to a command or task in batch mode
   :type: batch
   
   clean "test-only org.example.Test" "run-main demo.Main a b c"

sbt interprets each command line argument provided to it as a command together with the command's arguments.
Therefore, to run a command that takes arguments in batch mode, quote the command and its arguments.
For example,

.. code-block:: console

    $ sbt 'project X' clean '~ compile'

.. howto::
   :id: multi
   :title: Provide multiple commands to run consecutively
   :type: command
   
   ;clean ;compile

Multiple commands can be scheduled at once by prefixing each command with a semicolon.
This is useful for specifying multiple commands where a single command string is accepted.
For example, the syntax for triggered execution is ``~ <command>``.
To have more than one command run for each triggering, use semicolons.
For example, the following runs ``clean`` and then ``compile`` each time a source file changes:

.. code-block:: console

    > ~ ;clean;compile

.. howto::
   :id: read
   :title: Read commands from a file
   :type: command
   
   < /path/to/file

The ``<`` command reads commands from the files provided to it as arguments.  Run ``help <`` at the sbt prompt for details.

.. howto::
   :id: alias
   :title: Define an alias for a command or task
   :type: command
   
   alias h=help

The ``alias`` command defines, removes, and displays aliases for commands.  Run ``help alias`` at the sbt prompt for details.

Example usage:

.. code-block:: console

    > alias a=about
    > alias
        a = about    
    > a
    [info] This is sbt ...
    > alias a=
    > alias
    > a
    [error] Not a valid command: a ...


.. howto::
   :id: eval
   :title: Quickly evaluate a Scala expression
   :type: command
   
   eval 2+2

The ``eval`` command compiles and runs the Scala expression passed to it as an argument.
The result is printed along with its type.
For example,


.. code-block:: console

    > eval 2+2
    4: Int

Variables defined by an ``eval`` are not visible to subsequent ``eval``s, although changes to system properties persist and affect the JVM that is running sbt.
Use the Scala REPL (``console`` and related commands) for full support for evaluating Scala code interactively.
