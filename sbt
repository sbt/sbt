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
    
    if [[ $versionString =~ ^[0-9]\.[0-9]\.[0-9]$ ]]; then
      echo "$versionString"
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

set_sbt_jar () {
  if [[ -n "$sbt_version" ]]; then
    sbt_url=$(jar_url releases $sbt_version)
  elif (( $sbt_snapshot )); then
    sbt_version=$sbt_snapshot_version
    sbt_url=$(jar_url snapshots $sbt_version)
  else
    sbt_version=$sbt_release_version
    sbt_url=$(jar_url releases $sbt_version)
  fi

  sbt_jar=$(jar_file $sbt_version)
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
  -debug            set sbt log level to debug
  -no-colors        disable ANSI color codes
  -sbt-create       start sbt even if location has no sbt project
  -sbt-dir  <path>  location of global settings and plugins (default: ~/.sbt)
  -sbt-boot <path>  shared sbt boot directory (default: none, no sharing)
  -ivy      <path>  local Ivy repository (default: ~/.ivy2)
  
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
  
  # java version (default: $(which java))
  -java-home <path>         use specified path as JAVA_HOME

  # jvm options and output control
  JAVA_OPTS     environment variable, if unset uses "$default_java_opts"
  SBT_OPTS      environment variable, if unset uses "$default_sbt_opts"
  .sbtopts      if this file is in the sbt root directory, its contents are arguments
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
    -v|-verbose) verbose=1 ; shift ;;

           -ivy) addJava "-Dsbt.ivy.home=$2"; shift 2 ;;
     -no-colors) addJava "-Dsbt.log.noformat=true"; shift ;;
      -sbt-boot) addJava "-Dsbt.boot.directory=$2"; shift 2 ;;
       -sbt-dir) addJava "-Dsbt.global.base=$2"; shift 2 ;;

    -sbt-create) sbt_create=1; shift ;;
  -sbt-snapshot) sbt_snapshot=1; shift ;;
       -sbt-jar) sbt_jar="$2"; shift 2 ;;
   -sbt-version) sbt_version="$2"; shift 2 ;;
 -scala-version) scala_version="$2"; shift 2 ;;
    -scala-home) addSbt "set scalaHome in ThisBuild := Some(file(\"$2\"))"; shift 2 ;;
     -java-home) java_cmd="$2/bin/java"; shift 2 ;;

            -D*) addJava "$1"; shift ;;
            -J*) addJava "${1:2}"; shift ;;
            -28) addSbt "++ $latest_28"; shift ;;
            -29) addSbt "++ $latest_29"; shift ;;
          -29rc) addSbt "++ $latest_29rc"; shift ;;
           -210) addSbt "++ $latest_210"; shift ;;
         -debug) addSbt "set logLevel in Global := Level.Debug"; debug=1; shift ;;

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
  echo "Downloading sbt launcher $sbt_version:"
  echo "  From  $sbt_url"
  echo "    To  $sbt_jar"

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
