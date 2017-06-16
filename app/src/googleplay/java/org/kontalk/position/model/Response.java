package org.kontalk.position.model;

import java.util.List;

public class Response{
	private boolean confident;
	private List<VenuesItem> venues;

	public void setConfident(boolean confident){
		this.confident = confident;
	}

	public boolean isConfident(){
		return confident;
	}

	public void setVenues(List<VenuesItem> venues){
		this.venues = venues;
	}

	public List<VenuesItem> getVenues(){
		return venues;
	}

	@Override
 	public String toString(){
		return 
			"Response{" + 
			"confident = '" + confident + '\'' + 
			",venues = '" + venues + '\'' + 
			"}";
		}
}