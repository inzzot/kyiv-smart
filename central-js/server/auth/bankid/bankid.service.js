var request = require('request')
  , FormData = require('form-data')
  , async = require('async')
  , _ = require('lodash')
  , config = require('../../config/environment')
  , syncSubject = require('../../api/subject/subject.service')
  , Admin = require('../../components/admin/index')
  , url = require('url')
  , StringDecoder = require('string_decoder').StringDecoder
  , uploadFileService = require('../../api/uploadfile/uploadfile.service')
  , bankidUtil = require('./bankid.util')
  , errors = require('../../components/errors')
  , activiti = require('../../components/activiti');

var createError = function (error, error_description, response) {
  return {
    code: response ? response.statusCode : 500,
    err: {
      error: error,
      error_description: error_description
    }
  };
};

module.exports.decryptCallback = function (callback) {
 return bankidUtil.decryptCallback(callback);
};

module.exports.convertToCanonical = function (customer) {
  // сохранение признака для отображения надписи о необходимости проверки регистрационных данных, переданых от BankID
  customer.isAuthTypeFromBankID = true;
  return customer;
};

module.exports.getUserKeyFromSession = function (session){
  return session.access.accessToken;
};

module.exports.index = function (accessToken, callback, disableDecryption) {
  var url = bankidUtil.getInfoURL(config);

  function validateResponse(errorCallback, successCallback) {
    return function (error, repsonse, body) {
      if(error){
        errorCallback(error, repsonse, body);
      } else if (body && (body.state === 'err' ||  body.error)) {
        errorCallback(error, repsonse, body);
      } else {
        successCallback(error, repsonse, body);
      }
    }
  }

  function errorCallback(error, response, body) {
    callback(error, response, body);
  }

  function adminCheckCallback(error, response, body) {
    console.log("--------------- enter admin callback !!!!");
    var innToCheck;

    if (disableDecryption) {
      console.log("---------------  innToCheck before decryption !!!!" + body.customer.inn);
      innToCheck = bankidUtil.decryptField(body.customer.inn);
      console.log("---------------  innToCheck after decryption !!!!" + innToCheck);
    } else {
      innToCheck = body.customer.inn;
      console.log("--------------- nodecrption of inn !!!!");
    }

    console.log("---------------  innToCheck in result !!!!" + innToCheck);

    console.log("---------------  body.customer in result !!!!" + body.customer);
    console.log("---------------  Admin.isAdminInn(innToCheck) in result!!!! " + Admin.isAdminInn(innToCheck));

    if (body.customer && Admin.isAdminInn(innToCheck)) {
      console.log("---------------  user with inn " + innToCheck + " is admin");
      body.admin = {
        inn: innToCheck,
        token: Admin.generateAdminToken()
      };
    }
    callback(error, response, body);
  }

  var resultCallback;

  if (disableDecryption) {
    resultCallback = validateResponse(errorCallback, adminCheckCallback);
  } else {
    resultCallback = validateResponse(errorCallback, bankidUtil.decryptCallback(adminCheckCallback));
  }

  return request.post({
    'url': url,
    'headers': {
      'Content-Type': 'application/json',
      'Authorization': bankidUtil.getAuth(accessToken),
      'Accept': 'application/json'
    },
    json: true,
    body: {
      "type": "physical",
      "fields": ["firstName", "middleName", "lastName", "phone", "inn", "clId", "clIdText", "birthDay", "email"],

      "addresses": [
        {
          "type": "factual",
          "fields": ["country", "state", "area", "city", "street", "houseNo", "flatNo", "dateModification"]
        },
        {
          "type": "birth",
          "fields": ["country", "state", "area", "city", "street", "houseNo", "flatNo", "dateModification"]
        }
      ],

      "documents": [{
        "type": "passport",
        "fields": ["series", "number", "issue", "dateIssue", "dateExpiration", "issueCountryIso2"]
      }],

      "scans": [{
        "type": "passport",
        "fields": ["link", "dateCreate", "extension"]
      }, {
        "type": "zpassport",
        "fields": ["link", "dateCreate", "extension"]
      }]
    }
  }, resultCallback);
};

