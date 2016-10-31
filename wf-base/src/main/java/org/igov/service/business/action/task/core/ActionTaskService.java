/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.igov.service.business.action.task.core;

import static org.igov.io.fs.FileSystemData.getFiles_PatternPrint;
import static org.igov.util.Tool.sO;

import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import javax.script.ScriptException;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.EngineServices;
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.form.FormData;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.*;
import org.activiti.engine.identity.Group;
import org.activiti.engine.impl.persistence.entity.HistoricFormPropertyEntity;
import org.activiti.engine.impl.util.json.JSONArray;
import org.activiti.engine.impl.util.json.JSONObject;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.Attachment;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.NativeTaskQuery;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskInfo;
import org.activiti.engine.task.TaskInfoQuery;
import org.activiti.engine.task.TaskQuery;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.igov.io.GeneralConfig;
import org.igov.io.db.kv.temp.IBytesDataInmemoryStorage;
import org.igov.io.mail.Mail;
import org.igov.model.action.event.HistoryEvent_Service_StatusType;
import org.igov.model.action.task.core.ProcessDTOCover;
import org.igov.model.action.task.core.TaskAssigneeCover;
import org.igov.model.action.task.core.entity.TaskAssigneeI;
import org.igov.model.flow.FlowSlotTicket;
import org.igov.model.flow.FlowSlotTicketDao;
//import org.igov.service.business.access.BankIDConfig;
import org.igov.service.business.action.event.HistoryEventService;
import org.igov.service.business.action.task.form.QueueDataFormType;
import org.igov.service.exception.CRCInvalidException;
import org.igov.service.exception.CommonServiceException;
import org.igov.service.exception.RecordNotFoundException;
import org.igov.service.exception.TaskAlreadyUnboundException;
import org.igov.util.ToolFS;
import org.igov.util.ToolJS;
import org.igov.util.ToolLuna;
import org.igov.util.JSON.JsonDateTimeSerializer;
import org.igov.util.cache.CachedInvocationBean;
import org.igov.util.cache.SerializableResponseEntity;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author bw
 */
//@Component
@Service
public class ActionTaskService {
    public static final String GET_ALL_TASK_FOR_USER_CACHE = "getAllTaskForUser";
	public static final String GET_ALL_TICKETS_CACHE = "getAllTickets";
	public static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss", Locale.ENGLISH);
    public static final String CANCEL_INFO_FIELD = "sCancelInfo";
    private static final int DEFAULT_REPORT_FIELD_SPLITTER = 59;
    private static final int MILLIS_IN_HOUR = 1000 * 60 * 60;
    
    static final Comparator<FlowSlotTicket> FLOW_SLOT_TICKET_ORDER_CREATE_COMPARATOR = new Comparator<FlowSlotTicket>() {
		public int compare(FlowSlotTicket e1, FlowSlotTicket e2) {
			return e2.getsDateStart().compareTo(e1.getsDateStart());
		}
	};

    private static final Logger LOG = LoggerFactory.getLogger(ActionTaskService.class);
    //@Autowired
    //private BankIDConfig oBankIDConfig;
    //@Autowired
    //private ExceptionCommonController exceptionController;
    //@Autowired
    //private ExceptionCommonController exceptionController;
    @Autowired
    private RuntimeService oRuntimeService;
    /////////////////////////////////////////////////////////////////////////////////////////////////////
    @Autowired
    private TaskService oTaskService;
    //private HistoryService historyService;
    @Autowired
    private HistoryEventService oHistoryEventService;
    //private FormService formService;
    @Autowired
    private Mail oMail;
    //@Autowired
    //private RuntimeService oRuntimeService;
    //@Autowired
    //private TaskService oTaskService;
    @Autowired
    private RepositoryService oRepositoryService;
    @Autowired
    private FormService oFormService;
    @Autowired
    private IBytesDataInmemoryStorage oBytesDataInmemoryStorage;
    @Autowired
    private IdentityService oIdentityService;
    @Autowired
    private HistoryService oHistoryService;
    @Autowired
    private GeneralConfig oGeneralConfig;
    @Autowired
    private FlowSlotTicketDao flowSlotTicketDao;
    @Autowired
    private CachedInvocationBean cachedInvocationBean;


    
    public static String parseEnumValue(String sEnumName) {
        LOG.info("(sEnumName={})", sEnumName);
        String res = StringUtils.defaultString(sEnumName);
        LOG.info("(sEnumName(2)={})", sEnumName);
        if (res.contains("|")) {
            String[] as = sEnumName.split("\\|");
            LOG.info("(as.length - 1={})", (as.length - 1));
            LOG.info("(as={})", as);
            res = as[as.length - 1];
        }
        return res;
    }

    /*@ExceptionHandler({CRCInvalidException.class, EntityNotFoundException.class, RecordNotFoundException.class, TaskAlreadyUnboundException.class})
    @ResponseBody
    public ResponseEntity<String> handleAccessException(Exception e) throws CommonServiceException {
    return exceptionController.catchActivitiRestException(new CommonServiceException(
    ExceptionCommonController.BUSINESS_ERROR_CODE,
    e.getMessage(), e,
    HttpStatus.FORBIDDEN));
    }*/
    public static String parseEnumProperty(FormProperty property) {
        Object oValues = property.getType().getInformation("values");
        if (oValues instanceof Map) {
            Map<String, String> mValue = (Map) oValues;
            LOG.info("(m={})", mValue);
            String sName = property.getValue();
            LOG.info("(sName={})", sName);
            String sValue = mValue.get(sName);
            LOG.info("(sValue={})", sValue);
            return parseEnumValue(sValue);
        } else {
            LOG.error("Cannot parse values for property - {}", property);
            return "";
        }
    }

    public static String parseEnumProperty(FormProperty property, String sName) {
        Object oValues = property.getType().getInformation("values");
        if (oValues instanceof Map) {
            Map<String, String> mValue = (Map) oValues;
            LOG.info("(m={})", mValue);
            LOG.info("(sName={})", sName);
            String sValue = mValue.get(sName);
            LOG.info("(sValue={})", sValue);
            return parseEnumValue(sValue);
        } else {
            LOG.error("Cannot parse values for property - {}", property);
            return "";
        }
    }

    /*public static String createTable_TaskPropertiesBefore(String soData) {
        return createTable_TaskProperties(soData, false);
    }*/
    public static String createTable_TaskProperties(String saField, Boolean bNew) {
        if (saField == null || "[]".equals(saField) || "".equals(saField)) {
            return "";
        }
        //StringBuilder tableStr = new StringBuilder("Поле \t/ Тип \t/ Поточне значення\n");
        
        /*osTable.append("<td>").append("Поле").append("</td>");
        osTable.append("<td>").append("Тип").append("</td>");
        osTable.append("<td>").append("Поточне значення").append("</td>");*/
        JSONObject oFields = new JSONObject("{ \"soData\":" + saField + "}");
        JSONArray aField = oFields.getJSONArray("soData");
        StringBuilder osTable = new StringBuilder();
        
        osTable.append("<style>table.QuestionFields td { border-style: solid;}</style>");
        osTable.append("<table class=\"QuestionFields\">");
        osTable.append("<tr>");
        osTable.append("<td>").append("Поле").append("</td>");
        if(bNew){
            osTable.append("<td>").append("Старе значення").append("</td>");
            osTable.append("<td>").append("Нове значення").append("</td>");
        }else{
            osTable.append("<td>").append("Значення").append("</td>");
        }
        osTable.append("<td>").append("Коментар").append("</td>");
        osTable.append("</tr>");
        for (int i = 0; i < aField.length(); i++) {
            JSONObject oField = aField.getJSONObject(i);
            /*Object oID=oField.opt("id");
            Object oType=oField.opt("type");
            Object oValue=oField.opt("value");
            osTable.append("<tr>");
            osTable.append("<td>").append(oID!=null?oID:"").append("</td>");
            osTable.append("<td>").append(oType!=null?oType:"").append("</td>");
            osTable.append("<td>").append(oValue!=null?oValue:"").append("</td>");*/
        /*
        sID: item.id,
        sName: item.name,
        sType: item.type,
        sValue: item.value,
        sValueNew: "",
        sNotify: $scope.clarifyFields[item.id].text
        */
            Object sName=oField.opt("sName");
            if(sName==null){
                sName = oField.opt("sID");
            }
            if(sName==null){
                sName = oField.opt("id");
            }
            Object oValue=oField.opt("sValue");
            if(oValue==null){
                oValue = oField.opt("value");
            }
            osTable.append("<tr>");
            osTable.append("<td>").append(sName!=null?sName:"").append("</td>");
            if(bNew){
                Object oValueNew=oField.opt("sValueNew");
                osTable.append("<td>").append(oValue!=null?oValue:"").append("</td>");
                osTable.append("<td>").append(oValueNew!=null?oValueNew:"").append("</td>");
                osTable.append("<td>").append((oValueNew+"").equals(oValue+"")?"(Не змінилось)":"(Змінилось)").append("</td>");
            }else{
                Object oNotify=oField.opt("sNotify");
                osTable.append("<td>").append(oValue!=null?oValue:"").append("</td>");
                osTable.append("<td>").append(oNotify!=null?oNotify:"").append("</td>");
            }
            osTable.append("</tr>");
            /*osTable.append(record.opt("id") != null ? record.get("id") : "?")
                    .append(" \t ")
                    .append(record.opt("type") != null ? record.get("type").toString() : "??")
                    .append(" \t ")
                    .append(record.opt("value") != null ? record.get("value").toString() : "")
                    .append(" \n");*/
        }
        osTable.append("</table>");
        return osTable.toString();
    }

    public static String createTable_TaskProperties_Notification(String saField, Boolean bNew) {
        if (saField == null || "[]".equals(saField) || "".equals(saField)) {
            return "";
        }
        String sTableStyle;
        sTableStyle = "<style>table"
                + " { border-collapse: collapse;"
                + " width: 100%;"
                + " max-width: 800px;}"
                + " table td {"
                + " border: 1px solid #ddd;"
                + " text-align:left;"
                + " padding: 4px;"
                + " height:40px;}"
                + " table th {"
                + " background: #65ABD0;"
                + " vertical-align: middle;"
                + " padding: 10px;"
                + " width:200px;"
                + " text-align:left;"
                + " color:#fff;"
                + " }"
                + "</style>";
        //StringBuilder tableStr = new StringBuilder("Поле \t/ Тип \t/ Поточне значення\n");

        /*osTable.append("<td>").append("Поле").append("</td>");
        osTable.append("<td>").append("Тип").append("</td>");
        osTable.append("<td>").append("Поточне значення").append("</td>");*/
        JSONObject oFields = new JSONObject("{ \"soData\":" + saField + "}");
        JSONArray aField = oFields.getJSONArray("soData");
        StringBuilder osTable = new StringBuilder();
        osTable.append(sTableStyle);
        osTable.append("<table>");
        osTable.append("<tr>");
        osTable.append("<th>").append("Поле").append("</th>");
        if(bNew){
            osTable.append("<th>").append("Старе значення").append("</th>");
            osTable.append("<th>").append("Нове значення").append("</th>");
        }else{
            osTable.append("<th>").append("Значення").append("</th>");
        }
        osTable.append("<th>").append("Коментар").append("</th>");
        osTable.append("</tr>");
        for (int i = 0; i < aField.length(); i++) {
            JSONObject oField = aField.getJSONObject(i);
            /*Object oID=oField.opt("id");
            Object oType=oField.opt("type");
            Object oValue=oField.opt("value");
            osTable.append("<tr>");
            osTable.append("<td>").append(oID!=null?oID:"").append("</td>");
            osTable.append("<td>").append(oType!=null?oType:"").append("</td>");
            osTable.append("<td>").append(oValue!=null?oValue:"").append("</td>");*/
        /*
        sID: item.id,
        sName: item.name,
        sType: item.type,
        sValue: item.value,
        sValueNew: "",
        sNotify: $scope.clarifyFields[item.id].text
        */
            Object sName=oField.opt("sName");
            if(sName==null){
                sName = oField.opt("sID");
            }
            if(sName==null){
                sName = oField.opt("id");
            }
            Object oValue=oField.opt("sValue");
            if(oValue==null){
                oValue = oField.opt("value");
            }
            osTable.append("<tr>");
            osTable.append("<td>").append(sName!=null?sName:"").append("</td>");
            if(bNew){
                Object oValueNew=oField.opt("sValueNew");
                osTable.append("<td>").append(oValue!=null?oValue:"").append("</td>");
                osTable.append("<td>").append(oValueNew!=null?oValueNew:"").append("</td>");
                osTable.append("<td>").append((oValueNew+"").equals(oValue+"")?"(Не змінилось)":"(Змінилось)").append("</td>");
            }else{
                Object oNotify=oField.opt("sNotify");
                osTable.append("<td>").append(oValue!=null?oValue:"").append("</td>");
                osTable.append("<td>").append(oNotify!=null?oNotify:"").append("</td>");
            }
            osTable.append("</tr>");
            /*osTable.append(record.opt("id") != null ? record.get("id") : "?")
                    .append(" \t ")
                    .append(record.opt("type") != null ? record.get("type").toString() : "??")
                    .append(" \t ")
                    .append(record.opt("value") != null ? record.get("value").toString() : "")
                    .append(" \n");*/
        }
        osTable.append("</table>");
        return osTable.toString();
    }

