@echo off
echo Gerando os icones do INNAV a partir da imagem enviada...
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File "%~dp0generate_icons.ps1"
echo Processo concluido com sucesso!
pause
