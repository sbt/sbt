// with S selected, Q is loaded automatically, which in turn selects R
lazy val projA = project.addPlugins(S)

// S and T have direct conflicts of dependent plugins.
lazy val projB = project.addPlugins(S, T)

check := ()
