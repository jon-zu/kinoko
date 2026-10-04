default:
    @just --list

# Build the executable jar without running tests.
build:
    mvn -DskipTests package

# Run the test suite (set DEV_LOGIN=true to test the enabled dev login path).
test:
    mvn test

# Normal login with encrypted client traffic.
run: build
    @just _launch false false

# Direct channel login with encrypted client traffic.
run-dev: build
    @just _launch true false

# Normal login with plain client traffic.
run-plain: build
    @just _launch false true

# Direct channel login with plain client traffic.
run-dev-plain: build
    @just _launch true true

# Launch an already-built jar with the current environment settings.
run-jar:
    java -jar target/server.jar

[private]
_launch dev_login plain_traffic:
    DEV_LOGIN={{dev_login}} PLAIN_TRAFFIC={{plain_traffic}} java -jar target/server.jar

# Seed final-job development accounts offline and export their real IDs.
seed-jobs output="seed_acc.json": build
    java -jar target/server.jar --seed-jobs {{quote(output)}}
