/*
 * The MIT License
 *
 * Copyright 2019 Moebiusgames UG (haftungsbeschraenkt).
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

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * A simple PDFTable implementation for PDFBox
 */
public class PDFTable {

    public static int NOT_SET = -1;
    public static final float AUTO_DETERMINE_COLUMN_WIDTH = Float.NEGATIVE_INFINITY;
    public static Color NOT_SET_COLOR = new Color(255, 0, 255, 0);

    private final PageSettings pageSettings = new PageSettings();
    private final List<PDFTableColumn> columns = new ArrayList<>();
    private final List<PDFTableRow> rows = new ArrayList<>();

    private ColumnHeadersMode columnHeadersMode = ColumnHeadersMode.COLUMN_HEADERS_ON_FIRST_PAGE;

    public PDFTable(float... columnWidths) {
        for (int i = 0; i < columnWidths.length; ++i) {
            if (columnWidths[i] < 0) {
                throw new IllegalArgumentException("column " + (i + 1) + " size is < 0");
            }
            columns.add(new PDFTableColumn(this, columnWidths[i]));
        }
    }

    /**
     * returns the settings that are used as templates
     * for every new page
     *
     * @return returns the page settings
     */
    public PageSettings getPageSettings() {
        return pageSettings;
    }

    public ColumnHeadersMode getColumnHeadersMode() {
        return columnHeadersMode;
    }

    public void setColumnHeadersMode(ColumnHeadersMode columnHeadersMode) {
        if (columnHeadersMode == null) {
            throw new IllegalArgumentException("null is not allowed");
        }
        this.columnHeadersMode = columnHeadersMode;
    }

    public PDFTableColumn getColumn(int index) {
        if (index < 0 || index >= this.columns.size()) {
            throw new IllegalArgumentException("index is out of range");
        }
        return this.columns.get(index);
    }

    public int getColumns() {
        return this.columns.size();
    }

    public PDFTableRow addRow() {
        final PDFTableRow row = new PDFTableRow(this, rows.size());
        rows.add(row);
        return row;
    }

    public int getRows() {
        return this.rows.size();
    }

    public PDFTableRow getRow(int index) {
        if (index < 0 || index >= this.rows.size()) {
            throw new IllegalArgumentException("index is out of range");
        }
        return this.rows.get(index);
    }

    /**
     * renders this table to the given document and starts at the last page directly
     * under the last rendered element
     *
     * @param renderContext the render context that collects all pages
     * @param x the x position to render the table
     * @throws IOException
     */
    public void render(PDFRenderContext renderContext, float x) throws IOException {
        render(renderContext, renderContext.getLastPage(), x,
                renderContext.getLastPage().getRenderedYPosition());
    }

    /**
     * renders this table to the given document and starts at the last page
     *
     * @param renderContext the render context that collects all pages
     * @param x the x position to render the table
     * @param y the y position to render the table
     * @throws IOException
     */
    public void render(PDFRenderContext renderContext, float x, float y) throws IOException {
        render(renderContext, renderContext.getLastPage(), x, y);
    }

    /**
     * renders this table to the given document and starts at the given
     * page at the given coordinates.
     *
     * @param renderContext the render context that collects all pages
     * @param page the page to render the table to (not necessarily the last page)
     * @param x the x position to render the table
     * @param y the y position to render the table
     * @throws IOException
     */
    public void render(PDFRenderContext renderContext, PDFPageWithStream page, float x, float y) throws IOException {
        PDFTableRow headingRow = prepareHeadingRow();
        PagePosition pos = new PagePosition(x, y);

        //draw headers if needed
        if (columnHeadersMode == ColumnHeadersMode.COLUMN_HEADERS_ON_FIRST_PAGE
                || columnHeadersMode == ColumnHeadersMode.COLUMN_HEADERS_ON_EVERY_PAGE) {
            page = renderRow(page, headingRow, null, pos, x, renderContext, true);
        }

        for (int rowIndex = 0; rowIndex < this.rows.size(); ++rowIndex) {
            PDFTableRow row = this.rows.get(rowIndex);
            page = renderRow(page, row, headingRow, pos, x, renderContext);
        }
    }

    private PDFPageWithStream renderRow(PDFPageWithStream currentPage,
            PDFTableRow row, PDFTableRow headingRow, PagePosition pos, float x, final PDFRenderContext renderContext) throws IOException {
        return renderRow(currentPage, row, headingRow, pos, x, renderContext, false);
    }

