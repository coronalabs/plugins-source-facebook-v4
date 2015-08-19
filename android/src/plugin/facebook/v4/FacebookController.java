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
    private static CoronaRuntime sCoronaRuntime;
    // TODO: Add this back in for automatic token refresh.
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
    public static final String DIALOG_CANCELLED_MSG = "Dialog was cancelled by user.";
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

                    AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
                    if (currentAccessToken == null || // Should never happen if login was successful
                            !loginResults.getAccessToken().equals(currentAccessToken)) {
                        Log.v("Corona", "ERROR: " + methodName + ": lost the access token. This " +
                                "could be the result of another thread completing " +
                                "facebook.logout() before this callback was invoked.");
                        return;
                    } else {
                        dispatchLoginFBConnectTask(methodName, FBLoginEvent.Phase.login,
                                currentAccessToken.getToken(),
                                toSecondsFromMilliseconds(
                                        currentAccessToken.getExpires().getTime()));
                    }
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

                    dispatchDialogFBConnectTask(methodName, DIALOG_CANCELLED_MSG, false, true);
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

                    dispatchDialogFBConnectTask(methodName, DIALOG_CANCELLED_MSG, false, true);
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

    // Converts a long in milliseconds to seconds
    private static long toSecondsFromMilliseconds(long timeInMilliseconds) {
        return timeInMilliseconds/1000;
    }

    // Creates a Lua table out of an array of strings.
    // Leaves the Lua table on top of the stack.
    private static int createLuaTableFromStringArray(String[] array) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.createLuaTableFromStringArray()";

        if (array == null) {
            Log.v("Corona", "ERROR: " + methodName + ": cannot create a lua table from a null " +
                    "array! Please pass in a non-null string array.");
            return 0;
        }

        LuaState L = fetchLuaState();
        if (L == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_LUA_STATE_ERR_MSG);
            return 0;
        }

        L.newTable(array.length, 0);
        for (int i = 0; i < array.length; i++) {
            // Push this string to the top of the stack
            L.pushString(array[i]);

            // Assign this string to the table 2nd from the top of the stack.
            // Lua arrays are 1-based so add 1 to index correctly.
            L.rawSet(-2, i + 1);
        }

        // Result is on top of the lua stack.
        return 1;
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

    private static void loginWithOnlyRequiredPermissions() {
        // Grab the method name for error messages:
        String methodName = "FacebookController.loginWithOnlyRequiredPermissions()";

        CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        }
        //Log.d("Corona", "Actually log the user in with requried permissions");
        // The "public_profile" permission as this is expected of facebook.
        // We include the "user_friends" permission by default for legacy reasons.
        LoginManager.getInstance().logInWithReadPermissions(activity,
                Arrays.asList("public_profile", "user_friends"));
        //Log.d("Corona", "<--Leaving facebook login");
    }

    private static void requestPermissions(String permissions[]) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.requestPermissions()";

        if (permissions == null) {
            Log.v("Corona", "ERROR: " + methodName + ": Permissions held by this app" +
                    " are null. Be sure to provide at least an empty permission list" +
                    " to facebook.login() before requesting permissions.");
            return;
        }

        // Remove the permissions we already have access to
        // so that we don't try to get access to them again
        // causing constant flashes on the screen
        //Log.d("Corona", "Scanning permissions list");
        AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
        if (currentAccessToken != null) {
            Set grantedPermissions = currentAccessToken.getPermissions();
            for (int i = 0; i < permissions.length; i++) {
                if (grantedPermissions.contains(permissions[i])) {
                    permissions[i] = null;
                }
            }
        } else if (permissions.length == 0) {
            // They still need to login, but aren't requesting any permissions.
            loginWithOnlyRequiredPermissions();
        } // else { // Need to request all the desired permissions again }

        // Look for permissions to be requested
        List<String> readPermissions = new LinkedList<String>();
        List<String> publishPermissions = new LinkedList<String>();

        //Log.d("Corona", "Found permissions that need to be requested again");
        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i] != null) {
                if (isPublishPermission(permissions[i])) {
                    publishPermissions.add(permissions[i]);
                } else {
                    readPermissions.add(permissions[i]);
                }
                permissions[i] = null;
            }
        }

        // If someone is trying to request additional permissions before
        // doing an initial login, tack on the required read permissions.
        String[] requiredPermissions = {"public_profile", "user_friends"};
        for (int i = 0; i < requiredPermissions.length; i++) {

            // If they haven't requested one of the required permissions and
            // they either aren't logged in, or don't already have this required permission.
            if (!readPermissions.contains(requiredPermissions[i]) &&
                    (currentAccessToken == null ||
                    !currentAccessToken.getPermissions().contains(requiredPermissions[i]))) {
                //Log.d("Corona", "Adding required permission: " + requiredPermissions[i]);
                readPermissions.add(requiredPermissions[i]);
            }
        }

        CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
        if (activity == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
            return;
        }

        // If there are some permissions we haven't requested yet then we request them.
        if (!readPermissions.isEmpty()) {
            // Throw a warning if the user is trying to request
            // read and publish permissions at the same time.
            if (!publishPermissions.isEmpty()) {
                Log.v("Corona", "WARNING: " + methodName + ": cannot process read and publish " +
                        "permissions at the same time. Only the read permissions will be " +
                        "requested.");
            }
            LoginManager.getInstance().logInWithReadPermissions(activity, readPermissions);
        } else if (!publishPermissions.isEmpty()) {
            LoginManager.getInstance().logInWithPublishPermissions(activity, publishPermissions);
        } else if (currentAccessToken == null) {
            // They still need to login, but were a jerk and passed in a permissions array
            // containing only nulls. So login with only required permissions.
            loginWithOnlyRequiredPermissions();
        } else {
            // We've already been granted all these permissions.
            // Return sucessful login phase so Lua can move on.
            //Log.d("Corona", "All Permissions have been granted!");
            dispatchLoginFBConnectTask(methodName, FBLoginEvent.Phase.login,
                    currentAccessToken.getToken(),
                    toSecondsFromMilliseconds(currentAccessToken.getExpires().getTime()));
        }
    }

    private static boolean isShareAction(String action) {
        return  action.equals("feed") ||
                action.equals("link") ||
                action.equals("photo") ||
                action.equals("video") ||
                action.equals("openGraph");
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
                                    //Log.d("Corona", "Checking the type of what's about to get " +
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
     * facebook.getCurrentAccessToken entry point
     */
    public static int facebookGetCurrentAccessToken() {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookGetCurrentAccessToken()";

        LuaState L = fetchLuaState();
        if (L == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_LUA_STATE_ERR_MSG);
            return 0;
        }

        AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();
        if (currentAccessToken != null) {
            // Table of access token data to be returned
            L.newTable(0, 7);

            // Token string - like in fbconnect event
            L.pushString(currentAccessToken.getToken());
            L.setField(-2, "token");

            // Expiration date - like in fbconnect event
            L.pushNumber(toSecondsFromMilliseconds(currentAccessToken.getExpires().getTime()));
            L.setField(-2, "expiration");

            // Refresh date
            L.pushNumber(toSecondsFromMilliseconds(currentAccessToken.getLastRefresh().getTime()));
            L.setField(-2, "lastRefreshed");

            // App Id
            L.pushString(currentAccessToken.getApplicationId());
            L.setField(-2, "appId");

            // User Id
            L.pushString(currentAccessToken.getUserId());
            L.setField(-2, "userId");

            // Granted permissions
            Object[] grantedPermissions = currentAccessToken.getPermissions().toArray();
            if (createLuaTableFromStringArray(Arrays.copyOf(
                    grantedPermissions, grantedPermissions.length, String[].class)) > 0) {

                // Assign the granted permissions table to the access token table,
                // which is now 2nd from the top of the stack.
                L.setField(-2, "grantedPermissions");
            }

            // Declined permissions
            Object[] declinedPermissions =
                    currentAccessToken.getDeclinedPermissions().toArray();
            if (createLuaTableFromStringArray(Arrays.copyOf(
                    declinedPermissions, declinedPermissions.length, String[].class)) > 0) {

                // Assign the declined permissions table to the access token table,
                // which is now 2nd from the top of the stack.
                L.setField(-2, "declinedPermissions");
            }

            // Now our table of access token data is at the top of the stack
        } else {
            // Return nil
            L.pushNil();
        }

        return 1;
    }

    /**
     * facebook.login() entry point
     * @param permissions: An array of permissions to be requested if needed.
     */
    public static void facebookLogin(String permissions[]) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookLogin()";
        //Log.d("Corona", "-->In facebook login");

        if (permissions == null) {
            Log.v("Corona", "ERROR: " + methodName + ": Can't set permissions to null! " +
                    "Be sure to pass in an initialized array of permissions.");
            return;
        } else if (permissions.length == 0) {
            loginWithOnlyRequiredPermissions();
        } else {
            //Log.d("Corona", "Login with extended permissions");
            // We want to request some extended permissions.
            requestPermissions(permissions);
        }
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

    /**
     * facebook.request() entry point
     * @param path: Graph API path
     * @param method: HTTP method to use for the request, "GET" or "POST"
     * @param params: Arguments for Graph API request
     */
    public static void facebookRequest( String path, String method, Hashtable params ) {
        // Grab the method name for error messages:
        String methodName = "FacebookController.facebookRequest()";

        AccessToken currentAccessToken = AccessToken.getCurrentAccessToken();

        if (currentAccessToken != null) {

            // Verify params and environment
            CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
            if (activity == null) {
                Log.v("Corona", "ERROR: " + methodName + NO_ACTIVITY_ERR_MSG);
                return;
            }

            // Figure out what type of request to make
            HttpMethod httpMethod = HttpMethod.valueOf(method);
            if (httpMethod != HttpMethod.GET && httpMethod != HttpMethod.POST) {
                Log.v("Corona", "ERROR: " + methodName + ": only supports " +
                        "HttpMethods GET and POST! Cancelling request.");
                return;
            }

            // Use the most universal method for requests vs Facebook's very-specific request APIs
            GraphRequest myRequest = new GraphRequest(
                    currentAccessToken,
                    path,
                    createFacebookBundle(params),
                    HttpMethod.valueOf(method),
                    new FacebookRequestCallbackListener());

            final GraphRequest finalRequest = myRequest;

            // The facebook documentation says this should only be run on the UI thread
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finalRequest.executeAsync();
                }
            });
        } else {
            // Can't perform a Graph Request without being logged in.
            // TODO: Log the user in, and then retry the same Graph API request.
            Log.v("Corona", "ERROR: " + methodName + ": cannot process a Graph API request " +
                    "without being logged in. Please call facebook.login() before calling " +
                    "facebook.request()." );
        }
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
     * TODO: For sharing photos from deviceSupport loading bitmaps from app - Added in SDK 4+
     * TODO: For sharing photos from deviceCheck if image is 200x200
     * TODO: For sharing photos from device, have it work without FB app,
     *       SharePhoto only works with Facebook app according to:
     *       http://stackoverflow.com/questions/30843786/sharing-photo-using-facebook-sdk-4-2-0
     * TODO: Support batches of photos of each type
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
        } else if (L == null) {
            Log.v("Corona", "ERROR: " + methodName + NO_LUA_STATE_ERR_MSG);
            return;
        }

        final int finalListener = listener;

        // Do UI things on UI thread
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (isShareAction(action)) {
                    // Grab the base share parameters -- those defined in ShareContent.java
                    String contentUrl = params != null ? (String) params.get("link") : null;
                    LinkedList<String> peopleIds = null;
                    Hashtable peopleIdsTable =
                            params != null ? (Hashtable) params.get("peopleIds") : null;
                    if (peopleIdsTable != null) {
                        peopleIds = new LinkedList(peopleIdsTable.values());
                    }
                    String placeId = params != null ? (String) params.get("placeId") : null;
                    String ref = params != null ? (String) params.get("ref") : null;

                    // The link action also supports most of what the feed action used to support
                    // "feed" will present the feed dialog as it was in the old Facebook plugin
                    // "link" uses Facebook's default settings which depends on the presence
                    // of the Facebook app on the device.
                    if (action.equals("link") || action.equals("feed")) {

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
                        if (action.equals("feed")) {
                            // Present the dialog through the old Feed dialog.
                            sShareDialog.show(linkContent, ShareDialog.Mode.FEED);
                        } else {
                            // Presenting the share dialog behaves differently depending on whether the
                            // user has the Facebook app installed on their device or not. With the
                            // Facebook app, things like tagging friends and a location are built-in.
                            // Otherwise, these things aren't built-in.
                            sShareDialog.show(linkContent);
                        }
                    }
                } else if (action.equals("requests") || action.equals("apprequests")) {

                    // Grab game request-specific data
                    String message = params != null ? (String) params.get("message") : null;
                    String to = params != null ? (String) params.get("to") : null;
                    String data = params != null ? (String) params.get("data") : null;
                    String title = params != null ? (String) params.get("title") : null;
                    ActionType actiontype =
                            params != null ? (ActionType) params.get("actionType") : null;
                    String objectid = params != null ? (String) params.get("objectId") : null;
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
