package com.example.pdfreducer;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import org.apache.sanselan.ImageReadException;

import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.*;

public class PdfFileSizeReducer {
    private PdfReader pdfReader;
    private int resizeExceptWidthUnder = 0;
    private int resizeExceptHeightUnder = 0;

    private String iccProfileFileName = "ISOcoated_v2_300_eci.icc";

    public PdfFileSizeReducer(InputStream inputStream) throws IOException {
            pdfReader = new PdfReader(inputStream);
    }

    public void setResizeExceptSizeUnder(int width, int height) {
        resizeExceptWidthUnder = width;
        resizeExceptHeightUnder = height;
    }

    public void reduce(OutputStream outputStream, float scaleFactor,
                              float jpegCompressionFactor) throws IOException, DocumentException {
        int size = pdfReader.getXrefSize();

        ICC_ColorSpace cmykCS = getCmykCS(null);

        // Look for image and manipulate image stream
        for (int i = 0; i < size; i++) {
            PdfObject object = pdfReader.getPdfObject(i);

            if (object == null || !object.isStream())
                continue;

            PRStream stream = (PRStream)object;

            if (!PdfName.IMAGE.equals(stream.getAsName(PdfName.SUBTYPE)))
                continue;

            // if it is not a jpeg filter
            if (PdfName.DCTDECODE.equals(stream.getAsName(PdfName.FILTER))) {
                doJpegCompression(stream, scaleFactor, jpegCompressionFactor, cmykCS);
            }
        }

        // Save altered PDF
        PdfStamper stamper = new PdfStamper(pdfReader, outputStream);
        stamper.setFullCompression();
        stamper.close();
        pdfReader.close();
    }

    public String getIccProfileFileName() {
        return iccProfileFileName;
    }

    public void setIccProfileFileName(String iccProfileFileName) {
        this.iccProfileFileName = iccProfileFileName;
    }

    private void doJpegCompression(PRStream stream, float scaleFactor, float jpegCompressionFactor, ICC_ColorSpace cmykCS) throws IOException {
        PdfImageObject image = new PdfImageObject(stream);

        int imageWidth = image.getBufferedImage().getRaster().getWidth();
        int imageHeight = image.getBufferedImage().getRaster().getHeight();

        if (!(resizeExceptWidthUnder < imageWidth && resizeExceptHeightUnder < imageHeight)) {
            scaleFactor = 1.0f;
        }

        int width = (int)(imageWidth * scaleFactor);
        int height = (int)(imageHeight * scaleFactor);

        JpegCompressor jpegCompressor = new JpegCompressor(image.getImageAsBytes(), cmykCS);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            jpegCompressor.compress(output, scaleFactor, jpegCompressionFactor);
        } catch (IOException e){
            throw e;
        } catch (ImageReadException e) {
            throw new IOException(e.getMessage());
        }

        stream.clear();
        stream.setData(output.toByteArray(), false, PRStream.NO_COMPRESSION);
        stream.put(PdfName.TYPE, PdfName.XOBJECT);
        stream.put(PdfName.SUBTYPE, PdfName.IMAGE);
        stream.put(PdfName.FILTER, PdfName.DCTDECODE);
        stream.put(PdfName.WIDTH, new PdfNumber(width));
        stream.put(PdfName.HEIGHT, new PdfNumber(height));
        stream.put(PdfName.BITSPERCOMPONENT, new PdfNumber(8));
        stream.put(PdfName.COLORSPACE, PdfName.DEVICERGB);
    }

    private ICC_ColorSpace getCmykCS(ICC_Profile cmykProfile) throws IOException {
        if (cmykProfile == null){
            cmykProfile = ICC_Profile.getInstance(ClassLoader.getSystemResourceAsStream(iccProfileFileName));
        }

        return new ICC_ColorSpace(cmykProfile);
    }
}
