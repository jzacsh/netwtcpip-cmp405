#!/usr/bin/env bash

# sedExtr='s/^import[[:space:]]\([[:alpha:]]*\.\)+\.\([[:alpha:]]*\);/\1/g'p
matchExpr='^import.*[[:alnum:]]*\.\([[:alnum:]]*\);$'

sedExtr='s/'"$matchExpr"'/\1/g'

importMatch='^import[[:space:]].*;$'

# $1=java file
listImports() ( grep -E "$importMatch" "$1" | sed "$sedExtr"; )

for java in "$@";do
  #printf '\n\nscrubbing "%s"\n' "$java"

  # for debugging:
  #sed -n "/$matchExpr/"p "$java"
  #colordiff -u "$java" <(sed < "$sedExtr")

  while read import; do
    #printf '\tverifying import `%s` is used...\n' "$import"
    { grep -vE "$importMatch" "$java" | grep -E '\b'"$import"'\b'; } >/dev/null 2>&1 || {
      printf '\t%s  %s\n' "$import" "$java" >&2
    }
  done < <(listImports "$java")
done
