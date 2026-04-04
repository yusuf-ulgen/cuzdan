import os

drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"
files = os.listdir(drawable_dir)

print(f"Listing files in {drawable_dir}:")
png_files = [f for f in files if f.lower().endswith(".png")]

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

for filename in png_files:
    matched_val = None
    for key, val in mapping.items():
        # Using a very loose match for Turkish char issues
        if key.lower() in filename.lower() or any(c in filename for c in "üşıöçğÜŞİÖÇĞ"):
            # Check more specific matches for the ones with Turkish chars
            if "ana" in filename.lower(): matched_val = "ic_nav_home"
            elif "işlem" in filename.lower() or "islem" in filename.lower() or "i\u015flem" in filename.lower(): matched_val = "ic_nav_settings"
            elif "piyasa" in filename.lower(): matched_val = "ic_nav_markets"
            elif "rapor" in filename.lower(): matched_val = "ic_nav_reports"
            elif "varlık" in filename.lower() or "varl\u0131k" in filename.lower(): matched_val = "ic_nav_assets"
            elif "borsa" in filename.lower(): matched_val = "ic_cat_stock"
            elif "kripto" in filename.lower(): matched_val = "ic_cat_crypto"
            elif "nakit" in filename.lower(): matched_val = "ic_cat_cash"
            elif "fon" in filename.lower(): matched_val = "ic_cat_fund"
            elif "döviz" in filename.lower() or "doviz" in filename.lower() or "d\u00f6viz" in filename.lower(): matched_val = "ic_cat_doviz"
            elif "emtia" in filename.lower(): matched_val = "ic_cat_commodity"
            
            if matched_val:
                break

    if matched_val:
        old_path = os.path.join(drawable_dir, filename)
        new_name = matched_val + ".png"
        new_path = os.path.join(drawable_dir, new_name)
        
        if old_path.lower() != new_path.lower():
            try:
                if os.path.exists(new_path):
                    os.remove(new_path)
                os.rename(old_path, new_path)
                print(f"SUCCESS: '{filename}' -> '{new_name}'")
            except Exception as e:
                print(f"FAILED: '{filename}' -> '{new_name}': {e}")
        else:
             print(f"SKIP (Already named correctly): {filename}")

print("Script finished.")
