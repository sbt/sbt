### Improvements

This pull request implements a Boolean setting called `autoStartServer`, whose default value is `true'.

If a build or plugin explicitly sets it to `false`, the sbt-1.x server will not start up
(exactly as if the system property `sbt.server.autostart` were set to `false`).

Users who set `autoStartServer` to `false` may manually execute `startServer` at the interactive prompt,
if they wish to use the server during a shell session.

### Motivation

Projects often encounter private information, such as deployment credentials, private keys, etc.
For such projects, it may be preferable to reduce the potential attack surface than to enjoy the
interoperability offered by sbt's server. Projects that wish to make this tradeoff can set `autoStartServer`
to `false` in their build. Security-sensitive plugins can disable `autoStartServer` as well, modifying the
default behavior in favor of security.

(My own motivation is that I am working on a [plugin for developing Ethereum applications](https://github.com/swaldman/sbt-ethereum)
with scala and sbt. It must work with extremely sensitive private keys.)

---

See also a [recent conversation on Stack Exchange](https://stackoverflow.com/questions/48591179/can-one-disable-the-sbt-1-x-server/48593906#48593906).

---

##### History

2018-02-06 Modified from negative `suppressServer` to positive `autoStartServer` at the (sensible) request of @eed3si9n

