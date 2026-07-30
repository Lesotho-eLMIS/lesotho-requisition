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

package org.openlmis.requisition.web;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import net.sf.jasperreports.engine.JRException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openlmis.requisition.domain.requisition.Requisition;
import org.openlmis.requisition.domain.requisition.RequisitionStatus;
import org.openlmis.requisition.errorhandling.ValidationResult;
import org.openlmis.requisition.exception.ContentNotFoundMessageException;
import org.openlmis.requisition.exception.JasperReportViewException;
import org.openlmis.requisition.exception.ValidationMessageException;
import org.openlmis.requisition.repository.RequisitionRepository;
import org.openlmis.requisition.service.JasperReportsViewService;
import org.openlmis.requisition.service.JasperTemplateService;
import org.openlmis.requisition.service.PermissionService;
import org.springframework.http.ResponseEntity;

@SuppressWarnings({"PMD.UnusedPrivateField"})
public class ReportsControllerTest {
  private static final String REQUISITION_TEMPLATE_PATH = "jasperTemplates/requisition.jrxml";

  @Mock
  private RequisitionRepository requisitionRepository;

  @Mock
  private PermissionService permissionService;

  @Mock
  private JasperTemplateService jasperTemplateService;

  @Mock
  private RequisitionReportDtoBuilder requisitionReportDtoBuilder;

  @Mock
  private JasperReportsViewService jasperReportsViewService;

  @InjectMocks
  private ReportsController reportsController;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test(expected = ContentNotFoundMessageException.class)
  public void shouldNotPrintRequisitionIfTRequisitionDoesNotExist()
      throws JasperReportViewException {
    //given
    when(permissionService.canViewRequisition(any(UUID.class)))
        .thenReturn(ValidationResult.notFound("requisition.not.found"));
    // when
    reportsController.print(UUID.randomUUID());
  }

  @Test
  public void shouldPrintRequisition()
      throws JasperReportViewException, IOException, JRException {
    // given
    byte[] reportData = new byte[1];

    when(requisitionRepository.findById(any(UUID.class)))
        .thenReturn(Optional.of(mock(Requisition.class)));
    when(jasperReportsViewService.generateRequisitionReport(
        any(Requisition.class))).thenReturn(reportData);
    when(permissionService.canViewRequisition(any(UUID.class)))
        .thenReturn(ValidationResult.success());

    // when
    byte[] actualReportData = reportsController.print(UUID.randomUUID()).getBody();

    // then
    assertEquals(reportData, actualReportData);
  }

  @Test(expected = ValidationMessageException.class)
  public void shouldNotPrintNdsoRequisitionBeforeApproval() throws JasperReportViewException {
    Requisition requisition = mock(Requisition.class);
    when(requisition.getStatus()).thenReturn(RequisitionStatus.AUTHORIZED);
    when(requisitionRepository.findById(any(UUID.class))).thenReturn(Optional.of(requisition));
    when(permissionService.canViewRequisition(any(UUID.class)))
        .thenReturn(ValidationResult.success());

    reportsController.printNdso(UUID.randomUUID());
  }

  @Test
  public void shouldPrintNdsoRequisition() throws JasperReportViewException {
    UUID requisitionId = UUID.randomUUID();
    byte[] reportData = new byte[1];
    Requisition requisition = mock(Requisition.class);

    when(requisition.getStatus()).thenReturn(RequisitionStatus.APPROVED);
    when(requisitionRepository.findById(requisitionId)).thenReturn(Optional.of(requisition));
    when(permissionService.canViewRequisition(requisitionId))
        .thenReturn(ValidationResult.success());
    when(jasperReportsViewService.generateNdsoRequisitionReport(requisition))
        .thenReturn(reportData);

    ResponseEntity<byte[]> response = reportsController.printNdso(requisitionId);

    assertEquals(reportData, response.getBody());
    assertEquals("application/pdf;charset=UTF-8",
        response.getHeaders().getContentType().toString());
    assertEquals("inline; filename=ndso-requisition-" + requisitionId + ".pdf",
        response.getHeaders().getFirst("Content-Disposition"));
    verify(jasperReportsViewService).generateNdsoRequisitionReport(requisition);
  }
}
