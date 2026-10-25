@echo off
rem Lance la version PC de Triple Triad (utilise le gradlew du dossier parent TripleTriad\)
cd /d "%~dp0"
call ..\gradlew.bat run
