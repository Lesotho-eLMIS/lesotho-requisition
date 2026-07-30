/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.requisition.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.Test;
import org.openlmis.requisition.dto.NdsoRequisitionLineItemDto;
import org.openlmis.requisition.dto.NdsoRequisitionReportDto;

public class NdsoRequisitionJasperTemplateTest {

  @Test
  public void shouldRenderMultiPagePdfWithBundledResources() throws Exception {
    List<NdsoRequisitionLineItemDto> lineItems = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      lineItems.add(new NdsoRequisitionLineItemDto(
          "ARV",
          "Product with a sufficiently descriptive name " + i,
          "1 box (30 tablets)",
          Integer.toString(i),
          Integer.toString(i + 1),
          Integer.toString(i + 2)));
    }

    NdsoRequisitionReportDto reportDto = new NdsoRequisitionReportDto(
        "Facility - Monthly NDSO - ART - InformedPush",
        "Apr 2026",
        "Maseru District",
        "AHF ART Clinic",
        "Novusimusi Chesa",
        "2026-07-30",
        lineItems);
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("report", reportDto);

    try (InputStream template = getClass()
        .getResourceAsStream("/jasperTemplates/ndsoRequisition.jrxml")) {
      assertNotNull(template);
      JasperReport compiled = JasperCompileManager.compileReport(template);
      JasperPrint print = JasperFillManager.fillReport(
          compiled, parameters, new JRBeanCollectionDataSource(lineItems));
      byte[] pdf = JasperExportManager.exportReportToPdf(print);

      assertTrue(print.getPages().size() > 1);
      assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
    }
  }
}
