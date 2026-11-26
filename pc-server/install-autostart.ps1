$dir = $PSScriptRoot
$bat = Join-Path $dir "start-server.bat"
$vbs = Join-Path $dir "pcmon-launch.vbs"

$vbsContent = 'CreateObject("Wscript.Shell").Run "cmd /c ""' + $bat + '""", 0, False'
Set-Content -Path $vbs -Value $vbsContent -Encoding ASCII

$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "PCMon Server.lnk"
$ws = New-Object -ComObject WScript.Shell
$s = $ws.CreateShortcut($lnk)
$s.TargetPath = "wscript.exe"
$s.Arguments = '"' + $vbs + '"'
$s.WorkingDirectory = $dir
$s.Description = "PCMon server (demarrage auto)"
$s.Save()

try {
    New-NetFirewallRule -DisplayName "PCMon 8765 TCP" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -ErrorAction Stop | Out-Null
    New-NetFirewallRule -DisplayName "PCMon 8766 UDP" -Direction Inbound -Protocol UDP -LocalPort 8766 -Action Allow -ErrorAction Stop | Out-Null
    Write-Host "Regles pare-feu ajoutees : 8765/TCP et 8766/UDP (tous profils)."
} catch {
    Write-Host "REGLES PARE-FEU NON AJOUTEES (droits admin requis)."
    Write-Host "Relance install-autostart.bat EN TANT QU'ADMINISTRATEUR, ou autorise python.exe manuellement dans le pare-feu (reseau prive + public)."
}

Write-Host "OK : PCMon demarrera automatiquement a chaque ouverture de session (fenetre cachee)."
Write-Host "Pour retirer : double-clic sur uninstall-autostart.bat"
