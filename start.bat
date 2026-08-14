@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
set "MVN=%USERPROFILE%\.local\apache-maven-3.9.16\bin\mvn.cmd"
if not exist "%MVN%" set "MVN=mvn.cmd"

echo Starting FM AI Assistent...
echo JAVA_HOME=%JAVA_HOME%
echo Open http://127.0.0.1:8080 when the app is up.
echo.

"%MVN%" spring-boot:run
if errorlevel 1 (
  echo.
  echo Start failed. Close this window after reading the error.
  pause
)
endlocal
