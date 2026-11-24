@echo off
rem Compile le projet et lance tous les tests (base H2 en memoire, MySQL inutile)
cd /d "%~dp0.."
if not exist logs mkdir logs
call scripts\preparer-maven.cmd
echo Compilation et tests en cours (journal complet : logs\tests.log)...
call mvnw.cmd -B -ntp clean test > logs\tests.log 2>&1
set RESULTAT=%ERRORLEVEL%
type logs\tests.log
echo.
echo Code de sortie Maven : %RESULTAT%
if /i not "%~1"=="nopause" pause
exit /b %RESULTAT%
