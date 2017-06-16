package org.kontalk.position.model;

import java.util.List;

public class Specials{
	private int count;
	private List<Object> items;

	public void setCount(int count){
		this.count = count;
	}

	public int getCount(){
		return count;
	}

	public void setItems(List<Object> items){
		this.items = items;
	}

	public List<Object> getItems(){
		return items;
	}

	@Override
 	public String toString(){
		return 
			"Specials{" + 
			"count = '" + count + '\'' + 
			",items = '" + items + '\'' + 
			"}";
		}
}