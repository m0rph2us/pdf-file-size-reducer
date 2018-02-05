package com.example.pdfreducer;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfImageObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;

public class PdfFileSizeReducer {
    private PdfReader pdfReader;
    private int resizeExceptWidthUnder = 0;
    private int resizeExceptHeightUnder = 0;

    public PdfFileSizeReducer(InputStream inputStream) throws IOException {
            pdfReader = new PdfReader(inputStream);
    }

    public int getNumberOfPages() {
        return pdfReader.getNumberOfPages();
    }

    public void setResizeExceptSizeUnder(int width, int height) {
        resizeExceptWidthUnder = width;
        resizeExceptHeightUnder = height;
    }

    public void reduce(OutputStream outputStream, float scaleFactor,
                              float jpegCompressionFactor) throws IOException, DocumentException {
        int size = pdfReader.getXrefSize();

        // Look for image and manipulate image stream
        for (int i = 0; i < size; i++) {
            PdfObject object = pdfReader.getPdfObject(i);

            if (object == null || !object.isStream())
                continue;

            PRStream stream = (PRStream)object;

            if (!PdfName.IMAGE.equals(stream.getAsName(PdfName.SUBTYPE)))
                continue;

            if (!PdfName.DCTDECODE.equals(stream.getAsName(PdfName.FILTER)))
                continue;

            PdfImageObject image = new PdfImageObject(stream);
            BufferedImage bi = image.getBufferedImage();

            if (bi == null)
                continue;

            if (!(resizeExceptWidthUnder < bi.getWidth()
                    && resizeExceptHeightUnder < bi.getHeight())) {
                scaleFactor = 1.0f;
            }

            int width = (int)(bi.getWidth() * scaleFactor);
            int height = (int)(bi.getHeight() * scaleFactor);

            if (width <= 0 || height <= 0)
                continue;

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // scaling image
            AffineTransform at = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
            Graphics2D g = img.createGraphics();
            g.drawRenderedImage(bi, at);

            ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();

            // jpeg compression
            JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
            jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpegParams.setCompressionQuality(jpegCompressionFactor);

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            writer.setOutput(new MemoryCacheImageOutputStream(imgBytes));
            writer.write(null, new IIOImage(img, null, null), jpegParams);

            stream.clear();
            stream.setData(imgBytes.toByteArray(), false, PRStream.NO_COMPRESSION);
            stream.put(PdfName.TYPE, PdfName.XOBJECT);
            stream.put(PdfName.SUBTYPE, PdfName.IMAGE);
            stream.put(PdfName.FILTER, PdfName.DCTDECODE);
            stream.put(PdfName.WIDTH, new PdfNumber(width));
            stream.put(PdfName.HEIGHT, new PdfNumber(height));
            stream.put(PdfName.BITSPERCOMPONENT, new PdfNumber(8));
            stream.put(PdfName.COLORSPACE, PdfName.DEVICERGB);
        }

        pdfReader.removeUnusedObjects();

        // Save altered PDF
        PdfStamper stamper = new PdfStamper(pdfReader, outputStream);
        stamper.setFullCompression();
        stamper.close();
        pdfReader.close();
    }
}
