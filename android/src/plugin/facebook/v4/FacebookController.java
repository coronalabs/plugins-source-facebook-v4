//
//  FacebookController.java
//  Facebook-v4 Plugin
//
//  Copyright (c) 2015 Corona Labs Inc. All rights reserved.
//

package plugin.facebook.v4;

/*
 * Android classes
 */
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.Manifest.permission;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.net.Uri;
import android.util.Log;

/*
 * Corona classes
 */
import com.ansca.corona.Controller;
import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeProvider;
import com.ansca.corona.CoronaRuntimeTask;
import com.ansca.corona.events.EventManager;
import com.ansca.corona.storage.FileServices;

/*
 * Facebook classes
 */
import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.internal.WebDialog;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.share.Sharer;
import com.facebook.share.model.GameRequestContent;
import com.facebook.share.model.GameRequestContent.ActionType;
import com.facebook.share.model.GameRequestContent.Filters;
import com.facebook.share.model.ShareContent;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.ShareMedia;
//import com.facebook.share.model.ShareOpenGraphAction;
//import com.facebook.share.model.ShareOpenGraphContent;
//import com.facebook.share.model.ShareOpenGraphObject;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.GameRequestDialog;
import com.facebook.share.widget.ShareDialog;

/*
 * Java classes
 */
import java.io.File;
import java.lang.Override;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * JNLua classes
 */
import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaType;

/*
 * JSON classes
 */
import org.json.JSONObject;

public class FacebookController {
    /************************************** Member Variables **************************************/
    private static int sListener;
    private static int sLibRef;
    private static CallbackManager sCallbackManager;
    private static AccessTokenTracker sAccessTokenTracker;
    private static String sPermissions[];
    private static CoronaRuntime sCoronaRuntime;
    // TODO: Add this back in for getGrantedPermissions API
    //private static final AtomicBoolean accessTokenRefreshInProgress = new AtomicBoolean(false);

    // Dialogs
    private static ShareDialog sShareDialog;
    private static GameRequestDialog sRequestDialog;