module.exports.scansRequest = function (accessToken, callback) {
  var url = bankidUtil.getInfoURL();
  return request.post({
    'url': url,
    'headers': {
      'Content-Type': 'application/json',
      'Authorization': bankidUtil.getAuth(accessToken),
      'Accept': 'application/json'
    },
    json: true,
    body: {
      "type": "physical",
      "fields": ["firstName", "middleName", "lastName", "phone", "inn", "clId", "clIdText", "birthDay"],
      "scans": [{
        "type": "passport",
        "fields": ["link", "dateCreate", "extension"]
      }, {
        "type": "zpassport",
        "fields": ["link", "dateCreate", "extension"]
      }]
    }
  }, bankidUtil.decryptCallback(callback));
};

module.exports.getScanContentRequestOptions = function (documentScanLink, accessToken) {
  return {
    url: documentScanLink,
    headers: {
      Authorization: bankidUtil.getAuth(accessToken)
    },
    encoding: null
  };
};

module.exports.getScanContentRequest = function (documentScanLink, accessToken) {
  return request.get(this.getScanContentRequestOptions(documentScanLink, accessToken));
};

/**
 * function downloads buffer with scan bytes
 *
 * @param documentScanLink link to scan from where we should download it
 * @param accessToken access token from bankid authorization
 * @param callback function(error, buffer)
 */
module.exports.scanContentRequest = function (documentScanType, documentScanLink, accessToken, callback) {
  var scanContentRequestOptions = this.getScanContentRequestOptions(documentScanLink, accessToken);
  request.get(scanContentRequestOptions, function (error, response, buffer) {
    if (!error && response.headers['content-type'].indexOf('application/octet-stream') > -1) {
      callback(null, buffer);
    } else if (!error &&
      (response.headers['content-type'].indexOf('application/json') > -1
      || response.headers['content-type'].indexOf('application/xml') > -1)) {
      var decoder = new StringDecoder('utf8');
      callback(errors.createExternalServiceError('Can\'t get scan upload of ' + documentScanType, decoder.write(buffer)));
    } else if (error) {
      callback(errors.createExternalServiceError('Can\'t get scan upload of ' + documentScanType, error));
    }
  });
};

module.exports.cacheCustomer = function (customer, callback) {
  uploadFileService.upload([{
      name: 'file',
      text: JSON.stringify(customer),
      options: {
        filename: 'customerData.json'
      }
    }], callback);
};

module.exports.syncWithSubject = function (accessToken, done) {
  var self = this;
  var disableDecryption = true;

  function errorFromBody(body) {
    if(body && (body.error || body.state === 'err')){
      return body;
    } else {
      return null;
    }
  }

  async.waterfall([
    function (callback) {
      self.index(accessToken, function (error, response, body) {
        var err = errorFromBody(body);
        if (error || err) {
          callback(errors.createErrorOnResponse(err)(error, response, body), null);
        } else {
          callback(null, {
            customer: body.customer,
            admin: body.admin
          });
        }
      }, disableDecryption);
    },

    function (result, callback) {
      syncSubject.sync(bankidUtil.decryptField(result.customer.inn), function (error, response, body) {
        if (error) {
          callback(createError(error, response), null);
        } else {
          result.subject = body;
          callback(null, result);
        }
      });
    },

    function (result, callback) {
      self.cacheCustomer(result, function (error, reponse, body) {
        if (error || body.code) {
          callback(createError(body, 'error while caching data. ' + body.message, response), null);
        } else {
          result.usercacheid = body.fileID;

          if (result.customer.inn) {
            result.customer.inn = bankidUtil.decryptField(result.customer.inn);
          }
          if (result.customer.firstName) {
            result.customer.firstName = bankidUtil.decryptField(result.customer.firstName);
          }
          if (result.customer.middleName) {
            result.customer.middleName = bankidUtil.decryptField(result.customer.middleName);
          }
          if (result.customer.lastName) {
            result.customer.lastName = bankidUtil.decryptField(result.customer.lastName);
          }

          callback(null, result);
        }
      })
    }
  ], function (err, result) {
    done(err, result);
  });
};

