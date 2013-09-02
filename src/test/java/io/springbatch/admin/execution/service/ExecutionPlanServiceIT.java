package io.springbatch.admin.execution.service;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowJob;
import org.springframework.batch.core.job.flow.State;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.flow.support.StateTransition;
import org.springframework.batch.core.job.flow.support.state.StepState;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath:/META-INF/spring/batch/job-context.xml",
	"classpath:/META-INF/spring/batch/batch-context.xml"})
public class ExecutionPlanServiceIT {

	@Autowired
	private Job job;
	
	@Autowired
	private JobExplorer jobExplorer;
	
	@Autowired
	private JobLauncher jobLauncher;
	
	@Test
	public void test() throws Exception {
		//execute the job
		JobParameters jobParameters = new JobParametersBuilder().addLong("runtime",System.currentTimeMillis()).toJobParameters();
		JobExecution execution = jobLauncher.run(job, jobParameters);
		Thread.sleep(1* 1000);
		//retrieve
		execution = jobExplorer.getJobExecution(execution.getId());
		//show
		for (StepExecution stepExecution :execution.getStepExecutions()) {
			System.out.println(stepExecution.getStepName() + " " + stepExecution.getStartTime() + " " + stepExecution.getEndTime());
		}//end for
		
		//need to retrieve the flow (simpleFlow) and get the transitions from it
		Field field = job.getClass().getDeclaredField("flow");
		field.setAccessible(true);
		Flow flow = (Flow) field.get(job);
		//now we need to get the states, change, and set back
		assertTrue(flow instanceof SimpleFlow);
		Collection<State> states = ((SimpleFlow)flow).getStates();
		//get the statetransitions at present
		field = flow.getClass().getDeclaredField("stateTransitions");
		field.setAccessible(true);
		List<StateTransition> stateTransitions = (List<StateTransition>) field.get(flow);
		//loop and show
		for (StateTransition transition : stateTransitions) {
			System.out.println(transition);
		}//end for
		
		System.out.println(">>>>");
				
		//create a transition map that is step0 -> step2 -> step1 -> step3
		List<StateTransition> newTransitions = new ArrayList<StateTransition>();
		//loop and add
		for (StateTransition transition : stateTransitions) {
			if (transition.isEnd()) {
				newTransitions.add(transition);
			}//end if
		}//end for
//		newTransitions.add(StateTransition.createStateTransition(state, next))
		Map<String,State> stateMap = new HashMap<String,State>();
		for (State state : states) {
//			System.out.println(state);
			if (state instanceof StepState) {
				stateMap.put(state.getName(),state);
			}//end if
		}//end if		
		//add
		newTransitions.add(0,StateTransition.createStateTransition(stateMap.get("simpleJob1.step0"), "simpleJob1.step2"));
		newTransitions.add(StateTransition.createStateTransition(stateMap.get("simpleJob1.step2"), "simpleJob1.step1"));
		newTransitions.add(StateTransition.createStateTransition(stateMap.get("simpleJob1.step1"), "simpleJob1.step3"));
		newTransitions.add(StateTransition.createEndStateTransition(stateMap.get("simpleJob1.step3")));
		System.out.println("new...");
		//show
		for (StateTransition transition : newTransitions) {
			System.out.println(transition);
		}
		//set back
		((SimpleFlow)flow).setStateTransitions(newTransitions);
		((SimpleFlow)flow).afterPropertiesSet();
//		((FlowJob)job).setFlow(flow);
		//inititalize
//		field = job.getClass().getDeclaredField("initialized");
//		field.setAccessible(true);
//		field.set(job, Boolean.FALSE);//reset
		//now execute the job
		
		jobParameters = new JobParametersBuilder().addLong("runtime",System.currentTimeMillis()).toJobParameters();
		execution = jobLauncher.run(job, jobParameters);
		Thread.sleep(1* 1000);
		//retrieve
		execution = jobExplorer.getJobExecution(execution.getId());
		//show
		for (StepExecution stepExecution :execution.getStepExecutions()) {
			System.out.println(stepExecution.getStepName() + " " + stepExecution.getStartTime() + " " + stepExecution.getEndTime());
		}//end for		
	}

}
