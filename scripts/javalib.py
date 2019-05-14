"""
  Utility that lists all non-implementation specific classes in javalib.

  It must be run from the root of the Scala Native checkout.
"""

import subprocess,os

cwd = os.getcwd()

target = cwd + "/javalib/target/scala-2.11/classes/"

paths = subprocess.check_output(["find", target, "-name", "*.nir"])

classes = sorted(list(set(
    line.replace(target, "").replace(".nir", "").lstrip("/").rstrip("$").replace("/", ".")
    for line in paths.split("\n")
    if "$$anon" not in line and "java/" in line
)))

for cls in classes:
    print("* ``{}``".format(cls))
