from PIL import Image, ImageDraw, ImageFilter

def create_circular_mask(input_path, output_path):
    img = Image.open(input_path).convert("RGBA")
    width, height = img.size
    
    # Create mask at 2x resolution for better anti-aliasing
    mask = Image.new('L', (width * 2, height * 2), 0)
    draw = ImageDraw.Draw(mask)
    
    center_x = width
    center_y = height
    # The new generative image might have a slight border, so we use 0.98 padding just in case.
    # Actually wait! The prompt said "The badge should exactly fill the square frame perfectly".
    radius = int(min(width, height) * 0.98) 
    
    draw.ellipse((center_x - radius, center_y - radius, center_x + radius, center_y + radius), fill=255)
    
    # Blur slightly for smooth edge
    mask = mask.filter(ImageFilter.GaussianBlur(5))
    
    # Resize mask back to original size
    mask = mask.resize((width, height), Image.Resampling.LANCZOS)
    
    result = img.copy()
    result.putalpha(mask)
    
    result.save(output_path, "PNG")

create_circular_mask('app/src/main/res/drawable/ic_p_alert.png', 'app/src/main/res/drawable/ic_p_alert.png')
print("Circular mask applied perfectly.")
