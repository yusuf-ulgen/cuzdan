import os

drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

rename_map = {
    "ana sayfa (cüzdan sayfası).png": "ic_nav_home.png",
    "işlemler sayfası.png": "ic_nav_settings.png",
    "piyasalar sayfası.png": "ic_nav_markets.png",
    "raporlar sayfası.png": "ic_nav_reports.png",
    "varlıklar sayfası.png": "ic_nav_assets.png",
    "borsa.png": "ic_cat_stock.png",
    "kripto.png": "ic_cat_crypto.png",
    "nakit.png": "ic_cat_cash.png",
    "fon.png": "ic_cat_fund.png",
    "döviz.png": "ic_cat_doviz.png",
    "emtia.png": "ic_cat_commodity.png"
}

files = os.listdir(drawable_dir)
for old_name, new_name in rename_map.items():
    # Find the file by looking for common substrings to avoid encoding issues
    found = False
    for filename in files:
        if old_name.lower() in filename.lower() or filename.lower() in old_name.lower():
            old_path = os.path.join(drawable_dir, filename)
            new_path = os.path.join(drawable_dir, new_name)
            try:
                os.rename(old_path, new_path)
                print(f"Renamed: {filename} -> {new_name}")
                found = True
                break
            except Exception as e:
                print(f"Error renaming {filename}: {e}")
    if not found:
        print(f"Not found: {old_name}")
