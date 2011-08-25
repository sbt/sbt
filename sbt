#!/usr/bin/env bash
#
# A more capable sbt runner, coincidentally also called sbt.
# Author: Paul Phillips <paulp@typesafe.com>

set -e

jar_url () {
  local where=$1  # releases or snapshots
  local ver=$2
  
  echo "http://typesafe.artifactoryonline.com/typesafe/ivy-$where/org.scala-tools.sbt/sbt-launch/$ver/sbt-launch.jar"
}
jar_file () {
  echo "$script_dir/.lib/$1/sbt-launch.jar"
}

# todo - make this dynamic
sbt_release_version=0.10.1
sbt_release_url=$(jar_url releases $sbt_release_version)
sbt_release_jar=$(jar_file $sbt_release_version)
sbt_snapshot_version=0.11.0-20110825-052147
sbt_snapshot_url=$(jar_url snapshots $sbt_snapshot_version)
sbt_snapshot_jar=$(jar_file $sbt_snapshot_version)

# Falses made true via command line options
useSnapshot=0
createProject=0

set_sbt_jar () {
  if (( $useSnapshot )); then
    sbt_version=$sbt_snapshot_version
    sbt_url=$sbt_snapshot_url
  else
    sbt_version=$sbt_release_version
    sbt_url=$sbt_release_url
  fi

  sbt_jar="$script_dir/.lib/$sbt_version/sbt-launch.jar"
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
declare -r sbt_opts_filename=".sbtopts"
declare -r latest_28="2.8.2.RC1"
declare -r latest_29="2.9.1.RC4"
declare -r latest_210="2.10.0-SNAPSHOT"

usage () {
  cat <<EOM
Usage: $script_name [options]

  -help           prints this message
  -v | -verbose   this runner is chattier
  -debug          set sbt log level to debug
  -nocolors       disable ANSI color codes
  -create         start sbt even in a directory with no project
  -sbtjar <path>  location of sbt launcher (default: $(jar_file '<sbt version>'))
  -sbtdir <path>  location of global settings and plugins (default: ~/.sbt)
     -ivy <path>  local Ivy repository (default: ~/.ivy2)
  -shared <path>  shared sbt boot directory (default: none, no sharing)

  # setting scala and sbt versions
  -28           set scala version to $latest_28
  -29           set scala version to $latest_29
  -210          set scala version to $latest_210
  -local <path> set scala version to local installation at path
  -snapshot     use a snapshot of sbt (otherwise, latest released version)

  # jvm options and output control
  JAVA_OPTS     environment variable, if unset uses "$default_java_opts"
  SBT_OPTS      environment variable, if unset uses "$default_sbt_opts"
  .sbtopts      file in sbt root directory, if present contents passed to sbt
  -Dkey=val     pass -Dkey=val directly to the jvm
  -J-X          pass option -X directly to the jvm (-J is stripped)

In the case of duplicated or conflicting options, the order above
shows precedence: JAVA_OPTS lowest, command line options highest.
EOM
}

# pull -J and -D options to give to java.
declare -a args
declare -a java_args
declare -a sbt_commands
declare sbt_jar=
declare verbose=0

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
          -help) usage; exit 1 ;;

           -ivy) addJava "-Dsbt.ivy.home=$2"; shift 2 ;;
        -shared) addJava "-Dsbt.boot.directory=$2"; shift 2 ;;
        -sbtdir) addJava "-Dsbt.global.base=$2"; shift 2 ;;
      -snapshot) useSnapshot=1; shift ;;
      -nocolors) addJava "-Dsbt.log.noformat=true"; shift ;;
        -create) createProject=1; shift ;;

            -D*) addJava "$1"; shift ;;
            -J*) addJava "${1:2}"; shift ;;
            -28) addSbt "++ $latest_28"; shift ;;
            -29) addSbt "++ $latest_29"; shift ;;
           -210) addSbt "++ $latest_210"; shift ;;
         -debug) addSbt "set logLevel in Global := Level.Debug"; debug=1; shift ;;
         -local) addSbt "set scalaHome in ThisBuild := Some(file(\"$2\"))"; shift 2 ;;

    -v|-verbose)   verbose=1 ; shift ;;
        -sbtjar) sbt_jar="$2"; shift 2 ;;

              *) args=("${args[@]}" "$1") ; shift ;;
    esac
  done
}
 
# if .sbtopts exists, prepend its contents
[[ -f "$sbt_opts_filename" ]] && set -- $(cat $sbt_opts_filename) "${@}"

# no args - alert them there's stuff in here
[[ $# -gt 0 ]] || {
  echo "Starting $script_name: invoke with -help for other options"
}

# process the unified options
process_args "$@"

# reset "$@" to the residual args
set -- "${args[@]}"

# verify this is an sbt dir or -create was given
[[ -f "build.sbt" ]] || [[ -d "project" ]] || (( $createProject )) || {
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
  echo "Downloading sbt launcher $sbt_version, this should only take a moment..."

  mkdir -p $(dirname "$sbt_jar") &&   
    if which curl >/dev/null; then
      curl --silent "$sbt_url" --output "$sbt_jar"
    elif which wget >/dev/null; then
      wget --quiet "$sbt_url" > "$sbt_jar"
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
