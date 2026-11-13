@echo off
REM Gera o APK sem abrir o Android Studio.
REM Usa o Java e o Android SDK que o Android Studio ja instalou no computador.
cd /d "%~dp0"
set /p URL=Endereco do servidor (ex.: https://fontenele-servidor.onrender.com/): 
if "%JAVA_HOME%"=="" if exist "C:\Program Files\Android\Android Studio\jbr" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
echo.
echo Gerando o APK (a primeira vez demora alguns minutos)...
call gradlew.bat assembleDebug -PurlServidor="%URL%"
if errorlevel 1 (
  echo.
  echo Deu erro. Veja a mensagem acima.
  pause
  exit /b 1
)
copy /y "app\build\outputs\apk\debug\app-debug.apk" "Fontenele.apk" >nul
echo.
echo Pronto! O arquivo Fontenele.apk esta nesta pasta. Mande para os celulares e instale.
pause
