### sbt no longer refuses to start when the boot socket cannot be created

Failures to create the boot-time io socket used to be misreported: most were
wrapped as "sbt thinks that server is already booting" with a stack trace, and
non-interactive invocations exited with code 2, while failures to create the
socket directory crashed startup outright. Permission or path-length problems
with `XDG_RUNTIME_DIR` or the temp directory and Windows named-pipe access
errors all hit one of these ([#6777][6777]).

sbt now probes the socket first. Only a live server answering the probe is
treated as another sbt booting in the build (the interactive prompt and
non-interactive exit are unchanged, with a clearer message). Any other failure
is no longer fatal: sbt continues without the boot socket, whose only job is
forwarding boot-time io to early-connecting clients.

  [6777]: https://github.com/sbt/sbt/issues/6777
