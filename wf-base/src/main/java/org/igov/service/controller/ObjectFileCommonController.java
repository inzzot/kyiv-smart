package org.igov.service.controller;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.task.Attachment;
import org.activiti.engine.task.Task;
import org.apache.commons.io.IOUtils;
import org.igov.io.GeneralConfig;
import org.igov.io.db.kv.temp.IBytesDataInmemoryStorage;
import org.igov.io.db.kv.temp.exception.RecordInmemoryException;
import org.igov.io.db.kv.temp.model.ByteArrayMultipartFile;
import org.igov.model.action.task.core.AttachmentCover;
import org.igov.model.action.task.core.BuilderAttachModelCover;
import org.igov.model.action.task.core.entity.AttachmentEntityI;
//import org.igov.service.business.access.BankIDConfig;
import org.igov.service.business.access.BankIDUtils;
import org.igov.service.business.action.task.core.AbstractModelTask;
import org.igov.service.business.action.task.core.ActionTaskService;
import org.igov.service.business.action.task.systemtask.FileTaskUpload;
import org.igov.service.business.object.ObjectFileService;
import org.igov.service.exception.CommonServiceException;
import org.igov.service.exception.FileServiceIOException;
import org.igov.util.VariableMultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.igov.io.fs.FileSystemData;

import static org.igov.io.fs.FileSystemData.getFileData_Pattern;
import static org.igov.service.business.action.task.core.AbstractModelTask.getByteArrayMultipartFileFromStorageInmemory;
import static org.igov.util.Tool.sTextTranslit;

/**
 * @author BW
 */

@Controller
@Api(tags = { "ObjectFileCommonController -- Обьекты файлов общие" })
@RequestMapping(value = "/object/file")
public class ObjectFileCommonController {
    
    public static final String PATTERN_DEFAULT_CONTENT_TYPE = "text/plain";
    private static final Logger LOG = LoggerFactory.getLogger(ObjectFileCommonController.class);
    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private IBytesDataInmemoryStorage oBytesDataInmemoryStorage;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private GeneralConfig generalConfig;
    //@Autowired
    //private BankIDConfig bankIDConfig;

    @Autowired
    private ActionTaskService oActionTaskService;

    @Autowired
    private ObjectFileService oObjectFileService;

    @ApiOperation(value = "PutAttachmentsToRedis", notes = "#####  Укладываем в редис multipartFileToByteArray\n")
    @RequestMapping(value = "/upload_file_to_redis", method = RequestMethod.POST)
    @Transactional
    public
    @ResponseBody
    String putAttachmentsToRedis(
            @RequestParam(required = true, value = "file") MultipartFile file)
            throws FileServiceIOException {
        try {
            LOG.info("file.getContentType: " + file.getContentType());
            String key = oBytesDataInmemoryStorage.putBytes(AbstractModelTask
                    .multipartFileToByteArray(file, file.getOriginalFilename())
                    .toByteArray());
            return key;
        } catch (RecordInmemoryException | IOException e) {
            LOG.warn(e.getMessage(), e);
            throw new FileServiceIOException(
                    FileServiceIOException.Error.REDIS_ERROR, e.getMessage());
        }
    }

