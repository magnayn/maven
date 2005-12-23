#!/bin/sh

ARGS=$@
ORIG_ARGS=$ARGS

if [ -z "$JAVA_HOME" ]; then
  echo You must specify the JAVA_HOME environment variable
  exit 1
fi

JAVACMD="$JAVA_HOME/bin/java"

(
  cd bootstrap/bootstrap-mini
  ./build
  ret=$?; if [ $ret != 0 ]; then exit $ret; fi
  $JAVACMD $MAVEN_OPTS -jar bootstrap-mini.jar install $ARGS
  ret=$?; if [ $ret != 0 ]; then exit $ret; fi
)
ret=$?; if [ $ret != 0 ]; then exit $ret; fi

BOOTSTRAP_JAR=bootstrap-mini/bootstrap-mini.jar

(
  cd bootstrap/bootstrap-installer
  $JAVACMD $MAVEN_OPTS -jar ../$BOOTSTRAP_JAR package $ARGS
  ret=$?; if [ $ret != 0 ]; then exit $ret; fi
)
ret=$?; if [ $ret != 0 ]; then exit $ret; fi

PLUGINS_DIR=../plugins
if [ -d $PLUGINS_DIR ]; then
  ARGS="$ARGS --build-plugins --plugins-directory=$PLUGINS_DIR"
fi

# TODO: get rid of M2_HOME once integration tests are in here
PREFIX=`dirname $M2_HOME`

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
esac

if [ "$cygwin" = "true" ]; then
  PREFIX=`cygpath -w $PREFIX`
  JAVA_HOME=`cygpath -w $JAVA_HOME`
fi

OLD_M2_HOME=$M2_HOME
unset M2_HOME
$JAVACMD $MAVEN_OPTS -jar bootstrap/bootstrap-installer/target/bootstrap-installer.jar --prefix=$PREFIX $ARGS
ret=$?; if [ $ret != 0 ]; then exit $ret; fi
M2_HOME=$OLD_M2_HOME
export M2_HOME

ARGS=$ORIG_ARGS

(
  # TODO: should we be going back to the mini now that we have the real thing?
  cd maven-core-it-verifier
  $JAVACMD $MAVEN_OPTS -jar ../bootstrap/$BOOTSTRAP_JAR package $ARGS
  ret=$?; if [ $ret != 0 ]; then exit $ret; fi
)
ret=$?; if [ $ret != 0 ]; then exit $ret; fi

(
  cd ./maven-core-it
  echo
  echo "Running maven-core integration tests ..."
  echo
  ./maven-core-it.sh $ARGS
  ret=$?; if [ $ret != 0 ]; then exit $ret; fi
)
ret=$?; if [ $ret != 0 ]; then exit $ret; fi

