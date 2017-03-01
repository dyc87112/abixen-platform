/**
 * Copyright (c) 2010-present Abixen Systems. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.abixen.platform.service.businessintelligence.multivisualisation.service.impl.parser;

import com.abixen.platform.service.businessintelligence.multivisualisation.message.FileParseError;
import com.abixen.platform.service.businessintelligence.multivisualisation.message.FileParserMessage;
import com.abixen.platform.service.businessintelligence.multivisualisation.model.enumtype.DataValueType;
import com.abixen.platform.service.businessintelligence.multivisualisation.model.impl.data.DataValue;
import com.abixen.platform.service.businessintelligence.multivisualisation.model.impl.data.DataValueDouble;
import com.abixen.platform.service.businessintelligence.multivisualisation.model.impl.data.DataValueString;
import com.abixen.platform.service.businessintelligence.multivisualisation.model.impl.file.DataFileColumn;
import com.abixen.platform.service.businessintelligence.multivisualisation.service.FileParserService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.abixen.platform.service.businessintelligence.multivisualisation.model.enumtype.DataValueType.DOUBLE;
import static com.abixen.platform.service.businessintelligence.multivisualisation.model.enumtype.DataValueType.STRING;

@Service("excelParserService")
public class ExcelParserServiceImpl implements FileParserService {

    private final int headerRowIndex = 0;
    private final int firstSheetIndex = 0;

    @Override
    public FileParserMessage<DataFileColumn> parseFile(MultipartFile multipartFile) {
        FileParserMessage<DataFileColumn> msg = new FileParserMessage<>();
        try {
            Workbook workbook = openFileAsWorkBook(multipartFile, msg);
            parseWorkbook(workbook.getSheetAt(firstSheetIndex), msg);
        } catch (IOException e) {
            msg.addFileParseError(new FileParseError("Can't read file."));
        }
        return msg;
    }

    private void parseWorkbook(Sheet sheet, FileParserMessage<DataFileColumn> msg) {
        Row headerRow = sheet.getRow(headerRowIndex);
        if (validate(msg, headerRow, sheet)) {
            msg.setData(prepareData(msg, headerRow, sheet));
        }

    }

    private List<DataFileColumn> prepareData(FileParserMessage<DataFileColumn> msg, Row headerRow, Sheet sheet) {
        return getColumns(headerRow, sheet);
    }

    private List<DataFileColumn> getColumns(Row headerRow, Sheet sheet) {
        List<DataFileColumn> dataFileColumns = new ArrayList<>();
        for (int i = 0; i < headerRow.getPhysicalNumberOfCells(); i++) {
            DataFileColumn dataFileColumn = new DataFileColumn();
            DataValueType columnType = getColumnTypeAsDataValueType(sheet.getRow(headerRowIndex + 1).getCell(i), i);
            dataFileColumn.setDataValueType(columnType);
            dataFileColumn.setValues(getValues(sheet, i, columnType));
            dataFileColumns.add(dataFileColumn);
        }
        return dataFileColumns;
    }

    private List<DataValue> getValues(Sheet sheet, int i, DataValueType columnType) {
        List<DataValue> values = new ArrayList<>();
        for (int j = 1; j < sheet.getPhysicalNumberOfRows(); j++) {
            Cell cell = sheet.getRow(j).getCell(i);
            values.add(getValueAsDataValue(cell, columnType));
        }
        return values;
    }

    private DataValue getValueAsDataValue(Cell cell, DataValueType columnType) {
        switch (columnType) {
            case DOUBLE:
                return parseAsDouble(cell);
            case STRING:
                return parseAsString(cell);
            default:
                return null;
        }
    }

    private DataValue parseAsString(Cell cell) {
        DataValueString dataValueString = new DataValueString();
        dataValueString.setValue(cell.getStringCellValue());
        return dataValueString;
    }

    private DataValue parseAsDouble(Cell cell) {
        DataValueDouble dataValueDouble = new DataValueDouble();
        dataValueDouble.setValue(cell.getNumericCellValue());
        return dataValueDouble;
    }

    private boolean validate(FileParserMessage<DataFileColumn> msg, Row headerRow, Sheet sheet) {
        for (int i = 0; i < headerRow.getPhysicalNumberOfCells(); i++) {
            Cell columnCell = sheet.getRow(headerRowIndex + 1).getCell(i);
            if (columnCell == null) {
                msg.addFileParseError(new FileParseError(1, "[Line %d, column %d] Cell is empty. Column don't be validated"));
            } else {
                DataValueType columnType = getColumnTypeAsDataValueType(columnCell, i);
                for (int j = 1; j < sheet.getPhysicalNumberOfRows(); j++) {
                    Cell cell = sheet.getRow(j).getCell(i);
                    if (cell == null) {
                        msg.addFileParseError(new FileParseError(1, String.format("[Line %d, column %d] Cell is empty", j, i)));
                    } else {
                        if (!columnType.equals(getColumnTypeAsDataValueType(cell, i))) {
                            msg.addFileParseError(new FileParseError(1, String.format("[Line %d, column %d] Cell type is diffrent that first cell in this column", j, i)));
                        }
                    }
                }
            }
        }
        return msg.getFileParseErrors().isEmpty();
    }

    private DataValueType getColumnTypeAsDataValueType(Cell cell, int i) {
        switch (cell.getCellTypeEnum()) {
            case NUMERIC:
                return DOUBLE;
            case STRING:
                return STRING;
            default:
                return null;
        }
    }

    private Workbook openFileAsWorkBook(MultipartFile file, FileParserMessage<DataFileColumn> msg) throws IOException {
        try {
            String fileName = file.getOriginalFilename();
            if (".xlsx".equals(fileName.substring(fileName.lastIndexOf(".")))) {
                return new XSSFWorkbook(file.getInputStream());
            }
            if (".xls".equals(fileName.substring(fileName.lastIndexOf(".")))) {
                return new HSSFWorkbook(file.getInputStream());
            }
            throw new NotOfficeXmlFileException("Not recognized type");
        } catch (NotOfficeXmlFileException e) {
            msg.addFileParseError(new FileParseError(0, "Invalid file type"));
            throw new IOException();
        }
    }
}
