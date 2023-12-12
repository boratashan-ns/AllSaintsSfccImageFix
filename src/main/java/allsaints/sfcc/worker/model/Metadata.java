package allsaints.sfcc.worker.model;

import com.google.gson.annotations.SerializedName;

public class Metadata{

	@SerializedName("execution_id")
	private String executionId;

	@SerializedName("cartridge_version")
	private String cartridgeVersion;

	@SerializedName("job_id")
	private String jobId;

	@SerializedName("site_id")
	private String siteId;

	public String getExecutionId(){
		return executionId;
	}

	public String getCartridgeVersion(){
		return cartridgeVersion;
	}

	public String getJobId(){
		return jobId;
	}

	public String getSiteId(){
		return siteId;
	}
}