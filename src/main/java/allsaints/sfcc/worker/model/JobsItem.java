package allsaints.sfcc.worker.model;

import com.google.gson.annotations.SerializedName;

public class JobsItem{

	@SerializedName("createdAt")
	private String createdAt;

	@SerializedName("metadata")
	private Metadata metadata;

	@SerializedName("links")
	private Links links;

	@SerializedName("message")
	private String message;

	@SerializedName("uuid")
	private String uuid;

	@SerializedName("entity")
	private String entity;

	@SerializedName("status")
	private String status;

	@SerializedName("catalogImportId")
	private String catalogImportId;

	public String getCreatedAt(){
		return createdAt;
	}

	public Metadata getMetadata(){
		return metadata;
	}

	public Links getLinks(){
		return links;
	}

	public String getMessage(){
		return message;
	}

	public String getUuid(){
		return uuid;
	}

	public String getEntity(){
		return entity;
	}

	public String getStatus(){
		return status;
	}

	public String getCatalogImportId(){
		return catalogImportId;
	}
}