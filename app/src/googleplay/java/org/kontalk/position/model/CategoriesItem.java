package org.kontalk.position.model;

public class CategoriesItem{
	private String pluralName;
	private String name;
	private Icon icon;
	private String id;
	private String shortName;
	private boolean primary;

	public void setPluralName(String pluralName){
		this.pluralName = pluralName;
	}

	public String getPluralName(){
		return pluralName;
	}

	public void setName(String name){
		this.name = name;
	}

	public String getName(){
		return name;
	}

	public void setIcon(Icon icon){
		this.icon = icon;
	}

	public Icon getIcon(){
		return icon;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	public void setShortName(String shortName){
		this.shortName = shortName;
	}

	public String getShortName(){
		return shortName;
	}

	public void setPrimary(boolean primary){
		this.primary = primary;
	}

	public boolean isPrimary(){
		return primary;
	}

	@Override
 	public String toString(){
		return 
			"CategoriesItem{" + 
			"pluralName = '" + pluralName + '\'' + 
			",name = '" + name + '\'' + 
			",icon = '" + icon + '\'' + 
			",id = '" + id + '\'' + 
			",shortName = '" + shortName + '\'' + 
			",primary = '" + primary + '\'' + 
			"}";
		}
}
