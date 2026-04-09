#!/bin/sh

mkdir -p build/reports/modulegraph

./gradlew createModuleGraph
