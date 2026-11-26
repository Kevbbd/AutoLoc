@echo off
rem ==========================================================================
rem  AutoLoc : verification complete en un double-clic
rem   1. diagnostic des outils    4. controle de la base MySQL (XAMPP)
rem   2. compilation + tests      5. matrice de securite 200 / 401 / 403
rem   3. demarrage de l'API       6. Prometheus + Grafana (si Docker tourne)
rem  Les journaux sont ecrits dans le dossier logs\
rem ==========================================================================
cd /d "%~dp0"
if not exist logs mkdir logs
title AutoLoc - verification complete

rem Une ancienne version d'AutoLoc tourne encore ? On l'arrete pour la remplacer.
set "CODE=000"
for /f %%s in ('curl -s -o NUL -w "%%{http_code}" http://localhost:8080/api/public/ping') do set "CODE=%%s"
if "%CODE%"=="000" goto :port_libre
curl -s http://localhost:8080/api/public/ping | findstr /c:"Public endpoint is reachable" > NUL
if errorlevel 1 (
  echo  Le port 8080 est occupe par une autre application : liberez-le puis relancez ce script.
  goto :fin
)
echo [0/6] Arret de l'ancienne version d'AutoLoc...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:"0.0.0.0:8080 "') do taskkill /PID %%p /T /F > NUL 2>&1
ping -n 4 127.0.0.1 > NUL
:port_libre

echo [1/6] Diagnostic des outils...
call scripts\diagnostic.cmd nopause > NUL
where java > NUL 2>&1
if errorlevel 1 if "%JAVA_HOME%"=="" (
  echo.
  echo  ERREUR : Java est introuvable. Installez un JDK 17 ou plus, puis relancez.
  goto :fin
)

echo [2/6] Compilation et tests (premiere fois : telechargement des dependances, plusieurs minutes)...
call scripts\tests.cmd nopause > NUL
if errorlevel 1 (
  echo.
  echo  ERREUR : la compilation ou les tests ont echoue. Voir logs\tests.log
  goto :fin
)
echo       Tests OK.

netstat -ano | findstr /r /c:":3306 .*:0 " > NUL
if errorlevel 1 (
  echo.
  echo  ERREUR : MySQL ne tourne pas. Cliquez sur Start a cote de MySQL dans le panneau XAMPP, puis relancez.
  goto :fin
)

echo [3/6] Demarrage de l'API (fenetre "AutoLoc API" minimisee, journal logs\app.log)...
start "AutoLoc API" /min cmd /c scripts\lancer-application.cmd
set /a ESSAIS=0
:attente
set /a ESSAIS+=1
set "CODE=000"
for /f %%s in ('curl -s -o NUL -w "%%{http_code}" http://localhost:8080/actuator/health') do set "CODE=%%s"
if "%CODE%"=="200" goto :demarree
if %ESSAIS% GEQ 100 (
  echo.
  echo  ERREUR : l'API n'a pas demarre en 5 minutes. Voir logs\app.log
  goto :fin
)
ping -n 4 127.0.0.1 > NUL
goto :attente
:demarree
echo       API demarree sur http://localhost:8080

echo [4/6] Controle de la base MySQL...
call scripts\verifier-base.cmd nopause > NUL

echo [5/6] Matrice de securite...
call scripts\verifier-securite.cmd nopause > NUL
type logs\securite.log

echo [6/6] Monitoring...
docker info > NUL 2>&1
if errorlevel 1 (
  echo       Docker Desktop n'est pas demarre : etape ignoree. Lancez Docker Desktop puis scripts\monitoring-demarrer.cmd
) else (
  call scripts\monitoring-demarrer.cmd nopause > NUL
  echo       Prometheus : http://localhost:9090/targets
  echo       Grafana    : http://localhost:3000  ^(admin / admin^)
  echo       Mailpit    : http://localhost:8025  ^(e-mails : mot de passe oublie^)
)

echo.
echo Termine.
echo   Vitrine et reservation : http://localhost:8080
echo   Espace agence          : http://localhost:8080/admin.html  ^(admin / admin123^)
echo   Swagger UI (tests API) : http://localhost:8080/swagger-ui.html  ^(Authorize : student / student123 ou admin / admin123^)
echo   L'API continue de tourner dans la fenetre "AutoLoc API" (fermez-la pour l'arreter).
echo.
echo   Ouverture du site dans le navigateur...
start "" http://localhost:8080
:fin
echo.
pause
