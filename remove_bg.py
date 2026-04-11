from PIL import Image

def remove_black_bg(input_path, output_path, threshold=15, feather=45):
    img = Image.open(input_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            brightness = max(r, g, b)
            
            if brightness < threshold:
                pixels[x, y] = (0, 0, 0, 0)
            elif brightness < threshold + feather:
                ratio = (brightness - threshold) / feather
                new_a = int(255 * ratio)
                if new_a == 0:
                    pixels[x, y] = (0, 0, 0, 0)
                else:
                    new_r = min(255, int(r / max(0.01, ratio)))
                    new_g = min(255, int(g / max(0.01, ratio)))
                    new_b = min(255, int(b / max(0.01, ratio)))
                    pixels[x, y] = (new_r, new_g, new_b, new_a)
            else:
                pass

    img.save(output_path, "PNG")

remove_black_bg('app/src/main/res/drawable/ic_p_alert.png', 'app/src/main/res/drawable/ic_p_alert.png', threshold=10, feather=80)
print("Background removed and saved.")
