angular.module('app').directive('slotPicker', function($http, $filter, dialogs, ErrorsFactory) {

  return {
    restrict: 'EA',
    templateUrl: 'app/common/components/form/directives/slotPicker.html',
    scope: {
      serviceData: "=",
      service: "=",
      ngModel: "=",
      formData: "=",
      property: "="
    },
    link: function(scope) {
      scope.selected = {
        date: null,
        slot: null
      };

      scope.isConnectionServer = false;

      scope.$watch('selected.date', function() {
        scope.selected.slot = null;
      });

      var resetData = function()
      {
        scope.slotsData = {};
        scope.selected.date = null;
        scope.selected.slot = null;
        scope.ngModel = null;
      };

      var sID_Type_ID = 'sID_Type_' + scope.property.id;
      var nID_ServiceCustomPrivate_ID = 'nID_ServiceCustomPrivate_' + scope.property.id;
      var isQueueDataType = {
        iGov: !scope.formData.params[sID_Type_ID] || (scope.formData.params[sID_Type_ID] && (scope.formData.params[sID_Type_ID].value === 'iGov' || scope.formData.params[sID_Type_ID].value === '')),
        DMS: scope.formData.params[sID_Type_ID] && scope.formData.params[sID_Type_ID].value === 'DMS'
      };

      function isInvalidServiceCustomPrivate() {
        if (!scope.formData.params[nID_ServiceCustomPrivate_ID] ||
          scope.formData.params[nID_ServiceCustomPrivate_ID].value === null ||
          scope.formData.params[nID_ServiceCustomPrivate_ID].value === ''){
          console.warn('Field ' + nID_ServiceCustomPrivate_ID + ' is EMPTY');
          return true;
        }
        return false;
      }

      var nSlotsKey = 'nSlots_' + scope.property.id;
      var nSlotsParam = scope.formData.params[nSlotsKey];

      var nDiffDaysProperty = 'nDiffDays_' + scope.property.id;
      var nDiffDaysParam = scope.formData.params[nDiffDaysProperty];

      function selectedSlot(newValue) {
        if (isQueueDataType.DMS) {
          if(newValue){
            if (isInvalidServiceCustomPrivate()) return;
            var data = {
              nID_Server: scope.serviceData.nID_Server,
              nID_Service_Private: scope.formData.params[nID_ServiceCustomPrivate_ID].value,
              sDateTime: scope.selected.date.sDate + " " + newValue.sTime,
              sSubjectFamily: scope.formData.params.bankIdlastName.value,
              sSubjectName: scope.formData.params.bankIdfirstName.value,
              sSubjectSurname: scope.formData.params.bankIdmiddleName.value || '',
              sSubjectPassport: getPasportLastFourNumbers(scope.formData.params.bankIdPassport.value),
              sSubjectPhone: scope.formData.params.phone.value || ''
            };
            scope.isConnectionServer = true;
            $http.post('/api/service/flow/DMS/setSlotHold', data).
            success(function(data, status, headers, config) {
              scope.isConnectionServer = false;
              scope.ngModel = JSON.stringify({
                reserved_to: data.reserved_to,
                reserve_id: data.reserve_id,
                interval: data.interval
              });
              console.info('Reserved slot: ' + angular.toJson(data));
            }).
            error(function(data, status, headers, config) {
              scope.isConnectionServer = false;
              console.error('Error reserved slot ' + angular.toJson(data));
              var err = data.message.split(": response=");
              if(data.message.indexOf('api.cherg.net') >= 0 && err[1]){
                if(data.message.indexOf('Время уже занято') >= 0){
                  ErrorsFactory.push({
                    type: 'warning',
                    text: 'Неможливо вибрати час. Спробуйте обрати інший або пізніше, будь ласка'
                  });
                } else {
                  ErrorsFactory.push({
                    sType: 'danger',
                    sBody: 'Помилка резервування слота на сервері ДМС.',
                    sNote: 'Відповідь сервера: ' + err[1]
                  });
                }
                scope.selected.slot = null;
              } else {
                ErrorsFactory.push({
                  sType: 'danger',
                  sBody: 'Помилка при резервуванні слота.',
                  sNote: 'Відповідь сервера: ' + data.message
                });
              }
            });
          }
        } else if (isQueueDataType.iGov) {
          if (newValue) {
            var setFlowUrl = '/api/service/flow/set/' + newValue.nID + '?nID_Server=' + scope.serviceData.nID_Server;
            if (nSlotsParam) {
              var nSlots = parseInt(nSlotsParam.value) || 0;
              if (nSlots > 1)
                setFlowUrl += '&nSlots=' + nSlots;
            }
            scope.isConnectionServer = true;
            $http.post(setFlowUrl).then(function (response) {
              scope.isConnectionServer = false;
              scope.ngModel = JSON.stringify({
                sID_Type: "iGov",
                nID_FlowSlotTicket: response.data.nID_Ticket,
                sDate: scope.selected.date.sDate + ' ' + scope.selected.slot.sTime + ':00.00'
              });
            }, function () {
              scope.isConnectionServer = false;
              scope.selected.date.aSlot.splice(scope.selected.date.aSlot.indexOf(scope.selected.slot), 1);
              scope.selected.slot = null;
              //dialogs.error('Помилка', 'Неможливо вибрати час. Спробуйте обрати інший або пізніше, будь ласка');
              ErrorsFactory.push({
                type: 'danger',
                text: 'Неможливо вибрати час. Спробуйте обрати інший або пізніше, будь ласка'
              });
            });
          }
        }
      }

      function updateReservedSlot(newValue) {
        if (newValue && isQueueDataType.DMS){
          selectedSlot(scope.selected.slot);
        }
      }

      scope.$watch('selected.slot', function(newValue) {
        selectedSlot(newValue);
      });

      function getPasportLastFourNumbers(str) {
        if(!str || str === "") return "";
        return str.replace(new RegExp(/\s+/g), ' ').match(new RegExp(/\S{2} {0,1}\d{6}/gi))[0].match(new RegExp(/\d{4,4}$/))[0];
      }

      scope.unreadyRequestDMS = function () {
        if (isQueueDataType.DMS){
          return this.$parent.$parent.$parent.$parent.$parent.form.phone.$invalid ||
            (!scope.formData.params.bankIdlastName || scope.formData.params.bankIdlastName.value === '') ||
            (!scope.formData.params.bankIdfirstName || scope.formData.params.bankIdfirstName.value === '') ||
            (getPasportLastFourNumbers(scope.formData.params.bankIdPassport.value).length != 4);
        } else {
          return false;
        }
      };

      scope.slotsData = {};
      scope.slotsLoading = true;

      var departmentProperty = 'nID_Department_' + scope.property.id;
      var departmentParam = scope.formData.params[departmentProperty];

      scope.loadList = function(){
        var data = {};
        var sURL = '';

        if (isQueueDataType.DMS){

          if (isInvalidServiceCustomPrivate()) return;

          data = {
            nID_Server: scope.serviceData.nID_Server,
            nID_Service_Private: this.formData.params[nID_ServiceCustomPrivate_ID].value
          };
          sURL = '/api/service/flow/DMS/getSlots';

        } else if (isQueueDataType.iGov) {

          data = {
            nID_Server: scope.serviceData.nID_Server,
            nID_Service: (scope && scope.service && scope.service!==null ? scope.service.nID : null)
          };

          if (departmentParam) {
            if (parseInt(departmentParam.value) > 0)
              data.nID_SubjectOrganDepartment = departmentParam.value;
            else return;
          }

          if (nSlotsParam && parseInt(nSlotsParam.value) > 1) {
            data.nSlots = nSlotsParam.value;
          }

          if (nDiffDaysParam && parseInt(nDiffDaysParam.value) > 0) {
            data.nDiffDays = nDiffDaysParam.value;
          }

          sURL = '/api/service/flow/' + scope.serviceData.nID;
        } else {
          scope.slotsLoading = false;
          ErrorsFactory.push({
            type: 'danger',
            text: 'В полі ' + sID_Type_ID + ' прописаний непыдтримуэмий тип для поля queueData: ' + scope.formData.params[sID_Type_ID].value
          });
          console.error('slotsData for field id [' + this.property.id + '] not loading');
          return;
        }

        scope.slotsLoading = true;
        scope.isConnectionServer = true;

        return $http.get(sURL, {params:data}).
        success(function (response) {
          scope.isConnectionServer = false;
          if (isQueueDataType.DMS){
            scope.slotsData = convertSlotsDataDMS(response);
          } else if (isQueueDataType.iGov) {
            scope.slotsData = response;
          }
          scope.slotsLoading = false;
        }).
        error(function (err) {
          scope.isConnectionServer = false;
          scope.slotsLoading = false;
          if (isQueueDataType.DMS){
            ErrorsFactory.push({
              sType: 'warning',
              sBody: 'Виникла помилка при отриманні данних від сервера ДМС. Будь ласка, повторіть спробу пізніше.',
              sNote: 'Детальніше: ' + JSON.toString(err)
            });
          } else if (isQueueDataType.iGov) {
            ErrorsFactory.push({
              sType: 'warning',
              sBody: 'Виникла помилка при отриманні данних від сервера ДМС. Будь ласка, повторіть спробу пізніше.',
              sNote: 'Детальніше: ' + JSON.toString(err)
            });
          }
        }.bind(this));
      };

      function convertSlotsDataDMS(data) {
        var aDay = [];
        var nSlotID = 1;
        for (var sDate in data) if (data.hasOwnProperty(sDate)) {
          aDay.push({
            aSlot: [],
            sDate: $filter('date')(new Date(sDate), 'yyyy-MM-dd')
          });
          angular.forEach(data[sDate], function (slot) {
            aDay[aDay.length - 1].aSlot.push({
              bFree: true,
              nID: nSlotID,
              nMinutes: slot.t_length,
              sTime: slot.time
            });
            nSlotID++;
          });
          aDay[aDay.length - 1].bHasFree = aDay[aDay.length - 1].aSlot.length > 0;
        }
        var result = {
          aDay: []
        };
        angular.forEach(aDay, function (day) {
          if(day.aSlot.length > 0){
            result.aDay.push(day);
          }
        });
        result.aDay.sort(function (a, b) {
          return Date.parse(a.sDate) - Date.parse(b.sDate);
        });
        return result;
      }

      if (angular.isDefined(departmentParam)) {
        scope.$watch('formData.params.' + departmentProperty + '.value', function (newValue, oldValue) {
          resetData();
          if (parseInt(newValue) > 0) {
            scope.loadList();
          }
        });
      } else {
        scope.loadList();
      }

      if (angular.isDefined(nSlotsParam)) {
        scope.$watch('formData.params.' + nSlotsKey + '.value', function (newValue, oldValue) {
          if (newValue == oldValue)
            return;
          resetData();
          scope.loadList();
        });
      }

      if (angular.isDefined(nDiffDaysParam)) {
        scope.$watch('formData.params.' + nDiffDaysProperty + '.value', function (newValue, oldValue) {
          if (newValue == oldValue)
            return;
          resetData();
          scope.loadList();
        });
      }

      scope.$watch('formData.params.' + nID_ServiceCustomPrivate_ID + '.value', function () {
        resetData();
        scope.loadList();
      });

      scope.$watch('formData.params.bankIdlastName.value', function (newValue) {
        updateReservedSlot(newValue);
      });

      scope.$watch('formData.params.bankIdfirstName.value', function (newValue) {
        updateReservedSlot(newValue);
      });

      scope.$watch('formData.params.bankIdmiddleName.value', function (newValue) {
        updateReservedSlot(newValue);
      });

      scope.$watch('formData.params.bankIdPassport.value', function (newValue) {
        updateReservedSlot(newValue);
      });

      scope.$watch('formData.params.phone.value', function (newValue) {
        updateReservedSlot(newValue);
      });

      scope.$watch('formData.params.' + nDiffDaysProperty + '.value', function (newValue) {
        resetData();
        scope.loadList();
      });

      scope.loadList();
    }
  }
});
