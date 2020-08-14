function __sbtcomp
  sbtc --completions="$argv"
end
complete --command sbtn -f --arguments '(__sbtcomp (commandline -cp))'
