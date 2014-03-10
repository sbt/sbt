// excludePlugins(C) will prevent C, and thus D, from being auto-added
lazy val a = project.addPlugins(A, B).disablePlugins(Q)

// without B, C is not added
lazy val b = project.addPlugins(A)

// with both A and B, C is selected, which in turn selects D
lazy val c = project.addPlugins(A, B)

// with no natures defined, nothing is auto-added
lazy val d = project


check := {
	val ddel = (del in d).?.value // should be None
	same(ddel, None, "del in d")
	val bdel = (del in b).?.value // should be None
	same(bdel, None, "del in b")
	val adel = (del in a).?.value // should be None
	same(adel, None, "del in a")
//
	val buildValue = (demo in ThisBuild).value
	same(buildValue, "build 0", "demo in ThisBuild")
	val globalValue = (demo in Global).value
	same(globalValue, "global 0", "demo in Global")
	val projValue = (demo in c).value
	same(projValue, "project c Q R", "demo in c")
	val qValue = (del in c in q).value
	same(qValue, " Q R", "del in c in q")
}

def same[T](actual: T, expected: T, label: String) {
	assert(actual == expected, s"Expected '$expected' for `$label`, got '$actual'")
}