    // Error messages
    public static final String NO_ACTIVITY_ERR_MSG = ": cannot continue without a CoronaActivity." +
            " User action (hitting the back button) or another thread may have destroyed it.";
    public static final String NO_RUNTIME_ERR_MSG = ": cannot continue without a CoronaRuntime. " +
            "User action or another thread may have destroyed it.";
    public static final String NO_LUA_STATE_ERR_MSG = ": the Lua state has died! Abort!";
    /**********************************************************************************************/
    /*************************************** Callbacks ********************************************/
    /**
     * Login callback
     */
    private static FacebookCallback<LoginResult> loginCallback =
            new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult loginResults) {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.loginCallback.onSuccess()";

                    //Log.d("Corona", "Facebook login succeeded!");

                    // Handle permissions as necessary
                    // TODO: Refactor as a method for requestPermission API
                    //Log.d("Corona", "Handle permissions");
                    AccessToken myAccessToken = AccessToken.getCurrentAccessToken();
                    if (myAccessToken == null) { // Shouldn't ever happen if login was successful
                        Log.v("Corona", "ERROR: " + methodName + ": lost the access token. This " +
                                "could be the result of another thread completing " +
                                "facebook.logout() before this callback was invoked.");
                        return;
                    } else {
                        // Remove the permissions we already have access to
                        // so that we don't try to get access to them again
                        // causing constant flashes on the screen
                        //Log.d("Corona", "Scanning permissions list");
                        Set grantedPermissions = myAccessToken.getPermissions();
                        for (int i = 0; i < sPermissions.length; i++) {
                            if (grantedPermissions.contains(sPermissions[i])) {
                                sPermissions[i] = null;
                            }
                        }
                    }

                    // The accessToken was successfully created
                    List<String> permissionsToRequest = new LinkedList<String>();
                    boolean readPermissions = false;

                    // Look for read permissions so we can request them
                    if (sPermissions != null) {
                        //Log.d("Corona", "Found permissions that need to be requested again");
                        for(int i = 0; i < sPermissions.length; i++) {
                            if(!isPublishPermission(sPermissions[i]) && sPermissions[i] != null) {
                                permissionsToRequest.add(sPermissions[i]);
                                sPermissions[i] = null;
                                readPermissions = true;
                            }
                        }

                        // If there are no read permissions then we move on
                        // to publish permissions so we can request them
                        if (permissionsToRequest.isEmpty()) {
                            for(int i = 0; i < sPermissions.length; i++) {
                                if(isPublishPermission(sPermissions[i])
                                        && sPermissions[i] != null) {
                                    permissionsToRequest.add(sPermissions[i]);
                                    sPermissions[i] = null;
                                }
                            }
                        }
                    }
                    else {
                        Log.v("Corona", "ERROR: " + methodName + ": Permissions held by this app" +
                                " are null. Be sure to provide at least an empty permission list" +
                                " to facebook.login().");
                        return;
                    }

                    CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
                    if (activity == null) {
                        Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
                        return;
                    }

                    // If there are some permissions we haven't requested yet then
                    // we request them and set this object as the callback so we can
                    // request the next set of permissions
                    if (!permissionsToRequest.isEmpty()) {
                        // This part is to request additional permissions
                        if (readPermissions) {
                            LoginManager.getInstance()
                                    .logInWithReadPermissions(activity, permissionsToRequest);
                        } else {
                            LoginManager.getInstance()
                                    .logInWithPublishPermissions(activity, permissionsToRequest);
                        }

                        // Since we're still requesting permissions,
                        // we don't want to go back to the lua side yet
                        return;
                    }

                    dispatchLoginFBConnectTask(methodName, FBLoginEvent.Phase.login,
                            loginResults.getAccessToken().toString(),
                            loginResults.getAccessToken().getExpires().getTime());
                }

                @Override
                public void onCancel() {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.loginCallback.onCancel()";
                    //Log.d("Corona", "Facebook login cancelled!");

                    dispatchLoginFBConnectTask(methodName,
                            FBLoginEvent.Phase.loginCancelled, null, 0);
                }

                @Override
                public void onError(FacebookException exception) {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.loginCallback.onError()";
                    //Log.d("Corona", "Facebook login failed with exception:\n"
                    //        + exception.getLocalizedMessage());

                    dispatchLoginErrorFBConnectTask(methodName, exception.getLocalizedMessage());
                }
            };

    /**
     * Share dialog callback
     */
    private static FacebookCallback<Sharer.Result> shareCallback =
            new FacebookCallback<Sharer.Result>() {
                @Override
                public void onSuccess(Sharer.Result result) {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.shareCallback.onSuccess()";
                    //Log.d("Corona", "Share dialog succeeded!");

                    // Compose response with info about what happened
                    Uri.Builder builder = new Uri.Builder();
                    builder.authority("success");
                    builder.scheme("fbconnect");
                    String postId = result.getPostId();
                    postId = postId == null ? "" : postId;
                    builder.appendQueryParameter("PostID", postId);

                    dispatchDialogFBConnectTask(methodName,
                            builder.build().toString(), false, true);
                }

                @Override
                public void onCancel() {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.shareCallback.onCancel()";
                    //Log.d("Corona", "Share dialog was Canceled");

                    dispatchDialogFBConnectTask(methodName, "Share dialog was" +
                            " cancelled by user", false, true);
                }

                @Override
                public void onError(FacebookException error) {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.shareCallback.onError()";
                    //Log.d("Corona", String.format("Error: %s", error.toString()));

                    dispatchDialogFBConnectTask(methodName,
                            error.getLocalizedMessage(), true, false);
                }
            };

    /**
     * Game Request Dialog callback
     */
    private static FacebookCallback<GameRequestDialog.Result> requestCallback =
            new FacebookCallback<GameRequestDialog.Result>() {
                @Override
                public void onSuccess(GameRequestDialog.Result result) {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.requestCallback.onSuccess()";
                    //Log.d("Corona", "Request Dialog succeeded!");

                    // Compose response with info about what happened
                    Uri.Builder builder = new Uri.Builder();
                    builder.authority("success");
                    builder.scheme("fbconnect");

                    // Request ID
                    String requestId = result.getRequestId();
                    requestId = requestId == null ? "" : requestId;
                    builder.appendQueryParameter("RequestID", requestId);

                    // Request Recipients
                    List<String> requestRecipients = result.getRequestRecipients();
                    for(String recipient : requestRecipients) {
                        recipient = recipient == null ? "" : recipient;
                        builder.appendQueryParameter("Recipient", recipient);
                    }

                    dispatchDialogFBConnectTask(methodName,
                            builder.build().toString(), false, true);
                }

                @Override
                public void onCancel() {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.requestCallback.onCancel()";
                    //Log.d("Corona", "Request dialog cancelled by user");

                    dispatchDialogFBConnectTask(methodName, "Request dialog was" +
                            " cancelled by user", false, true);
                }

                @Override
                public void onError(FacebookException error) {
                    // Grab the method name for error messages:
                    String methodName = "FacebookController.requestCallback.onError()";
                    //Log.d("Corona", String.format("Error: %s", error.toString()));

                    dispatchDialogFBConnectTask(methodName,
                            error.getLocalizedMessage(), true, false);
                }
            };
    /**********************************************************************************************/
    /******************************** Facebook SDK Utilities **************************************/
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
    /**********************************************************************************************/
    /***************************** Facebook-v4 Plugin Utilities ***********************************/
    /**
     * FBConnectTask Wrappers
     */
    private static void dispatchLoginFBConnectTask(String fromMethod, FBLoginEvent.Phase phase,
                                                   String accessToken, long tokenExpiration) {
        // Create local reference to sCoronaRuntime and null check it to guard against
        // the possibility of a seperate thread nulling out sCoronaRuntime, say if the
        // Activity got destroyed.
        CoronaRuntime runtime = sCoronaRuntime;
        if (runtime != null) {
            // When we reach here, we're done with requesting permissions,
            // so we can go back to the lua side
            runtime.getTaskDispatcher().send(new FBConnectTask(
                    sListener, phase, accessToken, tokenExpiration));
        } else {
            Log.v("Corona", "ERROR: " + fromMethod + NO_RUNTIME_ERR_MSG);
            return;
        }
    }

    private static void dispatchLoginErrorFBConnectTask(String fromMethod, String msg) {
        // Create local reference to sCoronaRuntime and null check it to guard against
        // the possibility of a seperate thread nulling out sCoronaRuntime, say if the
        // Activity got destroyed.
        CoronaRuntime runtime = sCoronaRuntime;
        if (runtime != null) {
            runtime.getTaskDispatcher().send(
                    new FBConnectTask(sListener, msg));
        } else {
            Log.v("Corona", "ERROR: " + fromMethod + NO_RUNTIME_ERR_MSG);
            return;
        }
    }

    private static void dispatchDialogFBConnectTask(String fromMethod, String msg,
                                                    boolean isError, boolean didComplete) {
        // Create local reference to sCoronaRuntime and null check it to guard against
        // the possibility of a seperate thread nulling out sCoronaRuntime, say if the
        // Activity got destroyed.
        CoronaRuntime runtime = sCoronaRuntime;
        if (runtime != null) {
            // Send response back to lua
            runtime.getTaskDispatcher().send(new FBConnectTask(
                    sListener, msg, isError, didComplete));
        } else {
            Log.v("Corona", "ERROR: " + fromMethod + NO_RUNTIME_ERR_MSG);
            return;
        }
    }

    /**
     * Other utilities
     */
    // For inner classes, we grab a new reference to the Lua state here as opposed to declaring
    // a final variable containing the Lua State to cover the case of the initial LuaState being
    // closed by something out of our control, like the user destroying the
    // CoronaActivity and then recreating it.
    private static LuaState fetchLuaState() {
        // Grab the method name for error messages:
        String methodName = "FacebookController.fetchLuaState()";

        // Create local reference to sCoronaRuntime and null check it to guard against
        // the possibility of a seperate thread nulling out sCoronaRuntime, say if the
        // Activity got destroyed.
        CoronaRuntime runtime = sCoronaRuntime;
        if (runtime != null) {
            return runtime.getLuaState();
        } else {
            Log.v("Corona", "ERROR: " + methodName + NO_RUNTIME_ERR_MSG);
            return null;
        }
    }

    protected static Bundle createFacebookBundle( Hashtable map ) {
        Bundle result = new Bundle();

        if ( null != map ) {
            Hashtable< String, Object > m = (Hashtable< String, Object >)map;
            Set< Map.Entry< String, Object > > s = m.entrySet();
            if ( null != s ) {
                Context context = CoronaEnvironment.getApplicationContext();
                FileServices fileServices;
                fileServices = new FileServices(context);
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

    // Enforce proper setup of the project, throwing exceptions if setup is incorrect.
    private static void verifySetup(final CoronaActivity activity) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.verifySetup()";

        // Throw an exception if this application does not have the internet permission.
        // Without it the webdialogs won't show.
        if (activity != null) {
            //Log.d("Corona", "Enforce Internet permission");
            activity.enforceCallingOrSelfPermission(permission.INTERNET, null);
        } else {
            //Log.d("Corona", "Error: Don't have an Application Context");
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        }

        final String noFacebookAppIdMessage = "To develop for Facebook Connect, you need to get " +
                "a Facebook App ID and integrate it into your Corona project.";
        // Ensure the user provided a Facebook App ID.
        // Based on: http://www.coderzheaven.com/2013/10/03/meta-data-android-manifest-accessing-it/
        try {
            ApplicationInfo ai = activity.getPackageManager().getApplicationInfo(
                    activity.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            String facebookAppId = bundle.getString("com.facebook.sdk.ApplicationId");
            //Log.d("Corona", "facebookAppId: " + facebookAppId);
            if (facebookAppId == null) {
                activity.getHandler().post( new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog alertDialog = new AlertDialog.Builder(activity)
                                .setTitle("ERROR: Need Facebook App ID")
                                .setMessage(noFacebookAppIdMessage)
                                .setPositiveButton("Get App ID",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // Open Facebook dev portal:
                                                Uri uri = Uri.parse(
                                                        "https://developers.facebook.com/");
                                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                activity.startActivity(intent);

                                                // Close this app
                                                Process.killProcess(Process.myPid());
                                            }
                                        })
                                .setNeutralButton("Integrate in Corona",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                // Open Corona's Integrating Facebook guide:
                                                Uri uri = Uri.parse("https://docs.coronalabs.com" +
                                                        "/guide/social/implementFacebook" +
                                                        "/index.html#facebook");
                                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                activity.startActivity(intent);

                                                // Close the application
                                                Process.killProcess(Process.myPid());
                                            }
                                        })
                                        // Handle the user cancelling the dialog,
                                        // with the back button in particular.
                                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    public void onCancel(DialogInterface dialog) {
                                        // Close the application
                                        Process.killProcess(Process.myPid());
                                    }
                                })
                                .create();
                        alertDialog.setCanceledOnTouchOutside(false);
                        alertDialog.show();
                    }
                });
                // Block this thread since the app shouldn't continue with no Facebook App ID
                while (true) {Thread.yield();}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**********************************************************************************************/
    /********************************** API Implementations ***************************************/
    /**
     * require("plugin.facebook.v4") entry point
     *
     * This will verifySetup and Facebook initialization as well as setup callbacks for everything.
     *
     * @param runtime: The Corona runtime that facebook should interact with.
     *
     * TODO: refreshing accessTokens if needed
     */
    public static void facebookInit(CoronaRuntime runtime) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookInit()";

        //Log.d("Corona", "Begin initializing everything");

        final CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        } else {
            verifySetup(activity);
        }

        if (runtime == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_RUNTIME_ERR_MSG);
            return;
        } else {
            sCoronaRuntime = runtime;
        }

        LuaState L = fetchLuaState();
        if (L == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_LUA_STATE_ERR_MSG);
            return;
        } else {
            // Initialize currentAccessToken field and isActive fields
            sLibRef = CoronaLua.newRef(L, -1);
            L.pushString("");
            L.setField(-2, "currentAccessToken");

            L.pushBoolean(false);
            L.setField(-2, "isActive");

            // TODO: Initialize grantedPermissions field
        }

        final AtomicBoolean finishedFBSDKInit = new AtomicBoolean(false);

        // Initialize Facebook, create dialogs, and register callbacks on UI thread.
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                //Log.d("Corona", "Register Activity Result Handler and get " +
                //        "requestCode offset for Facebook SDK");
                int requestCodeOffset = activity.registerActivityResultHandler(
                        new FacebookActivityResultHandler(), 100);

                // Initialize the Facebook SDK
                FacebookSdk.sdkInitialize(activity.getApplicationContext(), requestCodeOffset);
                //Log.d("Corona", "Facebook SDK initialized");

                //Log.d("Corona", "Create the Callback Manager");
                // Create our callback manager and set up forwarding of login results
                sCallbackManager = CallbackManager.Factory.create();

                // Callback registration
                //Log.d("Corona", "Register login callback");
                LoginManager.getInstance().registerCallback(sCallbackManager, loginCallback);

                //Log.d("Corona", "Create the Share dialog");
                sShareDialog = new ShareDialog(activity);
                sShareDialog.registerCallback(
                        sCallbackManager,
                        shareCallback);

                //Log.d("Corona", "Create the Request Dialog");
                sRequestDialog = new GameRequestDialog(activity);
                sRequestDialog.registerCallback(
                        sCallbackManager,
                        requestCallback);

                // Set up access token tracker to handle login events
                sAccessTokenTracker = new AccessTokenTracker() {

                    @Override
                    protected void onCurrentAccessTokenChanged(
                            AccessToken oldAccessToken,
                            AccessToken currentAccessToken) {

                        final AccessToken newAccessToken = currentAccessToken;
                        CoronaRuntimeTask accessTokenToLuaTask = new CoronaRuntimeTask() {
                            @Override
                            public void executeUsing(CoronaRuntime runtime) {
                                // Grab the method name for error messages:
                                String methodName = "FacebookController.sAccessTokenTracker." +
                                        "onCurrentAccessTokenChanged.accessTokenToLuaTask." +
                                        "executeUsing()";

                                //Log.d("Corona", "In onCurrentAccessTokenChanged()");

                                // We grab a new reference to the Lua state here as opposed to
                                // declaring a final variable containing the Lua State to cover the
                                // case of the initial LuaState being closed by something out of our
                                // control, like the user destroying the CoronaActivity and then
                                // recreating it.
                                LuaState L = fetchLuaState();
                                if (L == null) {
                                    Log.v("Corona", "ERROR: " + methodName
                                            + NO_LUA_STATE_ERR_MSG);
                                    return;
                                } else {
                                    // Push the new access token to Lua
                                    L.rawGet(LuaState.REGISTRYINDEX, sLibRef);
                                    String accessToken = "";
                                    if (newAccessToken != null) {
                                        accessToken = newAccessToken.getToken();
                                    }
                                    L.pushString(accessToken);
                                    //Log.d("Corona", "New Access Token: " + accessToken);
                                    ////Log.d("Corona", "Checking the type of what's about to get " +
                                    //        "pushed to lua, to track down Illegal type that's " +
                                    //        "getting here intermitently.");
                                    //L.checkType(-2, LuaType.TABLE);
                                    L.setField(-2, "currentAccessToken");
                                    L.pop(1);

                                    // TODO: Add this back in for getGrantedPermissions API
                                    //accessTokenRefreshInProgress.set(false);
                                }
                            }
                        };
                        activity.getRuntimeTaskDispatcher().send(accessTokenToLuaTask);
                    }
                };

                finishedFBSDKInit.set(true);
            }
        });

        // Spin lock to wait for the facebook SDK to initialize
        // Do this with Thread.yield() to not slam the CPU for no reason.
        while(!finishedFBSDKInit.get()) {Thread.yield();}

        // Let lua know that facebook has been initialized and give the currentAccessToken.
        L.rawGet(LuaState.REGISTRYINDEX, sLibRef);

        AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
        String accessToken = "";
        if (currentAccessToken != null) {
            accessToken = currentAccessToken.getToken();
        }
        L.pushString(accessToken);
        //Log.d("Corona", "Initial Access Token: " + accessToken);
        L.setField(-2, "currentAccessToken");

        L.pushBoolean(true);
        L.setField(-2, "isActive");
        L.pop(1);
    }

    /**
     * facebook.login() entry point
     * @param permissions: An array of permissions to be requested if needed.
     */
    public static void facebookLogin(final String permissions[]) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookLogin()";
        //Log.d("Corona", "-->In facebook login");

        CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        }

        if (permissions == null) {
            Log.v("Corona", "ERROR: " + methodName + ": Can't set permissions to null! " +
                    "Be sure to pass in an initialized array of permissions.");
            return;
        } else {
            // Keep reference to our permission set.
            sPermissions = permissions;
        }

        //Log.d("Corona", "Actually log the user in");
        // Initially login with the "public_profile" permission as this is expected of facebook.
        // We include the "user_friends" permission by default for legacy reasons.
        LoginManager.getInstance().logInWithReadPermissions(activity,
                Arrays.asList("public_profile", "user_friends"));
        //Log.d("Corona", "<--Leaving facebook login");
    }

    private static class FacebookActivityResultHandler
            implements CoronaActivity.OnActivityResultHandler {
        @Override
        public void onHandleActivityResult(CoronaActivity activity, int requestCode,
                                           int resultCode, Intent data)
        {
            // Grab the method name for error messages:
            String methodName = "FacebookController." +
                    "FacebookActivityResultHandler.onHandleActivityResult()";
            //Log.d("Corona", "In Activity Result Handler");

            if (sCallbackManager != null) {
                //Log.d("Corona", "Invoking sCallbackManager.onActivityResult()");
                sCallbackManager.onActivityResult(requestCode, resultCode, data);
            }
            else {
                Log.v("Corona", "ERROR: " + methodName + ": Facebook's Callback manager isn't " +
                        "initialized. Be sure to initialize the callback manager before the " +
                        "FacebookActivityResultHandler is called.");
                return;
            }
        }
    }

    /**
     * facebook.logout() entry point
     * Logs out of facebook from the app.
     * This will not also log off the facebook app if it's installed.
     * Since login will grab info from the Facebook app if installed,
     * this means that logging back in will grab that info.
     * In the case of the Facebook app being installed, logout won't actually work because of this.
     * Users will need to log out from the Facebook App.
     * TODO: Add this to documentation, and have a way to have
     * facebook.logout() work with Facebook App installed if possible.
     */
    public static void facebookLogout() {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookLogout()";
        //Log.d("Corona", "Log the user out");

        LoginManager.getInstance().logOut();
        //Log.d("Corona", "user is logged out");

        dispatchLoginFBConnectTask(methodName, FBLoginEvent.Phase.logout, null, 0);
    }

    // TODO: Finish Implementing this
