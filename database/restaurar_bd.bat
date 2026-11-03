@echo off
REM ====================================================================
REM  Ferretería Aristools - Restauración de la base de datos (RNF-007)
REM  Restaura un respaldo generado con respaldar_bd.bat.
REM  ATENCION: reemplaza la base "aristools" actual por la del respaldo.
REM
REM  Antes de usarlo, revise que MYSQL_BIN apunte a la carpeta "bin" de
REM  su instalación de MySQL y que USUARIO sea su usuario de MySQL.
REM ====================================================================
setlocal
set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin"
set "USUARIO=root"

if not exist "%MYSQL_BIN%\mysql.exe" (
    echo No se encontro mysql.exe en "%MYSQL_BIN%".
    echo Corrija la variable MYSQL_BIN en este archivo.
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
