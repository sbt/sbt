Release notes
=============

Drafts live in `notes/<version>/`, named `<version>-<short-sha>.md` after the head commit.
Entries use the GitHub style, `- <title> by @<handle> in <PR URL>`, grouped in this order:

1. `## changes with compatibility implications`
2. `## Security fixes`
3. `## 🚀 updates` (new features and user-facing improvements)
4. `## 🐛 Bug fixes`
5. `## ⚡ Performance improvements`
6. `## 🌐 behind-the-scenes` (including Zinc, sbtn, Coursier, Scala, and other dependency bumps)

End with a `**Full Changelog**: https://github.com/sbt/sbt/compare/<from>...<to>` line.
