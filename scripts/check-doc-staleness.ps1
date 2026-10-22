# FocusLock — Documentation Staleness Detector
# Inspects Git status/diff to identify modified source files and maps them to affected Analysis documents.

param (
    [switch]$CheckCommitted = $false
)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  FocusLock Project Intelligence Staleness Check  " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Fetch changed files via Git
$changedFiles = @()

if ($CheckCommitted) {
    # Check changes in the last commit
    $gitOutput = git diff-tree --no-commit-id --name-only -r HEAD 2>$null
    if ($gitOutput) { $changedFiles += $gitOutput }
} else {
    # Check uncommitted working directory changes
    $statusOutput = git status --porcelain 2>$null
    foreach ($line in $statusOutput) {
        if ($line.Length -gt 3) {
            $changedFiles += $line.Substring(3).Trim()
        }
    }
}

if ($changedFiles.Count -eq 0) {
    Write-Host "`nNo modified source files detected. Working tree is clean." -ForegroundColor Green
    exit 0
}

Write-Host "`nModified Files Detected ($($changedFiles.Count)):" -ForegroundColor Yellow
$changedFiles | ForEach-Object { Write-Host "  - $_" -ForegroundColor DarkGray }

# 2. Rule mappings: Source file patterns to affected documentation
$mappingRules = @(
    @{ Pattern = ".*FocusLockAccessibilityService\.kt"; Docs = @("FEATURE_STATUS.md", "DEPENDENCIES.md", "SECURITY_AND_PERMISSIONS.md", "KNOWN_ISSUES.md") },
    @{ Pattern = ".*BlockingActivity\.kt"; Docs = @("FEATURE_STATUS.md", "DEPENDENCIES.md", "FILE_INDEX.md") },
    @{ Pattern = ".*(?:Dao|Entity|Database)\.kt"; Docs = @("DATABASE_SCHEMA.md", "DEPENDENCIES.md", "FILE_INDEX.md") },
    @{ Pattern = ".*AndroidManifest\.xml"; Docs = @("SECURITY_AND_PERMISSIONS.md", "FEATURE_STATUS.md", "PROJECT_OVERVIEW.md") },
    @{ Pattern = ".*(?:build\.gradle|libs\.versions\.toml)"; Docs = @("BUILD_AND_TEST.md", "PROJECT_OVERVIEW.md", "DEPENDENCIES.md") },
    @{ Pattern = ".*QuotaEngine\.kt"; Docs = @("FEATURE_STATUS.md", "ARCHITECTURE.md", "DEPENDENCIES.md") },
    @{ Pattern = ".*FloatingTimer.*"; Docs = @("FEATURE_STATUS.md", "DEPENDENCIES.md", "SECURITY_AND_PERMISSIONS.md") },
    @{ Pattern = ".*BreakReminder.*"; Docs = @("FEATURE_STATUS.md", "DEPENDENCIES.md") },
    @{ Pattern = ".*(?:AppUpdate|ApkSecurity).*"; Docs = @("FEATURE_STATUS.md", "BUILD_AND_TEST.md") },
    @{ Pattern = ".*(?:Screen|ViewModel)\.kt"; Docs = @("FEATURE_STATUS.md", "FILE_INDEX.md") }
)

# 3. Identify potentially stale documentation
$staleDocs = [System.Collections.Generic.HashSet[string]]::new()

foreach ($file in $changedFiles) {
    # Ignore edits directly inside analysis or rules
    if ($file -like "analysis/*" -or $file -like "project-intelligence/*" -or $file -like "*.agent*") {
        continue
    }

    foreach ($rule in $mappingRules) {
        if ($file -match $rule.Pattern) {
            foreach ($doc in $rule.Docs) {
                [void]$staleDocs.Add($doc)
            }
        }
    }
}

Write-Host "`nPotentially Affected Analysis Documents:" -ForegroundColor Cyan
if ($staleDocs.Count -gt 0) {
    foreach ($doc in $staleDocs) {
        Write-Host "  [!] analysis/$doc" -ForegroundColor Magenta
    }
    Write-Host "`nRecommendation: Inspect the files above and verify they match the actual source code." -ForegroundColor Yellow
} else {
    Write-Host "  None. Modified files do not affect core architecture or schema." -ForegroundColor Green
}
Write-Host "==================================================" -ForegroundColor Cyan
