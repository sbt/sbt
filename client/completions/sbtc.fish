function __sbtcomp
  sbtc --completions="$argv"
end
complete --command sbtc -f --arguments '(__sbtcomp (commandline -cp))'
