package io.springbatch.admin.execution.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowJob;
import org.springframework.batch.core.job.flow.State;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.flow.support.StateTransition;
import org.springframework.batch.core.job.flow.support.state.DecisionState;
import org.springframework.batch.core.job.flow.support.state.SplitState;
import org.springframework.batch.core.job.flow.support.state.StepState;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.step.StepLocator;
import org.springframework.beans.factory.annotation.Autowired;

public class SimpleExecutionPlanService implements ExecutionPlanService {

	private static final Logger logger = LoggerFactory.getLogger(SimpleExecutionPlanService.class);
	
	@Autowired
	private JobLocator jobLocator;
	
	@Override
	public Collection<String> getStepNames(String jobName) {
		Collection<String> stepNames = null;
		try {
			Job job = getJob(jobName);
			if (job instanceof StepLocator) {
				stepNames = ((StepLocator)job).getStepNames();
			}//end if
		}
		catch (Exception e) {
			logger.error("error getting step name",e);
		}
		return stepNames;
	}

	@Override
	public void resetOrder(String jobName, List<String> stepNames) {
		try {
			//retrieve the job
			Job job = getJob(jobName);
			//check the type
			if (job instanceof FlowJob) {
				//we're good
				Field field = job.getClass().getDeclaredField("flow");
				field.setAccessible(true);
				Flow flow = (Flow) field.get(job);
				//now we need to get the states, change, and set back
//				resetFlow(flow, stepNames);
				changeFlow(flow,stepNames);
			} else {
				logger.error("not a flow job {}",job.getClass().getName());
			}//end if
		}
		catch (Exception e) {
			logger.error("error mapping new steps",e);
		}
	}

	public void setJobLocator(JobLocator jobLocator) {
		this.jobLocator = jobLocator;
	}

	private Flow changeFlow(Flow flow,List<String> stepNames) throws Exception {
		//check
		if (stepNames.isEmpty()) {
			return flow;//nothing to do
		}//end if
		//init
		List<StateTransition> stateTransitions = new ArrayList<StateTransition>();
		List<String> remainderNames = new ArrayList<String>();
		//need to get the 'states' at this 'level'
		Collection<State> states = ((SimpleFlow)flow).getStates();
		Map<String,State> stateMap = new HashMap<String,State>();
		Map<String,String> stateNameMap = new HashMap<String,String>();
		//loop
		for (State state : states) {
			//add
			if (state instanceof StepState
					|| state instanceof DecisionState
					|| state instanceof SplitState) {
				stateMap.put(state.getName(), state);
				//check name
				if (state instanceof StepState) {
					stateNameMap.put(((StepState)state).getStep().getName(),state.getName());
				}//end if
			}//end if
		}//end for
		//now check if the stepNames are at this level and 'reorganize'
		for (int i=0;i<stepNames.size();i++) {
			logger.debug("processing {}",stepNames.get(i));
			String current = stepNames.get(i);
			State state = stateMap.get(current);
			if (state == null) {
				//map
				state = stateMap.get(stateNameMap.get(current));
			}//end if
			//if the state is STILL null --> then not at this level
			if (state == null) {
				logger.debug("adding to remainderNames {}",stepNames.get(i));
				remainderNames.add(stepNames.get(i));
				continue;//continue
			}//end if
			//now get the next
			if ((i+1) == stepNames.size()) {
				//no next --> it's an end
				stateTransitions.add(StateTransition.createEndStateTransition(state));
				logger.debug("added {} at the end",current);
			} else {
				//get 'next'
				String next = stepNames.get(i + 1);//'next' in order
				if (!stateMap.containsKey(next)) {
					//need to 'loop' through the list and find the matching 'next'
					int count = i + 1;
					while (true) {
						next = stepNames.get(count);
						if (stateMap.containsKey(next)) {
							logger.debug("found it at position {}",count);
							break;
						} else if (stateMap.containsKey(stateNameMap.get(next))) {
							next = stateNameMap.get(next);
							logger.debug("found it at position [translated] {}",count);
							break;
						} else if ((count+1) == stepNames.size()) {
							logger.error("got to the end without finding {} in {}",next,stateMap);
							break;
						}//end if
						count++;//increment
					}//end while
				}//end if
				//add in position 0
				if (stateTransitions.isEmpty()) {
					logger.debug("adding {} at the beginning",current);
					stateTransitions.add(0, StateTransition.createStateTransition(state, next));
				} else {
					logger.debug("adding {} at {}",current,i);
					stateTransitions.add(i, StateTransition.createStateTransition(state, next));
				}//end if
			}//end if
		}//end for
		if (remainderNames.isEmpty()) {
			//nothing was added to the next, it's 'done' - clean up the list
			stepNames = new ArrayList<String>();
		} else {
			logger.debug("possible embedded flow --> can't manage as need to rebuild a final object");
			for (State state : stateMap.values()) {
				//check if it's a flow
				if (state instanceof SplitState) {
					Collection<Flow> flows = new ArrayList<Flow>();
					//process the 'flow'
					for (Flow splitflow : ((SplitState)state).getFlows()) {
						if (stepNames != null) {
							flows.add(changeFlow(splitflow, remainderNames));
						} else {
							flows.add(splitflow);
						}//end if
					}//end for
				}//end if
			}//end for
		}//end if
		//before adding endstates
		logger.debug("before end states {}",stateTransitions);
		//now we add all the others
		List<StateTransition> existingTransitions = getStateTransitions(flow);
		for (StateTransition existing : existingTransitions) {
			if (existing.isEnd()) {
				stateTransitions.add(existing);
			}//end if
		}//end for
		logger.debug("after end states {}",stateTransitions);
		//set back
		((SimpleFlow)flow).setStateTransitions(stateTransitions);
		((SimpleFlow)flow).afterPropertiesSet();
		
		//show
		logger.debug("after completing flow {}",((SimpleFlow)flow).getStates());
		for (State state : ((SimpleFlow)flow).getStates()) {
			if (state instanceof SplitState) {
				logger.debug("split state after {} {}",Objects.hashCode(state),((SplitState) state).getFlows());
			}//end if
		}//end for
		//return
		return flow;
	}

	
	@SuppressWarnings("unchecked")
	private List<StateTransition> getStateTransitions(Flow flow) throws Exception {
		//get the statetransitions at present
		Field field = flow.getClass().getDeclaredField("stateTransitions");
		field.setAccessible(true);
		return (List<StateTransition>) field.get(flow);		
	}
	
	private Map<String,String> getStepNamesForStates(Flow flow) throws Exception {
		Map<String,String> stepNameForStates = new HashMap<String,String>();
		//get the states
		Collection<State> states = ((SimpleFlow)flow).getStates();
		//loop and process into the map
		for (State state : states) {
			if (!state.isEndState()
				&& state instanceof StepState) {
				//get the step name
				stepNameForStates.put(((StepState)state).getStep().getName(),state.getName());
			} else if (!state.isEndState()) {
				stepNameForStates.put(state.getName(), state.getName());
			}//end for
		}//end for
		
		//return 
		return stepNameForStates;
	}
	
	protected Job getJob(String jobName) throws Exception {
		try {
			return jobLocator.getJob(jobName);
			//check
		} catch (NoSuchJobException e) {
			logger.error("error finding job {}",jobName);
			throw new Exception(e);
		}
	}
}
