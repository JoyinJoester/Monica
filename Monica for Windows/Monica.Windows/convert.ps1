
Add-Type -AssemblyName System.Drawing
$path = "AppIcon.png"
if (Test-Path $path) {
    try {
        $img = [System.Drawing.Image]::FromFile($path)
        # Resize to 256x256 max for icon
        $thumb = $img.GetThumbnailImage(256, 256, $null, [IntPtr]::Zero)
        $bmp = [System.Drawing.Bitmap]$thumb
        $hicon = $bmp.GetHicon()
        $icon = [System.Drawing.Icon]::FromHandle($hicon)
        
        $fs = New-Object System.IO.FileStream "AppIcon.ico", Create
        $icon.Save($fs)
        $fs.Close()
        
        $icon.Dispose()
        $bmp.Dispose()
        $img.Dispose()
        Write-Host "Success"
    } catch {
        Write-Host "Error: $_"
        exit 1
    }
} else {
    Write-Host "File not found"
    exit 1
}
