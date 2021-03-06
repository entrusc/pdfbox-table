/*
 * The MIT License
 *
 * Copyright 2019 Moebiusgames UG (haftungsbeschaenkt).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.moebiusgames.pdfbox.table;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * This context collects all pages and their open streams
 */
public class PDFRenderContext {

    private final PDDocument document;
    private final List<PDFPageWithStream> pages = new ArrayList<>();

    public PDFRenderContext(PDDocument document, PDPage firstPage) {
        this.document = document;

        this.pages.add(new PDFPageWithStream(document, firstPage));
        document.addPage(firstPage);
    }

    public PDFPageWithStream getLastPage() {
        if (pages.isEmpty()) {
            throw new IllegalStateException("no pages");
        }
        return pages.get(pages.size() - 1);
    }

    public PDFPageWithStream getPage(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IllegalArgumentException("Page index " + index
                    + " is out of range");
        }
        return pages.get(index);
    }

    public int getPageCount() {
        return pages.size();
    }

    public List<PDFPageWithStream> getPages() {
        return Collections.unmodifiableList(pages);
    }

    public PDDocument getDocument() {
        return document;
    }

    public PDFPageWithStream getOrCreateNextPage(PDFPageWithStream currentPage) {
        int index = pages.indexOf(currentPage);
        if (index < 0) {
            throw new IllegalArgumentException("Not a page of this context");
        }
        if (index == pages.size() - 1) {
            return addPage();
        }
        return pages.get(index + 1);
    }

    public PDFPageWithStream addPage() {
        final PDFPageWithStream lastPage = getLastPage();
        final PDPage newPdPage = new PDPage(lastPage.getPage().getMediaBox());
        PDFPageWithStream newPage = new PDFPageWithStream(document, newPdPage);

        pages.add(newPage);
        document.addPage(newPdPage);

        return newPage;
    }

    public void closeAllPages() throws IOException {
        for (PDFPageWithStream page : pages) {
            page.close();
        }
    }

}
