Add-Type -AssemblyName System.Drawing

$inputPath = "C:\Users\Jhonata D Claudio\.gemini\antigravity\brain\49020a6d-c0bc-44e7-adc0-50661d3b7f7c\.user_uploaded\media__1785511304213.jpg"
$resDir = "C:\Users\Jhonata D Claudio\Documents\-INNAV-Navegacao-Indoor\app-mobile\app\src\main\res"

if (-not (Test-Path $inputPath)) {
    Write-Error "Imagem original nao encontrada em $inputPath"
    exit
}

$source = [System.Drawing.Image]::FromFile($inputPath)

$densities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($entry in $densities.GetEnumerator()) {
    $folder = Join-Path $resDir $entry.Key
    if (-not (Test-Path $folder)) { New-Item -ItemType Directory -Path $folder | Out-Null }
    
    $size = $entry.Value
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $graph = [System.Drawing.Graphics]::FromImage($bmp)
    $graph.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graph.DrawImage($source, 0, 0, $size, $size)
    
    $pngPath = Join-Path $folder "ic_launcher.png"
    $roundPngPath = Join-Path $folder "ic_launcher_round.png"
    $webpPath = Join-Path $folder "ic_launcher.webp"
    $roundWebpPath = Join-Path $folder "ic_launcher_round.webp"

    $bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($roundPngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($webpPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($roundWebpPath, [System.Drawing.Imaging.ImageFormat]::Png)

    $graph.Dispose()
    $bmp.Dispose()
    Write-Host "Gerados icones para $entry.Key ($size x $size px)"
}

# Gera 512x512 em drawable/ic_launcher_user_image.png
$drawableFolder = Join-Path $resDir "drawable"
if (-not (Test-Path $drawableFolder)) { New-Item -ItemType Directory -Path $drawableFolder | Out-Null }
$bmp512 = New-Object System.Drawing.Bitmap(512, 512)
$graph512 = [System.Drawing.Graphics]::FromImage($bmp512)
$graph512.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graph512.DrawImage($source, 0, 0, 512, 512)
$bmp512.Save((Join-Path $drawableFolder "ic_launcher_user_image.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$graph512.Dispose()
$bmp512.Dispose()

$source.Dispose()
Write-Host "Icones gerados com sucesso!"
