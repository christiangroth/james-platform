#!/bin/sh
docker run -v $PWD:/lizard --rm -u $(id -u ${USER}):$(id -g ${USER}) srzzumix/lizard -t 12 -l kotlin -x "*/build/*" -o lizard.csvloc
docker run -v $PWD:/lizard --rm -u $(id -u ${USER}):$(id -g ${USER}) srzzumix/lizard -t 12 -l kotlin -x "*/build/*" -o lizard.html