//    public static Set getGrantedPermissions() {
//        // Validate
//        CoronaActivity activity = com.ansca.corona.CoronaEnvironment.getCoronaActivity();
//        if (activity == null) {
//            return null;
//        }
//
//        final AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
//        if (currentAccessToken == null) {
//            return null;
//        }
//
//        accessTokenRefreshInProgress.set(true);
//
//        // The facebook documentation says this should only be run on the UI thread
//        activity.runOnUiThread( new Runnable() {
//            @Override
//            public void run() {
//                // Once complete, this will invoke AccessTokenTracker.onAccessTokenChanged()
//                //Log.d("Corona", "Beginning Access Token Refresh in facebook.getGrantedPermissions()");
//                currentAccessToken.refreshCurrentAccessTokenAsync();
//            }
//        });
//
//        // Spin lock to wait for onAccessTokenChanged() to complete
//        // Do this with Thread.yield() to not slam the CPU for no reason.
//        while(accessTokenRefreshInProgress.get()) {Thread.yield();}
//
//        //Log.d("Corona", "Access token refresh complete! Now return the updated list of permissions");
//        return AccessToken.getCurrentAccessToken().getPermissions();
//    }

    /**
     * facebook.request() entry point
     * @param path: Graph API path
     * @param method: HTTP method to use for the request, "GET" or "POST"
     * @param params: Arguments for Graph API request
     */
    public static void facebookRequest( String path, String method, Hashtable params ) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookRequest()";

        // Verify params and environment
        CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        }

        // Figure out what type of request to make
        //Log.d("Corona", "Result of HttpMethod.valueOf(method): " + HttpMethod.valueOf(method));
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        if (httpMethod != HttpMethod.GET && httpMethod != HttpMethod.POST) {
            Log.v("Corona", "ERROR: " + methodName + ": only supports " +
                    "HttpMethods GET and POST! Cancelling request.");
            return;
        }

        // Use the most universal method for requests vs Facebook's very-specific request APIs
        GraphRequest myRequest = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                path,
                createFacebookBundle(params),
                HttpMethod.valueOf(method),
                new FacebookRequestCallbackListener());

        final GraphRequest finalRequest = myRequest;

        // The facebook documentation says this should only be run on the UI thread
        activity.runOnUiThread( new Runnable() {
            @Override
            public void run() {
                finalRequest.executeAsync();
            }
        });
    }

    private static class FacebookRequestCallbackListener implements GraphRequest.Callback {
        @Override
        public void onCompleted(GraphResponse response)
        {
            // Grab the method name for error messages:
            String methodName = "FacebookController.FacebookRequestCallbackListener.onCompleted()";

            // Create local reference to sCoronaRuntime and null check it to guard against
            // the possibility of a seperate thread nulling out sCoronaRuntime, say if the
            // Activity got destroyed.
            CoronaRuntime runtime = sCoronaRuntime;
            if (runtime == null) {
                Log.v("Corona", "ERROR: " + methodName + NO_RUNTIME_ERR_MSG);
                return;
            }

            //Log.d("Corona", "In onComplete after initiating a GraphRequest");
            if (runtime.isRunning() && response != null) {
                //Log.d("Corona", "Composing response to Lua");
                if (response.getError() != null) {
                    //Log.d("Corona", "Send error to lua");
                    runtime.getTaskDispatcher().send(new FBConnectTask(sListener,
                            response.getError().getErrorMessage(), true));
                } else {
                    String message = "";
                    //Log.d("Corona", "Have data to send to lua");
                    if (response.getJSONObject() != null &&
                            response.getJSONObject().toString() != null) {
                        //Log.d("Corona", "Data is valid");
                        message = response.getJSONObject().toString();
                    } else {
                        Log.v("Corona", "ERROR: " + methodName +
                                ": could not parse the response from Facebook!");
                    }
                    //Log.d("Corona", "Send data to Lua");
                    runtime.getTaskDispatcher().send(new FBConnectTask(sListener, message, false));
                }

            } else {
                String runtimeNotRunning = "the corona runtime isn't running!";
                String noFBResponse = "facebook didn't give a response!";
                String reason = !runtime.isRunning() ? runtimeNotRunning : noFBResponse;
                Log.v("Corona", "ERROR: " + methodName +
                        ": could not send a response because " + reason);
            }
        }
    }

    /**
     * facebook.showDialog() entry point
     * @param action: The type of dialog to open
     * @param params: Table of arguments to the desired dialog
     *
     * TODO: Finish new options for showDialog so that "link" actually makes sense here
     * TODO: Refactor the parts of the base ShareContent class out so it's not repeated
     * TODO: For sharing photos from deviceSupport loading bitmaps from app - Added in SDK 4+
     * TODO: For sharing photos from deviceCheck if image is 200x200
     * TODO: For sharing photos from device, have it work without FB app,
     *       SharePhoto only works with Facebook app according to:
     *       http://stackoverflow.com/questions/30843786/sharing-photo-using-facebook-sdk-4-2-0
     * TODO: Support batches of photos of each type
     * TODO: consistent error handling like other APIs
     */
    public static void facebookDialog( final String action, final Hashtable params ) {

        // Grab the method name for error messages:
        final String methodName = "FacebookController.facebookDialog()";

        // Verify params and environment
        final CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        }

        // This is out here so that the listener won't disappear while on the other thread
        int listener = -1;
        LuaState L = fetchLuaState();
        if (L != null && CoronaLua.isListener(L, -1, "")) {
            listener = CoronaLua.newRef(L, -1);
        }
        final int finalListener = listener;

        // Do UI things on UI thread
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // Grab the base share parameters -- those defined in ShareContent.java
                String contentUrl = params != null ? (String) params.get("link") : null;
                List<String> peopleIds =
                        params != null ? (List<String>) params.get("peopleIds") : null;
                String placeId = params != null ? (String) params.get("placeId") : null;
                String ref = params != null ? (String) params.get("ref") : null;
                // The link action also supports most of what the feed action used to support
                // Treat link and feed the same as we open the share dialog in feed mode as is.
                if (/*action.equals("link") || */action.equals("feed")) {

                    // Validate data
                    // Get the Uris that we can parse
                    Uri linkUri = null;
                    if (contentUrl != null) {
                        linkUri = Uri.parse(contentUrl);
                    }

                    String photoUrl = params != null ? (String) params.get("picture") : null;
                    Uri photoUri = null;
                    if (photoUrl != null) {
                        photoUri = Uri.parse(photoUrl);
                    }

                    // Grab remaining link data
                    String description = params != null ? (String) params.get("description") : null;
                    String name = params != null ? (String) params.get("name") : null;

                    // Set up the dialog to share this link
                    ShareLinkContent linkContent = new ShareLinkContent.Builder()
                            .setContentDescription(description)
                            .setContentTitle(name)
                            .setImageUrl(photoUri)
                            .setContentUrl(linkUri)
                            .setPeopleIds(peopleIds)
                            .setPlaceId(placeId)
                            .setRef(ref)
                            .build();

                    sShareDialog.show(linkContent, ShareDialog.Mode.FEED);
                } else if (action.equals("requests") || action.equals("apprequests")) {

                    // Grab game request-specific data
                    String message = params != null ? (String) params.get("message") : null;
                    String to = params != null ? (String) params.get("to") : null;
                    String data = params != null ? (String) params.get("data") : null;
                    String title = params != null ? (String) params.get("title") : null;
                    ActionType actiontype =
                            params != null ? (ActionType) params.get("actiontype") : null;
                    String objectid = params != null ? (String) params.get("objectid") : null;
                    Filters filters = params != null ? (Filters) params.get("filters") : null;
                    ArrayList<String> suggestions =
                            params != null ? (ArrayList<String>) params.get("suggestions") : null;

                    // Create a game request dialog
                    // ONLY WORKS IF YOUR APP IS CATEGORIZED AS A GAME IN FACEBOOK DEV PORTAL
                    GameRequestContent requestContent = new GameRequestContent.Builder()
                            .setMessage(message)
                            .setTo(to)
                            .setData(data)
                            .setTitle(title)
                            .setActionType(actiontype)
                            .setObjectId(objectid)
                            .setFilters(filters)
                            .setSuggestions(suggestions)
                            .build();

                    sRequestDialog.show(requestContent);
                } else if (action.equals("place") || action.equals("friends")) {
                    // There are no facebook dialog for these
                    // Enforce location permission for places
                    if (action.equals("place")) {
                        activity.enforceCallingOrSelfPermission(
                                permission.ACCESS_FINE_LOCATION, null);
                    }
                    Intent intent = new Intent(activity, FacebookFragmentActivity.class);
                    intent.putExtra(FacebookFragmentActivity.FRAGMENT_NAME, action);
                    intent.putExtra(FacebookFragmentActivity.FRAGMENT_LISTENER, finalListener);
                    intent.putExtra(FacebookFragmentActivity.FRAGMENT_EXTRAS,
                            createFacebookBundle(params));
                    activity.startActivity(intent);
                } else {
//            // TODO: Figure out what happens here since the WebDialog flow no longer applies.
//            // This would probably be the opengraph case, like this GoT example
//            // Create an object
//            ShareOpenGraphObject object = new ShareOpenGraphObject.Builder()
//                    .putString("og:type", "books.book")
//                    .putString("og:title", "A Game of Thrones")
//                    .putString("og:description", "In the frozen wastes to the north of " +
//                    "Winterfell, sinister and supernatural forces are mustering.")
//                    .putString("books:isbn", "0-553-57340-3")
//                    .build();
//            // Create an action
//            ShareOpenGraphAction action = new ShareOpenGraphAction.Builder()
//                    .setActionType("books.reads")
//                    .putObject("book", object)
//                    .build();
//            // Create the content
//            ShareOpenGraphContent content = new ShareOpenGraphContent.Builder()
//                    .setPreviewPropertyName("book")
//                    .setAction(action)
//                    .build();
//            sShareDialog.show(content);
                }
            }
        });
    }

    /**
     * facebook.publishInstall() entry point
     */
    public static void publishInstall() {
        AppEventsLogger.activateApp(CoronaEnvironment.getCoronaActivity());
    }

    /**
     * facebook.setFBConnectListener entry point
     * @param listener: This listener to be called from the Facebook-v4 plugin
     */
    public static void setFBConnectListener(int listener) {
        sListener = listener;
    }
}
