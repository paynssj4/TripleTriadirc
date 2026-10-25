@echo off
rem Compile Triple Triad : regle Java/Gradle/SDK puis lance assembleDebug
for /d %%i in (C:\dev\jdk17\*) do set "JAVA_HOME=%%i"
set "ANDROID_HOME=C:\dev\android-sdk"
set "PATH=%JAVA_HOME%\bin;C:\dev\gradle-8.7\bin;%PATH%"
cd /d "%~dp0"
call gradle assembleDebug
if errorlevel 1 (
  echo.
  echo *** ECHEC DE LA COMPILATION ***
  pause
  exit /b 1
)
echo.
echo OK ! APK : %~dp0app\build\outputs\apk\debug\TripleTriad.apk
pause
