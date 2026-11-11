$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$deliveryDirectory = Join-Path $projectRoot 'entrega'
$zipPath = [System.IO.Path]::GetFullPath((Join-Path $deliveryDirectory 'TrabalhoIA-MLP.zip'))
if (-not $zipPath.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'O destino deve ficar dentro do projeto.'
}
$required = @('src', '.mvn', 'docs', 'experimentos', 'entradas', 'scripts')
$files = @()
foreach ($directory in $required) {
    $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -Recurse -File -Force
}
foreach ($name in @('README.md', 'pom.xml', 'mvnw', 'mvnw.cmd', 'iniciar.cmd', '.gitignore', 'TrabalhoIA2BimMLPBackpropagation.pdf')) {
    $files += Get-Item -LiteralPath (Join-Path $projectRoot $name)
}
New-Item -ItemType Directory -Path $deliveryDirectory -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($zipPath, [System.IO.FileMode]::Create)
$zip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $entry = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $file.FullName, $entry, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
    $stream.Dispose()
}
Write-Output ("ZIP criado: " + $zipPath)
Write-Output ("Arquivos: " + $files.Count)

