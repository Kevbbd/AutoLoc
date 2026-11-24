@echo off
rem Cree le depot Git "AutoLoc" (dossier parent d'autoloc-api) avec un commit par partie du TP, plus la vitrine.
rem Ne pousse rien : l'envoi vers GitHub se fait avec PUBLIER-SUR-GITHUB.cmd.
rem Argument facultatif "nopause" : pas de pause a la fin (appel depuis un autre script).
cd /d "%~dp0..\.."
set "CODE=1"
title AutoLoc - creation du depot Git

where git > NUL 2>&1
if errorlevel 1 (
  echo Git est introuvable sur ce PC.
  goto :fin
)
if exist .git (
  echo Un depot Git existe deja dans "%CD%" : rien a creer.
  set "CODE=0"
  goto :fin
)

rem Identite des commits : celle de Git si elle est connue, sinon demandee ici (enregistree dans ce depot seulement)
set "GITNAME="
set "GITMAIL="
for /f "delims=" %%v in ('git config user.name') do set "GITNAME=%%v"
for /f "delims=" %%v in ('git config user.email') do set "GITMAIL=%%v"
if not defined GITNAME if defined CFG_NOM set "GITNAME=%CFG_NOM%"
if not defined GITMAIL if defined CFG_EMAIL set "GITMAIL=%CFG_EMAIL%"
if not defined GITNAME set /p "GITNAME=Votre nom pour les commits (ex. : Kevin Bebada) : "
if not defined GITMAIL set /p "GITMAIL=Votre e-mail GitHub : "
if not defined GITNAME goto :identite
if not defined GITMAIL goto :identite


git init -q -b main
git config user.name "%GITNAME%"
git config user.email "%GITMAIL%"
git add .gitignore

git add autoloc-api/pom.xml autoloc-api/mvnw autoloc-api/mvnw.cmd autoloc-api/.mvn autoloc-api/.gitignore autoloc-api/.gitattributes autoloc-api/README.md autoloc-api/src/main/resources/application.properties autoloc-api/src/main/resources/application-dev.properties autoloc-api/src/main/java/fr/ece/autoloc/AutolocApiApplication.java autoloc-api/src/main/java/fr/ece/autoloc/domain autoloc-api/src/main/java/fr/ece/autoloc/repository autoloc-api/src/main/java/fr/ece/autoloc/service/package-info.java autoloc-api/src/main/java/fr/ece/autoloc/web/dto/package-info.java autoloc-api/src/main/java/fr/ece/autoloc/config/DemoDataInitializer.java autoloc-api/src/test/java/fr/ece/autoloc/domain autoloc-api/src/test/java/fr/ece/autoloc/repository autoloc-api/src/test/resources autoloc-api/docs/fetch-strategy.md
git commit -q -m "Implement AutoLoc domain model with JPA associations, cascade and fetch"

git add autoloc-api/src/main/java/fr/ece/autoloc/config/SecurityConfig.java autoloc-api/src/main/java/fr/ece/autoloc/web/controller/SecurityDemoController.java autoloc-api/src/test/java/fr/ece/autoloc/config autoloc-api/docs/security-notes.md autoloc-api/docs/postman
git commit -q -m "Add Spring Security authentication and authorization"

git add autoloc-api/docker-compose.yml autoloc-api/monitoring autoloc-api/docs/monitoring-notes.md
git commit -q -m "Add Prometheus and Grafana monitoring"

git add autoloc-api/LANCER-TOUT.cmd autoloc-api/scripts
git commit -q -m "Add Windows scripts to build, run and verify AutoLoc"

rem Dernier commit : tout le reste (vitrine, reservation en ligne, back-office), fichiers ignores exclus.
git add autoloc-api
git commit -q -m "Add showcase website with online reservation, client accounts, payments and back-office"

rem Presentation du projet pour tous les lecteurs : README a la racine et ses captures d'ecran
git add README.md captures
git commit -q -m "Add illustrated README for readers"

git rev-parse --verify -q HEAD > NUL
if errorlevel 1 (
  echo ERREUR : les commits n'ont pas pu etre crees.
  goto :fin
)
set "CODE=0"
echo.
echo Depot cree dans "%CD%" :
git log --oneline
echo.
echo Fichiers non suivis restants (normalement aucun) :
git status --short
echo.
echo Pour l'envoyer sur GitHub : double-cliquez sur PUBLIER-SUR-GITHUB.cmd
goto :fin

:identite
echo Il faut un nom et un e-mail pour creer les commits. Relancez ce script.

:fin
echo.
if /i not "%~1"=="nopause" pause
exit /b %CODE%
