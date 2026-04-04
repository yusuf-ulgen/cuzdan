import os

# The drawable directory path
drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

# Final mapping
# Old names (already partially cleaned) -> Final names
rename_map = {
    "ana_sayfa_cuzdan_sayfasi.png": "ic_nav_home.png",
    "islemler_sayfasi.png": "ic_nav_settings.png",
    "piyasalar_sayfasi.png": "ic_nav_markets.png",
    "raporlar_sayfasi.png": "ic_nav_reports.png",
    "varliklar_sayfasi.png": "ic_nav_assets.png",
    "borsa.png": "ic_cat_stock.png",
    "kripto.png": "ic_cat_crypto.png",
    "nakit.png": "ic_cat_cash.png",
    "fon.png": "ic_cat_fund.png",
    "doviz.png": "ic_cat_doviz.png",
    "emtia.png": "ic_cat_commodity.png"
}

for old, new in rename_map.items():
    old_path = os.path.join(drawable_dir, old)
    new_path = os.path.join(drawable_dir, new)
    
    if os.path.exists(old_path):
        try:
             if os.path.exists(new_path) and old_path.lower() != new_path.lower():
                 os.remove(new_path)
             os.rename(old_path, new_path)
             print(f"Final Renamed: {old} -> {new}")
        except Exception as e:
             print(f"Error renaming {old}: {e}")
    else:
        print(f"File not found: {old}")

print("Done.")
