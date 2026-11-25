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

Write-Host "OK : PCMon demarrera automatiquement a chaque ouverture de session (fenetre cachee)."
Write-Host "Pour retirer : double-clic sur uninstall-autostart.bat"
Write-Host "Note : autorise python.exe dans le pare-feu Windows (reseau prive) au premier lancement."
