### Improvements

- Improves the new startup messages. See below.

### Bug fixes

- Fixes the new startup messages. See below.

### Improvements and bug fixes to the new startup messages

The two new startup messages introduced in sbt 0.13.15 are:

+ when writing out `sbt.version` for build reproducability, and
+ when informing the user sbt shell for the performance improvement

When writing out `sbt.version` the messaging now:

+ correctly uses a logger rather than println
+ honours the log level set, for instance, by `--error`
+ never runs when sbt "new" is being run

When informing the user about sbt shell the messaging now:

+ is a 1 line message, rather than 3
+ is at info level, rather than warn level
+ can be suppressed with `suppressSbtShellNotification := false`
+ only triggers when "compile" is being run
+ never shows when sbt "new" is being run

[#3091][]/[#3097][]/[#3147][] by [@dwijnand][]

[#3091]: https://github.com/sbt/sbt/issues/3091
[#3097]: https://github.com/sbt/sbt/issues/3097
[#3147]: https://github.com/sbt/sbt/pull/3147

[@dwijnand]: https://github.com/dwijnand
