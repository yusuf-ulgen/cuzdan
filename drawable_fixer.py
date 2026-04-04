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

print(f"Directory: {drawable_dir}")
for i, filename in enumerate(files):
    if not filename.lower().endswith(".png"):
        continue
        
    print(f"[{i}] processing: {filename}")
    found = False
    
    # Try to find a match in our keywords
    for key, new_base in mapping.items():
        if key.lower() in filename.lower():
            new_name = new_base + ".png"
            old_path = os.path.join(drawable_dir, filename)
            new_path = os.path.join(drawable_dir, new_name)
            
            try:
                if os.path.exists(new_path) and old_path.lower() != new_path.lower():
                    os.remove(new_path)
                os.rename(old_path, new_path)
                print(f"  RENAME SUCCESS: {new_name}")
                found = True
                break
            except Exception as e:
                print(f"  RENAME FAILED: {e}")
                
    if not found and not re.match(r"^[a-z0-9_]+\.png$", filename):
        # Fallback for any other invalid names
        clean_base = re.sub(r'[^a-z0-9]', '_', filename.lower().replace(".png", ""))
        clean_base = re.sub(r'_+', '_', clean_base).strip('_')
        new_name = clean_base + ".png"
        old_path = os.path.join(drawable_dir, filename)
        new_path = os.path.join(drawable_dir, new_name)
        try:
             if os.path.exists(new_path) and old_path.lower() != new_path.lower():
                 os.remove(new_path)
             os.rename(old_path, new_path)
             print(f"  CLEAN SUCCESS: {new_name}")
        except Exception as e:
             print(f"  CLEAN FAILED: {e}")
