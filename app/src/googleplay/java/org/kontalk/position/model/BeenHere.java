package org.kontalk.position.model;

public class BeenHere{
	private int lastCheckinExpiredAt;

	public void setLastCheckinExpiredAt(int lastCheckinExpiredAt){
		this.lastCheckinExpiredAt = lastCheckinExpiredAt;
	}

	public int getLastCheckinExpiredAt(){
		return lastCheckinExpiredAt;
	}

	@Override
 	public String toString(){
		return 
			"BeenHere{" + 
			"lastCheckinExpiredAt = '" + lastCheckinExpiredAt + '\'' + 
			"}";
		}
}
