@echo off
setlocal
set DIR=%~dp0
if not defined JAVA_HOME goto findJavaFromPath
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME points to an invalid Java installation: %JAVA_HOME%
exit /b 1
:findJavaFromPath
set JAVA_EXE=java.exe
:execute
"%JAVA_EXE%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
