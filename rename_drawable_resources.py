import os
import re

# The drawable directory path
drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

# Mapping of keywords to their new names
rename_map = {
    "ana sayfa": "ic_nav_home",
    "işlemler": "ic_nav_settings",
    "piyasalar": "ic_nav_markets",
    "raporlar": "ic_nav_reports",
    "varlıklar": "ic_nav_assets",
    "borsa": "ic_cat_stock",
    "kripto": "ic_cat_crypto",
    "nakit": "ic_cat_cash",
    "fon": "ic_cat_fund",
    "döviz": "ic_cat_doviz",
    "emtia": "ic_cat_commodity"
}

def clean_name(name):
    # Basic cleaning if no keyword matches
    name = name.lower()
    name = re.sub(r'[^a-z0-9_]', '_', name)
    name = re.sub(r'_+', '_', name)
    return name.strip('_')

print(f"Checking directory: {drawable_dir}")

for filename in os.listdir(drawable_dir):
    if not filename.endswith(".png"):
        continue
    
    old_path = os.path.join(drawable_dir, filename)
    new_base = None
    
    # Check for keyword matches
    for key, val in rename_map.items():
        if key.lower() in filename.lower():
            new_base = val
            break
            
    if new_base:
        new_name = f"{new_base}.png"
    else:
        # Fallback to cleaning the name if already partially correct or unknown
        # but only if it contains invalid characters
        if not re.match(r'^[a-z0-9_]+\.png$', filename):
            base = os.path.splitext(filename)[0]
            new_name = f"{clean_name(base)}.png"
        else:
            continue
            
    new_path = os.path.join(drawable_dir, new_name)
    
    try:
        if old_path.lower() != new_path.lower():
            if os.path.exists(new_path):
                os.remove(new_path)
            os.rename(old_path, new_path)
            print(f"Renamed: '{filename}' -> '{new_name}'")
        else:
            # Case sensitivity change might be needed on some systems, 
            # but usually rename handles it or we need a temp file.
            pass
    except Exception as e:
        print(f"Error renaming '{filename}': {e}")

print("Done.")
