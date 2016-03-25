#!/bin/bash

# ------------------------------------------------------------------------------------------
# Builds the sample project from the command line.
#
# You must provide the path to the root Android SDK directory by doing one of the following:
# 1) Provide the path as a comman line argument. For example:  build.sh <MyAndroidSdkPath>
# 2) Set the path to an environment variable named "ANDROID_SDK".
# ------------------------------------------------------------------------------------------

#
# Checks exit value for error
#
checkError() {
	if [ $? -ne 0 ]
	then
		echo "Exiting due to errors (above)"
		exit -1
	fi
}

script=`basename $0`
path=`dirname $0`
buildAPK="b"

#
# Canonicalize relative paths to absolute paths
#
pushd $path > /dev/null
dir=`pwd`
path=$dir
popd > /dev/null

# Assume user has an $ANDROID_SDK env variable
SDK_PATH=$ANDROID_SDK
## Fetch the Android SDK path from the first command line argument.
## If not provided from the command line, then attempt to fetch it from environment variable ANDROID_SDK.
if [ ! -z "$1" ]
then
	if [[ $1 != $buildAPK ]];
	then
		SDK_PATH=$1
	fi
fi

# Grab the name of the Corona sample to build in, if specified
if [ ! -z "$1" ]
then
	if [[ $1 != $buildAPK ]];
	then
		cp -R $1 ../Corona
	fi
fi

if [ -z "$CORONA_ENTERPRISE_DIR" ]
then
	CORONA_ENTERPRISE_DIR=/Applications/CoronaEnterprise
	#CORONA_ENTERPRISE_DIR=/Users/ajaymccaleb/Desktop/CoronaLabs/main-tachyon/subrepos/enterprise/build/CoronaEnterprise
fi

if [ ! -z "$2" ]
then
	CORONA_PATH=$2
else
	CORONA_PATH=$CORONA_ENTERPRISE_DIR
fi

RELATIVE_PATH_TOOL=$CORONA_PATH/Corona/mac/bin/relativePath.sh

CORONA_PATH=`"$RELATIVE_PATH_TOOL" "$path" "$CORONA_PATH"`
echo CORONA_PATH: $CORONA_PATH

# Do not continue if we do not have the path to the Android SDK.
if [ -z "$SDK_PATH" ]
then

	echo ""
	echo "USAGE:  $script"
	echo "USAGE:  $script android_sdk_path"
	echo "USAGE:  $script android_sdk_path corona_enterprise_path"
	echo "\tandroid_sdk_path: Path to the root Android SDK directory."
	echo "\tcorona_enterprise_path: Path to the CoronaEnterprise directory."
	exit -1
fi


# Before we can do a build, we must update all Android project directories to use the given Android SDK.
# We do this by running the "android" command line tool. This will add a "local.properties" file to all
# project directories that is required by the Ant build system to compile these projects for Android.
"$SDK_PATH/tools/android" update project -p .
checkError

"$SDK_PATH/tools/android" update lib-project -p "$CORONA_PATH/Corona/android/lib/Corona"
checkError

echo "Using Corona Enterprise Dir: $CORONA_PATH"

# Build the Test project via the Ant build system.
ant release -D"CoronaEnterpriseDir"="$CORONA_PATH"
checkError

# Install apk to device is "b" is passed as an argument
if [[ $1 == $buildAPK ]]; then
    echo "installing apk to device"
	adb install -r "bin/CoronaKoobEcafSDK4Test-release.apk"
fi