module.exports.signFiles = function (accessToken, acceptKeyUrl, content, callback) {
  var bankIDURLs = bankidUtil.getBaseURLs();
  var params = {
    headers: {
      Authorization: bankidUtil.getAuth(accessToken),
      acceptKeyUrl: acceptKeyUrl
    }
  };

  console.log('[signFiles] : accessToken=', accessToken, 'acceptKeyUrl=', acceptKeyUrl, ' content=', JSON.stringify(content.map(function(contentObj) {
      return contentObj.name + ', ' + (contentObj.options ? JSON.stringify(contentObj.options) : '')
    }))
  );

  activiti.uploadContent(bankIDURLs.resource.path.signFiles, params, content, function (error, response, body) {
    if (!body) {
      callback('Unable to sign a file. bankid.privatbank.ua return an empty response', null);
    } else if (body && errors.isHttpError(response.statusCode)) {
      callback(errors.createExternalServiceError('Uknown error in the process of uploading content for adding eds', body), null);
    } else if (error || (error = body.error)) {
      callback(error, null);
    } else if (body.state && body.state === 'err'){
      callback(errors.createExternalServiceError(body.desc, body), null);
    } else {
      callback(null, body);
    }
  }, bankIDURLs.resource.base);
};

/**
 *
 * @param accessToken
 * @param codeValue
 * @param callback function(error, content)
 */
module.exports.downloadSignedContent = function (accessToken, codeValue, callback) {
  var r = {
    url: bankidUtil.getClientPdfClaim(codeValue),
    headers: {
      Authorization: bankidUtil.getAuth(accessToken)
    },
    encoding: null
  };

  request.get(r, function (error, response, buffer) {
    callback(null, {
      buffer: buffer,
      contentType: response.headers['content-type'],
      fileName: response.headers['content-disposition'].split('filename=')[1]
    });
  })
};
/**
 * После отработки п.3 (подписание), BankID делает редирект на https://{PI:port}/URL_callback?code=code_value с передачей
 * параметра авторизационного ключа code, тем самым заканчивая фазу п.4.
 * https://{PI:port}/ResourceService/checked/claim/code_value/clientPdfClaim
 * @param accessToken
 * @param codeValue
 */
module.exports.prepareSignedContentRequest = function (accessToken, codeValue) {
  return module.exports.getScanContentRequest(bankidUtil.getClientPdfClaim(codeValue), accessToken);
};

/**
 *  Content-Type = "multipart/form-data"
 * Authorization = "Bearer access_token, Id client_id" - (последовательность не важна)
 * Accept = "application/json"
 * acceptKeyUrl = URL_callback - (урл перенаправления браузера и передача ключа для забора подписанного PDF)
 * fileType = "pdf" (так же принимает еще значения html, image, которые будут преобразованы в формат PDF )
 * В результате ответа будет получен JSON вида
 * {"state":"ok","code":"000000","desc":"https://{IP:port}/IdentDigitalSignature/signPdf?sidBi=sidBi_value"}
 *
 * @param accessToken
 * @param acceptKeyUrl
 * @param formToUpload
 * @param callback
 */
module.exports.signHtmlForm = function (accessToken, acceptKeyUrl, formToUpload, callback) {
  var uploadURL = bankidUtil.getUploadFileForSignatureURL();

  var form = new FormData();
  form.append('file', formToUpload, {
    contentType: 'text/html'
  });

  var requestOptionsForUploadContent = {
    url: uploadURL,
    headers: _.merge({
      Authorization: bankidUtil.getAuth(accessToken),
      acceptKeyUrl: acceptKeyUrl,
      fileType: 'html'
    }, form.getHeaders()),
    formData: {
      file: formToUpload
    },
    json: true
  };

  request.post(requestOptionsForUploadContent, function (error, response, body) {
    if (!body)
      callback('Unable to sign a file. bankid.privatbank.ua return an empty response', null);
    else if (error || (error = body.error)) {
      callback(error, null);
    } else {
      callback(null, body);
    }
  });
};

/**
 * После отработки п.3 (подписание), BankID делает редирект на https://{PI:port}/URL_callback?code=code_value с передачей
 * параметра авторизационного ключа code, тем самым заканчивая фазу п.4.
 * https://{PI:port}/ResourceService/checked/claim/code_value/clientPdfClaim
 * @param accessToken
 * @param codeValue
 */
module.exports.prepareSignedContentRequest = function (accessToken, codeValue) {
  return module.exports.getScanContentRequest(bankidUtil.getClientPdfClaim(codeValue), accessToken);
};

