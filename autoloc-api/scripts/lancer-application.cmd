@echo off
rem Demarre l'API sur http://localhost:8080 avec le profil dev (donnees de demonstration)
rem Mot de passe MySQL : variable DB_PASSWORD (vide par defaut, comme le root de XAMPP)
cd /d "%~dp0.."
if not exist logs mkdir logs
call scripts\preparer-maven.cmd
echo Demarrage d'AutoLoc avec le profil dev... journal : logs\app.log
echo Pour arreter : Ctrl+C dans ce terminal, ou Terminal ^> Terminer la tache dans VS Code.
call mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev > logs\app.log 2>&1
