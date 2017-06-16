package org.kontalk.position.model;

public class Meta{
	private int code;
	private String requestId;

	public void setCode(int code){
		this.code = code;
	}

	public int getCode(){
		return code;
	}

	public void setRequestId(String requestId){
		this.requestId = requestId;
	}

	public String getRequestId(){
		return requestId;
	}

	@Override
 	public String toString(){
		return 
			"Meta{" + 
			"code = '" + code + '\'' + 
			",requestId = '" + requestId + '\'' + 
			"}";
		}
}
