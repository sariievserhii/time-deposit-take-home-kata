#!/usr/bin/env bash
# Builds the app with a JDK AOT cache (JEP 483/514) for faster startup. Measured: ~6.1 s -> ~2.2 s
# (see java/README.md, "Startup time"). Requires JDK 25+ at build and run time.
#
# The training run starts the application context once and exits (spring.context.exit=onRefresh). Liquibase and
# Hibernate validation run at startup, so it needs a reachable PostgreSQL, configured like a normal run, e.g.:
#
#   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/time_deposit_kata_db scripts/build-aot-cache.sh --spring.profiles.active=dev
#
# Then start the app with:
#
#   java -XX:AOTCache=target/extracted/app.aot -jar target/extracted/time-deposit-kata-1.0-SNAPSHOT.jar [same args]
#
# Rebuild the cache whenever the jar or the JDK changes. The JVM detects a mismatched cache, prints a warning
# and starts without it (slower, but still correct).
set -euo pipefail
cd "$(dirname "$0")/.."

java_major=$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
if [ "${java_major%%.*}" -lt 25 ]; then
    echo "JDK 25+ required for -XX:AOTCacheOutput (found ${java_major}). On JDK 21, use Spring Boot's CDS support instead." >&2
    exit 1
fi

jar=target/time-deposit-kata-1.0-SNAPSHOT.jar
out=target/extracted

mvn -B -q package -DskipTests
rm -rf "$out"
# Extracted layout (application jar + lib/): class loading from nested jars can't be cached.
java -Djarmode=tools -jar "$jar" extract --destination "$out"
# Training run: records the classes loaded and linked while the Spring context starts, then exits.
java -XX:AOTCacheOutput="$out/app.aot" -Dspring.context.exit=onRefresh -jar "$out/$(basename "$jar")" "$@"

echo
echo "AOT cache written to $out/app.aot. Start with:"
echo "  java -XX:AOTCache=$out/app.aot -jar $out/$(basename "$jar") $*"
