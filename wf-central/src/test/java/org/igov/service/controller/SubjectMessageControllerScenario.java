package org.igov.service.controller;

import java.util.ArrayList;
import java.util.List;

import org.activiti.engine.impl.util.json.JSONObject;
import org.apache.commons.lang.RandomStringUtils;
import org.igov.io.GeneralConfig;
import org.igov.model.subject.Subject;
import org.igov.model.subject.SubjectContact;
import org.igov.model.subject.SubjectContactDao;
import org.igov.model.subject.SubjectDao;
import org.igov.model.subject.SubjectHuman;
import org.igov.model.subject.SubjectHumanDao;
import org.igov.model.subject.message.SubjectMessageFeedback;
import org.igov.service.business.subject.SubjectMessageService;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.igov.util.JSON.JsonRestUtils;
import org.igov.model.subject.message.SubjectMessage;
import org.joda.time.DateTime;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebAppConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("default")
@ContextConfiguration(classes = IntegrationTestsApplicationConfiguration.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SubjectMessageControllerScenario {

    public static final String SET_MESSAGE = "/subject/message/setMessage";
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;
    @Autowired
    SubjectContactDao subjectContactDao;
    @Autowired
    SubjectHumanDao subjectHumanDao;
    @Autowired
    SubjectDao subjectDao;
    @Autowired
    GeneralConfig generalConfig;

    @Autowired
    private SubjectMessageService subjectMessageService;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Ignore
    @Test
    public void firstShouldSuccessfullySetAndGetMassage() throws Exception {
        String messageBody = "XXX";
        String jsonAfterSave = mockMvc.perform(post("/subject/message/setMessage").
                contentType(MediaType.APPLICATION_JSON).
                param("sHead", "expect").
                param("sBody", messageBody).
                param("sContacts", "093").
                param("sData", "some data").
                param("sMail", "ukr.net").
                param("nID_SubjectMessageType", "1")).
                andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        SubjectMessage savedMessage = JsonRestUtils.readObject(jsonAfterSave, SubjectMessage.class);
        assertNotNull(savedMessage.getId());
        assertNotNull(savedMessage.getSubjectMessageType());
        assertEquals(1L, savedMessage.getSubjectMessageType().getId().longValue());
        assertEquals(messageBody, savedMessage.getBody());
        assertEquals(0L, savedMessage.getId_subject().longValue());

        String jsonAfterGet = mockMvc.perform(get("/subject/message/getMessage").param("nID", "" + savedMessage.getId())).
                andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(jsonAfterSave, jsonAfterGet);
    }

    @Ignore
    @Test
    public void nextShouldSuccessfullySetMassageWithDefaultSubjectID() throws Exception {
        mockMvc.perform(post("/subject/message/setMessage").
                contentType(MediaType.APPLICATION_JSON).
                param("sHead", "expect").
                param("sBody", "XXX").
                param("sMail", "ukr.net")).
                andExpect(status().isOk());
    }
    @Ignore
    @Test
    public void shouldFailedNoObligatedParam() throws Exception {
        mockMvc.perform(post("/subject/message/setMessage").
                contentType(MediaType.APPLICATION_JSON).
                param("sBody", "XXXXXxxx").
                param("sMail", "ukr.ed")).
                andExpect(status().isBadRequest());
    }
    
    
    @Ignore
    @Test
    public void testSetMessage_nIDSubject_sMailNull() throws Exception
    {
       String messageBody = "XXX";
       String messageHead = "expect";
       String jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("nID_Subject", "22")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    }
   
    @Ignore
    @Test
    public void testSetMessage_nIDSubject_sMailEmpty() throws Exception
    {
       String messageBody = "XXX";
       String messageHead = "expect";
       String jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("nID_Subject", "22").
              param("sMail", "")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    }
    @Ignore
    @Test
    public void testSetMessage_nIDSubject() throws Exception
    {
       String messageBody = "XXX";
       String messageHead = "expect";
       String jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("nID_Subject", "22").
              param("sMail", "test@igov.org.ua")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
          
    }
    @Ignore
    @Test
    public void testSetMessageMailEmpty_nIDSubjectNull() throws Exception
    {
       String messageBody = "XXX";
       String messageHead = "expect";
       String jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("sMail", "")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    }
    @Ignore
    @Test
    public void testSetMessageWithSubject() throws Exception
    {
        //issue-1053
        //пункт 3.2 (при наличии nID_Subject: в случае отсутствия контактов подвязываем SubjectContact и 
        //делаем его дефолтным в структуре SubjectHuman)
       Subject subject = subjectDao.getSubject(25L);
       List<SubjectContact> subjectContacts = subjectContactDao.findContacts(subject);
       Assert.assertTrue(subjectContacts.size() == 0);
       String messageBody = "XXX";
       String messageHead = "expect";
       String jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("nID_Subject", "25").
              param("sMail", "test24@igov.org.ua")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
       
       SubjectMessage savedMessage = JsonRestUtils.readObject(jsonAfterSave, SubjectMessage.class);
       SubjectContact subjectContact = savedMessage.getoMail();
       SubjectHuman subjectHuman = subjectHumanDao.findByExpected("oSubject", subject);
       SubjectContact subjectContact_Default = subjectHuman.getDefaultEmail();
       Assert.assertNotNull(subjectContact);
       Assert.assertNotNull(subjectContact_Default);
       Assert.assertEquals(subjectContact.getsValue(), subjectContact_Default.getsValue());
       
       //пункт 3.2 при наличии nID_Subject и контактов у субъекта, но отсутствии заданного контакта
       //добавляем контакт в SubjectContact и делаем его дефолтным в SubjectHuman
       
       subject = subjectDao.getSubject(24L);
       subjectContacts = subjectContactDao.findContacts(subject);
       
       Assert.assertTrue(subjectContacts.size() != 0);
       boolean no_contact = true;
       for(SubjectContact sc : subjectContacts)
       {
          if(sc.getsValue().equals("test25@igov.org.ua"))
              no_contact = false;
       }
       Assert.assertTrue(no_contact);
       
       jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("nID_Subject", "24").
              param("sMail", "test25@igov.org.ua")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
       
       savedMessage = JsonRestUtils.readObject(jsonAfterSave, SubjectMessage.class);
       subjectContact = savedMessage.getoMail();
       subjectHuman = subjectHumanDao.findByExpected("oSubject", subject);
       subjectContact_Default = subjectHuman.getDefaultEmail();
       subjectContacts = subjectContactDao.findContacts(subject);
       for(SubjectContact sc : subjectContacts)
       {
          if(sc.getsValue().equals("test25@igov.org.ua"))
              no_contact = false;
  
       }
       Assert.assertNotNull(subjectContact);
       Assert.assertNotNull(subjectContact_Default);
       Assert.assertEquals(subjectContact.getsValue(), subjectContact_Default.getsValue());
       Assert.assertFalse(no_contact);
       
       //все контакты есть — обновляем sDate SubjectContact на текущее значение
       
       subject = subjectDao.getSubject(24L);
       subjectContacts = subjectContactDao.findContacts(subject);
       Assert.assertTrue(subjectContacts.size() != 0);
       no_contact = true;
       DateTime date = null;
       for(SubjectContact sc : subjectContacts)
       {
          if(sc.getsValue().equals("test25@igov.org.ua"))
          {
              no_contact = false;
              date = sc.getsDate();
          }
       }
       Assert.assertFalse(no_contact);
       
       
       jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("nID_Subject", "24").
              param("sMail", "test25@igov.org.ua")).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
       
       savedMessage = JsonRestUtils.readObject(jsonAfterSave, SubjectMessage.class);
       subjectContact = savedMessage.getoMail();
       DateTime date1 = null;
       subjectContacts = subjectContactDao.findContacts(subject);
       Assert.assertTrue(subjectContacts.size() != 0);
       no_contact = true;
       for(SubjectContact sc : subjectContacts)
       {
          if(sc.getsValue().equals("test25@igov.org.ua"))
          {
              no_contact = false;
              date1 = sc.getsDate();
          }
       }
       Assert.assertFalse(no_contact);
      
       Assert.assertNotNull(subjectContact);
       Assert.assertNotEquals(date, date1);
       
    }
    @Ignore
    @Test
    public void testSetMessageWithout_nIDSubject() throws Exception
    {
       //issue-1053
      //если есть такой дефолтный контакт в SubjectHuman обновляем nID_Subject в SubjectMessage и в SubjectContact
      //к пункту 3.1
        
       String messageBody = "XXX";
       String messageHead = "expect";
       String mail = "test@igov.org.ua";
       String jsonAfterSave = mockMvc.perform(post(SET_MESSAGE).
              contentType(MediaType.APPLICATION_JSON).
              param("sHead", messageHead).
              param("sBody", messageBody).
              param("sMail", mail)).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
       
        SubjectMessage savedMessage = JsonRestUtils.readObject(jsonAfterSave, SubjectMessage.class);
        SubjectContact subjectContact = savedMessage.getoMail();
        
        Assert.assertNotNull(savedMessage.getId_subject());
        Assert.assertNotNull(subjectContact.getSubject());
        Assert.assertEquals(savedMessage.getId_subject(), subjectContact.getSubject().getId());
      
        
        
    }
  
    @Test
    public void testTransferDataFromMail() throws Exception
    {
        //issue-1053
        //тест переноса данных с поля sMail в поле типа SubjectContact 
        String jsonAfterExecute = mockMvc.perform(get("/subject/message/transferDataFromMail").
              contentType(MediaType.APPLICATION_JSON)).
              andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
       
    }

    @Test
    public void shouldAddFeedbackToServiceAndReturnLink() throws Exception {
        SubjectMessageFeedback feedback = new SubjectMessageFeedback();
        feedback.setId(1L);
        feedback.setsID_Source("-1");
        feedback.setsAuthorFIO("FIO");
        feedback.setsMail("sMail");
        feedback.setsHead("sHead");
        feedback.setsBody("sBody");
        feedback.setsPlace("sPlace");
        feedback.setnID_Rate(-1L);
        feedback.setnID_Service(-1L);
        feedback.setsID_Token(RandomStringUtils.randomAlphanumeric(20));

        JSONObject expectedResponseObject = new JSONObject();

        String responseMessage = String.format("%s/service/%d/feedback?nID=%d&sID_Token=%s",
                generalConfig.getSelfHost(), feedback.getnID_Service(), feedback.getId(), feedback.getsID_Token());

        expectedResponseObject.put("sURL", responseMessage);

        when(subjectMessageService.setSubjectMessageFeedback(feedback.getsID_Source(),
                feedback.getsAuthorFIO(),
                feedback.getsMail(),
                feedback.getsHead(),
                feedback.getsBody(),
                feedback.getsPlace(),
                feedback.getsEmployeeFIO(),
                feedback.getnID_Rate(),
                feedback.getnID_Service(),
                null, // sAnswer
                null, // nId
                null))// nID_Subject
                 .thenReturn(feedback);

        mockMvc.perform(post("/subject/message/setFeedbackExternal").
                contentType(MediaType.APPLICATION_JSON)
                .param("sID_Source", feedback.getsID_Source())
                .param("sAuthorFIO", feedback.getsAuthorFIO())
                .param("sMail", feedback.getsMail())
                .param("sHead", feedback.getsHead())
                .param("sBody", feedback.getsBody())
                .param("sPlace", feedback.getsPlace())
                .param("nID_Rate", feedback.getnID_Rate().toString())
                .param("nID_Service", feedback.getnID_Service().toString()))
                .andExpect(status().isCreated())
                .andExpect(content().json(expectedResponseObject.toString()));
    }

    @Test
    public void shouldReturnSubjectMessageFeedbackByIdAndToken() throws Exception {
        SubjectMessageFeedback feedback = new SubjectMessageFeedback();
        feedback.setId(1L);
        feedback.setsID_Source("-1");
        feedback.setsAuthorFIO("FIO");
        feedback.setsMail("sMail");
        feedback.setsBody("sBody");
        feedback.setnID_Rate(-1L);
        feedback.setnID_Service(-1L);
        feedback.setsID_Token(RandomStringUtils.randomAlphanumeric(20));

        SubjectMessageFeedback feedbackWithNullToken = new SubjectMessageFeedback();
        feedbackWithNullToken.setId(1L);
        feedbackWithNullToken.setsID_Source("-1");
        feedbackWithNullToken.setsAuthorFIO("FIO");
        feedbackWithNullToken.setsMail("sMail");
        feedbackWithNullToken.setsBody("sBody");
        feedbackWithNullToken.setnID_Rate(-1L);
        feedbackWithNullToken.setnID_Service(-1L);

        String expectedResponse = JsonRestUtils.toJson(feedbackWithNullToken);

        when(subjectMessageService.getSubjectMessageFeedbackById(feedback.getId())).thenReturn(feedback);

        mockMvc.perform(get("/subject/message/getFeedbackExternal").
                contentType(MediaType.APPLICATION_JSON)
                .param("nID", feedback.getId().toString())
                .param("sID_Token", feedback.getsID_Token()))

                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }

    @Ignore
    @Test
    public void shouldReturnListOfSubjectMessageFeedbackBynID_Service() throws Exception {
        SubjectMessageFeedback feedback = new SubjectMessageFeedback();
        feedback.setId(1L);
        feedback.setsID_Source("-1");
        feedback.setsAuthorFIO("FIO");
        feedback.setsMail("sMail");
        feedback.setsBody("sBody");
        feedback.setnID_Rate(-1L);
        feedback.setnID_Service(-1L);
        feedback.setsID_Token(RandomStringUtils.randomAlphanumeric(20));

        SubjectMessageFeedback feedbackWithNullToken = new SubjectMessageFeedback();
        feedbackWithNullToken.setId(1L);
        feedbackWithNullToken.setsID_Source("-1");
        feedbackWithNullToken.setsAuthorFIO("FIO");
        feedbackWithNullToken.setsMail("sMail");
        feedbackWithNullToken.setsBody("sBody");
        feedbackWithNullToken.setnID_Rate(-1L);
        feedbackWithNullToken.setnID_Service(-1L);

        List<SubjectMessageFeedback> expectedFeedbackList = new ArrayList<>();
        expectedFeedbackList.add(feedbackWithNullToken);
        expectedFeedbackList.add(feedbackWithNullToken);

        String response = JsonRestUtils.toJson(expectedFeedbackList);

        when(subjectMessageService.getSubjectMessageFeedbackById(feedback.getId())).thenReturn(feedback);
        when(subjectMessageService.getAllSubjectMessageFeedbackBynID_Service(feedback.getnID_Service())).thenReturn(expectedFeedbackList);

        mockMvc.perform(get("/subject/message/getFeedbackExternal").
                contentType(MediaType.APPLICATION_JSON)
                .param("nID", feedback.getId().toString())
                .param("sID_Token", feedback.getsID_Token())
                .param("nID_Service", feedback.getnID_Service().toString()))

                .andExpect(status().isOk())
                .andExpect(content().json(response));
    }

//   TODO: THIS TEST SHOULD BE INTEGRATIONAL OR REFACTORED
    @Test
    @Ignore
    public void shouldAddAnswerToFeedback() throws Exception {
        SubjectMessageFeedback expectedFeedback = new SubjectMessageFeedback();
        ArrayList<String> expectedFeedbackList = new ArrayList<>();
        expectedFeedbackList.add("feedbackAfterInit");
        String expectedComments = JsonRestUtils.toJson(expectedFeedbackList);

        expectedFeedback.setId(1L);
        expectedFeedback.setsID_Source("-1");
        expectedFeedback.setsAuthorFIO("FIO");
        expectedFeedback.setsMail("sMail");
        expectedFeedback.setsHead("sHead");
        expectedFeedback.setsBody("sBody");
        expectedFeedback.setsPlace("sPlace");
        expectedFeedback.setnID_Rate(-1L);
        expectedFeedback.setnID_Service(-1L);
        expectedFeedback.setsAnswer(expectedComments);


        when(subjectMessageService.setSubjectMessageFeedback(expectedFeedback.getsID_Source(),
                expectedFeedback.getsAuthorFIO(),
                expectedFeedback.getsMail(),
                expectedFeedback.getsHead(),
                expectedFeedback.getsBody(),
                expectedFeedback.getsPlace(),
                expectedFeedback.getsEmployeeFIO(),
                expectedFeedback.getnID_Rate(),
                expectedFeedback.getnID_Service(),
                "feedbackAfterInit",
                null,
                null))
                .thenCallRealMethod();


        mockMvc.perform(post("/subject/message/setFeedbackExternal").
                contentType(MediaType.APPLICATION_JSON)
                .param("sID_Source", expectedFeedback.getsID_Source())
                .param("sAuthorFIO", expectedFeedback.getsAuthorFIO())
                .param("sMail", expectedFeedback.getsMail())
                .param("sHead", expectedFeedback.getsHead())
                .param("sBody", expectedFeedback.getsBody())
                .param("sPlace", expectedFeedback.getsPlace())
                .param("nID_Rate", expectedFeedback.getnID_Rate().toString())
                .param("nID_Service", expectedFeedback.getnID_Service().toString())
                .param("sAnswer", "feedbackAfterInit"))
                .andExpect(status().isCreated());
    }

    //  TODO:  THIS TEST SHOULD BE INTEGRATIONAL OR REFACTORED
    //    this test need prepared DB with SubjectMessageFeedback or DAO mock
    @Ignore
    @Test
    public void shouldUpdateFeedbackIfnIdPresent() throws Exception {
        SubjectMessageFeedback expectedFeedback = new SubjectMessageFeedback();
        List<String> expectedFeedbackList = new ArrayList<>();
        expectedFeedbackList.add("feedbackAfterInit");
        String expectedComments = JsonRestUtils.toJson(expectedFeedbackList);

        expectedFeedback.setId(1L);
        expectedFeedback.setsID_Source("-1");
        expectedFeedback.setsAuthorFIO("FIO");
        expectedFeedback.setsMail("sMail");
        expectedFeedback.setsHead("sHead");
        expectedFeedback.setsBody("sBody");
        expectedFeedback.setsPlace("sPlace");
        expectedFeedback.setnID_Rate(-1L);
        expectedFeedback.setnID_Service(-1L);
        expectedFeedback.setsAnswer(expectedComments);


        when(subjectMessageService.setSubjectMessageFeedback(expectedFeedback.getsID_Source(),
                expectedFeedback.getsAuthorFIO(),
                expectedFeedback.getsMail(),
                expectedFeedback.getsHead(),
                expectedFeedback.getsBody(),
                expectedFeedback.getsPlace(),
                expectedFeedback.getsEmployeeFIO(),
                expectedFeedback.getnID_Rate(),
                expectedFeedback.getnID_Service(),
                "feedbackAfterInit",
                expectedFeedback.getId(),
                null))
                .thenCallRealMethod();


        mockMvc.perform(post("/subject/message/setFeedbackExternal").
                contentType(MediaType.APPLICATION_JSON)
                .param("sID_Source", expectedFeedback.getsID_Source())
                .param("sAuthorFIO", expectedFeedback.getsAuthorFIO())
                .param("sMail", expectedFeedback.getsMail())
                .param("sHead", expectedFeedback.getsHead())
                .param("sBody", expectedFeedback.getsBody())
                .param("sPlace", expectedFeedback.getsPlace())
                .param("nID_Rate", expectedFeedback.getnID_Rate().toString())
                .param("nID_Service", expectedFeedback.getnID_Service().toString())
                .param("sAnswer", "feedbackAfterInit")
                .param("nID", expectedFeedback.getId().toString()))
                .andExpect(status().isCreated());
    }
}