    public TaskQuery buildTaskQuery(String sLogin, String bAssigned) {
        TaskQuery taskQuery = oTaskService.createTaskQuery();
        if (bAssigned != null) {
            if (!Boolean.valueOf(bAssigned)) {
                taskQuery.taskUnassigned();
                if (sLogin != null && !sLogin.isEmpty()) {
                    taskQuery.taskCandidateUser(sLogin);
                }
            } else if (sLogin != null && !sLogin.isEmpty()) {
                taskQuery.taskAssignee(sLogin);
            }
        } else {
            if (sLogin != null && !sLogin.isEmpty()) {
                taskQuery.taskCandidateOrAssigned(sLogin);
            }
        }
        return taskQuery;
    }

    public void cancelTasksInternal(Long nID_Order, String sInfo) throws CommonServiceException, CRCInvalidException, RecordNotFoundException, TaskAlreadyUnboundException {
        String nID_Process = getOriginalProcessInstanceId(nID_Order);
        getTasksByProcessInstanceId(nID_Process);
        LOG.info("(nID_Order={},nID_Process={},sInfo={})", nID_Order, nID_Process, sInfo);
        HistoricProcessInstance processInstance = oHistoryService.createHistoricProcessInstanceQuery().processInstanceId(nID_Process).singleResult();
        FormData formData = oFormService.getStartFormData(processInstance.getProcessDefinitionId());
        List<String> asID_Field = AbstractModelTask.getListField_QueueDataFormType(formData);
        LOG.info("asID_Field: " + asID_Field);
        List<String> queueDataList = AbstractModelTask.getVariableValues(oRuntimeService, nID_Process, asID_Field);
        LOG.info("queueDataList: " + queueDataList);
        if (queueDataList.isEmpty()) {
            LOG.error(String.format("Queue data list for Process Instance [id = '%s'] not found", nID_Process));
            throw new RecordNotFoundException("\u041c\u0435\u0442\u0430\u0434\u0430\u043d\u043d\u044b\u0435 \u044d\u043b\u0435\u043a\u0442\u0440\u043e\u043d\u043d\u043e\u0439 \u043e\u0447\u0435\u0440\u0435\u0434\u0438 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b");
        }
        for (String queueData : queueDataList) {
            Map<String, Object> m = QueueDataFormType.parseQueueData(queueData);
            long nID_FlowSlotTicket = QueueDataFormType.get_nID_FlowSlotTicket(m);
            LOG.info("(nID_Order={},nID_FlowSlotTicket={})", nID_Order, nID_FlowSlotTicket);
            if (!flowSlotTicketDao.unbindFromTask(nID_FlowSlotTicket)) {
                throw new TaskAlreadyUnboundException("\u0417\u0430\u044f\u0432\u043a\u0430 \u0443\u0436\u0435 \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430");
            }
        }
        oRuntimeService.setVariable(nID_Process, CANCEL_INFO_FIELD, String.format(
                "[%s] \u0417\u0430\u044f\u0432\u043a\u0430 \u0441\u043a\u0430\u0441\u043e\u0432\u0430\u043d\u0430: %s",
                DateTime.now(), sInfo == null ? "" : sInfo));
    }


    private String addCalculatedFields(String saFieldsCalc, TaskInfo curTask, String currentRow) {
        HistoricTaskInstance details = oHistoryService.createHistoricTaskInstanceQuery().includeProcessVariables().taskId(curTask.getId()).singleResult();
        LOG.info("Process variables of the task {}:{}: {}", curTask.getId(), saFieldsCalc, details.getProcessVariables());
        if (details != null && details.getProcessVariables() != null) {
            Set<String> headersExtra = new HashSet<>();
            for (String key : details.getProcessVariables().keySet()) {
                if (!key.startsWith("sBody")) {
                    headersExtra.add(key);
                }
            }
            saFieldsCalc = StringUtils.substringAfter(saFieldsCalc, "\"");
            saFieldsCalc = StringUtils.substringBeforeLast(saFieldsCalc, "\"");
            for (String expression : saFieldsCalc.split(";")) {
            	LOG.info("Processing expression: {}", expression);
                String variableName = StringUtils.substringBefore(expression, "=");
                String condition = StringUtils.substringAfter(expression, "=");
                LOG.info("Checking variable with (name={}, condition={}, expression={}) ", variableName, condition, expression);
                try {
                    Object conditionResult = getObjectResultofCondition(headersExtra, details, details, condition);
                    currentRow = currentRow + ";" + conditionResult;
                    LOG.info("Adding calculated field {} with the value {}", variableName, conditionResult);
                } catch (Exception oException) {
                    LOG.error("Error: {}, occured while processing (variable={}) ",oException.getMessage(), variableName);
                    LOG.debug("FAIL:", oException);
                }
            }
        }
        return currentRow;
    }

    public ResponseEntity<String> unclaimUserTask(String nID_UserTask) throws CommonServiceException, RecordNotFoundException {
        Task task = oTaskService.createTaskQuery().taskId(nID_UserTask).singleResult();
        if (task == null) {
            throw new RecordNotFoundException();
        }
        if (task.getAssignee() == null || task.getAssignee().isEmpty()) {
            return new ResponseEntity<>("Not assigned UserTask", HttpStatus.OK);
        }
        oTaskService.unclaim(task.getId());
        return new ResponseEntity<>("", HttpStatus.OK);
    }

    /*public void setInfo_ToActiviti(String snID_Process, String saField, String sBody) {
        try {
            LOG.info(String.format("try to set saField=%s and sBody=%s to snID_Process=%s", saField, sBody, snID_Process));
            oRuntimeService.setVariable(snID_Process, "saFieldQuestion", saField);
            oRuntimeService.setVariable(snID_Process, "sQuestion", sBody);
            LOG.info(String.format("completed set saField=%s and sBody=%s to snID_Process=%s", saField, sBody, snID_Process));
        } catch (Exception oException) {
            LOG.error("error: {}, during set variables to Activiti!", oException.getMessage());
        }
    }*/

    public void loadFormPropertiesToMap(FormData formData, Map<String, Object> variables, Map<String, String> formValues) {
        List<FormProperty> aFormProperty = formData.getFormProperties();
        if (!aFormProperty.isEmpty()) {
            for (FormProperty oFormProperty : aFormProperty) {
                String sType = oFormProperty.getType().getName();
                if (variables.containsKey(oFormProperty.getId())) {
                    if ("enum".equals(sType)) {
                        Object variable = variables.get(oFormProperty.getId());
                        if (variable != null) {
                            String sID_Enum = variable.toString();
                            LOG.info("execution.getVariable()(sID_Enum={})", sID_Enum);
                            String sValue = parseEnumProperty(oFormProperty, sID_Enum);
                            formValues.put(oFormProperty.getId(), sValue);
                        }
                    } else {
                        formValues.put(oFormProperty.getId(), variables.get(oFormProperty.getId()) != null ? String.valueOf(variables.get(oFormProperty.getId())) : null);
                    }
                }
            }
        }
    }

    public Date getBeginDate(Date date) {
        if (date == null) {
            return DateTime.now().minusDays(1).toDate();
        }
        return date;
    }

    private Object getObjectResultofCondition(Set<String> headersExtra, HistoricTaskInstance currTask, HistoricTaskInstance details, String condition) throws ScriptException, NoSuchMethodException {
        Map<String, Object> params = new HashMap<>();
        for (String headerExtra : headersExtra) {
            Object variableValue = details.getProcessVariables().get(headerExtra);
            String propertyValue = sO(variableValue);
            params.put(headerExtra, propertyValue);
        }
        params.put("sAssignedLogin", currTask.getAssignee());
        params.put("sID_UserTask", currTask.getTaskDefinitionKey());
        LOG.info("Calculating expression with (params={})", params);
        Object conditionResult = new ToolJS().getObjectResultOfCondition(new HashMap<String, Object>(),
                params, condition);
        LOG.info("Condition of the expression is {}", conditionResult.toString());
        return conditionResult;
    }

    public ProcessDefinition getProcessDefinitionByTaskID(String nID_Task){
        HistoricTaskInstance historicTaskInstance = oHistoryService.createHistoricTaskInstanceQuery()
                .taskId(nID_Task).singleResult();
        String sBP = historicTaskInstance.getProcessDefinitionId();
        ProcessDefinition processDefinition = oRepositoryService.createProcessDefinitionQuery()
                .processDefinitionId(sBP).singleResult();
        return processDefinition;
    }

    protected void processExtractFieldsParameter(Set<String> headersExtra, HistoricTaskInstance currTask, String saFields, Map<String, Object> line) {
        HistoricTaskInstance details = oHistoryService.createHistoricTaskInstanceQuery().includeProcessVariables().taskId(currTask.getId()).singleResult();
        LOG.info("Process variables of the task {}:{}", currTask.getId(), details.getProcessVariables());
        if (details != null && details.getProcessVariables() != null) {
            LOG.info("(Cleaned saFields={})", saFields);
            String[] expressions = saFields.split(";");
            if (expressions != null) {
                for (String expression : expressions) {
                    String variableName = StringUtils.substringBefore(expression, "=");
                    String condition = StringUtils.substringAfter(expression, "=");
                    LOG.info("Checking variable with (name={}, condition={}, expression={})", variableName, condition, expression);
                    try {
                        Object conditionResult = getObjectResultofCondition(headersExtra, currTask, details, condition);
                        line.put(variableName, conditionResult);
                    } catch (Exception oException) {
                        LOG.error("Error: {}, occured while processing variable {}", oException.getMessage(), variableName);
                        LOG.debug("FAIL:", oException);
                    }
                }
            }
        }
    }

    private void loadCandidateStarterGroup(ProcessDefinition processDef, Set<String> candidateCroupsToCheck) {
        List<IdentityLink> identityLinks = oRepositoryService.getIdentityLinksForProcessDefinition(processDef.getId());
        LOG.info(String.format("Found %d identity links for the process %s", identityLinks.size(), processDef.getKey()));
        for (IdentityLink identity : identityLinks) {
            if (IdentityLinkType.CANDIDATE.equals(identity.getType())) {
                String groupId = identity.getGroupId();
                candidateCroupsToCheck.add(groupId);
                LOG.info("Added candidate starter (group={})", groupId);
            }
        }
    }

    //@RequestMapping("/web")
    //public class StartWebController {
    /*private final Logger LOG = LoggerFactory
    .getLogger(StartWebController.class);
    @Autowired
    private RuntimeService oRuntimeService;
    @Autowired
    private RepositoryService oRepositoryService;
    @Autowired
    private FormService formService;
    @RequestMapping(value = "/activiti/index", method = RequestMethod.GET)
    public ModelAndView index() {
    ModelAndView modelAndView = new ModelAndView("index");
    List<ProcessDefinition> processDefinitions = oRepositoryService.createProcessDefinitionQuery().latestVersion()
    .list();
    modelAndView.addObject("processList", processDefinitions);
    return modelAndView;
    }
    @RequestMapping(value = "/activiti/startForm/{id}", method = RequestMethod.GET)
    public ModelAndView startForm(@PathVariable("id") String id) {
    StartFormData sfd = oFormService.getStartFormData(id);
    List<FormProperty> fpList = sfd.getFormProperties();
    ModelAndView modelAndView = new ModelAndView("startForm");
    modelAndView.addObject("fpList", fpList);
    modelAndView.addObject("id", id);
    return modelAndView;
    }
    @RequestMapping(value = "/activiti/startProcess/{id}", method = RequestMethod.POST)
    public ModelAndView startProcess(@PathVariable("id") String id, @RequestParam Map<String, String> params) {
    ProcessInstance pi = oFormService.submitStartFormData(id, params);
    ModelAndView modelAndView = new ModelAndView("startedProcess");
    modelAndView.addObject("pi", pi.getProcessInstanceId());
    modelAndView.addObject("bk", pi.getBusinessKey());
    return modelAndView;
    }*/
    public String getOriginalProcessInstanceId(Long nID_Protected) throws CRCInvalidException {
        return Long.toString(ToolLuna.getValidatedOriginalNumber(nID_Protected));
    }

    public Attachment getAttachment(String attachmentId, String nID_Task, Integer nFile, String processInstanceId) {
        List<Attachment> attachments = oTaskService.getProcessInstanceAttachments(processInstanceId);
        Attachment attachmentRequested = null;
        for (int i = 0; i < attachments.size(); i++) {
            if (attachments.get(i).getId().equalsIgnoreCase(attachmentId) || (null != nFile && nFile.equals(i + 1))) {
                attachmentRequested = attachments.get(i);
                break;
            }
        }
        if (attachmentRequested == null && !attachments.isEmpty()) {
            attachmentRequested = attachments.get(0);
        }
        if (attachmentRequested == null) {
            throw new ActivitiObjectNotFoundException("Attachment for nID_Task '" + nID_Task + "' not found.");
        }
        return attachmentRequested;
    }

    public Attachment getAttachment(String attachmentId, String nID_Task, String processInstanceId) {
        List<Attachment> attachments = oTaskService.getProcessInstanceAttachments(processInstanceId);
        Attachment attachmentRequested = null;
        for (int i = 0; i < attachments.size(); i++) {
            if (attachments.get(i).getId().equalsIgnoreCase(attachmentId)) {
                attachmentRequested = attachments.get(i);
                break;
            }
        }
        if (attachmentRequested == null) {
            throw new ActivitiObjectNotFoundException("Attachment for nID_Task '" + nID_Task + "' not found.");
        }
        return attachmentRequested;
    }

