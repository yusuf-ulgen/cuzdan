import os
import re

drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"
files = os.listdir(drawable_dir)

mapping = {
    "ana": "ic_nav_home",
    "işlem": "ic_nav_settings",
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

for filename in files:
    if not filename.endswith(".png"):
        continue
    
    # Check if name is already correct
    if re.match(r"^[a-z0-9_]+\.png$", filename):
        continue
        
    print(f"Checking invalid file: {filename}")
    found = False
    for key, new_base in mapping.items():
        if key.lower() in filename.lower():
            old_path = os.path.join(drawable_dir, filename)
            new_path = os.path.join(drawable_dir, new_base + ".png")
            try:
                # If new_path exists, delete it first to avoid collisions
                if os.path.exists(new_path):
                    os.remove(new_path)
                os.rename(old_path, new_path)
                print(f"Renamed: {filename} -> {new_base}.png")
                found = True
                break
            except Exception as e:
                print(f"Error: {e}")
    if not found:
        # Fallback: clean the filename
        clean_name = re.sub(r'[^a-z0-9_]', '_', filename.lower().replace(".png", ""))
        clean_name = re.sub(r'_+', '_', clean_name).strip('_')
        new_path = os.path.join(drawable_dir, clean_name + ".png")
        try:
            os.rename(os.path.join(drawable_dir, filename), new_path)
            print(f"Cleaned: {filename} -> {clean_name}.png")
        except Exception as e:
            print(f"Clean error: {e}")
