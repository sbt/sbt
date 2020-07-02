#!/usr/bin/env bash

_do_sbtc_completions() {
  COMPREPLY=($(sbtc "--completions=${COMP_LINE}"))
}

complete -F _do_sbtc_completions sbtc
