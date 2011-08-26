#!/usr/bin/env bash
#
# A more capable sbt runner, coincidentally also called sbt.
# Author: Paul Phillips <paulp@typesafe.com>

set -e

# todo - make this dynamic
sbt_release_version=0.10.1
sbt_snapshot_version=0.11.0-20110825-052147

# A bunch of falses and empties as defaults.
declare sbt_jar=
declare sbt_create=0
declare sbt_snapshot=0
declare sbt_version=$(
  if [[ -f project/build.properties ]]; then
    versionLine=$(grep ^sbt.version project/build.properties)
    versionString=${versionLine##sbt.version=}
    noRC=${versionString%.RC*}
    echo $noRC

    if [[ $noRC =~ ^[0-9]\.[0-9]\.[0-9]$ ]]; then
      echo "$noRC"
    fi
  fi
)

declare scala_version=
declare java_cmd=java
declare java_home=
declare verbose=0

jar_url () {
  local where=$1  # releases or snapshots
  local ver=$2

  if [[ $ver = 0.7* ]]; then
    echo "http://simple-build-tool.googlecode.com/files/sbt-launch-$ver.jar"
  else
    echo "http://typesafe.artifactoryonline.com/typesafe/ivy-$where/org.scala-tools.sbt/sbt-launch/$ver/sbt-launch.jar"
  fi
}
jar_file () {
  echo "$script_dir/.lib/$1/sbt-launch.jar"
}
jar_url_latest_snapshot () {
  # e.g. 0.11.0
  local version=${1%-SNAPSHOT}
  # trailing slash is important
  local base="http://typesafe.artifactoryonline.com/typesafe/ivy-snapshots/org.scala-tools.sbt/sbt-launch/"
  # e.g. 0.11.0-20110826-052141/
  local version1=$(curl -s --list-only "$base" | grep -F $version | tail -1 | perl -pe 's#^<a href="([^"/]+).*#$1#;')
  
  jar_url snapshots "$version1"
} 

set_sbt_jar () {
  if [[ -n "$sbt_version" ]]; then
    if [[ "$sbt_version" = *SNAPSHOT* ]]; then
      sbt_url=$(jar_url_latest_snapshot $sbt_version)
    else
      sbt_url=$(jar_url releases $sbt_version)
    fi
  elif (( $sbt_snapshot )); then
    sbt_version=$sbt_snapshot_version
    sbt_url=$(jar_url snapshots $sbt_version)
  else
    sbt_version=$sbt_release_version
    sbt_url=$(jar_url releases $sbt_version)
  fi

  sbt_jar=$(jar_file $sbt_version)
}
download_sbt_jar () {
  local url="$sbt_url"
  local jar="$sbt_jar"
  
  echo "Downloading sbt launcher $sbt_version:"
  echo "  From  $url"
  echo "    To  $jar"

  mkdir -p $(dirname "$jar") &&
    if which curl >/dev/null; then
      curl --silent "$url" --output "$jar"
    elif which wget >/dev/null; then
      wget --quiet "$url" > "$jar"
    fi
}

# this seems to cover the bases on OSX, and someone will
# have to tell me about the others.
get_script_path () {
  local path="$1"
  [[ -L "$path" ]] || { echo "$path" ; return; }

  local target=$(readlink "$path")
  if [[ "${target:0:1}" == "/" ]]; then
    echo "$target"
  else
    echo "$path/$target"
  fi
}

declare -r script_path=$(get_script_path "$BASH_SOURCE")
declare -r script_dir="$(dirname $script_path)"
declare -r script_name="$(basename $script_path)"
declare -r default_java_opts="-Dfile.encoding=UTF8"
declare -r default_sbt_opts="-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xmx2g -Xss2m"
declare -r sbt_opts=".sbtopts"
declare -r latest_28="2.8.1"
declare -r latest_29="2.9.0-1"
declare -r latest_29rc="2.9.1.RC4"
declare -r latest_210="2.10.0-SNAPSHOT"

usage () {
  cat <<EOM
Usage: $script_name [options]

  -h | -help        print this message
  -v | -verbose     this runner is chattier
  -d | -debug       set sbt log level to debug
  -no-colors        disable ANSI color codes
  -sbt-create       start sbt even if current directory contains no sbt project
  -sbt-dir  <path>  path to global settings/plugins directory (default: ~/.sbt)
  -sbt-boot <path>  path to shared boot directory (default: none, no sharing)
  -ivy      <path>  path to local Ivy repository (default: ~/.ivy2)

  # sbt version (default: from project/build.properties if there, else latest release)
  -sbt-version  <version>   use the specified version of sbt
  -sbt-jar      <path>      use the specified jar as the sbt launcher
  -sbt-snapshot             use a snapshot version of sbt

  # scala version (default: latest release)
  -28                       use $latest_28
  -29                       use $latest_29
  -29rc                     use $latest_29rc
  -210                      use $latest_210
  -scala-home <path>        use the scala build at the specified directory
  -scala-version <version>  use the specified version of scala

  # java version (default: java from PATH, currently $(java -version |& grep version))
  -java-home <path>         alternate JAVA_HOME

  # jvm options and output control
  JAVA_OPTS     environment variable, if unset uses "$default_java_opts"
  SBT_OPTS      environment variable, if unset uses "$default_sbt_opts"
  .sbtopts      if this file exists in the sbt root, it is prepended to the runner args
  -Dkey=val     pass -Dkey=val directly to the java runtime
  -J-X          pass option -X directly to the java runtime (-J is stripped)

In the case of duplicated or conflicting options, the order above
shows precedence: JAVA_OPTS lowest, command line options highest.
EOM
}

# pull -J and -D options to give to java.
declare -a args
declare -a java_args
declare -a sbt_commands

addJava () {
  java_args=("${java_args[@]}" "$1")
}
addSbt () {
  sbt_commands=("${sbt_commands[@]}" "$1")
}

process_args ()
{
  while [ $# -gt 0 ]; do
    case "$1" in
       -h|-help) usage; exit 1 ;;
    -v|-verbose) verbose=1; shift ;;
      -d|-debug) debug=1; addSbt "set logLevel in Global := Level.Debug"; shift ;;

           -ivy) addJava "-Dsbt.ivy.home=$2"; shift 2 ;;
     -no-colors) addJava "-Dsbt.log.noformat=true"; shift ;;
      -sbt-boot) addJava "-Dsbt.boot.directory=$2"; shift 2 ;;
       -sbt-dir) addJava "-Dsbt.global.base=$2"; shift 2 ;;

    -sbt-create) sbt_create=1; shift ;;
  -sbt-snapshot) sbt_snapshot=1; shift ;;
       -sbt-jar) sbt_jar="$2"; shift 2 ;;
   -sbt-version) sbt_version="$2"; shift 2 ;;
 -scala-version) addSbt "++ $2"; shift 2 ;;
    -scala-home) addSbt "set scalaHome in ThisBuild := Some(file(\"$2\"))"; shift 2 ;;
     -java-home) java_cmd="$2/bin/java"; shift 2 ;;

            -D*) addJava "$1"; shift ;;
            -J*) addJava "${1:2}"; shift ;;
            -28) addSbt "++ $latest_28"; shift ;;
            -29) addSbt "++ $latest_29"; shift ;;
          -29rc) addSbt "++ $latest_29rc"; shift ;;
           -210) addSbt "++ $latest_210"; shift ;;

              *) args=("${args[@]}" "$1") ; shift ;;
    esac
  done
}

