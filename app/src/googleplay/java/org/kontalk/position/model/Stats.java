package org.kontalk.position.model;

public class Stats{
	private int checkinsCount;
	private int usersCount;
	private int tipCount;

	public void setCheckinsCount(int checkinsCount){
		this.checkinsCount = checkinsCount;
	}

	public int getCheckinsCount(){
		return checkinsCount;
	}

	public void setUsersCount(int usersCount){
		this.usersCount = usersCount;
	}

	public int getUsersCount(){
		return usersCount;
	}

	public void setTipCount(int tipCount){
		this.tipCount = tipCount;
	}

	public int getTipCount(){
		return tipCount;
	}

	@Override
 	public String toString(){
		return 
			"Stats{" + 
			"checkinsCount = '" + checkinsCount + '\'' + 
			",usersCount = '" + usersCount + '\'' + 
			",tipCount = '" + tipCount + '\'' + 
			"}";
		}
}
