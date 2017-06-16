package org.kontalk.position.model;

import java.util.List;

public class HereNow{
	private String summary;
	private int count;
	private List<Object> groups;

	public void setSummary(String summary){
		this.summary = summary;
	}

	public String getSummary(){
		return summary;
	}

	public void setCount(int count){
		this.count = count;
	}

	public int getCount(){
		return count;
	}

	public void setGroups(List<Object> groups){
		this.groups = groups;
	}

	public List<Object> getGroups(){
		return groups;
	}

	@Override
 	public String toString(){
		return 
			"HereNow{" + 
			"summary = '" + summary + '\'' + 
			",count = '" + count + '\'' + 
			",groups = '" + groups + '\'' + 
			"}";
		}
}