#!/usr/bin/env bash
#
# A more capable sbt runner, coincidentally also called sbt.
# Author: Paul Phillips <paulp@typesafe.com>

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

# todo - make this dynamic
declare -r sbt_release_version=0.10.1
declare -r sbt_rc_version=0.11.0-RC0
declare -r sbt_snapshot_version=0.11.0-SNAPSHOT
declare -r sbt_snapshot_baseurl="http://typesafe.artifactoryonline.com/typesafe/ivy-snapshots/org.scala-tools.sbt/sbt-launch/"

declare -r default_java_opts="-Dfile.encoding=UTF8"
declare -r default_sbt_opts="-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xms1536m -Xmx1536m -Xss2m"
declare -r sbt_opts=".sbtopts"
declare -r latest_28="2.8.1"
declare -r latest_29="2.9.1"
declare -r latest_210="2.10.0-SNAPSHOT"

declare -r script_path=$(get_script_path "$BASH_SOURCE")
declare -r script_dir="$(dirname $script_path)"
declare -r script_name="$(basename $script_path)"

# A bunch of falses and empties as defaults.
declare sbt_jar=
declare sbt_create=
declare sbt_version=
declare scala_version=
declare sbt_snapshot=0
declare java_cmd=java
declare java_home=
unset verbose
unset debug

build_props_sbt () {
  if [[ -f project/build.properties ]]; then
    versionLine=$(grep ^sbt.version project/build.properties)
    versionString=${versionLine##sbt.version=}
    echo "$versionString"
  fi
}
build_props_scala () {
  if [[ -f project/build.properties ]]; then
    versionLine=$(grep ^build.scala.versions project/build.properties)
    versionString=${versionLine##build.scala.versions=}
    echo ${versionString%% .*}
  fi
}

execRunner () {
  # print the arguments one to a line, quoting any containing spaces
  [[ $verbose || $debug ]] && echo "# Executing command line:" && {
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
echoerr () {
  echo 1>&2 "$@"
}
dlog () {
  [[ $verbose || $debug ]] && echoerr "$@"
}

sbtjar_07_url () {
  echo "http://simple-build-tool.googlecode.com/files/sbt-launch-${1}.jar"
}
sbtjar_release_url () {
  echo "http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/$sbt_version/sbt-launch.jar"
}
sbtjar_snapshot_url () {
  local ver="$sbt_version"
  [[ "$sbt_version" == *-SNAPSHOT ]] && {
    ver=$(sbt_snapshot_actual_version)
    echoerr "sbt snapshot is $ver"
  }
  
  echo "${sbt_snapshot_baseurl}${ver}/sbt-launch.jar"
}
jar_url () {
  case $sbt_version in
      0.7.4*) sbtjar_07_url 0.7.4 ;;
      0.7.5*) sbtjar_07_url 0.7.5 ;;
      0.7.7*) sbtjar_07_url 0.7.7 ;;
       0.7.*) sbtjar_07_url 0.7.7 ;;
 *-SNAPSHOT*) sbtjar_snapshot_url ;;
       *-RC*) sbtjar_snapshot_url ;;
           *) sbtjar_release_url ;;
  esac
}

jar_file () {
  echo "$script_dir/.lib/$1/sbt-launch.jar"
}

sbt_artifactory_list () {
  local version=${sbt_version%-SNAPSHOT}
  
  curl -s --list-only "$sbt_snapshot_baseurl" | \
    grep -F $version | \
    grep -v -- '-RC' | \
    perl -e 'print reverse <>' | \
    perl -pe 's#^<a href="([^"/]+).*#$1#;'
}

# argument is e.g. 0.11.0-SNAPSHOT
# finds the actual version (with the build id) at artifactory
sbt_snapshot_actual_version () {
  for ver in $(sbt_artifactory_list); do
    local url="$sbt_snapshot_baseurl$ver/sbt-launch.jar"
    dlog "Testing $url"
    curl -s --head "$url" >/dev/null
    dlog "curl returned: $?"
    echo "$ver"
    return
  done
}

download_url () {
  local url="$1"
  local jar="$2"
  
  echo "Downloading sbt launcher $sbt_version:"
  echo "  From  $url"
  echo "    To  $jar"

  mkdir -p $(dirname "$jar") && {
    if which curl >/dev/null; then
      curl --silent "$url" --output "$jar"
    elif which wget >/dev/null; then
      wget --quiet "$url" > "$jar"
    fi
  } && [[ -f "$jar" ]]
}

acquire_sbt_jar () {
  if (( $sbt_snapshot )); then
    sbt_version=$sbt_snapshot_version
  elif [[ ! $sbt_version ]]; then
    sbt_version=$sbt_release_version
  fi
  
  sbt_url="$(jar_url)"
  sbt_jar="$(jar_file $sbt_version)"

  [[ -f "$sbt_jar" ]] || download_url "$sbt_url" "$sbt_jar"
}


