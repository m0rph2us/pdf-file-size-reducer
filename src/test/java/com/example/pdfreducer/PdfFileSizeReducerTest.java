package com.example.pdfreducer;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class PdfFileSizeReducerTest {
    @Test
    public void testReducingPdf() throws Exception {
        String inputFilePath = "/Users/we/Documents/sample2.pdf";
        String outputFilePath = "/Users/we/Documents/sample2-comp.pdf";

        PdfFileSizeReducer pdfReducer = new PdfFileSizeReducer(new FileInputStream(inputFilePath));

        pdfReducer.setResizeExceptSizeUnder(1024, 768);
        pdfReducer.reduce(new FileOutputStream(outputFilePath), 0.5f, 0.5f);
    }
}