    private PDFPageWithStream renderRow(PDFPageWithStream currentPage,
            PDFTableRow row, PDFTableRow headingRow, PagePosition pos, float x, final PDFRenderContext renderContext,
            boolean forceTopBorder) throws IOException {

        pos.x = x;
        final List<CellRenderInfo> cellInfosList = new ArrayList<>(row.cells.size());
        for (int colIndex = 0; colIndex < row.cells.size(); ++colIndex) {
            final PDFTableCell cell = row.getCell(colIndex);
            if (cell != null) {
                cellInfosList.add(new CellRenderInfo(cell));
            }
        }

        float freeSpace = pos.y - pageSettings.getMarginBottom();
        boolean newPage = freeSpace <= 0;

        //we write out pages as long as we have cells that have not rendered
        //all of their content yet
        while(cellInfosList.stream().anyMatch(cellInfo -> !cellInfo.isDone())) {

            if (newPage) {
                currentPage = renderContext.getOrCreateNextPage(currentPage);

                pos.y = currentPage.getPage().getMediaBox().getHeight() - pageSettings.getMarginTop();
                pos.x = x;

                if (headingRow != null && columnHeadersMode == ColumnHeadersMode.COLUMN_HEADERS_ON_EVERY_PAGE) {
                    //recursive render headings
                    currentPage = renderRow(currentPage, headingRow, null, pos, x, renderContext);
                    pos.x = x;

                    currentPage.setRenderedYPosition(pos.y);
                }

                freeSpace = pos.y - pageSettings.getMarginBottom();
            }

            //we add in-cell rows to each cell for as long as all of them stay below the
            //free space that we still have (aka. Layout cells)
            List<CellRenderInfo> cellInfosQueue = new LinkedList<>(cellInfosList);
            while (!cellInfosQueue.isEmpty()) {
                final Iterator<CellRenderInfo> iterator = cellInfosQueue.iterator();
                while (iterator.hasNext()) {
                    final CellRenderInfo cellInfo = iterator.next();

                    //everything already rendered? -> done
                    if (cellInfo.isDone()) {
                        iterator.remove();
                    } else {
                        //can't add more in-cell rows? -> done
                        if (!cellInfo.incEndRow()) {
                            iterator.remove();
                        } else
                            //takes more space than available? -> done
                            if (cellInfo.getHeight() > freeSpace) {
                                cellInfo.decEndRow();
                                iterator.remove();
                            }
                    }
                }
            }

            //determine max height of all cells
            float maxHeight = cellInfosList.stream()
                    .map(cellInfo -> cellInfo.getHeight())
                    .max(Float::compare).orElse(0f);

            //next: we actually render the cells' content
            final boolean pageBreakBefore = currentPage.isFreshPage();
            for (CellRenderInfo cellInfo : cellInfosList) {
                cellInfo.render(currentPage.getOrCreateStream(), pos, maxHeight, pageBreakBefore, forceTopBorder);
            }
            pos.y -= maxHeight;

            //if we do another loop, we need a new page!
            newPage = true;

            //update rendered y pos
            currentPage.setRenderedYPosition(pos.y);
        }

        return currentPage;
    }

    private PDFTableRow prepareHeadingRow() {
        //prepare heading row
        PDFTableRow headingRow = new PDFTableRow(this, -1);
        for (int colIndex = 0; colIndex < this.columns.size(); ++colIndex) {
            final PDFTableCell cell = headingRow.getCell(colIndex);
            final PDFTableColumn column = columns.get(colIndex);
            cell.setContent(column.getHeading());
            cell.setAlign(column.getHeadingAlign());
            cell.setFontColor(column.getHeadingFontColor());
            cell.setBackgroundColor(column.getHeadingBackgroundColor());
            cell.setFont(column.getHeadingFont());
            cell.setFontSize(column.getHeadingFontSize());
        }
        return headingRow;
    }

    /**
     * calculates the height of the table with the current
     * content (without and top or bottom paddings added).
     * If this exceeds the height of a page, then
     * the table will be wrapped. Note that multiple headings
     * are not accounted for.
     *
     * @return returns the height of the table
     * @throws IOException
     */
    public float getHeight() throws IOException {
        List<Float> rowHeights = getRowHeights();

        PDFTableRow headingRow = prepareHeadingRow();
        final float headingRowHeight = headingRow.getMaxHeight();
        return rowHeights.stream().reduce(0f, (sum, height) -> sum += height, (sum1, sum2) -> sum1 + sum2) +
                (this.getColumnHeadersMode() == ColumnHeadersMode.NO_COLUMN_HEADERS
                ? 0
                : headingRowHeight);
    }

