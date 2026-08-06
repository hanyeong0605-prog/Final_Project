<#
Creates a team-safe copy of locally accumulated OpenAI Luna analysis data.
It maps target job postings by Wanted external_job_id and target skills by
canonical name, so it never copies local numeric primary keys.
#>
[CmdletBinding()]
param(
    [string]$MySqlExe = "mysql",
    [string]$Database = "jobpilot",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "mysql",
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\import\openai_luna_requirement_seed_for_team.sql")
)

$ErrorActionPreference = "Stop"
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory((Split-Path -Parent $outputFullPath)) | Out-Null

function Invoke-MySqlRawQuery {
    param([string]$Query, [string]$TargetFile)

    $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processInfo.FileName = $MySqlExe
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $escapedQuery = $Query.Replace('"', '\"')
    $processInfo.Arguments = "--default-character-set=utf8mb4 -u$MySqlUser -p$MySqlPassword -N -B -r -e `"$escapedQuery`""

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $processInfo
    [void]$process.Start()
    $errorReader = $process.StandardError.ReadToEndAsync()
    $targetStream = [System.IO.File]::Open($TargetFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $process.StandardOutput.BaseStream.CopyTo($targetStream)
    }
    finally {
        $targetStream.Dispose()
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "mysql export query failed: $($errorReader.Result)"
    }
}

function Write-ValuesInChunks {
    param(
        [System.IO.StreamWriter]$Writer,
        [string]$RowsFile,
        [string]$InsertPrefix,
        [int]$ChunkSize = 500
    )

    $reader = [System.IO.StreamReader]::new($RowsFile, $utf8NoBom, $false)
    try {
        $chunk = [System.Collections.Generic.List[string]]::new()
        while (($line = $reader.ReadLine()) -ne $null) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $chunk.Add($line)
            if ($chunk.Count -eq $ChunkSize) {
                $Writer.WriteLine($InsertPrefix)
                $Writer.WriteLine(($chunk -join ",`n"))
                $Writer.WriteLine(";")
                $Writer.WriteLine()
                $chunk.Clear()
            }
        }
        if ($chunk.Count -gt 0) {
            $Writer.WriteLine($InsertPrefix)
            $Writer.WriteLine(($chunk -join ",`n"))
            $Writer.WriteLine(";")
            $Writer.WriteLine()
        }
    }
    finally {
        $reader.Dispose()
    }
}

$requirementsRowsFile = [System.IO.Path]::GetTempFileName()
$jobSkillsRowsFile = [System.IO.Path]::GetTempFileName()

