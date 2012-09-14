Advanced Configurations Example
-------------------------------

This is an example :doc:`full build definition </Getting-Started/Full-Def>` that
demonstrates using Ivy configurations to group dependencies.

The ``utils`` module provides utilities for other modules. It uses Ivy
configurations to group dependencies so that a dependent project doesn't
have to pull in all dependencies if it only uses a subset of
functionality. This can be an alternative to having multiple utilities
modules (and consequently, multiple utilities jars).

In this example, consider a ``utils`` project that provides utilities
related to both Scalate and Saxon. It therefore needs both Scalate and
Saxon on the compilation classpath and a project that uses all of the
functionality of 'utils' will need these dependencies as well. However,
project ``a`` only needs the utilities related to Scalate, so it doesn't
need Saxon. By depending only on the ``scalate`` configuration of
``utils``, it only gets the Scalate-related dependencies.

::

       import sbt._
       import Keys._

    object B extends Build 
    {
       /********** Projects ************/

       // An example project that only uses the Scalate utilities.
       lazy val a = Project("a", file("a")) dependsOn(utils % "compile->scalate")

       // An example project that uses the Scalate and Saxon utilities.
       // For the configurations defined here, this is equivalent to doing dependsOn(utils),
       //  but if there were more configurations, it would select only the Scalate and Saxon
       //  dependencies.
       lazy val b = Project("b", file("b")) dependsOn(utils % "compile->scalate,saxon")

       // Defines the utilities project
       lazy val utils = Project("utils", file("utils")) settings(utilsSettings : _*)

       def utilsSettings: Seq[Setting[_]] =
            // Add the src/common/scala/ compilation configuration.
          inConfig(Common)(Defaults.configSettings) ++
            // Publish the common artifact
          addArtifact(artifact in (Common, packageBin), packageBin in Common) ++ Seq(
            // We want our Common sources to have access to all of the dependencies on the classpaths
            //   for compile and test, but when depended on, it should only require dependencies in 'common'
          classpathConfiguration in Common := CustomCompile,
            // Modify the default Ivy configurations.
            //   'overrideConfigs' ensures that Compile is replaced by CustomCompile
          ivyConfigurations ~= overrideConfigs(Scalate, Saxon, Common, CustomCompile),
            // Put all dependencies without an explicit configuration into Common (optional)
          defaultConfiguration := Some(Common),
            // Declare dependencies in the appropriate configurations
          libraryDependencies ++= Seq(
             "org.fusesource.scalate" % "scalate-core" % "1.5.0" % "scalate",
             "org.squeryl" %% "squeryl" % "0.9.4" % "scalate",
             "net.sf.saxon" % "saxon" % "8.7" % "saxon"
          )
       )

       /********* Configurations *******/

       lazy val Scalate = config("scalate") extend(Common) describedAs("Dependencies for using Scalate utilities.")
       lazy val Common = config("common") describedAs("Dependencies required in all configurations.")
       lazy val Saxon = config("saxon") extend(Common) describedAs("Dependencies for using Saxon utilities.")

         // Define a customized compile configuration that includes
         //   dependencies defined in our other custom configurations
       lazy val CustomCompile = config("compile") extend(Saxon, Common, Scalate)
    }