    private List<Float> getRowHeights() throws IOException {
        final List<Float> rowHeights = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < this.rows.size(); ++rowIndex) {
            final PDFTableRow row = this.rows.get(rowIndex);
            rowHeights.add(row.getMaxHeight());
        }
        return rowHeights;
    }

    /**
     * creates a table according to a given total width of
     * the table and sets the column's with as a pecentage
     * of that width
     *
     * @param tableWidth the total table width
     * @param columnWidthPercent the percentages for the columns
     * @return returns a new PDFTable object
     */
    public static PDFTable createByRelativeColumnWidth(float tableWidth, float... columnWidthPercent) {
        final float[] columnWidths = new float[columnWidthPercent.length];
        float total = 0f;
        for (int i = 0; i < columnWidthPercent.length; ++i) {
            columnWidths[i] = tableWidth * columnWidthPercent[i];
            total += columnWidthPercent[i];
            if (total > 1f) {
                throw new IllegalArgumentException("Column widths sum up to be greater thatn 1.0");
            }
        }
        return new PDFTable(columnWidths);
    }

    /**
     * Creates a table with the given total width and fixed widths
     * for the column specified. The remainder of the space is split
     * up between the columns that have the value of constant
     * AUTO_DETERMINE_COLUMN_WIDTH.
     *
     * @param tableWidth the total width
     * @param columnWidths the fixed widths for the columns
     * @return returns a new PDFTable object
     */
    public static PDFTable createWithSomeFixedColumnWidths(float tableWidth, float... columnWidths) {
        final float[] resultWidths = new float[columnWidths.length];
        int undefinedColumns = 0;
        for (int i = 0; i < columnWidths.length; ++i) {
            if (columnWidths[i] != AUTO_DETERMINE_COLUMN_WIDTH) {
                tableWidth -= columnWidths[i];
                if (tableWidth < 0) {
                    throw new IllegalArgumentException("sum of column widths are greater than the given table width");
                }
                resultWidths[i] = columnWidths[i];
            } else {
                undefinedColumns++;
            }
        }
        if (undefinedColumns > 0) {

            float undefinedColumnsWidth = tableWidth / (float) undefinedColumns;
            for (int i = 0; i < columnWidths.length; ++i) {
                if (columnWidths[i] == AUTO_DETERMINE_COLUMN_WIDTH) {
                    resultWidths[i] = undefinedColumnsWidth;
                }
            }
        }

        return new PDFTable(resultWidths);
    }

    public static enum ColumnHeadersMode {
        NO_COLUMN_HEADERS,

        COLUMN_HEADERS_ON_FIRST_PAGE,

        COLUMN_HEADERS_ON_EVERY_PAGE
    }

    public static class PageSettings {

        private float marginTop = 10 * PDFUtils.MM_TO_POINTS_72DPI;
        private float marginBottom = 20 * PDFUtils.MM_TO_POINTS_72DPI;

        public float getMarginTop() {
            return marginTop;
        }

        public void setMarginTop(float marginTop) {
            if (marginTop < 0) {
                throw new IllegalArgumentException("Invalid value");
            }
            this.marginTop = marginTop;
        }

        public float getMarginBottom() {
            return marginBottom;
        }

        public void setMarginBottom(float marginBottom) {
            if (marginBottom < 0) {
                throw new IllegalArgumentException("Invalid value");
            }
            this.marginBottom = marginBottom;
        }

    }

    private static class CellRenderInfo {
        private final PDFTableCell cell;
        private int startRow;
        private int endRow;

        public CellRenderInfo(PDFTableCell cell) {
            this.cell = cell;
            this.cell.updateContentLayout();
        }

        public float getHeight() {
            return cell.getLaidoutContent().getHeight(startRow, endRow)
                    + cell.getPaddingBottom() + cell.getPaddingTop();
        }

        public boolean incEndRow() {
            if (this.endRow < cell.getLaidoutContent().getNumRows()) {
                this.endRow++;
                return true;
            }
            return false;
        }

        public void decEndRow() {
            if (this.endRow > this.startRow + 1) {
                this.endRow--;
            }
        }

        /**
         * renders the current part of the cell and
         * sets the startRow to the current value
         * of the end row and also increments the pos.x
         * value according to the cell's width
         *
         * @param stream
         * @param pos
         * @param rowMaxHeight
         * @param pageBreakBefore
         * @param forceTopBorder
         * @throws IOException
         */
        public void render(PDPageContentStream stream, PagePosition pos,
                float rowMaxHeight, boolean pageBreakBefore,
                boolean forceTopBorder) throws IOException {
            cell.render(stream, pos.x, pos.y, startRow,
                    endRow, rowMaxHeight, pageBreakBefore || forceTopBorder);
            startRow = endRow;

            pos.x += cell.getWidth();
        }

        public boolean isDone() {
            return startRow >= cell.getLaidoutContent().getNumRows();
        }

    }

    private static class PagePosition {
        private float x;
        private float y;

        public PagePosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

}
