librarymanagement module for sbt
================================

```scala
scala> import java.io.File
import java.io.File

scala> import sbt.librarymanagement._, syntax._
import sbt.librarymanagement._
import syntax._

scala> val log = sbt.util.LogExchange.logger("test")
log: sbt.internal.util.ManagedLogger = sbt.internal.util.ManagedLogger@c439b0f

scala> val lm = {
         import sbt.librarymanagement.ivy._
         val ivyConfig = InlineIvyConfiguration().withLog(log)
         IvyLibraryManagement(ivyConfig)
       }
lm: sbt.librarymanagement.LibraryManagement = sbt.librarymanagement.ivy.IvyLibraryManagement@11c07acb

scala> val module = "commons-io" % "commons-io" % "2.5"
module: sbt.librarymanagement.ModuleID = commons-io:commons-io:2.5

scala> lm.retrieve(module, scalaModuleInfo = None, new File("target"), log)
res0: Either[sbt.librarymanagement.UnresolvedWarning,Vector[java.io.File]] = Right(Vector(target/jars/commons-io/commons-io/commons-io-2.5.jar, target/jars/commons-io/commons-io/commons-io-2.5.jar, target/jars/commons-io/commons-io/commons-io-2.5.jar))
```
