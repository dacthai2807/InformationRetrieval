package vn.hust.ir.crawler;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import java.io.File;

@Service
public class OcrService {

    private final Tesseract tesseract;

    public OcrService() {
        this.tesseract = new Tesseract();

        // Thư mục chứa vie.traineddata và eng.traineddata
        this.tesseract.setDatapath("tessdata");

        // Nhận diện tiếng Việt và tiếng Anh
        this.tesseract.setLanguage("vie+eng");
    }

    public String extractText(File image) throws TesseractException {
        return tesseract.doOCR(image);
    }

    public String extractText(byte[] imageBytes) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }

        try (ByteArrayInputStream input =
                    new ByteArrayInputStream(imageBytes)) {

            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                throw new IllegalArgumentException(
                        "Unsupported image format"
                );
            }

            return tesseract.doOCR(image);
        }
    }
}