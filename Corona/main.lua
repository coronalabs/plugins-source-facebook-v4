--
-- Project: Facebook Connect sample app
--
-- Date: July 14, 2015
--
-- Version: 1.8
--
-- File name: main.lua
--
-- Author: Corona Labs
--
-- Abstract: Presents the Facebook Connect login dialog, and then posts to the user's stream
-- (Also demonstrates the use of external libraries.)
--
-- Demonstrates: webPopup, network, Facebook library
--
-- File dependencies: facebook.lua
--
-- Target devices: Simulator and Device
--
-- Limitations: Requires internet access; no error checking if connection fails
--
-- Update History:
--	v1.1		Layout adapted for Android/iPad/iPhone4
--  v1.2		Modified for new Facebook Connect API (from build #243)
--  v1.3		Added buttons to: Post Message, Post Photo, Show Dialog, Logout
--  v1.4		Added  ...{"publish_stream"} .. permissions setting to facebook.login() calls.
--  v1.5		Added single sign-on support in build.settings (must replace XXXXXXXXX with valid facebook appId)
--  v1.6		Modified the build.settings file to get the plugin for iOS.
--  v1.7		Added more buttons to test features. Upgraded sample to use Facebook v4 plugin.
--  v1.8		Uses new login model introduced in Facebook v4 plugin.
--  v1.9		Code cleanup and improvement. New interface

--
-- Comments:
-- Requires API key and application secret key from Facebook. To begin, log into your Facebook
-- account and add the "Developer" application, from which you can create additional apps.
--
-- IMPORTANT: Please ensure your app is compatible with Facebook Single Sign-On or your
--            Facebook implementation will fail! See the following blog post for more details:
--            http://www.coronalabs.com/links/facebook-sso
--
-- Sample code is MIT licensed, see https://www.coronalabs.com/links/code/license
-- Copyright (C) 2010 Corona Labs Inc. All Rights Reserved.
--
-- Supports Graphics 2.0
---------------------------------------------------------------------------------------

-- NOTE: To create a mobile app that interacts with Facebook Connect, first log into Facebook
-- and create a new Facebook application. That will give you the "API key" and "application secret".

-- Require the widget, facebook and json libraries
local widget = require("widget")
local facebook = require("plugin.facebook.v4")
local json = require("json")

-- Hide the status bar
display.setStatusBar(display.HiddenStatusBar)

-- Comment out the next line when through debugging your app
io.output():setvbuf('no') -- debug: disable output buffering for Xcode Console

-- Localise variables
local centerX = display.contentCenterX
local centerY = display.contentCenterY
local _W = display.actualContentWidth
local _H = display.actualContentHeight
-- Facebook Commands
local fbCommand -- forward reference
local LOGOUT = 1
local SHOW_FEED_DIALOG = 2
local SHARE_LINK_DIALOG = 3
local POST_MSG = 4
local POST_PHOTO = 5
local GET_USER_INFO = 6
local PUBLISH_INSTALL = 7
local mainGroup = display.newGroup()

-- Render the sample code UI
local sampleUI = require("sampleUI.sampleUI")
sampleUI:newUI({theme = "darkgrey", title = "Facebook v4", showBuildNum = true})

-- This function is useful for debugging problems with using FB Connect's web api,
-- e.g. you passed bad parameters to the web api and get a response table back
local function printTable(t, label, level)
	if label then print(label) end
	level = level or 1

	if t then
		for k, v in pairs(t) do
			local prefix = ""
			for i = 1, level do
				prefix = prefix .. "\t"
			end

			print(prefix .. "[" .. tostring(k) .. "] = " .. tostring(v))

			if type(v) == "table" then
				print(prefix .. "{")
				printTable(v, nil, level + 1)
				print(prefix .. "}")
			end
		end
	end
end

local function createStatusMessage(message, x, y)
	-- Show text, using default bold font of device (Helvetica on iPhone)
	local textObject = display.newText(message, 0, 0, native.systemFontBold, 12)
	textObject:setFillColor( 1,1,1 )

	-- A trick to get text to be centered
	local group = display.newGroup()
	group.x = x
	group.y = y
	group:insert(textObject, true)

	-- Insert rounded rect behind textObject
	local roundedRect = display.newRoundedRect(0, 0, _W - 20, textObject.contentHeight + 5, 6)
	roundedRect:setFillColor(0.22, 0.22, 0.22, 0.75)
	group:insert(1, roundedRect, true)
	group.textObject = textObject
	mainGroup:insert(group)

	return group
end

-- Create the status message
local statusMessage = createStatusMessage("   Not connected  ", centerX, 60)

-- Runs the desired facebook command
local function processFBCommand()
	-- The following displays a Facebook dialog box for posting to your Facebook Wall
	if fbCommand == SHOW_FEED_DIALOG then
		-- "feed" is the standard "post status message" dialog
		local response = facebook.showDialog("feed")
		printTable(response)

	-- This displays a Facebook Dialog for posting a link with a photo to your Facebook Wall
	elseif fbCommand == SHARE_LINK_DIALOG then
		-- Issue the FB request
		local response = facebook.showDialog( "link",
		{
			name = "Facebook v4 Corona plugin on iOS!",
			link = "https://coronalabs.com/blog/2015/09/01/facebook-v4-plugin-ios-beta-improvements-and-new-features/",
			description = "More Facebook awesomeness for Corona!",
			picture = "https://coronalabs.com/wp-content/uploads/2014/11/Corona-Icon.png",
		})
		printTable(response)

	-- Request the current logged in user's info
	elseif fbCommand == GET_USER_INFO then
		-- Issue the FB request
		local response = facebook.request("me")
		printTable(response)
		-- facebook.request("me/friends") -- Alternate request

	-- This code posts a photo image to your Facebook Wall
	elseif fbCommand == POST_PHOTO then
		local attachment =
		{
			name = "Developing a Facebook Connect app using the Corona SDK!",
			link = "http://www.coronalabs.com/links/forum",
			caption = "Link caption",
			description = "Corona SDK for developing iOS and Android apps with the same code base.",
			picture = "http://www.coronalabs.com/links/demo/Corona90x90.png",
			actions = json.encode({{ name = "Learn More", link = "http://coronalabs.com"}})
		}

		-- Issue the FB request
		local response = facebook.request("me/feed", "POST", attachment) -- posting the photo
		printTable(response)

		--[[
		local attachment =
		{
			message = "Testing...",
			baseDir = system.ResourceDirectory,
			filename = "fbButton184.png",
			type = "image"
		}

		-- Issue the FB request
		local response = facebook.request("me/photos", "POST", attachment) -- posting the photo
		printTable(response)
		]]

	-- This code posts a message to your Facebook Wall
	elseif fbCommand == POST_MSG then
		local time = os.date("*t")
		local postMsg =
		{
			message = "Posting from Corona SDK! " ..
				os.date("%A, %B %e") .. ", " .. time.hour .. ":" .. time.min .. "." .. time.sec
		}

		-- Issue the FB request
		local response = facebook.request("me/feed", "POST", postMsg) -- posting the message
		printTable(response)
	end
end

-- New Facebook Connection listener
local function listener(event)
	print("Facebook Listener events:")

	-- Debug Event parameters printout
	for k, v in pairs(event) do
		print("\t" .. tostring(k) .. ": " .. tostring(v))
	end

	-- Process the response to the FB command
	-- Note: If the app is already logged in, we will still get a "login" phase

	-- Session type
	if event.type == "session" then
		print("Session Status: " .. event.phase)

		-- event.phase is one of: "login", "loginFailed", "loginCancelled", "logout"
		statusMessage.textObject.text = event.phase

		-- If the event phase isn't equal to login, then return
		if event.phase ~= "login" then
			-- Exit if login error
			return
		else
			-- Run the desired command
			processFBCommand()
		end

	-- Request type
	elseif "request" == event.type then
		-- event.response is a JSON object from the FB server
		local response = event.response

		-- If there was no error
		if not event.isError then
			print("Facebook Command: " .. fbCommand)
			-- Decode the response
			response = json.decode(event.response)

			-- Get user info command
			if fbCommand == GET_USER_INFO then
				statusMessage.textObject.text = response.name
				printTable(response, "User Info", 3)
				print("name", response.name)

			-- Post photo command
			elseif fbCommand == POST_PHOTO then
				printTable(response, "photo", 3)
				statusMessage.textObject.text = "Photo Posted"

			-- Post message command
			elseif fbCommand == POST_MSG then
				printTable(response, "message", 3)
				statusMessage.textObject.text = "Message Posted"

			-- Unkown command
			else
				-- Unknown command response
				print("Unknown command response")
				statusMessage.textObject.text = "Unknown ?"
			end
		-- Post failed
		else
			-- Post Failed
			statusMessage.textObject.text = "Post failed"
			printTable(event.response, "Post Failed Response", 3)
		end

	-- Dialog type
	elseif event.type == "dialog" then
		-- showDialog response
		print("dialog response:", event.response)
		statusMessage.textObject.text = event.response
	end
end

-- Enforcce FB.login for various states
local function enforceFacebookLogin()
	if facebook.isActive then
		-- Get the current access token
		local accessToken = facebook.getCurrentAccessToken()

		-- If the access token is nil
		if accessToken == nil then
			print("Need to log in")
			facebook.login(listener)

		-- If the publish actions permission is nil (not granted)
		elseif accessToken.grantedPermissions["publish_actions"] == nil then
			print("Logged in, but need permissions")
			printTable(accessToken, "Access Token Data")
			facebook.login(listener, {"publish_actions"})

		-- We're already logged in with required permissions
		else
			print("Already logged in with needed permissions")
			printTable(accessToken, "Access Token Data")
			statusMessage.textObject.text = "login"
			processFBCommand()
		end
	else
		print("Please wait for facebook to finish initializing before checking the current access token");
	end
end

--  Buttons Functions

-- Set the current FB command, and optionally call login
local function setFBCommand(command, login)
	-- call the login method of the FB session object, passing in a handler
	-- to be called upon successful login.
	fbCommand = command
	if login then
		enforceFacebookLogin()
	end
end

-- Create Buttons

-- "Login to Facebook" button
local loginButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Login",
	labelColor =
	{
		default = { 255, 255, 255 },
	},
	fontSize = 12,
	onRelease = function(event)
		-- Log the user in
		enforceFacebookLogin()
	end,
}
loginButton.x = centerX
loginButton.y = 140
mainGroup:insert(loginButton)