try {
    $requirementsQuery = @"
USE $Database;
SELECT CONCAT(
    '(', QUOTE(jp.external_job_id), ',',
    QUOTE(COALESCE(jr.type, '')), ',',
    QUOTE(REPLACE(REPLACE(COALESCE(jr.content, ''), CHAR(13), ' '), CHAR(10), ' ')), ',',
    QUOTE(REPLACE(REPLACE(COALESCE(jr.source_excerpt, ''), CHAR(13), ' '), CHAR(10), ' ')), ',',
    QUOTE(COALESCE(jr.importance, '')), ',',
    QUOTE(COALESCE(jr.extraction_source, '')), ',',
    QUOTE(COALESCE(jr.verification_status, '')), ')'
)
FROM job_requirements jr
JOIN job_postings jp ON jp.id = jr.job_posting_id
WHERE jp.source_provider = 'WANTED'
  AND jr.extraction_source = 'OPENAI_LUNA'
ORDER BY jp.external_job_id, jr.id;
"@

    $jobSkillsQuery = @"
USE $Database;
SELECT CONCAT(
    '(', QUOTE(jp.external_job_id), ',',
    QUOTE(canonical_skill.name), ',',
    QUOTE(js.requirement_type), ',',
    QUOTE(REPLACE(REPLACE(COALESCE(MIN(js.source_excerpt), ''), CHAR(13), ' '), CHAR(10), ' ')), ')'
)
FROM job_skills js
JOIN job_postings jp ON jp.id = js.job_posting_id
JOIN skills canonical_skill
  ON canonical_skill.id = js.canonical_skill_id
 AND canonical_skill.catalog_status = 'CANONICAL'
JOIN job_requirements jr
  ON jr.job_posting_id = js.job_posting_id
 AND jr.type = 'SKILL'
 AND jr.extraction_source = 'OPENAI_LUNA'
 AND jr.source_excerpt = js.source_excerpt
WHERE jp.source_provider = 'WANTED'
GROUP BY jp.external_job_id, canonical_skill.name, js.requirement_type
ORDER BY jp.external_job_id, canonical_skill.name, js.requirement_type;
"@

    Invoke-MySqlRawQuery -Query $requirementsQuery -TargetFile $requirementsRowsFile
    Invoke-MySqlRawQuery -Query $jobSkillsQuery -TargetFile $jobSkillsRowsFile

    $writer = [System.IO.StreamWriter]::new($outputFullPath, $false, $utf8NoBom)
    try {
        $writer.WriteLine("-- Team-safe OpenAI Luna job-requirement seed.")
        $writer.WriteLine("-- Prerequisites: Wanted job postings are imported and Flyway V10/V11 or newer has run.")
        $writer.WriteLine("-- This file does not insert skills or aliases with local numeric IDs.")
        $writer.WriteLine("SET NAMES utf8mb4;")
        $writer.WriteLine("START TRANSACTION;")
        $writer.WriteLine()
        $writer.WriteLine("CREATE TEMPORARY TABLE _luna_requirement_seed (")
        $writer.WriteLine("  external_job_id VARCHAR(255) NOT NULL,")
        $writer.WriteLine("  type VARCHAR(30) NOT NULL,")
        $writer.WriteLine("  content TEXT NOT NULL,")
        $writer.WriteLine("  source_excerpt TEXT NOT NULL,")
        $writer.WriteLine("  importance VARCHAR(30) NOT NULL,")
        $writer.WriteLine("  extraction_source VARCHAR(30) NOT NULL,")
        $writer.WriteLine("  verification_status VARCHAR(30) NOT NULL"); $writer.WriteLine(");"); $writer.WriteLine()
        Write-ValuesInChunks -Writer $writer -RowsFile $requirementsRowsFile -InsertPrefix "INSERT INTO _luna_requirement_seed (external_job_id, type, content, source_excerpt, importance, extraction_source, verification_status) VALUES"
        $writer.WriteLine("INSERT INTO job_requirements (job_posting_id, type, content, source_excerpt, importance, extraction_source, verification_status)")
        $writer.WriteLine("SELECT jp.id, seed.type, seed.content, seed.source_excerpt, seed.importance, seed.extraction_source, seed.verification_status")
        $writer.WriteLine("FROM _luna_requirement_seed seed")
        $writer.WriteLine("JOIN job_postings jp ON jp.source_provider = 'WANTED' AND jp.external_job_id = seed.external_job_id")
        $writer.WriteLine("WHERE NOT EXISTS (SELECT 1 FROM job_requirements existing")
        $writer.WriteLine("  WHERE existing.job_posting_id = jp.id AND existing.type = seed.type")
        $writer.WriteLine("    AND existing.content = seed.content AND existing.importance = seed.importance")
        $writer.WriteLine("    AND existing.extraction_source = seed.extraction_source);"); $writer.WriteLine()
        $writer.WriteLine("CREATE TEMPORARY TABLE _luna_job_skill_seed (")
        $writer.WriteLine("  external_job_id VARCHAR(255) NOT NULL,")
        $writer.WriteLine("  skill_name VARCHAR(100) NOT NULL,")
        $writer.WriteLine("  requirement_type VARCHAR(30) NOT NULL,")
        $writer.WriteLine("  source_excerpt TEXT NOT NULL"); $writer.WriteLine(");"); $writer.WriteLine()
        Write-ValuesInChunks -Writer $writer -RowsFile $jobSkillsRowsFile -InsertPrefix "INSERT INTO _luna_job_skill_seed (external_job_id, skill_name, requirement_type, source_excerpt) VALUES"
        $writer.WriteLine("INSERT INTO job_skills (job_posting_id, skill_id, canonical_skill_id, requirement_type, source_excerpt)")
        $writer.WriteLine("SELECT jp.id, skill.id, skill.id, seed.requirement_type, seed.source_excerpt")
        $writer.WriteLine("FROM _luna_job_skill_seed seed")
        $writer.WriteLine("JOIN job_postings jp ON jp.source_provider = 'WANTED' AND jp.external_job_id = seed.external_job_id")
        $writer.WriteLine("JOIN skills skill ON skill.name = seed.skill_name AND skill.catalog_status = 'CANONICAL'")
        $writer.WriteLine("ON DUPLICATE KEY UPDATE canonical_skill_id = VALUES(canonical_skill_id), source_excerpt = VALUES(source_excerpt);"); $writer.WriteLine()
        $writer.WriteLine("SELECT COUNT(DISTINCT seed.external_job_id) AS missing_wanted_job_postings")
        $writer.WriteLine("FROM _luna_requirement_seed seed")
        $writer.WriteLine("LEFT JOIN job_postings jp ON jp.source_provider = 'WANTED' AND jp.external_job_id = seed.external_job_id")
        $writer.WriteLine("WHERE jp.id IS NULL;")
        $writer.WriteLine("DROP TEMPORARY TABLE _luna_job_skill_seed;")
        $writer.WriteLine("DROP TEMPORARY TABLE _luna_requirement_seed;")
        $writer.WriteLine("COMMIT;")
    }
    finally { $writer.Dispose() }

    $requirementRows = (Get-Content -LiteralPath $requirementsRowsFile -Encoding utf8 | Where-Object { $_.Trim() }).Count
    $jobSkillRows = (Get-Content -LiteralPath $jobSkillsRowsFile -Encoding utf8 | Where-Object { $_.Trim() }).Count
    Write-Host "Created $outputFullPath"
    Write-Host "Requirement rows: $requirementRows"
    Write-Host "Canonical job-skill rows: $jobSkillRows"
}
finally {
    Remove-Item -LiteralPath $requirementsRowsFile, $jobSkillsRowsFile -Force -ErrorAction SilentlyContinue
}
