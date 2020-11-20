function __sbtcomp
  sbtn --completions="$argv"
end
complete --command sbtn -f --arguments '(__sbtcomp (commandline -cp))'
