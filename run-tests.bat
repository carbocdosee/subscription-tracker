@echo off
chcp 65001 > nul

:: Принимает те же флаги: --build, --keep-up
:: Пример: run-tests.bat --build --keep-up

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-tests.ps1" %*

echo.
if %ERRORLEVEL% equ 0 (
    echo [PASS] Все тесты прошли успешно.
) else (
    echo [FAIL] Часть тестов упала. Смотри HTML-отчёт.
)

echo.
pause
