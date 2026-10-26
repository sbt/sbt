Compile / compile := Def.uncached {
  Count.increment()
  // Trigger a new build by updating the last modified time
  (Compile / compile).value
}
