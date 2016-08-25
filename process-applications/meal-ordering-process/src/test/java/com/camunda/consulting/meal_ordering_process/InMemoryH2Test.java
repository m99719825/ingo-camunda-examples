package com.camunda.consulting.meal_ordering_process;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.camunda.consulting.meal_ordering_process.data.MealSelection;
import com.camunda.consulting.meal_ordering_process.data.Participant;
import com.camunda.consulting.meal_ordering_process.data.Training;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;
import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.consulting.process_test_coverage.ProcessTestCoverage;

/**
 * Test case starting an in-memory database-backed Process Engine.
 * 
 * @param <E>
 */
public class InMemoryH2Test<E> {

  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  private static final String PROCESS_DEFINITION_KEY = "MealOrderingProcess";

  private DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy hh:mm");

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  @Before
  public void setup() {
    init(rule.getProcessEngine());
  }

  /**
   * Just tests if the process definition is deployable.
   */
  @Test
  @Deployment(resources = "meal-ordering.bpmn")
  public void testParsingAndDeployment() {
    // nothing is done here, as we just want to check for exceptions during
    // deployment
  }

  @Test
  @Deployment(resources = "meal-ordering.bpmn")
  public void testHappyPath() throws ParseException {
    Participant jakob = new Participant("Jakob Freund", "Jakob.Freund@camunda.com");
    Participant bernd = new Participant("Bernd Rücker", "bernd.Ruecker@camunda.com");
    Participant robert = new Participant("Robert Gimbel", "Robert.gimbel@Camunda.com");
    Participant tobias = new Participant("Tobias Knisch", "tobias.knisch@generail.com");

    List<Participant> participants = Arrays.asList(jakob, bernd, robert, tobias);

    Training training = new Training("cam-ca051 Berlin", dateFormat.parse("05.09.2016 10:00"), dateFormat.parse("07.09.2016 16:00"));

    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey(PROCESS_DEFINITION_KEY,
        withVariables("training", training, "participants", participants));

    // Now: Drive the process by API and assert correct behavior by
    // camunda-bpm-assert
    assertThat(processInstance).isWaitingAt("checkParticipantsUserTask").task().hasCandidateGroup("backoffice");

    complete(task());

    for (String weekday : training.getWeekdays()) {
      
      VariableInstance weekdayVar = runtimeService().createVariableInstanceQuery().variableName("weekday").singleResult();
      assertThat(weekdayVar.getValue()).isEqualTo(weekday);

      assertThat(processInstance).isWaitingAt("selectLocationUserTask").task().hasCandidateGroup("backoffice");

      complete(task());

      assertThat(processInstance).isWaitingAt("selectMealUserTask");

      List<Task> selectMealTasks = taskQuery().processDefinitionKey(PROCESS_DEFINITION_KEY).list();

      assertThat(selectMealTasks).hasSize(4);

      Task taskOfBernd = taskQuery().taskAssignee(bernd.getEmail()).singleResult();
      assertThat(taskOfBernd).isNotNull(); // .hasDueDate(dateFormat.parse("05.09.2016
                                           // 11:00:00"));

      runtimeService().setVariableLocal(taskOfBernd.getExecutionId(), "meal", new MealSelection(bernd.getName(), "Pasta primavera"));
      complete(taskOfBernd);

      assertThat(processInstance).hasVariables("mealSelections");

      Task taskOfJakob = taskQuery().taskAssignee(jakob.getEmail()).singleResult();
      assertThat(taskOfJakob).isNotNull();

      runtimeService().setVariableLocal(taskOfJakob.getExecutionId(), "meal", new MealSelection(jakob.getName(), "Steak medium"));
      complete(taskOfJakob);

      Task taskOfRobert = taskQuery().taskAssignee(robert.getEmail()).singleResult();
      assertThat(taskOfRobert).isNotNull();

      runtimeService().setVariableLocal(taskOfRobert.getExecutionId(), "meal", new MealSelection(robert.getName(), "Wiener Schnitzel"));
      complete(taskOfRobert);

      Task taskOfTobias = taskQuery().taskAssignee(tobias.getEmail()).singleResult();
      assertThat(taskOfTobias).isNotNull();

      runtimeService().setVariableLocal(taskOfTobias.getExecutionId(), "meal", new MealSelection(tobias.getName(), "Salat mit Putenbrust"));
      complete(taskOfTobias);

      assertThat(processInstance).isWaitingAt("reserveAndOrderMealUserTask").task().hasCandidateGroup("backoffice");

      VariableInstance mealsSelectedVariable = runtimeService().createVariableInstanceQuery().variableName("mealSelections").singleResult();
      List<MealSelection> mealsSelected = (List<MealSelection>) mealsSelectedVariable.getValue();
      assertThat(mealsSelected).hasSize(4);
      complete(task());

      assertThat(processInstance).isWaitingAt("printListUserTask").task().hasCandidateGroup("trainer");
      complete(task());

    }

    assertThat(processInstance).isWaitingAt("trainingFinishedTimerEvent");
    execute(job());
    
    assertThat(processInstance).isEnded();
  }
  
  @Test
  public void testIsoFormatting() throws ParseException {
    Training training = new Training("doesntMatter", dateFormat.parse("05.09.2016 10:00"), dateFormat.parse("07.09.2016 16:00"));
    
    assertThat(training.getEndDateAsIso8601()).isEqualTo("2016-09-07T16:00:00");
  }
  
  @Test
  public void getWeekdays() throws ParseException {
    List<String> weekdays = Arrays.asList("Monday", "Tuesday", "Wednesday");
    Training training = new Training("doesntMatter", dateFormat.parse("05.09.2016 10:00"), dateFormat.parse("07.09.2016 16:00"));
    
    assertThat(training.getWeekdays()).isEqualTo(weekdays);
  }

  @After
  public void calculateCoverageForAllTests() throws Exception {
    ProcessTestCoverage.calculate(rule.getProcessEngine());
  }

}