    public void fillTheCSVMapHistoricTasks(String sID_BP, Date dateAt, Date dateTo, List<HistoricTaskInstance> foundResults, SimpleDateFormat sDateCreateDF, List<Map<String, Object>> csvLines, String pattern, 
    		Set<String> tasksIdToExclude, String saFieldsCalc, String[] headers, String sID_State_BP) {
        LOG.info("!!!!!csvLines: "+csvLines);
         
        LOG.info("<--------------------------------fillTheCSVMapHistoricTasks_begin---------------------------------------------------------->");
        if (CollectionUtils.isEmpty(foundResults)) {
            LOG.info(String.format("No historic tasks found for business process %s for date period %s - %s", sID_BP, DATE_TIME_FORMAT.format(dateAt), DATE_TIME_FORMAT.format(dateTo)));
            return;
        }
        LOG.info(String.format("Found %s historic tasks for business process %s for date period %s - %s", foundResults.size(), sID_BP, DATE_TIME_FORMAT.format(dateAt), DATE_TIME_FORMAT.format(dateTo)));
        if (pattern != null) {
            LOG.info("List of fields to retrieve: {}", pattern);
        } else {
            LOG.info("Will retreive all fields from tasks");
        }
        LOG.info("Tasks to skip {}", tasksIdToExclude);
        for (HistoricTaskInstance curTask : foundResults) {
            if (tasksIdToExclude.contains(curTask.getId())) {
                LOG.info("Skipping historic task {} from processing as it is already in the response", curTask.getId());
                continue;
            }
            String currentRow = pattern;
            Map<String, Object> variables = curTask.getProcessVariables();
            LOG.info("!!!!!!!!!!!!!!!variablessb= "+variables);
            LOG.info("Loaded historic variables for the task {}|{}", curTask.getId(), variables);
            currentRow = replaceFormProperties(currentRow, variables);
            if (saFieldsCalc != null) {
                currentRow = addCalculatedFields(saFieldsCalc, curTask, currentRow);
            }
            if (pattern != null) {
                currentRow = replaceReportFields(sDateCreateDF, curTask, currentRow);
                currentRow = currentRow.replaceAll("\\$\\{.*?\\}", "");
            }
            String[] values = currentRow.split(";");
            LOG.info("values= "+values);
            if (headers.length != values.length) {
                LOG.info("Size of header :{} Size of values array:{}", headers.length, values.length);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < headers.length; i++) {
                    sb.append(headers[i]);
                    LOG.info("!!!!!!!!!!!!!!!sb= "+sb);
                    sb.append(";");
                    LOG.info("!!!!!!!!!!!!!!!sb= "+sb);
                }
                LOG.info("(headers={})", sb.toString());
                sb = new StringBuilder();
                LOG.info("!!!!!!!!!!!!!!!sb= "+sb);
                for (int i = 0; i < values.length; i++) {
                    sb.append(values[i]);
                    LOG.info("!!!!!!!!!!!!!!!sb= "+sb);
                    sb.append(";");
                    LOG.info("!!!!!!!!!!!!!!!sb= "+sb);
                }
                LOG.info("(values={})", sb.toString());
            }
            Map<String, Object> currRow = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                currRow.put(headers[i], i < values.length ? values[i] : "");
            }
            csvLines.add(currRow);
            LOG.info("csvLines= " + csvLines);
            LOG.info("<--------------------------------fillTheCSVMapHistoricTasks_end---------------------------------------------------------->");
        }
    }

    /*
     * private void clearEmptyValues(Map<String, Object> params) {
     * Iterator<String> iterator = params.keySet().iterator(); while
     * (iterator.hasNext()){ String key = iterator.next(); if (params.get(key)
     * == null){ iterator.remove(); } } }
     */
    private void addTasksDetailsToLine(Set<String> headersExtra, HistoricTaskInstance currTask, Map<String, Object> resultLine) {
        LOG.debug("(currTask={})", currTask.getId());
        HistoricTaskInstance details = oHistoryService.createHistoricTaskInstanceQuery().includeProcessVariables().taskId(currTask.getId()).singleResult();
        if (details != null && details.getProcessVariables() != null) {
            for (String headerExtra : headersExtra) {
                Object variableValue = details.getProcessVariables().get(headerExtra);
                resultLine.put(headerExtra, variableValue);
            }
        }
    }

    private Set<String> findExtraHeadersForDetail(List<HistoricTaskInstance> foundResults, List<String> headers) {
        Set<String> headersExtra = new TreeSet<>();
        for (HistoricTaskInstance currTask : foundResults) {
            HistoricTaskInstance details = oHistoryService.createHistoricTaskInstanceQuery().includeProcessVariables().taskId(currTask.getId()).singleResult();
            if (details != null && details.getProcessVariables() != null) {
                LOG.info("(proccessVariavles={})", details.getProcessVariables());
                for (String key : details.getProcessVariables().keySet()) {
                    if (!key.startsWith("sBody")) {
                        headersExtra.add(key);
                    }
                }
            }
        }
        headers.addAll(headersExtra);
        return headersExtra;
    }

    private String replaceFormProperties(String currentRow, Map<String, Object> data) {
        String res = currentRow;
        for (Map.Entry<String, Object> property : data.entrySet()) {
            LOG.info(String.format("Matching property %s:%s with fieldNames", property.getKey(), property.getValue()));
            //LOG.info("!!!!!!!!!!data: " + data);
            if (currentRow != null && res.contains("${" + property.getKey() + "}")) {
                LOG.info(String.format("Found field with id %s in the pattern. Adding value to the result", "${" + property.getKey() + "}"));
                if (property.getValue() != null) {
                    String sValue = property.getValue().toString();
                    LOG.info("(sValue={})", sValue);
                    if (sValue != null) {
                        LOG.info(String.format("Replacing field with the value %s", sValue));
                        res = res.replace("${" + property.getKey() + "}", sValue);
                    }
                }
            }
        }
        return res;
    }

    private String replaceFormProperties(String currentRow, TaskFormData data) {
        String res = currentRow;
        for (FormProperty property : data.getFormProperties()) {
            LOG.info(String.format("Matching property %s %s %s with fieldNames", property.getId(), property.getName(), property.getType().getName()));
            //LOG.info("!!!!!!!!!!getId: " + property.getId() + " getName: " + property.getName() + " getType: " +  property.getType().getName() + " getValue: " +  property.getValue() + "!");
            if (currentRow != null && res.contains("${" + property.getId() + "}")) {
                LOG.info(String.format("Found field with id %s in the pattern. Adding value to the result", "${" + property.getId() + "}"));
                String sValue = getPropertyValue(property);
                if (sValue != null) {
                    LOG.info(String.format("Replacing field with the value %s", sValue));
                    res = res.replace("${" + property.getId() + "}", sValue);
                }
            }
        }
        return res;
    }

    public Charset getCharset(String sID_Codepage) {
        Charset charset;
        String codePage = sID_Codepage.replaceAll("-", "");
        try {
            if ("win1251".equalsIgnoreCase(codePage) || "CL8MSWIN1251".equalsIgnoreCase(codePage)) {
                codePage = "CP1251";
            }
            charset = Charset.forName(codePage);
            LOG.debug("use charset - {}", charset);
        } catch (IllegalArgumentException e) {
            LOG.error("Error: {}. Do not support charset - {}", e.getMessage(), codePage);
            throw new ActivitiObjectNotFoundException("Statistics for the business task for charset '" + codePage + "' cannot be construct.", Task.class, e);
        }
        return charset;
    }

    public String getFileExtention(MultipartFile file) {
        String[] parts = file.getOriginalFilename().split("\\.");
        if (parts.length != 0) {
            return parts[parts.length - 1];
        }
        return "";
    }

    public String getFileExtention(String fileName) {
        String[] parts = fileName.split("\\.");
        if (parts.length != 0) {
            return parts[parts.length - 1];
        }
        return "";
    }
    /*private static class TaskAlreadyUnboundException extends Exception {
    private TaskAlreadyUnboundException(String message) {
    super(message);
    }
    }*/

    public Map<String, Object> createCsvLine(boolean bDetail, Set<String> headersExtra, HistoricTaskInstance currTask, String saFields) {
        Map<String, Object> line = new HashMap<>();
        line.put("nID_Process", currTask.getProcessInstanceId());
        line.put("sLoginAssignee", currTask.getAssignee());
        Date startDate = currTask.getStartTime();
        line.put("sDateTimeStart", DATE_TIME_FORMAT.format(startDate));
        line.put("nDurationMS", String.valueOf(currTask.getDurationInMillis()));
        long durationInHours = currTask.getDurationInMillis() / MILLIS_IN_HOUR;
        line.put("nDurationHour", String.valueOf(durationInHours));
        line.put("sName", currTask.getName());
        if (bDetail) {
            addTasksDetailsToLine(headersExtra, currTask, line);
        }
        if (saFields != null) {
            processExtractFieldsParameter(headersExtra, currTask, saFields, line);
        }
        return line;
    }

    public String getSeparator(String sID_BP, String nASCI_Spliter) {
        if (nASCI_Spliter == null) {
            return String.valueOf(Character.toChars(DEFAULT_REPORT_FIELD_SPLITTER));
        }
        if (!StringUtils.isNumeric(nASCI_Spliter)) {
            LOG.error("ASCI code is not a number {}", nASCI_Spliter);
            throw new ActivitiObjectNotFoundException("Statistics for the business task with name '" + sID_BP + "' not found. Wrong splitter.", Task.class);
        }
        return String.valueOf(Character.toChars(Integer.valueOf(nASCI_Spliter)));
    }

    public String formHeader(String saFields, List<HistoricTaskInstance> foundHistoricResults, String saFieldsCalc) {
        String res = null;
        if (saFields != null && !"".equals(saFields.trim())) {
            LOG.info("Fields have custom header names");
            StringBuilder sb = new StringBuilder();
            String[] fields = saFields.split(";");
            LOG.info("fields: "+fields);
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].contains("\\=")) {
                    sb.append(StringUtils.substringBefore(fields[i], "\\="));
                    LOG.info("if (fields[i].contains(\"\\\\=\"))_sb: "+sb);
                } else {
                    sb.append(fields[i]);
                    LOG.info("else_sb: "+sb);
                }
                if (i < fields.length - 1) {
                    sb.append(";");
                     LOG.info("(i < fields.length - 1)_sb: "+sb);
                }
            }
            res = sb.toString();
            res = res.replaceAll("\\$\\{", "");
            res = res.replaceAll("\\}", "");
            LOG.info("Formed header from list of fields: {}", res);
        } else {
            if (foundHistoricResults != null && !foundHistoricResults.isEmpty()) {
                HistoricTaskInstance historicTask = foundHistoricResults.get(0);
                Set<String> keys = historicTask.getProcessVariables().keySet();
                StringBuilder sb = new StringBuilder();
                Iterator<String> iter = keys.iterator();
                while (iter.hasNext()) {
                    sb.append(iter.next());
                    if (iter.hasNext()) {
                        sb.append(";");
                    }
                }
                res = sb.toString();
                LOG.info("res: "+res);
            }
            LOG.info("Formed header from all the fields of a task: {}", res);
        }
        if (saFieldsCalc != null) {
            saFieldsCalc = StringUtils.substringAfter(saFieldsCalc, "\"");
            saFieldsCalc = StringUtils.substringBeforeLast(saFieldsCalc, "\"");
            String[] params = saFieldsCalc.split(";");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < params.length; i++) {
                String currParam = params[i];
                String cutHeader = StringUtils.substringBefore(currParam, "=");
                LOG.info("Adding header to the csv file from saFieldsCalc: {}", cutHeader);
                sb.append(cutHeader);
                if (i < params.length - 1) {
                    sb.append(";");
                }
            }
            res = res + ";" + sb.toString();
            LOG.info("Header with calculated fields: {}", res);
        }
        return res;
    }

    public Task getTaskByID(String nID_Task) {
        return oTaskService.createTaskQuery().taskId(nID_Task).singleResult();
    }

    private List<Task> getTasksByProcessInstanceId(String processInstanceID) throws RecordNotFoundException {
        List<Task> tasks = oTaskService.createTaskQuery().processInstanceId(processInstanceID).list();
        if (tasks == null || tasks.isEmpty()) {
            LOG.error(String.format("Tasks for Process Instance [id = '%s'] not found", processInstanceID));
            throw new RecordNotFoundException();
        }
        return tasks;
    }

    private String getPropertyValue(FormProperty property) {
        String sValue;
        String sType = property.getType().getName();
        LOG.info("getId:" + property.getId() + " getName: " + property.getName() + " getType: " + sType + " getValue: " + property.getValue());
        if ("enum".equalsIgnoreCase(sType)) {
            sValue = parseEnumProperty(property);
        } else {
            sValue = property.getValue();
        }
        LOG.info("(sValue={})", sValue);
        return sValue;
    }

    public Set<String> findExtraHeaders(Boolean bDetail, List<HistoricTaskInstance> foundResults, List<String> headers) {
        if (bDetail) {
            Set<String> headersExtra = findExtraHeadersForDetail(foundResults, headers);
            return headersExtra;
        } else {
            return new TreeSet<>();
        }
    }

    private String replaceReportFields(SimpleDateFormat sDateCreateDF, Task curTask, String currentRow) {
        String res = currentRow;
        for (TaskReportField field : TaskReportField.values()) { 
            if (res.contains(field.getPattern())) {
                res = field.replaceValue(res, curTask, sDateCreateDF, oGeneralConfig);//sID_Order
                LOG.info("!!!!!!!!!!res: "+res);
            }
        }
        return res;
    }

    private String replaceReportFields(SimpleDateFormat sDateCreateDF, HistoricTaskInstance curTask, String currentRow) {
        LOG.info("<--------------------------------replaceReportFields_begin-------------------------------------------->");
        String res = currentRow;
        for (TaskReportField field : TaskReportField.values()) {
            if (res.contains(field.getPattern())) {
                res = field.replaceValue(res, curTask, sDateCreateDF, oGeneralConfig);
                LOG.info("!!!!!!!!!!res: "+res);
            }
        }
         LOG.info("<--------------------------------replaceReportFields_end-------------------------------------------->");
        return res;
        
    }

    /*private String createTable(String soData) throws UnsupportedEncodingException {
        if (soData == null || "[]".equals(soData) || "".equals(soData)) {
            return "";
        }
        StringBuilder tableStr = new StringBuilder("<table><tr><th>Поле</th><th>Тип </th><th> Поточне значення</th></tr>");
        JSONObject jsnobject = new JSONObject("{ soData:" + soData + "}");
        JSONArray jsonArray = jsnobject.getJSONArray("soData");
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject record = jsonArray.getJSONObject(i);
            tableStr.append("<tr><td>")
                    .append(record.opt("id") != null ? record.get("id") : "?")
                    .append("</td><td>")
                    .append(record.opt("type") != null ? record.get("type").toString() : "??")
                    .append("</td><td>")
                    .append(record.opt("value") != null ? record.get("value")
                            .toString() : "").append("</td></tr>");
        }
        tableStr.append("</table>");
        return tableStr.toString();
    }*/

    private void loadCandidateGroupsFromTasks(ProcessDefinition processDef, Set<String> candidateCroupsToCheck) {
        BpmnModel bpmnModel = oRepositoryService.getBpmnModel(processDef.getId());
        for (FlowElement flowElement : bpmnModel.getMainProcess().getFlowElements()) {
            if (flowElement instanceof UserTask) {
                UserTask userTask = (UserTask) flowElement;
                List<String> candidateGroups = userTask.getCandidateGroups();
                if (candidateGroups != null && !candidateGroups.isEmpty()) {
                    candidateCroupsToCheck.addAll(candidateGroups);
                    LOG.info(String.format("Added candidate groups %s from user task %s", candidateGroups,
                            userTask.getId()));
                }
            }
        }
    }
    
    /**
     * saFeilds paramter may contain name of headers or can be empty. Before
     * forming the result - we need to cut header names
     *
     //     * @param saFields
     //     * @param foundHistoricResults
     //     * @return
     */
    public String processSaFields(String saFields, List<HistoricTaskInstance> foundHistoricResults) {
        String res = null;
         
        if (saFields != null) {          
            LOG.info("saFields has custom header names");
            StringBuilder sb = new StringBuilder();
            String[] fields = saFields.split(";");
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].contains("=")) {
                    sb.append(StringUtils.substringAfter(fields[i], "="));
                } else {
                    sb.append(fields[i]);
                }
                if (i < fields.length - 1) {
                    sb.append(";");
                }
            }
            res = sb.toString();
        } else {
            if (foundHistoricResults != null && !foundHistoricResults.isEmpty()) {
                HistoricTaskInstance historicTask = foundHistoricResults.get(0);
                Set<String> keys = historicTask.getProcessVariables().keySet();
                StringBuilder sb = new StringBuilder();
                Iterator<String> iter = keys.iterator();
                while (iter.hasNext()) {
                    sb.append("${").append(iter.next()).append("}");
                    if (iter.hasNext()) {
                        sb.append(";");
                    }
                }
                res = sb.toString();
            }
            LOG.info("Formed header from all the fields of a task: {}", res);
        }
        return res;
    }

    // private Long getProcessId(String sID_Order, Long nID_Protected, Long
    // nID_Process) {
    // Long result = null;
    // if (nID_Process != null) {
    // result = nID_Process;
    // } else if (nID_Protected != null) {
    // result = ToolLuna.getOriginalNumber(nID_Protected);
    // } else if (sID_Order != null && !sID_Order.isEmpty()) {
    // Long protectedId;
    // if (sID_Order.contains("-")) {
    // int dash_position = sID_Order.indexOf("-");
    // protectedId = Long.valueOf(sID_Order.substring(dash_position + 1));
    // } else {
    // protectedId = Long.valueOf(sID_Order);
    // }
    // result = ToolLuna.getOriginalNumber(protectedId);
    // }
    // return result;
    // }
    

    public Long getIDProtectedFromIDOrder(String sID_order) {
        StringBuilder ID_Protected = new StringBuilder();
        int hyphenPosition = sID_order.lastIndexOf("-");
        if (hyphenPosition < 0) {
            for (int i = 0; i < sID_order.length(); i++){
                buildID_Protected(sID_order, ID_Protected, i);
            }
        } else {
            for (int i = hyphenPosition + 1; i < sID_order.length(); i++) {
                buildID_Protected(sID_order, ID_Protected, i);
            }
        }
        return Long.parseLong(ID_Protected.toString());
    }

    private void buildID_Protected(String sID_order, StringBuilder ID_Protected, int i) {
        String ch = "" + sID_order.charAt(i);
        Scanner scanner = new Scanner(ch);
        if(scanner.hasNextInt()){
            ID_Protected.append(ch);
        }
    }

    public Date getEndDate(Date date) {
        if (date == null) {
            return DateTime.now().toDate();
        }
        return date;
    }

    public List<String> getTaskIdsByProcessInstanceId(String processInstanceID) throws RecordNotFoundException {

        return findTaskIDsByActiveAndHistoryProcessInstanceID(Long.parseLong(processInstanceID));

    }

    public void fillTheCSVMap(String sID_BP, Date dateAt, Date dateTo, List<Task> aTaskFound, SimpleDateFormat sDateCreateDF, 
            List<Map<String, Object>> csvLines, String pattern, String saFieldsCalc, String[] asHeader) {
        if (CollectionUtils.isEmpty(aTaskFound)) {
            
            
            LOG.info(String.format("No tasks found for business process %s for date period %s - %s", sID_BP, DATE_TIME_FORMAT.format(dateAt), DATE_TIME_FORMAT.format(dateTo)));
            return;
        }
        LOG.info(String.format("Found %s tasks for business process %s for date period %s - %s", aTaskFound.size(), sID_BP, DATE_TIME_FORMAT.format(dateAt), DATE_TIME_FORMAT.format(dateTo)));
        if (pattern != null) {
            LOG.info("List of fields to retrieve: }{", pattern);
        } else {
            LOG.info("Will retreive all fields from tasks");
        }
        for (Task oTask : aTaskFound) {
            String sRow = pattern;
            LOG.trace("Process task - {}", oTask);
            TaskFormData oTaskFormData = oFormService.getTaskFormData(oTask.getId());
            sRow = replaceFormProperties(sRow, oTaskFormData);
            LOG.info("!!!!!!!!!!!!!!!!!!!!!!fillTheCSVMap!_!sRows= "+sRow);
           if (saFieldsCalc != null) {
                sRow = addCalculatedFields(saFieldsCalc, oTask, sRow);
            }
            if (pattern != null) {
                sRow = replaceReportFields(sDateCreateDF, oTask, sRow);
                sRow = sRow.replaceAll("\\$\\{.*?\\}", "");
            }
            String[] asField = sRow.split(";");
            Map<String, Object> mCell = new HashMap<>();
            for (int i = 0; i < asField.length; i++) {
                try{
                    String sName = "Column_"+i;
                    if(asHeader.length>i){
                        sName = asHeader[i];
                    }
                    mCell.put(sName, asField[i]);
                }catch(Exception oException){
                    LOG.warn("oException.getMessage()={} (i={},mCell={},asHeader={},asField={})", oException.getMessage(),i,mCell, asHeader, asField);
                }
            }
            csvLines.add(mCell);
        }
    }

    public String[] createStringArray(Map<String, Object> csvLine, List<String> headers) {
        List<String> result = new LinkedList<>();
        for (String header : headers) {
            Object value = csvLine.get(header);
            result.add(value == null ? "" : value.toString());
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * Получение списка бизнес процессов к которым у пользователя есть доступ
     * @param sLogin - Логин пользователя
     * @return
     */
    public List<Map<String, String>> getBusinessProcessesForUser(String sLogin){

        if (sLogin.isEmpty()) {
            LOG.error("Unable to found business processes for user with empty login");
            throw new ActivitiObjectNotFoundException(
                    "Unable to found business processes for user with empty login",
                    ProcessDefinition.class);
        }

        List<Map<String, String>> result = new LinkedList<>();
        List<ProcessDefinition> resultProcessDefinitionList = new LinkedList<>();

        LOG.info(String.format(
                "Selecting business processes for the user with login: %s",
                sLogin));

        List<ProcessDefinition> processDefinitionsList = oRepositoryService
                .createProcessDefinitionQuery().active().latestVersion().list();
        if (CollectionUtils.isNotEmpty(processDefinitionsList)) {
            LOG.info(String.format("Found %d active process definitions",
                    processDefinitionsList.size()));

            resultProcessDefinitionList = getAvailabilityProcessDefinitionByLogin(sLogin, processDefinitionsList);
        } else {
            LOG.info("Have not found active process definitions.");
        }

        for (ProcessDefinition processDef : resultProcessDefinitionList){
            Map<String, String> process = new HashMap<>();
            process.put("sID", processDef.getKey());
            process.put("sName", processDef.getName());
            LOG.info(String.format("Added record to response %s", process.toString()));
            result.add(process);
        }

        return result;
    }

    private List<ProcessDefinition> getAvailabilityProcessDefinitionByLogin(String sLogin, List<ProcessDefinition> processDefinitionsList) {

        List<ProcessDefinition> resultList = new LinkedList<>();

        List<Group> groups;
        groups = oIdentityService.createGroupQuery().groupMember(sLogin).list();
        if (groups != null && !groups.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Group group : groups) {
                sb.append(group.getId());
                sb.append(",");
            }
            LOG.info("Found {}  groups for the user {}:{}", groups.size(), sLogin, sb.toString());
        }

        for (ProcessDefinition processDef : processDefinitionsList) {
            LOG.info("process definition id: {}", processDef.getId());

            Set<String> candidateCroupsToCheck = getGroupsByProcessDefinition(processDef);

            if(checkIncludeProcessDefinitionIntoGroupList(groups, candidateCroupsToCheck)){
                resultList.add(processDef);
            }
        }
        return resultList;
    }

    private Set<String> getGroupsByProcessDefinition(ProcessDefinition processDef) {
        Set<String> candidateCroupsToCheck = new HashSet<>();
        loadCandidateGroupsFromTasks(processDef, candidateCroupsToCheck);
        loadCandidateStarterGroup(processDef, candidateCroupsToCheck);
        return candidateCroupsToCheck;
    }


    private boolean checkIncludeProcessDefinitionIntoGroupList(List<Group> groups, Set<String> candidateCroupsToCheck){
        for (Group group : groups) {
            for (String groupFromProcess : candidateCroupsToCheck) {
                if (groupFromProcess.contains("${")) {
                    groupFromProcess = groupFromProcess.replaceAll("\\$\\{?.*}", "(.*)");
                }
                if (group.getId().matches(groupFromProcess)) {
                    return true;
                }
            }
        }
        return false;
    }

    
    public Map<String, String> getTaskFormDataInternal(Long nID_Task) throws CommonServiceException {
        Map<String, String> result = new HashMap<>();
        Task task = oTaskService.createTaskQuery().taskId(nID_Task.toString()).singleResult();
        LOG.info("Found task with (ID={}, process instance ID={})", nID_Task, task.getProcessInstanceId());
        FormData taskFormData = oFormService.getTaskFormData(task.getId());
        Map<String, Object> variables = oRuntimeService.getVariables(task.getProcessInstanceId());
        if (taskFormData != null) {
            loadFormPropertiesToMap(taskFormData, variables, result);
        }
        return result;
    }
    
    
    public Map<String, Object> sendProccessToGRESInternal(Long nID_Task) throws CommonServiceException {
        Map<String, Object> res = new HashMap<>();

        Task task = oTaskService.createTaskQuery().taskId(nID_Task.toString()).singleResult();

        LOG.info("Found task with (ID={}, process inctanse ID={})", nID_Task, task.getProcessInstanceId());

        HistoricProcessInstance processInstance = oHistoryService.createHistoricProcessInstanceQuery().processInstanceId(
                task.getProcessInstanceId()).singleResult();

        ProcessDefinition processDefinition = oRepositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId()).singleResult();

        FormData startFormData = oFormService.getStartFormData(processInstance.getProcessDefinitionId());
        FormData taskFormData = oFormService.getTaskFormData(task.getId());

        res.put("nID_Task", nID_Task.toString());
        res.put("nID_Proccess", task.getProcessInstanceId());
        res.put("sProcessName", processDefinition.getName());
        res.put("sProcessDefinitionKey", processDefinition.getKey());

        Map<String, Object> variables = oRuntimeService.getVariables(task.getProcessInstanceId());

        Map<String, String> startFormValues = new HashMap<>();
        Map<String, String> taskFormValues = new HashMap<>();
        if (startFormData != null) {
            loadFormPropertiesToMap(startFormData, variables, startFormValues);
        }
        if (taskFormData != null) {
            loadFormPropertiesToMap(taskFormData, variables, taskFormValues);
        }

        res.put("startFormData", startFormValues);
        res.put("taskFormData", taskFormValues);

        return res;
    }    
 
    public String updateHistoryEvent_Service(HistoryEvent_Service_StatusType oHistoryEvent_Service_StatusType, String sID_Order,
            String saField, String sBody, String sToken, String sUserTaskName,String sSubjectInfo, Long nID_Subject
        ) throws Exception {

        Map<String, String> mParam = new HashMap<>();
        //params.put("sID_Order", sID_Order);
        //Long nID_StatusType
        mParam.put("nID_StatusType", oHistoryEvent_Service_StatusType.getnID() + "");
        mParam.put("soData", saField);
        //params.put("sHead", sHead);
        mParam.put("sBody", sBody);
        mParam.put("sToken", sToken);
        mParam.put("sSubjectInfo",sSubjectInfo);
        if(nID_Subject != null){
        mParam.put("snID_Subject",nID_Subject+"");
        }
        //params.put("sUserTaskName", sUserTaskName);
        return oHistoryEventService.updateHistoryEvent(sID_Order, sUserTaskName, true, oHistoryEvent_Service_StatusType, mParam);
    }
    
    public List<Task> getTasksForChecking(String sLogin,
            Boolean bEmployeeUnassigned) {
        List<Task> tasks;
        if (bEmployeeUnassigned) {
            //tasks = oTaskService.createTaskQuery().taskUnassigned().active().list();
            tasks = oTaskService.createTaskQuery().taskCandidateUser(sLogin).taskUnassigned().active().list();
            LOG.info("Looking for unassigned tasks. Found {} tasks", (tasks != null ? tasks.size() : 0));
        } else {
            //tasks = oTaskService.createTaskQuery().taskAssignee(sLogin).active().list();
            tasks = oTaskService.createTaskQuery().taskCandidateOrAssigned(sLogin).active().list();
            LOG.info("Looking for tasks assigned to user:{}. Found {} tasks", sLogin, (tasks != null ? tasks.size() : 0));
        }
        return tasks;
    }

    /*public static void main(String[] args) {
        System.out.println(createTable_TaskProperties("[{'id':'bankIdfirstName','type':'string','value':'3119325858'}]"));
    }*/

    public static void replacePatterns(DelegateExecution execution, DelegateTask task, Logger LOG) {
        try {
            LOG.info("(task.getId()={})", task.getId());
            //LOG.info("execution.getId()=" + execution.getId());
            //LOG.info("task.getVariable(\"sBody\")=" + task.getVariable("sBody"));
            //LOG.info("execution.getVariable(\"sBody\")=" + execution.getVariable("sBody"));

            EngineServices oEngineServices = execution.getEngineServices();
            RuntimeService oRuntimeService = oEngineServices.getRuntimeService();
            TaskFormData oTaskFormData = oEngineServices
                    .getFormService()
                    .getTaskFormData(task.getId());

            LOG.info("Found taskformData={}", oTaskFormData);
            if (oTaskFormData == null) {
                return;
            }

            Collection<File> asPatterns = getFiles_PatternPrint();
            for (FormProperty oFormProperty : oTaskFormData.getFormProperties()) {
                String sFieldID = oFormProperty.getId();
                String sExpression = oFormProperty.getName();

                LOG.info("(sFieldID={})", sFieldID);
                //LOG.info("sExpression=" + sExpression);
                LOG.info("(sExpression.length()={})", sExpression != null ? sExpression.length() + "" : "");

                if (sExpression == null || sFieldID == null || !sFieldID.startsWith("sBody")) {
                    continue;
                }

                for (File oFile : asPatterns) {
                    String sName = "pattern/print/" + oFile.getName();
                    //LOG.info("sName=" + sName);

                    if (sExpression.contains("[" + sName + "]")) {
                        LOG.info("sExpression.contains! (sName={})", sName);

                        String sData = ToolFS.getFileString(oFile, null);
                        //LOG.info("sData=" + sData);
                        LOG.info("(sData.length()={})", sData != null ? sData.length() + "" : "null");
                        if (sData == null) {
                            continue;
                        }

                        sExpression = sExpression.replaceAll("\\Q[" + sName + "]\\E", sData);
                        //                        LOG.info("sExpression=" + sExpression);

                        //LOG.info("[replacePatterns](sFieldID=" + sFieldID + "):1-Ok!");
                        oRuntimeService.setVariable(task.getProcessInstanceId(), sFieldID, sExpression);
/*                        LOG.info("[replacePatterns](sFieldID=" + sFieldID + "):2-Ok:" + oRuntimeService
                                .getVariable(task.getProcessInstanceId(), sFieldID));*/
                        LOG.info("setVariable Ok! (sFieldID={})", sFieldID);
                    }
                    LOG.info("Ok! (sName={})",sName);
                }
                LOG.info("Ok! (sFieldID={})", sFieldID);
            }
        } catch (Exception oException) {
            LOG.error("FAIL:", oException);
        }
    }    
    
    
    public static String setStringFromFieldExpression(Expression expression,
            DelegateExecution execution, Object value) {
        if (expression != null && value != null) {
            expression.setValue(value, execution);
        }
        return null;
    }

    /**
     * получаем по задаче ид процесса
     *
     * @param nID_Task ИД-номер таски
     * @return processInstanceId
     */
    public String getProcessInstanceIDByTaskID(String nID_Task) {

        HistoricTaskInstance historicTaskInstanceQuery = oHistoryService
                .createHistoricTaskInstanceQuery().taskId(nID_Task)
                .singleResult();
        String processInstanceId = historicTaskInstanceQuery
                .getProcessInstanceId();
        if (processInstanceId == null) {
            throw new ActivitiObjectNotFoundException(String.format(
                    "ProcessInstanceId for taskId '{%s}' not found.", nID_Task),
                    Attachment.class);
        }
        return processInstanceId;
    }

    /**
     * Получение процесса по его ИД
     *
     * @param sProcessInstanceID
     * @return ProcessInstance
     */
    public HistoricProcessInstance getProcessInstancyByID(String sProcessInstanceID) {
        HistoricProcessInstance processInstance = oHistoryService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(sProcessInstanceID).includeProcessVariables()
                .singleResult();
        if (processInstance == null) {
            throw new ActivitiObjectNotFoundException(String.format(
                    "ProcessInstance for processInstanceId '{%s}' not found.",
                    sProcessInstanceID), Attachment.class);
        }
        return processInstance;
    }

    /**
     * Получение данных о процессе по Таске
     * @param nID_Task - номер-ИД таски
     * @return DTO-объект ProcessDTOCover
     */
    public ProcessDTOCover getProcessInfoByTaskID(Long nID_Task) {
        LOG.info("start process getting Task Data by nID_Task = {}", nID_Task);

        HistoricTaskInstance historicTaskInstance = oHistoryService.createHistoricTaskInstanceQuery()
                .taskId(nID_Task.toString()).singleResult();

        String sBP = historicTaskInstance.getProcessDefinitionId();
        LOG.info("id-бизнес-процесса (БП) sBP={}", sBP);


        ProcessDefinition processDefinition = oRepositoryService.createProcessDefinitionQuery()
                .processDefinitionId(sBP).singleResult();

        String sName = processDefinition.getName();
        LOG.info("название услуги (БП) sName={}", sName);
        
        HistoricProcessInstance processInstance = oHistoryService.createHistoricProcessInstanceQuery().
        		processInstanceId(historicTaskInstance.getProcessInstanceId()).
        		includeProcessVariables().singleResult();
        String sPlace = processInstance.getProcessVariables().containsKey("sPlace") ? (String) processInstance.getProcessVariables().get("sPlace") : "";
        LOG.info("Found process instance with variables. sPlace {}", sPlace);
        
        Date oProcessInstanceStartDate = oHistoryService.createProcessInstanceHistoryLogQuery(getProcessInstanceIDByTaskID(
                nID_Task.toString())).singleResult().getStartTime();
        DateTimeFormatter formatter = JsonDateTimeSerializer.DATETIME_FORMATTER;
        String sDateCreate = formatter.print(oProcessInstanceStartDate.getTime());
        LOG.info("дата создания процесса sDateCreate={}", sDateCreate);

        Long nID = Long.valueOf(historicTaskInstance.getProcessInstanceId());
        LOG.info("id процесса (nID={})", nID.toString());

        ProcessDTOCover oProcess = new ProcessDTOCover(sPlace + " " + sName, sBP, nID, sDateCreate);
        LOG.info("Created ProcessDTOCover={}", oProcess.toString());

        return oProcess;
    }

    /**
     * Получение полей стартовой формы по ID таски
     * @param nID_Task номер-ИД таски, для которой нужно найти процесс и вернуть поля его стартовой формы.
     * @return
     * @throws RecordNotFoundException
     */
    public Map<String, Object> getStartFormData(Long nID_Task) throws RecordNotFoundException {
        Map<String, Object> mReturn = new HashMap();
        HistoricTaskInstance oHistoricTaskInstance = oHistoryService.createHistoricTaskInstanceQuery()
                .taskId(nID_Task.toString()).singleResult();
        LOG.info("(oHistoricTaskInstance={})", oHistoricTaskInstance);
        if (oHistoricTaskInstance != null) {
            String snID_Process = oHistoricTaskInstance.getProcessInstanceId();
            LOG.info("(snID_Process={})", snID_Process);
            List<HistoricDetail> aHistoricDetail = null;
            if(snID_Process != null){
                aHistoricDetail = oHistoryService.createHistoricDetailQuery().formProperties()
                        .processInstanceId(snID_Process).list();
            }
            LOG.info("(aHistoricDetail={})", aHistoricDetail);
            if(aHistoricDetail == null){
                throw new RecordNotFoundException("aHistoricDetail");
            }
            for (HistoricDetail oHistoricDetail : aHistoricDetail) {
                HistoricFormProperty oHistoricFormProperty = (HistoricFormProperty) oHistoricDetail;
                mReturn.put(oHistoricFormProperty.getPropertyId(), oHistoricFormProperty.getPropertyValue());
            }
        }else{
            HistoricProcessInstance oHistoricProcessInstance = oHistoryService.createHistoricProcessInstanceQuery().processInstanceId(nID_Task.toString()).singleResult();
            LOG.info("(oHistoricProcessInstance={})", oHistoricProcessInstance);
            //if(oHistoricProcessInstance==null){
            //    throw new RecordNotFoundException("oHistoricProcessInstance");
            //}

            //oHistoricProcessInstance.getId()
            /*
            for(Map.Entry<String,Object> oHistoricProcess : oHistoricProcessInstance.getProcessVariables().entrySet()){
                mReturn.put(oHistoricProcess.getKey(), oHistoricProcess.getValue());
            }
            */

            /*FormData oFormData = formService.getStartFormData(oHistoricProcessInstance.getProcessDefinitionId());
            if(oFormData==null){
                throw new RecordNotFoundException("oFormData");
            }
            List<FormProperty> aFormProperty = oFormData.getFormProperties();
            for (FormProperty oFormProperty : aFormProperty) {
                mReturn.put(oFormProperty.getId(), oFormProperty.getValue());
            }*/
            //Task oTask = oActionTaskService.findBasicTask(nID_Task.toString());


            /*TaskFormData oTaskFormData = formService.getTaskFormData(nID_Task);
            if(oTaskFormData==null){
                throw new RecordNotFoundException("oTaskFormData");
            }
            List<FormProperty> aFormProperty = oTaskFormData.getFormProperties();
            for (FormProperty oFormProperty : aFormProperty) {
                mReturn.put(oFormProperty.getId(), oFormProperty.getValue());
            }*/

            List<Task> activeTasks = null;
            TaskQuery taskQuery = oTaskService.createTaskQuery();
            taskQuery.taskId(nID_Task.toString());
            activeTasks = taskQuery.list();//.active()
            LOG.info("(nID_Task={})",nID_Task);
            if(activeTasks.isEmpty()){
                taskQuery = oTaskService.createTaskQuery();
                LOG.info("1)activeTasks.isEmpty()");
                taskQuery.processInstanceId(nID_Task.toString());
                activeTasks = taskQuery.list();//.active()
                if(activeTasks.isEmpty() && oHistoricProcessInstance!=null){
                    taskQuery = oTaskService.createTaskQuery();
                    LOG.info("2)activeTasks.isEmpty()(oHistoricProcessInstance.getId()={})",oHistoricProcessInstance.getId());
                    taskQuery.processInstanceId(oHistoricProcessInstance.getId());
                    activeTasks = taskQuery.list();//.active()
                    /*if(activeTasks.isEmpty()){
                        taskQuery = oTaskService.createTaskQuery();
                        LOG.info("3)activeTasks.isEmpty()(oHistoricProcessInstance.getSuperProcessInstanceId()={})",oHistoricProcessInstance.getSuperProcessInstanceId());
                        taskQuery.processInstanceId(oHistoricProcessInstance.getSuperProcessInstanceId());
                        activeTasks = taskQuery.list();//.active()
                        if(activeTasks.isEmpty()){
                            if(oHistoricProcessInstance.getSuperProcessInstanceId()!= null){
                                taskQuery = oTaskService.createTaskQuery();
                                LOG.info("4)activeTasks.isEmpty()(oHistoricProcessInstance.getSuperProcessInstanceId()={})",oHistoricProcessInstance.getSuperProcessInstanceId());
                                taskQuery.taskId(oHistoricProcessInstance.getSuperProcessInstanceId());
                                activeTasks = taskQuery.list();//.active()

                            }
                            if(activeTasks.isEmpty() && oHistoricProcessInstance.getId()!=null){
                                taskQuery = oTaskService.createTaskQuery();
                                LOG.info("5)activeTasks.isEmpty()(oHistoricProcessInstance.getId(){})",oHistoricProcessInstance.getId());
                                taskQuery.taskId(oHistoricProcessInstance.getId());
                                activeTasks = taskQuery.list();//.active()
                            }
                        }
                    }*/
                }
            }
            for (Task currTask : activeTasks) {
                TaskFormData data = oFormService.getTaskFormData(currTask.getId());
                if (data != null) {
                    LOG.info("Found TaskFormData for task {}.", currTask.getId());
                    for (FormProperty property : data.getFormProperties()) {
                        mReturn.put(property.getId(), property.getValue());

                        /*String sValue = "";
                        String sType = property.getType().getName();
                        if ("enum".equalsIgnoreCase(sType)) {
                            sValue = oActionTaskService.parseEnumProperty(property);
                        } else {
                            sValue = property.getValue();
                        }
                        LOG.info("taskId=" + currTask.getId() + "propertyName=" + property.getName() + "sValue=" + sValue);
                        if (sValue != null) {
                            if (sValue.toLowerCase().contains(searchTeam)) {
                                res.add(currTask.getId());
                            }
                        }*/
                    }
                } else {
                    LOG.info("Not found TaskFormData for task {}. Skipping from processing.", currTask.getId());
                }
            }

            /*TaskFormData data = formService.getTaskFormData(nID_Task);
            Map<String, String> newProperties = new HashMap<>();
            for (FormProperty oFormProperty : data.getFormProperties()) {
                if (oFormProperty.isWritable()) {
                    newProperties.put(oFormProperty.getId(), oFormProperty.getValue());
                }
            }*/


            //EngineServices oEngineServices = execution.getEngineServices();
            //engineServices = execution.getEngineServices();
            //RuntimeService oRuntimeService = engineServices.getRuntimeService();
            /*TaskFormData oTaskFormData = oEngineServices
                    .getFormService()
                    .getTaskFormData(nID_Task);

            LOG.info("Found taskformData={}", oTaskFormData);
            if (oTaskFormData == null) {
                return;
            }*/
/*
            Collection<File> asPatterns = getFiles_PatternPrint();
            for (FormProperty oFormProperty : oTaskFormData.getFormProperties()) {
                String sFieldID = oFormProperty.getId();
                String sExpression = oFormProperty.getName();

            }
*/


        }
        return mReturn;
    }

    public Map<String, Object> getProcessVariableValue(String nProcessID, String variableName) throws RecordNotFoundException {
    	Map<String, Object> res = new HashMap<String, Object>();
    	
    	HistoricVariableInstance historicVariableInstance = oHistoryService.createHistoricVariableInstanceQuery().processInstanceId(nProcessID).variableName(variableName).singleResult();
    	
    	LOG.info("Retreived HistoricVariableInstance for process {} with value {}", nProcessID, historicVariableInstance);
    	if (historicVariableInstance != null){
    		res.put(historicVariableInstance.getVariableName(), historicVariableInstance.getValue());
    	}
    	
    	return res;
    }
    
    public boolean deleteProcess(Long nID_Order, String sLogin, String sReason) throws Exception{
        boolean success = false;
        String nID_Process = null;

        nID_Process = String.valueOf(ToolLuna.getValidatedOriginalNumber(nID_Order));

        //String sID_Order,
        String sID_Order = oGeneralConfig.getOrderId_ByOrder(nID_Order);

        HistoryEvent_Service_StatusType oHistoryEvent_Service_StatusType = HistoryEvent_Service_StatusType.REMOVED;
        String sUserTaskName = oHistoryEvent_Service_StatusType.getsName_UA();
        String sBody = sUserTaskName;
        //        String sID_status = "Заявка была удалена";
        if (sLogin != null) {
            sBody += " (" + sLogin + ")";
        }
        if (sReason != null) {
            sBody += ": " + sReason;
        }
        Map<String, String> mParam = new HashMap<>();
        mParam.put("nID_StatusType", oHistoryEvent_Service_StatusType.getnID() + "");
        mParam.put("sBody", sBody);
        LOG.info("Deleting process {}: {}", nID_Process, sUserTaskName);
        try {
            oRuntimeService.deleteProcessInstance(nID_Process, sReason);
        } catch (ActivitiObjectNotFoundException e) {
            LOG.info("Could not find process {} to delete: {}", nID_Process, e);
            throw new RecordNotFoundException();
        }

        oHistoryEventService.updateHistoryEvent(
                //processInstanceID,
                sID_Order,
                sUserTaskName, false, oHistoryEvent_Service_StatusType.REMOVED, mParam);

        success = true;
        return success;
    }
    
    
    public boolean deleteProcessSimple(String snID_Process, String sLogin, String sReason) throws Exception{
        boolean bOk = false;
        LOG.info("Deleting process snID_Process={}, sLogin={}, sReason={}", snID_Process, sLogin, sReason);
        try {
            oRuntimeService.deleteProcessInstance(snID_Process, sReason);
        } catch (ActivitiObjectNotFoundException e) {
            LOG.info("Could not find process {} to delete: {}", snID_Process, e);
            throw new RecordNotFoundException();
        }
        bOk = true;
        return bOk;
    }    

    /**
     * Загрузка задач из Activiti
     * @param sAssignee - ID авторизированого субъекта
     * @return
     */
    public List<TaskAssigneeI> getTasksByAssignee(String sAssignee){
        List<Task> tasks = oTaskService.createTaskQuery().taskAssignee(sAssignee).list();
        List<TaskAssigneeI> facadeTasks = new ArrayList<>();
        TaskAssigneeCover adapter = new TaskAssigneeCover();
        for (Task task : tasks) {
            facadeTasks.add(adapter.apply(task));
        }
        return facadeTasks;
    }

    public List<TaskAssigneeI> getTasksByAssigneeGroup(String sGroup){
        List<Task> tasks = oTaskService.createTaskQuery().taskCandidateGroup(sGroup).list();
        List<TaskAssigneeI> facadeTasks = new ArrayList<>();
        TaskAssigneeCover adapter = new TaskAssigneeCover();
        for (Task task : tasks) {
            facadeTasks.add(adapter.apply(task));
        }
        return facadeTasks;
    }

    /**
     * Поиск nID_Task по nID_Process (process instance id) независимо от того, активный этот процесс либо уже находится в архиве
     * @param nID_Process
     */
    public List<String> findTaskIDsByActiveAndHistoryProcessInstanceID(Long nID_Process) throws RecordNotFoundException {
        List<String> result = new ArrayList<>();
        List<Task> aTask = null;
        List<HistoricTaskInstance> aHistoricTask = null;
        aTask = oTaskService.createTaskQuery().processInstanceId(nID_Process.toString()).list();
        if (aTask == null || aTask.isEmpty()) {
            LOG.info(String.format("Tasks for active Process Instance [id = '%s'] not found", nID_Process));
            aHistoricTask = oHistoryService.createHistoricTaskInstanceQuery().processInstanceId(nID_Process.toString()).list();
            if(aHistoricTask == null || aHistoricTask.isEmpty()){
                LOG.error(String.format("Tasks for Process Instance [id = '%s'] not found", nID_Process));
                throw new RecordNotFoundException();
            }
            for(HistoricTaskInstance historicTask : aHistoricTask){
                result.add(historicTask.getId());
                LOG.info(String.format("Historic Task [id = '%s'] is found", historicTask.getId()));
            }
            LOG.info("Tasks for historic process instance: " + result.toString());
        }
        if(result.isEmpty()){
            for (Task task : aTask) {
                result.add(task.getId());
                LOG.info(String.format("Task [id = '%s'] is found", task.getId()));
            }
            LOG.info("Tasks for process instance: " + result.toString());
        }
        return result;
    }

    /**
     * Поиск nID_Task из активного или завершенного процесса
     * @param nID_Process - process instance ID
     * @param sID_Order
     * @param bIsFirstCreatedTask -- true - для поиска Task с более ранней датой создания
     *                            false - для поиска Task с более поздней датой создания
     */
    public Long getTaskIDbyProcess(Long nID_Process, String sID_Order, Boolean bIsFirstCreatedTask)
            throws CRCInvalidException, RecordNotFoundException {
        Long nID_Task;
        ArrayList<String> taskIDsList = new ArrayList<>();
        List<String> resultTaskIDs = null;
        if (sID_Order != null && !sID_Order.isEmpty() && !sID_Order.equals("")) {
            LOG.info("start process getting Task Data by sID_Order={}", sID_Order);
            Long ProtectedID = getIDProtectedFromIDOrder(sID_Order);
            String snID_Process = getOriginalProcessInstanceId(ProtectedID);
            nID_Process = Long.parseLong(snID_Process);
            resultTaskIDs = findTaskIDsByActiveAndHistoryProcessInstanceID(nID_Process);
        } else if (nID_Process != null) {
            LOG.info("start process getting Task Data by nID_Process={}", nID_Process);
            resultTaskIDs = findTaskIDsByActiveAndHistoryProcessInstanceID(nID_Process);
        } else {
            String massege = "All request param is NULL";
            LOG.info(massege);
            throw new RecordNotFoundException(massege);
        }
        for (String taskID : resultTaskIDs){
            taskIDsList.add(taskID);
        }

        String task = taskIDsList.get(0);

        if(taskIDsList.size() > 1){
            LOG.info("Result tasks list size: " + taskIDsList.size());
            if (bIsFirstCreatedTask){
                LOG.info("Searching Task with an earlier creation date");
            } else {
                LOG.info("Searching Task with an later creation date");
            }

            Date createDateTask, createDateTaskOpponent;

            createDateTask = getTaskDateTimeCreate(Long.parseLong(task));
            LOG.info(String.format("Task create date: ['%s']",
                    JsonDateTimeSerializer.DATETIME_FORMATTER.print(createDateTask.getTime())));

            String taskOpponent;
            for (String taskID : taskIDsList) {
                taskOpponent = taskID;
                LOG.info(String.format("Task [id = '%s'] is detect", taskID));

                createDateTaskOpponent = getTaskDateTimeCreate(Long.parseLong(taskID));


                if (bIsFirstCreatedTask){
                    if (createDateTask.after(createDateTaskOpponent)) {
                        task = taskOpponent;
                        LOG.info(String.format("Set new result Task [id = '%s']", task));
                    }
                } else {
                    if (createDateTask.before(createDateTaskOpponent)) {
                        task = taskOpponent;
                        LOG.info(String.format("Set new result Task [id = '%s']", task));
                    }
                }
            }
        }
        nID_Task = Long.parseLong(task);
        LOG.info(String.format("Task [id = '%s'] is found", nID_Task));
        return nID_Task;
    }

    /**
     * Ищет таску среди активных и архивных и возвращает дату ее создания (поиск сначала происходит среди активных Тасок, если не удается найти - ищет в архивных)
     * @param nID_Task - ИД таски
     * @return - результат метода Таски getCreateTime()
     * @throws RecordNotFoundException - в случая не возможности найти заданный ИД среди архивных тасок
     */
    public Date getTaskDateTimeCreate(Long nID_Task) throws RecordNotFoundException {
        Date result;
        String taskID = nID_Task.toString();
        try{
            result = oTaskService.createTaskQuery().taskId(taskID).singleResult().getCreateTime();
        } catch (NullPointerException e){
            LOG.info(String.format("Must search Task [id = '%s'] in history!!!", taskID));
            try {
                result = oHistoryService.createHistoricTaskInstanceQuery().taskId(taskID).singleResult().getCreateTime();
            } catch (NullPointerException e1){
                throw new RecordNotFoundException(String.format("Task [id = '%s'] not faund", taskID));
            }
        }
        LOG.info("Task id = "
                + nID_Task
                + " is created on: "
                + JsonDateTimeSerializer.DATETIME_FORMATTER.print(result.getTime()));
        return result;
    }

    /**
     * Ищет таску среди активных и архивных и возвращает ее имя или статус (поиск сначала происходит среди активных Тасок, если не удается найти - ищет в архивных)
     * @param nID_Task - ИД таски
     * @return - результат метода Таски getName()
     * @throws RecordNotFoundException - в случая не возможности найти заданный ИД среди архивных тасок
     */
    public String getTaskName(Long nID_Task) throws RecordNotFoundException {
        String result;
        String taskID = nID_Task.toString();
        try{
            result = oTaskService.createTaskQuery().taskId(taskID).singleResult().getName();
        } catch (NullPointerException e){
            LOG.info(String.format("Must search Task [id = '%s'] in history!!!", taskID));
            try {
                result = oHistoryService.createHistoricTaskInstanceQuery().taskId(taskID).singleResult().getName();
            } catch (NullPointerException e1){
                throw new RecordNotFoundException(String.format("Task [id = '%s'] not faund", taskID));
            }
        }
        LOG.info("Task id = "
                + nID_Task
                + "; name is: "
                + result);
        return result;
    }


    /**
     * Ищет таску среди активных и архивных и возвращает ее имя или статус (поиск сначала происходит среди активных Тасок, если не удается найти - ищет в архивных)
     * @param nID_Task - ИД таски
     * @return - результат метода Таски getName()
     * @throws RecordNotFoundException - в случая не возможности найти заданный ИД среди архивных тасок
     */
    public Map<String,String> getTaskData(Long nID_Task) throws RecordNotFoundException {
        SimpleDateFormat oDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Map<String,String> m = new HashMap();
        //String result;
        String snID_Task = nID_Task.toString();
        try{
            //result = oTaskService.createTaskQuery().taskId(snID_Task).singleResult().getName();
            //m.put("sDateEnd", oActionTaskService.getsIDUserTaskByTaskId(nID_Task));
            Task oTask=oTaskService.createTaskQuery().taskId(snID_Task).singleResult();
            m.put("sLoginAssigned", oTask.getAssignee());
            //m.put("sDateEnd", oDateFormat.format(oTask.getCreateTime()));
        /*return oHistoryService.createHistoricTaskInstanceQuery()
                .taskId(nID_Task.toString()).singleResult().getTaskDefinitionKey();
        */
		//taskInfo.put("createTime", oDateFormat.format(task.getCreateTime()));
            
        } catch (NullPointerException e){
            LOG.info(String.format("Must search Task [id = '%s'] in history!!!", snID_Task));
            try {
                //oTask = oHistoryService.createHistoricTaskInstanceQuery().taskId(snID_Task).singleResult().getName();
                HistoricTaskInstance oTask = oHistoryService.createHistoricTaskInstanceQuery().taskId(snID_Task).singleResult();
                m.put("sLoginAssigned", oTask.getAssignee());
                m.put("sDateEnd", oDateFormat.format(oTask.getCreateTime()));
            } catch (NullPointerException e1){
                throw new RecordNotFoundException(String.format("Task [id = '%s'] not faund", snID_Task));
            }
        }
        LOG.info("Task id = " + nID_Task + "; m = " + m);
        return m;
    }
    
    
        
        
    
    
    /*public static String parseEnumProperty(FormProperty property) {
        Object oValues = property.getType().getInformation("values");
        if (oValues instanceof Map) {
            Map<String, String> mValue = (Map) oValues;
            LOG.info("(m={})", mValue);
            String sName = property.getValue();
            LOG.info("(sName={})", sName);
            String sValue = mValue.get(sName);
            LOG.info("(sValue={})", sValue);
            return parseEnumValue(sValue);
        } else {
            LOG.error("Cannot parse values for property - {}", property);
            return "";
        }
    }*/
    //public HashMap<String, Object> getFormPropertiesMapByTaskID(Long nID_Task) {
    public List<Map<String,Object>> getFormPropertiesMapByTaskID(Long nID_Task) {
        List<FormProperty> a = oFormService.getTaskFormData(nID_Task.toString()).getFormProperties();
        List<Map<String,Object>> aReturn = new LinkedList();
        Map<String,Object> mReturn;
        //a.get(1).getType().getInformation()
        for (FormProperty oProperty : a) {
            mReturn=new HashMap();
            //String sValue = "";
            //String sValue = oProperty.getValue();
            mReturn.put("sValue", oProperty.getValue());
            String sType = oProperty.getType() != null ? oProperty.getType().getName() : "";
            mReturn.put("sType", sType);
            mReturn.put("sID", oProperty.getId());
            mReturn.put("sName", oProperty.getName());
            mReturn.put("bReadable", oProperty.isReadable());
            mReturn.put("bWritable", oProperty.isWritable());
            mReturn.put("bRequired", oProperty.isRequired());
            if ("enum".equalsIgnoreCase(sType)) {
                //sValue = oActionTaskService.parseEnumProperty(oProperty);
                Object oEnums = oProperty.getType().getInformation("values");
                if (oEnums instanceof Map) {
                    Map<String, String> mEnum = (Map) oEnums;
//                    LOG.info("(mEnum={})", mEnum);
                    mReturn.put("mEnum", mEnum);
//                    String sName = oProperty.getValue();
//                    LOG.info("(sName={})", sName);
//                    String sValue = mValue.get(sName);
//                    LOG.info("(sValue={})", sValue);
//                    return parseEnumValue(sValue);
//                } else {
//                    LOG.error("Cannot parse values for property - {}", property);
//                    return "";
                }
            /*} else {
                sValue = oProperty.getValue();*/
            }
//            LOG.info("(nID_Task={}, sType={}, propertyName={}, sValue={})", nID_Task, sType, oProperty.getName(), sValue);
//            if (sValue != null) {
                //if (sValue.toLowerCase().contains(searchTeam)) {
                //    res.add(currTask.getId());
                //}
//            }
//            LOG.info("(nID_Task={}, mReturn={})", nID_Task, mReturn);
            aReturn.add(mReturn);
        }        
        return aReturn;
    }
    
    public List<FormProperty> getFormPropertiesByTaskID(Long nID_Task) {
        List<FormProperty> a = oFormService.getTaskFormData(nID_Task.toString()).getFormProperties();
        //a.get(1).getType().getInformation()
        return a;
    }

    /**
     * Получение массива полей propertyId и propertyValue из HistoricFormProperty
     * @param nID_Task - ID-номер таски, которая находится в архиве
     * @return
     * @throws RecordNotFoundException
     */
    public List<Map<String, String>> getHistoricFormPropertiesByTaskID(Long nID_Task) throws RecordNotFoundException {
        List<Map<String, String>> aReturn = new ArrayList<>();
        List<HistoricDetail> aHistoricDetail = oHistoryService.createHistoricDetailQuery().taskId(nID_Task.toString()).formProperties().list();
        LOG.info("(aHistoricDetail={})", aHistoricDetail);
        if (aHistoricDetail == null) {
            throw new RecordNotFoundException("aHistoricDetail");
        }
        for (HistoricDetail oHistoricDetail : aHistoricDetail) {
            Map<String, String> mReturn = new HashMap<>();
            HistoricFormProperty oHistoricFormProperty = (HistoricFormProperty) oHistoricDetail;
            //oHistoricFormPropertyCover.put("id", historicFormProperty.getPropertyId());
            //oHistoricFormPropertyCover.put("value", historicFormProperty.getPropertyValue());
            mReturn.put("sID", oHistoricFormProperty.getPropertyId());
            mReturn.put("sValue", oHistoricFormProperty.getPropertyValue());
            aReturn.add(mReturn);
        }
//        LOG.info("(List oHistoricFormPropertyCover = {})", aReturn);
        return aReturn;
    }

    /**
     * Проверка наличия полей электронной очереди и парсинг их контекста
     * @param aFormProperties
     * @return
     */
    public Map<String, Object> getQueueData(List<FormProperty> aFormProperties){
        Map<String, Object> result = null;
        List<FormProperty> aFormPropertiesQueueDataType = new ArrayList<>();
        if (aFormProperties == null || aFormProperties.isEmpty()){
            LOG.info("List<FormProperty> is NULL");

        } else {
            for (FormProperty oFormProperty : aFormProperties) {
                if (oFormProperty.getType() instanceof QueueDataFormType) {
                    aFormPropertiesQueueDataType.add(oFormProperty);
                }
            }
        }

        if (aFormPropertiesQueueDataType.isEmpty()){
            LOG.info("The array does not contain elements of the QueueData");
        } else {
            result = new HashMap<>();
            for (FormProperty field : aFormPropertiesQueueDataType){
                result.put(field.getType().getName(), parseQueueDataFromFormProperty(field));
            }
        }
        LOG.info("getQueueData result = {}", result);
        return result;
    }

    private Map<String, Object> parseQueueDataFromFormProperty(FormProperty oFormProperty){
        Map<String, Object> mItemReturn = new HashMap<>();
        Map<String, Object> mPropertyReturn = new HashMap<>();
        String sValue = oFormProperty.getValue();
        LOG.info("sValue = {}", sValue);
        Map<String, Object> m = QueueDataFormType.parseQueueData(sValue);
        String sDate = (String) m.get(QueueDataFormType.sDate);
        LOG.info("(sDate={})" + sDate);
        String sID_Type = QueueDataFormType.get_sID_Type(m);
        LOG.info("(sID_Type={})", sID_Type);
        if("DMS".equals(sID_Type)){
        //}else if("iGov".equals(sID_Type)){
            String snID_ServiceCustomPrivate = m.get("nID_ServiceCustomPrivate")+"";
            LOG.info("(nID_ServiceCustomPrivate={})", snID_ServiceCustomPrivate);
            String sTicket_Number = (String) m.get("ticket_number");
            LOG.info("(sTicket_Number={})", sTicket_Number);
            String sTicket_Code = (String) m.get("ticket_code");
            LOG.info("(sTicket_Code={})", sTicket_Code);
            //element.put("nID_FlowSlotTicket", sTicket_Number);
            mPropertyReturn.put("snID_ServiceCustomPrivate", snID_ServiceCustomPrivate);
            mPropertyReturn.put("sTicket_Number", sTicket_Number);
            mPropertyReturn.put("sTicket_Code", sTicket_Code);
        }else{
            Long nID_FlowSlotTicket = QueueDataFormType.get_nID_FlowSlotTicket(m);
            LOG.info("(nID_FlowSlotTicket={})", nID_FlowSlotTicket);
            mPropertyReturn.put("nID_FlowSlotTicket", nID_FlowSlotTicket);
        }
        mPropertyReturn.put("sDate", sDate);
        mItemReturn.put(oFormProperty.getId(), mPropertyReturn);
        return mItemReturn;
    }

    /**
     * Получение массива отождествленных групп по Task
     * @param nID_Task - Task ID
     * @return - CandidateGroup from ProcessDefinition by Task
     */
    private Set<String> getCandidateGroupByTaskID(Long nID_Task){
        Set<String> aCandidateGroup = new HashSet<>();
        ProcessDefinition processDefinition = getProcessDefinitionByTaskID(nID_Task.toString());
        loadCandidateGroupsFromTasks(processDefinition, aCandidateGroup);
        return aCandidateGroup;
    }



    /**
     * Возвращает список объектов Attachment, привязанных к таске
     * @param nID_Task - ИД-номер таски
     */
    public List<Attachment> getAttachmentsByTaskID(Long nID_Task){
        LOG.info(String.format("Start load Attachment object by Task [id = '%s']", nID_Task));
        List<Attachment> attachments = oTaskService.getTaskAttachments(nID_Task.toString());
        if (attachments.isEmpty()){
           LOG.info(String.format("No attachments in the Task [id = '%s']", nID_Task));
        } else {
            List<String> attachmetIDs = new ArrayList<>();
            for (Attachment attachment : attachments){
                attachmetIDs.add(attachment.getId());
            }
            LOG.info("Task attachmets: " + attachmetIDs.toString());
        }
        return attachments;
    }
    
    /**
     * 
     * @param taskQuery
     * @param nStart
     * @param nSize
     * @param bFilterHasTicket
     * @param mapOfTickets
     * @return
     */
    public List<TaskInfo> getTasksWithTicketsFromQuery(Object taskQuery, int nStart, int nSize, boolean bFilterHasTicket, Map<String, FlowSlotTicket> mapOfTickets){
	    List<TaskInfo> tasks = (taskQuery instanceof TaskInfoQuery) ? ((TaskInfoQuery) taskQuery).listPage(nStart, nSize)
				: (List) ((NativeTaskQuery) taskQuery).listPage(nStart, nSize);
	
		List<Long> taskIds = new LinkedList<Long>();
		for (int i = 0; i < tasks.size(); i++){
			TaskInfo currTask = tasks.get(i);
			if (currTask.getProcessInstanceId() != null){
				taskIds.add(Long.valueOf(currTask.getProcessInstanceId()));
			}
		}
		LOG.info("Preparing to select flow slot tickets. taskIds:{}", taskIds.toString());
		List<FlowSlotTicket> tickets  = new LinkedList<FlowSlotTicket>();
		if (taskIds.size() == 0){
			return tasks;
		}
		try {
			tickets = flowSlotTicketDao.findAllByInValues("nID_Task_Activiti", taskIds);
		} catch (Exception e){
			LOG.error("Error occured while getting tickets for tasks", e);
		}
		LOG.info("Found {} tickets for specified list of tasks IDs", tickets.size());
		if (tickets != null) {
			for (FlowSlotTicket ticket : tickets) {
				mapOfTickets.put(ticket.getnID_Task_Activiti().toString(), ticket);
			}
		}
		if (bFilterHasTicket) {
			LOG.info("Removing tasks which don't have flow slot tickets");
			Iterator<TaskInfo> iter = tasks.iterator();
			while (iter.hasNext()){
				TaskInfo curr = iter.next();
				if (!mapOfTickets.keySet().contains(curr.getProcessInstanceId())){
					LOG.info("Removing tasks with ID {}", curr.getId());
					iter.remove();
				}
			}
		}
		return tasks;
    }
    
    public List<TaskInfo> matchTasksWithTicketsFromQuery(final String sLogin, boolean bIncludeAlienTickets, String sFilterStatus,
    		List<TaskInfo> tasks){
	
		final List<Long> taskIds = new LinkedList<Long>();
		for (int i = 0; i < tasks.size(); i++){
			TaskInfo currTask = tasks.get(i);
			if (currTask.getProcessInstanceId() != null){
				taskIds.add(Long.valueOf(currTask.getProcessInstanceId()));
			}
		}
		LOG.info("Preparing to select flow slot tickets. taskIds:{}", taskIds.toString());
		if (taskIds.size() == 0){
			return tasks;
		}
		SerializableResponseEntity<ArrayList<FlowSlotTicket>> entities = cachedInvocationBean
	            .invokeUsingCache(new CachedInvocationBean.Callback<SerializableResponseEntity<ArrayList<FlowSlotTicket>>>(
	                    GET_ALL_TICKETS_CACHE, sLogin, bIncludeAlienTickets, sFilterStatus) {
	                @Override
	                public SerializableResponseEntity<ArrayList<FlowSlotTicket>> execute() {
	                	LOG.info("Loading tickets from cache for user {}", sLogin);

	                	ArrayList<FlowSlotTicket> res = (ArrayList<FlowSlotTicket>) flowSlotTicketDao.findAllByInValues("nID_Task_Activiti", taskIds);
	                    
	                    return new SerializableResponseEntity<>(new ResponseEntity<>(res, null, HttpStatus.OK));
	                }
	            });
		ArrayList<FlowSlotTicket> tickets = entities.getBody();
		LOG.info("Found {} tickets for specified list of tasks IDs", tickets.size());
		Map<String, FlowSlotTicket> mapOfTickets = new HashMap<String, FlowSlotTicket>();
		if (tickets != null) {
			for (FlowSlotTicket ticket : tickets) {
				mapOfTickets.put(ticket.getnID_Task_Activiti().toString(), ticket);
			}
		}
		LOG.info("Removing tasks which don't have flow slot tickets");
		LinkedList<TaskInfo> res = new LinkedList<TaskInfo>();
		Iterator<TaskInfo> iter = tasks.iterator();
		while (iter.hasNext()){
			TaskInfo curr = iter.next();
			if (mapOfTickets.keySet().contains(curr.getProcessInstanceId())){
				LOG.info("Adding tasks with ID {} to the response", curr.getId());
				res.add(curr);
			}
		}
		return res;
    }
    
    public long getCountOfTasksForGroups(List<String> groupsIds) {
		StringBuilder groupIdsSB = new StringBuilder();
		for (int i = 0; i < groupsIds.size(); i++){
			groupIdsSB.append("'");
			groupIdsSB.append(groupsIds.get(i));
			groupIdsSB.append("'");
			if (i < groupsIds.size() - 1){
				groupIdsSB.append(",");
			}
		}
		
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT count(task.*) FROM ACT_RU_TASK task, ACT_RU_IDENTITYLINK link WHERE task.ID_ = link.TASK_ID_ AND link.GROUP_ID_ IN(");
		sql.append(groupIdsSB.toString());
		sql.append(") ");
		
		return oTaskService.createNativeTaskQuery().sql(sql.toString()).count();
	}

	public void populateResultSortedByTasksOrder(boolean bFilterHasTicket,
			List<?> tasks, Map<String, FlowSlotTicket> mapOfTickets,
			List<Map<String, Object>> data) {
		LOG.info("populateResultSortedByTasksOrder. number of tasks:{} number of tickets:{} ", tasks.size(), mapOfTickets.size());
		for (int i = 0; i < tasks.size(); i++){
			try {
				TaskInfo task = (TaskInfo)tasks.get(i);
				Map<String, Object> taskInfo = populateTaskInfo(task, mapOfTickets.get(task.getProcessInstanceId()));
				
				data.add(taskInfo);
			} catch (Exception e){
				LOG.error("error: Error while populatiing task", e);
			}
		}
	}

	public void populateResultSortedByTicketDate(boolean bFilterHasTicket, List<?> tasks,
			Map<String, FlowSlotTicket> mapOfTickets, List<Map<String, Object>> data) {
		LOG.info("Sorting result by flow slot ticket create date. Number of tasks:{} number of tickets:{}", tasks.size(), mapOfTickets.size());
		List<FlowSlotTicket> tickets = new LinkedList<FlowSlotTicket>();
		tickets.addAll(mapOfTickets.values());
		Collections.sort(tickets, FLOW_SLOT_TICKET_ORDER_CREATE_COMPARATOR);
		LOG.info("Sorted tickets by order create date");
		Map<String, TaskInfo> tasksMap = new HashMap<String, TaskInfo>();
		for (int i = 0; i < tasks.size(); i++){
			TaskInfo task = (TaskInfo)tasks.get(i);
			tasksMap.put(((TaskInfo)tasks.get(i)).getProcessInstanceId(), task);
		}
		for (int i = 0; i < tickets.size(); i++){
			try {
				FlowSlotTicket ticket = tickets.get(i);
				TaskInfo task = tasksMap.get(ticket.getnID_Task_Activiti());
				Map<String, Object> taskInfo = populateTaskInfo(task, ticket);
				
				data.add(taskInfo);
			} catch (Exception e){
				LOG.error("error: ", e);
			}
		}
	}
    
	public List<TaskInfo> returnTasksFromCache(final String sLogin, final String sFilterStatus, final boolean bIncludeAlienAssignedTasks,
			final List<String> groupsIds){
		SerializableResponseEntity<ArrayList<TaskInfo>> entity = cachedInvocationBean
            .invokeUsingCache(new CachedInvocationBean.Callback<SerializableResponseEntity<ArrayList<TaskInfo>>>(
                    GET_ALL_TASK_FOR_USER_CACHE, sLogin, sFilterStatus, bIncludeAlienAssignedTasks) {
                @Override
                public SerializableResponseEntity<ArrayList<TaskInfo>> execute() {
                	LOG.info("Loading tasks from cache for user {} with filterStatus {} and bIncludeAlienAssignedTasks {}", sLogin, sFilterStatus, bIncludeAlienAssignedTasks);
                	Object taskQuery = createQuery(sLogin, bIncludeAlienAssignedTasks, null, sFilterStatus, groupsIds);
                	
                	ArrayList<TaskInfo> res = (ArrayList<TaskInfo>) ((taskQuery instanceof TaskInfoQuery) ? ((TaskInfoQuery) taskQuery).list()
            				: (List) ((NativeTaskQuery) taskQuery).list());
                	
                    LOG.info("Loaded {} tasks", res.size());
                    return new SerializableResponseEntity<>(new ResponseEntity<>(res, null, HttpStatus.OK));
                }
            });
		LOG.info("Entity {}", entity.toString());
		return entity.getBody();
	}
	
	public Object createQuery(String sLogin,
			boolean bIncludeAlienAssignedTasks, String sOrderBy, String sFilterStatus,
			List<String> groupsIds) {
		Object taskQuery = null; 
		if ("Closed".equalsIgnoreCase(sFilterStatus)){
			taskQuery = oHistoryService.createHistoricTaskInstanceQuery().taskInvolvedUser(sLogin).finished();
			if ("taskCreateTime".equalsIgnoreCase(sOrderBy)){
				 ((TaskInfoQuery)taskQuery).orderByTaskCreateTime();
			} else {
				 ((TaskInfoQuery)taskQuery).orderByTaskId();
			}
			 ((TaskInfoQuery)taskQuery).asc();
		} else {
			if (bIncludeAlienAssignedTasks){
				StringBuilder groupIdsSB = new StringBuilder();
				for (int i = 0; i < groupsIds.size(); i++){
					groupIdsSB.append("'");
					groupIdsSB.append(groupsIds.get(i));
					groupIdsSB.append("'");
					if (i < groupsIds.size() - 1){
						groupIdsSB.append(",");
					}
				}
				
				StringBuilder sql = new StringBuilder();
				sql.append("SELECT task.* FROM ACT_RU_TASK task, ACT_RU_IDENTITYLINK link WHERE task.ID_ = link.TASK_ID_ AND link.GROUP_ID_ IN(");
				sql.append(groupIdsSB.toString());
				sql.append(") ");
				
				if ("taskCreateTime".equalsIgnoreCase(sOrderBy)){
					 sql.append(" order by task.CREATE_TIME_ asc");
				} else {
					 sql.append(" order by task.ID_ asc");
				}
				LOG.info("Query to execute {}", sql.toString());
				taskQuery = oTaskService.createNativeTaskQuery().sql(sql.toString());
			}  else {
				taskQuery = oTaskService.createTaskQuery();
				if ("OpenedUnassigned".equalsIgnoreCase(sFilterStatus)){
					((TaskQuery)taskQuery).taskCandidateUser(sLogin);
				} else if ("OpenedAssigned".equalsIgnoreCase(sFilterStatus)){
					taskQuery =  ((TaskQuery)taskQuery).taskAssignee(sLogin);
				} else if ("Opened".equalsIgnoreCase(sFilterStatus)){
					taskQuery = ((TaskQuery)taskQuery).taskCandidateOrAssigned(sLogin);
				}
				if ("taskCreateTime".equalsIgnoreCase(sOrderBy)){
					 ((TaskQuery)taskQuery).orderByTaskCreateTime();
				} else {
					 ((TaskQuery)taskQuery).orderByTaskId();
				}
				 ((TaskQuery)taskQuery).asc();
			}
		}
		return taskQuery;
	}

	public Map<String, Object> populateTaskInfo(TaskInfo task, FlowSlotTicket flowSlotTicket) {
        HistoricProcessInstance processInstance = oHistoryService.createHistoricProcessInstanceQuery().
        		processInstanceId(task.getProcessInstanceId()).
        		includeProcessVariables().singleResult();
        String sPlace = processInstance.getProcessVariables().containsKey("sPlace") ? (String) processInstance.getProcessVariables().get("sPlace") + " ": "";
        LOG.info("Found process instance with variables. sPlace {} taskId {} processInstanceId {}", sPlace, task.getId(), task.getProcessInstanceId());
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		Map<String, Object> taskInfo = new HashMap<String, Object>();
		taskInfo.put("id", task.getId());
		taskInfo.put("url", oGeneralConfig.getSelfHost() + "/wf/service/runtime/tasks/" + task.getId());
		taskInfo.put("owner", task.getOwner());
		taskInfo.put("assignee", task.getAssignee());
		taskInfo.put("delegationState", (task instanceof Task) ? ((Task)task).getDelegationState() : null);
		taskInfo.put("name", sPlace + task.getName());
		taskInfo.put("description", task.getDescription());
		taskInfo.put("createTime", sdf.format(task.getCreateTime()));
		taskInfo.put("dueDate", task.getDueDate() != null ? sdf.format(task.getDueDate()) : null);
		taskInfo.put("priority", task.getPriority());
		taskInfo.put("suspended", (task instanceof Task) ? ((Task)task).isSuspended() : null);
		taskInfo.put("taskDefinitionKey", task.getTaskDefinitionKey());
		taskInfo.put("tenantId", task.getTenantId());
		taskInfo.put("category", task.getCategory());
		taskInfo.put("formKey", task.getFormKey());
		taskInfo.put("parentTaskId", task.getParentTaskId());
		taskInfo.put("parentTaskUrl", "");
		taskInfo.put("executionId", task.getExecutionId());
		taskInfo.put("executionUrl", oGeneralConfig.getSelfHost() + "/wf/service/runtime/executions/" + task.getExecutionId());
		taskInfo.put("processInstanceId", task.getProcessInstanceId());
		taskInfo.put("processInstanceUrl", oGeneralConfig.getSelfHost() + "/wf/service/runtime/process-instances/" + task.getProcessInstanceId());
		taskInfo.put("processDefinitionId", task.getProcessDefinitionId());
		taskInfo.put("processDefinitionUrl", oGeneralConfig.getSelfHost() + "/wf/service/repository/process-definitions/" + task.getProcessDefinitionId());
		taskInfo.put("variables", new LinkedList());
		if (flowSlotTicket != null){
			LOG.info("Populating flow slot ticket");
			DateTimeFormatter dtf = org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd_HH-mm-ss");
			Map<String, Object> flowSlotTicketData = new HashMap<String, Object>();
			flowSlotTicketData.put("nID", flowSlotTicket.getId());
			flowSlotTicketData.put("nID_Subject", flowSlotTicket.getnID_Subject());
			flowSlotTicketData.put("sDateStart", flowSlotTicket.getsDateStart() != null ? dtf.print(flowSlotTicket.getsDateStart()): null);
			flowSlotTicketData.put("sDateFinish", flowSlotTicket.getsDateFinish() != null ? dtf.print(flowSlotTicket.getsDateFinish()): null);
			taskInfo.put("flowSlotTicket", flowSlotTicketData);
		}
		return taskInfo;
	}

    /**
     * Get sID_UserTask by nID_Task
     * @param nID_Task
     * @return sID_UserTask
     */
    public String getsIDUserTaskByTaskId(Long nID_Task){
        return oHistoryService.createHistoricTaskInstanceQuery()
                .taskId(nID_Task.toString()).singleResult().getTaskDefinitionKey();
    }

    /**
     * Получить список идентификаторов отождествленных групп по таске
     * @param nID_Task - идентификатор таски
     * @see IdentityLink#getGroupId()
     * @see HistoricIdentityLink#getGroupId()
     */
    public Set<String> getGroupIDsByTaskID(Long nID_Task){
        LOG.info(String.format("Start extraction Group IDs for Task [id=%s]", nID_Task));
        Set<String> result = new HashSet<>();
        try {
            List<IdentityLink> identityLinks = oTaskService.getIdentityLinksForTask(nID_Task.toString());
            for (IdentityLink link : identityLinks){
                LOG.info(String.format("Extraction Group ID from IdentityLink %s", link.toString()));
                if(link.getGroupId() == null || link.getGroupId().isEmpty()){
                    LOG.info(String.format("Not found Group in IdentityLink %s", link.toString()));
                } else {
                    result.add(link.getGroupId());
                    LOG.info(String.format("Add Group id=%s for active Task id=%s from IdentityLink %s",
                            link.getGroupId(), nID_Task, link.toString()));
                }
            }
        } catch (NullPointerException e) {
            try {
                List<HistoricIdentityLink> historicIdentityLinks = oHistoryService.getHistoricIdentityLinksForTask(nID_Task.toString());
                for (HistoricIdentityLink link : historicIdentityLinks){
                    LOG.info(String.format("Extraction Group ID from HistoricIdentityLink %s", link.toString()));
                    if(link.getGroupId() == null || link.getGroupId().isEmpty()){
                        LOG.info(String.format("Not found Group in HistoricIdentityLink %s", link.toString()));
                    } else {
                        result.add(link.getGroupId());
                        LOG.info(String.format("Add Group id=%s for historic Task id=%s from HistoricIdentityLink %s",
                                link.getGroupId(), nID_Task, link.toString()));
                    }
                }
            } catch (NullPointerException eh) {
                LOG.info(String.format("No found Group id for Task id=%s", nID_Task));
            }
        }

        return result;
    }

    /**
     * Проверяет вхождение пользователя в одну из груп, на которую распространяется тиска
     * @param sLogin - логгин пользователя
     * @param nID_Task - ИД-номер таски
     * @return true - если пользователь входит в одну из групп; false - если совпадений не найдено.
     */
    public boolean checkAvailabilityTaskGroupsForUser(String sLogin, Long nID_Task){

        ProcessDefinition BP_Task = getProcessDefinitionByTaskID(nID_Task.toString());
        List<ProcessDefinition> aBP_Task = new LinkedList<>();
        aBP_Task.add(BP_Task);

        List<ProcessDefinition> result = new LinkedList<>();
        result = getAvailabilityProcessDefinitionByLogin(sLogin, aBP_Task);

        if (CollectionUtils.isNotEmpty(result)){
            return true;
        }

        return false;
    }

}

