<#
.SYNOPSIS
  Nạp / gỡ dữ liệu demo cho EduERP (chạy psql bên trong container Postgres).

.EXAMPLE
  # Nạp dữ liệu demo (tự sao lưu DB trước), mọi tài khoản demo nhận mail về hộp thư này:
  powershell -ExecutionPolicy Bypass -File scripts\demo-data\demo-data.ps1 -Action seed -Mailbox ten.cua.ban@gmail.com

  # Gỡ sạch dữ liệu demo:
  powershell -ExecutionPolicy Bypass -File scripts\demo-data\demo-data.ps1 -Action cleanup

  # Khôi phục bản sao lưu:
  powershell -ExecutionPolicy Bypass -File scripts\demo-data\demo-data.ps1 -Action restore -BackupFile scripts\demo-data\backups\eduerp-20261030-101500.dump
#>
param(
    [ValidateSet("seed", "cleanup", "backup", "restore")]
    [string]$Action = "seed",
    [string]$Mailbox = "",
    [string]$Container = "agent-postgres",
    [string]$Database = "eduerp",
    [string]$DbUser = "agent",
    [string]$BackupFile = ""
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$backupDir = Join-Path $here "backups"
$remoteDir = "/tmp/eduerp-demo"

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker $($Arguments -join ' ') thất bại (mã $LASTEXITCODE)" }
}

function Backup-Database {
    New-Item -ItemType Directory -Force $backupDir | Out-Null
    $name = "eduerp-$(Get-Date -Format 'yyyyMMdd-HHmmss').dump"
    Invoke-Docker @("exec", $Container, "pg_dump", "-U", $DbUser, "-d", $Database, "-Fc", "-f", "/tmp/$name")
    Invoke-Docker @("cp", "${Container}:/tmp/$name", (Join-Path $backupDir $name))
    Invoke-Docker @("exec", $Container, "rm", "-f", "/tmp/$name")
    Write-Host "Đã sao lưu DB: $(Join-Path $backupDir $name)"
}

function Copy-Scripts {
    Invoke-Docker @("exec", $Container, "rm", "-rf", $remoteDir)
    Invoke-Docker @("exec", $Container, "mkdir", "-p", $remoteDir)
    foreach ($file in @("cleanup-demo.sql", "seed-demo.sql", "run-seed.sql", "run-cleanup.sql")) {
        Invoke-Docker @("cp", (Join-Path $here $file), "${Container}:$remoteDir/$file")
    }
}

switch ($Action) {
    "backup" { Backup-Database }
    "seed" {
        if ($Mailbox -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
            throw "Cần -Mailbox là một hộp thư thật, ví dụ -Mailbox ten.cua.ban@gmail.com"
        }
        Backup-Database
        Copy-Scripts
        Invoke-Docker @("exec", $Container, "psql", "-U", $DbUser, "-d", $Database, "-v", "mailbox=$Mailbox",
            "-f", "$remoteDir/run-seed.sql")
        $local = ($Mailbox.Split("@")[0]).Split("+")[0].ToLower()
        $domain = $Mailbox.Split("@")[1].ToLower()
        Write-Host ""
        Write-Host "Xong. Đăng nhập thử (mật khẩu chung: Demo@2026):"
        Write-Host "  Giáo viên: $local+gv01@$domain ... $local+gv12@$domain"
        Write-Host "  Học viên : $local+hv001@$domain ... $local+hv150@$domain"
    }
    "cleanup" {
        Copy-Scripts
        Invoke-Docker @("exec", $Container, "psql", "-U", $DbUser, "-d", $Database, "-f", "$remoteDir/run-cleanup.sql")
    }
    "restore" {
        if (-not (Test-Path $BackupFile)) { throw "Không thấy file sao lưu: $BackupFile" }
        $name = Split-Path -Leaf $BackupFile
        Invoke-Docker @("cp", $BackupFile, "${Container}:/tmp/$name")
        Invoke-Docker @("exec", $Container, "pg_restore", "-U", $DbUser, "-d", $Database, "--clean", "--if-exists",
            "--single-transaction", "/tmp/$name")
        Write-Host "Đã khôi phục từ $BackupFile. Nên khởi động lại backend (make compose-restart)."
    }
}
