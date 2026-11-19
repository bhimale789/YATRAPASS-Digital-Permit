import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javax.imageio.ImageIO;
import java.io.File;
public class Decode {
    public static void main(String[] a) throws Exception {
        var img = ImageIO.read(new File(a[0]));
        System.out.print(new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)))).getText());
    }
}
