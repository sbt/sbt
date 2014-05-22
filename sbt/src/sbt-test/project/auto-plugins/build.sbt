// disablePlugins(Q) will prevent R from being auto-added
lazy val projA = project.enablePlugins(A, B).disablePlugins(Q)

// without B, Q is not added
lazy val projB = project.enablePlugins(A)

// with both A and B, Q is selected, which in turn selects R, but not S
lazy val projC = project.enablePlugins(A, B)

// with no natures defined, nothing is auto-added
lazy val projD = project

// with S selected, Q is loaded automatically, which in turn selects R
lazy val projE = project.enablePlugins(S)

// with X enabled, TopA is loaded automatically
lazy val projF = project.enablePlugins(X)

// only TopB should be enabled
lazy val projG = project.enablePlugins(TopB)

// enables TopC, which declares topLevelKeyTest
lazy val projH = project.enablePlugins(TopC)

check := {
	val adel = (del in projA).?.value // should be None
	same(adel, None, "del in projA")
	val bdel = (del in projB).?.value // should be None
	same(bdel, None, "del in projB")	
	val ddel = (del in projD).?.value // should be None
	same(ddel, None, "del in projD")
//
	val buildValue = (demo in ThisBuild).value
	same(buildValue, "build 0", "demo in ThisBuild")
	val globalValue = (demo in Global).value
	same(globalValue, "global 0", "demo in Global")
	val projValue = (demo in projC).value
	same(projValue, "project projC Q R", "demo in projC")
	val qValue = (del in projC in q).value
	same(qValue, " Q R", "del in projC in q")
	val optInValue = (del in projE in q).value
	same(optInValue, " Q S R", "del in projE in q")
// tests for top level plugins
  val topLevelAValueF = (topLevelDemo in projF).value
  same(topLevelAValueF, "TopA: topLevelDemo project projF", "topLevelDemo in projF")
  val demoValueF = (demo in projF).value
  same(demoValueF, "TopA: demo project projF", "demo in projF")
  val topLevelBValueG = (topLevelDemo in projG).value
  same(topLevelBValueG, "TopB: topLevelDemo project projG", "topLevelDemo in projG")
  val gdel = (del in projG).?.value
  same(gdel, None, "del in projG")
}

keyTest := "foo"

topLevelKeyTest := "bar"

def same[T](actual: T, expected: T, label: String) {
	assert(actual == expected, s"Expected '$expected' for `$label`, got '$actual'")
}
