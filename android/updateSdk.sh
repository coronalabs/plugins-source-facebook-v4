#!/bin/bash
# This script is to aid in updating the Facebook SDK used by Corona Lab's Facebook plugin
# It assumes that you've already downloaded the latest version of the Facebook SDK and placed it in ../sdk/android/[Version number] directory
# This script modifies the Facebook SDK project to make it compatible with Corona SDK.
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

#
# Checks if data needs to be copied to the Facebook SDK
#
needToCopyData() {
    if [ -z "$1" ]
    then
        echo "updateSdk.needToCopyData needs a resource xml file in the Facebook SDK to compare against"
        exit -1
    else
        while IFS= read -r line || [[ -n $line ]]; do
            if [ "$line" = "    <!-- Picker additions from Scrumptious sample -->" ]
            then
                # We've already copied the data over
                echo "Already copied data over for $1"
                return 0
            fi
        done < "$1"
    fi
    # Assume we need to copy data over unless we find otherwise
    return 1
}

script=`basename $0`
path=`dirname $0`

# We use a temporary delimiter to be replaced by newlines later. This is to work-around sed
tempDelimiter="@"

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
if [ -d "$NEW_SDK_PATH/facebook/libs" ]
then
    echo "Already copied over libs directory with dependencies"
else
    cp -r "$OLD_SDK_PATH/facebook/libs" "$NEW_SDK_PATH/facebook/libs"
fi

# 4. Copy picker code from Scrumptious sample to Facebook SDK (at com.facebook.picker)
echo "Copy picker code from Scrumptious sample to Facebook SDK if needed"
PICKER_PATH="$NEW_SDK_PATH/facebook/src/com/facebook/picker"
if [ -d "$PICKER_PATH" ]
then
    echo "Already copied over picker classes"
else
    cp -r "$NEW_SDK_PATH/samples/Scrumptious/src/com/example/scrumptious/picker" "$PICKER_PATH"
fi

# 5. Change package names in picker code to "com.facebook"
echo "Change package names for picker code to match Facebook SDK"
# Based on: http://how-to.wikia.com/wiki/Howto_use_find_and_sed_to_make_a_replace_in_multiple_files
find "$PICKER_PATH" -name "*.java" -exec sed -i "" "s/com.example.scrumptious/com.facebook/g" '{}' \;

# 6. Merge attributes and strings for pickers into Facebook SDK
echo "Merge attributes and strings for pickers into Facebook SDK"
SCRUM_RES_PATH="$NEW_SDK_PATH/samples/Scrumptious/res"
SDK_RES_PATH="$NEW_SDK_PATH/facebook/res"

# Merge values/attrs.xml probably wiht a comment on top:
echo "Merge res/values/attrs.xml if needed"
needToCopyData "$SDK_RES_PATH/values/attrs.xml"
if [ $? -ne 0 ]
then
    echo "Need to Copy attrs over"
    # Read in portions of values/attrs.xml we need to merge
    # Based on: http://stackoverflow.com/questions/10929453/bash-scripting-read-file-line-by-line
    saveInput="false"
    attrsToAdd="$tempDelimiter    <!-- Picker additions from Scrumptious sample -->$tempDelimiter"
    while IFS= read -r line || [[ -n $line ]]; do
        echo "line read from values/attrs.xml: $line"
        if [ "$saveInput" = "true" ]
        then
            attrsToAdd=$attrsToAdd$line$tempDelimiter
        fi

        if [ "$line" = "<resources>" ]
        then
            # Only need pieces after this tag
            saveInput="true"
        fi
    done < "$SCRUM_RES_PATH/values/attrs.xml"
    #echo "attrsToAdd: $attrsToAdd"
    # Add the content from Scrumptious to the Facebook SDK
    sed -i '' s:$'</resources>':"$attrsToAdd":g "$SDK_RES_PATH/values/attrs.xml"
    # Put in the newlines
    # Based on: http://unix.stackexchange.com/questions/48725/redirecting-tr-stdout-to-a-file
    cat "$SDK_RES_PATH/values/attrs.xml" | tr "$tempDelimiter" '\n' > temp && mv temp "$SDK_RES_PATH/values/attrs.xml"
