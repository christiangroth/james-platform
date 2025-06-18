#!/bin/sh
docker run --rm -v $PWD:/tmp -u $(id -u ${USER}):$(id -g ${USER}) aldanial/cloc --exclude-dir=build,.gradle --json --report-file=cloc.json .
docker run --rm -v $PWD:/tmp -u $(id -u ${USER}):$(id -g ${USER}) aldanial/cloc --exclude-dir=build,.gradle --by-file --json --report-file=cloc-per-file.json .
