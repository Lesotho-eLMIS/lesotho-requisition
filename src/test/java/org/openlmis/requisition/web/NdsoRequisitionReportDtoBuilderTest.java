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
import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.when;
import static org.openlmis.requisition.domain.requisition.RequisitionStatus.APPROVED;
import static org.openlmis.requisition.domain.requisition.RequisitionStatus.RELEASED_WITHOUT_ORDER;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.requisition.domain.requisition.Requisition;
import org.openlmis.requisition.domain.requisition.RequisitionDataBuilder;
import org.openlmis.requisition.domain.requisition.RequisitionLineItem;
import org.openlmis.requisition.domain.requisition.RequisitionLineItemDataBuilder;
import org.openlmis.requisition.domain.requisition.StatusChange;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.GeographicZoneDto;
import org.openlmis.requisition.dto.NdsoRequisitionLineItemDto;
import org.openlmis.requisition.dto.NdsoRequisitionReportDto;
import org.openlmis.requisition.dto.OrderableDto;
import org.openlmis.requisition.dto.ProcessingPeriodDto;
import org.openlmis.requisition.dto.ProgramDto;
import org.openlmis.requisition.dto.UserDto;
import org.openlmis.requisition.service.PeriodService;
import org.openlmis.requisition.service.referencedata.FacilityReferenceDataService;
import org.openlmis.requisition.service.referencedata.OrderableReferenceDataService;
import org.openlmis.requisition.service.referencedata.ProgramReferenceDataService;
import org.openlmis.requisition.service.referencedata.UserReferenceDataService;
import org.openlmis.requisition.testutils.FacilityDtoDataBuilder;
import org.openlmis.requisition.testutils.GeographicZoneDtoDataBuilder;
import org.openlmis.requisition.testutils.OrderableDtoDataBuilder;
import org.openlmis.requisition.testutils.ProcessingPeriodDtoDataBuilder;
import org.openlmis.requisition.testutils.ProgramDtoDataBuilder;
import org.openlmis.requisition.testutils.StatusChangeDataBuilder;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class NdsoRequisitionReportDtoBuilderTest {

  private static final UUID FACILITY_ID = UUID.randomUUID();
  private static final UUID PROGRAM_ID = UUID.randomUUID();
  private static final UUID PERIOD_ID = UUID.randomUUID();
  private static final UUID APPROVER_ID = UUID.randomUUID();
  private static final String NULL_VALUE = "NULL";
  private static final Clock FIXED_CLOCK = Clock.fixed(
      Instant.parse("2026-07-30T00:52:00Z"), ZoneId.of("Africa/Maseru"));

  @Mock
  private FacilityReferenceDataService facilityReferenceDataService;

  @Mock
  private ProgramReferenceDataService programReferenceDataService;

  @Mock
  private OrderableReferenceDataService orderableReferenceDataService;

  @Mock
  private UserReferenceDataService userReferenceDataService;

  @Mock
  private PeriodService periodService;

  @InjectMocks
  private NdsoRequisitionReportDtoBuilder builder = new NdsoRequisitionReportDtoBuilder();

  private Requisition requisition;
  private OrderableDto alphaOrderable;
  private OrderableDto zuluOrderable;

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(builder, "clock", FIXED_CLOCK);

    RequisitionLineItem zuluLine = createLine(101, 8, 12L, false);
    RequisitionLineItem alphaLine = createLine(null, null, null, false);
    RequisitionLineItem skippedLine = createLine(1, 1, 1L, true);

    zuluOrderable = createOrderable(zuluLine, "Zulu Product", "ARV", "Box");
    alphaOrderable = createOrderable(alphaLine, "Alpha Product", "ARV", null);

    requisition = new RequisitionDataBuilder()
        .withFacilityId(FACILITY_ID)
        .withProgramId(PROGRAM_ID)
        .withProcessingPeriodId(PERIOD_ID)
        .withStatus(APPROVED)
        .withRequisitionLineItems(Arrays.asList(zuluLine, alphaLine, skippedLine))
        .build();

    GeographicZoneDto zone = new GeographicZoneDtoDataBuilder().buildAsDto();
    zone.setName("Maseru District");
    FacilityDto facility = new FacilityDtoDataBuilder()
        .withId(FACILITY_ID)
        .withName("AHF ART Clinic")
        .withGeographicZone(zone)
        .buildAsDto();
    ProgramDto program = new ProgramDtoDataBuilder()
        .withId(PROGRAM_ID)
        .withName("ART")
        .buildAsDto();
    ProcessingPeriodDto period = new ProcessingPeriodDtoDataBuilder()
        .withId(PERIOD_ID)
        .withStartDate(LocalDate.of(2026, 4, 1))
        .buildAsDto();

    StatusChange approval = approval(APPROVED, APPROVER_ID,
        ZonedDateTime.parse("2026-07-29T12:00:00+02:00"));
    requisition.setStatusChanges(Collections.singletonList(approval));

    UserDto approver = new UserDto();
    approver.setFirstName("Novusimusi");
    approver.setLastName("Chesa");

    when(facilityReferenceDataService.findOne(FACILITY_ID)).thenReturn(facility);
    when(programReferenceDataService.findOne(PROGRAM_ID)).thenReturn(program);
    when(periodService.getPeriod(PERIOD_ID)).thenReturn(period);
    when(orderableReferenceDataService.findByIdentities(anySet()))
        .thenReturn(Arrays.asList(zuluOrderable, alphaOrderable));
    when(userReferenceDataService.findOne(APPROVER_ID)).thenReturn(approver);
  }

  @Test
  public void shouldBuildNdsoReportAndMapLineItems() {
    NdsoRequisitionReportDto result = builder.build(requisition);

    assertEquals("Facility - Monthly NDSO - ART - InformedPush", result.getTitle());
    assertEquals("Apr 2026", result.getReportingPeriod());
    assertEquals("Maseru District", result.getDistrictName());
    assertEquals("AHF ART Clinic", result.getFacilityName());
    assertEquals("Novusimusi Chesa", result.getAuthorisingPerson());
    assertEquals("2026-07-30", result.getGeneratedDate());
    assertEquals(2, result.getLineItems().size());

    NdsoRequisitionLineItemDto alpha = result.getLineItems().get(0);
    assertEquals("Alpha Product", alpha.getProductName());
    assertEquals(NULL_VALUE, alpha.getUnitOfIssue());
    assertEquals(NULL_VALUE, alpha.getAverageMonthlyConsumption());
    assertEquals(NULL_VALUE, alpha.getStockOnHand());
    assertEquals(NULL_VALUE, alpha.getQuantityToOrder());

    NdsoRequisitionLineItemDto zulu = result.getLineItems().get(1);
    assertEquals("ARV", zulu.getTabId());
    assertEquals("Box", zulu.getUnitOfIssue());
    assertEquals("101", zulu.getAverageMonthlyConsumption());
    assertEquals("8", zulu.getStockOnHand());
    assertEquals("12", zulu.getQuantityToOrder());
  }

  @Test
  public void shouldUseApprovalProducingReportOnlyTransition() {
    UUID reportOnlyApproverId = UUID.randomUUID();
    requisition.setReportOnly(true);
    StatusChange approved = approval(APPROVED, APPROVER_ID,
        ZonedDateTime.parse("2026-07-28T12:00:00+02:00"));
    StatusChange releasedWithoutOrder = approval(RELEASED_WITHOUT_ORDER, reportOnlyApproverId,
        ZonedDateTime.parse("2026-07-29T12:00:00+02:00"));
    requisition.setStatusChanges(Arrays.asList(approved, releasedWithoutOrder));

    UserDto reportOnlyApprover = new UserDto();
    reportOnlyApprover.setUsername("report-only-approver");
    when(userReferenceDataService.findOne(reportOnlyApproverId)).thenReturn(reportOnlyApprover);

    NdsoRequisitionReportDto result = builder.build(requisition);

    assertEquals("report-only-approver", result.getAuthorisingPerson());
  }

  private RequisitionLineItem createLine(Integer averageConsumption, Integer stockOnHand,
      Long packsToShip, boolean skipped) {
    return new RequisitionLineItemDataBuilder()
        .withAverageConsumption(averageConsumption)
        .withStockOnHand(stockOnHand)
        .withPacksToShip(packsToShip)
        .withSkippedFlag(skipped)
        .build();
  }

  private OrderableDto createOrderable(RequisitionLineItem line, String productName,
      String tabId, String displayUnit) {
    OrderableDto orderable = new OrderableDtoDataBuilder()
        .withId(line.getOrderable().getId())
        .withVersionNumber(line.getOrderable().getVersionNumber())
        .withFullProductName(productName)
        .withProgramOrderable(PROGRAM_ID, true)
        .buildAsDto();
    orderable.getProgramOrderable(PROGRAM_ID).setOrderableCategoryDisplayName(tabId);
    orderable.getDispensable().setDisplayUnit(displayUnit);
    return orderable;
  }

  private StatusChange approval(org.openlmis.requisition.domain.requisition.RequisitionStatus status,
      UUID authorId, ZonedDateTime createdDate) {
    return new StatusChangeDataBuilder()
        .withRequisition(requisition)
        .withStatus(status)
        .withAuthorId(authorId)
        .withCreatedDate(createdDate)
        .build();
  }
}
