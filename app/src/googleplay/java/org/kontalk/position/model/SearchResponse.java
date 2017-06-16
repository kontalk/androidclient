package org.kontalk.position.model;

public class SearchResponse{
	private Meta meta;
	private Response response;

	public void setMeta(Meta meta){
		this.meta = meta;
	}

	public Meta getMeta(){
		return meta;
	}

	public void setResponse(Response response){
		this.response = response;
	}

	public Response getResponse(){
		return response;
	}

	@Override
 	public String toString(){
		return 
			"SearchResponse{" + 
			"meta = '" + meta + '\'' + 
			",response = '" + response + '\'' + 
			"}";
		}
}
