---
layout: howto
title: Project metadata
sections:
 - id: name
   name: set the project name
   setting: name := "demo"
 - id: version
   name: set the project version
   setting: version := "1.0"
 - id: organization
   name: set the project organization
   setting: organization := "org.example"
 - id: other
   name: set the project homepage and other metadata used in a published pom.xml
---

A project should define `name` and `version`.  These will be used in various parts of the build, such as the names of generated artifacts.  Projects that are published to a repository should also override `organization`.

<h4 id="name">Set the project name</h4>

{% highlight scala %}
name := "Your project name"
{% endhighlight %}

For published projects, this name is normalized to be suitable for use as an artifact name and dependency ID.  This normalized name is stored in `normalizedName`.

<h4 id="version">Set the project version</h4>

{% highlight scala %}
version := "1.0"
{% endhighlight %}

<h4 id="organization">Set the project organization</h4>

{% highlight scala %}
organization := "org.example"
{% endhighlight %}

By convention, this is a reverse domain name that you own, typically one specific to your project.  It is used as a namespace for projects.

A full/formal name can be defined in the `organizationName` setting.  This is used in the generated pom.xml.  If the organization has a web site, it may be set in the `organizationHomepage` setting.  For example:

{% highlight scala %}
organization := "Example, Inc."

organizationHomepage := "org.example"
{% endhighlight %}

<h4 id="other">Set the project's homepage and other metadata</h4>

{% highlight scala %}
homepage := Some(url("http://scala-sbt.org"))

startYear := Some(2008)

description := "A build tool for Scala."

licenses += "GPLv2" -> "http://www.gnu.org/licenses/gpl-2.0.html"
{% endhighlight %}
