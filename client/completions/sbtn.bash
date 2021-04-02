#!/usr/bin/env bash

_do_sbtn_completions() {
  COMPREPLY=($(sbtn "--completions=${COMP_LINE}"))
}

complete -F _do_sbtn_completions sbtn
