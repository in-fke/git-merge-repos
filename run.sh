#!/bin/sh

# BASEDIR=`dirname $0`
# mvn --file "$BASEDIR/pom.xml" --quiet clean compile exec:java -Dexec.args="$*"

SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
(
cd "$SCRIPT_PATH" && \
mvn clean install && \
java -Xmx512m -cp '$SCRIPT_PATH/lib/*' org.nibor.git_merge_repos.Main "$@"
)
