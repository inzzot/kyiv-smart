package org.igov.service.business.finance;

import org.igov.service.controller.security.AuthenticationTokenSelector;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.igov.service.business.object.Language;
import org.igov.service.business.access.AccessKeyService;
import org.igov.io.web.HttpRequester;
import org.igov.io.GeneralConfig;

import java.util.HashMap;
import java.util.Map;

import org.igov.service.controller.security.AccessContract;
import org.igov.util.ToolWeb;
import static org.igov.util.ToolWeb.getSignature;
import static org.igov.util.ToolWeb.base64_encode;

@Component()
public class Liqpay {

    public static final Language DEFAULT_LANG = Language.RUSSIAN;
    private static final Logger LOG = LoggerFactory.getLogger(Liqpay.class);
    private static final String sURL_Liqpay = "https://www.liqpay.com/api/checkout";
    private static final String version = "3";
    private static final String sandbox = "1";
    private static final String sHTML_PayButton = new StringBuilder()
            .append("<form method=\"POST\" action=\"")
            .append(sURL_Liqpay)
            .append("\" ")
            .append("accept-charset=\"utf-8\">")
            .append("<input type=\"hidden\" name=\"data\" value=\"%s\"/>")
            .append("<input type=\"hidden\" name=\"signature\" value=\"%s\"/>")
            .append("<input type=\"image\" src=\"https://static.liqpay.com/buttons/p1%s.radius.png\"/>")
            .append("</form>").toString();
    @Autowired
    GeneralConfig generalConfig;
    @Autowired
    HttpRequester httpRequester;
    @Autowired
    AccessKeyService accessCover;
    
    private String privateKey;
    //@Autowired
    //private AccessDataService accessDataDao;
    //result = result.replaceAll("\\Q//static.liqpay.com\\E", "https://static.liqpay.com");

