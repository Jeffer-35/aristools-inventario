@echo off
REM ====================================================================
REM  Ferretería Aristools - Respaldo de la base de datos (RNF-007)
REM  Genera un archivo .sql con toda la base "aristools" (estructura y
REM  datos) dentro de la carpeta "respaldos", con la fecha y hora en el
REM  nombre. Ejemplo: respaldos\aristools_20261102_1530.sql
REM
REM  Busca solo la carpeta "bin" de MySQL Server (sirve para 8.x y 9.x).
REM  Si MySQL esta en otra ruta, escribala en MYSQL_BIN. USUARIO es su
REM  usuario de MySQL.
REM ====================================================================
setlocal
set "MYSQL_BIN="
REM Detecta la version instalada de MySQL Server (8.0, 8.4, 9.x...)
for /d %%D in ("C:\Program Files\MySQL\MySQL Server *") do (
    if exist "%%~D\bin\mysqldump.exe" set "MYSQL_BIN=%%~D\bin"
)
set "USUARIO=root"
set "BASE=aristools"
set "CARPETA=%~dp0respaldos"

if not exist "%MYSQL_BIN%\mysqldump.exe" (
    echo No se encontro mysqldump.exe de MySQL Server.
    echo Instale MySQL o escriba la ruta de su carpeta "bin" en MYSQL_BIN.
    pause
    exit /b 1
)
if not exist "%CARPETA%" mkdir "%CARPETA%"

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set "FECHA=%%i"
set "ARCHIVO=%CARPETA%\%BASE%_%FECHA%.sql"

echo Generando respaldo de la base %BASE%...
echo Escriba la contrasena de MySQL cuando se la pida.
"%MYSQL_BIN%\mysqldump.exe" -u %USUARIO% -p --single-transaction --routines --triggers --default-character-set=utf8mb4 --databases %BASE% --result-file="%ARCHIVO%"

if errorlevel 1 (
    echo ERROR: el respaldo no se pudo generar.
) else (
    echo Respaldo generado correctamente en:
    echo %ARCHIVO%
)
pause
endlocal
