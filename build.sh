#!/usr/bin/env bash
set -ueo pipefail
scriptDir=$(readlink -f $(dirname $0))

mkdir -p bin
echo >&2 "Compiling..."
scalac -deprecation -d bin sametime.scala

echo >&2 "Building JAR $scriptDir/sametime.jar"
(cd $scriptDir/bin; zip -qr $scriptDir/sametime.jar *)