    @ApiOperation(value = "GetAttachmentsFromRedis", notes = "#####  ObjectFileCommonController: описания нет\n")
    @RequestMapping(value = "/download_file_from_redis", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    byte[] getAttachmentsFromRedis(
            @RequestParam("key") String key) throws FileServiceIOException {
        byte[] upload = null;
        try {
            upload = oBytesDataInmemoryStorage.getBytes(key);
        } catch (RecordInmemoryException e) {
            LOG.warn(e.getMessage(), e);
            throw new FileServiceIOException(
                    FileServiceIOException.Error.REDIS_ERROR, e.getMessage());
        }
        return upload;
    }

    @ApiOperation(value = "GetAttachmentsFromRedisBytes", notes = "#####  ObjectFileCommonController: описания нет\n")
    @RequestMapping(value = "/download_file_from_redis_bytes", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    byte[] getAttachmentsFromRedisBytes(
            @RequestParam("key") String key) throws FileServiceIOException {
        byte[] upload = null;
        try {
            byte[] aByteFile = oBytesDataInmemoryStorage.getBytes(key);
            ByteArrayMultipartFile oByteArrayMultipartFile = null;
            oByteArrayMultipartFile = getByteArrayMultipartFileFromStorageInmemory(aByteFile);

            if (oByteArrayMultipartFile != null) {

                upload = oByteArrayMultipartFile.getBytes();

            } else {
                // LOG.error("[getAttachmentsFromRedisBytes]oByteArrayMultipartFile==null! aByteFile="
                // + aByteFile.
                // .toString());
                // Unreachable code?
                LOG.error("[getAttachmentsFromRedisBytes]oByteArrayMultipartFile==null! key={}", key);
            }

        } catch (RecordInmemoryException e) {
            LOG.warn("Error: {}", e.getMessage(), e);
            throw new FileServiceIOException(
                    FileServiceIOException.Error.REDIS_ERROR, e.getMessage());
        } catch (ClassNotFoundException | IOException e) {
            LOG.error("Error: {}", e.getMessage(), e);
            throw new ActivitiException(e.getMessage(), e);
        }

        return upload;
    }

    @ApiOperation(value = "Проверка ЭЦП на файле хранящемся в Redis", notes = "#####  Примеры:\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/check_file_from_redis_sign?sID_File_Redis=d2993755-70e5-409e-85e5-46ba8ce98e1d\n\n"
            + "Ответ json описывающий ЭЦП:\n\n"
            + "\n```json\n"
            + "{\n"
            + "  \"state\": \"ok\",\n"
            + "  \"customer\": {\n"
            + "    \"inn\": \"1436057000\",\n"
            + "    \"fullName\": \"Сервіс зберігання сканкопій\",\n"
            + "    \"signatureData\": {\n"
            + "      \"name\": \"АЦСК ПАТ КБ «ПРИВАТБАНК»\",\n"
            + "      \"serialNumber\": \"0D84EDA1BB9381E80400000079DD02004A710800\",\n"
            + "      \"timestamp\": \"29.10.2015 13:45:33\",\n"
            + "      \"code\": true,\n"
            + "      \"desc\": \"ПІДПИС ВІРНИЙ\",\n"
            + "      \"dateFrom\": \"13.08.2015 11:24:31\",\n"
            + "      \"dateTo\": \"12.08.2016 23:59:59\",\n"
            + "      \"sn\": \"UA-14360570-1\"\n"
            + "    },\n"
            + "    \"organizations\": [\n"
            + "      {\n"
            + "        \"type\": \"edsOwner\",\n"
            + "        \"name\": \"ПАТ КБ «ПРИВАТБАНК»\",\n"
            + "        \"mfo\": \"14360570\",\n"
            + "        \"position\": \"Технологічний сертифікат\",\n"
            + "        \"ownerDesc\": \"Співробітник банку\",\n"
            + "        \"address\": {\n"
            + "          \"type\": \"factual\",\n"
            + "          \"state\": \"Дніпропетровська\",\n"
            + "          \"city\": \"Дніпропетровськ\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"type\": \"edsIsuer\",\n"
            + "        \"name\": \"ПУБЛІЧНЕ АКЦІОНЕРНЕ ТОВАРИСТВО КОМЕРЦІЙНИЙ БАНК «ПРИВАТБАНК»\",\n"
            + "        \"unit\": \"АЦСК\",\n"
            + "        \"address\": {\n"
            + "          \"type\": \"factual\",\n"
            + "          \"state\": \"Дніпропетровська\",\n"
            + "          \"city\": \"Дніпропетровськ\"\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}\n"
            + "\n```\n"
            + "Ответ для несуществующего ключа (sID_File_Redis):\n"
            + "\n```json\n"
            + "{\"code\":\"SYSTEM_ERR\",\"message\":\"File with sID_File_Redis 'd2993755-70e5-409e-85e5-46ba8ce98e1e' not found.\"}\n\n"
            + "\n```\n"
            + "Ответ для файла который не имеет наложеной ЭЦП:\n\n"
            + "\n```json\n"
            + "{}\n"
            + "\n```\n")
    @RequestMapping(value = "/check_file_from_redis_sign", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    @Transactional
    public
    @ResponseBody
    String checkAttachmentsFromRedisSign(
            @ApiParam(value = "строка-ключ по которому можно получить файл из хранилища Redis", required = true) @RequestParam("sID_File_Redis") String sID_File_Redis)
            throws FileServiceIOException {
        byte[] upload = null;
        String fileName = null;
        try {
            byte[] aByteFile = oBytesDataInmemoryStorage.getBytes(sID_File_Redis);
            ByteArrayMultipartFile oByteArrayMultipartFile = null;

            if (aByteFile == null) {
                throw new ActivitiObjectNotFoundException(
                        "File with sID_File_Redis '" + sID_File_Redis
                                + "' not found.");
            }
            try {
                oByteArrayMultipartFile = getByteArrayMultipartFileFromStorageInmemory(aByteFile);
            } catch (ClassNotFoundException | IOException e1) {
                throw new ActivitiException(e1.getMessage(), e1);
            }
            if (oByteArrayMultipartFile != null) {

                upload = oByteArrayMultipartFile.getBytes();
                fileName = oByteArrayMultipartFile.getName();

            } else {
                // /
                // LOG.error("[checkAttachmentsFromRedisSign]oByteArrayMultipartFile==null! aByteFile="
                // + aByteFile
                // / .toString());
                // Unreachable code?
                LOG.error("[checkAttachmentsFromRedisSign]oByteArrayMultipartFile==null! sID_File_Redis="
                        + sID_File_Redis);
            }

        } catch (RecordInmemoryException e) {
            LOG.warn(e.getMessage(), e);
            throw new FileServiceIOException(
                    FileServiceIOException.Error.REDIS_ERROR, e.getMessage());
        }

        String soSignData = BankIDUtils.checkECP(generalConfig, upload, fileName);

        return soSignData;
    }

    @ApiOperation(value = "Загрузки прикрепленного к заявке файла из постоянной базы", notes = "##### Пример:\n "
            + "https://test.igov.org.ua/wf/service/object/file/download_file_from_db?taskId=82596&attachmentId=6726532&nFile=7\n")
    @RequestMapping(value = "/download_file_from_db", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    byte[] getAttachmentFromDb(
            @ApiParam(value = "строка-ИД задачи", required = true) @RequestParam(value = "taskId") String taskId,
            @ApiParam(value = "строка-ID прикрепленного файла", required = false) @RequestParam(required = false, value = "attachmentId") String attachmentId,
            @ApiParam(value = "порядковый номер прикрепленного файла", required = false) @RequestParam(required = false, value = "nFile") Integer nFile,
            HttpServletResponse httpResponse) throws IOException {

        // Получаем по задаче ид процесса
        HistoricTaskInstance historicTaskInstanceQuery = historyService
                .createHistoricTaskInstanceQuery().taskId(taskId)
                .singleResult();
        String processInstanceId = historicTaskInstanceQuery
                .getProcessInstanceId();
        if (processInstanceId == null) {
            throw new ActivitiObjectNotFoundException(
                    "ProcessInstanceId for taskId '" + taskId + "' not found.",
                    Attachment.class);
        }

        // Выбираем по процессу прикрепленные файлы
        Attachment attachmentRequested = oActionTaskService.getAttachment(attachmentId, taskId,
                nFile, processInstanceId);
        String sFileName = attachmentRequested.getName();
        String description = attachmentRequested.getDescription();
        String type = attachmentRequested.getType();

        String id = attachmentRequested.getId();
		InputStream attachmentStream = taskService
		        .getAttachmentContent(id);
		if (attachmentStream == null) {
		    throw new ActivitiObjectNotFoundException("Attachment with ID '"
		            + id + "' doesn't have content associated with it.",
		            Attachment.class);
		}
		
		
		int nTo = sFileName.lastIndexOf(".");
		if (nTo >= 0) {
		    sFileName = "attach_" + id + "."
		            + sFileName.substring(nTo + 1);
		}
		
		// Вычитывем из потока массив байтов контента и помещаем параметры
		// контента в header
		VariableMultipartFile multipartFile = new VariableMultipartFile(
		        attachmentStream, description,
		        sFileName, type);
		httpResponse.setHeader("Content-disposition", "attachment; filename="
		        + sFileName);
		httpResponse.setHeader("Content-Type", "application/octet-stream");
		
		httpResponse.setContentLength(multipartFile.getBytes().length);
		
		return multipartFile.getBytes();
    }

    @ApiOperation(value = "Загрузки прикрепленного к заявке файла из постоянной базы", notes = "##### Пример:\n "
            + "https://test.igov.org.ua/wf/service/object/file/download_file_from_storage_static?sId=111111&sFileName=111.txt&sType=text\n")
    @RequestMapping(value = "/download_file_from_storage_static", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    byte[] getAttachmentByID(
    		@ApiParam(value = "sId", required = true) @RequestParam(required = true, value = "sId") String nId,
            @ApiParam(value = "sFileName", required = true) @RequestParam(value = "sFileName") String sFileName,
            @ApiParam(value = "sType", required = true) @RequestParam(value = "sType") String sType,
            HttpServletResponse httpResponse) throws IOException {
		InputStream attachmentStream = ((org.igov.service.conf.TaskServiceImpl)taskService)
		        .getAttachmentContentByMongoID(nId);
		if (attachmentStream == null) {
		    throw new ActivitiObjectNotFoundException("Attachment with ID '"
		            + nId + "' doesn't have content associated with it.",
		            Attachment.class);
		}
		
		
		int nTo = sFileName.lastIndexOf(".");
		if (nTo >= 0) {
		    sFileName = "attach_" + nId + "."
		            + sFileName.substring(nTo + 1);
		}
		
		// Вычитывем из потока массив байтов контента и помещаем параметры
		// контента в header
		VariableMultipartFile multipartFile = new VariableMultipartFile(
		        attachmentStream, sFileName,
		        sFileName, sType);
		httpResponse.setHeader("Content-disposition", "attachment; filename="
		        + sFileName);
		httpResponse.setHeader("Content-Type", "application/octet-stream");
		
		httpResponse.setContentLength(multipartFile.getBytes().length);
		
		return multipartFile.getBytes();
    }
    
	@ApiOperation(value = "Проверка ЭЦП на атачменте(файл) таски Activiti", notes = "##### Примеры:\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/check_attachment_sign?nID_Task=7315073&nID_Attach=7315075\n"
            + "Ответ:\n"
            + "\n```json\n"
            + "{\n"
            + "  \"state\": \"ok\",\n"
            + "  \"customer\": {\n"
            + "    \"inn\": \"1436057000\",\n"
            + "    \"fullName\": \"Сервіс зберігання сканкопій\",\n"
            + "    \"signatureData\": {\n"
            + "      \"name\": \"АЦСК ПАТ КБ «ПРИВАТБАНК»\",\n"
            + "      \"serialNumber\": \"0D84EDA1BB9381E80400000079DD02004A710800\",\n"
            + "      \"timestamp\": \"29.10.2015 13:45:33\",\n"
            + "      \"code\": true,\n"
            + "      \"desc\": \"ПІДПИС ВІРНИЙ\",\n"
            + "      \"dateFrom\": \"13.08.2015 11:24:31\",\n"
            + "      \"dateTo\": \"12.08.2016 23:59:59\",\n"
            + "      \"sn\": \"UA-14360570-1\"\n"
            + "    },\n"
            + "    \"organizations\": [\n"
            + "      {\n"
            + "        \"type\": \"edsOwner\",\n"
            + "        \"name\": \"ПАТ КБ «ПРИВАТБАНК»\",\n"
            + "        \"mfo\": \"14360570\",\n"
            + "        \"position\": \"Технологічний сертифікат\",\n"
            + "        \"ownerDesc\": \"Співробітник банку\",\n"
            + "        \"address\": {\n"
            + "          \"type\": \"factual\",\n"
            + "          \"state\": \"Дніпропетровська\",\n"
            + "          \"city\": \"Дніпропетровськ\"\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"type\": \"edsIsuer\",\n"
            + "        \"name\": \"ПУБЛІЧНЕ АКЦІОНЕРНЕ ТОВАРИСТВО КОМЕРЦІЙНИЙ БАНК «ПРИВАТБАНК»\",\n"
            + "        \"unit\": \"АЦСК\",\n"
            + "        \"address\": {\n"
            + "          \"type\": \"factual\",\n"
            + "          \"state\": \"Дніпропетровська\",\n"
            + "          \"city\": \"Дніпропетровськ\"\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}\n"
            + "\n```\n"
            + "\nОтвет для несуществующей таски (nID_Task):\n"
            + "\n```json\n"
            + "{\"code\":\"SYSTEM_ERR\",\"message\":\"ProcessInstanceId for taskId '7315070' not found.\"}\n"
            + "\n```\n"
            + "\nОтвет для несуществующего атачмента (nID_Attach):\n"
            + "\n```json\n"
            + "{\"code\":\"SYSTEM_ERR\",\"message\":\"Attachment for taskId '7315073' not found.\"}\n"
            + "\n```\n"
            + "\nОтвет для атачмента который не имеет наложеной ЭЦП:\n"
            + "\n```json\n"
            + "{}\n"
            + "\n```\n")
    @RequestMapping(value = "/check_attachment_sign", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    @Transactional
    public
    @ResponseBody
    String checkAttachSign(
            @ApiParam(value = "ИД-номер таски", required = true) @RequestParam(value = "nID_Task") String taskId,
            @ApiParam(value = "строка-ИД атачмента приложеного к таске", required = true) @RequestParam(value = "nID_Attach") String attachmentId)
            throws IOException {

        HistoricTaskInstance historicTaskInstanceQuery = historyService
                .createHistoricTaskInstanceQuery().taskId(taskId)
                .singleResult();
        String processInstanceId = null;

        if (historicTaskInstanceQuery != null) {
            processInstanceId = historicTaskInstanceQuery
                    .getProcessInstanceId();
        }
        if (processInstanceId == null) {
            throw new ActivitiObjectNotFoundException(
                    "ProcessInstanceId for taskId '" + taskId + "' not found.",
                    Attachment.class);
        }

        Attachment attachmentRequested = oActionTaskService.getAttachment(attachmentId, taskId,
                processInstanceId);

        InputStream attachmentStream = null;
        if (attachmentRequested != null) {
            attachmentStream = taskService
                    .getAttachmentContent(attachmentRequested.getId());
        }

        if (attachmentStream == null) {
            throw new ActivitiObjectNotFoundException("Attachment for taskId '"
                    + taskId + "' doesn't have content associated with it.",
                    Attachment.class);
        }

        LOG.info("Attachment found. taskId {}, attachmentID {} With name {} ",
                taskId, attachmentId, attachmentRequested.getName());

        byte[] content = IOUtils.toByteArray(attachmentStream);

        String soSignData = BankIDUtils.checkECP(generalConfig, content, attachmentRequested.getName());

        return soSignData;
    }

    @ApiOperation(value = "Сервис для получения Attachment из execution", notes = "")
    @RequestMapping(value = "/download_file_from_db_execution", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    byte[] getAttachmentFromDbExecution(
            @ApiParam(value = "ИД-номер таски", required = true) @RequestParam("taskId") String taskId,
            HttpServletResponse httpResponse) throws IOException {

        String processInstanceId = oActionTaskService.getProcessInstanceIDByTaskID(taskId);
        HistoricProcessInstance processInstance = oActionTaskService.getProcessInstancyByID(processInstanceId);

        // получаем коллекцию переменных процеса и прикрепленный файл
        Map<String, Object> processVariables = processInstance
                .getProcessVariables();
        BuilderAttachModelCover attachModel = null;

        if (processVariables != null) {
            List<BuilderAttachModelCover> builderAttachModelList = (List) processVariables
                    .get(FileTaskUpload.BUILDER_ATACH_MODEL_LIST);

            if (builderAttachModelList != null) {
                attachModel = builderAttachModelList.get(0);
            }
        }

        if (attachModel == null) {
            throw new ActivitiObjectNotFoundException(
                    String.format(
                            "ProcessVariable '{%s}' for processInstanceId '{%s}' not found.",
                            FileTaskUpload.BUILDER_ATACH_MODEL_LIST,
                            processInstanceId));
        }

        // Помещаем параметры контента в header
        httpResponse.setHeader("Content-disposition", "attachment; filename="
                + attachModel.getOriginalFilename());
        httpResponse.setHeader("Content-Type", attachModel.getContentType()
                + ";charset=UTF-8");
        httpResponse.setContentLength(attachModel.getByteToStringContent()
                .getBytes().length);

        return AbstractModelTask.contentStringToByte(attachModel
                .getByteToStringContent());
    }

    @ApiOperation(value = "Аплоад(upload) и прикрепление файла в виде атачмента к таске Activiti", notes =
            "#####  Примеры: \n"
                    + "http://test.igov.org.ua/wf/service/object/file/upload_file_as_attachment?taskId=68&description=ololo\n"
            + "\n```json\n"
            + "Ответ без ошибок:\n"
            + "{\n"
            + "  \"taskId\": \"38\",\n"
            + "  \"processInstanceId\": null,\n"
            + "  \"userId\": \"kermit\",\n"
            + "  \"name\": \"jmt.png\",\n"
            + "  \"id\": \"45\",\n"
            + "  \"type\": \"image/png;png\",\n"
            + "  \"description\": \"SomeDocumentDescription\",\n"
            + "  \"time\": 1433539278957,\n"
            + "  \"url\": null\n"
            + "}\n"
            + "\nID созданного attachment - \"id\": \"45\"\n\n"
            + "\n```\n"
            + "Ответ с ошибкой:\n\n"
            + "\n```json\n"
            + "{\"code\":\"SYSTEM_ERR\",\"message\":\"Cannot find task with id 384\"}\n"
            + "\n```\n")
    @RequestMapping(value = "/upload_file_as_attachment", method = RequestMethod.POST, produces = "application/json")
    @Transactional
    public
    @ResponseBody
    AttachmentEntityI putAttachmentsToExecution(//ResponseEntity
            @ApiParam(value = "ИД-номер таски", required = true) @RequestParam(value = "taskId") String taskId,
            @ApiParam(value = "файл html. в html это имя элемента input типа file - <input name=\"file\" type=\"file\" />. в HTTP заголовках - Content-Disposition: form-data; name=\"file\" ...", required = true) @RequestParam("file") MultipartFile file,
            @ApiParam(value = "строка-описание", required = true) @RequestParam(value = "description") String description)
            throws IOException {

        String processInstanceId = null;
        String assignee = null;

        List<Task> tasks = taskService.createTaskQuery().taskId(taskId).list();
        if (!tasks.isEmpty()) {
            Task task = tasks.iterator().next();
            processInstanceId = task.getProcessInstanceId();
            assignee = task.getAssignee() != null ? task.getAssignee()
                    : "kermit";
            LOG.debug("(processInstanceId={}, taskId={}, assignee={})", processInstanceId, taskId, assignee);
        } else {
            LOG.error("There is no tasks at all!");
        }

        identityService.setAuthenticatedUserId(assignee);

        String sFilename = file.getOriginalFilename();
        LOG.debug("(sFilename={})", file.getOriginalFilename());
        sFilename = sTextTranslit(sFilename);
        LOG.debug("(FileExtention:{}, fileContentType:{}, fileName:{}) ",
                oActionTaskService.getFileExtention(file), file.getContentType(), sFilename);
        LOG.debug("description: {}", description);

        Attachment oAttachment = taskService.createAttachment(file.getContentType() + ";" + oActionTaskService.getFileExtention(file), taskId,
                processInstanceId, sFilename,// file.getOriginalFilename()
                description, file.getInputStream());

        AttachmentCover oAttachmentCover = new AttachmentCover();
        //AttachmentEntityI oAttachmentEntityI=oAttachmentCover.apply(oAttachment);
        //LOG.info("(oAttachmentEntityI={})", oAttachmentEntityI.toString());
        //return JsonRestUtils.toJsonResponse(oAttachmentEntityI);
        return oAttachmentCover.apply(oAttachment);
    }

    @ApiOperation(value = "Аплоад(upload) и прикрепление текстового файла в виде атачмента к таске Activiti", notes =
            "#####Пример: "
                    + "http://localhost:8080/wf/service/object/file/upload_content_as_attachment?nTaskId=24&sDescription=someText&sFileName=FlyWithMe.html\n"
            + "\n```json\n"
            + "Ответ без ошибок:\n"
            + "{\n"
            + "  \"taskId\": \"38\",\n"
            + "  \"processInstanceId\": null,\n"
            + "  \"userId\": \"kermit\",\n"
            + "  \"name\": \"FlyWithMe.html\",\n"
            + "  \"id\": \"25\",\n"
            + "  \"type\": \"text/html;html\",\n"
            + "  \"description\": \"someText\",\n"
            + "  \"time\": 1433539278957,\n"
            + "  \"url\": null\n"
            + "}\n\n"
            + "ID созданного attachment - \"id\": \"25\"\n"
            + "\n```\n"
            + "\nОтвет с ошибкой:\n"
            + "\n```json\n"
            + "{\"code\":\"SYSTEM_ERR\",\"message\":\"Cannot find task with id 384\"}\n"
            + "\n```\n")
    @RequestMapping(value = "/upload_content_as_attachment", method = RequestMethod.POST, produces = "application/json")
    @Transactional
    public
    @ResponseBody
    AttachmentEntityI putTextAttachmentsToExecution(
            @ApiParam(value = "строка-Логин пользователя", required = true) @RequestParam(value = "nTaskId") String taskId,
            @ApiParam(value = "строка-MIME тип отправляемого файла (по умолчанию = \"text/html\")", required = false) @RequestParam(value = "sContentType", required = false, defaultValue = "text/html") String sContentType,
            @ApiParam(value = "строка-описание", required = true) @RequestParam(value = "sDescription") String description,
            @RequestParam(value = "sFileName") String sFileName,
            @RequestBody String sData) {

        String processInstanceId = null;
        String assignee = null;

        List<Task> tasks = taskService.createTaskQuery().taskId(taskId).list();
        if (!tasks.isEmpty()) {
            Task task = tasks.iterator().next();
            processInstanceId = task.getProcessInstanceId();
            assignee = task.getAssignee() != null ? task.getAssignee()
                    : "kermit";
            LOG.debug("processInstanceId:{}, taskId:{}, assignee:{} ", processInstanceId, taskId, assignee);
        } else {
            LOG.error("There is no tasks at all!");

        }

        identityService.setAuthenticatedUserId(assignee);

        String sFilename = sFileName;
        LOG.debug("sFilename={}", sFileName);
        sFilename = sTextTranslit(sFilename);
        LOG.debug("FileExtention: {}, fileContentType:{}, fileName:{}",
                oActionTaskService.getFileExtention(sFileName), sContentType, sFilename);
        LOG.debug("description: {}", description);

        Attachment attachment = taskService.createAttachment(sContentType + ";"
                        + oActionTaskService.getFileExtention(sFileName), taskId, processInstanceId,
                sFilename, description,
                new ByteArrayInputStream(sData.getBytes(Charsets.UTF_8)));

        AttachmentCover oAttachmentCover = new AttachmentCover();

        return oAttachmentCover.apply(attachment);
    }

    @ApiOperation(value = "Работа с файлами-шаблонами", notes =
            "#####  возвращает содержимое указанного файла с указанным типом контента (если он задан).\n"
            + "Если указанный путь неверен и файл не найден -- вернется соответствующая ошибка.\n\n"
            + "Примеры:\n\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/getPatternFile?sPathFile=print//subsidy_zayava.html\n\n"
            + "ответ: вернется текст исходного кода файла-шаблона\n\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/getPatternFile?sPathFile=print//subsidy_zayava.html&sContentType=text/html\n\n"
            + "ответ: файл-шаблон будет отображаться в виде html-страницы")
    @RequestMapping(value = "/getPatternFile", method = RequestMethod.GET)
    public void getPatternFile(
            @ApiParam(value = "строка-полный путь к файлу", required = true) @RequestParam(value = "sPathFile") String sPathFile,
            @ApiParam(value = "строка-тип контента", required = false) @RequestParam(value = "sContentType", required = false) String sContentType,
            HttpServletResponse response) throws CommonServiceException {

        try {
            String contentType = sContentType == null ? PATTERN_DEFAULT_CONTENT_TYPE
                    : sContentType;
            response.setContentType(contentType);
            response.setCharacterEncoding(Charsets.UTF_8.toString());
            LOG.info(String.format("Getting FileData pattern [sPathFile = %s]", sPathFile));
            byte[] resultObj = getFileData_Pattern(sPathFile);
            response.getOutputStream().write(resultObj);
        } catch (IllegalArgumentException | IOException e) {
            CommonServiceException newErr = new CommonServiceException(
                    "BUSINESS_ERR", e.getMessage(), e);
            newErr.setHttpStatus(HttpStatus.FORBIDDEN);
            throw newErr;
        } catch (Exception e) {
            CommonServiceException newErr = new CommonServiceException(
                    "SYSTEM_ERR", e.getMessage(), e);
            newErr.setHttpStatus(HttpStatus.FORBIDDEN);
            throw newErr;
        }
    }

    @ApiOperation(value = "moveAttachsToMongo", notes =
            "#####  ObjectFileCommonController: Перенос атачментов задач активити в mongo DB  \n"
                    + "пробегается по всем активным задачам и переносит их атачменты в mongo DB (если они еще не там) \n"
                    + "и в самом объекте атачмента меняет айдишники атачментов на новые\n"
                    + "Метод содержит необязательные параметры, которые определяют какие задачи обрабатывать\n"
    	    + "nStartFrom - порядковый номер задачи в списке всех задач, с которого начинать обработку\n"
    	    + "nChunkSize - количество задач, которые обрабатывать начиная или с первой или со значения nStartFrom. \n"
    	    + "Задачи выюираются по 10 из базы, поэтому лучше делать значени nChunkSize кратным 10\n"
    	    + "nProcessId - обрабатывать задачу с заданным айдишником\n"
            + "Примеры:\n\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/moveAttachsToMongo\n"
            + "Перенести все атачменты задач в Монго ДБ\n\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/moveAttachsToMongo?nProcessId=9397569\n"
            + "Перенести атачменты процесса с ID 9397569 в Монго ДБ\n\n"
            + "https://test.region.igov.org.ua/wf/service/object/file/moveAttachsToMongo?nStartFrom=0&nChunkSize=10\n\n"
            + "Перенести аттачменты процесса с 0 по 10 в монго")
    @RequestMapping(value = "/moveAttachsToMongo", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    String moveAttachsToMongo(@ApiParam(value = "Порядковый номер процесса с которого начинать обработку аттачментов", required = false) 
    	@RequestParam(value = "nStartFrom", required = false) String nStartFrom,
            @ApiParam(value = "строка-размер блока для выборки процесса на обработку", required = false) @RequestParam(value = "nChunkSize", required = false) String nChunkSize,
            @ApiParam(value = "строка-ИД конкретного процесса", required = false) @RequestParam(value = "nProcessId", required = false) String nProcessId) {
        return oObjectFileService.moveAttachsToMongo(nStartFrom, nChunkSize, nProcessId);
    }
    
    @ApiOperation(value = "/dfs/getPatternFilled", notes = "##### Контроллер сохранения заполненного шаблона в базу\n")
    @RequestMapping(value = {"/dfs/getPatternFilled"}, method = RequestMethod.POST, headers = {"Accept=application/json"})
    public @ResponseBody
    Map<String, String> getPatternFilled(
            @ApiParam(value = "Список алиасов и значений из формы в json формате", required = false) @RequestBody(required = false) Map<String, String> data,
            @ApiParam(value = "Ид файла-шаблона", required = true) @RequestParam(required = true) String sID_Pattern,
            HttpServletResponse httpResponse) throws Exception {
        
        Map<String, String> result = new HashMap<>();
        LOG.info("data: " + data);
        File file = FileSystemData.getFile(FileSystemData.SUB_PATH_XML, sID_Pattern + ".xml");
        String declarContent = Files.toString(file, Charset.defaultCharset());
        LOG.info("Created document with customer info: {}", declarContent);
        String regex, replacement;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            regex = "<" + entry.getKey().trim().toUpperCase() + ">";
            replacement = regex + URLEncoder.encode(entry.getValue(), "UTF-8"); //<![CDATA[текст]]>
            declarContent = declarContent.replaceAll(regex, replacement);
        }
        //запись контента в xml файл
        //MultipartFile multipartFile = new ByteArrayMultipartFile(declarContent.getBytes(),
        //        sID_Pattern, sID_Pattern, "application/xml");
        //httpResponse.setHeader("Content-disposition", "attachment; filename=" + multipartFile.getName());
        //httpResponse.setHeader("Content-Type", multipartFile.getContentType());
        //httpResponse.setContentLength(multipartFile.getBytes().length);
        //String key = oBytesDataInmemoryStorage.putBytes(AbstractModelTask
        //        .multipartFileToByteArray(multipartFile, multipartFile.getOriginalFilename())
        //        .toByteArray());
        //result.put("sID_Redis", key);
        result.put("soPatternFilled", declarContent.replaceAll(" ", "").replaceAll(System.getProperty("line.separator"), ""));
        return result;
    }
    
    /*
    @ApiOperation(value = "/putAttachmentToRedis_old", notes = "##### Контроллер платежей. Регистрация проведенного платежа - по колбэку от платежной системы\n")
    @RequestMapping(value = {"/putAttachmentToRedis_old"}, method = RequestMethod.POST, headers = {"Accept=application/json"})
    public @ResponseBody
    String putAttachmentToRedis_old(@RequestBody(required = false) Map<String, String> data,
            HttpServletResponse httpResponse) throws Exception {
        //генерируем хмл в виде файла и проставляем значения в него. отдаем на клиента
        LOG.info("data: " + data);
        DBody body = new DBody();
        ObjectFactory factory = new ObjectFactory();
        body.setHLNAME(data.get("hlname"));
        body.setHPNAME(data.get("hpname"));
        body.setHFNAME(factory.createDBodyHFNAME(data.get("hfname")));
        body.setHTIN(data.get("htin"));
        body.setHEMAIL(data.get("hemail"));
        body.setHPASS(factory.createDBodyHPASS(data.get("hpass")));
        body.setHPASSDATE(factory.createDBodyHPASSDATE(data.get("hpassDate")));
        body.setHPASSISS(factory.createDBodyHPASS(data.get("hpassiss")));
        body.setHSTREET(data.get("hstreet"));
        body.setHBUILD(data.get("hbuild"));
        body.setHAPT(factory.createDBodyHAPT(data.get("hapart")));
        body.setHCOUNTRY(data.get("country"));
        body.setR01G01(Integer.valueOf(data.get("kvStart")));
        body.setR01G02(Integer.valueOf(data.get("yStart")));
        body.setR02G01(Integer.valueOf(data.get("kvEnd")));
        body.setR02G02(Integer.valueOf(data.get("yEnd")));

        DHead head = new DHead();
        head.setTIN(null);
        head.setLINKEDDOCS(factory.createDHeadLINKEDDOCS(null));
        head.setCDOC(null);
        head.setCDOCSUB(null);
        head.setCDOCVER(null);
        head.setCDOCTYPE(null);
        head.setCDOCCNT(null);
        head.setCREG(null);
        head.setCRAJ(null);
        head.setPERIODMONTH(null);
        head.setPERIODTYPE(null);
        head.setPERIODYEAR(null);
        head.setCSTIORIG(null);
        head.setCDOCSTAN(null);
        head.setDFILL(null);
        head.setSOFTWARE(null);
        head.setTIN(null);

        DeclarContent declar = new DeclarContent();
        declar.setDECLARHEAD(head);
        declar.setDECLARBODY(body);

        StringWriter sw = new StringWriter();
        JAXBContext jc = JAXBContext.newInstance(DeclarContent.class);
        Marshaller m = jc.createMarshaller();
        m.marshal(declar, sw);

        String declarContent = sw.toString();
        LOG.info("Created document with customer info to embedd to message: {}", declarContent);
        //запись контента в xml файл
        MultipartFile file = new ByteArrayMultipartFile(declarContent.getBytes(),
                "SwinEd", "SwinEd", "application/xml");
        httpResponse.setHeader("Content-disposition", "attachment; filename=" + file.getName());
        httpResponse.setHeader("Content-Type", file.getContentType());
        httpResponse.setContentLength(declarContent.getBytes().length);
        String key = oBytesDataInmemoryStorage.putBytes(AbstractModelTask
                .multipartFileToByteArray(file, file.getOriginalFilename())
                .toByteArray());
        return key + " " + declarContent;
        //return declarContent.getBytes();
    }

    */
    
}