usage () {
  cat <<EOM
Usage: $script_name [options]

  -h | -help        print this message
  -v | -verbose     this runner is chattier
  -d | -debug       set sbt log level to debug
  -no-colors        disable ANSI color codes
  -sbt-create       start sbt even if current directory contains no sbt project
  -sbt-dir  <path>  path to global settings/plugins directory (default: ~/.sbt)
  -sbt-boot <path>  path to shared boot directory (default: ~/.sbt/boot in 0.11 series)
  -ivy      <path>  path to local Ivy repository (default: ~/.ivy2)

  # sbt version (default: from project/build.properties if present, else latest release)
  -sbt-version  <version>   use the specified version of sbt
  -sbt-jar      <path>      use the specified jar as the sbt launcher
  -sbt-rc                   use an RC version of sbt
  -sbt-snapshot             use a snapshot version of sbt

  # scala version (default: latest release)
  -28                       use $latest_28
  -29                       use $latest_29
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

addJava () {
  java_args=( "${java_args[@]}" "$1" )
}
addSbt () {
  sbt_commands=( "${sbt_commands[@]}" "$1" )
}
addResidual () {
  residual_args=( "${residual_args[@]}" "$1" )
}

process_args ()
{
  while [[ $# -gt 0 ]]; do
    case "$1" in
       -h|-help) usage; exit 1 ;;
    -v|-verbose) verbose=1; shift ;;
      -d|-debug) debug=1; shift ;;
    # -u|-upgrade) addSbt 'set sbt.version 0.7.7' ; addSbt reload ; shift ;;

           -ivy) addJava "-Dsbt.ivy.home=$2"; shift 2 ;;
     -no-colors) addJava "-Dsbt.log.noformat=true"; shift ;;
      -sbt-boot) addJava "-Dsbt.boot.directory=$2"; shift 2 ;;
       -sbt-dir) addJava "-Dsbt.global.base=$2"; shift 2 ;;

    -sbt-create) sbt_create=true; shift ;;
        -sbt-rc) sbt_version=$sbt_rc_version; shift ;;
  -sbt-snapshot) sbt_version=$sbt_snapshot_version; shift ;;
       -sbt-jar) sbt_jar="$2"; shift 2 ;;
   -sbt-version) sbt_version="$2"; shift 2 ;;
 -scala-version) addSbt "++ $2"; shift 2 ;;
    -scala-home) addSbt "set scalaHome in ThisBuild := Some(file(\"$2\"))"; shift 2 ;;
     -java-home) java_cmd="$2/bin/java"; shift 2 ;;

            -D*) addJava "$1"; shift ;;
            -J*) addJava "${1:2}"; shift ;;
            -28) addSbt "++ $latest_28"; shift ;;
            -29) addSbt "++ $latest_29"; shift ;;
           -210) addSbt "++ $latest_210"; shift ;;

              *) addResidual "$1"; shift ;;
    esac
  done
  
  [[ $debug ]] && {
    case "$sbt_version" in
      0.7*) addSbt "debug" ;; 
         *) addSbt "set logLevel in Global := Level.Debug" ;;
    esac
  }
}

# if .sbtopts exists, prepend its contents to $@ so it can be processed by this runner
[[ -f "$sbt_opts" ]] && set -- $(cat $sbt_opts) "$@"

# process the combined args, then reset "$@" to the residuals
process_args "$@"
set -- "${residual_args[@]}"
argumentCount=$#

# figure out the version
[[ "$sbt_version" ]] || sbt_version=$(build_props_sbt)
[[ "$sbt_version" = *-SNAPSHOT* || "$sbt_version" = *-RC* ]] && sbt_snapshot=1
[[ -n "$sbt_version" ]] && echo "Detected sbt version $sbt_version"
[[ -n "$scala_version" ]] && echo "Detected scala version $scala_version"

# pull -J and -D options to give to java.
declare -a residual_args
declare -a java_args
declare -a sbt_commands

# no args - alert them there's stuff in here
(( $argumentCount > 0 )) || echo "Starting $script_name: invoke with -help for other options"

# verify this is an sbt dir or -create was given
[[ -f ./build.sbt || -d ./project || -n "$sbt_create" ]] || {
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
[[ -f "$sbt_jar" ]] || acquire_sbt_jar || {
  # still no jar? uh-oh.
  echo "Download failed. Obtain the jar manually and place it at $sbt_jar"
  exit 1
}

sbt-args () {
  # since sbt 0.7 doesn't understand iflast
  if (( "${#residual_args[@]}" == 0 )); then
    echo "shell"
  else
    echo "${residual_args[@]}"
  fi
}

# run sbt
execRunner "$java_cmd" \
  ${JAVA_OPTS:-$default_java_opts} \
  ${SBT_OPTS:-$default_sbt_opts} \
  ${java_args[@]} \
  -jar "$sbt_jar" \
  "${sbt_commands[@]}" \
  $(sbt-args)
