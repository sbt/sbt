[@panaeon]: https://github.com/panaeon

[#2932]: https://github.com/sbt/sbt/issues/2939

### Bug fixes

- Make sure that when `sbt help <command>` is executed from the command line it behaves similar to `sbt "help <command>"`.
- Add basic example and link to gitter8 templates page to `sbt new` help message.

### Note:
Unfortunately `sbt help shell` will print default general help message and drop to the sbt shell instead of displaying sbt shell help. 
Also now `help` command will consume commands after it so `;help smth; other cmd` will not work.

