"""
Emtia ve Doviz PNG'lerinin beyaz/açık background'ını düzelt.
Strateji: Köşelerden BFS flood-fill ile bağlantılı açık pikselleri sil.
Bu yöntem ortadaki ikon içeriğine dokunmaz, sadece kenardan bağlantılı arka planı temizler.
"""
from PIL import Image
import numpy as np
from collections import deque
import os

def flood_fill_transparent(img_path, out_path, tolerance=40):
    img = Image.open(img_path).convert("RGBA")
    data = np.array(img)
    h, w = data.shape[:2]
    
    # Arka plan rengi tespiti: sol üst köşeyi referans al
    bg_r, bg_g, bg_b = data[0, 0, :3]
    print(f"  Arka plan rengi: R={bg_r} G={bg_g} B={bg_b}")
    
    visited = np.zeros((h, w), dtype=bool)
    mask = np.zeros((h, w), dtype=bool)  # silinecek pikseller
    
    # 4 köşeden BFS başlat
    queue = deque()
    seeds = [(0, 0), (0, w-1), (h-1, 0), (h-1, w-1)]
    # Kenar boyunca da seed ekle
    for x in range(0, w, 4):
        seeds.append((0, x))
        seeds.append((h-1, x))
    for y in range(0, h, 4):
        seeds.append((y, 0))
        seeds.append((y, w-1))
    
    for sy, sx in seeds:
        if not visited[sy, sx]:
            r, g, b, a = data[sy, sx]
            # Bu piksel arka plana benziyor mu?
            diff = abs(int(r) - int(bg_r)) + abs(int(g) - int(bg_g)) + abs(int(b) - int(bg_b))
            if diff < tolerance * 3:
                queue.append((sy, sx))
                visited[sy, sx] = True
                mask[sy, sx] = True
    
    # BFS flood fill
    while queue:
        y, x = queue.popleft()
        for dy, dx in [(-1,0),(1,0),(0,-1),(0,1)]:
            ny, nx = y+dy, x+dx
            if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx]:
                visited[ny, nx] = True
                r, g, b, a = data[ny, nx]
                diff = abs(int(r) - int(bg_r)) + abs(int(g) - int(bg_g)) + abs(int(b) - int(bg_b))
                if diff < tolerance * 3:
                    mask[ny, nx] = True
                    queue.append((ny, nx))
    
    # Mask uygula: işaretli pikselleri transparan yap
    result = data.copy()
    result[mask, 3] = 0  # alpha = 0
    
    # Kenar yumuşatma: mask sınırındaki piksellere kısmi şeffaflık
    from scipy.ndimage import binary_dilation
    border = binary_dilation(mask) & ~mask
    for y, x in zip(*np.where(border)):
        # Komşu mask piksel sayısına göre alpha ayarla
        neighbors_masked = 0
        for dy, dx in [(-1,0),(1,0),(0,-1),(0,1),(-1,-1),(-1,1),(1,-1),(1,1)]:
            ny, nx = y+dy, x+dx
            if 0 <= ny < h and 0 <= nx < w and mask[ny, nx]:
                neighbors_masked += 1
        alpha = int(255 * (1 - neighbors_masked / 8))
        result[y, x, 3] = alpha
    
    out_img = Image.fromarray(result, 'RGBA')
    out_img.save(out_path, "PNG")
    removed = np.sum(mask)
    print(f"  {removed} piksel silindi ({removed*100/(h*w):.1f}%)")
    print(f"  Kaydedildi: {out_path}")

drawable_dir = r"c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable"

for fname in ["emtia.png", "doviz.png"]:
    path = os.path.join(drawable_dir, fname)
    print(f"\nIsleyici: {fname}")
    # Orijinali yedekle
    backup = path.replace(".png", "_backup.png")
    if not os.path.exists(backup):
        Image.open(path).save(backup)
        print(f"  Yedek: {backup}")
    flood_fill_transparent(path, path, tolerance=35)

print("\nTamamlandi!")
