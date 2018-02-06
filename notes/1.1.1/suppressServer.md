### Improvements

This pull request implements a Boolean setting called `suppressServer`, whose default value is `false'.

If a build or plugin explicitly sets it to `true`, the sbt-1.x server will not start up
(exactly as if `sbt.server.autostart` were set to start).

Users may manually override this setting by running the `startServer` command at the interactive prompt.

### Motivation

Projects often encounter private information, such as deployment credentials, private keys, etc.
For such projects, it may be preferable to reduce the potential attack surface than to enjoy the
interoperability offered by sbt's server. Projects that wish to make this tradeoff can set `suppressServer`
to `true` in their build. Security-sensitive plugins can define this setting as well, modifying the
default behavior in favor of security.

(My own motivation is that I am working on a [plugin for developing Ethereum applications](https://github.com/swaldman/sbt-ethereum)
with scala and sbt. It must work with extremely sensitive private keys.)

---

See also a [recent conversation on Stack Exchange](https://stackoverflow.com/questions/48591179/can-one-disable-the-sbt-1-x-server/48593906#48593906).
