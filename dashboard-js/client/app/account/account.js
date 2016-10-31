'use strict';

var idleProvider, keepaliveProvider;

angular.module('dashboardJsApp')
	.config(function($routeProvider, IdleProvider, KeepaliveProvider) {
		idleProvider = IdleProvider;
		keepaliveProvider = KeepaliveProvider;
	})
	.run(
		function($rootScope, $location, $cookieStore, Auth, Idle, Modal, $state) {
			var isKeepAliveAsked = false;

			$rootScope.$on('Keepalive', function() {
				if (!isKeepAliveAsked) {
					Auth.pingSession();
				}
			});

			var keepAliveModal;
			var startWatching = function() {
				if (Auth.getSessionSettings()) {
					var sessionSettings = Auth.getSessionSettings();
					// configure Idle settings
					idleProvider.idle(sessionSettings.sessionIdle); // in seconds
					idleProvider.timeout(sessionSettings.timeOut | 0); // in seconds
					keepaliveProvider.interval(sessionSettings.interval); // in seconds
					idleProvider.autoResume(false);
				}

				Idle.watch();
			};
			var stopWatchinAndlogout = function() {
				isKeepAliveAsked = false;
				Idle.unwatch();
				Auth.logout();
				$location.path('/');
			};

			$rootScope.$on('IdleWarn', function(e, countdown) {
				if (!isKeepAliveAsked) {
					keepAliveModal = Modal.confirm.keepalive(function(event) {
						startWatching();
					}, function(event) {
						stopWatchinAndlogout();
					})('Сесія буде закрита через ' + countdown + ' секунд');
				}
				isKeepAliveAsked = true;
			});

			$rootScope.$on('IdleTimeout', function() {
				Modal.inform.warning(function(event) {
					if (keepAliveModal) {
						keepAliveModal.dismiss(event);
					}
					stopWatchinAndlogout();
				})('Сесія буде закрита');
			});

      /*
			var lastPage;
			$rootScope.$on('$routeChangeStart', function(event, next) {
				if (next && next.access !== undefined) {
					if (!Auth.isLoggedIn()) {
						event.preventDefault();
						lastPage = next.originalPath;
						$location.path('/login').replace();
					} else {
						startWatching();
						if (lastPage) {
							var lastPageTemp = lastPage;
							$location.path(lastPageTemp);
							lastPage = undefined;
						} else {
							$location.path(next.originalPath);
						}
					}
				}
			});
			*/

      $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams, options) {
        if (toState && toState.access && toState.access.requiresLogin) {
          if (!Auth.isLoggedIn()) {
            event.preventDefault();
            $state.go('main');
          } else {
            startWatching();
          }
        }
      })
		});
