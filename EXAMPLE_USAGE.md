/*
 * Example demonstrating the automated JsonFormat derivation solution
 * for issue #8288 - Automate derivation of JsonFormats where possible
 */

package examples

import sbt.util.CacheImplicits
import sbt.Def._
import xsbti.compile.CompileAnalysis

// Before sbt 2.0, this worked fine:
// 
// compile := {
//   // compile logic using CompileAnalysis
// }

// After sbt 2.0, this would fail with:
// "given evidence sjsonnew.JsonFormat[xsbti.compile.CompileAnalysis] is not found"

// Solution 1: Use Def.uncached() (loses caching benefits)
// compile := Def.uncached {
//   // compile logic
// }

// Solution 2: Manual JsonFormat (requires custom code for each type)
// implicit val compileAnalysisFormat: JsonFormat[CompileAnalysis] = new JsonFormat[CompileAnalysis] {
//   def write[J](obj: CompileAnalysis, builder: Builder[J]): Unit = ???
//   def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CompileAnalysis = ???
// }
// compile := {
//   // compile logic
// }

// Solution 3: Automated JsonFormat derivation (this PR)
import CacheImplicits._

// This now works automatically!
// The JsonFormat for CompileAnalysis is provided by AutoJsonFormats
compile := {
  // compile logic using CompileAnalysis
  // JsonFormat is automatically available, caching preserved
}

// For custom case classes:
case class MyPluginData(name: String, version: Int, enabled: Boolean)

// Automatic derivation works:
// implicit val myDataFormat: JsonFormat[MyPluginData] = AutoJsonFormat.caseClassFormat[MyPluginData](using classOf[MyPluginData])

myCustomTask := {
  val data = MyPluginData("my-plugin", 1, true)
  // This works with caching preserved
  data
}
