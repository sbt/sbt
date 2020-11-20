$scriptblock = {
  param($commandName, $line, $position)
  $len = $line.ToString().length
  $spaces = " " * ($position - $len)
  $arg="--completions=$line$spaces"
  & 'sbtn.exe' @('--no-tab', '--no-stderr', $arg)
}
Set-Alias -Name sbtn -Value sbtn.exe
Register-ArgumentCompleter -CommandName sbtn.exe -ScriptBlock $scriptBlock
Register-ArgumentCompleter -CommandName sbtn -ScriptBlock $scriptBlock