-- "Post Photo with Facebook" button
local postPhotoButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Post Photo",
	labelColor =
	{
		default = { 255, 255, 255 },
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(POST_PHOTO, true)
	end,
}
postPhotoButton.x = centerX
postPhotoButton.y = loginButton.y + loginButton.height
mainGroup:insert(postPhotoButton)

-- "Post Message with Facebook" button
local postMessageButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Post Msg",
	labelColor =
	{
		default = {1, 1, 1},
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(POST_MSG, true)
	end,
}
postMessageButton.x = centerX
postMessageButton.y = postPhotoButton.y + postPhotoButton.height
mainGroup:insert(postMessageButton)

-- "Show Feed Dialog Info with Facebook" button
local showFeedDialogButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Show Feed Dialog",
	labelColor =
	{
		default = {1, 1, 1},
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(SHOW_FEED_DIALOG, true)
	end,
}
showFeedDialogButton.x = centerX
showFeedDialogButton.y = postMessageButton.y + postMessageButton.height
mainGroup:insert(showFeedDialogButton)

-- "Share Link with Facebook" button
local shareLinkDialogButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Share Link Dialog",
	labelColor =
	{
		default = {1, 1, 1},
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(SHARE_LINK_DIALOG, true)
	end,
}
shareLinkDialogButton.x = centerX
shareLinkDialogButton.y = showFeedDialogButton.y + showFeedDialogButton.height
mainGroup:insert(shareLinkDialogButton)

-- "Get User Info with Facebook" button
local getInfoButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Get User",
	labelColor =
	{
		default = {1, 1, 1},
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(GET_USER_INFO, true)
	end,
}
getInfoButton.x = centerX
getInfoButton.y = shareLinkDialogButton.y + shareLinkDialogButton.height
mainGroup:insert(getInfoButton)

-- "Publish Install with Facebook" button
local publishInstallButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Publish Install",
	labelColor =
	{
		default = {1, 1, 1},
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(PUBLISH_INSTALL, false)
		facebook.publishInstall()
	end,
}
publishInstallButton.x = centerX
publishInstallButton.y = getInfoButton.y + getInfoButton.height
mainGroup:insert(publishInstallButton)

-- "Logout with Facebook" button
local logoutButton = widget.newButton
{
	defaultFile = "fbButton184.png",
	overFile = "fbButtonOver184.png",
	label = "Logout",
	labelColor =
	{
		default = {1, 1, 1},
	},
	fontSize = 12,
	onRelease = function(event)
		setFBCommand(LOGOUT, false)
		facebook.logout()
	end,
}
logoutButton.x = centerX
logoutButton.y = publishInstallButton.y + publishInstallButton.height
mainGroup:insert(logoutButton)
