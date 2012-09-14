---
layout: howto
title: Running commands
sections:
 - id: batch
   name: pass arguments to a command or task in batch mode
   batch: 'clean "test-only org.example.Test" "run-main demo.Main a b c"'
 - id: multi
   name: provide multiple commands to run consecutively
   command: ';clean ;compile'
 - id: read
   name: read commands from a file
   command: '< /path/to/file'
 - id: alias
   name: define an alias for a command or task
   command: 'alias h=help'
 - id: eval
   name: quickly evaluate a Scala expression
   command: 'eval 2+2'
---

<h4 id="batch">Pass arguments to a command in batch mode</h4>

sbt interprets each command line argument provided to it as a command together with the command's arguments.
Therefore, to run a command that takes arguments in batch mode, quote the command and its arguments.
For example,

    $ sbt 'project X' clean '~ compile'

<h4 id="multi">Provide several commands to run consecutively </h4>

Multiple commands can be scheduled at once by prefixing each command with a semicolon.
This is useful for specifying multiple commands where a single command string is accepted.
For example, the syntax for triggered execution is `~ <command>`.
To have more than one command run for each triggering, use semicolons.
For example, the following runs `clean` and then `compile` each time a source file changes:

    > ~ ;clean;compile

<h4 id="read">Read commands from a file</h4>

The `<` command reads commands from the files provided to it as arguments.  Run `help <` at the sbt prompt for details.

<h4 id="alias">Set, unset, and show aliases for commands</h4>

The `alias` command defines, removes, and displays aliases for commands.  Run `help alias` at the sbt prompt for details.

Example usage:

    > alias a=about
    > alias
        a = about    
    > a
    [info] This is sbt ...
    > alias a=
    > alias
    > a
    [error] Not a valid command: a ...

<h4 id="eval">Quickly evaluate a Scala expression</h4>

The `eval` command compiles and runs the Scala expression passed to it as an argument.
The result is printed along with its type.
For example,

    > eval 2+2
    4: Int

Variables defined by an `eval` are not visible to subsequent `eval`s, although changes to system properties persist and affect the JVM that is running sbt.
Use the Scala REPL (`console` and related commands) for full support for evaluating Scala code interactively.
