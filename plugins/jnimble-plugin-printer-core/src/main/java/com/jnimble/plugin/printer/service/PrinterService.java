package com.jnimble.plugin.printer.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.printer.mapper.PrinterMapper;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PrinterService {

    private final PrinterMapper printerMapper;

    public PrinterService(PrinterMapper printerMapper) {
        this.printerMapper = printerMapper;
    }

    public List<PrinterEntity> listPrinters() {
        return MapperUtils.selectList(printerMapper, PrinterEntity.class, null);
    }

    public PrinterEntity getPrinter(String id) {
        return MapperUtils.getById(printerMapper, id, null);
    }

    public PrinterEntity createPrinter(PrinterEntity entity) {
        return MapperUtils.insert(printerMapper, entity);
    }

    public PrinterEntity updatePrinter(PrinterEntity entity) {
        return MapperUtils.updateById(printerMapper, entity);
    }

    public void deletePrinter(String id) {
        MapperUtils.deleteById(printerMapper, id);
    }
}
