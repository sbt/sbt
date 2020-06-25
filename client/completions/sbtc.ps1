$scriptblock = {
  param($commandName, $line, $position)
  $len = $line.ToString().length
  $spaces = " " * ($position - $len)
  $arg="--completions=$line$spaces"
  & 'sbtc.exe' @('--no-tab', '--no-stderr', $arg)
}
Set-Alias -Name sbtc -Value sbtc.exe
Register-ArgumentCompleter -CommandName sbtc.exe -ScriptBlock $scriptBlock
Register-ArgumentCompleter -CommandName sbtc -ScriptBlock $scriptBlock
