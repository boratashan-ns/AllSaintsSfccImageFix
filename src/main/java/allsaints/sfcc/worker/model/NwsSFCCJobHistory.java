package allsaints.sfcc.worker.model;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class NwsSFCCJobHistory{

	@SerializedName("nextPage")
	private String nextPage;

	@SerializedName("jobs")
	private List<JobsItem> jobs;

	public String getNextPage(){
		return nextPage;
	}

	public List<JobsItem> getJobs(){
		return jobs;
	}
}