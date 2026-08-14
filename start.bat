@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
set "MVN=%USERPROFILE%\.local\apache-maven-3.9.16\bin\mvn.cmd"

echo Starting FM AI Assistent...
echo JAVA_HOME=%JAVA_HOME%
echo Open http://127.0.0.1:8080 when the app is up.
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Java 25 was not found at:
  echo   %JAVA_HOME%
  echo Install Temurin JDK 25 or set JAVA_HOME, then try again.
  goto :fail
)

if not exist "%MVN%" (
  echo Maven was not found at:
  echo   %MVN%
  echo Install Maven 3.9+ and update this script, or add mvn.cmd to PATH.
  where mvn.cmd >nul 2>&1
  if errorlevel 1 goto :fail
  set "MVN=mvn.cmd"
)

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":8080 .*LISTENING"') do (
  echo Port 8080 is already in use by PID %%P. Stopping it so this start can continue.
  taskkill /PID %%P /F >nul 2>&1
)
timeout /t 2 /nobreak >nul

call "%MVN%" -DskipTests spring-boot:run
if errorlevel 1 goto :fail
goto :eof

:fail
echo.
echo Start failed. Read the error above, then press a key to close this window.
pause
exit /b 1