    public Map<String, String> getPayData(String sID_Merchant, String sSum,
            Currency oID_Currency, Language oLanguage, String sDescription,
            String sID_Order, String sURL_CallbackStatusNew,
            String sURL_CallbackPaySuccess, Long nID_Subject, boolean bTest) throws Exception {

        
        if (oLanguage == null) {
            oLanguage = DEFAULT_LANG;
        }

        String sURL = "/wf/service/finance/getMerchant";
        Map<String, String> paramMerchant = new HashMap<String, String>();
        paramMerchant.put("sID", sID_Merchant);
        String soJSON_Merchant = httpRequester.getInside(generalConfig.getSelfHostCentral() + sURL, paramMerchant);
        LOG.info("(soJSON_Merchant={})", soJSON_Merchant);

        JSONParser oJSONParser = new JSONParser();
        JSONObject oJSONObject = (JSONObject) oJSONParser.parse(soJSON_Merchant);

        String sPublicKey = sID_Merchant;
        privateKey = (String) oJSONObject.get("sPrivateKey");
        if (privateKey == null) {
            privateKey = "test";
        }
        if (sURL_CallbackStatusNew == null) {
            if (oJSONObject.get("sURL_CallbackStatusNew") != null) {
                sURL_CallbackStatusNew = (String) oJSONObject.get("sURL_CallbackStatusNew");
            } else {
                sURL_CallbackStatusNew = "";
            }
        }
        LOG.info("(sURL_CallbackStatusNew={})", sURL_CallbackStatusNew);

        if (sURL_CallbackPaySuccess == null) {
            if (oJSONObject.get("sURL_CallbackPaySuccess") != null) {
                sURL_CallbackPaySuccess = (String) oJSONObject.get("sURL_CallbackPaySuccess");
            } else {
                sURL_CallbackPaySuccess = generalConfig.getSelfHostCentral(); //"https://igov.org.ua";
            }
        }
        LOG.info("(sURL_CallbackPaySuccess={})", sURL_CallbackPaySuccess);

        if (sURL_CallbackStatusNew != null && !"".equals(sURL_CallbackStatusNew)) {
            LOG.info("(nID_Subject={})", nID_Subject);
            if (nID_Subject == null) {
                nID_Subject = new Long(0);
            }
            String snID_Subject = "" + nID_Subject;
            LOG.info("(snID_Subject={})", snID_Subject);
            String delimiter = sURL_CallbackStatusNew.indexOf("?") > -1 ? "&" : "?";
            String queryParam = delimiter + "nID_Subject=" + nID_Subject;
            sURL = ToolWeb.deleteContextFromURL(sURL_CallbackStatusNew) + queryParam;
            LOG.info("(URI={})", sURL);
            //String sAccessKey = accessDataDao.setAccessData(URI);
            String sAccessKey = accessCover.getAccessKey(sURL);
            //            sURL_CallbackStatusNew = sURL_CallbackStatusNew + queryParam + "&sAccessContract=Request" + "&sAccessKey=" + sAccessKey;
            sURL_CallbackStatusNew = sURL_CallbackStatusNew + queryParam
                    + "&" + AuthenticationTokenSelector.ACCESS_CONTRACT + "="
                    + AccessContract.Request.name()
                    + "&" + AuthenticationTokenSelector.ACCESS_KEY + "=" + sAccessKey
            ;
        }
        LOG.info("sURL_CallbackStatusNew(with security-key)={}", sURL_CallbackStatusNew);

        Map<String, String> params = new HashMap<String, String>();
        params.put("version", version);
        params.put("amount", sSum);
        params.put("currency", oID_Currency.name());
        params.put("language", oLanguage.getShortName());
        params.put("description", sDescription);
        params.put("order_id", sID_Order);
        params.put("server_url", sURL_CallbackStatusNew);
        params.put("result_url", sURL_CallbackPaySuccess);
        params.put("public_key", sPublicKey);

        if (bTest) {
            params.put("sandbox", sandbox);
        }

        LOG.info("(params={},privateKey={})", params, privateKey);
        return params;
    }

        
    public String getPayButtonHTML_LiqPay(String sID_Merchant, String sSum,
            Currency oID_Currency, Language oLanguage, String sDescription,
            String sID_Order, String sURL_CallbackStatusNew,
            String sURL_CallbackPaySuccess, Long nID_Subject, boolean bTest) throws Exception {

        Map<String, String> mParam = getPayData(sID_Merchant, sSum,
            oID_Currency, oLanguage, sDescription,
            sID_Order, sURL_CallbackStatusNew,
            sURL_CallbackPaySuccess, nID_Subject, bTest);

        String sHTML = getForm(mParam, privateKey, oLanguage);
        LOG.info("ok! (sHTML={})", sHTML);
        return sHTML;
    }

    private String getForm(Map<String, String> mParam, String sPrivateKey, Language oLanguage) {
        String sData = base64_encode(JSONObject.toJSONString(mParam));
        String sSignature = getSignature(sData, sPrivateKey);
        return String.format(sHTML_PayButton, sData, sSignature, oLanguage.getShortName());
    }

    public Map<String, String> getPayDataRequest(String sID_Merchant, String sSum,
            Currency oID_Currency, Language oLanguage, String sDescription,
            String sID_Order, String sURL_CallbackStatusNew,
            String sURL_CallbackPaySuccess, Long nID_Subject, boolean bTest) throws Exception {
        
        Map<String, String> mParam = getPayData(sID_Merchant, sSum,
            oID_Currency, oLanguage, sDescription,
            sID_Order, sURL_CallbackStatusNew,
            sURL_CallbackPaySuccess, nID_Subject, bTest);

        //String result = getForm(mParam, privateKey, oLanguage);
        String sData = base64_encode(JSONObject.toJSONString(mParam));
        String sSignature = getSignature(sData, privateKey);
        //return String.format(payButtonHTML, sData, sSignature, oLanguage.getShortName());
        
        Map<String, String> mReturn = new HashMap();
        mReturn.put("sURL", sURL_Liqpay);
        mReturn.put("data", sData);
        mReturn.put("signature", sSignature);
        
        LOG.info("ok! (mReturn={})", mReturn);
        return mReturn;
    }
}
