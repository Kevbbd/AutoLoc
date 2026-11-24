@echo off
rem Publie le projet AutoLoc sur GitHub, en un double-clic.
rem   1. cree le depot Git local s'il n'existe pas (scripts\git-initialiser.cmd : 6 commits) ;
rem   2. demande l'adresse du depot GitHub, a creer VIDE au prealable sur https://github.com/new ;
rem   3. envoie le projet. La premiere fois, Git ouvre une fenetre de connexion a GitHub.
rem Les dossiers target et logs ne sont jamais envoyes (.gitignore).
rem Facultatif : scripts\publication.cfg (URL=, NOM=, EMAIL=) evite toute saisie. Il n'est jamais envoye.
cd /d "%~dp0"
title AutoLoc - publication sur GitHub
set "CFG_URL="
set "CFG_NOM="
set "CFG_EMAIL="
if exist scripts\publication.cfg for /f "usebackq eol=# tokens=1,* delims==" %%a in ("scripts\publication.cfg") do set "CFG_%%a=%%b"

where git > NUL 2>&1
if errorlevel 1 goto :sansgit

echo [1/3] Depot Git local...
call scripts\git-initialiser.cmd nopause
if errorlevel 1 goto :fin

cd /d "%~dp0.."
echo.
echo [2/3] Adresse du depot GitHub
echo   Si ce n'est pas deja fait : ouvrez https://github.com/new et creez un depot VIDE
echo   nom conseille : AutoLoc. Ne cochez ni README, ni .gitignore, ni licence.
echo   Public : votre professeur le voit directement. Prive : invitez-le dans Settings, Collaborators.
echo   Copiez ensuite l'adresse du depot, par exemple https://github.com/votre-compte/AutoLoc.git
echo.
set "URL=%CFG_URL%"
if defined URL echo Adresse lue dans scripts\publication.cfg : %URL%
if not defined URL set /p "URL=Collez l'adresse du depot puis Entree : "
if not defined URL goto :sansadresse
echo %URL%| findstr /b /i /c:"https://github.com/" > NUL
if errorlevel 1 goto :adresseinvalide

git remote get-url origin > NUL 2>&1
if errorlevel 1 goto :ajouterorigine
git remote set-url origin "%URL%"
goto :envoyer
:ajouterorigine
git remote add origin "%URL%"

:envoyer
echo.
echo [3/3] Envoi vers GitHub. La premiere fois, une fenetre de connexion GitHub s'ouvre : connectez-vous.
git push -u origin main
if errorlevel 1 goto :echec

set "PAGE=%URL:.git=%"
echo.
echo Termine : le projet est en ligne sur %PAGE%
start "" "%PAGE%"
goto :fin

:sansgit
echo Git est introuvable sur ce PC : installez Git pour Windows, puis relancez.
goto :fin

:sansadresse
echo Aucune adresse saisie : relancez ce script quand le depot GitHub sera cree.
goto :fin

:adresseinvalide
echo Adresse invalide : elle doit commencer par https://github.com/
goto :fin

:echec
echo.
echo ECHEC de l'envoi. Causes frequentes :
echo  - le depot GitHub n'est pas vide, il a ete cree avec un README : supprimez-le et recreez-le vide ;
echo  - l'adresse est mal copiee : relancez ce script et collez-la de nouveau ;
echo  - la connexion a GitHub a ete fermee ou refusee : relancez et connectez-vous.

:fin
echo.
pause