# if .sbtopts exists, prepend its contents so it can be processed by this runner
[[ -f "$sbt_opts" ]] && set -- $(cat $sbt_opts) "${@}"

# no args - alert them there's stuff in here
[[ $# -gt 0 ]] || echo "Starting $script_name: invoke with -help for other options"

# process the combined args, then reset "$@" to the residuals
process_args "$@"
set -- "${args[@]}"

# verify this is an sbt dir or -create was given
[[ -f build.sbt ]] || [[ -d project ]] || (( $sbt_create )) || {
  cat <<EOM
$(pwd) doesn't appear to be an sbt project.
If you want to start sbt anyway, run:
  $0 -create

EOM
  exit 1
}

# pick up completion if present; todo
[[ -f .sbt_completion.sh ]] && source .sbt_completion.sh

# no jar? download it.
[[ -f "$sbt_jar" ]] || set_sbt_jar
[[ -f "$sbt_jar" ]] || {
  download_sbt_jar || {
    [[ "$sbt_version" = *.RC* ]] && {
     sbt_version="${sbt_version%.RC*}" &&
     set_sbt_jar &&
     download_sbt_jar
    }
  }
}

# still no jar? uh-oh.
[[ -f "$sbt_jar" ]] || {
  echo "Download failed. Obtain the jar manually and place it at $sbt_jar"
  exit 1
}

execRunner () {
  # print the arguments one to a line, quoting any containing spaces
  (( debug )) || (( verbose )) && echo "# Executing command line:" && {
    for arg; do
      if echo "$arg" | grep -q ' '; then
        printf "\"%s\"\n" "$arg"
      else
        printf "%s\n" "$arg"
      fi
    done
    echo ""
  }

  "$@"
}

# since sbt 0.7 doesn't understand iflast
iflast-shell () {
  if [[ $sbt_version = 0.7* ]]; then
    echo "shell"
  else
    echo "iflast shell"
  fi
}

# run sbt
execRunner "$java_cmd" \
  ${JAVA_OPTS:-$default_java_opts} \
  ${SBT_OPTS:-$default_sbt_opts} \
  ${java_args[@]} \
  -jar "$sbt_jar" \
  "${sbt_commands[@]}" \
  "$(iflast-shell)" \
  "$@"
