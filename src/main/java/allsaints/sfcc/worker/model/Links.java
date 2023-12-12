package allsaints.sfcc.worker.model;

import com.google.gson.annotations.SerializedName;

public class Links{

	@SerializedName("output")
	private String output;

	@SerializedName("input")
	private String input;

	@SerializedName("details")
	private String details;

	@SerializedName("logs")
	private String logs;

	public String getOutput(){
		return output;
	}

	public String getInput(){
		return input;
	}

	public String getDetails(){
		return details;
	}

	public String getLogs(){
		return logs;
	}
}