# ===================================================================
# Housing Society Billing Portal - Ansible Playbook Runner
# Executes Ansible playbook inside WSL Ubuntu environment
# ===================================================================

param (
    [string]$Playbook = "provision.yml",
    [switch]$CheckMode,
    [switch]$Verbose,
    [string]$ExtraArgs = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " Housing Society Billing Portal - Ansible Automation     "
Write-Host " Controller  : WSL Ubuntu (26.04)"
Write-Host " Playbook    : ansible/$Playbook"
Write-Host " Check Mode  : $CheckMode"
Write-Host "=========================================================="

$cmdArgs = @($Playbook)

if ($CheckMode) {
    $cmdArgs += "--check"
}

if ($Verbose) {
    $cmdArgs += "-v"
}

if ($ExtraArgs) {
    $cmdArgs += $ExtraArgs
}

$argString = $cmdArgs -join " "
$wslCommand = "export ANSIBLE_CONFIG=/mnt/d/Housing-Society-Billing-Portal/ansible/ansible.cfg && cd /mnt/d/Housing-Society-Billing-Portal/ansible && ansible-playbook $argString"

Write-Host "Executing in WSL: $wslCommand" -ForegroundColor Cyan
wsl -d Ubuntu -- bash -c $wslCommand

if ($LASTEXITCODE -ne 0) {
    Write-Error "Ansible playbook execution failed with exit code $LASTEXITCODE"
} else {
    Write-Host "Ansible playbook executed successfully." -ForegroundColor Green
}
