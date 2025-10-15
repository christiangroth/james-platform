#!/bin/sh

mkdir -p build/reports
docker run -v $PWD:/lizard --rm -u $(id -u ${USER}):$(id -g ${USER}) srzzumix/lizard -t 12 -l kotlin -x "*/build/*" -o build/reports/lizard.csvloc
docker run -v $PWD:/lizard --rm -u $(id -u ${USER}):$(id -g ${USER}) srzzumix/lizard -t 12 -l kotlin -x "*/build/*" -o build/reports/lizard.html
