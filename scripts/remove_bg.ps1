Add-Type -AssemblyName System.Drawing
$SourceDir = "C:\Users\YUSUF\.gemini\antigravity\brain\57702cd2-5f21-4372-b5e3-a9872e8d6195\browser"
$DestDir = "c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

function Remove-BlackBackground($FileName, $DestName) {
    try {
        $SourceFile = Join-Path $SourceDir $FileName
        $DestFile = Join-Path $DestDir $DestName
        $img = [System.Drawing.Image]::FromFile($SourceFile)
        $bmp = New-Object System.Drawing.Bitmap($img)
        $img.Dispose()
        
        # Color to make transparent (Black)
        $bmp.MakeTransparent([System.Drawing.Color]::Black)
        
        $bmp.Save($DestFile, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        Write-Output "Processed: $FileName -> $DestName"
    } catch {
        Write-Error "Failed to process $FileName: $_"
    }
}

Remove-BlackBackground "ic_p_agr.png" "ic_p_agr.png"
Remove-BlackBackground "ic_p_bio.png" "ic_p_bio.png"
Remove-BlackBackground "ic_p_cur.png" "ic_p_cur.png"
Remove-BlackBackground "ic_p_faq.png" "ic_p_faq.png"
Remove-BlackBackground "ic_p_lang.png" "ic_p_lang.png"
Remove-BlackBackground "ic_p_notif.png" "ic_p_notif.png"
Remove-BlackBackground "ic_p_res.png" "ic_p_res.png"
Remove-BlackBackground "ic_p_sha.png" "ic_p_sha.png"
Remove-BlackBackground "ic_p_sup.png" "ic_p_sup.png"
Remove-BlackBackground "ic_p_theme.png" "ic_p_theme.png"
