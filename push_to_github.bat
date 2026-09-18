@echo off
echo ===================================================
echo Pushing SAP-CPI-Resend-Dashboard to GitHub
echo ===================================================
cd /d "%~dp0"
"C:\Program Files\Git\cmd\git.exe" push -u origin main
echo ===================================================
echo Done!
pause
