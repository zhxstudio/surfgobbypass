@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%init-signing.ps1"
if errorlevel 1 exit /b 1
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
    echo Downloading Gradle Wrapper...
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -TimeoutSec 120 -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.12.1/gradle/wrapper/gradle-wrapper.jar' -OutFile (Join-Path $env:APP_HOME 'gradle\wrapper\gradle-wrapper.jar')"
    if errorlevel 1 exit /b 1
)
if "%~1"=="" (
    "%JAVA_EXE%" -Djava.net.preferIPv4Stack=true -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain --project-dir "%APP_HOME%." --no-daemon assembleRelease
) else (
    "%JAVA_EXE%" -Djava.net.preferIPv4Stack=true -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain --project-dir "%APP_HOME%." %*
)
exit /b %ERRORLEVEL%
