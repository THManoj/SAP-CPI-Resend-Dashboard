@echo off
echo ===================================================
echo Starting SAP CI Message Monitor - Streamlit Frontend
echo ===================================================
cd /d "%~dp0"
if exist ".venv\Scripts\activate.bat" (
    call .venv\Scripts\activate.bat
) else (
    echo Virtual environment not found, using global python...
)
echo Launching Streamlit dashboard on http://localhost:8501 ...
python -m streamlit run frontend\streamlit_app.py --server.port 8501
pause
