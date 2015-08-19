//
//  LuaLoader.java
//  Facebook-v4 Plugin
//
//  Copyright (c) 2015 Corona Labs Inc. All rights reserved.
//

package plugin.facebook.v4;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaLuaEvent;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.ansca.corona.CoronaRuntimeProvider;

import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaType;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.NamedJavaFunction;

import java.lang.IllegalArgumentException;
import java.lang.Override;
import java.util.ArrayList;
import java.util.Hashtable;

import android.util.Log;

public class LuaLoader implements JavaFunction {
	private CoronaRuntime mRuntime;

	private static final String APP_ID_ERR_MSG = ": appId is no longer a required argument." +
			" This argument will be ignored.";

	/**
	 * Creates a new object for displaying banner ads on the CoronaActivity
	 */
	public LuaLoader() {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();

		// Validate.
		if (activity == null) {
			throw new IllegalArgumentException(
					"ERROR: LuaLoader()" + FacebookController.NO_ACTIVITY_ERR_MSG);
		}
	}

	/**
	 * Warning! This method is not called on the main UI thread.
	 */
	@Override
	public int invoke(LuaState L) {
		mRuntime = CoronaRuntimeProvider.getRuntimeByLuaState(L);

		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
				new GetCurrentAccessTokenWrapper(),
				new LoginWrapper(),
				new LogoutWrapper(),
				new PublishInstallWrapper(),
				new RequestWrapper(),
				new SetFBConnectListenerWrapper(),
				new ShowDialogWrapper(),
		};

		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		FacebookController.facebookInit(mRuntime);

		return 1;
	}

	private class GetCurrentAccessTokenWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "getCurrentAccessToken";
		}

		@Override
		public int invoke(LuaState L) {

			// Return the Lua table now atop the stack.
			return FacebookController.facebookGetCurrentAccessToken();
		}
	}

	private class LoginWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "login";
		}
		
		@Override
		public int invoke(LuaState L) {
			String methodName = "facebook." + getName() + "()";
			ArrayList<String> permissions = new ArrayList<String>();

			// Parse args if there are any
			if (L.getTop() != 0) {
				int index = 1;

				LuaType firstArgType = L.type(index);
				if (firstArgType == LuaType.STRING || firstArgType == LuaType.NUMBER) {
					// Warn the user about using deprecated login API
					Log.v("Corona", "WARNING: " + methodName + APP_ID_ERR_MSG);
					// Process the remaining arguments
					index++;
				}

				if (CoronaLua.isListener(L, index, "fbconnect")) {
					//Log.d("Corona", "Found a listener to invoke after login finishes");
					FacebookController.setFBConnectListener(CoronaLua.newRef(L, index));
					index++;
				}

				if (L.type(index) == LuaType.TABLE) {
					int arrayLength = L.length(index);
					for (int i = 1; i <= arrayLength; i++) {
						L.rawGet(index, i);
						permissions.add(L.toString(-1));
						L.pop(1);
					}
				}
			}

			FacebookController.facebookLogin(permissions.toArray(new String[0]));
			return 0;
		}
	}

	private class LogoutWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "logout";
		}

		@Override
		public int invoke(LuaState L) {
			FacebookController.facebookLogout();
			return 0;
		}
	}

	private class PublishInstallWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "publishInstall";
		}

		@Override
		public int invoke(LuaState L) {
			String methodName = "facebook." + getName() + "()";
			if (L.getTop() != 0) {
				// Warn the user about using deprecated login API
				Log.v("Corona", "WARNING: " + methodName + APP_ID_ERR_MSG);
			}
			FacebookController.publishInstall();
			return 0;
		}
	}

	private class RequestWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "request";
		}

		@Override
		public int invoke(LuaState L) {
			int index = 1;

			String path = L.toString(index);
			index++;

			// We default to "GET" if no method is specified.
			// Calling HttpMethod.valueOf(null) has undefined behavior.
			// Doing this prevents that case from occuring within
			// FacebookController.facebookRequest().
			String method = "GET";
			if (L.type(index) == LuaType.STRING) {
				method = L.toString(index);
			}
			index++;

			Hashtable params = null;
			if (L.type(index) == LuaType.TABLE) {
				params = CoronaLua.toHashtable(L, index);
			} else {
				params = new Hashtable();
			}
			index++;

			FacebookController.facebookRequest(path, method, params);

			return 0;
		}
	}

	private class SetFBConnectListenerWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "setFBConnectListener";
		}

		@Override
		public int invoke(LuaState L) {
			String methodName = "facebook." + getName() + "()";
			if (CoronaLua.isListener(L, 1, "fbconnect")) {
				//Log.d("Corona", "Found a FBConnect listener");
				FacebookController.setFBConnectListener(CoronaLua.newRef(L, 1));
			} else {
				Log.v("Corona", "ERROR: " + methodName + ": Please provide a listener.");
			}
			return 0;
		}
	}

	private class ShowDialogWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "showDialog";
		}

		@Override
		public int invoke(LuaState L) {
			String methodName = "facebook." + getName() + "()";
			String action = null;
			Hashtable params = null;

			if (L.isString(1)) {

				action = L.toString(1);

				if (L.type(2) == LuaType.TABLE) {
					params = CoronaLua.toHashtable(L, 2);
				}

				FacebookController.facebookDialog(action, params);

			} else {
				Log.v("Corona", "ERROR: " + methodName + ": Invalid parameters passed to " +
						"facebook.showDialog( action [, params] ).");
			}

			return 0;
		}
	}
}