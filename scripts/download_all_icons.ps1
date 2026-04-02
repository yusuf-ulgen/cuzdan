$ArtifactDir = "C:\Users\YUSUF\.gemini\antigravity\brain\57702cd2-5f21-4372-b5e3-a9872e8d6195"
$DestDir = "c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

# 1. Download the Icons8 icons (Transparent 3D Fluency)
$Icons8 = @{
    "ic_p_bio.png" = "https://img.icons8.com/3d-fluency/300/thumb-id.png"
    "ic_p_res.png" = "https://img.icons8.com/3d-fluency/300/update-left-rotation.png"
    "ic_p_faq.png" = "https://img.icons8.com/3d-fluency/300/question-mark.png"
    "ic_p_sup.png" = "https://img.icons8.com/3d-fluency/300/customer-support.png"
    "ic_p_sha.png" = "https://img.icons8.com/3d-fluency/300/paper-plane.png"
    "ic_p_agr.png" = "https://img.icons8.com/3d-fluency/300/document.png"
}

foreach ($name in $Icons8.Keys) {
    try {
        $dest = Join-Path $DestDir $name
        Invoke-WebRequest -Uri $Icons8[$name] -OutFile $dest
        Write-Output "Downloaded: $name"
    } catch {
        Write-Error "Failed to download $name: $_"
    }
}

# 2. Move the generated transparent icons
$Generated = @{
    "ic_premium_theme_trans_1774203112345_png_1774204453947.png" = "ic_p_theme.png"
    "ic_premium_notif_trans_1774203112345_png_1774204468441.png" = "ic_p_notif.png"
    "ic_premium_lang_trans_1774203112345_png_1774204480329.png" = "ic_p_lang.png"
    "ic_premium_cur_trans_1774203112345_png_1774204506747.png" = "ic_p_cur.png"
}

foreach ($old in $Generated.Keys) {
    try {
        $source = Join-Path $ArtifactDir $old
        $dest = Join-Path $DestDir $Generated[$old]
        Copy-Item -Path $source -Destination $dest -Force
        Write-Output "Copied: $old -> $($Generated[$old])"
    } catch {
        Write-Error "Failed to copy $old: $_"
    }
}
