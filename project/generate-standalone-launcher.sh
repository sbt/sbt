#!/bin/bash
set -e

cd "$(dirname "$0")/.."

if [ ! -d cli/target/pack/lib ]; then
  echo "Compiling coursier-cli..." 1>&2
  sbt cli/pack
fi

echo -n > coursier.pro

for i in cli/target/pack/lib/*.jar; do
  echo "-injars $i" >> coursier.pro
done

cat >> coursier.pro << EOF
-outjars coursier-standalone.jar
-libraryjars <java.home>/lib/rt.jar

-dontwarn

-keep class coursier.cli.Coursier {
  public static void main(java.lang.String[]);
}

-keep class coursier.cli.IsolatedClassLoader {
  public java.lang.String[] getIsolationTargets();
}
EOF

# -noverify added in launcher below because of errors like
# http://sourceforge.net/p/proguard/bugs/567/

# These options don't fix it:
# -dontshrink
# -dontoptimize
# -dontobfuscate

./coursier launch \
  net.sf.proguard:proguard-base:5.2.1 -M proguard.ProGuard -- -- \
    @coursier.pro
cat > coursier-standalone << EOF
#!/bin/sh
exec java -noverify -cp "\$0" coursier.cli.Coursier "\$@"
EOF
cat coursier-standalone.jar >> coursier-standalone
chmod +x coursier-standalone
rm -f coursier-standalone.jar
rm -f coursier.pro