fi

# Append this to values/strings.xml within the resources tag if needed
#
#			<!-- Picker additions from Scrumptious sample -->
#    		<string name="picker_placepicker_subtitle_format">%1$s • %2$,d were here</string>
#    		<string name="picker_placepicker_subtitle_catetory_only_format">%1$s</string>
#    		<string name="picker_placepicker_subtitle_were_here_only_format">%1$,d were here</string>
#    		<string name="picker_picker_done_button_text">Done</string>
#    		<string name="choose_friends">Choose Friends</string>
#			<string name="nearby">Nearby</string>
echo "Adding needed strings to res/values/strings.xml if needed"
needToCopyData "$SDK_RES_PATH/values/strings.xml"
if [ $? -ne 0 ]
then
    stringsToAdd="
    <!-- Picker additions from Scrumptious sample -->
    <string name=\"picker_placepicker_subtitle_format\">%1\$s • %2$,d were here</string>
    <string name=\"picker_placepicker_subtitle_catetory_only_format\">%1\$s</string>
    <string name=\"picker_placepicker_subtitle_were_here_only_format\">%1$,d were here</string>
    <string name=\"picker_picker_done_button_text\">Done</string>
    <string name=\"choose_friends\">Choose Friends</string>
	<string name=\"nearby\">Nearby</string>
</resources>"
    # Put in temp delimiters for sed
    stringsToAdd=`echo "$stringsToAdd" | tr '\n' "$tempDelimiter"`
    echo "stringsToAdd: $stringsToAdd"
    # Add the content from Scrumptious to the Facebook SDK
    sed -i '' s:$'</resources>':"$stringsToAdd":g "$SDK_RES_PATH/values/strings.xml"
    # Put in the newlines
    # Based on: http://unix.stackexchange.com/questions/48725/redirecting-tr-stdout-to-a-file
    cat "$SDK_RES_PATH/values/strings.xml" | tr "$tempDelimiter" '\n' > temp && mv temp "$SDK_RES_PATH/values/strings.xml"
fi

# 7. Copy over resources for Place and Friends pickers:
echo "Copy over resources for Place and Friends pickers"
#       Copy over drawables:
echo "Copy over drawables"
SCRUM_DRAWABLES_PATH="$SCRUM_RES_PATH/drawable"
SDK_DRAWABLES_PATH="$SDK_RES_PATH/drawable"
#		- Copy over drawable/picker_list_divider.9.png
#		- Copy over drawable/picker_list_selector.xml
#		- Copy over drawable/picker_top_background.xml
#		- Copy over drawable/picker_selector_top_button.xml
#		- Copy over drawable/picker_list_selector_disabled.9.png
#		- Copy over drawable/picker_list_selector_background_transition.xml
#		- Copy over drawable/picker_list_focused.9.png
#		- Copy over drawable/picker_list_pressed.9.png
#		- Copy over drawable/picker_list_longpressed.9.png
#		- Copy over drawable/picker_top_button.xml
#		- Copy over drawable/picker_list_section_header_background.9.png
#		- Copy over drawable/picker_button_check.xml
#		- Copy over drawable/picker_button_check_on.png
#		- Copy over drawable/picker_button_check_off.png
#		- Copy over drawable/profile_default_icon.png
#		- Copy over drawable/picker_place_default_icon.png
drawablesToCopy=(   picker_list_divider.9.png
                    picker_list_selector.xml
                    picker_top_background.xml
                    picker_selector_top_button.xml
                    picker_list_selector_disabled.9.png
                    picker_list_selector_background_transition.xml
                    picker_list_focused.9.png
                    picker_list_pressed.9.png
                    picker_list_longpressed.9.png
                    picker_top_button.xml
                    picker_list_section_header_background.9.png
                    picker_button_check.xml
                    picker_button_check_on.png
                    picker_button_check_off.png
                    profile_default_icon.png
                    picker_place_default_icon.png
                )
