#!/usr/bin/env bash
#
# A more capable sbt runner, coincidentally called sbt.
# Author: Paul Phillips <paulp@typesafe.com>

set -e

declare -r script_name="$(basename $BASH_SOURCE)"
declare -r sbt_jar=/soft/inst/sbt/xsbt-launch.jar
declare -r default_java_opts="-Dfile.encoding=UTF8"
declare -r default_sbt_opts="-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xmx2g -Xss2m"
declare -r latest_28="2.8.1"
declare -r latest_29="2.9.0-1"
declare -r latest_210="2.10.0-SNAPSHOT"

# pick up completion if present
[[ -f .sbt_completion.sh ]] && source .sbt_completion.sh

usage () {
  cat <<EOM
Usage: $script_name [options]

  -help      prints this message
  -nocolor   disable ANSI color codes
  -debug     set sbt log level to debug
  -28        set scala version to $latest_28
  -29        set scala version to $latest_29
  -210       set scala version to $latest_210
  -Dkey=val  pass -Dkey=val directly to the jvm
  -J-X       pass option -X directly to the jvm

  # Options which require a path:
  -ivy       local Ivy repository (default: ~/.ivy2)
  -sbtdir    directory containing global settings and plugins (default: ~/.sbt)
  -shared    shared sbt boot directory (default: nones, no sharing)
  -local     local scala installation to set as scala home

The contents of the following environment variables, if any, will be
passed to the jvm. Later variables take priority over earlier ones, and
command line options (-D/-J) take priority over all of them.

  JAVA_OPTS   # defaults: $default_java_opts
  SBT_OPTS    # defaults: $default_sbt_opts

If an environment variable is set, the defaults are not given.
EOM
}

# no args - alert them there's stuff in here
[[ $# -gt 0 ]] || {
  echo "Starting $script_name: invoke with -help for other options"
  # so it still starts if we injected any sbt commands
  set -- "shell"
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

while [ $# -gt 0 ]; do
  case "$1" in
        -help) usage; exit 1 ;;

         -ivy) addJava "-Dsbt.ivy.home=$2"; shift 2 ;;
      -shared) addJava "-Dsbt.boot.directory=$2"; shift 2 ;;
      -global) addJava "-Dsbt.global.base=$2"; shift 2 ;;
    -nocolors) addJava "-Dsbt.log.noformat=true"; shift ;;
          -28) addJava "-Dsbt.scala.version=$latest_28"; shift ;;
          -29) addJava "-Dsbt.scala.version=$latest_29"; shift ;;
         -210) addJava "-Dsbt.scala.version=$latest_210"; shift ;;

          -D*) addJava "$1"; shift ;;
          -J*) addJava "${1:2}"; shift ;;
       -debug) debug=1 ; addSbt "set logLevel := Level.Debug"; shift ;;
       -local) addSbt "set scalaHome := Some(file(\"/path/to/scala\"))"; shift ;;

            *) args=("${args[@]}" "$1") ; shift ;;
  esac
done

# reset "$@" to the residual args
set -- "${args[@]}"

execRunner () {
  (( debug )) && echo "[command line] $@"
  "$@"
}

# run sbt
execRunner java \
  ${JAVA_OPTS:-$default_java_opts} \
  ${SBT_OPTS:-$default_sbt_opts} \
  ${java_args[@]} \
  -jar "$sbt_jar" \
  "${sbt_commands[@]}" \
  "$@"
