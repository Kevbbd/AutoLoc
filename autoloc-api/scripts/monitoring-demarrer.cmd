@echo off
rem Demarre Prometheus, Grafana et Mailpit (Docker), verifie la collecte et genere du trafic pour les graphiques
cd /d "%~dp0.."
if not exist logs mkdir logs
call :run > logs\monitoring.log 2>&1
type logs\monitoring.log
if /i not "%~1"=="nopause" pause
exit /b 0

:run
echo === docker compose up -d
docker compose up -d
docker compose ps
echo.
echo Attente de la premiere collecte Prometheus (20 s)...
ping -n 21 127.0.0.1 > NUL
echo === Cibles Prometheus
curl -s http://localhost:9090/api/v1/targets
echo.
echo.
echo === Sante Grafana
curl -s http://localhost:3000/api/health
echo.
echo === Tableau de bord provisionne
curl -s -u admin:admin "http://localhost:3000/api/search?query=AutoLoc"
echo.
echo.
echo === Mailpit (boite de reception de test)
curl -s http://localhost:8025/api/v1/info
echo.
echo.
echo === Generation de trafic (60 requetes : 200, 401 et 403)
for /l %%i in (1,1,20) do (
  curl -s -o NUL http://localhost:8080/api/public/ping
  curl -s -o NUL http://localhost:8080/api/hello
  curl -s -o NUL -u student:student123 http://localhost:8080/api/admin/report
)
echo Trafic envoye.
echo.
echo Prometheus : http://localhost:9090/targets
echo Grafana    : http://localhost:3000  (Dashboards ^> AutoLoc)
echo Mailpit    : http://localhost:8025  (e-mails de l'application)
exit /b 0
