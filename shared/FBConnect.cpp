// ----------------------------------------------------------------------------
// 
// FBConnect.cpp
// Copyright (c) 2013 Corona Labs Inc. All rights reserved.
// 
// Reviewers:
// 		Walter
//
// ----------------------------------------------------------------------------

#include "FBConnect.h"
#include "FBConnectEvent.h"

#include "CoronaAssert.h"
#include "CoronaEvent.h"
#include "CoronaLog.h"
#include "CoronaLua.h"

#include <string.h>

// ----------------------------------------------------------------------------

namespace Corona
{

FBConnect::FBConnect( )
:	fListener( NULL )
{
}

FBConnect::~FBConnect()
{
	CORONA_ASSERT( NULL == fListener );
}

void
FBConnect::SetListener( lua_State *L, int listenerIndex )
{
	if ( ! CoronaLuaEqualRef( L, fListener, listenerIndex ) )
	{
		CoronaLuaDeleteRef( L, fListener );

		CORONA_ASSERT( FBConnectEvent::IsListener( L, listenerIndex ) );

		fListener = CoronaLuaNewRef( L, listenerIndex );
	}
}

void
FBConnect::Finalize( lua_State *L )
{
	CoronaLuaDeleteRef( L, fListener );
	fListener = NULL;
}
	
/*
void
FBConnect::Login( const char *appId, const char *permissions[], int numPermissions ) const
{
	CORONA_LOG_WARNING( "facebook.login() is not supported on this platform." );
}

void
FBConnect::Logout() const
{
	CORONA_LOG_WARNING( "facebook.logout() is not supported on this platform." );
}

void
FBConnect::Request( lua_State *L, const char *path, const char *httpMethod, int index ) const
{
	CORONA_LOG_WARNING( "facebook.request() is not supported on this platform." );
}

void
FBConnect::ShowDialog( lua_State *L, int index ) const
{
	CORONA_LOG_WARNING( "facebook.showDialog() is not supported on this platform." );
}
*/

// ----------------------------------------------------------------------------

} // namespace Corona

// ----------------------------------------------------------------------------

