local Library = require "CoronaLibrary"

-- Create stub library for simulator
local lib = Library:new{ name='plugin.facebook.v4', publisherId='com.coronalabs' }

-- Default implementations
local function defaultFunction()
	print( "WARNING: The '" .. lib.name .. "' library is not available in the Corona Simulator." )
end

lib.login = defaultFunction
lib.logout = defaultFunction
lib.request = defaultFunction
lib.showDialog = defaultFunction
lib.publishInstall = defaultFunction
lib.accessDenied = defaultFunction

-- New Facebook-v4 APIs
lib.currentAccessToken = defaultFunction
lib.isActive = defaultFunction
lib.setFBConnectListener= defaultFunction

-- Return an instance
return lib
