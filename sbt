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

# a ham-fisted attempt to move some memory settings in concert
# so they need not be dicked around with individually.
get_mem_opts () {
  local mem=${1:-1536}
  local perm=$(( $mem / 4 ))
  (( $perm > 256 )) || perm=256
  (( $perm < 1024 )) || perm=1024
  local codecache=$(( $perm / 2 ))
  
  echo "-Xms${mem}m -Xmx${mem}m -XX:MaxPermSize=${perm}m -XX:ReservedCodeCacheSize=${codecache}m"
}

die() {
  echo "Aborting: $@"
  exit 1
}

# todo - make this dynamic
declare -r sbt_release_version=0.11.2
unset sbt_rc_version
# declare -r sbt_rc_version=
declare -r sbt_snapshot_version=0.11.3-SNAPSHOT
declare -r sbt_snapshot_baseurl="http://typesafe.artifactoryonline.com/typesafe/ivy-snapshots/org.scala-tools.sbt/sbt-launch/"

declare -r default_java_opts="-Dfile.encoding=UTF8"
declare -r default_sbt_opts="-XX:+CMSClassUnloadingEnabled"
declare -r default_sbt_mem=1536
declare -r noshare_opts="-Dsbt.global.base=project/.sbtboot -Dsbt.boot.directory=project/.boot -Dsbt.ivy.home=project/.ivy"
declare -r sbt_opts_file=".sbtopts"
declare -r latest_28="2.8.2"
declare -r latest_29="2.9.1"
declare -r latest_210="2.10.0-SNAPSHOT"

declare -r script_path=$(get_script_path "$BASH_SOURCE")
declare -r script_dir="$(dirname $script_path)"
declare -r script_name="$(basename $script_path)"

declare java_cmd=java
declare sbt_mem=$default_sbt_mem
declare java_opts="${JAVA_OPTS:-$default_java_opts}"

unset sbt_jar sbt_create sbt_version sbt_snapshot
unset scala_version
unset java_home
unset verbose debug

# pull -J and -D options to give to java.
declare -a residual_args
declare -a java_args
declare -a scalac_args
declare -a sbt_commands

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

isSnapshot () {
  [[ "$sbt_version" = *-SNAPSHOT* ]]
}
isRC () {
  [[ "$sbt_version" = *-RC* ]]
}

execRunner () {
  # print the arguments one to a line, quoting any containing spaces
  [[ $verbose || $debug ]] && echo "# Executing command line:" && {
    for arg; do
      if printf "%s\n" "$arg" | grep -q ' '; then
        printf "\"%s\"\n" "$arg"
      else
        printf "%s\n" "$arg"
      fi
    done
    echo ""
  }

  exec "$@"
}

echoerr () {
  echo 1>&2 "$@"
}
vlog () {
  [[ $verbose || $debug ]] && echoerr "$@"
}
dlog () {
  [[ $debug ]] && echoerr "$@"
}

sbtjar_07_url () {
  echo "http://simple-build-tool.googlecode.com/files/sbt-launch-${1}.jar"
}
sbtjar_release_url () {
  echo "http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/$sbt_version/sbt-launch.jar"
}
sbtjar_snapshot_url () {
  local ver="$sbt_version"
  if [[ "$sbt_version" = *-SNAPSHOT ]]; then
    ver=$(sbt_snapshot_actual_version -SNAPSHOT)
    echoerr "sbt snapshot is $ver"
  elif [[ "$sbt_version" = *-SNAPSHOT ]]; then
    ver=$(sbt_snapshot_actual_version -RC)
    echoerr "sbt rc is $ver"
  fi

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
  local type="$1" # -RC or -SNAPSHOT
  local version=${sbt_version%-SNAPSHOT}
  
  curl -s --list-only "$sbt_snapshot_baseurl" | \
    grep -F $version | \
    perl -e 'print reverse <>' | \
    perl -pe 's#^<a href="([^"/]+).*#$1#;'
}

# argument is e.g. 0.11.0-SNAPSHOT
# finds the actual version (with the build id) at artifactory
sbt_snapshot_actual_version () {
  for ver in $(sbt_artifactory_list "$1"); do
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
      wget --quiet -O "$jar" "$url"
    fi
  } && [[ -f "$jar" ]]
}

acquire_sbt_jar () {
  if [[ $sbt_snapshot ]]; then
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

  -h | -help         print this message
  -v | -verbose      this runner is chattier
  -d | -debug        set sbt log level to debug
  -no-colors         disable ANSI color codes
  -sbt-create        start sbt even if current directory contains no sbt project
  -sbt-dir   <path>  path to global settings/plugins directory (default: ~/.sbt)
  -sbt-boot  <path>  path to shared boot directory (default: ~/.sbt/boot in 0.11 series)
  -ivy       <path>  path to local Ivy repository (default: ~/.ivy2)
  -mem    <integer>  set memory options (default: $sbt_mem, which is $(get_mem_opts $sbt_mem))
  -no-share          use all local caches; no sharing
  -offline           put sbt in offline mode
  -jvm-debug <port>  Turn on JVM debugging, open at the given port.
  -batch             Disable interactive mode

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
  JAVA_OPTS     environment variable, if unset uses "$java_opts"
  SBT_OPTS      environment variable, if unset uses "$default_sbt_opts"
  .sbtopts      if this file exists in the sbt root, it is prepended to the runner args
  -Dkey=val     pass -Dkey=val directly to the java runtime
  -J-X          pass option -X directly to the java runtime (-J is stripped)
  -S-X          add -X to sbt's scalacOptions (-J is stripped)

In the case of duplicated or conflicting options, the order above
shows precedence: JAVA_OPTS lowest, command line options highest.
EOM
}

