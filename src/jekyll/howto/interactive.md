---
layout: howto
title: Interactive mode
sections:
 - id: basic_completion
   name: use tab completion
   command: 'test-only <TAB>'
 - id: verbose_completion
   name: show more tab completion suggestions
   short: Press tab multiple times.
 - id: show_keybindings
   name: view basic JLine keybindings
   commands: |
     > console-quick
     scala> :keybindings
 - id: change_keybindings
   name: modify the default JLine keybindings
 - id: prompt
   name: configure the prompt string
   setting: 'shellPrompt := { (s: State) => System.getProperty("user.name") + "> " }'
 - id: history
   name: use history
   command: '!'
 - id: history_file
   name: change the location of the interactive history file
   setting: 'historyPath <<= baseDirectory(t => Some(t / ".history"))'
 - id: share_history
   name: share interactive history across projects
   setting: 'historyPath <<= (target in LocalRootProject) { t => Some(t / ".history") }'
 - id: disable_history
   name: disable interactive history
   setting: 'historyPath := None'
 - id: pre_commands
   name: start interactive mode after executing some commands first
   batch: clean compile shell
---

[State]: https://github.com/harrah/xsbt/wiki/Build-State

By default, sbt's interactive mode is started when no commands are provided on the command line or when the `shell` command is invoked.

<h4 id="basic_completion">Using tab completion</h4>

As the name suggests, tab completion is invoked by hitting the tab key.
Suggestions are provided that can complete the text entered to the left of the current cursor position.
Any part of the suggestion that is unambiguous is automatically appended to the current text.
Commands typically support tab completion for most of their syntax.

As an example, entering `tes` and hitting tab:

    > tes<TAB>
 
results in sbt appending a `t`:

    > test

To get further completions, hit tab again:

    > test<TAB>
    test-frameworks   test-listeners    test-loader       test-only         test-options      test:

Now, there is more than one possibility for the next character, so sbt prints the available options.
We will select `test-only` and get more suggestions by entering the rest of the command and hitting tab twice:

    > test-only<TAB><TAB>
    --                      sbt.DagSpecification    sbt.EmptyRelationTest   sbt.KeyTest             sbt.RelationTest        sbt.SettingsTest

The first tab inserts an unambiguous space and the second suggests names of tests to run.
The suggestion of `--` is for the separator between test names and options provided to the test framework.
The other suggestions are names of test classes for one of sbt's modules.
Test name suggestions require tests to be compiled first.
If tests have been added, renamed, or removed since the last test compilation, the completions will be out of date until another successful compile.

<h4 id="verbose_completion">Showing more completions</h4>

Some commands have different levels of completion.  Hitting tab multiple times increases the verbosity of completions.  (Presently, this feature is rarely used.)

<h4 id="show_keybindings">Showing JLine keybindings</h4>

Both the Scala and sbt command prompts use JLine for interaction.  The Scala REPL contains a `:keybindings` command to show many of the keybindings used for JLine.  For sbt, this can be used by running one of the `console` commands (`console`, `console-quick`, or `console-project`) and then running `:keybindings`.  For example:

    > console-project
    [info] Starting scala interpreter...
    ...
    scala> :keybindings
    Reading jline properties for default key bindings.
    Accuracy not guaranteed: treat this as a guideline only.

    1 CTRL-A: move to the beginning of the line
    2 CTRL-B: move to the previous character
    ...

<h4 id="change_keybindings">Changing JLine keybindings</h4>

JLine, used by both Scala and sbt, uses a configuration file for many of its keybindings.
The location of this file can be changed with the system property `jline.keybindings`.
The default keybindings file is included in the sbt launcher and may be used as a starting point for customization.

<h4 id="prompt">Configure the prompt string</h4>

By default, sbt only displays `> ` to prompt for a command.
This can be changed through the `shellPrompt` setting, which has type `State => String`.
[State] contains all state for sbt and thus provides access to all build information for use in the prompt string.

Examples:
{% highlight scala %}
// set the prompt (for this build) to include the project id.
shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }

// set the prompt (for the current project) to include the username
shellPrompt := { state => System.getProperty("user.name") + "> " }
{% endhighlight %}

<h4 id="history">Using history</h4>

Interactive mode remembers history even if you exit sbt and restart it.
The simplest way to access history is to press the up arrow key to cycle
through previously entered commands.  Use `Ctrl+r` to incrementally
search history backwards.  The following commands are supported:

* `!` Show history command help.
* `!!` Execute the previous command again.
* `!:` Show all previous commands.
* `!:n` Show the last n commands.
* `!n` Execute the command with index `n`, as shown by the `!:` command.
* `!-n` Execute the nth command before this one.
* `!string` Execute the most recent command starting with 'string'
* `!?string` Execute the most recent command containing 'string'

<h4 id="history_file">Changing the history file location</h4>

By default, interactive history is stored in the `target/` directory for the current project (but is not removed by a `clean`).
History is thus separate for each subproject.
The location can be changed with the `historyPath` setting, which has type `Option[File]`.
For example, history can be stored in the root directory for the project instead of the output directory:

{% highlight scala %}
historyPath <<= baseDirectory(t => Some(t / ".history"))
{% endhighlight %}

The history path needs to be set for each project, since sbt will use the value of `historyPath` for the current project (as selected by the `project` command).

<h4 id="share_history">Use the same history for all projects</h4>

The previous section describes how to configure the location of the history file.
This setting can be used to share the interactive history among all projects in a build instead of using a different history for each project.
The way this is done is to set `historyPath` to be the same file, such as a file in the root project's `target/` directory:

{% highlight scala %}
historyPath <<=
  (target in LocalRootProject) { t =>
    Some(t / ".history")
  }
{% endhighlight %}

The `in LocalRootProject` part means to get the output directory for the root project for the build.

<h4 id="disable_history">Disable interactive history</h4>

If, for whatever reason, you want to disable history, set `historyPath` to `None` in each project it should be disabled in:

{% highlight scala %}
historyPath := None
{% endhighlight %}

<h4 id="pre_commands">Run commands before entering interactive mode</h4>

Interactive mode is implemented by the `shell` command.
By default, the `shell` command is run if no commands are provided to sbt on the command line.
To run commands before entering interactive mode, specify them on the command line followed by `shell`.
For example,

    $ sbt clean compile shell

This runs `clean` and then `compile` before entering the interactive prompt.
If either `clean` or `compile` fails, sbt will exit without going to the prompt.
To enter the prompt whether or not these initial commands succeed, prepend `-shell`, which means to run `shell` if any command fails.
For example, 

    $ sbt -shell clean compile shell