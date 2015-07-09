package plugin.facebook.v4;

//TODO: Implement saving of login info via: http://stackoverflow.com/questions/29294015/how-to-check-if-user-is-logged-in-with-fb-sdk-4-0-for-android

import com.ansca.corona.Controller;
import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;

import com.ansca.corona.events.EventManager;

import java.io.File;
import java.lang.Exception;
import java.lang.NullPointerException;
import java.lang.Override;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.naef.jnlua.LuaState;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import com.facebook.HttpMethod;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookException;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.internal.WebDialog;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

// Share dialog
import android.app.AlertDialog;
import com.facebook.share.Sharer;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.model.GameRequestContent.ActionType;
import com.facebook.share.model.GameRequestContent.Filters;
import com.facebook.share.model.ShareContent;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.ShareMedia;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
//import com.facebook.share.model.ShareOpenGraphObject;
//import com.facebook.share.model.ShareOpenGraphAction;
//import com.facebook.share.model.ShareOpenGraphContent;
import com.facebook.share.widget.GameRequestDialog;
import com.facebook.share.widget.ShareDialog;

public class FacebookController {
    private static int mListener;
    private static CallbackManager callbackManager;
    private static AccessTokenTracker accessTokenTracker;
    private static String mPermissions[];
    private static CoronaRuntime mCoronaRuntime;

    // Dialogs
    private static ShareDialog shareDialog;
    private static GameRequestDialog requestDialog;

    private static FacebookCallback<Sharer.Result> shareCallback = new FacebookCallback<Sharer.Result>() {
        @Override
        public void onCancel() {
            Log.d("Corona", "Canceled");
        }

        @Override
        public void onError(FacebookException error) {
            Log.d("Corona", String.format("Error: %s", error.toString()));
            String title = "Error";
            String alertMessage = error.getMessage();
            showResult(title, alertMessage);
        }

        @Override
        public void onSuccess(Sharer.Result result) {
            Log.d("Corona", "Success!");
            if (result.getPostId() != null) {
                String title = "Success";
                String id = result.getPostId();
                String alertMessage = "Post ID: " + id;
                showResult(title, alertMessage);
            }
        }

        private void showResult(String title, String alertMessage) {
            CoronaActivity myActivity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
            if (myActivity == null) {
                Log.d("Corona", "Error: No Corona Activity found");
                return;
            }
            new AlertDialog.Builder(myActivity)
                    .setTitle(title)
                    .setMessage(alertMessage)
                    .setPositiveButton("Ok", null)
                    .show();
        }
    };

    // TODO: Handle these cases in a cleaner manner
    private static FacebookCallback<GameRequestDialog.Result> requestCallback = new FacebookCallback<GameRequestDialog.Result>() {
        public void onSuccess(GameRequestDialog.Result result) {
            Log.d("Corona", "Request Dialog succeeded");
        }

        public void onCancel() {Log.d("Corona", "Request Dialog cancelled");}

        public void onError(FacebookException error) {Log.d("Corona", "Request Dialog errored");}
    };

