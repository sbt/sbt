# Advanced Command Example

This is an advanced example showing some of the power of the new settings system.  It shows how to temporarily modify all declared dependencies in the build, regardless of where they are defined.  It directly operates on the final Seq[Setting[_]] produced from every setting involved in the build.

The modifications are applied by running _canonicalize_.  A _reload_ or using _set_ reverts the modifications, requiring _canonicalize_ to be run again.

This particular example shows how to transform all declared dependencies on ScalaCheck to use version 1.8.  As an exercise, you might try transforming other dependencies, the repositories used, or the scalac options used.  It is possible to add or remove settings as well.

This kind of transformation is possible directly on the settings of Project, but it would not include settings automatically added from plugins or build.sbt files.  What this example shows is doing it unconditionally on all settings in all projects in all builds, including external builds.

```scala
import sbt._
import Keys._

object Canon extends Plugin
{
      // Registers the canonicalize command in every project
   override def settings = Seq(commands += canonicalize)
      
      // Define the command.  This takes the existing settings (including any session settings)
      // and applies 'f' to each Setting[_]
   def canonicalize = Command.command("canonicalize") { (state: State) =>
      val extracted = Project.extract(state)
      import extracted._
      val transformed = session.mergeSettings map ( s => f(s) )
      val newStructure = Load.reapply(transformed, structure)
      Project.setProject(session, newStructure, state)
   }

      // Transforms a Setting[_].
   def f(s: Setting[_]): Setting[_] = s.key.key match {
      // transform all settings that modify libraryDependencies
      case Keys.libraryDependencies.key =>
         // hey scalac.  T == Seq[ModuleID]
         s.asInstanceOf[Setting[Seq[ModuleID]]].mapInit(mapLibraryDependencies)
      // preserve other settings
      case _ => s
   }
      // This must be idempotent because it gets applied after every transformation.
      // That is, if the user does:
      //  libraryDependencies += a
      //  libraryDependencies += b
      // then this method will be called for Seq(a) and Seq(a,b)
   def mapLibraryDependencies(key: ScopedKey[Seq[ModuleID]], value: Seq[ModuleID]): Seq[ModuleID] =
     value map mapSingle

     // This is the fundamental transformation.
     // Here we map all declared ScalaCheck dependencies to be version 1.8
   def mapSingle(module: ModuleID): ModuleID =
      if(module.name == "scalacheck") 
         module.copy(revision = "1.8") 
      else
         module
}
```