//
//  FBRequestEvent.java
//  Facebook-v4 Plugin
//
//  Copyright (c) 2015 Corona Labs Inc. All rights reserved.
//

package plugin.facebook.v4;

import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaLuaEvent;
import com.ansca.corona.CoronaRuntime;

import com.naef.jnlua.LuaState;

public class FBRequestEvent extends FBBaseEvent {

	private boolean mDidComplete;

	public FBRequestEvent(String response, boolean isError, boolean didComplete) {
		super(FBType.request, response, isError);
		mDidComplete = didComplete;
	}

	public void executeUsing(CoronaRuntime runtime) {
		super.executeUsing(runtime);

		LuaState L = runtime.getLuaState();

		L.pushBoolean(mDidComplete);
		L.setField(-2, "didComplete");
	}
}
