#!/usr/bin/env bash
#
# A more capable sbt runner, coincidentally also called sbt.
# Author: Paul Phillips <paulp@typesafe.com>

set -e

# todo - make this dynamic
launch_base=http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch
launch_url=$launch_base/0.10.1/sbt-launch.jar

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
declare -r default_sbt_jar="$script_dir/.lib/sbt-launch.jar"
declare -r default_java_opts="-Dfile.encoding=UTF8"
declare -r default_sbt_opts="-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xmx2g -Xss2m"
declare -r latest_28="2.8.1"
declare -r latest_29="2.9.0-1"
declare -r latest_210="2.10.0-SNAPSHOT"

usage () {
  cat <<EOM
Usage: $script_name [options]

  -help           prints this message
  -nocolor        disable ANSI color codes
  -debug          set sbt log level to debug
  -v | -verbose   this runner is chattier
  -sbtjar <path>  location of sbt launcher (default: $default_sbt_jar)
  -sbtdir <path>  location of global settings and plugins (default: ~/.sbt)
     -ivy <path>  local Ivy repository (default: ~/.ivy2)
  -shared <path>  shared sbt boot directory (default: none, no sharing)

  # setting scala version
  -28           set scala version to $latest_28
  -29           set scala version to $latest_29
  -210          set scala version to $latest_210
  -local <path> set scala version to local installation at path

  # passing options to jvm
  JAVA_OPTS     environment variable  # default: "$default_java_opts"
  SBT_OPTS      environment variable  # default: "$default_sbt_opts"
  -Dkey=val     pass -Dkey=val directly to the jvm
  -J-X          pass option -X directly to the jvm (-J is stripped)

The defaults given for JAVA_OPTS and SBT_OPTS are only used if the
corresponding variable is unset. In the case of a duplicated option,
SBT_OPTS takes precedence over JAVA_OPTS, and command line options
take precedence over both.
EOM
}

# no args - alert them there's stuff in here
[[ $# -gt 0 ]] || {
  echo "Starting $script_name: invoke with -help for other options"
}

# pull -J and -D options to give to java.
declare -a args
declare -a java_args
declare -a sbt_commands
declare sbt_jar="$default_sbt_jar"
declare verbose=0

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
      -sbtdir) addJava "-Dsbt.global.base=$2"; shift 2 ;;
    -nocolors) addJava "-Dsbt.log.noformat=true"; shift ;;

          -D*) addJava "$1"; shift ;;
          -J*) addJava "${1:2}"; shift ;;
          -28) addSbt "++ $latest_28"; shift ;;
          -29) addSbt "++ $latest_29"; shift ;;
         -210) addSbt "++ $latest_210"; shift ;;
       -debug) addSbt "set logLevel in Global := Level.Debug"; debug=1; shift ;;
       -local) addSbt "set scalaHome := Some(file(\"$2\"))"; shift 2 ;;

  -v|-verbose)   verbose=1 ; shift ;;
      -sbtjar) sbt_jar="$2"; shift 2 ;;

            *) args=("${args[@]}" "$1") ; shift ;;
  esac
done

# reset "$@" to the residual args
set -- "${args[@]}"

# pick up completion if present; todo
[[ -f .sbt_completion.sh ]] && source .sbt_completion.sh

# no jar? download it.
[[ -f "$sbt_jar" ]] || {
  echo "Downloading sbt launcher, this should only take a moment..."

  if which curl >/dev/null; then
    curl "$launch_url" --output "$sbt_jar"
  elif which wget >/dev/null; then
    wget "$launch_url" > "$sbt_jar"
  fi
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

# run sbt
execRunner java \
  ${JAVA_OPTS:-$default_java_opts} \
  ${SBT_OPTS:-$default_sbt_opts} \
  ${java_args[@]} \
  -jar "$sbt_jar" \
  "${sbt_commands[@]}" \
  "iflast shell" \
  "$@"