    // Enforce proper setup of the project, throwing exceptions if setup is incorrect.
    private static void verifySetup(final CoronaActivity activity) {

        // Throw an exception if this application does not have the internet permission.
        // Without it the webdialogs won't show.
        android.content.Context context = CoronaEnvironment.getApplicationContext();
        if (context != null) {
            Log.d("Corona", "Enforce Internet permission");
            context.enforceCallingOrSelfPermission(android.Manifest.permission.INTERNET, null);
        } else {
            // TODO: Throw this exception in a better way
            Log.d("Corona", "Error: Don't have an Application Context");
            throw new IllegalArgumentException("Error: Don't have an Application Context");
        }

        String noFacebookAppIdMessage = "Error: To develop for Facebook Connect, you need to get " +
                "an App ID and App Secret. This is available from Facebook's website.";
        // Ensure the user provided a Facebook App ID.
        // Based on: http://www.coderzheaven.com/2013/10/03/meta-data-android-manifest-accessing-it/
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            String facebookAppId = bundle.getString("com.facebook.sdk.ApplicationId");
            Log.d("Corona", "facebookAppId: " + facebookAppId);
            if (facebookAppId == null) {
                throw new IllegalArgumentException(noFacebookAppIdMessage);
            }
        } catch (NameNotFoundException e) {
            Log.d("Corona", "Failed to load Facebook App ID, NameNotFound: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalArgumentException(noFacebookAppIdMessage);
        } catch (NullPointerException e) {
            Log.d("Corona", "Failed to load Facebook App ID, NullPointer: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalArgumentException(noFacebookAppIdMessage);
        }
    }

    // Initialize the facebook SDK and setup needed dialogs if needed
    private static void verifyInitialization(CoronaActivity activity) {
        if(!FacebookSdk.isInitialized()) {

            verifySetup(activity);

            Log.d("Corona", "Register Activity Result Handler and get requestCode offset for Facebook SDK");
            int requestCodeOffset = activity.registerActivityResultHandler(new FacebookLoginActivityResultHandler(), 100);

            // Initialize the Facebook SDK
            FacebookSdk.sdkInitialize(activity.getApplicationContext(), requestCodeOffset);

            Log.d("Corona", "Create the Callback Manager");
            // Create our callback manager and set up forwarding of login results
            callbackManager = CallbackManager.Factory.create();

            Log.d("Corona", "Create the Share dialog");
            shareDialog = new ShareDialog(activity);
            shareDialog.registerCallback(
                    callbackManager,
                    shareCallback);

            Log.d("Corona", "Create the Request Dialog");
            requestDialog = new GameRequestDialog(activity);
            requestDialog.registerCallback(
                    callbackManager,
                    requestCallback);

        }
    }

    // This was brought up from the Facebook SDK (where it was private)
    // and reimplemented to be more maintainable by Corona in the future
    private static final String PUBLISH_PERMISSION_PREFIX = "publish";
    private static final String MANAGE_PERMISSION_PREFIX = "manage";
    private static final Set<String> OTHER_PUBLISH_PERMISSIONS = getOtherPublishPermissions();

    // This was brought up from the Facebook SDK (where it was private)
    // and reimplemented to be more maintainable by Corona in the future
    private static boolean isPublishPermission(String permission) {
        return permission != null &&
                (permission.startsWith(PUBLISH_PERMISSION_PREFIX) ||
                        permission.startsWith(MANAGE_PERMISSION_PREFIX) ||
                        OTHER_PUBLISH_PERMISSIONS.contains(permission));
    }

    // This was brought up from the Facebook SDK (where it was private)
    // and reimplemented to be more maintainable by Corona in the future
    private static Set<String> getOtherPublishPermissions() {
        HashSet<String> set = new HashSet<String>() {{
            add("ads_management");
            add("create_event");
            add("rsvp_event");
        }};
        return Collections.unmodifiableSet(set);
    }

    public static void facebookLogin(final CoronaRuntime runtime, final int listener, final String permissions[])
    {
        Log.d("Corona", "-->In facebook login");
        CoronaActivity myActivity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
        if (myActivity == null) {
            Log.d("Corona", "Error: No Corona Activity found");
            return;
        }
        else {
            Log.d("Corona", "Got a CoronaActivity");
        }

        Log.d("Corona", "Verifying the Facebook SDK was initialized");
        verifyInitialization(myActivity);
        Log.d("Corona", "Facebook SDK initialized");

        // Assign member variables
        mCoronaRuntime = runtime;
        mListener = listener;
        // Keep reference to our permission set.
        mPermissions = permissions;

//        // Set up access token tracker to handle login events
//        accessTokenTracker = new AccessTokenTracker() {
//            @Override
//            protected void onCurrentAccessTokenChanged(
//                    AccessToken oldAccessToken,
//                    AccessToken currentAccessToken) {
//                Log.d("Corona", "In onCurrentAccessTokenChanged()");
//            }
//        };
                // TODO: Rafector this like the share and request dialog's callbacks
                // Callback registration
                Log.d("Corona", "Register login callback");
                LoginManager.getInstance().registerCallback(callbackManager,
                        new FacebookCallback<LoginResult>() {
                            @Override
                            public void onSuccess(LoginResult loginResults) {
                                // App code
                                Log.d("Corona", "Facebook login succeeded!");

                                // Handle permissions as necessary
                                // TODO: Refactor as a method
                                Log.d("Corona", "Handle permissions");
                                AccessToken myAccessToken = AccessToken.getCurrentAccessToken();
                                if (myAccessToken == null /*|| Session.getActiveSession().isClosed()*/) { // Shouldn't ever happen if login was successful
                                    Log.d("Corona", "No current access token");
                                } else {
                                    //Remove the permissions we already have access to so that we don't try to get access to them again
                                    //causing constant flashes on the screen
                                    Log.d("Corona", "Scanning permissions list");
                                    Set grantedPermissions = myAccessToken.getPermissions();
                                    for (int i = 0; i < permissions.length; i++) {
                                        if (grantedPermissions.contains(permissions[i])) {
                                            permissions[i] = null;
                                        }
                                    }
                                }

                                // The accessToken was successfully created
                                List<String> permissions = new LinkedList<String>();
                                boolean readPermissions = false;

                                // Look for read permissions so we can request them
                                if (mPermissions != null) {
                                    Log.d("Corona", "Found permissions that need to be requested again");
                                    for(int i = 0; i < mPermissions.length; i++) {
                                        if(!isPublishPermission(mPermissions[i]) && mPermissions[i] != null) {
                                            permissions.add(mPermissions[i]);
                                            mPermissions[i] = null;
                                            readPermissions = true;
                                        }
                                    }

                                    // If there are no read permissions then we move on to publish permissions so we can request them
                                    if (permissions.isEmpty()) {
                                        for(int i = 0; i < mPermissions.length; i++) {
                                            if(isPublishPermission(mPermissions[i]) && mPermissions[i] != null) {
                                                permissions.add(mPermissions[i]);
                                                mPermissions[i] = null;
                                            }
                                        }
                                    }
                                }
                                else {
                                    Log.d("Corona", "Don't need to request anymore permissions");
                                }

                                CoronaActivity myActivity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
                                if (myActivity == null) {
                                    Log.d("Corona", "LoginManager.onSuccess() - no Corona Activity");
                                    return;
                                }

                                // If there are some permissions we haven't requested yet then we request them and set this object as the callback so we can request the next set of permissions
                                if (!permissions.isEmpty()) {
                                    // This part is to request additional permissions
                                    if (readPermissions) {
                                        LoginManager.getInstance().logInWithReadPermissions(myActivity, permissions);
                                    } else {
                                        LoginManager.getInstance().logInWithPublishPermissions(myActivity, permissions);
                                    }

                                    // Since we're still requesting permissions then we don't want to go back to the lua side yet
                                    return;
                                }

                                // When we reach here we're done with requesting permissions so we can go back to the lua side
                                mCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(
                                        mListener,
                                        FBLoginEvent.Phase.login,
                                        loginResults.getAccessToken().toString(), // TODO: Make this work with actual AccessToken objects
                                        loginResults.getAccessToken().getExpires().getTime()));
                            }

                            @Override
                            public void onCancel() {
                                // App code
                                Log.d("Corona", "Facebook login cancelled!");
                                mCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, FBLoginEvent.Phase.loginCancelled, null, 0));
                            }

                            @Override
                            public void onError(FacebookException exception) {
                                // App code
                                Log.d("Corona", "Facebook login failed with exception:\n" + exception.getLocalizedMessage());
                                mCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, exception.getLocalizedMessage()));
                            }
                        });

        Log.d("Corona", "Actually log the user in");
        LoginManager.getInstance().logInWithReadPermissions(myActivity, Arrays.asList("public_profile", "user_friends"));
        Log.d("Corona", "<--Leaving facebook login");
    }

    private static class FacebookLoginActivityResultHandler implements CoronaActivity.OnActivityResultHandler {
        @Override
        public void onHandleActivityResult(CoronaActivity activity, int requestCode, int resultCode, Intent data)
        {
            Log.d("Corona", "In Activity Result Handler");

            if (callbackManager != null) {
                Log.d("Corona", "Invoking callbackManager.onActivityResult()");
                callbackManager.onActivityResult(requestCode, resultCode, data);
            }
            else {
                Log.d("Corona", "Error: Callback manager isn't initialized");
            }
        }
    }

    public static void facebookLogout()
    {
        Log.d("Corona", "Log the user out");
        LoginManager.getInstance().logOut();
        Log.d("Corona", "user is logged out, so stop tracking their access token");
        mCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, FBLoginEvent.Phase.logout, null, 0));
