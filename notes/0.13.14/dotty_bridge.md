### Improvements

- When sbt detects that the project is compiled with dotty, it now automatically
  set `scalaCompilerBridgeSource` correctly, this reduces the boilerplate needed
  to make a dotty project. Note that dotty support in sbt is still considered
  experimental and not officially supported, see [dotty.epfl.ch][dotty] for
  more information. [#2902][2902] by [@smarter][@smarter]

  [dotty]: http://dotty.epfl.ch/
  [2902]: https://github.com/sbt/sbt/pull/2902
  [@smarter]: https://github.com/smarter
