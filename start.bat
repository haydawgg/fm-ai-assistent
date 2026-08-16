@echo off
setlocal EnableExtensions
cd /d "%~dp0"

if not exist "%JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME="
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*") do (
    if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
  )
  if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-25\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-25"
  )
)
set "MVN=mvn.cmd"
where mvn.cmd >nul 2>&1
if errorlevel 1 (
  set "MVN=%USERPROFILE%\.local\apache-maven-3.9.16\bin\mvn.cmd"
  if not exist "%MVN%" (
    for /d %%D in ("%USERPROFILE%\.local\apache-maven-*") do (
      if exist "%%D\bin\mvn.cmd" set "MVN=%%D\bin\mvn.cmd"
    )
  )
)

echo Starting FM AI Assistent...
echo JAVA_HOME=%JAVA_HOME%
echo Open http://127.0.0.1:8080 when the app is up.
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Java 25 was not found.
  echo Install Eclipse Temurin JDK 25, then try again.
  echo Expected locations:
  echo   C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot
  echo   C:\Program Files\Java\jdk-25
  goto :fail
)

if not exist "%MVN%" (
  echo Maven was not found.
  echo Install Maven 3.9+ and add mvn.cmd to PATH, or place it under
  echo   %USERPROFILE%\.local\apache-maven-<version>\bin\
  goto :fail
)

netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo Port 8080 is already in use. Stop the process using it, or change
  echo server.port in application.properties, then try again.
  goto :fail
)

call "%MVN%" -DskipTests -Dspring-boot.run.jvmArguments="-Xms256m -Xmx2g --enable-native-access=ALL-UNNAMED" spring-boot:run
if errorlevel 1 goto :fail
goto :eof

:fail
echo.
echo Start failed. Read the error above, then press a key to close this window.
pause
exit /b 1
