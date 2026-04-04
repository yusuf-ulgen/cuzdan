import base64

drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

ps_script = f"""
$dir = "{drawable_dir}"
Get-ChildItem -Path $dir -Filter "*.png" | ForEach-Object {{
    $name = $_.Name
    $newName = $null
    
    if ($name -like "*ana sayfa*") {{ $newName = "ic_nav_home.png" }}
    elseif ($name -like "*işlemler*") {{ $newName = "ic_nav_settings.png" }}
    elseif ($name -like "*piyasalar*") {{ $newName = "ic_nav_markets.png" }}
    elseif ($name -like "*raporlar*") {{ $newName = "ic_nav_reports.png" }}
    elseif ($name -like "*varlıklar*") {{ $newName = "ic_nav_assets.png" }}
    elseif ($name -like "*borsa*") {{ $newName = "ic_cat_stock.png" }}
    elseif ($name -like "*kripto*") {{ $newName = "ic_cat_crypto.png" }}
    elseif ($name -like "*nakit*") {{ $newName = "ic_cat_cash.png" }}
    elseif ($name -like "*fon*") {{ $newName = "ic_cat_fund.png" }}
    elseif ($name -like "*döviz*") {{ $newName = "ic_cat_doviz.png" }}
    elseif ($name -like "*emtia*") {{ $newName = "ic_cat_commodity.png" }}
    
    if ($newName -ne $null) {{
        $oldPath = Join-Path $dir $name
        $newPath = Join-Path $dir $newName
        if (Test-Path $newPath) {{ Remove-Item $newPath -Force }}
        Move-Item -Path $oldPath -Destination $newPath -Force
        Write-Output "Renamed: $name -> $newName"
    }}
}}
"""

# Encode to UTF-16LE then base64 for PowerShell -EncodedCommand
encoded = base64.b64encode(ps_script.encode('utf-16-le')).decode('utf-16-le')
print(f"powershell -EncodedCommand {encoded}")

with open(r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\ps_fix.txt", "w") as f:
    f.write(f"powershell -EncodedCommand {encoded}")
