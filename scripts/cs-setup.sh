#!/usr/bin/env bash

#
# coursier launcher setup script
# See https://github.com/coursier/ci-scripts
#


set -e

coursier_version="2.0.0-RC6-24"
for_graalvm="false"
dest="cs"

# Adapted from https://github.com/paulp/sbt-extras/blob/ea47026bd55439760f4c5e9b27b002fa64a8b82f/sbt#L427-L435 and around
die() {
  echo "Aborting: $*"
  exit 1
}
process_args() {
  require_arg() {
    local type="$1"
    local opt="$2"
    local arg="$3"

    if [[ -z "$arg" ]] || [[ "${arg:0:1}" == "-" ]]; then
      die "$opt requires <$type> argument"
    fi
  }
  parsing_args="true"
  while [[ $# -gt 0 && "$parsing_args" == "true" ]]; do
    case "$1" in
      --version) require_arg version "$1" "$2" && coursier_version="$2" && shift 2 ;;
      --dest) require_arg path "$1" "$2" && dest="$2" && shift 2 ;;
      --graalvm) for_graalvm="true" && shift ;;
      *) die "Unexpected argument: '$1'" ;;
    esac
  done
}

process_args "$@"


do_chmod="1"
ext=""
setenv_bat="0"

# https://stackoverflow.com/questions/3466166/how-to-check-if-running-in-cygwin-mac-or-linux/17072017#17072017
if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  cs_url="https://github.com/coursier/coursier/releases/download/v$coursier_version/cs-x86_64-pc-linux"
  cache_base="$HOME/.cache/coursier/v1"
elif [ "$(uname)" == "Darwin" ]; then
  cs_url="https://github.com/coursier/coursier/releases/download/v$coursier_version/cs-x86_64-apple-darwin"
  cache_base="$HOME/Library/Caches/Coursier/v1"
else
  # assuming Windows…
  cs_url="https://github.com/coursier/coursier/releases/download/v$coursier_version/cs-x86_64-pc-win32.exe"
  cache_base="$LOCALAPPDATA/Coursier/v1" # TODO Check that
  ext=".exe"
  do_chmod="0"

  if [ "$for_graalvm" = "true" ]; then
    choco install -y windows-sdk-7.1 vcbuildtools kb2519277
    setenv_bat="1"
  fi
fi

cache_dest="$cache_base/$(echo "$cs_url" | sed 's@://@/@')"

if [ -f "$cache_dest" ]; then
  echo "Found $cache_dest in cache"
else
  mkdir -p "$(dirname "$cache_dest")"
  tmp_dest="$cache_dest.tmp-setup"
  echo "Downloading $cs_url"
  curl -Lo "$tmp_dest" "$cs_url"
  mv "$tmp_dest" "$cache_dest"
fi

if [ "$setenv_bat" = "1" ]; then
  cp "$cache_dest" ".$dest$ext"
  "./.$dest$ext" --help
  if [ "$do_chmod" = "1" ]; then
    chmod +x ".$dest$ext"
  fi
  cat > "$dest.bat" << EOF
@call "C:\\Program Files\\Microsoft SDKs\\Windows\\v7.1\\Bin\\SetEnv.Cmd"
%~dp0.$dest$ext %*
EOF
  # that one is for bash
  cat > "$dest" << EOF
#!/usr/bin/env bash
set -e
exec $(pwd)/$dest.bat "\$@"
EOF
  chmod +x "$dest" || true
else
  cp "$cache_dest" "$dest$ext"
  if [ "$do_chmod" = "1" ]; then
    chmod +x "$dest$ext"
  fi
fi

export PATH=".:$PATH"
"$dest" --help
