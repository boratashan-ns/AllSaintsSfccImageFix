package allsaints.sfcc.worker.model;

import com.google.gson.annotations.SerializedName;

public class CatalogExportJobResponse{

	@SerializedName("upload_url")
	private String uploadUrl;

	public String getUploadUrl(){
		return uploadUrl;
	}
}