addJava () {
  dlog "[addJava] arg = '$1'"
  java_args=( "${java_args[@]}" "$1" )
}
addSbt () {
  dlog "[addSbt] arg = '$1'"
  sbt_commands=( "${sbt_commands[@]}" "$1" )
}
addScalac () {
  dlog "[addScalac] arg = '$1'"
  scalac_args=( "${scalac_args[@]}" "$1" )
}
addResidual () {
  dlog "[residual] arg = '$1'"
  residual_args=( "${residual_args[@]}" "$1" )
}
addResolver () {
  addSbt "set resolvers in ThisBuild += $1"
}
unset addedSnapshotRepo
addSnapshotRepo () {
  [[ -n "$addedSnapshotRepo" ]] || addResolver "ScalaToolsSnapshots" && addedSnapshotRepo=true
}
addDebugger () {
  addJava "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=$1"
}

process_args ()
{
  require_arg () {
    local type="$1"
    local opt="$2"
    local arg="$3"
    
    if [[ -z "$arg" ]] || [[ "${arg:0:1}" == "-" ]]; then
      die "$opt requires <$type> argument"
    fi
  }
  while [[ $# -gt 0 ]]; do
    case "$1" in
       -h|-help) usage; exit 1 ;;
    -v|-verbose) verbose=1 && shift ;;
      -d|-debug) debug=1 && shift ;;
    # -u|-upgrade) addSbt 'set sbt.version 0.7.7' ; addSbt reload  && shift ;;

           -ivy) require_arg path "$1" "$2" && addJava "-Dsbt.ivy.home=$2" && shift 2 ;;
           -mem) require_arg integer "$1" "$2" && sbt_mem="$2" && shift 2 ;;
     -no-colors) addJava "-Dsbt.log.noformat=true" && shift ;;
      -no-share) addJava "$noshare_opts" && shift ;;
      -sbt-boot) require_arg path "$1" "$2" && addJava "-Dsbt.boot.directory=$2" && shift 2 ;;
       -sbt-dir) require_arg path "$1" "$2" && addJava "-Dsbt.global.base=$2" && shift 2 ;;
     -debug-inc) addJava "-Dxsbt.inc.debug=true" && shift ;;
       -offline) addSbt "set offline := true" && shift ;;
     -jvm-debug) require_arg port "$1" "$2" && addDebugger $2 && shift 2 ;;
         -batch) exec </dev/null && shift ;;

    -sbt-create) sbt_create=true && shift ;;
        -sbt-rc) [[ -n "$sbt_rc_version" ]] || die "no sbt RC candidate defined."; sbt_version=$sbt_rc_version && shift ;;
  -sbt-snapshot) addSnapshotRepo ; sbt_version=$sbt_snapshot_version && shift ;;
       -sbt-jar) require_arg path "$1" "$2" && sbt_jar="$2" && shift 2 ;;
   -sbt-version) require_arg version "$1" "$2" && sbt_version="$2" && shift 2 ;;
 -scala-version) require_arg version "$1" "$2" && addSbt "++ $2" && shift 2 ;;
    -scala-home) require_arg path "$1" "$2" && addSbt "set scalaHome in ThisBuild := Some(file(\"$2\"))" && shift 2 ;;
     -java-home) require_arg path "$1" "$2" && java_cmd="$2/bin/java" && shift 2 ;;

            -D*) addJava "$1" && shift ;;
            -J*) addJava "${1:2}" && shift ;;
            -S*) addScalac "${1:2}" && shift ;;
            -28) addSbt "++ $latest_28" && shift ;;
            -29) addSbt "++ $latest_29" && shift ;;
           -210) addSnapshotRepo ; addSbt "++ $latest_210" && shift ;;

              *) addResidual "$1" && shift ;;
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
[[ -f "$sbt_opts_file" ]] && set -- $(cat "$sbt_opts_file") "$@"

# process the combined args, then reset "$@" to the residuals
process_args "$@"
set -- "${residual_args[@]}"
argumentCount=$#

# set scalacOptions if we were given any -S opts
[[ ${#scalac_args[@]} -eq 0 ]] || addSbt "set scalacOptions in ThisBuild += \"${scalac_args[@]}\""

# figure out the version
[[ "$sbt_version" ]] || sbt_version=$(build_props_sbt)
[[ "$sbt_version" = *-SNAPSHOT* || "$sbt_version" = *-RC* ]] && sbt_snapshot=1
[[ -n "$sbt_version" ]] && echo "Detected sbt version $sbt_version"
[[ -n "$scala_version" ]] && echo "Detected scala version $scala_version"

# no args - alert them there's stuff in here
(( $argumentCount > 0 )) || echo "Starting $script_name: invoke with -help for other options"

# verify this is an sbt dir or -create was given
[[ -f ./build.sbt || -d ./project || -n "$sbt_create" ]] || {
  cat <<EOM
$(pwd) doesn't appear to be an sbt project.
If you want to start sbt anyway, run:
  $0 -sbt-create

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

# since sbt 0.7 doesn't understand iflast
(( ${#residual_args[@]} == 0 )) && residual_args=( "shell" )

# run sbt
execRunner "$java_cmd" \
  ${SBT_OPTS:-$default_sbt_opts} \
  $(get_mem_opts $sbt_mem) \
  ${java_opts} \
  ${java_args[@]} \
  -jar "$sbt_jar" \
  "${sbt_commands[@]}" \
  "${residual_args[@]}"