//        accessTokenTracker.stopTracking();
    }
    //
    public static void facebookRequest( CoronaRuntime runtime, String path, String method, Hashtable params )
    {
		CoronaActivity myActivity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
		if (myActivity == null) {
			return;
		}

        verifyInitialization(myActivity);

        // Figure out what type of request to make
        //Log.d("Corona", "Result of HttpMethod.valueOf(method): " + HttpMethod.valueOf(method));
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        if (httpMethod != HttpMethod.GET && httpMethod != HttpMethod.POST) {
            Log.d("Corona", "FacebookController.facebookRequest() only supports HttpMethods GET and POST. Cancelling request.");
            return;
        }
        // Use the most universal method for requests vs Facebook's very-specific request APIs
        GraphRequest myRequest = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                path,
                createFacebookBundle(params),
                HttpMethod.valueOf(method),
                new FacebookRequestCallbackListener(runtime));

		final GraphRequest finalRequest = myRequest;

		// The facebook documentation says this should only be run on the UI thread
		myActivity.runOnUiThread( new Runnable() {
			@Override
			public void run() {
				finalRequest.executeAsync();
			}
		});
    }

	private static class FacebookRequestCallbackListener implements GraphRequest.Callback
	{
		CoronaRuntime fCoronaRuntime;

		FacebookRequestCallbackListener(CoronaRuntime runtime) {
			fCoronaRuntime = runtime;
		}

		@Override
		public void onCompleted(GraphResponse response)
		{
            Log.d("Corona", "In onComplete after initiating a GraphRequest");
            if (fCoronaRuntime.isRunning() && response != null) {
                if (response.getError() != null) {
                    fCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, response.getError().getErrorMessage(), true));
                } else {
                    String message = "";

                    if (response.getJSONObject() != null &&
                            response.getJSONObject().toString() != null) {

                        message = response.getJSONObject().toString();
                    }
                    fCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, message, false));
                }

            }
		}
	}
