package io.springbatch.admin.execution.service;

import java.util.Collection;
import java.util.List;

/**
 * service to view and modify an execution plan.
 * 
 * resetOrder allows specification of a jobname, followed by an ordered
 * List of steps (the 'new' order for execution).
 * 
 * this manipulates the job flow in memory and sets for the job to be executed;
 * **LIMITATIONS -> cannot modify a split flow
 * 				 -> cannot modify more than linear execution (can reorder)
 * 
 */
public interface ExecutionPlanService {

	public Collection<String> getStepNames(String jobName);
	
	public void resetOrder(String jobName,List<String> stepNames);
	
}
