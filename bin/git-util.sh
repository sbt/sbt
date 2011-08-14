#!/usr/bin/env bash
#

camelCase () {
  # pretty sad having to resort to this in 2011
  SED=""
  if [ -f /usr/local/bin/gsed ]; then
    SED=/usr/local/bin/gsed
  else
    SED=sed
  fi

  echo "$1" | $SED -e 's/[-_]\([a-z]\)/\u\1/g' | $SED -e 's/^./\u&/;'
}

createGithub () {
  which hub && { echo "You need hub for this." ; return }
  
  local project="$1"
  # local git_token=$(git config --global github.token)
  local git_url="git@github.com:$(git config --global github.user)/$project.git"

  echo Creating $git_url
  hub create
  git config --local --add branch.master.remote origin
  git config --local --add branch.master.merge refs/heads/master
  git push origin master
fi

createGitIgnore () {
  cat > .gitignore <<EOM
target
/project/boot
/project/plugins
lib_managed
src_managed
/ext
EOM
}

createGitRepo () {
  createGitIgnore
  git init
  git add .gitignore project src
  git add -f project/plugins/Plugins.scala
  git commit -m "Initial Import."
}
