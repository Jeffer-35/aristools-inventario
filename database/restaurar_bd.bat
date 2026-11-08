@echo off
REM ====================================================================
REM  Ferretería Aristools - Restauración de la base de datos (RNF-007)
REM  Restaura un respaldo generado con respaldar_bd.bat.
REM  ATENCION: reemplaza la base "aristools" actual por la del respaldo.
REM
REM  Busca solo la carpeta "bin" de MySQL Server (sirve para 8.x y 9.x).
REM  Si MySQL esta en otra ruta, escribala en MYSQL_BIN. USUARIO es su
REM  usuario de MySQL.
REM ====================================================================
setlocal
set "MYSQL_BIN="
REM Detecta la version instalada de MySQL Server (8.0, 8.4, 9.x...)
for /d %%D in ("C:\Program Files\MySQL\MySQL Server *") do (
    if exist "%%~D\bin\mysql.exe" set "MYSQL_BIN=%%~D\bin"
)
set "USUARIO=root"

if not exist "%MYSQL_BIN%\mysql.exe" (
    echo No se encontro mysql.exe de MySQL Server.
    echo Instale MySQL o escriba la ruta de su carpeta "bin" en MYSQL_BIN.
    pause
    exit /b 1
)

echo Respaldos disponibles en la carpeta "respaldos":
dir /b "%~dp0respaldos\*.sql" 2>nul
echo.
set /p "ARCHIVO=Escriba el nombre del archivo a restaurar: "
if not exist "%~dp0respaldos\%ARCHIVO%" (
    echo ERROR: no existe el archivo "%ARCHIVO%" en la carpeta respaldos.
    pause
    exit /b 1
)

echo.
echo Se va a REEMPLAZAR la base aristools por el contenido de %ARCHIVO%.
set /p "CONFIRMA=Desea continuar? (S/N): "
if /i not "%CONFIRMA%"=="S" (
    echo Restauracion cancelada.
    pause
    exit /b 0
)

echo Escriba la contrasena de MySQL cuando se la pida.
"%MYSQL_BIN%\mysql.exe" -u %USUARIO% -p --default-character-set=utf8mb4 < "%~dp0respaldos\%ARCHIVO%"

if errorlevel 1 (
    echo ERROR: la restauracion no se completo.
) else (
    echo Restauracion completada. Abra la aplicacion y verifique los datos.
)
pause
endlocal
