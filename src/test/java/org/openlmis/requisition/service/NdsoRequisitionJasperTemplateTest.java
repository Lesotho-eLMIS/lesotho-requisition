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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRPrintImage;
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
          "TB-" + i,
          "Product with a sufficiently descriptive name " + i,
          Integer.toString(i),
          Integer.toString(i + 1),
          Integer.toString(i + 2)));
    }

    NdsoRequisitionReportDto reportDto = new NdsoRequisitionReportDto(
        "Facility - Monthly NDSO - ART - eLMIS",
        "Apr 2026",
        "Maseru District",
        "AHF ART Clinic",
        "Novusimusi Chesa",
        "2026-07-30",
        lineItems);
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("report", reportDto);
    try (InputStream flag = getClass().getResourceAsStream("/images/Flag_of_Lesotho.png")) {
      assertNotNull(flag);
      parameters.put("flagImage", toByteArray(flag));
    }

    try (InputStream template = getClass()
        .getResourceAsStream("/jasperTemplates/ndsoRequisition.jrxml")) {
      assertNotNull(template);
      byte[] templateData = toByteArray(template);
      String templateXml = new String(templateData, StandardCharsets.UTF_8);
      assertTrue(templateXml.contains("PRODUCT CODE"));
      assertFalse(templateXml.contains("TAB ID"));
      assertFalse(templateXml.contains("UNIT OF ISSUE"));

      JasperReport compiled = JasperCompileManager.compileReport(
          new ByteArrayInputStream(templateData));
      JasperPrint print = JasperFillManager.fillReport(
          compiled, parameters, new JRBeanCollectionDataSource(lineItems));
      byte[] pdf = JasperExportManager.exportReportToPdf(print);

      assertTrue(print.getPages().size() > 1);
      assertTrue(print.getPages().get(0).getElements().stream()
          .filter(element -> element instanceof JRPrintImage)
          .map(element -> (JRPrintImage) element)
          .anyMatch(image -> null != image.getRenderer()));
      assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
    }
  }

  private byte[] toByteArray(InputStream inputStream) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      output.write(buffer, 0, length);
    }
    return output.toByteArray();
  }
}
