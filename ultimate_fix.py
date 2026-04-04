import os
import re

drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

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

print(f"Scanning: {drawable_dir}")
for filename in os.listdir(drawable_dir):
    if not filename.endswith(".png"):
        continue
    
    # If it's already a valid name, leave it
    if re.match(r"^[a-z0-9_]+\.png$", filename):
        continue
        
    print(f"Found invalid name: {filename}")
    found = False
    name_for_check = filename.lower()
    
    # Special case mappings first
    for key, new_base in mapping.items():
        if key.lower() in name_for_check:
            new_name = new_base + ".png"
            old_path = os.path.join(drawable_dir, filename)
            new_path = os.path.join(drawable_dir, new_name)
            try:
                if os.path.exists(new_path) and old_path != new_path:
                    os.remove(new_path)
                os.rename(old_path, new_path)
                print(f"SUCCESS: {filename} -> {new_name}")
                found = True
                break
            except Exception as e:
                print(f"FAILED: {filename} -> {new_name}: {e}")
    
    if not found:
        # Generic cleaner for anything else with spaces/special chars
        basename = filename.lower().replace(".png", "")
        # Remove anything except lowercase alphanumeric
        clean_basename = re.sub(r'[^a-z0-9]', '_', basename)
        # Collapse multiple underscores
        clean_basename = re.sub(r'_+', '_', clean_basename).strip('_')
        new_name = clean_basename + ".png"
        
        old_path = os.path.join(drawable_dir, filename)
        new_path = os.path.join(drawable_dir, new_name)
        try:
            if os.path.exists(new_path) and old_path != new_path:
                os.remove(new_path)
            os.rename(old_path, new_path)
            print(f"CLEANED: {filename} -> {new_name}")
        except Exception as e:
            print(f"FAILED CLEAN: {filename} -> {new_name}: {e}")
