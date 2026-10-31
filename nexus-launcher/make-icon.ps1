# Makes the launcher's app icon from any image. Rebuild the APK afterwards with build.ps1.
#   .\make-icon.ps1 -Image C:\path\to\artwork.png          artwork fills the square
#   .\make-icon.ps1 -Image logo.png -Plate                 logo centred on the dark plate
param(
    [Parameter(Mandatory)][string]$Image,
    [switch]$Plate,
    [int]$Size = 512
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Image).Path)
$icon = New-Object System.Drawing.Bitmap $Size, $Size
$graphics = [System.Drawing.Graphics]::FromImage($icon)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
try {
    if ($Plate) {
        $area = New-Object System.Drawing.Rectangle 0, 0, $Size, $Size
        $fill = New-Object System.Drawing.Drawing2D.LinearGradientBrush $area, ([System.Drawing.Color]::FromArgb(46, 26, 92)), ([System.Drawing.Color]::FromArgb(14, 11, 26)), 60.0
        $graphics.FillRectangle($fill, $area)
        $scale = [Math]::Min(($Size * 0.8) / $source.Width, ($Size * 0.8) / $source.Height)
    } else {
        # Crop to a centred square by covering the whole icon.
        $scale = [Math]::Max($Size / $source.Width, $Size / $source.Height)
    }
    $width = $source.Width * $scale
    $height = $source.Height * $scale
    $graphics.DrawImage($source, [single](($Size - $width) / 2), [single](($Size - $height) / 2), [single]$width, [single]$height)
    $target = Join-Path $PSScriptRoot 'src/main/res/drawable-nodpi'
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    $icon.Save((Join-Path $target 'nexus_icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose(); $icon.Dispose(); $source.Dispose()
}
"Icon written: $(Join-Path $target 'nexus_icon.png')"
