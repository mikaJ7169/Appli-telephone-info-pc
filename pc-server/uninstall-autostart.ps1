$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "PCMon Server.lnk"
if (Test-Path $lnk) {
    Remove-Item $lnk -Force
    Write-Host "Raccourci de demarrage supprime."
} else {
    Write-Host "Aucun raccourci de demarrage trouve."
}

$vbs = Join-Path $PSScriptRoot "pcmon-launch.vbs"
if (Test-Path $vbs) { Remove-Item $vbs -Force }

Get-CimInstance Win32_Process -Filter "CommandLine like '%pc-server%server.py%'" -ErrorAction SilentlyContinue |
    ForEach-Object { $_ | Invoke-CimMethod -MethodName Terminate | Out-Null }
Write-Host "Serveur arrete s'il tournait."
