package io.springbatch.admin.execution.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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
			}//end if
			Field field = job.getClass().getDeclaredField("flow");
			field.setAccessible(true);
			Flow flow = (Flow) field.get(job);
			//now we need to get the states, change, and set back
			resetFlow(flow, stepNames);
		}
		catch (Exception e) {
			logger.error("error mapping new steps",e);
		}
	}

	public void setJobLocator(JobLocator jobLocator) {
		this.jobLocator = jobLocator;
	}

	private void resetFlow(Flow flow,List<String> stepNames) throws Exception {
		resetFlow(flow,stepNames,Boolean.FALSE);
	}
	
	private void resetFlow(Flow flow,List<String> stepNames,boolean split) throws Exception {
		//init
		int changeCount = 0;
		Queue<String> splits = new LinkedList<String>();
		//now we need to get the states, change, and set back
		Collection<State> states = ((SimpleFlow)flow).getStates();
		//get the statetransitions at present
		List<StateTransition> stateTransitions = getStateTransitions(flow);
		//create the name of names to states
		Map<String,State> stateMap = new HashMap<String,State>();
		for (State state : states) {
			if (state instanceof StepState
					|| state instanceof DecisionState
					|| state instanceof SplitState) {
				stateMap.put(state.getName(),state);
				if (state instanceof SplitState) {
					splits.add(state.getName());
				}//end if
			}//end if
		}//end if				
		
		//get a map to 'translate' the name
		Map<String,String> stateNameMap = this.getStepNamesForStates(flow);
		
		//create a transition list --> add all the 'ends' first
		List<StateTransition> newTransitions = new ArrayList<StateTransition>();
		//loop and add
		for (StateTransition transition : stateTransitions) {
			if (transition.isEnd()) {
				newTransitions.add(transition);
			}//end if
		}//end for
		//go through and add this 'level' of steps
		for (int position=0;position<stepNames.size();position++) {
			logger.debug("processing step name {}",stepNames.get(position));			
			//set into the statetransition
			//process if it's a split (to see if the next steps inline are internal to it)
			if ((position+1) == stepNames.size()) {//last one --> just add as an end state
				String current = stepNames.get(position);
				//last one
				if (!split && current != null) {
					logger.debug("adding at last {}",stateMap.get(current));
					newTransitions.add(StateTransition.createEndStateTransition(stateMap.get(current)));
					changeCount++;
				}//end if
			} else {
				//first and more --> get
				String current = stepNames.get(position);
				String next = stepNames.get(position+1);
				//check if the 'next' is in THIS flow
				if (!stateNameMap.containsKey(next)) {
					logger.debug("not in the map {} {}",next,stateNameMap);
					next = null;
				}//end if
				//check if the names exists
				if (!stateMap.containsKey(current)) {
					logger.debug("name not found --> remapping current {}",current);
					current = stateNameMap.get(current);
				}//end if
				if (!stateMap.containsKey(next)) {
					logger.debug("name not found --> remapping next {}",next);
					next = stateNameMap.get(next);
				}//end if
				if (current == null) {
					//can't remap --> possibly inner flow?
					logger.warn("can't remap {} {}",current,next);
					continue;//bypass this setting
				}//end if
				if (next == null && !split) {//NOT in a split --> allow for 'searching'
					int loopCount = position+1;
					//loop until gets the next
					while (true) {
						next = stepNames.get(loopCount);
						if (next != null) {
							break;
						} else if ((loopCount+1) == stepNames.size()) {
							break;//finished the loop --> not found
						}//end if
						loopCount++;
					}//end while
					//check if the 'next' is actually at this 'level'
					if (!stateMap.containsKey(next)) {
						//is NOT at this 'level' continue
						next = null;//reset
					}//end if
				}
				if (current != null && next == null && split) {
					logger.debug("adding at last in a split {}",stateMap.get(current));
					newTransitions.add(StateTransition.createEndStateTransition(stateMap.get(current)));
					changeCount++;
				} else if (changeCount == 0) {//set in the 'first' position
					logger.debug("adding first {} {}",stateMap.get(current),next);
					newTransitions.add(0,StateTransition.createStateTransition(stateMap.get(current), next));
					changeCount++;
				} else {
					//create
					logger.debug("adding {} {}",stateMap.get(current),next);
					newTransitions.add(position,StateTransition.createStateTransition(stateMap.get(current), next));
					changeCount++;
				}//end if
			}//end if
		}//end for
		if (changeCount != stepNames.size()) {
			logger.debug("doesn't match - and do the splits");
			for (State state : stateMap.values()) {
				if (state instanceof SplitState) {
					//get the flow and process
					for (Flow splitFlow : ((SplitState)state).getFlows()) {
						logger.debug("processing flow before {}",((SimpleFlow)flow).getStates());
						this.resetFlow(splitFlow, stepNames,Boolean.TRUE);
						logger.debug("after processing flow {}",((SimpleFlow)flow).getStates());
					}//end for
				}//end if
			}//end for
		}//end if
		//set back
		((SimpleFlow)flow).setStateTransitions(newTransitions);
		((SimpleFlow)flow).afterPropertiesSet();	
		//show
		logger.debug("after [split] {} {}",split,((SimpleFlow)flow).getStates());
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
