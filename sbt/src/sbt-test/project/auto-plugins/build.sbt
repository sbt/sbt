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

lazy val projF = project

// with X enabled, TopA is loaded automatically
lazy val projG = project.enablePlugins(X)

// only TopB should be enabled
lazy val projH = project.enablePlugins(TopB)

// enables TopC, which declares topLevelKeyTest
lazy val projI = project.enablePlugins(TopC)


// Tests that we can disable an auto-enabled root plugin
lazy val disableAutoNoRequirePlugin = project.disablePlugins(OrgPlugin)


disablePlugins(plugins.IvyPlugin)

check := {
    // Ensure organization on root is overridable.
    val rorg = (organization).value // Should be None
    same(rorg, "override", "organization")
    // this will pass when the raw disablePlugin works.
    val dversion = (projectID in projD).?.value // Should be None
    same(dversion, None, "projectID in projD")
    val rversion = projectID.?.value // Should be None
    same(rversion, None, "projectID")
//  Ensure with multiple .sbt files that disabling/enabling works across them
    val fDel = (del in Quux in projF).?.value
    same(fDel, Some(" Q"), "del in Quux in projF")
//
	val adel = (del in projA).?.value // should be None
	same(adel, None, "del in projA")
	val bdel = (del in projB).?.value // should be None
	same(bdel, None, "del in projB")
	val ddel = (del in projD).?.value // should be None
	same(ddel, None, "del in projD")
//
	val buildValue = (demo in ThisBuild).value
	same(buildValue, "build 1", "demo in ThisBuild") // this is temporary, should be 0 until # is fixed
	val globalValue = (demo in Global).value
	same(globalValue, "global 1", "demo in Global") // this is temporary, should be 0 until # is fixed
	val projValue = (demo in projC).?.value
	same(projValue, Some("project projC Q R"), "demo in projC")
	val qValue = (del in projC in Quux).?.value
	same(qValue, Some(" Q R"), "del in projC in Quux")
	val optInValue = (del in projE in Quux).value
	same(optInValue, " Q S R", "del in projE in Quux")
	val overrideOrgValue = (organization in projE).value
	same(overrideOrgValue, "S", "organization in projE")
// tests for top level plugins
  val topLevelAValueG = (topLevelDemo in projG).value
  same(topLevelAValueG, "TopA: topLevelDemo project projG", "topLevelDemo in projG")
  val demoValueG = (demo in projG).value
  same(demoValueG, "TopA: demo project projG", "demo in projG")
  val topLevelBValueH = (topLevelDemo in projH).value
  same(topLevelBValueH, "TopB: topLevelDemo project projH", "topLevelDemo in projH")
  val hdel = (del in projH).?.value
  same(hdel, None, "del in projH")
}

keyTest := "foo"

topLevelKeyTest := "bar"

def same[T](actual: T, expected: T, label: String): Unit = {
	assert(actual == expected, s"Expected '$expected' for `$label`, got '$actual'")
}
