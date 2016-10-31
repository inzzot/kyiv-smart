package org.igov.model.action.event;

import org.igov.model.core.EntityDao;
import org.igov.service.exception.CRCInvalidException;

import java.util.List;
import java.util.Map;

import org.igov.service.exception.EntityNotFoundException;
import org.joda.time.DateTime;

public interface HistoryEvent_ServiceDao extends EntityDao<Long, HistoryEvent_Service> {

    HistoryEvent_Service addHistoryEvent_Service(HistoryEvent_Service event_service);

    HistoryEvent_Service updateHistoryEvent_Service(HistoryEvent_Service historyEvent_Service);

    List<Map<String, Long>> getHistoryEvent_ServiceBynID_Service(Long nID_Service);

    HistoryEvent_Service getOrgerByID(String sID_Order) throws CRCInvalidException, EntityNotFoundException, IllegalArgumentException;

    HistoryEvent_Service getOrgerByProcessID(Long nID_Process, Integer nID_Server);

    HistoryEvent_Service getOrgerByProtectedID(Long nID_Protected, Integer nID_Server) throws CRCInvalidException, EntityNotFoundException;
    
    HistoryEvent_Service getLastTaskHistory(Long nID_Subject, Long nID_Service, String sID_UA);

    List<HistoryEvent_Service> getOrdersHistory(Long nID_Subject, Long nID_Service, String sID_UA);

    List<HistoryEvent_Service> getHistoryEventPeriod(DateTime dateAt, DateTime dateTo, List<Long> anID_Service_Exclude);

    /**
     * method analyses HistoryEvent_Service records filters them and perform grouping an aggregations
     * result of aggregations represented in separate entity ServicesStatistics
     * @param from
     * @param to
     * @return
     */
    List<ServicesStatistics> getServicesStatistics(DateTime from, DateTime to);
}
