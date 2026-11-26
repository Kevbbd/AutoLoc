@echo off
rem Rejoue la matrice 200 / 401 / 403 du TP Spring Security avec curl
cd /d "%~dp0.."
if not exist logs mkdir logs
call :run > logs\securite.log 2>&1
type logs\securite.log
if /i not "%~1"=="nopause" pause
exit /b 0

:run
echo Matrice d'acces AutoLoc (application attendue sur http://localhost:8080)
echo.
call :check anonyme ""                  /api/public/ping     200
call :check student "student:student123" /api/public/ping     200
call :check admin   "admin:admin123"     /api/public/ping     200
call :check anonyme ""                  /api/hello           401
call :check student "student:student123" /api/hello           200
call :check admin   "admin:admin123"     /api/hello           200
call :check anonyme ""                  /api/admin/report    401
call :check student "student:student123" /api/admin/report    403
call :check admin   "admin:admin123"     /api/admin/report    200
call :check anonyme ""                  /actuator/health     200
call :check anonyme ""                  /actuator/prometheus 200
call :check anonyme ""                  /                    200
call :check anonyme ""                  /api/public/vehicules 200
call :check anonyme ""                  /admin.html          200
call :check anonyme ""                  /api/admin/reservations 401
call :check student "student:student123" /api/admin/reservations 403
call :check admin   "admin:admin123"     /api/admin/reservations 200
call :check anonyme ""                  /reinitialisation.html 200
call :check anonyme ""                  /inscription.html    200
call :check anonyme ""                  /swagger-ui/index.html 200
call :check anonyme ""                  /v3/api-docs         200
call :check anonyme ""                  /api/client/profil   401
call :check student "student:student123" /api/client/profil   403
call :check admin   "admin:admin123"     /api/client/profil   403
call :check admin   "admin:mauvais"      /api/hello           401
echo.
echo En-tetes de protection (page d'accueil)
call :entete "Content-Security-Policy: default-src 'self'"
call :entete "X-Content-Type-Options: nosniff"
call :entete "X-Frame-Options: DENY"
call :entete "Referrer-Policy: same-origin"
call :entete "Permissions-Policy:"
exit /b 0

:entete
set "VERDICT=ECHEC"
curl -s -D - -o NUL http://localhost:8080/ | findstr /i /c:"%~1" > NUL && set "VERDICT=OK"
echo %~1   %VERDICT%
exit /b 0

:check
set "CODE=000"
if "%~2"=="" goto :anonyme
for /f %%s in ('curl -s -o NUL -w "%%{http_code}" -u %~2 http://localhost:8080%~3') do set "CODE=%%s"
goto :verdict
:anonyme
for /f %%s in ('curl -s -o NUL -w "%%{http_code}" http://localhost:8080%~3') do set "CODE=%%s"
:verdict
set "VERDICT=ECHEC"
if "%CODE%"=="%~4" set "VERDICT=OK"
echo GET %~3   en tant que %~1   attendu %~4   obtenu %CODE%   %VERDICT%
exit /b 0
