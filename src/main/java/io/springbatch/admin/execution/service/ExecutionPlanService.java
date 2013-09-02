package io.springbatch.admin.execution.service;

import java.util.Collection;
import java.util.List;

public interface ExecutionPlanService {

	public Collection<String> getStepNames(String jobName);
	
	public void resetOrder(String jobName,List<String> stepNames);
	
}
