@echo off
echo ===================================================
echo Starting SAP CI Message Monitor - Spring Boot Backend
echo ===================================================
cd /d "%~dp0backend"
if not exist "target\sap-ci-monitor-0.0.1-SNAPSHOT.jar" (
    echo Building backend jar with Maven...
    call mvn clean package -DskipTests
)
echo Starting server on http://localhost:8081 ...
java -jar target\sap-ci-monitor-0.0.1-SNAPSHOT.jar
pause
