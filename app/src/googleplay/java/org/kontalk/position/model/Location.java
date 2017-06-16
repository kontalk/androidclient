package org.kontalk.position.model;

import java.util.List;

public class Location{
	private String cc;
	private String country;
	private String address;
	private List<LabeledLatLngsItem> labeledLatLngs;
	private double lng;
	private int distance;
	private List<String> formattedAddress;
	private String city;
	private String postalCode;
	private String state;
	private String crossStreet;
	private double lat;

	public void setCc(String cc){
		this.cc = cc;
	}

	public String getCc(){
		return cc;
	}

	public void setCountry(String country){
		this.country = country;
	}

	public String getCountry(){
		return country;
	}

	public void setAddress(String address){
		this.address = address;
	}

	public String getAddress(){
		return address;
	}

	public void setLabeledLatLngs(List<LabeledLatLngsItem> labeledLatLngs){
		this.labeledLatLngs = labeledLatLngs;
	}

	public List<LabeledLatLngsItem> getLabeledLatLngs(){
		return labeledLatLngs;
	}

	public void setLng(double lng){
		this.lng = lng;
	}

	public double getLng(){
		return lng;
	}

	public void setDistance(int distance){
		this.distance = distance;
	}

	public int getDistance(){
		return distance;
	}

	public void setFormattedAddress(List<String> formattedAddress){
		this.formattedAddress = formattedAddress;
	}

	public List<String> getFormattedAddress(){
		return formattedAddress;
	}

	public void setCity(String city){
		this.city = city;
	}

	public String getCity(){
		return city;
	}

	public void setPostalCode(String postalCode){
		this.postalCode = postalCode;
	}

	public String getPostalCode(){
		return postalCode;
	}

	public void setState(String state){
		this.state = state;
	}

	public String getState(){
		return state;
	}

	public void setCrossStreet(String crossStreet){
		this.crossStreet = crossStreet;
	}

	public String getCrossStreet(){
		return crossStreet;
	}

	public void setLat(double lat){
		this.lat = lat;
	}

	public double getLat(){
		return lat;
	}

	@Override
 	public String toString(){
		return 
			"Location{" + 
			"cc = '" + cc + '\'' + 
			",country = '" + country + '\'' + 
			",address = '" + address + '\'' + 
			",labeledLatLngs = '" + labeledLatLngs + '\'' + 
			",lng = '" + lng + '\'' + 
			",distance = '" + distance + '\'' + 
			",formattedAddress = '" + formattedAddress + '\'' + 
			",city = '" + city + '\'' + 
			",postalCode = '" + postalCode + '\'' + 
			",state = '" + state + '\'' + 
			",crossStreet = '" + crossStreet + '\'' + 
			",lat = '" + lat + '\'' + 
			"}";
		}
}