for drawable in "${drawablesToCopy[@]}"
do
    cp -v "$SCRUM_DRAWABLES_PATH/$drawable" "$SDK_DRAWABLES_PATH/$drawable"
done
#		- Copy over drawable-hdpi/picker_magnifier.png
cp -v "$SCRUM_DRAWABLES_PATH-hdpi/picker_magnifier.png" "$SDK_DRAWABLES_PATH-hdpi/picker_magnifier.png"
#		- Copy over drawable-mdpi/picker_magnifier.png
cp -v "$SCRUM_DRAWABLES_PATH-mdpi/picker_magnifier.png" "$SDK_DRAWABLES_PATH-mdpi/picker_magnifier.png"
#		- Copy over drawable-xhdpi/picker_magnifier.png
cp -v "$SCRUM_DRAWABLES_PATH-xhdpi/picker_magnifier.png" "$SDK_DRAWABLES_PATH-xhdpi/picker_magnifier.png"

#       Copy over layout:
echo "Copy over layout"
SCRUM_LAYOUT_PATH="$SCRUM_RES_PATH/layout"
SDK_LAYOUT_PATH="$SDK_RES_PATH/layout"
#		- Copy over layout/picker_friendpickerfragment.xml
#		- Copy over layout/picker_placepickerfragment.xml
#		- Copy over layout/picker_title_bar_stub.xml
#		- Copy over layout/picker_title_bar.xml
#		- Copy over layout/picker_list_section_header.xml
#		- Copy over layout/picker_activity_circle_row.xml
#		- Copy over layout/picker_list_row.xml
#		- Copy over layout/picker_image.xml
#		- Copy over layout/picker_checkbox.xml
#		- Copy over layout/picker_search_box.xml
#		- Copy over layout/picker_search_bar_layout.xml
#		- Copy over layout/picker_placepickerfragment_list_row.xml
layoutToCopy=(  picker_friendpickerfragment.xml
                picker_placepickerfragment.xml
                picker_title_bar_stub.xml
                picker_title_bar.xml
                picker_list_section_header.xml
                picker_activity_circle_row.xml
                picker_list_row.xml
                picker_image.xml
                picker_checkbox.xml
                picker_search_box.xml
                picker_search_bar_layout.xml
                picker_placepickerfragment_list_row.xml
             )
for layout in "${layoutToCopy[@]}"
do
    cp -v "$SCRUM_LAYOUT_PATH/$layout" "$SDK_LAYOUT_PATH/$layout"
done

#       Copy over values:
echo "Copy over values"
SCRUM_VALUES_PATH="$SCRUM_RES_PATH/values"
SDK_VALUES_PATH="$SDK_RES_PATH/values"
#		- Copy over values/colors.xml
cp -v "$SCRUM_VALUES_PATH/colors.xml" "$SDK_VALUES_PATH/colors.xml"
#		- Copy over values/drawables.xml
cp -v "$SCRUM_VALUES_PATH/drawables.xml" "$SDK_VALUES_PATH/drawables.xml"
#		- Copy over values-hdpi/dimens.xml
mkdir "$SDK_VALUES_PATH-hdpi"
cp -v "$SCRUM_VALUES_PATH-hdpi/dimens.xml" "$SDK_VALUES_PATH-hdpi/dimens.xml"
#		- Copy over values-ldpi/dimens.xml
mkdir "$SDK_VALUES_PATH-ldpi"
cp -v "$SCRUM_VALUES_PATH-ldpi/dimens.xml" "$SDK_VALUES_PATH-ldpi/dimens.xml"
#		- Copy over values-mdpi/dimens.xml
mkdir "$SDK_VALUES_PATH-mdpi"
cp -v "$SCRUM_VALUES_PATH-mdpi/dimens.xml" "$SDK_VALUES_PATH-mdpi/dimens.xml"
#		- Copy over values-xhdpi/dimens.xml
mkdir "$SDK_VALUES_PATH-xhdpi"
cp -v "$SCRUM_VALUES_PATH-xhdpi/dimens.xml" "$SDK_VALUES_PATH-xhdpi/dimens.xml"