// TODO: Port relevant parts of this to share and request dialog callbacks
// Facebook SDK 3.19
//	private static class FacebookWebDialogOnCompleteListener implements WebDialog.OnCompleteListener
//	{
//		CoronaRuntime fCoronaRuntime;
//
//		FacebookWebDialogOnCompleteListener(CoronaRuntime runtime) {
//			fCoronaRuntime = runtime;
//		}
//
//		public void onComplete(Bundle bundle, FacebookException error)
//		{
//			if (fCoronaRuntime.isRunning()) {
//				if (error == null) {
//					Uri.Builder builder = new Uri.Builder();
//					builder.authority("success");
//					builder.scheme("fbconnect");
//					for(String bundleKey : bundle.keySet()) {
//						String value = bundle.getString(bundleKey);
//						value = value == null ? "" : value;
//						builder.appendQueryParameter(bundleKey, value);
//					}
//
//					fCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, builder.build().toString(), false, true));
//				} else {
//					fCoronaRuntime.getTaskDispatcher().send(new FBConnectTask(mListener, error.getLocalizedMessage(), true, false));
//				}
//			}
//		}
//	}
//
    public static void facebookDialog( final CoronaRuntime runtime, final android.content.Context context, final String action, final Hashtable params )
    {
        final CoronaActivity myActivity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
        if (myActivity == null) {
            return;
        }
        verifyInitialization(myActivity);

		//This is out here so that the listener won't disappear while on the other thread
		int listener = -1;
		if (runtime != null) {
			LuaState L = runtime.getLuaState();
			if (L != null && CoronaLua.isListener(L, -1, "")) {
				listener = CoronaLua.newRef(L, -1);
			}
		}

		final int finalListener = listener;

		Handler myHandler = new Handler(Looper.getMainLooper());

		myHandler.post( new Runnable() {
			@Override
			public void run() {
                // TODO: Refactor the parts of the base ShareContent class out so it's not repeated
                // TODO: Support loading bitmaps from app - Added in SDK 4+
                // TODO: Check if image is 200x200
                // TODO: Have this work without Facebook app. SharePhoto only works with Facebook app according to:
                // http://stackoverflow.com/questions/30843786/sharing-photo-using-facebook-sdk-4-2-0
                // TODO: Support batches of photos of each type
                // TODO: null check params cleanly

                // Grab the base share parameters -- those defined in ShareContent.java
                String contentUrl = params != null ? (String)params.get("link") : null;
                List<String> peopleIds = params != null ? (List<String>)params.get("peopleIds") : null;
                String placeId = params != null ? (String)params.get("placeId") : null;
                String ref = params != null ? (String)params.get("ref") : null;
                // The link action also supports most of what the feed action used to support
                // So we'll treat link and feed the same as we open the share dialog in feed mode as is.
                if (action.equals("link") || action.equals("feed")) {

                    // Validate data
                    // Get the Uris that we can parse
                    Uri linkUri = null;
                    if (contentUrl != null) {
                        linkUri = Uri.parse(contentUrl);
                    } else {
                        // TODO: Throw clean error and return
                    }
                    String photoUrl = (String)params.get("picture");
                    Uri photoUri = null;
                    if (photoUrl != null) {
                        photoUri = Uri.parse(photoUrl);
                    }

                    // Set up the dialog to share this link
                    ShareLinkContent linkContent = new ShareLinkContent.Builder()
                            .setContentDescription((String)params.get("description"))
                            .setContentTitle((String)params.get("name"))
                            .setImageUrl(photoUri)
                            .setContentUrl(linkUri)
                            .setPeopleIds(peopleIds)
                            .setPlaceId(placeId)
                            .setRef(ref)
                            .build();

                    shareDialog.show(linkContent, ShareDialog.Mode.FEED);
                } else if (action.equals("requests") || action.equals("apprequests")) {
                    // Create a game request dialog
                    // ONLY WORKS IF YOUR APP IS CATEGORIZED AS A GAME IN FACEBOOK DEV PORTAL
                    // TODO: ENSURE DOCUMENTATION MENTIONS THIS
                    GameRequestContent requestContent = new GameRequestContent.Builder()
                            .setMessage((String)params.get("message"))
                            .setTo((String) params.get("to"))
                            .setData((String) params.get("data"))
                            .setTitle((String) params.get("title"))
                            .setActionType((ActionType) params.get("actiontype"))
                            .setObjectId((String) params.get("objectid"))
                            .setFilters((Filters) params.get("filters"))
                            .setSuggestions((ArrayList<String>)params.get("suggestions"))
                            .build();

                    requestDialog.show(requestContent);
                } else if(action.equals("place") || action.equals("friends")) {
                    // There are no facebook dialog for these
					android.content.Intent intent = new android.content.Intent(context, FacebookFragmentActivity.class);
					intent.putExtra(FacebookFragmentActivity.FRAGMENT_NAME, action);
					intent.putExtra(FacebookFragmentActivity.FRAGMENT_LISTENER, finalListener);
					intent.putExtra(FacebookFragmentActivity.FRAGMENT_EXTRAS, createFacebookBundle(params));
					context.startActivity(intent);
                } else {
                    // TODO: Figure out what happens in this case since the WebDialog flow no longer applies.
                    // This would probably be the opengraph case, like this GoT example
//                CoronaActivity myActivity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
//                if (myActivity == null) {
//                    return;
//                }
//                // Create an object
//                ShareOpenGraphObject object = new ShareOpenGraphObject.Builder()
//                        .putString("og:type", "books.book")
//                        .putString("og:title", "A Game of Thrones")
//                        .putString("og:description", "In the frozen wastes to the north of Winterfell, sinister and supernatural forces are mustering.")
//                        .putString("books:isbn", "0-553-57340-3")
//                        .build();
//                // Create an action
//                ShareOpenGraphAction action = new ShareOpenGraphAction.Builder()
//                        .setActionType("books.reads")
//                        .putObject("book", object)
//                        .build();
//                // Create the content
//                ShareOpenGraphContent content = new ShareOpenGraphContent.Builder()
//                        .setPreviewPropertyName("book")
//                        .setAction(action)
//                        .build();
//                shareDialog.show(content);
                }
			}
		});
    }

	protected static Bundle createFacebookBundle( Hashtable map )
	{
		Bundle result = new Bundle();

		if ( null != map ) {
			Hashtable< String, Object > m = (Hashtable< String, Object >)map;
			Set< Map.Entry< String, Object > > s = m.entrySet();
			if ( null != s ) {
				android.content.Context context = com.ansca.corona.CoronaEnvironment.getApplicationContext();
				com.ansca.corona.storage.FileServices fileServices;
				fileServices = new com.ansca.corona.storage.FileServices(context);
				for ( Map.Entry< String, Object > entry : s ) {
					String key = entry.getKey();
					Object value = entry.getValue();

					if (value instanceof File) {
						byte[] bytes = fileServices.getBytesFromFile(((File)value).getPath());
						if (bytes != null) {
							result.putByteArray( key, bytes );
						}
					}
					else if (value instanceof byte[]) {
						result.putByteArray( key, (byte[])value );
					}
					else if (value instanceof String[]) {
						result.putStringArray( key, (String[])value );
					}
					else if (value != null) {
						boolean done = false;
						File f = new File(value.toString());
						if (f.exists()) {
							byte[] bytes = fileServices.getBytesFromFile(f);
							if (bytes != null) {
								result.putByteArray( key, bytes );
								done = true;
							}
						}

						if (!done) {
							result.putString( key, value.toString() );
						}
					}
				}
			}
		}
		return result;
	}

    public static void publishInstall()
    {
        AppEventsLogger.activateApp(CoronaEnvironment.getCoronaActivity());
    }
}
