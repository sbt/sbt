---
layout: howto
title: Triggered execution
sections:
 - id: basic
   name: run a command when sources change
   command: '~ test'
 - id: multi
   name: run multiple commands when sources change
   command: '~ ;a ;b'
 - id: sources
   name: configure the sources that are checked for changes
   setting: 'watchSources <+= baseDirectory { _ / "examples.txt" }'
 - id: interval
   name: set the time interval between checks for changes to sources
   setting: 'pollInterval := 1000 // in ms'
---

<h4 id="basic">Run a command when sources change</h4>

You can make a command run when certain files change by prefixing the command with `~`.  Monitoring is terminated when `enter` is pressed.  This triggered execution is configured by the `watch` setting, but typically the basic settings `watch-sources` and `poll-interval` are modified as described in later sections.

The original use-case for triggered execution was continuous compilation:

{% highlight scala %}
> ~ test:compile

> ~ compile
{% endhighlight %}

You can use the triggered execution feature to run any command or task, however.  The following will poll for changes to your source code (main or test) and run `test-only` for the specified test.

{% highlight scala %}
> ~ test-only example.TestA
{% endhighlight %}

<h4 id="multi">Run multiple commands when sources change</h4>

The command passed to `~` may be any command string, so multiple commands may be run by separating them with a semicolon.  For example,

{% highlight scala %}
> ~ ;a ;b
{% endhighlight %}

This runs `a` and then `b` when sources change.

<h4 id="sources">Configure the sources checked for changes</h4>

* `watchSources` defines the files for a single project that are monitored for changes.  By default, a project watches resources and Scala and Java sources.
* `watchTransitiveSources` then combines the `watchSources` for the current project and all execution and classpath dependencies (see [Full Configuration] for details on inter-project dependencies).

To add the file `demo/example.txt` to the files to watch,

{% highlight scala %}
watchSources <+= baseDirectory { _ / "demo" / "examples.txt" }
{% endhighlight %}

<h4 id="interval">Configure the polling time</h4>

`pollInterval` selects the interval between polling for changes in milliseconds.  The default value is `500 ms`.  To change it to `1 s`,

{% highlight scala %}
pollInterval := 1000 // in ms
{% endhighlight %}
