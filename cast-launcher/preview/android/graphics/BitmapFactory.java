package android.graphics;

import java.io.File;
import javax.imageio.ImageIO;

public final class BitmapFactory {
    public static Bitmap decodeFile(String path) {
        try {
            return new Bitmap(ImageIO.read(new File(path)));
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot read background: " + path, error);
        }
    }
}
