@echo off
echo Starting Vikify...
echo.

REM Start backend in a new window
start "Vikify Backend" cmd /c "cd backend && python server.py"

REM Wait a moment for backend to start
timeout /t 2 /nobreak > nul

REM Start frontend
echo Starting frontend...
npm run dev
