#!/bin/sh

mkdir -p build/reports/cloc

docker run --rm -v $PWD:/tmp -u $(id -u ${USER}):$(id -g ${USER}) aldanial/cloc --exclude-dir=build,.gradle --md --report-file=build/reports/cloc/cloc.md .
docker run --rm -v $PWD:/tmp -u $(id -u ${USER}):$(id -g ${USER}) aldanial/cloc --exclude-dir=build,.gradle --by-file --md --report-file=build/reports/cloc/cloc-per-file.md .
