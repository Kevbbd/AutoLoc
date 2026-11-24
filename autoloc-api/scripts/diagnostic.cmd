@echo off
rem Verifie les outils necessaires (Java, Docker, Git, MySQL XAMPP, curl)
cd /d "%~dp0.."
if not exist logs mkdir logs
call :run > logs\diagnostic.log 2>&1
type logs\diagnostic.log
if /i not "%~1"=="nopause" pause
exit /b 0

:run
echo === Java
where java
java -version
echo JAVA_HOME="%JAVA_HOME%"
echo.
echo === Docker
docker version --format "client {{.Client.Version}} / moteur {{.Server.Version}}"
docker compose version
echo.
echo === Git
where git
git --version
echo.
echo === MySQL (XAMPP)
"C:\xampp\mysql\bin\mysql.exe" -u root -e "SELECT VERSION();"
echo.
echo === curl
curl --version
exit /b 0
