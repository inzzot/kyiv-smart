(function () {
  'use strict';

  angular
    .module('dashboardJsApp')
    .factory('iGovNavbarHelper', iGovNavbarHelperFactory);

  iGovNavbarHelperFactory.$inject = ['Auth', 'tasks', '$location', '$state'];
  function iGovNavbarHelperFactory(Auth, tasks, $location, $state) {
    var service = {
      areInstrumentsVisible: false,
      auth: Auth,
      getCurrentTab: getCurrentTab,
      isCollapsed: true,
      isTest: false,
      load: load,
      loadTaskCounters: loadTaskCounters,
      instrumentsMenus: [],
      tasksSearch: {
        value: null,
        count: 0,
        archive: false,
        loading: false,
        submited: false
      }
    };

    service.menus = [{
      title: 'Необроблені',
      type: tasks.filterTypes.unassigned,
      count: 0,
      showCount: true,
      tab: 'unassigned'
    }, {
      title: 'В роботі',
      type: tasks.filterTypes.selfAssigned,
      count: 0,
      showCount: true,
      tab: 'selfAssigned'
    }, {
      title: 'Мій розклад',
      type: tasks.filterTypes.tickets,
      count: 0,
      showCount: true,
      tab: 'tickets'
    }, {
      title: 'Усі',
      type: tasks.filterTypes.all,
      count: 0,
      showCount: false,
      tab: 'all'
    }, {
      title: 'Історія',
      type: tasks.filterTypes.finished,
      count: 0,
      showCount: false,
      tab: 'finished'
    }];

    service.instrumentsMenus = [
      {state: 'tools.users', title: 'Користувачі'},
      {state: 'tools.groups', title: 'Групи'},
      {state: 'tools.escalations', title: 'Ескалації'},
      {state: 'tools.reports', title: 'Звіт'},
      {state: 'tools.services', title: 'Розклад'},
      {state: 'tools.deploy', title: 'Розгортання'}
    ];

    return service;

    function getCurrentTab() {
      var path = $location.path();
      if (path.indexOf('/tasks') === 0) {
        service.areInstrumentsVisible = false;
        var matches = path.match(/^\/tasks\/(\w+)(\/\d+)?$/);
        if (matches)
          service.currentTab = matches[1];
        else
          service.currentTab = 'tickets';
      }
      else {
        if(path.indexOf('/profile') === 0){
          service.areInstrumentsVisible = false;
        } else {
          service.areInstrumentsVisible = true;
        }
        service.currentTab = path;
      }
    }

    function load() {
      service.loadTaskCounters();
      service.getCurrentTab();
    }

    function loadTaskCounters() {
      _.each(service.menus, function (menu) {
        if (menu.showCount) {
          tasks.list(menu.type)
              .then(function(result) {
                try {
                  result = JSON.parse(result);
                } catch (e) {
                  result = result;
                }
                menu.count = result.total;
              });
        }
      });
    }
  }
})();
