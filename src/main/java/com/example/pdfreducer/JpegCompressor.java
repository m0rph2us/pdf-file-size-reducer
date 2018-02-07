package com.example.pdfreducer;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.byteSources.ByteSource;
import org.apache.sanselan.common.byteSources.ByteSourceArray;
import org.apache.sanselan.formats.jpeg.JpegImageParser;
import org.apache.sanselan.formats.jpeg.segments.UnknownSegment;

import javax.imageio.*;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

public class JpegCompressor {
    private ICC_ColorSpace fallbackCmykCS;
    private byte[] inputBytes;

    public JpegCompressor(byte[] inputBytes, ICC_ColorSpace fallbackCmykCS) {
        this.inputBytes = inputBytes;
        this.fallbackCmykCS = fallbackCmykCS;
    }

    public void compress(OutputStream outputStream, float scaleFactor, float jpegCompressionFactor) throws IOException, ImageReadException {
        BufferedImage bi = readImage(inputBytes);

        if (bi == null) return;

        int width = (int)(bi.getWidth() * scaleFactor);
        int height = (int)(bi.getHeight() * scaleFactor);

        if (width <= 0 || height <= 0) return;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // scaling image
        AffineTransform at = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
        Graphics2D g = img.createGraphics();
        g.drawRenderedImage(bi, at);

        // jpeg compression
        JPEGImageWriteParam jpegWriteParams = new JPEGImageWriteParam(null);
        jpegWriteParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegWriteParams.setCompressionQuality(jpegCompressionFactor);

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        writer.setOutput(new MemoryCacheImageOutputStream(outputStream));
        writer.write(null, new IIOImage(img, null, null), jpegWriteParams);
    }

    private BufferedImage readImage(byte[] inputBytes) throws IOException, ImageReadException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("jpg").next();
        reader.setInput(new MemoryCacheImageInputStream(new ByteArrayInputStream(inputBytes)));

        BufferedImage bi;

        if (isCmyk(reader)) {
            ICC_Profile profile = Sanselan.getICCProfile(new ByteArrayInputStream(inputBytes), "");

            WritableRaster raster = (WritableRaster) reader.readRaster(0, null);

            if (isApp14Ycck(inputBytes)) {
                convertYcckToCmyk(raster);
            }

            bi = convertCmykToRgb(raster, profile, fallbackCmykCS);
        } else {
            bi = reader.read(0);
        }

        return bi;
    }

    private boolean isCmyk(ImageReader reader) throws IOException {
        Iterator iter = reader.getImageTypes(0);
        while (iter.hasNext()) {
            ImageTypeSpecifier type = (ImageTypeSpecifier)iter.next();
            if (type.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_CMYK) {
                return true;
            }
        }
        return false;
    }

    private boolean isApp14Ycck(byte[] b) throws IOException, ImageReadException {
        JpegImageParser parser = new JpegImageParser();
        ByteSource byteSource = new ByteSourceArray(b);

        @SuppressWarnings("rawtypes")
        List segments = parser.readSegments(byteSource, new int[] { 0xffee }, true);

        if (segments != null && segments.size() >= 1) {
            UnknownSegment app14Segment = (UnknownSegment) segments.get(0);
            byte[] data = app14Segment.bytes;
            if (data.length >= 12 && data[0] == 'A' && data[1] == 'd' && data[2] == 'o' && data[3] == 'b' && data[4] == 'e')
            {
                int transform = app14Segment.bytes[11] & 0xff;
                if (transform == 2) return true;
            }
        }

        return false;
    }

    private void convertYcckToCmyk(WritableRaster raster) {
        int height = raster.getHeight();
        int width = raster.getWidth();
        int stride = width * 4;
        int[] pixelRow = new int[stride];

        for (int h = 0; h < height; h++) {
            raster.getPixels(0, h, width, 1, pixelRow);

            for (int x = 0; x < stride; x += 4) {
                int y = pixelRow[x];
                int cb = pixelRow[x + 1];
                int cr = pixelRow[x + 2];

                int c = (int) (y + 1.402 * cr - 178.956);
                int m = (int) (y - 0.34414 * cb - 0.71414 * cr + 135.95984);
                y = (int) (y + 1.772 * cb - 226.316);

                if (c < 0) c = 0; else if (c > 255) c = 255;
                if (m < 0) m = 0; else if (m > 255) m = 255;
                if (y < 0) y = 0; else if (y > 255) y = 255;

                pixelRow[x] = 255 - c;
                pixelRow[x + 1] = 255 - m;
                pixelRow[x + 2] = 255 - y;
            }

            raster.setPixels(0, h, width, 1, pixelRow);
        }
    }

    private BufferedImage convertCmykToRgb(Raster cmykRaster, ICC_Profile cmykProfile, ICC_ColorSpace fallbackCmykCS) throws IOException {
        ICC_ColorSpace cmykCS = fallbackCmykCS;

        if (cmykProfile != null) {
            cmykCS = new ICC_ColorSpace(cmykProfile);
        }

        BufferedImage rgbImage = new BufferedImage(cmykRaster.getWidth(), cmykRaster.getHeight(), BufferedImage.TYPE_INT_RGB);
        WritableRaster rgbRaster = rgbImage.getRaster();
        ColorSpace rgbCS = rgbImage.getColorModel().getColorSpace();
        ColorConvertOp cmykToRgb = new ColorConvertOp(cmykCS, rgbCS, null);
        cmykToRgb.filter(cmykRaster, rgbRaster);

        return rgbImage;
    }
}
