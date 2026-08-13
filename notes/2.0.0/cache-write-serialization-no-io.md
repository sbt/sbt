### Cache-write serialization no longer performs file I/O

Serializing a task's result for the action cache re-read the size of every
referenced file at write time, so a file vanishing between the task completing
and the cache write (for example when two overlapping evaluations of the same
task race the jar-to-CAS-symlink swap) made an otherwise successful task's
cache write throw an intermittent `sjsonnew.SerializationException: error
while writing the field outputFiles`. Each stored output reference is now
materialized once, before its blob is stored, so the cache write succeeds even
if the file vanishes afterwards, and I/O errors on an output file surface
upfront at blob storage time rather than mid-serialization.

This addresses [#9349][i9349].

[i9349]: https://github.com/sbt/sbt/issues/9349
