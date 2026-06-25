$env:JAVA_HOME = "D:\develop\jdk17"
$env:Path = "D:\develop\jdk17\bin;D:\develop\apache-maven-3.9.4\bin;D:\develop\node;$env:Path"

Write-Host "Temporary development environment configured for this PowerShell window."
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host ""
Write-Host "Use npm.cmd instead of npm in PowerShell if npm.ps1 is blocked."

