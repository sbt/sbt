#!/usr/bin/env bash
#

# A library to simplify using the SBT launcher from other packages.
# Note: This should be used by tools like giter8/conscript etc.

# TODO - Should we merge the main SBT script with this library?

# TODO - Don't hardcode this.
declare sbt_version="0.11.2"
declare -r sbt_release_version=0.11.2
declare -r sbt_snapshot_version=0.11.3-SNAPSHOT

if test -z "$HOME"; then
  declare -r script_dir="$(dirname $script_path)"
else
  declare -r script_dir="$HOME/.sbt"
fi

declare -a residual_args
declare -a java_args
declare -a scalac_args
declare -a sbt_commands
declare java_cmd=java

echoerr () {
  echo 1>&2 "$@"
}
vlog () {
  [[ $verbose || $debug ]] && echoerr "$@"
}
dlog () {
  [[ $debug ]] && echoerr "$@"
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
 *-SNAPSHOT*) sbtjar_snapshot_url ;;
       *-RC*) sbtjar_snapshot_url ;;
           *) sbtjar_release_url ;;
  esac
}

jar_file () {
  if [[ -f "/usr/lib/sbt/$1/sbt-launch.jar" ]]; then
    echo "/usr/lib/sbt/$1/sbt-launch.jar"
  else
    echo "$script_dir/.lib/$1/sbt-launch.jar"
  fi
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
  local sbt_version="$1"

  sbt_url="$(jar_url)"
  sbt_jar="$(jar_file $sbt_version)"

  [[ -f "$sbt_jar" ]] || download_url "$sbt_url" "$sbt_jar"
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

addJava () {
  dlog "[addJava] arg = '$1'"
  java_args=( "${java_args[@]}" "$1" )
}
addSbt () {
  dlog "[addSbt] arg = '$1'"
  sbt_commands=( "${sbt_commands[@]}" "$1" )
}
addResidual () {
  dlog "[residual] arg = '$1'"
  residual_args=( "${residual_args[@]}" "$1" )
}
addDebugger () {
  addJava "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=$1"
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

process_args () {
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
     -jvm-debug) require_arg port "$1" "$2" && addDebugger $2 && shift 2 ;;
         -batch) exec </dev/null && shift ;;

       -sbt-jar) require_arg path "$1" "$2" && sbt_jar="$2" && shift 2 ;;
   -sbt-version) require_arg version "$1" "$2" && sbt_version="$2" && shift 2 ;;
     -java-home) require_arg path "$1" "$2" && java_cmd="$2/bin/java" && shift 2 ;;

            -D*) addJava "$1" && shift ;;
            -J*) addJava "${1:2}" && shift ;;
              *) addResidual "$1" && shift ;;
    esac
  done
}

run() {
  # no jar? download it.
  [[ -f "$sbt_jar" ]] || acquire_sbt_jar "$sbt_version" || {
    # still no jar? uh-oh.
    echo "Download failed. Obtain the sbt-launch.jar manually and place it at $sbt_jar"
    exit 1
  }

  # process the combined args, then reset "$@" to the residuals
  process_args "$@"
  set -- "${residual_args[@]}"
  argumentCount=$#

  # run sbt
  execRunner "$java_cmd" \
    ${SBT_OPTS:-$default_sbt_opts} \
    $(get_mem_opts $sbt_mem) \
    ${java_opts} \
    ${java_args[@]} \
    -jar "$sbt_jar" \
    "${sbt_commands[@]}" \
    "${residual_args[@]}"
}

runAlternateBoot() {
  local bootpropsfile="$1"
  shift
  addJava "-Dsbt.boot.properties=$bootpropsfile"
  run $@
}
