cleanKeepGlobs in Compile +=
  ((classDirectory in Compile in compile).value / "X.class").toGlob
