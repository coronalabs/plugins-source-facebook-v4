// ----------------------------------------------------------------------------
// 
// IOSFBConnect.h
// Copyright (c) 2013 Corona Labs Inc. All rights reserved.
// 
// Reviewers:
// 		Walter
//
// ----------------------------------------------------------------------------

#ifndef _IOSFBConnect_H__
#define _IOSFBConnect_H__

#include "CoronaLua.h"
#include "FBConnect.h"
#include "FBConnectEvent.h"

#import <UIKit/UIKit.h>
#import <AddressBook/AddressBook.h>
#import <AddressBookUI/AddressBookUI.h>

// ----------------------------------------------------------------------------

// Facebook SDK 3.19
//@class Facebook;
//@class FBSession;
//@class IOSFBConnectDelegate;
@class NSArray;
@class NSError;
@class NSString;
@class NSURL;

@protocol CoronaRuntime;

struct lua_State;

namespace Corona
{

class FBConnectEvent;

// ----------------------------------------------------------------------------

class IOSFBConnect : public FBConnect
{
	public:
		typedef FBConnect Super;
		typedef IOSFBConnect Self;

	public:
		IOSFBConnect( id< CoronaRuntime > runtime );
		virtual ~IOSFBConnect();

	protected:
		bool Initialize( NSString *appId );

	protected:
		// Facebook SDK 4+
		void LoginStateChanged( FBConnectLoginEvent::Phase state, NSError *error ) const;
		void ReauthorizationCompleted( NSError *error ) const;
	// Facebook SDK 3.19
//		void SessionChanged( FBSession *session, int state, NSError *error ) const;
//		void ReauthorizationCompleted( FBSession *session, NSError *error ) const;

	public:
		void Dispatch( const FBConnectEvent& e ) const;

	public:
		virtual void GetCurrentAccessToken( lua_State *L ) const;
		// Facebook SDK 4+
		virtual void Login( const char *permissions[], int numPermissions ) const;
		// Facebook SDK 3.19
		//virtual bool IsAccessDenied() const;
		//virtual void Login( const char *appId, const char *permissions[], int numPermissions ) const;
		virtual void Logout() const;
		// Facebook SDK 4+
		virtual void PublishInstall() const;
		// Facebook SDK 3.19
		//virtual void PublishInstall( const char *appId ) const;
		virtual void Request( lua_State *L, const char *path, const char *httpMethod, int x ) const;
		virtual void ShowDialog( lua_State *L, int index ) const;

	protected:
		// Facebook SDK 4+
		void LoginAsync( NSArray *readPermissions, NSArray *publishPermissions ) const;
		// Facebook SDK 3.19
		//void LoginAsync( NSString *applicationId, NSArray *readPermissions, NSArray *publishPermissions ) const;

	private:
		static void CreateLuaTableFromStringArray(lua_State *L, NSArray* array);
		static bool IsPublishPermission(NSString *permission);
	
	private:
		id< CoronaRuntime > fRuntime;
		// Facebook SDK 3.19
		//mutable FBSession *fSession;
		//mutable Facebook *fFacebook; // Need this to support Dialogs
		//IOSFBConnectDelegate *fFacebookDelegate;
		id fConnectionDelegate;
		mutable bool fHasObserver;
};

// ----------------------------------------------------------------------------

} // namespace Corona

// ----------------------------------------------------------------------------

#endif // _IOSFBConnect_H__
