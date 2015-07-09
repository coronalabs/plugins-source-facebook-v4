#!/bin/bash
# This script is to aid in updating the Facebook SDK used by Corona Lab's Facebook plugin
# It assumes that you've already downloaded the latest version of the Facebook SDK and placed it in ../sdk/android/[Version number] directory
# This script modifies the Facebook SDK project to make it more compatible with Corona SDK.
#

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

# 1. Fetch versions to upgrade from and to
echo "Fetch Facebook SDK Versions"
OLD_SDK_VERSION=
NEW_SDK_VERSION=
if [ ! -z "$1" ]
then
    OLD_SDK_VERSION=$1
fi

if [ ! -z "$2" ]
then
    NEW_SDK_VERSION=$2
fi

if [ -z "$OLD_SDK_VERSION" ] || [ -z "$NEW_SDK_VERSION" ]
then
    echo ""
	echo "USAGE:  $script old_facebook_sdk_version new_facebook_sdk_version"
	echo -e "\told_facebook_sdk_version: Facebook SDK version we're upgrading from (e.g. 4.3.0)."
	echo -e "\tnew_facebook_sdk_version: Facebook SDK version we're upgrading to (e.g. 4.4.0)."
	exit -1
fi

# 2. Replace SDK number in build.plugin.sh, build.plugin.xml and project.properties
echo "Upgrade plugin project files with new SDK version"
sed -i "" "s/$OLD_SDK_VERSION/$NEW_SDK_VERSION/g" "$path/build.plugin.sh"
sed -i "" "s/$OLD_SDK_VERSION/$NEW_SDK_VERSION/g" "$path/build.plugin.xml"
sed -i "" "s/$OLD_SDK_VERSION/$NEW_SDK_VERSION/g" "$path/project.properties"

# 3. Copy build files into Facebook SDK: build.xml, project.properties and libs directory
echo "Copying Ant build scripts and dependencies into Facebook SDK project"
OLD_SDK_PATH="$path/../sdk/android/$OLD_SDK_VERSION"
NEW_SDK_PATH="$path/../sdk/android/$NEW_SDK_VERSION"
cp -v "$OLD_SDK_PATH/facebook/build.xml" "$NEW_SDK_PATH/facebook/build.xml"
cp -v "$OLD_SDK_PATH/facebook/project.properties" "$NEW_SDK_PATH/facebook/project.properties"
# libs directory with android-support-v4.jar, bolts-android-1.1.2.jar, and BUCK
cp -r "$OLD_SDK_PATH/facebook/libs" "$NEW_SDK_PATH/facebook/libs"

# 4. Copy picker code from Scrumptious sample to Facebook SDK (at com.facebook.picker)
echo "Copy picker code from Scrumptious sample to Facebook SDK"
PICKER_PATH="$NEW_SDK_PATH/facebook/src/com/facebook/picker"
cp -r "$NEW_SDK_PATH/samples/Scrumptious/src/com/example/scrumptious/picker" "$PICKER_PATH"

# 5. Change package names in picker code to "com.facebook"
echo "Change package names for picker code to match Facebook SDK"
# Based on: http://how-to.wikia.com/wiki/Howto_use_find_and_sed_to_make_a_replace_in_multiple_files
find "$PICKER_PATH" -name "*.java" -exec sed -i "" "s/com.example.scrumptious/com.facebook/g" '{}' \;

# 6. Merge attributes and strings for pickers into Facebook SDK
echo "Merge attributes and strings for pickers into Facebook SDK"
SCRUM_RES_PATH="$NEW_SDK_PATH/samples/Scrumptious/res"
SDK_RES_PATH="$NEW_SDK_PATH/facebook/res"

