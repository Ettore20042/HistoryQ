
package com.example.historyq.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

@Service
public class PdfToImageService {

    private static final float DPI_SCALE = 2.0f;

    public List<byte[]> convertToImages(InputStream inputStream)
            throws IOException {

        List<byte[]> pages = new ArrayList<>();

        try (PDDocument pdf = PDDocument.load(inputStream)) {

            PDFRenderer renderer = new PDFRenderer(pdf);

            for (int pageIndex = 0;
                 pageIndex < pdf.getNumberOfPages();
                 pageIndex++) {

                BufferedImage image =
                        renderer.renderImage(pageIndex, DPI_SCALE);

                try (ByteArrayOutputStream output =
                             new ByteArrayOutputStream()) {

                    ImageIO.write(image, "png", output);
                    pages.add(output.toByteArray());
                }

                image.flush();
            }
        }

        return pages;
    }
}
