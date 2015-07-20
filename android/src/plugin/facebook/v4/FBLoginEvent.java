//
//  FBLoginEvent.java
//  Facebook-v4 Plugin
//
//  Copyright (c) 2015 Corona Labs Inc. All rights reserved.
//

package plugin.facebook.v4;

import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaLuaEvent;
import com.ansca.corona.CoronaRuntime;

import com.naef.jnlua.LuaState;

public class FBLoginEvent extends FBBaseEvent {
	public enum Phase {
		login,
		loginFailed,
		loginCancelled,
		logout
	}

	private long mExpirationTime;
	private String mToken;
	private Phase mPhase;

	public FBLoginEvent(String token, long expirationTime) {
		super(FBType.session);
		mPhase = Phase.login;
		mToken = token;
		mExpirationTime = expirationTime;
	}

	public FBLoginEvent(Phase phase) {
		super(FBType.session);
		mPhase = phase;
		mToken = null;
		mExpirationTime = 0;
	}

	public FBLoginEvent(Phase phase, String errorMessage) {
		super(FBType.session, errorMessage, true);
		mPhase = phase;
		mToken = null;
		mExpirationTime = 0;
	}

	public void executeUsing(CoronaRuntime runtime) {
		super.executeUsing(runtime);

		LuaState L = runtime.getLuaState();

		L.pushString(mPhase.name());
		L.setField(-2, "phase");

		if (mToken != null) {
			L.pushString(mToken);
			L.setField(-2, "token");

			L.pushNumber((new Long(mExpirationTime).doubleValue()));
			L.setField(-2, "expiration");
		}
	}
}
