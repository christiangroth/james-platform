#!/bin/sh

mkdir -p build/reports
docker run --rm -v $PWD:/tmp -u $(id -u ${USER}):$(id -g ${USER}) aldanial/cloc --exclude-dir=build,.gradle --json --report-file=build/reports/cloc.json .
docker run --rm -v $PWD:/tmp -u $(id -u ${USER}):$(id -g ${USER}) aldanial/cloc --exclude-dir=build,.gradle --by-file --json --report-file=build/reports/cloc-per-file.json .
