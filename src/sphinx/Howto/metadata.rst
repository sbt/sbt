================
Project metadata
================

A project should define :key:`name` and :key:`version`.  These will be used in various parts of the build, such as the names of generated artifacts.  Projects that are published to a repository should also override :key:`organization`.

.. howto::
   :id: name
   :type: setting
   :title: Set the project name

::

    name := "Your project name"

For published projects, this name is normalized to be suitable for use as an artifact name and dependency ID.  This normalized name is stored in :key:`normalizedName`.

.. howto::
   :id: version
   :type: setting
   :title: Set the project version

::

   version := "1.0-SNAPSHOT"

.. howto::
   :id: organization
   :type: setting
   :title: Set the project organization

::

   organization := "org.example"

By convention, this is a reverse domain name that you own, typically one specific to your project.  It is used as a namespace for projects.

A full/formal name can be defined in the :key:`organizationName` setting.  This is used in the generated pom.xml.  If the organization has a web site, it may be set in the :key:`organizationHomepage` setting.  For example:

::

    organizationName := "Example, Inc."

    organizationHomepage := Some(url("http://example.org"))

.. howto::
   :id: other
   :type: setting
   :title: Set the project's homepage and other metadata
   :reftext: set the project homepage and other metadata used in a published pom.xml

::

    homepage := Some(url("http://scala-sbt.org"))

    startYear := Some(2008)

    description := "A build tool for Scala."

    licenses += "GPLv2" -> url("http://www.gnu.org/licenses/gpl-2.0.html")

