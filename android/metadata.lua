local metadata =
{
	plugin =
	{
		format = 'jar',
		manifest = 
		{
			permissions = {},
			usesPermissions =
			{
				"android.permission.INTERNET",
				-- Needed for Facebook places.
				"android.permission.ACCESS_FINE_LOCATION",
			},
			usesFeatures = {},
			applicationChildElements =
			{
				-- Array of strings
				-- Facebook SDK v4+
				[[
				<!-- Add facebook activity so login can work. Replaces LoginActivity from version 3. -->
				<activity android:name="com.facebook.FacebookActivity"
						  android:configChanges="keyboard|keyboardHidden|screenLayout|screenSize|orientation"
						  android:theme="@android:style/Theme.Translucent.NoTitleBar" />

				<activity android:name="plugin.facebook.v4.FacebookFragmentActivity"
					android:theme="@android:style/Theme.NoTitleBar.Fullscreen" 
					android:configChanges="keyboardHidden|screenSize|orientation"/>]],
				-- TODO: Add this when we want to support sharing photos and videos
				--<!-- Add facebook content provider to enable sending images and videos 
				--	 TODO: Add [App ID] in the designated spot.-->
				--<provider android:authorities="com.facebook.app.FacebookContentProvider[App ID]"
				--		  android:name="com.facebook.FacebookContentProvider"
				--		  android:exported="true" />
			},
		},
	},
}

return metadata