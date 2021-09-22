#!/bin/bash
set -eux

version_prefix="61c.2021-fa"
version="${version_prefix}.$(git describe --abbrev --dirty --always)"

./gradlew -Pversion="${version}" build
ln -sf "venus-${version}.jar" build/libs/venus-latest.jar
