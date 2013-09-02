package io.springbatch.admin.execution.service;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath:/META-INF/spring/batch/job-context.xml",
	"classpath:/META-INF/spring/batch/execution-context.xml",
	"classpath:/META-INF/spring/batch/batch-context.xml"})
public class SimpleExecutionPlanServiceIT {

	@Autowired
	private ExecutionPlanService executionPlanService;
	
	@Autowired
	@Qualifier("simpleJob1")
	private Job job;
	
	@Autowired
	@Qualifier("deciderJob1")
	private Job decisionJob;
	
	@Autowired
	@Qualifier("splitJob1")
	private Job splitJob;	
	
	@Autowired
	private JobExplorer jobExplorer;
	
	@Autowired
	private JobLauncher jobLauncher;	
	
	@Test
	public void testResetOrder() throws Exception {
		executionPlanService.resetOrder(job.getName(), Arrays.asList("simpleJob1.step1","simpleJob1.step0","simpleJob1.step2","simpleJob1.step3"));
		//execute
		//execute the job
		JobParameters jobParameters = new JobParametersBuilder().addLong("runtime",System.currentTimeMillis()).toJobParameters();
		JobExecution execution = jobLauncher.run(job, jobParameters);
		Thread.sleep(1* 1000);
		//retrieve
		execution = jobExplorer.getJobExecution(execution.getId());		
	}
	
	@Test
	public void testResetOrderDecider() throws Exception {
		executionPlanService.resetOrder(decisionJob.getName(), Arrays.asList("deciderJob1.step2","deciderJob1.decider1","deciderJob1.step1"));
		//execute
		//execute the job
		JobParameters jobParameters = new JobParametersBuilder().addLong("runtime",System.currentTimeMillis()).toJobParameters();
		JobExecution execution = jobLauncher.run(decisionJob, jobParameters);
		Thread.sleep(1* 1000);
		//retrieve
		execution = jobExplorer.getJobExecution(execution.getId());		
	}
	
	@Test
	public void testResetOrderSplit() throws Exception {
		executionPlanService.resetOrder(splitJob.getName(), Arrays.asList("splitJob1.split","splitJob1.split.step2","splitJob1.split.step1","splitJob1.step1"));
//		executionPlanService.resetOrder(splitJob.getName(), Arrays.asList("splitJob1.split","splitJob1.step1"));
		//execute
		//execute the job
		JobParameters jobParameters = new JobParametersBuilder().addLong("runtime",System.currentTimeMillis()).toJobParameters();
		JobExecution execution = jobLauncher.run(splitJob, jobParameters);
		Thread.sleep(1* 1000);
		//retrieve
		execution = jobExplorer.getJobExecution(execution.getId());		
	}	
	
	@Test
	public void testBypass() throws Exception {
		executionPlanService.resetOrder(job.getName(), Arrays.asList("simpleJob1.step1","simpleJob1.step2","simpleJob1.step3"));
		//execute
		//execute the job
		JobParameters jobParameters = new JobParametersBuilder().addLong("runtime",System.currentTimeMillis()).toJobParameters();
		JobExecution execution = jobLauncher.run(job, jobParameters);
		Thread.sleep(1* 1000);
		//retrieve
		execution = jobExplorer.getJobExecution(execution.getId());	
		//check
		assertTrue(execution.getStepExecutions().size() == 3);
	}

}