# Merge values/attrs.xml probably wiht a comment on top:
echo "Merge res/values/attrs.xml"
# Read in portions of values/attrs.xml we need to merge
# Based on: http://stackoverflow.com/questions/10929453/bash-scripting-read-file-line-by-line
saveInput="false"
# We use a temporary delimiter to be replaced by newlines later. This is to work-around sed
tempDelimiter="@"
attrsToAdd="$tempDelimiter    <!-- Picker additions from Scrumptious sample -->$tempDelimiter"
while IFS= read -r line || [[ -n $line ]]; do
    echo "line read from values/attrs.xml: $line"
    if [ "$saveInput" = "true" ]
    then
        attrsToAdd=$attrsToAdd$line$tempDelimiter
    fi
    # Only need pieces after this tag
    if [ "$line" = "<resources>" ]
    then
        saveInput="true"
    fi
done < "$SCRUM_RES_PATH/values/attrs.xml"
echo "attrsToAdd: $attrsToAdd"
# Add the content from Scrumptious to the Facebook SDK
sed -i '' s,$'</resources>',"$attrsToAdd",g "$SDK_RES_PATH/values/attrs.xml"
# Put in the newlines
# Based on: http://unix.stackexchange.com/questions/48725/redirecting-tr-stdout-to-a-file
cat "$SDK_RES_PATH/values/attrs.xml" | tr "$tempDelimiter" '\n' > temp && mv temp "$SDK_RES_PATH/values/attrs.xml"

#		- Append this to values/strings.xml within the resources tag:
#
#			<!-- Picker additions from Scrumptious sample -->
#    		<string name="picker_placepicker_subtitle_format">%1$s • %2$,d were here</string>
#    		<string name="picker_placepicker_subtitle_catetory_only_format">%1$s</string>
#    		<string name="picker_placepicker_subtitle_were_here_only_format">%1$,d were here</string>
#    		<string name="picker_picker_done_button_text">Done</string>
#    		<string name="choose_friends">Choose Friends</string>
#			<string name="nearby">Nearby</string>

# 7. Copy over resources for Place and Friends pickers:
#		- Copy over layout/picker_friendpickerfragment.xml
#		- Copy over layout/picker_placepickerfragment.xml
#		- Copy over layout/picker_title_bar_stub.xml
#		- Copy over drawable/picker_list_divider.9.png
#		- Copy over drawable/picker_list_selector.xml
#		- Copy over layout/picker_title_bar.xml
#		- Copy over drawable/picker_top_background.xml
#		- Copy over drawable/picker_selector_top_button.xml
#		- Copy over drawable/picker_list_selector_disabled.9.png
#		- Copy over drawable/picker_list_selector_background_transition.xml
#		- Copy over drawable/picker_list_focused.9.png
#		- Copy over drawable/picker_list_pressed.9.png
#		- Copy over drawable/picker_list_longpressed.9.png
#		- Copy over drawable/picker_top_button.xml
#		- Copy over layout/picker_list_section_header.xml
#		- Copy over drawable/picker_list_section_header_background.9.png
#		- Copy over layout/picker_activity_circle_row.xml
#		- Copy over layout/picker_list_row.xml
#		- Copy over layout/picker_image.xml
#		- Copy over layout/picker_checkbox.xml
#		- Copy over drawable/picker_button_check.xml
#		- Copy over drawable/picker_button_check_on.png
#		- Copy over drawable/picker_button_check_off.png
#		- Copy over drawable/profile_default_icon.png
#		- Copy over layout/picker_search_box.xml
#		- Copy over layout/picker_search_bar_layout.xml
#		- Copy over values/colors.xml PROBABLY NOT A BAD IDEA TO REMOVE THE SCRUMPTIOUS_MAIN_ORANGE in here
#		- Copy over drawable-hdpi/picker_magnifier.png
#		- Copy over drawable-mdpi/picker_magnifier.png
#		- Copy over drawable-xhdpi/picker_magnifier.png
#		- Copy over values-hdpi/dimens.xml
#		- Copy over values-ldpi/dimens.xml
#		- Copy over values-mdpi/dimens.xml
#		- Copy over values-xhdpi/dimens.xml
#		- Copy over values/drawables.xml
#		- Copy over layout/picker_placepickerfragment_list_row.xml
#		- Copy over drawable/picker_place_default_icon.png