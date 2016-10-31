(function () {
  'use strict';

  angular
    .module('dashboardJsApp')
    .controller('NavbarCtrl', navbarCtrl);

  navbarCtrl.$inject = ['$scope', '$location', 'Auth', 'envConfigService', 'iGovNavbarHelper', 'tasksSearchService', '$state', 'tasks'];
  function navbarCtrl($scope, $location, Auth, envConfigService, iGovNavbarHelper, tasksSearchService, $state, tasks) {
    $scope.menu = [{
      'title': 'Задачі',
      'link': '/tasks'
    }];

    envConfigService.loadConfig(function (config) {
      iGovNavbarHelper.isTest = config.bTest;
    });

    $scope.isAdmin = Auth.isAdmin;
    $scope.areInstrumentsVisible = false;
    $scope.iGovNavbarHelper = iGovNavbarHelper;

    $scope.isVisible = function(menuType){
      //$scope.menus = [{
      if(menuType === 'all'){
        return Auth.isLoggedIn() && Auth.hasOneOfRoles('manager', 'admin', 'kermit');
      }
      if(menuType === 'finished'){
        return Auth.isLoggedIn() && Auth.hasOneOfRoles('manager', 'admin', 'kermit', 'supervisor');
      }
      return Auth.isLoggedIn();
    };

    $scope.isVisibleInstrument = function(menuType){
      if(menuType === 'tools.users'){
        return Auth.isLoggedIn() && Auth.hasOneOfRoles('superadmin');
      }
      if(menuType === 'tools.groups'){
        return Auth.isLoggedIn() && Auth.hasOneOfRoles('superadmin');
      }
      return Auth.isLoggedIn();
    };

    $scope.getCurrentUserName = function() {
      var user = Auth.getCurrentUser();
      return user.firstName + ' ' + user.lastName;
    };

    $scope.goToServices = function() {
      $location.path('/services');
    };

    $scope.goToEscalations = function() {
      $location.path('/escalations');
    };

    $scope.goToReports = function () {
      $location.path('/reports');
    };

    $scope.logout = function() {
      Auth.logout();
      $location.path('/login');
    };

    $scope.isActive = function(route) {
      return route === $location.path();
    };

    $scope.goToUsers = function () {
      $location.path('/users');
    };

    $scope.goToGroups = function () {
      $location.path('/groups');
    };

    $scope.goToDeploy = function () {
      $location.path('/deploy');
    };

    $scope.goToProfile = function () {
      $location.path('/profile');
    };

    $scope.tasksSearch = iGovNavbarHelper.tasksSearch;

    $scope.searchInputKeyup = function ($event) {
      if ($event.keyCode === 13 && $scope.tasksSearch.value) {
        $scope.tasksSearch.loading=true;
        $scope.tasksSearch.count=0;
        $scope.tasksSearch.submited=true;
        if($scope.tasksSearch.archive) {
          tasks.getProcesses($scope.tasksSearch.value).then(function (res) {
            var response = JSON.parse(res);
            $scope.archive = response[0];
            $scope.switchArchive = true;
          })
        } else {
          tasksSearchService.searchTaskByUserInput($scope.tasksSearch.value, $scope.tasksSearch.archive)
            .then(function(res, aIds) {
              // $scope.tasksSearch.count = aIds.length;
              $scope.tasksSearch.count = res.length;
            })
            .finally(function(res) {
              $scope.tasksSearch.loading=false;
           });
        }
      }
      if($event.keyCode === 8 || $event.keyCode === 46) {
        $scope.switchArchive = false;
      }
    };

    $scope.closeArchive = function () {
      $scope.switchArchive = false;
    };

    $scope.archiveTextValue = function () {
      return isNaN($scope.tasksSearch.value);
    };

    $scope.isSelectedInstrumentsMenu = function(menuItem) {
      return menuItem.state==$state.current.name;
    };
  }
})();
