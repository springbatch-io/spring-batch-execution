package io.springbatch.admin.execution.test.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;

public class SimpleDecider implements JobExecutionDecider {

	private static final Logger logger = LoggerFactory.getLogger(SimpleDecider.class);
	
	@Override
	public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
		logger.debug("executing the decider");
		return FlowExecutionStatus.COMPLETED;
	}

}
