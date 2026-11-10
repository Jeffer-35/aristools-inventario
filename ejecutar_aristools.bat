@echo off
REM Abre Aristools sin ventana de consola. Debe estar en la misma carpeta
REM que Aristools.jar y db.properties.
cd /d "%~dp0"
start "" javaw -jar Aristools.jar