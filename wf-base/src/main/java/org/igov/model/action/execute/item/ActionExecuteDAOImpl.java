package org.igov.model.action.execute.item;

import java.util.ArrayList;
import java.util.List;

import org.activiti.engine.impl.util.json.JSONArray;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.igov.model.core.GenericEntityDao;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ActionExecuteDAOImpl extends GenericEntityDao<Long, ActionExecute> implements ActionExecuteDAO {

	@Autowired
	ActionExecuteOldDAO actionExecuteOldDAO;
	
	private static final Logger LOG = LoggerFactory.getLogger(ActionExecuteDAOImpl.class);
	
	protected ActionExecuteDAOImpl() {
		super(ActionExecute.class);
	}

	@Override
	public ActionExecute getActionExecute(Long id) {
		return findById(id).orNull();
	}

	@Override
	public List<ActionExecute> getAllActionExecutes() {
		return findAll();
	}

	@Transactional
	@Override
	public ActionExecute setActionExecute(Long nID_ActionExecuteStatus,
			DateTime oDateMake, DateTime oDateEdit, Integer nTry,
			String sObject, String sMethod, byte[] soRequest, String smParam, String sReturn) {
		ActionExecute actionExecute = new ActionExecute();
		ActionExecuteStatus aes = new ActionExecuteStatus();
        aes.setId(nID_ActionExecuteStatus);
        actionExecute.setActionExecuteStatus(aes);

		actionExecute.setoDateMake(oDateMake);
		actionExecute.setoDateEdit(oDateEdit);
		actionExecute.setnTry(nTry);
		actionExecute.setsObject(sObject);
		actionExecute.setsMethod(sMethod);
		actionExecute.setSoRequest(soRequest);
		actionExecute.setSmParam(smParam);
		actionExecute.setsReturn(sReturn);
		
		getSession().saveOrUpdate(actionExecute);
		return actionExecute;
	}

	@Transactional
	@Override
	public List<ActionExecute> getActionExecute(Integer nRowsMax, String sMethodMask, String asID_Status, Integer nTryMax, Long nID) {		
		return getActionExecuteListByCriteria(nRowsMax, sMethodMask, asID_Status, nTryMax, nID);
	}

	@Override
	@Transactional
	public void moveActionExecute(Integer nRowsMax, String sMethodMask, String asID_Status, Integer nTryMax, Long nID) {
		List<ActionExecute> actionExecuteList = new ArrayList<ActionExecute>();
		actionExecuteList = getActionExecuteListByCriteria(nRowsMax, sMethodMask, asID_Status, nTryMax, nID);
		if (actionExecuteList.size()>0){
			for(ActionExecute actionExecute:actionExecuteList){
				moveActionExecute(actionExecute);
			}
		}
	}
	
	@Transactional
	private List<ActionExecute> getActionExecuteListByCriteria(Integer nRowsMax, String sMethodMask, String asID_Status, Integer nTryMax, Long nID){
		Criteria criteria = getSession().createCriteria(ActionExecute.class);
		LOG.info("in getActionExecuteListByCriteria");
		criteria.setMaxResults(nRowsMax);
		LOG.info("nRowsMax - {}", nRowsMax);
		LOG.info("nTryMax - {}", nTryMax);
		if(nTryMax!=null)
			criteria.add(Restrictions.le("nTry", nTryMax));
		LOG.info("nID - {}", nID);
		if(nID!=null)
			criteria.add(Restrictions.eq("id", nID));
		LOG.info("asID_Status - {}", asID_Status);
		if(asID_Status!=null){			
			JSONArray statuses = new JSONArray(asID_Status);			
			for(int i=0;i<statuses.length();i++){
				criteria.add(Restrictions.in("nID_ActionExecuteStatus", (Object[]) statuses.get(i)));
			}
		}
		LOG.info("sMethodMask - {}", sMethodMask);
		if(sMethodMask!=null){
			if(sMethodMask.contains("*"))			
				criteria.add(Restrictions.like("sMethod", sMethodMask.replace("*", "%")));
			else
				criteria.add(Restrictions.eq("sMethod", sMethodMask));
		}		
		return criteria.list();
	}
	
	@Transactional
	public void moveActionExecute(ActionExecute actionExecute) {
		ActionExecuteOld actionExecuteOld = new ActionExecuteOld();
        
		actionExecuteOld.setActionExecuteStatus(actionExecute.getActionExecuteStatus());
		actionExecuteOld.setoDateMake(actionExecute.getoDateMake());
		actionExecuteOld.setoDateEdit(actionExecute.getoDateEdit());
		actionExecuteOld.setnTry(actionExecute.getnTry());
		actionExecuteOld.setsObject(actionExecute.getsObject());
		actionExecuteOld.setsMethod(actionExecute.getsMethod());
		actionExecuteOld.setSoRequest(actionExecute.getSoRequest());
		actionExecuteOld.setsReturn(actionExecute.getsReturn());
		actionExecuteOld.setSmParam(actionExecute.getSmParam());
		
		actionExecuteOldDAO.saveOrUpdate(actionExecuteOld);
		getSession().delete(actionExecute);		
	}
}
