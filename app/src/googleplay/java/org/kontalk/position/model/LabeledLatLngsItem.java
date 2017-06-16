package org.kontalk.position.model;

public class LabeledLatLngsItem{
	private double lng;
	private String label;
	private double lat;

	public void setLng(double lng){
		this.lng = lng;
	}

	public double getLng(){
		return lng;
	}

	public void setLabel(String label){
		this.label = label;
	}

	public String getLabel(){
		return label;
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
			"LabeledLatLngsItem{" + 
			"lng = '" + lng + '\'' + 
			",label = '" + label + '\'' + 
			",lat = '" + lat + '\'' + 
			"}";
		}
}
